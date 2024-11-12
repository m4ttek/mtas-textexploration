package mtas.codec.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mtas.analysis.token.MtasToken;
import mtas.analysis.token.MtasTokenString;
import mtas.codec.MtasCodecPostingsFormat;
import mtas.codec.tree.IntervalTreeNodeData;
import mtas.codec.util.CodecInfo.IndexDoc;
import mtas.codec.util.CodecSearchTree.MtasTreeHit;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.codec.util.heatmap.HeatmapMtasCounter;
import mtas.parser.function.ParseException;
import mtas.parser.function.util.MtasFunctionParserFunction;
import mtas.search.spans.MtasSpanAndQuery;
import mtas.search.spans.MtasSpanFollowedByQuery;
import mtas.search.spans.MtasSpanMatchAllQuery;
import mtas.search.spans.MtasSpanPrecededByQuery;
import mtas.search.spans.MtasSpanSequenceItem;
import mtas.search.spans.MtasSpanSequenceQuery;
import mtas.search.spans.MtasSpanTermQuery;
import mtas.search.spans.util.MtasSpanQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.solr.legacy.LegacyNumericUtils;
import org.apache.solr.schema.NumberType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class CodecCollector.
 */
public interface CodecCollector {

  /** The Constant log. */
  Logger log = LoggerFactory.getLogger(CodecCollector.class);

  ExecutorService queryExecutorService = Executors.newVirtualThreadPerTaskExecutor();

  Pattern patternAlias = Pattern.compile("^([^:]+):([^:]+)$");

  /** The Constant INDEX_MATCH_INTERSECT. */
  String MATCH_INTERSECT = "intersect";

  /** The Constant INDEX_MATCH_START. */
  String MATCH_START = "start";

  /** The Constant INDEX_MATCH_COMPLETE. */
  String MATCH_COMPLETE = "complete";

  /**
   * Collect field.
   *
   * @param field
   *          the field
   * @param searcher
   *          the searcher
   * @param reader
   *          the reader
   * @param rawReader
   *          the raw reader
   * @param fullDocList
   *          the full doc list
   * @param fullDocSet
   *          the full doc set
   * @param fieldInfo
   *          the field info
   * @param spansQueryWeight
   *          the spans query weight
   * @param status
   *          the status
   * @throws IllegalAccessException
   *           the illegal access exception
   * @throws IllegalArgumentException
   *           the illegal argument exception
   * @throws InvocationTargetException
   *           the invocation target exception
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  static void collectField(String field, IndexSearcher searcher, IndexReader reader, IndexReader rawReader,
      List<Integer> fullDocList, List<Integer> fullDocSet, ComponentField fieldInfo,
      Map<MtasSpanQuery, SpanWeight> spansQueryWeight, Status status) throws IllegalArgumentException, IOException {


      LongAdder numberOfDocumentsFound = new LongAdder();
      if (status != null) {
          status.init(reader.numDocs(), reader.leaves().size());
          if (fullDocSet != null && status.numberDocumentsFound == null) {
              status.numberDocumentsFound = new AtomicLong();
          }
      }

      Map<Integer, List<Integer>> docSets = new ConcurrentHashMap<>();
      var queries = reader.leaves()
            .stream()
            .<Callable<Void>>map(lrc -> () -> {
                searchAndCollectPerIndex(lrc, searcher, rawReader, fullDocList, fullDocSet, docSets, fieldInfo, spansQueryWeight, status, numberOfDocumentsFound, field);
                return null;
            })
            .toList();
    try {
        var futures = queryExecutorService.invokeAll(queries);
        for (var future: futures) {
            future.get();
        }
    } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
    }

// LIST query strategy
//          if (!fieldInfo.listList.isEmpty()) {
//              var componentField = fieldInfo.listList.getFirst();
//              if (componentField.getHits().size() >= componentField.getNumber()) {
//                  break;
//              }
//          }
//    });
    if (status != null) {
        if (!status.subNumberSegmentsFinished.isEmpty()) {
            status.numberSegmentsFinished = new AtomicInteger(Collections.max(status.subNumberSegmentsFinished.values()));
        }
        if (!status.subNumberDocumentsFinished.isEmpty()) {
            status.numberDocumentsFinished = new AtomicLong(Collections.max(status.subNumberDocumentsFinished.values()));
        }
    }

      // check termvectors
      if (!fieldInfo.termVectorList.isEmpty() && needSecondRoundTermvector(fieldInfo.termVectorList)) {
          // check positions
          boolean needPositions = false;
          if (!fieldInfo.termVectorList.isEmpty()) {
              for (ComponentTermVector ctv : fieldInfo.termVectorList) {
                  if (!needPositions) {
                      needPositions = ctv.functions != null ? ctv.functionNeedPositions() : needPositions;
                  }
              }
          }


          // loop
          for (LeafReaderContext lrc : reader.leaves()) {
              LeafReader r = lrc.reader();
              List<Integer> docSet = docSets.get(lrc.ord);
              Terms t = rawReader.leaves().get(lrc.ord).reader().terms(field);
              Map<Integer, Integer> positionsData = null;
              if (needPositions) {
                  CodecInfo mtasCodecInfo = t == null ? null : CodecInfo.getCodecInfoFromTerms(t);
                  positionsData = computePositions(mtasCodecInfo, r, lrc, field, docSet);
              }
              createTermvectorSecondRound(fieldInfo.termVectorList, positionsData, docSets.get(lrc.ord), t, r, lrc, status);
          }

      }
  }

    static void searchAndCollectPerIndex(LeafReaderContext lrc,
                                         IndexSearcher searcher,
                                         IndexReader rawReader,
                                         List<Integer> fullDocList,
                                         List<Integer> fullDocSet,
                                         Map<Integer, List<Integer>> docSets,
                                         ComponentField fieldInfo,
                                         Map<MtasSpanQuery, SpanWeight> spansQueryWeight,
                                         Status status,
                                         LongAdder numberOfDocumentsFound,
                                         String field) {
        try {
            LeafReader r = lrc.reader();
            // compute relevant docSet/docList
            List<Integer> docSet = null;
            List<Integer> docList = null;
            if (fullDocSet != null) {
                docSet = new ArrayList<>();
                docSets.put(lrc.ord, docSet);
                Bits liveDocs = lrc.reader().getLiveDocs();
                for (Integer docSetId : fullDocSet) {
                    // just to make sure to ignore deleted documents
                    if ((docSetId >= lrc.docBase) && (docSetId < lrc.docBase + lrc.reader().maxDoc())
                            && (liveDocs == null || liveDocs.get((docSetId - lrc.docBase)))) {
                        docSet.add(docSetId);
                    }
                }
                Collections.sort(docSet);
                numberOfDocumentsFound.add(docSet.size());
                status.numberDocumentsFound.accumulateAndGet(numberOfDocumentsFound.longValue(),
                        Math::max);// = Math.max(status.numberDocumentsFound, numberOfDocumentsFound);
            }
            if (fullDocList != null) {
                docList = new ArrayList<>();
                for (Integer docListId : fullDocList) {
                    if ((docListId >= lrc.docBase) && (docListId < lrc.docBase + lrc.reader().maxDoc())) {
                        docList.add(docListId);
                    }
                }
                Collections.sort(docList);
            }

            Terms terms = rawReader.leaves().get(lrc.ord).reader().terms(field);
            CodecInfo mtasCodecInfo = terms == null ? null : CodecInfo.getCodecInfoFromTerms(terms);

            collectSpansPositionsAndTokens(spansQueryWeight, searcher, mtasCodecInfo, r, lrc, field, terms, docSet, docList,
                    fieldInfo, rawReader.leaves().get(lrc.ord).reader().getFieldInfos());
            collectPrefixes(rawReader.leaves().get(lrc.ord).reader().getFieldInfos(), field, fieldInfo);

            if (status != null) {
                status.subNumberSegmentsFinished.computeIfPresent(field, (f, currentCount) -> {
                    status.subNumberSegmentsFinishedTotal.incrementAndGet();
                    return currentCount + 1;
                });
                status.subNumberDocumentsFinished.computeIfPresent(field, (s, docCount) -> {
                    status.subNumberDocumentsFinishedTotal.addAndGet(r.numDocs());
                    return docCount + r.numDocs();
                });
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

  /**
   * Collect collection.
   *
   * @param reader
   *          the reader
   * @param docSet
   *          the doc set
   * @param collectionInfo
   *          the collection info
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  static void collectCollection(IndexReader reader, List<Integer> docSet, ComponentCollection collectionInfo)
      throws IOException {
    if (collectionInfo.action().equals(ComponentCollection.ACTION_CHECK)) {
      // can't do anything in lucene for check
    } else if (collectionInfo.action().equals(ComponentCollection.ACTION_LIST)) {
      // can't do anything in lucene for list
    } else if (collectionInfo.action().equals(ComponentCollection.ACTION_CREATE)) {
      BytesRef term = null;
      PostingsEnum postingsEnum = null;
      Integer docId;
      Integer termDocId = -1;
      Terms terms;
      LeafReaderContext lrc;
      LeafReader r;
        for (LeafReaderContext leafReaderContext : reader.leaves()) {
            lrc = leafReaderContext;
            r = lrc.reader();
            for (String field : collectionInfo.fields()) {
                if ((terms = r.terms(field)) != null) {
                    TermsEnum termsEnum = terms.iterator();
                    while ((term = termsEnum.next()) != null) {
                        Iterator<Integer> docIterator = docSet.iterator();
                        postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.NONE);
                        termDocId = -1;
                        while (docIterator.hasNext()) {
                            docId = docIterator.next() - lrc.docBase;
                            if ((docId >= termDocId)
                                    && ((docId.equals(termDocId)) || ((termDocId = postingsEnum.advance(docId)).equals(docId)))) {
                                collectionInfo.addValue(term.utf8ToString());
                                break;
                            }
                            if (termDocId.equals(PostingsEnum.NO_MORE_DOCS)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
  }

  /**
   * Collect spans positions and tokens.
   *
   * @param spansQueryWeight
   *          the spans query weight
   * @param searcher
   *          the searcher
   * @param mtasCodecInfo
   *          the mtas codec info
   * @param r
   *          the r
   * @param lrc
   *          the lrc
   * @param field
   *          the field
   * @param t
   *          the t
   * @param docSet
   *          the doc set
   * @param docList
   *          the doc list
   * @param fieldInfo
   *          the field info
   * @param fieldInfos
   *          the field infos
   * @param status
   *          the status
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void collectSpansPositionsAndTokens(Map<MtasSpanQuery, SpanWeight> spansQueryWeight,
      IndexSearcher searcher, CodecInfo mtasCodecInfo, LeafReader r, LeafReaderContext lrc, String field, Terms t,
      List<Integer> docSet, List<Integer> docList, ComponentField fieldInfo, FieldInfos fieldInfos)
      throws IOException {

    boolean needSpans = false;
    boolean needPositions = false;
    boolean needTokens = false;

    // results
    Map<Integer, Integer> positionsData = null;
    Map<Integer, Integer> tokensData = null;
    Set<MtasSpanQuery> spansNumberByPositions = null;
    Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData = null;
    Map<MtasSpanQuery, Map<Integer, List<Match>>> spansMatchData = null;
    Map<String, SortedMap<String, int[]>> facetData = null;
    Map<String, String> facetDataType = null;

    // collect position stats
    if (!fieldInfo.statsPositionList.isEmpty()) {
      needPositions = true;
    }
    // collect token stats
    if (!fieldInfo.statsTokenList.isEmpty()) {
      needTokens = true;
    }
    if (!fieldInfo.termVectorList.isEmpty()) {
      for (ComponentTermVector ctv : fieldInfo.termVectorList) {
        if (!needPositions) {
          needPositions = (ctv.functions == null ? ctv.subComponentFunction.parserFunction.needPositions()
              : ctv.functionNeedPositions());
        }
      }
    }

    // compute from spans for selected docs
    if (!fieldInfo.spanQueryList.isEmpty()) {
      // check for statsSpans
      spansNumberByPositions = new HashSet<>();
      spansNumberData = new HashMap<>();
      spansMatchData = new HashMap<>();
      facetData = new HashMap<>();
      facetDataType = new HashMap<>();
      // spans
      if (!fieldInfo.statsSpanList.isEmpty()) {
        for (ComponentSpan cs : fieldInfo.statsSpanList) {
          needPositions = (!needPositions) ? cs.parser.needPositions() : needPositions;
          needPositions = (!needPositions) ? cs.functionNeedPositions() : needPositions;
          needSpans = (!needSpans) ? cs.parser.needArgumentsNumber() > 0 : needSpans;
          Set<Integer> arguments = cs.parser.needArgument();
          arguments.addAll(cs.functionNeedArguments());
          for (int a : arguments) {
            if (cs.getQueries().size() > a) {
              MtasSpanQuery q = cs.getQueries().get(a);
              if (!spansNumberData.containsKey(q)) {
                spansNumberData.put(q, new HashMap<Integer, Integer>());
              }
            }
          }
        }
      }
      // kwic
      if (!fieldInfo.kwicList.isEmpty()) {
        needSpans = true;
        for (ComponentKwic ck : fieldInfo.kwicList) {
          if (!spansMatchData.containsKey(ck.getQuery())) {
            spansMatchData.put(ck.getQuery(), new HashMap<>());
          }
        }
      }
      // index
      if (!fieldInfo.indexList.isEmpty()) {
        needSpans = true;
        for (ComponentIndex ci : fieldInfo.indexList) {
          if (!spansMatchData.containsKey(ci.query)) {
            spansMatchData.put(ci.query, new HashMap<>());
          }
          if (ci.blockQuery != null && !spansMatchData.containsKey(ci.blockQuery)) {
            spansMatchData.put(ci.blockQuery, new HashMap<>());
          }
        }
      }
      // list
      if (!fieldInfo.listList.isEmpty()) {
        needSpans = true;
        for (ComponentList cl : fieldInfo.listList) {
          if (!spansMatchData.containsKey(cl.getSpanQuery())) {
            if (cl.getNumber() > 0) {
              // only if needed
              if (cl.getPosition() < (cl.getStart() + cl.getNumber())) {
                spansMatchData.put(cl.getSpanQuery(), new HashMap<Integer, List<Match>>());
              } else {
                spansNumberData.put(cl.getSpanQuery(), new HashMap<Integer, Integer>());
              }
            } else if (!spansNumberData.containsKey(cl.getSpanQuery())) {
              spansNumberData.put(cl.getSpanQuery(), new HashMap<Integer, Integer>());
            }
          }
        }
      }
      // group
      if (!fieldInfo.groupList.isEmpty()) {
        needSpans = true;
        for (ComponentGroup cg : fieldInfo.groupList) {
          if (!spansMatchData.containsKey(cg.getSpanQuery())) {
            spansMatchData.put(cg.getSpanQuery(), new HashMap<>());
          }
        }
      }
      // heatmap
      if (!fieldInfo.heatmapList.isEmpty()) {
        for (ComponentHeatmap ch : fieldInfo.heatmapList) {
          needPositions = (!needPositions) ? ch.parser.needPositions() : needPositions;
          needPositions = (!needPositions) ? ch.hm.functionNeedPositions() : needPositions;
          Set<Integer> arguments = ch.parser.needArgument();
          arguments.addAll(ch.hm.functionNeedArguments());
          for (int a : arguments) {
            if (ch.queries.size() > a) {
              MtasSpanQuery q = ch.queries.get(a);
              if (!spansNumberData.containsKey(q)) {
                spansNumberData.put(q, new HashMap<>());
              }
            }
          }
        }
      }
      // facet
      if (!fieldInfo.facetList.isEmpty()) {
        for (ComponentFacet cf : fieldInfo.facetList) {
          needPositions = !needPositions ? cf.baseParserNeedPositions() : needPositions;
          needPositions = !needPositions ? cf.functionNeedPositions() : needPositions;
          for (int i = 0; i < cf.getBaseFields().length; i++) {
            needSpans = !needSpans ? cf.getBaseParsers()[i].needArgumentsNumber() > 0 : needSpans;
            Set<Integer> arguments = cf.getBaseParsers()[i].needArgument();
            for (int a : arguments) {
              if (cf.getSpanQueries().size() > a) {
                MtasSpanQuery q = cf.getSpanQueries().get(a);
                if (!spansNumberData.containsKey(q)) {
                  spansNumberData.put(q, new HashMap<Integer, Integer>());
                }
              }
            }
            for (MtasFunctionParserFunction function : cf.getBaseFunctionParserFunctions()[i]) {
              needSpans = !needSpans ? function.needArgumentsNumber() > 0 : needSpans;
              arguments = function.needArgument();
              for (int a : arguments) {
                if (cf.getSpanQueries().size() > a) {
                  MtasSpanQuery q = cf.getSpanQueries().get(a);
                  if (!spansNumberData.containsKey(q)) {
                    spansNumberData.put(q, new HashMap<Integer, Integer>());
                  }
                }
              }
            }
            if (!facetData.containsKey(cf.getBaseFields()[i])) {
              facetData.put(cf.getBaseFields()[i], new TreeMap<String, int[]>());
              facetDataType.put(cf.getBaseFields()[i], cf.getBaseFieldTypes()[i]);
            }
          }
        }
      }
      // termvector
      if (!fieldInfo.termVectorList.isEmpty()) {
        for (ComponentTermVector ctv : fieldInfo.termVectorList) {
            if ((ctv.subComponentFunction.parserFunction != null
                    && ctv.subComponentFunction.parserFunction.needPositions())
                    || (ctv.functions != null && ctv.functionNeedPositions())) {
                needPositions = true;
                break;
            }
        }
      }
    }

    if (needSpans) {
      Map<Integer, Integer> numberData;
      Map<Integer, List<Match>> matchData;
      // collect values for facetFields
      for (Entry<String, SortedMap<String, int[]>> entry : facetData.entrySet()) {
        FieldInfo fi = fieldInfos.fieldInfo(entry.getKey());
        if (fi != null) {
          // prefer to use pointvalue
          if (!fi.getDocValuesType().equals(DocValuesType.NONE)) {
            Iterator<Integer> docIterator = docSet.iterator();
            // numeric or sorted
            if (fi.getDocValuesType().equals(DocValuesType.NUMERIC)
                || fi.getDocValuesType().equals(DocValuesType.SORTED)
                || fi.getDocValuesType().equals(DocValuesType.SORTED_SET)
                || fi.getDocValuesType().equals(DocValuesType.SORTED_NUMERIC)
                || fi.getDocValuesType().equals(DocValuesType.BINARY)) {
              // create map of values to corresponding docIds
              Map<Object, List<Integer>> facetDataSubList = new HashMap<>();
              // numeric
              if (fi.getDocValuesType().equals(DocValuesType.NUMERIC)) {
                NumericDocValues docValues = r.getContext().reader().getNumericDocValues(entry.getKey());
                int docId;
                while (docIterator.hasNext()) {
                  docId = docIterator.next() - lrc.docBase;
                  if (docValues.advanceExact(docId)) {
                    long value = docValues.longValue();
                    if (!facetDataSubList.containsKey(value)) {
                      List<Integer> facetDataSubListItem = new ArrayList<>();
                      facetDataSubListItem.add(docId + lrc.docBase);
                      facetDataSubList.put(value, facetDataSubListItem);
                    } else {
                      facetDataSubList.get(value).add(docId + lrc.docBase);
                    }
                  }
                }
                // sorted numeric
              } else if (fi.getDocValuesType().equals(DocValuesType.SORTED_NUMERIC)) {
                SortedNumericDocValues docValues = r.getContext().reader().getSortedNumericDocValues(entry.getKey());
                int docId;
                while (docIterator.hasNext()) {
                  docId = docIterator.next() - lrc.docBase;
                  if (docValues.advanceExact(docId)) {
                    int n = docValues.docValueCount();
                    for (int i = 0; i < n; i++) {
                      long value = docValues.nextValue();
                      if (!facetDataSubList.containsKey(value)) {
                        List<Integer> facetDataSubListItem = new ArrayList<>();
                        facetDataSubListItem.add(docId + lrc.docBase);
                        facetDataSubList.put(value, facetDataSubListItem);
                      } else {
                        facetDataSubList.get(value).add(docId + lrc.docBase);
                      }
                    }
                  }
                }
                // sorted set
              } else if (fi.getDocValuesType().equals(DocValuesType.SORTED_SET)) {
                SortedSetDocValues docValues = r.getContext().reader().getSortedSetDocValues(entry.getKey());
                int docId;
                Map<Long, String> dictionary = new HashMap<>();
                while (docIterator.hasNext()) {
                  docId = docIterator.next() - lrc.docBase;
                  if (docValues.advanceExact(docId)) {
                    long tmpValue;
                    String value;
                    while ((tmpValue = docValues.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
                      if (!dictionary.containsKey(tmpValue)) {
                        value = docValues.lookupOrd(tmpValue).utf8ToString();
                        dictionary.put(tmpValue, value);
                      } else {
                        value = dictionary.get(tmpValue);
                      }
                      if (!facetDataSubList.containsKey(value)) {
                        List<Integer> facetDataSubListItem = new ArrayList<>();
                        facetDataSubListItem.add(docId + lrc.docBase);
                        facetDataSubList.put(value, facetDataSubListItem);
                      } else {
                        facetDataSubList.get(value).add(docId + lrc.docBase);
                      }
                    }
                  }
                }
                // sorted
              } else if (fi.getDocValuesType().equals(DocValuesType.SORTED)) {
                SortedDocValues docValues = r.getContext().reader().getSortedDocValues(entry.getKey());
                int docId;
                while (docIterator.hasNext()) {
                  docId = docIterator.next() - lrc.docBase;
                  if (docValues.advanceExact(docId)) {
                    String value = docValues.lookupOrd(docValues.ordValue()).utf8ToString();
                    if (!facetDataSubList.containsKey(value)) {
                      List<Integer> facetDataSubListItem = new ArrayList<>();
                      facetDataSubListItem.add(docId + lrc.docBase);
                      facetDataSubList.put(value, facetDataSubListItem);
                    } else {
                      facetDataSubList.get(value).add(docId + lrc.docBase);
                    }
                  }
                }
              }
              if (!facetDataSubList.isEmpty()) {
                SortedMap<String, int[]> facetDataList = entry.getValue();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                for (Entry<Object, List<Integer>> facetEntry : facetDataSubList.entrySet()) {
                  int[] docIdList = facetEntry.getValue().stream().mapToInt(i -> i).toArray();
                  String termValue;
                  if (facetDataType.get(entry.getKey()).equals(NumberType.DATE.name())) {
                    Date date = new Date((Long) facetEntry.getKey());
                    termValue = sdf.format(date);
                  } else {
                    termValue = facetEntry.getKey().toString();
                  }
                  if (!facetDataList.containsKey(termValue)) {
                    facetDataList.put(termValue, docIdList);
                  } else {
                    int[] oldList = facetDataList.get(termValue);
                    int[] newList = new int[oldList.length + docIdList.length];
                    System.arraycopy(oldList, 0, newList, 0, oldList.length);
                    System.arraycopy(docIdList, 0, newList, oldList.length, docIdList.length);
                    facetDataList.put(termValue, newList);
                  }
                }
              }
            } else {
              throw new IOException("facets for docValues of type " + fi.getDocValuesType() + " not implemented");
            }
          } else if (!docSet.isEmpty()) {
            if (facetDataType.get(entry.getKey()).equals(ComponentFacet.TYPE_POINTFIELD_WITHOUT_DOCVALUES)) {
              throw new IOException("can't create facets for pointField without docValues");
            }
            Terms fft = r.terms(entry.getKey());
            if (fft != null) {
              TermsEnum termsEnum = fft.iterator();
              BytesRef term = null;
              PostingsEnum postingsEnum = null;
              SortedMap<String, int[]> facetDataList = entry.getValue();
              while ((term = termsEnum.next()) != null) {
                int docId;
                int termDocId = -1;
                int[] facetDataSublist = new int[docSet.size()];
                int facetDataSublistCounter = 0;
                Iterator<Integer> docIterator = docSet.iterator();
                postingsEnum = termsEnum.postings(postingsEnum);
                while (docIterator.hasNext()) {
                  docId = docIterator.next() - lrc.docBase;
                  if (docId >= termDocId
                      && ((docId == termDocId) || ((termDocId = postingsEnum.advance(docId)) == docId))) {
                    facetDataSublist[facetDataSublistCounter] = docId + lrc.docBase;
                    facetDataSublistCounter++;
                  }
                }
                if (facetDataSublistCounter > 0) {
                  String termValue = null;
                  if (facetDataType.get(entry.getKey()).equals(NumberType.INTEGER.name())) {
                    // only values without shifting bits
                    if (term.bytes[term.offset] == LegacyNumericUtils.SHIFT_START_INT) {
                      termValue = Integer.toString(LegacyNumericUtils.prefixCodedToInt(term));
                    } else {
                      continue;
                    }
                  } else if (facetDataType.get(entry.getKey()).equals(NumberType.LONG.name())) {
                    if (term.bytes[term.offset] == LegacyNumericUtils.SHIFT_START_LONG) {
                      termValue = Long.toString(LegacyNumericUtils.prefixCodedToLong(term));
                    } else {
                      continue;
                    }
                  } else {
                    termValue = term.utf8ToString();
                  }
                  if (!facetDataList.containsKey(termValue)) {
                    facetDataList.put(termValue, Arrays.copyOf(facetDataSublist, facetDataSublistCounter));
                  } else {
                    int[] oldList = facetDataList.get(termValue);
                    int[] newList = new int[oldList.length + facetDataSublistCounter];
                    System.arraycopy(oldList, 0, newList, 0, oldList.length);
                    System.arraycopy(facetDataSublist, 0, newList, oldList.length, facetDataSublistCounter);
                    facetDataList.put(termValue, newList);
                  }
                }
              }
            }
          }
        }
      }

      // collect matches and numbers for queries
      for (MtasSpanQuery sq : fieldInfo.spanQueryList) {
        // what to collect : numbers
          numberData = spansNumberData.getOrDefault(sq, null);
        // what to collect: matches
          matchData = spansMatchData.getOrDefault(sq, null);
        boolean doNormalCollection = true;
        // if only number is needed, possibly termvectors can be used
        if ((numberData != null) && (matchData == null)) {
          if (sq.isMatchAllPositionsQuery()) {
            spansNumberByPositions.add(sq);
            needPositions = true;
            doNormalCollection = false;
          }
        }
        // collect (if termvector collection didn't work)
        if (doNormalCollection && ((numberData != null) || (matchData != null))) {
          Spans spans = spansQueryWeight.get(sq).getSpans(lrc, SpanWeight.Postings.POSITIONS);
          if (spans != null) {
            Iterator<Integer> it;
            if (docSet != null) {
              it = docSet.iterator();
            } else {
              it = docList.iterator();
            }
            if (it.hasNext()) {
              int docId = it.next();
              int number;
              ArrayList<Match> matchDataList;
              Integer spansDocId = null;
              while (docId != DocIdSetIterator.NO_MORE_DOCS) {
                if (spans.advance((docId - lrc.docBase)) == DocIdSetIterator.NO_MORE_DOCS) {
                  break;
                }
                spansDocId = spans.docID() + lrc.docBase;
                while ((docId < spansDocId) && it.hasNext()) {
                  docId = it.next();
                }
                if (docId < spansDocId) {
                  break;
                }
                if (spansDocId.equals(docId)) {
                  number = 0;
                  matchDataList = new ArrayList<>();
                  int tmpStartPosition;
                  while ((tmpStartPosition = spans.nextStartPosition()) != Spans.NO_MORE_POSITIONS) {
                    number++;
                    if (matchData != null) {
                      Match m = new Match(tmpStartPosition, spans.endPosition());
                      matchDataList.add(m);
                    }
                  }
                  if ((numberData != null)) {
                    numberData.put(spansDocId, number);
                  }
                  if ((matchData != null)) {
                    matchData.put(spansDocId, matchDataList);
                  }
                  if (it.hasNext()) {
                    docId = it.next();
                  } else {
                    break;
                  }
                }
              }
            }
          }
        }
      }
    }

    // collect position stats
    if (needPositions) {
      if (mtasCodecInfo != null) {
        // for relatively small numbers, compute only what is needed
        if (docSet.size() < Math.log(r.maxDoc())) {
          positionsData = new HashMap<>();
          for (int docId : docSet) {
            positionsData.put(docId, mtasCodecInfo.getNumberOfPositions(field, (docId - lrc.docBase)));
          }
          // compute everything, only use what is needed
        } else {
          positionsData = mtasCodecInfo.getAllNumberOfPositions(field, lrc.docBase);
          for (int docId : docSet) {
            if (!positionsData.containsKey(docId)) {
              positionsData.put(docId, 0);
            }
          }
        }
      } else {
        positionsData = new HashMap<>();
        for (int docId : docSet) {
          positionsData.put(docId, 0);
        }
      }
      if (spansNumberByPositions != null) {
        for (MtasSpanQuery sq : spansNumberByPositions) {
          Map<Integer, Integer> numberData = spansNumberData.get(sq);
          positionsData.forEach((k, v) -> numberData.put(k, v != null ? v : 0));
        }
      }
    }

    // collect token stats
    if (needTokens) {
      if (mtasCodecInfo != null) {
        // for relatively small numbers, compute only what is needed
        if (docSet.size() < Math.log(r.maxDoc())) {
          tokensData = new HashMap<>();
          for (int docId : docSet) {
            tokensData.put(docId, mtasCodecInfo.getNumberOfTokens(field, (docId - lrc.docBase)));
          }
          // compute everything, only use what is needed
        } else {
          tokensData = mtasCodecInfo.getAllNumberOfTokens(field, lrc.docBase);
          for (int docId : docSet) {
            if (!tokensData.containsKey(docId)) {
              tokensData.put(docId, 0);
            }
          }
        }
      } else {
        tokensData = new HashMap<>();
        for (int docId : docSet) {
          tokensData.put(docId, 0);
        }
      }
    }


    if (!fieldInfo.statsPositionList.isEmpty()) {
      // create positions
      createPositions(fieldInfo.statsPositionList, positionsData, docSet);
    }

    if (!fieldInfo.statsTokenList.isEmpty()) {
      // create positions
      createTokens(fieldInfo.statsTokenList, tokensData, docSet);
    }

    if (!fieldInfo.pageList.isEmpty()) {
      // create pages
      createPages(fieldInfo.pageList, docList, fieldInfos.fieldInfo(field), field, lrc.docBase,
          fieldInfo.uniqueKeyField, mtasCodecInfo, searcher);
    }

    if (!fieldInfo.documentList.isEmpty()) {
      // create document
      createDocument(fieldInfo.documentList, docList, fieldInfo.uniqueKeyField, searcher, t, lrc);
    }

    if (!fieldInfo.spanQueryList.isEmpty()) {

      if (!fieldInfo.statsSpanList.isEmpty()) {
        // create stats
        createStats(fieldInfo.statsSpanList, positionsData, spansNumberData, docSet.toArray(new Integer[0]));
      }
      if (!fieldInfo.listList.isEmpty()) {
        // create list
        createList(fieldInfo.listList, spansNumberData, spansMatchData, docSet, field, lrc.docBase,
            fieldInfo.uniqueKeyField, mtasCodecInfo, searcher);
      }
      if (!fieldInfo.groupList.isEmpty()) {
        // create group
        createGroup(fieldInfo.groupList, spansMatchData, docSet, fieldInfos.fieldInfo(field), field, lrc.docBase,
            mtasCodecInfo, searcher, lrc);
      }
      if (!fieldInfo.kwicList.isEmpty()) {
        // create kwic
        createKwic(fieldInfo.kwicList, spansMatchData, docList, field, lrc.docBase, fieldInfo.uniqueKeyField,
            mtasCodecInfo, searcher);
      }
      if (!fieldInfo.indexList.isEmpty()) {
        // create indexes
        createIndexes(fieldInfo.indexList, spansMatchData, docList, field, lrc.docBase,
            fieldInfo.uniqueKeyField, mtasCodecInfo, searcher);
      }
      if (!fieldInfo.facetList.isEmpty()) {
        // create facets
        createFacet(fieldInfo.facetList, positionsData, spansNumberData, facetData, docSet);
      }

      if (!fieldInfo.heatmapList.isEmpty()) {
        // create heatmaps
        synchronized (fieldInfo.heatmapList) {
            createHeatmaps(fieldInfo.heatmapList, positionsData, spansNumberData, docSet, lrc);
        }
      }
    }

      if (!fieldInfo.termVectorList.isEmpty()) {
          synchronized (fieldInfo.termVectorList) {
              createTermvectorFull(fieldInfo.termVectorList, positionsData, docSet, t, r, lrc);
              createTermvectorFirstRound(fieldInfo.termVectorList, positionsData, docSet, t, r, lrc);
          }
      }
  }

  /**
   * Collect known prefixes.
   *
   * @param fi
   *          the fi
   * @return the sets the
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static Set<String> collectKnownPrefixes(FieldInfo fi) throws IOException {
    if (fi != null) {
      HashSet<String> result = new HashSet<>();
      String singlePositionPrefixes = fi
          .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SINGLE_POSITION);
      String multiplePositionPrefixes = fi
          .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_MULTIPLE_POSITION);
      String setPositionPrefixes = fi
          .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SET_POSITION);
      if (singlePositionPrefixes != null) {
        String[] prefixes = singlePositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
          for (String prefix : prefixes) {
              String item = prefix.trim();
              if (!item.isEmpty()) {
                  result.add(item);
              }
          }
      }
      if (multiplePositionPrefixes != null) {
        String[] prefixes = multiplePositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
          for (String prefix : prefixes) {
              String item = prefix.trim();
              if (!item.isEmpty()) {
                  result.add(item);
              }
          }
      }
      if (setPositionPrefixes != null) {
        String[] prefixes = setPositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
          for (String prefix : prefixes) {
              String item = prefix.trim();
              if (!item.isEmpty()) {
                  result.add(item);
              }
          }
      }
      return result;
    } else {
      return Collections.emptySet();
    }
  }

  /**
   * Collect intersection prefixes.
   *
   * @param fi
   *          the fi
   * @return the sets the
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static Set<String> collectIntersectionPrefixes(FieldInfo fi) throws IOException {
    if (fi != null) {
      Set<String> result = new HashSet<>();
      String intersectingPrefixes = fi
          .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_INTERSECTION);
      if (intersectingPrefixes != null) {
        String[] prefixes = intersectingPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
          for (String prefix : prefixes) {
              String item = prefix.trim();
              if (!item.equals("")) {
                  result.add(item);
              }
          }
      }
      return result;
    } else {
      return Collections.emptySet();
    }
  }

  /**
   * Collect prefixes.
   *
   * @param fieldInfos
   *          the field infos
   * @param field
   *          the field
   * @param fieldInfo
   *          the field info
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void collectPrefixes(FieldInfos fieldInfos, String field, ComponentField fieldInfo)
      throws IOException {
    if (fieldInfo.prefix.get() != null) {
      FieldInfo fi = fieldInfos.fieldInfo(field);
      if (fi != null) {
        String singlePositionPrefixes = fi
            .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SINGLE_POSITION);
        String multiplePositionPrefixes = fi
            .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_MULTIPLE_POSITION);
        String setPositionPrefixes = fi
            .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SET_POSITION);
        String intersectingPrefixes = fi
            .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_INTERSECTION);
        if (singlePositionPrefixes != null) {
          String[] prefixes = singlePositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
            for (String prefix : prefixes) {
                fieldInfo.prefix.get().addSinglePosition(prefix);
            }
        }
        if (multiplePositionPrefixes != null) {
          String[] prefixes = multiplePositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
            for (String prefix : prefixes) {
                fieldInfo.prefix.get().addMultiplePosition(prefix);
            }
        }
        if (setPositionPrefixes != null) {
          String[] prefixes = setPositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
            for (String prefix : prefixes) {
                fieldInfo.prefix.get().addSetPosition(prefix);
            }
        }
        if (intersectingPrefixes != null) {
          String[] prefixes = intersectingPrefixes.split(Pattern.quote(MtasToken.DELIMITER));
            for (String prefix : prefixes) {
                fieldInfo.prefix.get().addIntersecting(prefix);
            }
        }
      }
    }
  }

  /**
   * Collect spans for occurences.
   *
   * @param occurences
   *          the occurences
   * @param prefixes
   *          the prefixes
   * @param field
   *          the field
   * @param searcher
   *          the searcher
   * @param lrc
   *          the lrc
   * @return the map
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static Map<GroupHit, Spans> collectSpansForOccurences(Set<GroupHit> occurences, Set<String> prefixes,
      String field, IndexSearcher searcher, LeafReaderContext lrc) throws IOException {
    Map<GroupHit, Spans> list = new HashMap<>();
    final float boost = 0;
    for (GroupHit hit : occurences) {
      MtasSpanQuery queryHit = createQueryFromGroupHit(prefixes, field, hit);
      if (queryHit != null) {
        MtasSpanQuery queryHitRewritten = queryHit.rewrite(searcher);
        SpanWeight weight = queryHitRewritten.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, boost);
        Spans spans = weight.getSpans(lrc, SpanWeight.Postings.POSITIONS);
        if (spans != null) {
          list.put(hit, spans);
        }
      }
    }
    return list;
  }

  /**
   * Creates the query from group hit.
   *
   * @param prefixes
   *          the prefixes
   * @param field
   *          the field
   * @param hit
   *          the hit
   * @return the mtas span query
   */
  private static MtasSpanQuery createQueryFromGroupHit(Set<String> prefixes, String field, GroupHit hit) {
    // initial check
    if (prefixes == null || field == null || hit == null) {
      return null;
    } else {
      MtasSpanQuery query = null;
      // check for missing
      if (hit.getMissingLeft() != null && hit.getMissingLeft().length > 0) {
        for (int i = 0; i < hit.getMissingLeft().length; i++) {
          if (hit.getMissingLeft()[i].size() != hit.getUnknownLeft()[i].size()) {
            return null;
          }
        }
      }
      if (hit.getMissingHit() != null && hit.getMissingHit().length > 0) {
        for (int i = 0; i < hit.getMissingHit().length; i++) {
          if (hit.getMissingHit()[i].size() != hit.getUnknownHit()[i].size()) {
            return null;
          }
        }
      }
      if (hit.getMissingRight() != null && hit.getMissingRight().length > 0) {
        for (int i = 0; i < hit.getMissingRight().length; i++) {
          if (hit.getMissingRight()[i].size() != hit.getUnknownRight()[i].size()) {
            return null;
          }
        }
      }
      MtasSpanQuery hitQuery = createSubQueryFromGroupHit(hit.getDataHit(), false, field);
      if (hitQuery != null) {
        query = hitQuery;
        MtasSpanQuery leftHitQuery = createSubQueryFromGroupHit(hit.getDataLeft(), true, field);
        MtasSpanQuery rightHitQuery = createSubQueryFromGroupHit(hit.getDataRight(), false, field);
        if (leftHitQuery != null) {
          query = new MtasSpanPrecededByQuery(query, leftHitQuery);
        }
        if (rightHitQuery != null) {
          query = new MtasSpanFollowedByQuery(query, rightHitQuery);
        }
      }
      return query;
    }
  }

  /**
   * Creates the sub query from group hit.
   *
   * @param subHit
   *          the sub hit
   * @param reverse
   *          the reverse
   * @param field
   *          the field
   * @return the mtas span query
   */
  private static MtasSpanQuery createSubQueryFromGroupHit(List<String>[] subHit, boolean reverse, String field) {
    MtasSpanQuery query = null;
    if (subHit != null && subHit.length > 0) {
      List<MtasSpanSequenceItem> items = new ArrayList<>();
      List<String> subHitItem;
      for (int i = 0; i < subHit.length; i++) {
        MtasSpanQuery item = null;
        if (reverse) {
          subHitItem = subHit[(subHit.length - i - 1)];
        } else {
          subHitItem = subHit[i];
        }
        if (subHitItem.isEmpty()) {
          item = new MtasSpanMatchAllQuery(field);
        } else if (subHitItem.size() == 1) {
          Term term = new Term(field, subHitItem.get(0));
          item = new MtasSpanTermQuery(term);
        } else {
          MtasSpanQuery[] subList = new MtasSpanQuery[subHitItem.size()];
          for (int j = 0; j < subHitItem.size(); j++) {
            Term term = new Term(field, subHitItem.get(j));
            subList[j] = new MtasSpanTermQuery(term);
          }
          item = new MtasSpanAndQuery(subList);
        }
        items.add(new MtasSpanSequenceItem(item, false));
      }
      query = new MtasSpanSequenceQuery(items, null, null);
    }
    return query;
  }

  /**
   * Compute positions.
   *
   * @param mtasCodecInfo
   *          the mtas codec info
   * @param r
   *          the r
   * @param lrc
   *          the lrc
   * @param field
   *          the field
   * @param docSet
   *          the doc set
   * @return the map
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static Map<Integer, Integer> computePositions(CodecInfo mtasCodecInfo, LeafReader r, LeafReaderContext lrc,
      String field, List<Integer> docSet) throws IOException {
    HashMap<Integer, Integer> positionsData;
    if (mtasCodecInfo != null) {
      // for relatively small numbers, compute only what is needed
      if (docSet.size() < Math.log(r.maxDoc())) {
        positionsData = new HashMap<>();
        for (int docId : docSet) {
          positionsData.put(docId, mtasCodecInfo.getNumberOfPositions(field, (docId - lrc.docBase)));
        }
        // compute everything, only use what is needed
      } else {
        positionsData = mtasCodecInfo.getAllNumberOfPositions(field, lrc.docBase);
        for (int docId : docSet) {
          if (!positionsData.containsKey(docId)) {
            positionsData.put(docId, 0);
          }
        }
      }
    } else {
      positionsData = new HashMap<>();
      for (int docId : docSet) {
        positionsData.put(docId, 0);
      }
    }
    return positionsData;
  }

  /**
   * Compute arguments.
   *
   * @param spansNumberData
   *          the spans number data
   * @param queries
   *          the queries
   * @param docSet
   *          the doc set
   * @return the map
   */
  private static Map<Integer, long[]> computeArguments(Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData,
                                                       List<MtasSpanQuery> queries, Integer[] docSet) {
    Map<Integer, long[]> args = new HashMap<>();
    for (int q = 0; q < queries.size(); q++) {
      Map<Integer, Integer> tmpData = spansNumberData.get(queries.get(q));
      long[] tmpList = null;
      for (int docId : docSet) {
        if (tmpData != null && tmpData.containsKey(docId)) {
          if (!args.containsKey(docId)) {
            tmpList = new long[queries.size()];
          } else {
            tmpList = args.get(docId);
          }
          tmpList[q] = tmpData.get(docId);
          args.put(docId, tmpList);
        } else if (!args.containsKey(docId)) {
          tmpList = new long[queries.size()];
          args.put(docId, tmpList);
        }
      }
    }
    return args;
  }

  /**
   * Intersected doc list.
   *
   * @param facetDocList
   *          the facet doc list
   * @param docSet
   *          the doc set
   * @return the integer[]
   */
  private static Integer[] intersectedDocList(int[] facetDocList, Integer[] docSet) {
    if (facetDocList != null && docSet != null) {
      Integer[] c = new Integer[Math.min(facetDocList.length, docSet.length)];
      int ai = 0;
      int bi = 0;
      int ci = 0;
      while (ai < facetDocList.length && bi < docSet.length) {
        if (facetDocList[ai] < docSet[bi]) {
          ai++;
        } else if (facetDocList[ai] > docSet[bi]) {
          bi++;
        } else {
          if (ci == 0 || facetDocList[ai] != c[ci - 1]) {
            c[ci++] = facetDocList[ai];
          }
          ai++;
          bi++;
        }
      }
      return Arrays.copyOfRange(c, 0, ci);
    }
    return new Integer[] {};
  }

  /**
   * Creates the positions.
   *
   * @param statsPositionList
   *          the stats position list
   * @param positionsData
   *          the positions data
   * @param docSet
   *          the doc set
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createPositions(List<ComponentPosition> statsPositionList, Map<Integer, Integer> positionsData,
      List<Integer> docSet) throws IOException {
    if (statsPositionList != null) {
      for (ComponentPosition position : statsPositionList) {

        Integer tmpValue;
        long[] values = new long[docSet.size()];
        int value;
        int number = 0;
        for (int docId : docSet) {
          tmpValue = positionsData.get(docId);
          value = tmpValue == null ? 0 : tmpValue.intValue();
          if (((position.minimumLong == null) || (value >= position.minimumLong))
              && ((position.maximumLong == null) || (value <= position.maximumLong))) {
            values[number] = value;
            number++;
          }
        }
        try {
            position.dataCollector.getLock().lock();
            position.dataCollector.initNewList(1);
            if (number > 0) {
              position.dataCollector.add(values, number);
            }
            position.dataCollector.closeNewList();
        } finally {
            position.dataCollector.getLock().unlock();
        }
      }
    }
  }

  /**
   * Creates the tokens.
   *
   * @param statsTokenList
   *          the stats token list
   * @param tokensData
   *          the tokens data
   * @param docSet
   *          the doc set
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createTokens(List<ComponentToken> statsTokenList, Map<Integer, Integer> tokensData,
      List<Integer> docSet) throws IOException {
    if (statsTokenList != null) {
      for (ComponentToken token : statsTokenList) {

        Integer tmpValue;
        long[] values = new long[docSet.size()];
        int value;
        int number = 0;
        if (tokensData != null) {
          for (int docId : docSet) {
            tmpValue = tokensData.get(docId);
            value = tmpValue == null ? 0 : tmpValue.intValue();
            if (((token.minimumLong == null) || (value >= token.minimumLong))
                && ((token.maximumLong == null) || (value <= token.maximumLong))) {
              values[number] = value;
              number++;
            }
          }
        }
          try {
              token.dataCollector.getLock().lock();
              token.dataCollector.initNewList(1);
              if (number > 0) {
                  token.dataCollector.add(values, number);
              }
              token.dataCollector.closeNewList();
          } finally {
              token.dataCollector.getLock().unlock();
          }
      }
    }
  }

  /**
   * Creates the stats.
   *
   * @param statsSpanList
   *          the stats span list
   * @param positionsData
   *          the positions data
   * @param spansNumberData
   *          the spans number data
   * @param docSet
   *          the doc set
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createStats(List<ComponentSpan> statsSpanList, Map<Integer, Integer> positionsData,
      Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData, Integer[] docSet) throws IOException {
    if (statsSpanList != null) {
      for (ComponentSpan span : statsSpanList) {
        if (span.parser.needArgumentsNumber() > span.getQueries().size()) {
          throw new IOException(
              "function " + span.parser + " expects (at least) " + span.parser.needArgumentsNumber() + " queries");
        }
        // collect
        Map<Integer, long[]> args = computeArguments(spansNumberData, span.getQueries(), docSet);
        if (span.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
          // try to call functionParser as little as possible
          if (span.statsType.equals(CodecUtil.STATS_BASIC) && (span.minimumLong == null) && (span.maximumLong == null)
              && (span.functions == null
                  || (span.functionBasic() && span.functionSumRule() && !span.functionNeedPositions()))) {
            // initialise
            int length = span.parser.needArgumentsNumber();
            long[] valueQSum = new long[length];
            long[] valueDSum = new long[length];
            // collect
            if (docSet.length > 0) {
              long[] tmpArgs;
              long valuePositions = 0;
              for (int docId : docSet) {
                tmpArgs = args.get(docId);
                valuePositions += (positionsData == null) ? 0 : positionsData.get(docId);
                if (tmpArgs != null) {
                  for (int i = 0; i < length; i++) {
                    if(tmpArgs[i]>0) {
                      valueQSum[i] += tmpArgs[i];
                      valueDSum[i] += 1;
                    }
                  }
                }
              }
              long valueLong = span.parser.getValueLong(valueQSum, valueDSum, valuePositions, docSet.length);
            try {
              span.dataCollector.getLock().lock();
              span.dataCollector.initNewList(1);
              try {
                span.dataCollector.add(valueLong, docSet.length);
              } catch (IOException e) {
                log.debug("Error", e);
                span.dataCollector.error(e.getMessage(), 1);
              }
              if (span.functions != null) {
                for (SubComponentFunction function : span.functions) {
                  function.dataCollector.initNewList(1);
                  if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                    try {
                      long functionValueLong = function.parserFunction.getValueLong(valueQSum, valueDSum, valuePositions, docSet.length);
                      function.dataCollector.add(functionValueLong, docSet.length);
                    } catch (IOException e) {
                      log.debug("Error", e);
                      function.dataCollector.error(e.getMessage(), 1);
                    }
                  } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                    try {
                      double valueDouble = function.parserFunction.getValueDouble(valueQSum, valueDSum, valuePositions, docSet.length);
                      function.dataCollector.add(valueDouble, docSet.length);
                    } catch (IOException e) {
                      log.debug("Error", e);
                      function.dataCollector.error(e.getMessage(), 1);
                    }
                  } else {
                    throw new IOException("can't handle function dataType " + function.dataType);
                  }
                  function.dataCollector.closeNewList();
                }
              }
              span.dataCollector.closeNewList();
            } finally {
                span.dataCollector.getLock().unlock();
            }
            }
          } else {
            // collect
            if (docSet.length > 0) {
              int number = 0;
              double valueDouble;
              long[] values = new long[docSet.length];
              long[][] functionValuesLong = null;
              double[][] functionValuesDouble = null;
//              span.dataCollector.initNewList(1);
              if (span.functions != null) {
                functionValuesLong = new long[span.functions.size()][];
                functionValuesDouble = new double[span.functions.size()][];
                for (int i = 0; i < span.functions.size(); i++) {
                  SubComponentFunction function = span.functions.get(i);
                  if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                    functionValuesLong[i] = new long[docSet.length];
                    functionValuesDouble[i] = null;
                  } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                    functionValuesLong[i] = null;
                    functionValuesDouble[i] = new double[docSet.length];
                  }
//                  function.dataCollector.initNewList(1);
                }
              }
              for (int docId : docSet) {
                int positions;
                if (positionsData == null) {
                  positions = 0;
                } else {
                  positions = (positionsData.get(docId) == null ? 0 : positionsData.get(docId));
                }
                long[] argsD = args.get(docId).clone();
                for(int k=0; k<argsD.length; k++) {
                  argsD[k] = (argsD[k]>0)?1:0;
                }
                long valueLong = span.parser.getValueLong(args.get(docId), argsD, positions, 1);
                if (((span.minimumLong == null) || (valueLong >= span.minimumLong))
                    && ((span.maximumLong == null) || (valueLong <= span.maximumLong))) {
                  values[number] = valueLong;
                  if (span.functions != null) {
                    for (int i = 0; i < span.functions.size(); i++) {
                      SubComponentFunction function = span.functions.get(i);
                      try {
                        if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                          valueLong = function.parserFunction.getValueLong(args.get(docId), argsD, positions, 1);
                          functionValuesLong[i][number] = valueLong;
                        } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                          valueDouble = function.parserFunction.getValueDouble(args.get(docId), argsD, positions, 1);
                          functionValuesDouble[i][number] = valueDouble;
                        }
                      } catch (IOException e) {
                        log.debug("Error", e);
                        function.dataCollector.error(e.getMessage(), 1);
                      }
                    }
                  }
                  number++;
                }
              }
              try {
                  span.dataCollector.getLock().lock();
                  span.dataCollector.initNewList(1);
                  if (number > 0) {
                    span.dataCollector.add(values, number);
                    if (span.functions != null) {
                      for (int i = 0; i < span.functions.size(); i++) {
                        SubComponentFunction function = span.functions.get(i);
                        function.dataCollector.initNewList(1);
                        if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                          function.dataCollector.add(functionValuesLong[i], number);
                        } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                          function.dataCollector.add(functionValuesDouble[i], number);
                        }
                      }
                    }
                  }
                  span.dataCollector.closeNewList();
                  if (span.functions != null) {
                    for (SubComponentFunction function : span.functions) {
                      function.dataCollector.closeNewList();
                    }
                  }
              } finally {
                span.dataCollector.getLock().unlock();
              }
            }
          }
        } else {
          throw new IOException("unexpected dataType " + span.dataType);
        }
      }
    }
  }

  /**
   * Creates the list.
   *
   * @param listList
   *          the list list
   * @param spansNumberData
   *          the spans number data
   * @param spansMatchData
   *          the spans match data
   * @param docSet
   *          the doc set
   * @param field
   *          the field
   * @param docBase
   *          the doc base
   * @param uniqueKeyField
   *          the unique key field
   * @param mtasCodecInfo
   *          the mtas codec info
   * @param searcher
   *          the searcher
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createList(List<ComponentList> listList,
      Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData,
      Map<MtasSpanQuery, Map<Integer, List<Match>>> spansMatchData, List<Integer> docSet, String field, int docBase,
      String uniqueKeyField, CodecInfo mtasCodecInfo, IndexSearcher searcher) throws IOException {
    if (listList != null) {
      for (ComponentList list : listList) {
        // collect not only stats
        if (list.getNumber() > 0) {
          Map<Integer, List<Match>> matchData = spansMatchData.get(list.getSpanQuery());
          Map<Integer, Integer> numberData = spansNumberData.get(list.getSpanQuery());
          List<Match> matchList;
          Integer matchNumber;
          for (int docId : docSet) {
            if (matchData != null && (matchList = matchData.get(docId)) != null) {
              if (list.getPosition() < (list.getStart() + list.getNumber())) {
                boolean getDoc = false;
                Match m;
                if (list.getOutput().equals(ComponentList.LIST_OUTPUT_HIT)) {
                  for (int i = 0; i < matchList.size(); i++) {
                    if ((list.getPosition() >= list.getStart()) && (list.getPosition() < (list.getStart() + list.getNumber()))) {
                      m = matchList.get(i);
                      getDoc = true;
                      int startPosition = m.startPosition();
                      int endPosition = m.endPosition() - 1;
                      if (mtasCodecInfo != null) {
                        List<MtasTreeHit<String>> terms = mtasCodecInfo.getPositionedTermsByPrefixesAndPositionRange(
                                field, (docId - docBase), list.getPrefixes(), startPosition - list.getLeft(), endPosition + list.getRight());
                        // construct hit
                        Map<Integer, List<String>> kwicListHits = new HashMap<>();
                        for (int position = Math.max(0, startPosition - list.getLeft()); position <= (endPosition
                                + list.getRight()); position++) {
                          kwicListHits.put(position, new ArrayList<String>());
                        }
                        List<String> termList;
                        for (MtasTreeHit<String> term : terms) {
                          for (int position = Math.max((startPosition - list.getLeft()), term.startPosition); position <= Math
                                  .min((endPosition + list.getRight()), term.endPosition); position++) {
                            termList = kwicListHits.get(position);
                            termList.add(term.data);
                          }
                        }
                        list.addHit(new ListHit(docId, i, m, kwicListHits));
                      }
                    }
                    list.incrementPosition();
                  }
                } else if (list.getOutput().equals(ComponentList.LIST_OUTPUT_TOKEN)) {
                  for (int i = 0; i < matchList.size(); i++) {
                    if ((list.getPosition() >= list.getStart()) && (list.getPosition() < (list.getStart() + list.getNumber()))) {
                      m = matchList.get(i);
                      getDoc = true;
                      int startPosition = m.startPosition();
                      int endPosition = m.endPosition() - 1;
                      if (mtasCodecInfo != null) {
                        List<MtasTokenString> tokens;
                        tokens = mtasCodecInfo.getPrefixFilteredObjectsByPositions(field, (docId - docBase),
                                list.getPrefixes(), startPosition - list.getLeft(), endPosition + list.getRight());
                        list.addToken(new ListToken(docId, i, m, tokens));
                      }
                    }
                    list.incrementPosition();
                  }
                }
                if (getDoc) {
                  // get unique id
                  Document doc = searcher.storedFields().document(docId, Set.of(uniqueKeyField));
                  IndexableField indxfld = doc.getField(uniqueKeyField);
                  if (indxfld != null) {
                    list.getUniqueKey().put(docId, indxfld.stringValue());
                  }
                  // get additional fields
                  if (!list.getFieldNames().isEmpty()) {
                    Map<String, Object> docFieldValues = list.getFieldValues().compute(docId,
                            (key, docFieldValuesMap) -> docFieldValuesMap != null ? docFieldValuesMap : new HashMap<>());

                    IndexableField[] indxflds;
                    Object fieldValue;
                    List<Object> fieldValues;
                    String finalFieldName, indexFieldName;

                    Matcher matcherAlias;
                    for (String fieldName : list.getFieldNames()) {
                      if (fieldName.equals("*")) {
                        finalFieldName = null;
                        indexFieldName = null;
                        List<IndexableField> indxfldsList = doc.getFields();
                        indxflds = (IndexableField[]) indxfldsList.toArray(new IndexableField[0]);
                      } else {
                        // implement aliases
                        matcherAlias = patternAlias.matcher(fieldName);
                        if (matcherAlias.find()) {
                          finalFieldName = matcherAlias.group(1);
                          indexFieldName = matcherAlias.group(2);
                        } else {
                          finalFieldName = fieldName;
                          indexFieldName = fieldName;
                        }
                        // get values
                        indxflds = doc.getFields(indexFieldName);
                      }
                      // store single or multiple values
                      if (indxflds.length == 1) {
                        // handle wildcard
                        if (indexFieldName == null) {
                          finalFieldName = indxflds[0].name();
                        }
                        if ((fieldValue = indxflds[0].numericValue()) != null) {
                          docFieldValues.put(finalFieldName, fieldValue);
                        } else if ((fieldValue = indxflds[0].stringValue()) != null) {
                          docFieldValues.put(finalFieldName, fieldValue);
                        }
                      } else if (indxflds.length > 1) {
                        fieldValues = new ArrayList<>();
                          for (IndexableField indexableField : indxflds) {
                              // handle wildcard
                              if (indexFieldName == null) {
                                  if (finalFieldName == null) {
                                      finalFieldName = indxflds[0].name();
                                  } else if (!finalFieldName.equals(indexableField.name())) {
                                      if (!fieldValues.isEmpty()) {
                                          if (fieldValues.size() == 1) {
                                              docFieldValues.put(finalFieldName, fieldValues.get(0));
                                          } else {
                                              docFieldValues.put(finalFieldName, fieldValues);
                                          }
                                          fieldValues = new ArrayList<>();
                                      }
                                      finalFieldName = indexableField.name();
                                  }
                              }
                              if ((fieldValue = indexableField.numericValue()) != null) {
                                  fieldValues.add(fieldValue);
                              } else if ((fieldValue = indexableField.stringValue()) != null) {
                                  fieldValues.add(fieldValue);
                              }
                          }
                        if (!fieldValues.isEmpty()) {
                          if (fieldValues.size() == 1) {
                            docFieldValues.put(finalFieldName, fieldValues.get(0));
                          } else {
                            docFieldValues.put(finalFieldName, fieldValues);
                          }
                        }
                      }
                    }
                  }
                  // get other doc info
                  list.getSubTotal().put(docId, matchList.size());
                  if (mtasCodecInfo != null) {
                    IndexDoc mDoc = mtasCodecInfo.getDoc(field, (docId - docBase));
                    if (mDoc != null) {
                      list.getMinPosition().put(docId, mDoc.minPosition);
                      list.getMaxPosition().put(docId, mDoc.maxPosition);
                    }
                  }
                }
              } else {
                list.accumulatePosition(matchList.size());
              }
            } else if (numberData != null && (matchNumber = numberData.get(docId)) != null) {
              list.accumulatePosition(matchNumber);
            }
          }
          list.overwriteTotal(list.getPosition());
        } else {
          Map<Integer, Integer> data = spansNumberData.get(list.getSpanQuery());
          if (data != null) {
            for (int docId : docSet) {
              Integer matchNumber = data.get(docId);
              if (matchNumber != null) {
                list.accumulatePosition(matchNumber);
              }
            }
            list.overwriteTotal(list.getPosition());
          }
        }
      }
    }
  }

  /**
   * Creates the group.
   *
   * @param groupList
   *          the group list
   * @param spansMatchData
   *          the spans match data
   * @param docSet
   *          the doc set
   * @param fieldInfo
   *          the field info
   * @param field
   *          the field
   * @param docBase
   *          the doc base
   * @param mtasCodecInfo
   *          the mtas codec info
   * @param searcher
   *          the searcher
   * @param lrc
   *          the lrc
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createGroup(List<ComponentGroup> groupList,
      Map<MtasSpanQuery, Map<Integer, List<Match>>> spansMatchData, List<Integer> docSet, FieldInfo fieldInfo,
      String field, int docBase, CodecInfo mtasCodecInfo, IndexSearcher searcher, LeafReaderContext lrc)
      throws IOException {

    if (mtasCodecInfo != null && groupList != null) {
      List<Match> matchList;
      Map<Integer, List<Match>> matchData;

      for (ComponentGroup group : groupList) {

        if (!group.getPrefixes().isEmpty()) {
          matchData = spansMatchData.get(group.getSpanQuery());
          Set<String> knownPrefixes = collectKnownPrefixes(fieldInfo);
          Set<String> intersectionPrefixes = collectIntersectionPrefixes(fieldInfo);
          boolean intersectionGroupPrefixes = intersectionPrefixes(group, intersectionPrefixes);
          boolean availablePrefixes = availablePrefixes(group, knownPrefixes);
          // sort match lists
          if (!intersectionGroupPrefixes) {
            for (Entry<Integer, List<Match>> entry : matchData.entrySet()) {
              sortMatchList(entry.getValue());
            }
          }
          // init


          Map<GroupHit, Long> occurencesSum = new HashMap<>();
          Map<GroupHit, Integer> occurencesN = new HashMap<>();
          Set<GroupHit> occurencesInCurrentDocument = new HashSet<>();

          if (!availablePrefixes) {
            HashMap<Integer, GroupHit> hits = new HashMap<>();
              for (int docId : docSet) {
                  occurencesInCurrentDocument.clear();
                  GroupHit hit;
                  GroupHit hitKey;
                  if (matchData != null && (matchList = matchData.get(docId)) != null && !matchList.isEmpty()) {
                      for (Match m : matchList) {
                          IntervalTreeNodeData<String> positionHit = createPositionHit(m, group);
                          int length = m.endPosition() - m.startPosition();
                          hitKey = null;
                          if (!hits.containsKey(length)) {
                              hit = new GroupHit(positionHit.list, positionHit.start, positionHit.end, positionHit.hitStart,
                                      positionHit.hitEnd, group, knownPrefixes);
                              hits.put(length, hit);
                          } else {
                              hit = hits.get(length);
                              hitKey = occurencesSum.containsKey(hit) ? hit : null;
//                              for (GroupHit hitKeyItem : occurencesSum.keySet()) {
//                                  if (hitKeyItem.equals(hit)) {
//                                      hitKey = hitKeyItem;
//                                      break;
//                                  }
//                              }
                          }
                          if (hitKey == null) {
                              occurencesSum.put(hit, 1L);
                              occurencesN.put(hit, 1);
                              occurencesInCurrentDocument.add(hit);
                          } else {
                              occurencesSum.put(hitKey, occurencesSum.get(hitKey) + 1);
                              if (!occurencesInCurrentDocument.contains(hitKey)) {
                                  if (occurencesN.containsKey(hitKey)) {
                                      occurencesN.put(hitKey, occurencesN.get(hitKey) + 1);
                                  } else {
                                      occurencesN.put(hitKey, 1);
                                  }
                                  occurencesInCurrentDocument.add(hitKey);
                              }
                          }
                      }
                  }
              }
          } else {
            int maximumNumberOfDocuments = 0;
            int boundaryMinimumNumberOfDocuments = 1;
            int boundaryMaximumNumberOfDocuments = 5;
            Set<GroupHit> administrationOccurrences = new HashSet<>();

            for (int docCounter = 0; docCounter < docSet.size(); docCounter++) {
              occurencesInCurrentDocument.clear();
              int docId = docSet.get(docCounter);
              if (matchData != null && (matchList = matchData.get(docId)) != null && !matchList.isEmpty()) {
                // loop over matches
                Iterator<Match> it = matchList.listIterator();
                ArrayList<IntervalTreeNodeData<String>> positionsHits = new ArrayList<>();
                while (it.hasNext()) {
                  Match m = it.next();
                  positionsHits.add(createPositionHit(m, group));
                }
                mtasCodecInfo.collectTermsByPrefixesForListOfHitPositions(field, (docId - docBase), group.getPrefixes(),
                    positionsHits);
                // administration
                for (IntervalTreeNodeData<String> positionHit : positionsHits) {
                  GroupHit hit = new GroupHit(positionHit.list, positionHit.start, positionHit.end,
                      positionHit.hitStart, positionHit.hitEnd, group, knownPrefixes);
                  GroupHit hitKey = occurencesSum.containsKey(hit) ? hit : null;
                  if (hitKey == null) {
                    occurencesSum.put(hit, 1L);
                    occurencesN.put(hit, 1);
                    occurencesInCurrentDocument.add(hit);
                  } else {
                    occurencesSum.put(hitKey, occurencesSum.get(hitKey) + 1);
                    if (!occurencesInCurrentDocument.contains(hitKey)) {
                      if (occurencesN.containsKey(hitKey)) {
                        occurencesN.put(hitKey, occurencesN.get(hitKey) + 1);
                      } else {
                        occurencesN.put(hitKey, 1);
                      }
                      occurencesInCurrentDocument.add(hitKey);
                    }
                  }
                }
                if (!intersectionGroupPrefixes) {
                  for (GroupHit groupHit : occurencesInCurrentDocument) {
                    int tmpNumber = occurencesN.get(groupHit);
                    maximumNumberOfDocuments = Math.max(maximumNumberOfDocuments, tmpNumber);
                    if (tmpNumber > boundaryMinimumNumberOfDocuments) {
                      administrationOccurrences.add(groupHit);
                    }
                  }
                  // collect spans
                  if (maximumNumberOfDocuments > boundaryMaximumNumberOfDocuments) {
                    if (!administrationOccurrences.isEmpty()) {
                      Map<GroupHit, Spans> list = collectSpansForOccurences(administrationOccurrences, knownPrefixes,
                          field, searcher, lrc);
                      if (!list.isEmpty()) {
                        collectGroupUsingSpans(list, docSet, docBase, docCounter, matchData, occurencesSum,
                            occurencesN);
                      }
                    }
                    administrationOccurrences.clear();
                    maximumNumberOfDocuments = 0;
                    boundaryMinimumNumberOfDocuments = (int) Math.ceil(boundaryMinimumNumberOfDocuments * 1.2);
                    boundaryMaximumNumberOfDocuments = (int) Math.ceil(boundaryMaximumNumberOfDocuments * 1.2);
                  }
                }
              }
            }
          }


            try {
                group.getDataCollector().getLock().lock();
                group.getDataCollector().setWithTotal();
                group.getDataCollector().initNewList(1);
                for (Entry<GroupHit, Long> entry : occurencesSum.entrySet()) {
                    group.getDataCollector().add(entry.getKey().toString(), entry.getValue(), occurencesN.get(entry.getKey()));
                }
                group.getDataCollector().closeNewList();
            } finally {
                group.getDataCollector().getLock().unlock();
            }

        }
      }
    }
  }

  /**
   * Available prefixes.
   *
   * @param group
   *          the group
   * @param knownPrefixes
   *          the known prefixes
   * @return true, if successful
   */
  private static boolean availablePrefixes(ComponentGroup group, Set<String> knownPrefixes) {
    if (knownPrefixes != null) {
      for (String prefix : group.getPrefixes()) {
        if (knownPrefixes.contains(prefix)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Intersection prefixes.
   *
   * @param group
   *          the group
   * @param intersectionPrefixes
   *          the intersection prefixes
   * @return true, if successful
   */
  private static boolean intersectionPrefixes(ComponentGroup group, Set<String> intersectionPrefixes) {
    if (intersectionPrefixes != null) {
      for (String prefix : group.getPrefixes()) {
        if (intersectionPrefixes.contains(prefix)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Creates the position hit.
   *
   * @param m
   *          the m
   * @param group
   *          the group
   * @return the interval tree node data
   */
  private static IntervalTreeNodeData<String> createPositionHit(Match m, ComponentGroup group) {
    Integer start = null;
    Integer end = null;
    if (group.hasHitsInside() || group.hasHitsInsideLeft() || group.hasHitsInsideRight()) {
      start = m.startPosition();
      end = m.endPosition() - 1;
    }
    if (group.hasHitLeft()) {
      start = m.startPosition();
      end = Math.max(m.startPosition() + group.hitLeftSize() - 1, m.endPosition() - 1);
    }
    if (group.hasHitRight()) {
      start = Math.min(m.endPosition() - group.hitRightSize(), m.startPosition());
      end = end == null ? (m.endPosition() - 1) : Math.max(end, (m.endPosition() - 1));
    }
    if (group.hasLeft()) {
      start = start == null ? m.startPosition() - group.leftSize()
          : Math.min(m.startPosition() - group.leftSize(), start);
      end = end == null ? m.startPosition() - 1 : Math.max(m.startPosition() - 1, end);
    }
    if (group.hasRight()) {
      start = start == null ? m.endPosition() : Math.min(m.endPosition(), start);
      end = end == null ? m.endPosition() + group.rightSize() - 1
          : Math.max(m.endPosition() + group.rightSize() - 1, end);
    }
    return new IntervalTreeNodeData<>(start, end, m.startPosition(), m.endPosition() - 1);
  }

  private static IntervalTreeNodeData<String> createPositionHit(Match m) {
    return new IntervalTreeNodeData<>(m.startPosition(), m.endPosition() - 1, m.startPosition(), m.endPosition() - 1);
  }

  /**
   * Collect group using spans.
   *
   * @param list
   *          the list
   * @param docSet
   *          the doc set
   * @param docBase
   *          the doc base
   * @param docCounter
   *          the doc counter
   * @param matchData
   *          the match data
   * @param occurencesSum
   *          the occurences sum
   * @param occurencesN
   *          the occurences N
   * @return the int
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static int collectGroupUsingSpans(Map<GroupHit, Spans> list, List<Integer> docSet, int docBase,
      int docCounter, Map<Integer, List<Match>> matchData, Map<GroupHit, Long> occurencesSum,
      Map<GroupHit, Integer> occurencesN) throws IOException {
    int total = 0;
    if (docCounter + 1 < docSet.size()) {
      // initialize
      int nextDocCounter = docCounter + 1;
      long[] subSum = new long[list.size()];
      int[] subN = new int[list.size()];
      boolean[] newNextDocs = new boolean[list.size()];
      boolean newNextDoc;
      int[] spansNextDoc = new int[list.size()];
      int nextDoc = 0;
      List<Match> matchList;
      GroupHit[] hitList = list.keySet().toArray(new GroupHit[0]);
      Spans[] spansList = new Spans[list.size()];
      boolean[] finishedSpansList = new boolean[list.size()];
      newNextDoc = true;
      // advance spans, find nextDoc
      for (int i = 0; i < hitList.length; i++) {
        newNextDocs[i] = true;
        spansList[i] = list.get(hitList[i]);
        spansNextDoc[i] = spansList[i].advance(docSet.get(nextDocCounter) - docBase);
        nextDoc = (i == 0) ? spansNextDoc[i] : Math.min(nextDoc, spansNextDoc[i]);
      }
      // loop over future documents
      while (nextDoc < DocIdSetIterator.NO_MORE_DOCS) {
        // find matches for next document
        while (nextDocCounter < docSet.size() && docSet.get(nextDocCounter) < (nextDoc + docBase)) {
          nextDocCounter++;
        }
        // finish, if no more docs in set
        if (nextDocCounter >= docSet.size()) {
          break;
        }
        // go to the matches
        if (docSet.get(nextDocCounter) == nextDoc + docBase) {
          matchList = matchData.get(nextDoc + docBase);
          if (matchList != null && !matchList.isEmpty()) {
            // initialize
            int currentMatchPosition = 0;
            int lastMatchStartPosition = matchList.get(matchList.size() - 1).startPosition();
            ArrayList<Match> newMatchList = new ArrayList<>(matchList.size());
            int currentSpanPosition = Spans.NO_MORE_POSITIONS;
            // check and initialize for each span
            for (int i = 0; i < spansList.length; i++) {
              if (spansList[i].docID() == nextDoc) {
                int tmpStartPosition = spansList[i].nextStartPosition();
                if (tmpStartPosition < Spans.NO_MORE_POSITIONS) {
                  finishedSpansList[i] = false;
                } else {
                  finishedSpansList[i] = true;
                }
                // compute position
                currentSpanPosition = (currentSpanPosition == Spans.NO_MORE_POSITIONS) ? tmpStartPosition
                    : Math.min(currentSpanPosition, tmpStartPosition);
              } else {
                finishedSpansList[i] = true;
              }
            }
            // loop over matches
            while (currentMatchPosition < matchList.size() && currentSpanPosition < Spans.NO_MORE_POSITIONS) {

              if (currentSpanPosition < matchList.get(currentMatchPosition).startPosition()) {
                // do nothing, match not reached
              } else if (currentSpanPosition > lastMatchStartPosition) {
                // finish, past last match
                break;
              } else {
                // advance matches
                while (currentMatchPosition < matchList.size()
                    && currentSpanPosition > matchList.get(currentMatchPosition).startPosition()) {
                  // store current match, not relevant
                  newMatchList.add(matchList.get(currentMatchPosition));
                  currentMatchPosition++;
                }
                // equal startPosition
                while (currentMatchPosition < matchList.size()
                    && currentSpanPosition == matchList.get(currentMatchPosition).startPosition()) {
                  // check for each span
                  for (int i = 0; i < spansList.length; i++) {
                    // equal start and end, therefore match
                    if (!finishedSpansList[i] && spansList[i].docID() == nextDoc
                        && spansList[i].startPosition() == matchList.get(currentMatchPosition).startPosition()
                        && spansList[i].endPosition() == matchList.get(currentMatchPosition).endPosition()) {
                      // administration
                      total++;
                      subSum[i]++;
                      if (newNextDocs[i]) {
                        subN[i]++;
                        newNextDocs[i] = false;
                        newNextDoc = false;
                      }
                    } else if (!finishedSpansList[i] && spansList[i].docID() == nextDoc
                        && spansList[i].startPosition() == matchList.get(currentMatchPosition).startPosition()) {
                      // no match, store
                      newMatchList.add(matchList.get(currentMatchPosition));
                    }
                  }
                  currentMatchPosition++;
                }
              }

              // advance spans
              if (currentMatchPosition < matchList.size()) {
                currentSpanPosition = Spans.NO_MORE_POSITIONS;
                for (int i = 0; i < spansList.length; i++) {
                  if (!finishedSpansList[i] && (spansList[i].docID() == nextDoc)) {
                    while (!finishedSpansList[i]
                        && spansList[i].startPosition() < matchList.get(currentMatchPosition).startPosition()) {
                      int tmpStartPosition = spansList[i].nextStartPosition();
                      if (tmpStartPosition == Spans.NO_MORE_POSITIONS) {
                        finishedSpansList[i] = true;
                      }
                    }
                    if (!finishedSpansList[i]) {
                      currentSpanPosition = (currentSpanPosition == Spans.NO_MORE_POSITIONS)
                          ? spansList[i].startPosition()
                          : Math.min(currentSpanPosition, spansList[i].startPosition());
                    }
                  } else {
                    finishedSpansList[i] = true;
                  }
                }
              }
            }
            if (!newNextDoc) {
              // add other matches
              while (currentMatchPosition < matchList.size()) {
                newMatchList.add(matchList.get(currentMatchPosition));
                currentMatchPosition++;
              }
              // update administration
              if (!newMatchList.isEmpty()) {
                matchData.put(nextDoc + docBase, newMatchList);
              } else {
                matchData.put(nextDoc + docBase, null);
              }
            }
          }
        }
        // advance to next document
        nextDocCounter++;
        newNextDoc = true;
        for (int i = 0; i < hitList.length; i++) {
          newNextDocs[i] = true;
        }
        // advance spans
        if (nextDocCounter < docSet.size()) {
          nextDoc = Spans.NO_MORE_DOCS;
          // advance spans
          for (int i = 0; i < hitList.length; i++) {
            if (spansNextDoc[i] < (docSet.get(nextDocCounter) - docBase)) {
              spansNextDoc[i] = spansList[i].advance(docSet.get(nextDocCounter) - docBase);
            }
            if (spansNextDoc[i] < Spans.NO_MORE_DOCS) {
              nextDoc = (nextDoc == Spans.NO_MORE_DOCS) ? spansNextDoc[i] : Math.min(nextDoc, spansNextDoc[i]);
            }
          }
        }
      }
      // update administration
      for (int i = 0; i < hitList.length; i++) {
        if (subSum[i] > 0 && (occurencesSum.containsKey(hitList[i]))) {
          occurencesSum.put(hitList[i], occurencesSum.get(hitList[i]) + subSum[i]);
          occurencesN.put(hitList[i], occurencesN.get(hitList[i]) + subN[i]);
        }
      }

    }
    return total;
  }

  /**
   * Sort match list.
   *
   * @param list
   *          the list
   */
  private static void sortMatchList(List<Match> list) {
    if (list != null) {
      // light sorting on start position
      list.sort(Comparator.comparingInt(Match::startPosition));
    }
  }

  /**
   * Creates the pages.
   *
   * @param pageList
   *          the page list
   * @param docList
   *          the doc list
   * @param fieldInfo
   *          the field info
   * @param field
   *          the field
   * @param docBase
   *          the doc base
   * @param uniqueKeyField
   *          the unique key field
   * @param mtasCodecInfo
   *          the mtas codec info
   * @param searcher
   *          the searcher
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createPages(List<ComponentPage> pageList, List<Integer> docList, FieldInfo fieldInfo,
      String field, int docBase, String uniqueKeyField, CodecInfo mtasCodecInfo, IndexSearcher searcher)
      throws IOException {
    if (pageList != null) {
      for (ComponentPage page : pageList) {
        // initialize
        for (int docId : docList) {
          // get unique id
          Document doc = searcher.storedFields().document(docId, Set.of(uniqueKeyField));
          IndexableField indxfld = doc.getField(uniqueKeyField);
          // get other doc info
          if (indxfld != null) {
            page.uniqueKey.put(docId, indxfld.stringValue());
          }
          if (mtasCodecInfo != null) {
            IndexDoc mDoc = mtasCodecInfo.getDoc(field, (docId - docBase));
            if (mDoc != null) {
              page.minPosition.put(docId, mDoc.minPosition);
              page.maxPosition.put(docId, mDoc.maxPosition);
              // get prefixes
              String singlePositionPrefixes = fieldInfo
                      .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SINGLE_POSITION);
              String multiplePositionPrefixes = fieldInfo
                      .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_MULTIPLE_POSITION);
              String setPositionPrefixes = fieldInfo
                      .getAttribute(MtasCodecPostingsFormat.MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SET_POSITION);
              // collect tokens
              List<MtasTokenString> tokens;
              List<String> allPrefixes;
              // words
              if (singlePositionPrefixes != null) {
                allPrefixes = new ArrayList<>(
                        Arrays.asList(singlePositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER))));
                if (!page.prefixes.isEmpty()) {
                  allPrefixes.retainAll(page.prefixes);
                }
                if (allPrefixes.size() > 0) {
                  Map<Integer, PageWordData> wordList = new HashMap<>();
                  PageWordData wordData;
                  tokens = mtasCodecInfo.getPrefixFilteredObjectsByPositions(field, (docId - docBase), allPrefixes,
                          Math.max(mDoc.minPosition, page.start), Math.min(mDoc.maxPosition, page.end));
                  for (MtasTokenString token : tokens) {
                    if (wordList.containsKey(token.getPositionStart())) {
                      wordData = wordList.get(token.getPositionStart());
                    } else {
                      wordData = new PageWordData();
                      wordList.put(token.getPositionStart(), wordData);
                    }
                    wordData.add(token);
                  }
                  page.wordList.put(docId, wordList);
                }
              }
              // ranges
              if (multiplePositionPrefixes != null) {
                allPrefixes = new ArrayList<>(
                        Arrays.asList(multiplePositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER))));
                if (setPositionPrefixes != null) {
                  allPrefixes.removeAll(Arrays.asList(setPositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER))));
                }
                if (!page.prefixes.isEmpty()) {
                  allPrefixes.retainAll(page.prefixes);
                }
                if (allPrefixes.size() > 0) {
                  Map<Integer, PageRangeData> rangeList = new HashMap<>();
                  PageRangeData rangeData;
                  tokens = mtasCodecInfo.getPrefixFilteredObjectsByPositions(field, (docId - docBase), allPrefixes,
                          Math.max(mDoc.minPosition, page.start), Math.min(mDoc.maxPosition, page.end));
                  for (MtasTokenString token : tokens) {
                    if (rangeList.containsKey(token.getPositionStart())) {
                      rangeData = rangeList.get(token.getPositionStart());
                    } else {
                      rangeData = new PageRangeData();
                      rangeList.put(token.getPositionStart(), rangeData);
                    }
                    rangeData.add(token);
                  }
                  page.rangeList.put(docId, rangeList);
                }
              }
              // sets
              if (setPositionPrefixes != null) {
                allPrefixes = new ArrayList<>(
                        Arrays.asList(setPositionPrefixes.split(Pattern.quote(MtasToken.DELIMITER))));
                if (!page.prefixes.isEmpty()) {
                  allPrefixes.retainAll(page.prefixes);
                }
                if (allPrefixes.size() > 0) {
                  Map<Integer, PageSetData> setList = new HashMap<>();
                  PageSetData setData;
                  tokens = mtasCodecInfo.getPrefixFilteredObjectsByPositions(field, (docId - docBase), allPrefixes,
                          Math.max(mDoc.minPosition, page.start), Math.min(mDoc.maxPosition, page.end));
                  for (MtasTokenString token : tokens) {
                    if (setList.containsKey(token.getPositionStart())) {
                      setData = setList.get(token.getPositionStart());
                    } else {
                      setData = new PageSetData();
                      setList.put(token.getPositionStart(), setData);
                    }
                    setData.add(token);
                  }
                  page.setList.put(docId, setList);
                }
              }
            }

          }
        }
      }
    }
  }

  private static void createIndexes(List<ComponentIndex> indexList,
      Map<MtasSpanQuery, Map<Integer, List<Match>>> spansMatchData, List<Integer> docList,
      String field, int docBase, String uniqueKeyField, CodecInfo mtasCodecInfo, IndexSearcher searcher)
      throws IOException {
    if (indexList != null) {
      for (ComponentIndex index : indexList) {
        Map<Integer, List<Match>> matchData = spansMatchData.get(index.query);
        Map<Integer, List<Match>> blockMatchData = index.blockQuery != null ? spansMatchData.get(index.blockQuery)
            : null;
        List<Match> matchList, blockMatchList;
        // initialize
        for (int docId : docList) {
          // get unique id
          Document doc = searcher.storedFields().document(docId, Set.of(uniqueKeyField));
          IndexableField indxfld = doc.getField(uniqueKeyField);
          // get other doc info
          if (indxfld != null) {
            index.uniqueKey.put(docId, indxfld.stringValue());
          }
          if (mtasCodecInfo != null) {
            IndexDoc mDoc = mtasCodecInfo.getDoc(field, (docId - docBase));
            if (mDoc != null) {
              IntervalTree intervalTree = new IntervalTree();
              index.minPosition.put(docId, mDoc.minPosition);
              index.maxPosition.put(docId, mDoc.maxPosition);
              List<IndexItem> indexItems = new ArrayList<>();
              if (index.blockQuery != null) {
                index.indexItems.put(docId, indexItems);
                if (blockMatchData != null && (blockMatchList = blockMatchData.get(docId)) != null) {
                  for (Match m : blockMatchList) {
                    int start = m.startPosition();
                    int end = m.endPosition() - 1;
                    IndexItem indexItem = new IndexItem(start, end, null);
                    intervalTree.insertNode(new IntervalTreeItem(indexItem));
                    indexItems.add(indexItem);
                  }
                }
              } else {
                int blockSize = 0;
                if (index.blockSize != null && index.blockSize > 0) {
                  blockSize = index.blockSize;
                } else if (index.blockNumber != null && index.blockNumber > 0) {
                  blockSize = (int) Math.max(1,
                          Math.ceil(((1.0 + mDoc.maxPosition - mDoc.minPosition) / index.blockNumber)));
                } else {
                  // should not happen
                  throw new IOException("No blockSize or blockNumber defined");
                }
                index.indexItems.put(docId, indexItems);
                // define intervals
                for (int startPosition = mDoc.minPosition; startPosition <= mDoc.maxPosition; startPosition += blockSize) {
                  int start = startPosition;
                  int end = Math.min(mDoc.maxPosition, startPosition + blockSize - 1);
                  IndexItem indexItem = new IndexItem(start, end, null);
                  intervalTree.insertNode(new IntervalTreeItem(indexItem));
                  indexItems.add(indexItem);
                }
              }

              // update these intervals
              if (matchData != null && (matchList = matchData.get(docId)) != null) {
                ArrayList<IntervalTreeNodeData<String>> positionsHits = new ArrayList<>();
                for (Match m : matchList) {
                  positionsHits.add(createPositionHit(m));
                  intervalTree.updateInterval(m.startPosition(), (m.endPosition() - 1), index.match);
                }
                if (!index.listPrefixes.isEmpty()) {
                  mtasCodecInfo.collectTermsByPrefixesForListOfHitPositions(field, (docId - docBase), index.listPrefixes,
                          positionsHits);
                  for (IntervalTreeNodeData<String> positionHit : positionsHits) {
                    intervalTree.updateInterval(positionHit.hitStart, positionHit.hitEnd, index.match, positionHit.list);
                  }
                }
              }
            }

          }
        }
      }
    }
  }

  private static void createHeatmaps(List<ComponentHeatmap> heatmapList, Map<Integer, Integer> positionsData,
      Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData, List<Integer> docSetOld, LeafReaderContext lrc) throws IOException {
    Integer[] docSet = docSetOld.toArray(new Integer[0]);
    if (heatmapList != null) {
      for (ComponentHeatmap heatmap : heatmapList) {
        if (heatmap.parser.needArgumentsNumber() > heatmap.queries.size()) {
          throw new IOException("function " + heatmap.parser + " expects (at least) "
              + heatmap.parser.needArgumentsNumber() + " queries");
        }
        // collect
        Map<Integer, long[]> args = computeArguments(spansNumberData, heatmap.queries, docSet);
        if (heatmap.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
          // collect
          if (docSet.length > 0) {
            int number = 0;
            int docPositions;
            long docValueLong;
            // double valueDouble;
            long[] values = new long[docSet.length];
            int[] docs = new int[docSet.length];
            int[] positions = new int[docSet.length];
            long[][] arguments = new long[docSet.length][];
            // long[][] functionValuesLong = null;
            // double[][] functionValuesDouble = null;
            // if (heatmap.hm.functions != null) {
            // functionValuesLong = new long[heatmap.hm.functions.size()][];
            // functionValuesDouble = new double[heatmap.hm.functions.size()][];
            // for (int i = 0; i < heatmap.hm.functions.size(); i++) {
            // SubComponentFunction function = heatmap.hm.functions.get(i);
            // if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
            // functionValuesLong[i] = new long[docSet.length];
            // functionValuesDouble[i] = null;
            // } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
            // functionValuesLong[i] = null;
            // functionValuesDouble[i] = new double[docSet.length];
            // }
            // }
            // }
            for (int docId : docSet) {
              if (positionsData == null) {
                docPositions = 0;
              } else {
                docPositions = (positionsData.get(docId) == null ? 0 : positionsData.get(docId));
              }
              docValueLong = heatmap.parser.getValueLong(args.get(docId), new long[] {1}, docPositions, 1);
              if (((heatmap.minimumLong == null) || (docValueLong >= heatmap.minimumLong))
                  && ((heatmap.maximumLong == null) || (docValueLong <= heatmap.maximumLong))) {
                values[number] = docValueLong;
                docs[number] = docId - lrc.docBase;
                positions[number] = docPositions;
                arguments[number] = args.get(docId);
                number++;
              }
            }
            if (number > 0) {
              HeatmapMtasCounter.calcValues(heatmap.strategy, lrc, heatmap, number, docs, values, arguments, positions);
            }
          }
        } else {
          throw new IOException("unexpected dataType " + heatmap.dataType);
        }
      }
    }
  }

  /**
   * Creates the document.
   *
   * @param documentList
   *          the document list
   * @param docList
   *          the doc list
   * @param uniqueKeyField
   *          the unique key field
   * @param searcher
   *          the searcher
   * @param t
   *          the t
   * @param lrc
   *          the lrc
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createDocument(List<ComponentDocument> documentList, List<Integer> docList, String uniqueKeyField,
      IndexSearcher searcher, Terms t, LeafReaderContext lrc) throws IOException {
    if (documentList != null) {
      SortedSet<String> listStatsItems = CodecUtil.createStatsItems("sum");
      String listStatsType = CodecUtil.createStatsType(listStatsItems, CodecUtil.STATS_TYPE_SUM, null);
      for (ComponentDocument document : documentList) {
        // initialize
        for (int docId : docList) {
          // get unique id
          Document doc = searcher.storedFields().document(docId, Set.of(uniqueKeyField));
          IndexableField indxfld = doc.getField(uniqueKeyField);
          // get other doc info
          if (indxfld != null) {
            document.getUniqueKey().put(docId, indxfld.stringValue());
            MtasDataCollector<?, ?> stats = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_DATA,
                document.getDataType(), document.getStatsType(), document.getStatsItems(), null, null, null, null, null, null);
            document.getStatsData().put(docId, stats);
            if (document.getStatsList() != null) {
              MtasDataCollector<?, ?> list;
              if (document.isListExpand()) {
                SortedSet<String>[] baseStatsItems = new SortedSet[] { listStatsItems };
                list = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_LIST, CodecUtil.DATA_TYPE_LONG,
                    listStatsType, listStatsItems, CodecUtil.STATS_TYPE_SUM, CodecUtil.SORT_DESC, 0,
                    document.getListNumber(), new String[] { DataCollector.COLLECTOR_TYPE_LIST },
                    new String[] { CodecUtil.DATA_TYPE_LONG }, new String[] { listStatsType },
                    Arrays.copyOfRange(baseStatsItems, 0, baseStatsItems.length),
                    new String[] { CodecUtil.STATS_TYPE_SUM }, new String[] { CodecUtil.SORT_DESC },
                    new Integer[] { 0 }, new Integer[] { document.getListExpandNumber() }, null, null);
              } else {
                list = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_LIST, CodecUtil.DATA_TYPE_LONG,
                    listStatsType, listStatsItems, CodecUtil.STATS_TYPE_SUM, CodecUtil.SORT_DESC, 0,
                    document.getListNumber(), null, null);
              }
              document.getStatsList().put(docId, list);
            }
          }
        }
      }
      // collect
      if (t != null) {
        BytesRef term;
        TermsEnum termsEnum;
        PostingsEnum postingsEnum = null;
        // loop over termvectors
        for (ComponentDocument document : documentList) {

          List<CompiledAutomaton> listAutomata;
          Map<String, Automaton> automatonMap;
          Map<String, ByteRunAutomaton> byteRunAutomatonMap;
          if (document.getList() == null) {
            automatonMap = null;
            byteRunAutomatonMap = null;
            listAutomata = List.of(getCompiledAutomaton(document));
          } else {
            automatonMap = MtasToken.createAutomatonMap(document.getPrefix(), new ArrayList<String>(document.getList()),
                    !document.isListRegexp());
            byteRunAutomatonMap = MtasToken.byteRunAutomatonMap(automatonMap);
            listAutomata = MtasToken.createAutomata(document.getPrefix(), document.getRegexp(), automatonMap);
          }
          List<ByteRunAutomaton> ignoreByteRunAutomatonList = null;
          if ((document.getIgnoreRegexp() != null) && (!document.getIgnoreRegexp().isEmpty())) {
            ignoreByteRunAutomatonList = new ArrayList<>();
            RegExp re = new RegExp(document.getPrefix() + MtasToken.DELIMITER + document.getIgnoreRegexp() + "\u0000*");
            ignoreByteRunAutomatonList.add(new ByteRunAutomaton(re.toAutomaton()));
          }
          if (document.getIgnoreList() != null) {
            if (ignoreByteRunAutomatonList == null) {
              ignoreByteRunAutomatonList = new ArrayList<>();
            }
            Map<String, Automaton> list = MtasToken.createAutomatonMap(document.getPrefix(),
                new ArrayList<String>(document.getIgnoreList()), !document.isIgnoreListRegexp());
            for (Automaton automaton : list.values()) {
              ignoreByteRunAutomatonList.add(new ByteRunAutomaton(automaton));
            }
          }

          for (CompiledAutomaton compiledAutomaton : listAutomata) {
            if (!compiledAutomaton.type.equals(CompiledAutomaton.AUTOMATON_TYPE.NONE)) {
              termsEnum = t.intersect(compiledAutomaton, null);
              // init
              int initBaseSize = Math.min((int) t.size(), 1000);
              int initListSize = document.getStatsList() != null ? Math.min(document.getStatsList().size(), initBaseSize)
                  : initBaseSize;
              HashSet<MtasDataCollector<?, ?>> initialised = new HashSet<>();
              for (int docId : docList) {
                document.getStatsData().get(docId).initNewList(1);
                initialised.add(document.getStatsData().get(docId));
                if (document.getStatsList() != null && !document.getStatsList().isEmpty()) {
                  document.getStatsList().get(docId).initNewList(initListSize);
                  initialised.add(document.getStatsList().get(docId));
                }
              }
              // fill
              int termDocId;
              boolean acceptedTerm;
              while ((term = termsEnum.next()) != null) {
                Iterator<Integer> docIterator = docList.iterator();
                postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.FREQS);
                termDocId = -1;
                acceptedTerm = true;
                if (ignoreByteRunAutomatonList != null) {
                  for (ByteRunAutomaton ignoreByteRunAutomaton : ignoreByteRunAutomatonList) {
                    if (ignoreByteRunAutomaton.run(term.bytes, term.offset, term.length)) {
                      acceptedTerm = false;
                      break;
                    }
                  }
                }
                if (acceptedTerm) {
                  while (docIterator.hasNext()) {
                    int segmentDocId = docIterator.next() - lrc.docBase;
                    if (segmentDocId >= termDocId && ((segmentDocId == termDocId)
                        || ((termDocId = postingsEnum.advance(segmentDocId)) == segmentDocId))) {
                      // register stats
                      document.getStatsData().get(segmentDocId + lrc.docBase).add(new long[] { postingsEnum.freq() }, 1);
                      // register list
                      if (document.getStatsList() != null) {
                        if (automatonMap != null) {
                          MtasDataCollector<?, ?> dataCollector;
                          MtasDataCollector<?, ?> subSataCollector;
                          for (Entry<String, ByteRunAutomaton> entry : byteRunAutomatonMap.entrySet()) {
                            ByteRunAutomaton bra = entry.getValue();
                            if (bra.run(term.bytes, term.offset, term.length)) {
                              dataCollector = document.getStatsList().get(segmentDocId + lrc.docBase);
                              subSataCollector = dataCollector.add(entry.getKey(), new long[] { postingsEnum.freq() },
                                  1);
                              if (document.isListExpand() && subSataCollector != null) {
                                if (!initialised.contains(subSataCollector)) {
                                  subSataCollector.initNewList(initBaseSize);
                                  initialised.add(subSataCollector);
                                }
                                subSataCollector.add(MtasToken.getPostfixFromValue(term),
                                    new long[] { postingsEnum.freq() }, 1);
                              }
                            }
                          }
                        } else {
                          document.getStatsList().get(segmentDocId + lrc.docBase).add(MtasToken.getPostfixFromValue(term),
                              new long[] { postingsEnum.freq() }, 1);
                        }
                      }
                    }
                  }
                }
              }
              // close
              for (MtasDataCollector<?, ?> item : initialised) {
                item.closeNewList();
              }
              initialised.clear();
            }
          }
        }
      }
    }
  }

    private static CompiledAutomaton getCompiledAutomaton(ComponentDocument document) {
        CompiledAutomaton compiledAutomaton;
        Automaton automaton;
        if ((document.getRegexp() == null) || (document.getRegexp().isEmpty())) {
          RegExp re = new RegExp(document.getPrefix() + MtasToken.DELIMITER + ".*");
          automaton = re.toAutomaton();
        } else {
          RegExp re = new RegExp(document.getPrefix() + MtasToken.DELIMITER + document.getRegexp() + "\u0000*");
          automaton = re.toAutomaton();
        }
        compiledAutomaton = new CompiledAutomaton(automaton);
        return compiledAutomaton;
    }

    /**
   * Creates the kwic.
   *
   * @param kwicList
   *          the kwic list
   * @param spansMatchData
   *          the spans match data
   * @param docList
   *          the doc list
   * @param field
   *          the field
   * @param docBase
   *          the doc base
   * @param uniqueKeyField
   *          the unique key field
   * @param mtasCodecInfo
   *          the mtas codec info
   * @param searcher
   *          the searcher
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createKwic(List<ComponentKwic> kwicList,
      Map<MtasSpanQuery, Map<Integer, List<Match>>> spansMatchData, List<Integer> docList, String field, int docBase,
      String uniqueKeyField, CodecInfo mtasCodecInfo, IndexSearcher searcher) throws IOException {
    if (kwicList != null) {
      for (ComponentKwic kwic : kwicList) {
        Map<Integer, List<Match>> matchData = spansMatchData.get(kwic.getQuery());
        List<Match> matchList;
        if (kwic.getOutput().equals(ComponentKwic.KWIC_OUTPUT_HIT)) {
          for (int docId : docList) {
            if (matchData != null && (matchList = matchData.get(docId)) != null) {
              // get unique id
              Document doc = searcher.storedFields().document(docId, Set.of(uniqueKeyField));
              IndexableField indxfld = doc.getField(uniqueKeyField);
              // get other doc info
              if (indxfld != null) {
                kwic.getUniqueKey().put(docId, indxfld.stringValue());
              }
              kwic.getSubTotal().put(docId, matchList.size());
              if (mtasCodecInfo != null) {
                IndexDoc mDoc = mtasCodecInfo.getDoc(field, (docId - docBase));
                if (mDoc != null) {
                  kwic.getMinPosition().put(docId, mDoc.minPosition);
                  kwic.getMaxPosition().put(docId, mDoc.maxPosition);
                }

              // kwiclist
              List<KwicHit> kwicItemList = new ArrayList<>();
              int number = 0;
              for (Match m : matchList) {
                if (kwic.getNumber() != null && number >= (kwic.getStart() + kwic.getNumber())) {
                  break;
                } else if (kwic.getPageStart() != null && kwic.getPageEnd() != null) {
                  if ((m.endPosition() - 1) < kwic.getPageStart() || m.startPosition() > kwic.getPageEnd()) {
                    continue;
                  }
                }
                if (number >= kwic.getStart()) {
                  int startPosition = m.startPosition();
                  int endPosition = m.endPosition() - 1;
                  List<MtasTreeHit<String>> terms = mtasCodecInfo.getPositionedTermsByPrefixesAndPositionRange(field,
                      (docId - docBase), kwic.getPrefixes(), Math.max(mDoc.minPosition, startPosition - kwic.getLeft()),
                      Math.min(mDoc.maxPosition, endPosition + kwic.getRight()));
                  // construct hit
                  Map<Integer, List<String>> kwicListHits = new HashMap<>();
                  for (int position = Math.max(mDoc.minPosition, startPosition - kwic.getLeft()); position <= Math
                      .min(mDoc.maxPosition, endPosition + kwic.getRight()); position++) {
                    kwicListHits.put(position, new ArrayList<String>());
                  }
                  List<String> termList;
                  for (MtasTreeHit<String> term : terms) {
                    for (int position = Math.max((startPosition - kwic.getLeft()), term.startPosition); position <= Math
                        .min((endPosition + kwic.getRight()), term.endPosition); position++) {
                      termList = kwicListHits.get(position);
                      termList.add(term.data);
                    }
                  }
                  kwicItemList.add(new KwicHit(m, kwicListHits));
                }
                number++;
              }
              kwic.getHits().put(docId, kwicItemList);
              }
            }
          }
        } else if (kwic.getOutput().equals(ComponentKwic.KWIC_OUTPUT_TOKEN)) {
          for (int docId : docList) {
            if (matchData != null && (matchList = matchData.get(docId)) != null) {
              // get unique id
              Document doc = searcher.storedFields().document(docId, Set.of(uniqueKeyField));
              // get other doc info
              IndexableField indxfld = doc.getField(uniqueKeyField);
              if (indxfld != null) {
                kwic.getUniqueKey().put(docId, indxfld.stringValue());
              }
              kwic.getSubTotal().put(docId, matchList.size());
              if (mtasCodecInfo != null) {
                IndexDoc mDoc = mtasCodecInfo.getDoc(field, (docId - docBase));
                if (mDoc != null) {
                  kwic.getMinPosition().put(docId, mDoc.minPosition);
                  kwic.getMaxPosition().put(docId, mDoc.maxPosition);
                  List<KwicToken> kwicItemList = new ArrayList<>();
                  int number = 0;
                  for (Match m : matchList) {
                    if (kwic.getNumber() != null && number >= (kwic.getStart() + kwic.getNumber())) {
                      break;
                    } else if (kwic.getPageStart() != null && kwic.getPageEnd() != null) {
                      if ((m.endPosition() - 1) < kwic.getPageStart() || m.startPosition() > kwic.getPageEnd()) {
                        continue;
                      }
                    }
                    if (number >= kwic.getStart()) {
                      int startPosition = m.startPosition();
                      int endPosition = m.endPosition() - 1;
                      List<MtasTokenString> tokens;
                      tokens = mtasCodecInfo.getPrefixFilteredObjectsByPositions(field, (docId - docBase), kwic.getPrefixes(),
                              Math.max(mDoc.minPosition, startPosition - kwic.getLeft()),
                              Math.min(mDoc.maxPosition, endPosition + kwic.getRight()));
                      kwicItemList.add(new KwicToken(m, tokens));
                    }
                    number++;
                  }
                  kwic.getTokens().put(docId, kwicItemList);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Creates the facet base.
   *
   * @param cf
   *          the cf
   * @param level
   *          the level
   * @param dataCollector
   *          the data collector
   * @param positionsData
   *          the positions data
   * @param spansNumberData
   *          the spans number data
   * @param facetData
   *          the facet data
   * @param docSet
   *          the doc set
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createFacetBase(ComponentFacet cf, int level, MtasDataCollector<?, ?> dataCollector,
      Map<Integer, Integer> positionsData, Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData,
      Map<String, SortedMap<String, int[]>> facetData, Integer[] docSet) throws IOException {
    for (MtasFunctionParserFunction function : cf.getBaseFunctionParserFunctions()[level]) {
      if (function.needArgumentsNumber() > cf.getSpanQueries().size()) {
        throw new IOException(
            "function " + function + " expects (at least) " + function.needArgumentsNumber() + " queries");
      }
    }
    Map<String, int[]> list = facetData.get(cf.getBaseFields()[level]);
    if (dataCollector != null) {

      dataCollector.initNewList(1);
      if (cf.getBaseFunctionList()[level] != null) {
        SubComponentFunction[] tmpList;
        if (!cf.getBaseFunctionList()[level].containsKey(dataCollector)) {
          tmpList = new SubComponentFunction[cf.getBaseFunctionParserFunctions()[level].length];
          cf.getBaseFunctionList()[level].put(dataCollector, tmpList);
          for (int i = 0; i < cf.getBaseFunctionParserFunctions()[level].length; i++) {
            try {
              tmpList[i] = new SubComponentFunction(DataCollector.COLLECTOR_TYPE_LIST, cf.getBaseFunctionKeys()[level][i],
                  cf.getBaseFunctionTypes()[level][i], cf.getBaseFunctionParserFunctions()[level][i], null, null, 0,
                  Integer.MAX_VALUE, null, null);

            } catch (ParseException e) {
              throw new IOException(e.getMessage());
            }
          }
        } else {
          tmpList = cf.getBaseFunctionList()[level].get(dataCollector);
        }
        for (SubComponentFunction function : tmpList) {
          function.dataCollector.initNewList(1);
        }
      }
      // check type
      if (dataCollector.getCollectorType().equals(DataCollector.COLLECTOR_TYPE_LIST)) {
        dataCollector.setWithTotal();
        // only if documents and facets
        if (docSet.length > 0 && !list.isEmpty()) {
          HashMap<String, Integer[]> docLists = new HashMap<>();
          HashMap<String, String> groupedKeys = new HashMap<>();
          boolean documentsInFacets = false;
          // compute intersections
          for (Entry<String, int[]> entry : list.entrySet()) {
            // fill grouped keys
            if (!groupedKeys.containsKey(entry.getKey())) {
              groupedKeys.put(entry.getKey(),
                  groupedKeyName(entry.getKey(), cf.getBaseRangeSizes()[level], cf.getBaseRangeBases()[level]));
            }
            // intersect docSet with docList
            Integer[] docList = intersectedDocList(entry.getValue(), docSet);
            if (docList.length > 0) {
              documentsInFacets = true;
            }
            // update docLists
            if (docLists.containsKey(groupedKeys.get(entry.getKey()))) {
              docLists.put(groupedKeys.get(entry.getKey()),
                  mergeDocLists(docLists.get(groupedKeys.get(entry.getKey())), docList));
            } else {
              docLists.put(groupedKeys.get(entry.getKey()), docList);
            }
          }
          // compute stats for each key
          if (documentsInFacets) {
            Map<Integer, long[]> args = computeArguments(spansNumberData, cf.getSpanQueries(), docSet);
            if (cf.getBaseDataTypes()[level].equals(CodecUtil.DATA_TYPE_LONG)) {
              // check functions
              boolean applySumRule = false;
              if (cf.getBaseStatsTypes()[level].equals(CodecUtil.STATS_BASIC) && cf.getBaseParsers()[level].sumRule()
                  && (cf.getBaseMinimumLongs()[level] == null) && (cf.getBaseMaximumLongs()[level] == null)) {
                applySumRule = true;
                if (cf.getBaseFunctionList()[level].get(dataCollector) != null) {
                  for (SubComponentFunction function : cf.getBaseFunctionList()[level].get(dataCollector)) {
                    if (!function.statsType.equals(CodecUtil.STATS_BASIC) || !function.parserFunction.sumRule()
                        || function.parserFunction.needPositions()) {
                      applySumRule = false;
                      break;
                    }
                  }
                }
              }
              if (applySumRule) {
                for (String key : Set.copyOf(groupedKeys.values())) {
                  if (docLists.get(key).length > 0) {
                    // initialise
                    Integer[] subDocSet = docLists.get(key);
                    int length = cf.getBaseParsers()[level].needArgumentsNumber();
                    long[] valueQSum = new long[length];
                    long[] valueDSum = new long[length];
                    long valuePositions = 0;
                    // collect
                    if (subDocSet.length > 0) {
                      long[] tmpArgs;
                      for (int docId : subDocSet) {
                        tmpArgs = args.get(docId);
                        if (positionsData != null && positionsData.containsKey(docId)
                            && positionsData.get(docId) != null) {
                          valuePositions += positionsData.get(docId).longValue();
                        }
                        if (tmpArgs != null) {
                          for (int i = 0; i < length; i++) {
                            if(tmpArgs[i]>0) {
                              valueQSum[i] += tmpArgs[i];
                              valueDSum[i] += 1;
                            }
                          }
                        }
                      }
                      long value;
                      MtasDataCollector<?, ?> subDataCollector = null;
                      try {
                        value = cf.getBaseParsers()[level].getValueLong(valueQSum, valueDSum, valuePositions, subDocSet.length);
                        subDataCollector = dataCollector.add(key, value, subDocSet.length);
                      } catch (IOException e) {
                        log.debug("Error", e);
                        dataCollector.error(key, e.getMessage(), 1);
                        subDataCollector = null;
                      }
                      if (cf.getBaseFunctionList()[level] != null && cf.getBaseFunctionList()[level].containsKey(dataCollector)) {
                        SubComponentFunction[] functionList = cf.getBaseFunctionList()[level].get(dataCollector);
                        for (SubComponentFunction function : functionList) {
                          if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                            try {
                              long valueLong = function.parserFunction.getValueLong(valueQSum, valueDSum, valuePositions, subDocSet.length);
                              function.dataCollector.add(key, valueLong, subDocSet.length);
                            } catch (IOException e) {
                              log.debug("Error", e);
                              function.dataCollector.error(key, e.getMessage(), 1);
                            }
                          } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                            try {
                              double valueDouble = function.parserFunction.getValueDouble(valueQSum, valueDSum, valuePositions, subDocSet.length);
                              function.dataCollector.add(key, valueDouble, subDocSet.length);
                            } catch (IOException e) {
                              log.debug("Error", e);
                              function.dataCollector.error(key, e.getMessage(), 1);
                            }
                          }
                        }
                      }
                      if (subDataCollector != null) {
                        createFacetBase(cf, (level + 1), subDataCollector, positionsData, spansNumberData, facetData,
                            subDocSet);
                      }
                    }
                  }
                }
              } else {
                for (String key : Set.copyOf(groupedKeys.values())) {
                  if (docLists.get(key).length > 0) {
                    // initialise
                    Integer[] subDocSet = docLists.get(key);
                    // collect
                    if (subDocSet.length > 0 && cf.getBaseDataTypes()[level].equals(CodecUtil.DATA_TYPE_LONG)) {
                      // check for functions
                      long[][] functionValuesLong = null;
                      double[][] functionValuesDouble = null;
                      int[] functionNumber = null;
                      SubComponentFunction[] functionList = null;
                      if (cf.getBaseFunctionList()[level] != null && cf.getBaseFunctionList()[level].containsKey(dataCollector)) {
                        functionList = cf.getBaseFunctionList()[level].get(dataCollector);
                        functionValuesLong = new long[functionList.length][];
                        functionValuesDouble = new double[functionList.length][];
                        functionNumber = new int[functionList.length];
                        for (int i = 0; i < functionList.length; i++) {
                          functionValuesLong[i] = new long[subDocSet.length];
                          functionValuesDouble[i] = new double[subDocSet.length];
                        }
                      }
                      // check main
                      int number = 0;
                      Integer[] restrictedSubDocSet = new Integer[subDocSet.length];
                      long[] values = new long[subDocSet.length];
                      for (int docId : subDocSet) {
                        long[] tmpArgsQ = args.get(docId);
                        long[] tmpArgsD = tmpArgsQ.clone();
                        for(int k=0; k<tmpArgsD.length; k++) {
                          tmpArgsD[k] = (tmpArgsD[k]>0)?1:0;
                        }
                        int tmpPositions = (positionsData == null) ? 0
                            : (positionsData.get(docId) == null ? 0 : positionsData.get(docId));
                        long value = cf.getBaseParsers()[level].getValueLong(tmpArgsQ, tmpArgsD, tmpPositions, 1);
                        if ((cf.getBaseMinimumLongs()[level] == null || value >= cf.getBaseMinimumLongs()[level])
                            && (cf.getBaseMaximumLongs()[level] == null || value <= cf.getBaseMaximumLongs()[level])) {
                          values[number] = value;
                          restrictedSubDocSet[number] = docId;
                          number++;
                          if (functionList != null) {
                            for (int i = 0; i < functionList.length; i++) {
                              SubComponentFunction function = functionList[i];
                              if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                                try {
                                  functionValuesLong[i][functionNumber[i]] = function.parserFunction
                                      .getValueLong(tmpArgsQ, tmpArgsD, tmpPositions, 1);
                                  functionNumber[i]++;
                                } catch (IOException e) {
                                  log.debug("Error", e);
                                  function.dataCollector.error(key, e.getMessage(), 1);
                                }
                              } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                                try {
                                  functionValuesDouble[i][functionNumber[i]] = function.parserFunction
                                      .getValueDouble(tmpArgsQ, tmpArgsD, tmpPositions, 1);
                                  functionNumber[i]++;
                                } catch (IOException e) {
                                  log.debug("Error", e);
                                  function.dataCollector.error(key, e.getMessage(), 1);
                                }
                              }
                            }
                          }
                        }
                      }
                      if (number > 0) {
                        MtasDataCollector<?, ?> subDataCollector = dataCollector.add(key, values, number);
                        if (cf.getBaseFunctionList()[level] != null
                            && cf.getBaseFunctionList()[level].containsKey(dataCollector)) {
                          for (int i = 0; i < functionList.length; i++) {
                            SubComponentFunction function = functionList[i];
                            if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                              function.dataCollector.add(key, functionValuesLong[i], functionNumber[i]);
                            } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                              function.dataCollector.add(key, functionValuesDouble[i], functionNumber[i]);
                            }
                          }
                        }
                        if (subDataCollector != null) {
                          createFacetBase(cf, (level + 1), subDataCollector, positionsData, spansNumberData, facetData,
                              Arrays.copyOfRange(restrictedSubDocSet, 0, number));
                        }
                      }
                    }
                  }
                }
              }
            } else {
              throw new IOException("unexpected dataType " + cf.getBaseDataTypes()[level]);
            }
          }
        }
      } else {
        throw new IOException("unexpected type " + dataCollector.getCollectorType());
      }
      dataCollector.closeNewList();
      if (cf.getBaseFunctionList()[level] != null && cf.getBaseFunctionList()[level].containsKey(dataCollector)) {
        SubComponentFunction[] tmpList = cf.getBaseFunctionList()[level].get(dataCollector);
        for (SubComponentFunction function : tmpList) {
          function.dataCollector.closeNewList();
        }
      }
    }

  }

  /**
   * Grouped key name.
   *
   * @param key
   *          the key
   * @param baseRangeSize
   *          the base range size
   * @param baseRangeBase
   *          the base range base
   * @return the string
   */
  private static String groupedKeyName(String key, Double baseRangeSize, Double baseRangeBase) {
    final double precision = 0.000001;
    if (baseRangeSize == null || baseRangeSize <= 0) {
      return key;
    } else {
      double doubleKey;
      double doubleBase;
      double doubleNumber;
      Double doubleStart;
      double doubleEnd;
      try {
        doubleKey = Double.parseDouble(key);
        doubleBase = baseRangeBase != null ? baseRangeBase : 0;
        doubleNumber = Math.floor((doubleKey - doubleBase) / baseRangeSize);
        doubleStart = doubleBase + doubleNumber * baseRangeSize;
        doubleEnd = doubleStart + baseRangeSize;
      } catch (NumberFormatException e) {
        return key;
      }
      // integer
      if (Math.abs(baseRangeSize - Math.floor(baseRangeSize)) < precision
          && Math.abs(doubleBase - Math.floor(doubleBase)) < precision) {
        try {
          if (baseRangeSize > 1) {
            return String.format("%.0f", doubleStart) + "-" + String.format("%.0f", doubleEnd - 1);
          } else {
            return String.format("%.0f", doubleStart);
          }
        } catch (NumberFormatException e) {
          return key;
        }
      } else {
        return "[" + doubleStart + "," + doubleEnd + ")";
      }
    }
  }

  /**
   * Merge doc lists.
   *
   * @param a
   *          the a
   * @param b
   *          the b
   * @return the integer[]
   */
  private static Integer[] mergeDocLists(Integer[] a, Integer[] b) {
    Integer[] answer = new Integer[a.length + b.length];
    int i = 0;
    int j = 0;
    int k = 0;
    Integer tmp;
    while (i < a.length && j < b.length) {
      tmp = a[i] < b[j] ? a[i++] : b[j++];
      for (; i < a.length && a[i].equals(tmp); i++) {
        ;
    }
      for (; j < b.length && b[j].equals(tmp); j++) {
        ;
    }
      answer[k++] = tmp;
    }
    while (i < a.length) {
      tmp = a[i++];
      for (; i < a.length && a[i].equals(tmp); i++) {
        ;
    }
      answer[k++] = tmp;
    }
    while (j < b.length) {
      tmp = b[j++];
      for (; j < b.length && b[j].equals(tmp); j++) {
        ;
    }
      answer[k++] = tmp;
    }
    return Arrays.copyOf(answer, k);
  }

  /**
   * Creates the facet.
   *
   * @param facetList
   *          the facet list
   * @param positionsData
   *          the positions data
   * @param spansNumberData
   *          the spans number data
   * @param facetData
   *          the facet data
   * @param docSet
   *          the doc set
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createFacet(List<ComponentFacet> facetList, Map<Integer, Integer> positionsData,
      Map<MtasSpanQuery, Map<Integer, Integer>> spansNumberData, Map<String, SortedMap<String, int[]>> facetData,
      List<Integer> docSet) throws IOException {

    if (facetList != null) {
      for (ComponentFacet cf : facetList) {
        if (cf.getBaseFields().length > 0) {
            // TODO try to make it as local as possible
            try {
                cf.getDataCollector().getLock().lock();
                createFacetBase(cf, 0, cf.getDataCollector(), positionsData, spansNumberData, facetData,
                        docSet.toArray(new Integer[0]));
            } finally {
                cf.getDataCollector().getLock().unlock();
            }
        }
      }
    }
  }

  /**
   * Creates the termvector full.
   *
   * @param termVectorList
   *          the term vector list
   * @param positionsData
   *          the positions data
   * @param docSet
   *          the doc set
   * @param t
   *          the t
   * @param r
   *          the r
   * @param lrc
   *          the lrc
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createTermvectorFull(List<ComponentTermVector> termVectorList,
      Map<Integer, Integer> positionsData, List<Integer> docSet, Terms t, LeafReader r, LeafReaderContext lrc)
      throws IOException {
    if (t != null) {
      BytesRef term;
      TermsEnum termsEnum;
      PostingsEnum postingsEnum = null;
      String segmentName = "segment" + lrc.ord;
      int segmentNumber = lrc.parent.leaves().size();
      // loop over termvectors
      for (ComponentTermVector termVector : termVectorList) {
        if (termVector.full || termVector.list != null) {
          if (termVector.full) {
            termVector.subComponentFunction.dataCollector.setWithTotal();
          }
          List<CompiledAutomaton> listAutomata;
          Map<String, Automaton> automatonMap;
          if (termVector.list == null) {
            listAutomata = new ArrayList<>();
            CompiledAutomaton compiledAutomaton;
            Automaton automaton;
            if ((termVector.regexp == null) || (termVector.regexp.isEmpty())) {
              RegExp re = new RegExp(termVector.prefix + MtasToken.DELIMITER + ".*");
              automaton = re.toAutomaton();
            } else {
              RegExp re = new RegExp(termVector.prefix + MtasToken.DELIMITER + termVector.regexp + "\u0000*");
              automaton = re.toAutomaton();
            }
            compiledAutomaton = new CompiledAutomaton(automaton);
            listAutomata.add(compiledAutomaton);
          } else {
            automatonMap = MtasToken.createAutomatonMap(termVector.prefix, new ArrayList<String>(termVector.list),
                termVector.listRegexp ? false : true);
            listAutomata = MtasToken.createAutomata(termVector.prefix, termVector.regexp, automatonMap);
          }
          List<ByteRunAutomaton> ignoreByteRunAutomatonList = null;
          if ((termVector.ignoreRegexp != null) && (!termVector.ignoreRegexp.isEmpty())) {
            ignoreByteRunAutomatonList = new ArrayList<>();
            RegExp re = new RegExp(termVector.prefix + MtasToken.DELIMITER + termVector.ignoreRegexp + "\u0000*");
            ignoreByteRunAutomatonList.add(new ByteRunAutomaton(re.toAutomaton()));
          }
          if (termVector.ignoreList != null) {
            if (ignoreByteRunAutomatonList == null) {
              ignoreByteRunAutomatonList = new ArrayList<>();
            }
            Map<String, Automaton> list = MtasToken.createAutomatonMap(termVector.prefix,
                new ArrayList<String>(termVector.ignoreList), !termVector.ignoreListRegexp);
            for (Automaton automaton : list.values()) {
              ignoreByteRunAutomatonList.add(new ByteRunAutomaton(automaton));
            }
          }
          for (CompiledAutomaton compiledAutomaton : listAutomata) {
            if (!compiledAutomaton.type.equals(CompiledAutomaton.AUTOMATON_TYPE.NORMAL)) {
              if (compiledAutomaton.type.equals(CompiledAutomaton.AUTOMATON_TYPE.NONE)) {
                // do nothing
              } else {
                throw new IOException("compiledAutomaton is " + compiledAutomaton.type);
              }
            } else {
              termsEnum = t.intersect(compiledAutomaton, null);
              int initSize = Math.min((int) t.size(), 1000);
              termVector.subComponentFunction.dataCollector.initNewList(initSize, segmentName, segmentNumber,
                  termVector.boundary);
              boolean doBasic = termVector.subComponentFunction.dataCollector.getStatsType()
                  .equals(CodecUtil.STATS_BASIC);
              if (termVector.functions != null) {
                for (SubComponentFunction function : termVector.functions) {
                  function.dataCollector.initNewList(initSize);
                  doBasic = doBasic
                      ? (function.parserFunction.sumRule() && !function.parserFunction.needPositions()
                          && function.dataCollector.getStatsType().equals(CodecUtil.STATS_BASIC))
                      : doBasic;
                }
              }
              // only if documents
              if (!docSet.isEmpty()) {
                int termDocId;
                boolean acceptedTerm;
                String key;
                // loop over terms
                while ((term = termsEnum.next()) != null) {
                  if (validateTermWithStartValue(term, termVector) && validateTermWithDistance(term, termVector)) {
                    termDocId = -1;
                    acceptedTerm = true;
                    if (ignoreByteRunAutomatonList != null) {
                      for (ByteRunAutomaton ignoreByteRunAutomaton : ignoreByteRunAutomatonList) {
                        if (ignoreByteRunAutomaton.run(term.bytes, term.offset, term.length)) {
                          acceptedTerm = false;
                          break;
                        }
                      }
                    }
                    if (acceptedTerm) {
                      if (doBasic) {
                        // compute numbers;
                        TermvectorNumberBasic numberBasic = computeTermvectorNumberBasic(docSet, termDocId, termsEnum,
                            r, lrc, postingsEnum);
                        // register
                        if (numberBasic.docNumber > 0) {
                          long valueLong = 0;
                          key = MtasToken.getPostfixFromValue(term);
                          try {
                            valueLong = termVector.subComponentFunction.parserFunction
                                .getValueLong(numberBasic.valueSum, new long[] {numberBasic.docNumber}, 1, numberBasic.docNumber);
                          } catch (IOException e) {
                            log.debug("Error", e);
                            termVector.subComponentFunction.dataCollector.error(MtasToken.getPostfixFromValue(term),
                                e.getMessage(), 1);
                          }
                          termVector.subComponentFunction.dataCollector.add(key, valueLong, numberBasic.docNumber);
                          if (termVector.functions != null) {
                            for (SubComponentFunction function : termVector.functions) {
                              if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                                long valueFunction = function.parserFunction.getValueLong(numberBasic.valueSum, new long[] {numberBasic.docNumber}, 0, numberBasic.docNumber);
                                function.dataCollector.add(key, valueFunction, numberBasic.docNumber);
                              } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                                double valueFunction = function.parserFunction.getValueDouble(numberBasic.valueSum, new long[] {numberBasic.docNumber}, 0, numberBasic.docNumber);
                                function.dataCollector.add(key, valueFunction, numberBasic.docNumber);
                              }
                            }
                          }
                        }
                      } else {
                        TermvectorNumberFull numberFull = computeTermvectorNumberFull(docSet, termDocId, termsEnum, lrc,
                            postingsEnum, positionsData);
                        if (numberFull.docNumber > 0) {
                          long[] valuesLong = new long[numberFull.docNumber];
                          key = MtasToken.getPostfixFromValue(term);
                          for (int i = 0; i < numberFull.docNumber; i++) {
                            try {
                              valuesLong[i] = termVector.subComponentFunction.parserFunction
                                  .getValueLong(new long[] { numberFull.args[i] }, new long[] {1}, numberFull.positions[i], 1);
                            } catch (IOException e) {
                              log.debug("Error", e);
                              termVector.subComponentFunction.dataCollector.error(key, e.getMessage(), 1);
                            }
                          }
                          termVector.subComponentFunction.dataCollector.add(key, valuesLong, valuesLong.length);
                          if (termVector.functions != null) {
                            for (SubComponentFunction function : termVector.functions) {
                              if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                                valuesLong = new long[numberFull.docNumber];
                                for (int i = 0; i < numberFull.docNumber; i++) {
                                  try {
                                    valuesLong[i] = function.parserFunction
                                        .getValueLong(new long[] { numberFull.args[i] }, new long[] {1}, numberFull.positions[i], 1);
                                  } catch (IOException e) {
                                    log.debug("Error", e);
                                    function.dataCollector.error(key, e.getMessage(), 1);
                                  }
                                }
                                function.dataCollector.add(key, valuesLong, valuesLong.length);
                              } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                                double[] valuesDouble = new double[numberFull.docNumber];
                                for (int i = 0; i < numberFull.docNumber; i++) {
                                  try {
                                    valuesDouble[i] = function.parserFunction
                                        .getValueDouble(new long[] { numberFull.args[i] }, new long[] {1}, numberFull.positions[i], 1);
                                  } catch (IOException e) {
                                    log.debug("Error", e);
                                    function.dataCollector.error(key, e.getMessage(), 1);
                                  }
                                }
                                function.dataCollector.add(key, valuesDouble, valuesDouble.length);
                              }
                            }
                          }
                        }

                      }
                    }
                  }
                }
              }
              termVector.subComponentFunction.dataCollector.closeNewList();
              if (termVector.functions != null) {
                for (SubComponentFunction function : termVector.functions) {
                  function.dataCollector.closeNewList();
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Creates the termvector first round.
   *
   * @param termVectorList
   *          the term vector list
   * @param positionsData
   *          the positions data
   * @param docSet
   *          the doc set
   * @param t
   *          the t
   * @param r
   *          the r
   * @param lrc
   *          the lrc
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createTermvectorFirstRound(List<ComponentTermVector> termVectorList,
      Map<Integer, Integer> positionsData, List<Integer> docSet, Terms t, LeafReader r, LeafReaderContext lrc)
      throws IOException {
    if (t != null) {
      BytesRef term;
      TermsEnum termsEnum;
      PostingsEnum postingsEnum = null;
      String segmentName = "segment" + lrc.ord;
      String[] mutableKey = new String[1];
      int segmentNumber = lrc.parent.leaves().size();
      // loop over termvectors
      for (ComponentTermVector termVector : termVectorList) {
        CompiledAutomaton compiledAutomaton;
        if ((termVector.regexp == null) || (termVector.regexp.isEmpty())) {
          RegExp re = new RegExp(termVector.prefix + MtasToken.DELIMITER + ".*");
          compiledAutomaton = new CompiledAutomaton(re.toAutomaton());
        } else {
          RegExp re = new RegExp(termVector.prefix + MtasToken.DELIMITER + termVector.regexp + "\u0000*");
          compiledAutomaton = new CompiledAutomaton(re.toAutomaton());
        }
        List<ByteRunAutomaton> ignoreByteRunAutomatonList = null;
        if ((termVector.ignoreRegexp != null) && (!termVector.ignoreRegexp.isEmpty())) {
          ignoreByteRunAutomatonList = new ArrayList<>();
          RegExp re = new RegExp(termVector.prefix + MtasToken.DELIMITER + termVector.ignoreRegexp + "\u0000*");
          ignoreByteRunAutomatonList.add(new ByteRunAutomaton(re.toAutomaton()));
        }
        if (termVector.ignoreList != null) {
          if (ignoreByteRunAutomatonList == null) {
            ignoreByteRunAutomatonList = new ArrayList<>();
          }
          Map<String, Automaton> list = MtasToken.createAutomatonMap(termVector.prefix,
              new ArrayList<String>(termVector.ignoreList), !termVector.ignoreListRegexp);
          for (Automaton automaton : list.values()) {
            ignoreByteRunAutomatonList.add(new ByteRunAutomaton(automaton));
          }
        }
        if (!termVector.full && termVector.list == null) {
          termsEnum = t.intersect(compiledAutomaton, null);
          int initSize = Math.min((int) t.size(), 1000);
          termVector.subComponentFunction.dataCollector.initNewList(initSize, segmentName, segmentNumber,
              termVector.boundary);
          if (termVector.functions != null) {
            for (SubComponentFunction function : termVector.functions) {
              function.dataCollector.initNewList(initSize);
            }
          }
          // only if documents
          if (!docSet.isEmpty()) {
            int termDocId;
            int termNumberMaximum = termVector.number;
            HashMap<BytesRef, RegisterStatus> computeFullList = new HashMap<>();
            RegisterStatus registerStatus;
            // basic, don't need full values
            if (termVector.subComponentFunction.sortType.equals(CodecUtil.SORT_TERM)
                || termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_SUM)
                || termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_N)) {
              int termCounter = 0;

              boolean continueAfterPreliminaryCheck;
              boolean preliminaryCheck = r.getLiveDocs() == null && (docSet.size() != r.numDocs());
                // loop over terms
              boolean acceptedTerm;
              while ((term = termsEnum.next()) != null) {
                if (validateTermWithStartValue(term, termVector) && validateTermWithDistance(term, termVector)) {
                  termDocId = -1;
                  acceptedTerm = true;
                  if (ignoreByteRunAutomatonList != null) {
                    for (ByteRunAutomaton ignoreByteRunAutomaton : ignoreByteRunAutomatonList) {
                      if (ignoreByteRunAutomaton.run(term.bytes, term.offset, term.length)) {
                        acceptedTerm = false;
                        break;
                      }
                    }
                  }
                  if (acceptedTerm) {
                    continueAfterPreliminaryCheck = true;
                    mutableKey[0] = null;
                    if (preliminaryCheck) {
                      try {
                        TermvectorNumberBasic preliminaryNumberBasic = computeTermvectorNumberBasic(termsEnum, r);
                        if (preliminaryNumberBasic.docNumber > 0) {
                          continueAfterPreliminaryCheck = preliminaryRegisterValue(term, termVector,
                              preliminaryNumberBasic, termNumberMaximum, segmentNumber, mutableKey);
                        } else {
                          continueAfterPreliminaryCheck = false;
                        }
                      } catch (IOException e) {
                        log.debug("Error", e);
                      }
                    }
                    if (continueAfterPreliminaryCheck) {
                      // compute numbers;
                      TermvectorNumberBasic numberBasic = computeTermvectorNumberBasic(docSet, termDocId, termsEnum, r,
                          lrc, postingsEnum);
                      // register
                      if (numberBasic.docNumber > 0) {
                        termCounter++;
                        registerStatus = registerValue(term, termVector, numberBasic, termNumberMaximum, segmentNumber,
                            false, mutableKey);
                        if (registerStatus != null) {
                          computeFullList.put(BytesRef.deepCopyOf(term), registerStatus);
                        }
                      }
                    }
                    // stop after termCounterMaximum
                    if (termVector.subComponentFunction.sortType.equals(CodecUtil.SORT_TERM)
                        && termVector.subComponentFunction.sortDirection.equals(CodecUtil.SORT_ASC)
                        && termCounter >= termNumberMaximum) {
                      break;
                    }
                  }
                }
              }
              // rerun for full
              if (!computeFullList.isEmpty()) {
                termsEnum = t.intersect(compiledAutomaton, null);
                while ((term = termsEnum.next()) != null) {
                  if (validateTermWithStartValue(term, termVector) && validateTermWithDistance(term, termVector)) {
                    termDocId = -1;
                    mutableKey[0] = null;
                    // only if (probably) needed
                    if (computeFullList.containsKey(term)) {
                      registerStatus = computeFullList.get(term);
                      boolean doAdd;
                      doAdd = termVector.subComponentFunction.sortType.equals(CodecUtil.SORT_TERM);
                      doAdd |= termVector.subComponentFunction.sortDirection.equals(CodecUtil.SORT_ASC);
                      doAdd |= termVector.list != null;
                      doAdd |= termVector.boundaryRegistration;
                      doAdd |= registerStatus.force;
                      doAdd |= termVector.subComponentFunction.dataCollector
                          .validateSegmentBoundary(registerStatus.sortValue);
                      if (doAdd) {
                        TermvectorNumberFull numberFull = computeTermvectorNumberFull(docSet, termDocId, termsEnum, lrc,
                            postingsEnum, positionsData);
                        if (numberFull.docNumber > 0) {
                          termCounter++;
                          registerValue(term, termVector, numberFull, mutableKey);
                        }
                      }
                    }
                  }
                }
                computeFullList.clear();
              }
            } else {
              throw new IOException("sort '" + termVector.subComponentFunction.sortType + " "
                  + termVector.subComponentFunction.sortDirection + "' not supported");
            }
            // finish if segments are used
            termVector.subComponentFunction.dataCollector.closeSegmentKeyValueRegistration();
            if (termVector.functions != null) {
              for (SubComponentFunction function : termVector.functions) {
                function.dataCollector.closeSegmentKeyValueRegistration();
              }
            }
          }
          termVector.subComponentFunction.dataCollector.closeNewList();
          if (termVector.functions != null) {
            for (SubComponentFunction function : termVector.functions) {
              function.dataCollector.closeNewList();
            }
          }
        }
      }
    }
  }

  /**
   * Creates the termvector second round.
   *
   * @param termVectorList
   *          the term vector list
   * @param positionsData
   *          the positions data
   * @param docSet
   *          the doc set
   * @param t
   *          the t
   * @param r
   *          the r
   * @param lrc
   *          the lrc
   * @param status
   *          the status
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static void createTermvectorSecondRound(List<ComponentTermVector> termVectorList,
      Map<Integer, Integer> positionsData, List<Integer> docSet, Terms t, LeafReader r, LeafReaderContext lrc,
      Status status) throws IOException {
    if (t != null) {
      BytesRef term;
      TermsEnum termsEnum;
      PostingsEnum postingsEnum = null;
      String segmentName = "segment" + lrc.ord;
      int segmentNumber = lrc.parent.leaves().size();
      String[] mutableKey = new String[1];
      for (ComponentTermVector termVector : termVectorList) {
        if (!termVector.full && termVector.list == null
            && (termVector.subComponentFunction.dataCollector.segmentRecomputeKeyList != null
                && termVector.subComponentFunction.dataCollector.segmentRecomputeKeyList.containsKey(segmentName))) {
          Set<String> recomputeKeyList = termVector.subComponentFunction.dataCollector.segmentRecomputeKeyList
              .get(segmentName);
          if (!recomputeKeyList.isEmpty()) {
            Map<String, Automaton> automatonMap = MtasToken.createAutomatonMap(termVector.prefix,
                new ArrayList<String>(recomputeKeyList), true);
            List<CompiledAutomaton> listCompiledAutomata = MtasToken.createAutomata(termVector.prefix,
                termVector.regexp, automatonMap);
            for (CompiledAutomaton compiledAutomaton : listCompiledAutomata) {
              if (!compiledAutomaton.type.equals(CompiledAutomaton.AUTOMATON_TYPE.NONE)) {
                termsEnum = t.intersect(compiledAutomaton, null);
                termVector.subComponentFunction.dataCollector.initNewList(
                    termVector.subComponentFunction.dataCollector.segmentKeys.size(), segmentName, segmentNumber,
                    termVector.boundary);
                RegisterStatus registerStatus = null;
                if (termVector.functions != null) {
                  for (SubComponentFunction function : termVector.functions) {
                    function.dataCollector.initNewList((int) t.size(), segmentName, segmentNumber, null);
                  }
                }
                if (!docSet.isEmpty()) {
                  int termDocId;
                  while ((term = termsEnum.next()) != null) {
                    if (validateTermWithStartValue(term, termVector)) {
                      termDocId = -1;
                      mutableKey[0] = null;
                      // compute numbers;
                      TermvectorNumberBasic numberBasic = computeTermvectorNumberBasic(docSet, termDocId, termsEnum, r,
                          lrc, postingsEnum);
                      if (numberBasic.docNumber > 0) {
                        registerStatus = registerValue(term, termVector, numberBasic, 0, segmentNumber, true,
                            mutableKey);
                        if (registerStatus != null) {
                          TermvectorNumberFull numberFull = computeTermvectorNumberFull(docSet, termDocId, termsEnum,
                              lrc, postingsEnum, positionsData);
                          if (numberFull.docNumber > 0) {
                            registerValue(term, termVector, numberFull, mutableKey);
                          }
                        }
                      }
                    }
                  }
                }
                termVector.subComponentFunction.dataCollector.closeNewList();
                if (termVector.functions != null) {
                  for (SubComponentFunction function : termVector.functions) {
                    function.dataCollector.closeNewList();
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Validate term with start value.
   *
   * @param term
   *          the term
   * @param termVector
   *          the term vector
   * @return true, if successful
   */
  private static boolean validateTermWithStartValue(BytesRef term, ComponentTermVector termVector) {
    if (termVector.startValue == null) {
      return true;
    } else if (termVector.subComponentFunction.sortType.equals(CodecUtil.SORT_TERM)) {
      if (term.length > termVector.startValue.length) {
        byte[] zeroBytes = (new BytesRef("\u0000")).bytes;
        int n = (int) (Math.ceil(((double) (term.length - termVector.startValue.length)) / zeroBytes.length));
        byte[] newBytes = new byte[termVector.startValue.length + n * zeroBytes.length];
        System.arraycopy(termVector.startValue.bytes, 0, newBytes, 0, termVector.startValue.length);
        for (int i = 0; i < n; i++) {
          System.arraycopy(zeroBytes, 0, newBytes, termVector.startValue.length + i * zeroBytes.length,
              zeroBytes.length);
        }
        termVector.startValue = new BytesRef(newBytes);
      }
      return (termVector.subComponentFunction.sortDirection.equals(CodecUtil.SORT_ASC)
                && (termVector.startValue.compareTo(term) < 0))
                || (termVector.subComponentFunction.sortDirection.equals(CodecUtil.SORT_DESC)
                && (termVector.startValue.compareTo(term) > 0));
    }
    return false;
  }

  /**
   * Validate term with distance.
   *
   * @param term
   *          the term
   * @param termVector
   *          the term vector
   * @return true, if successful
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static boolean validateTermWithDistance(BytesRef term, ComponentTermVector termVector) throws IOException {
    if (termVector.distances == null || termVector.distances.isEmpty()) {
      return true;
    } else {
      // first check maximum for all distances
      for (SubComponentDistance item : termVector.distances) {
        if (item.getMaximum() != null) {
          if (!item.getDistance().validateMaximum(term)) {
            return false;
          }
        }
      }
      // then check minimum for all distances
      for (SubComponentDistance item : termVector.distances) {
        if (item.getMinimum() != null) {
          if (!item.getDistance().validateMinimum(term)) {
            return false;
          }
        }
      }
      return true;
    }
  }

  /**
   * Need second round termvector.
   *
   * @param termVectorList
   *          the term vector list
   * @return true, if successful
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static boolean needSecondRoundTermvector(List<ComponentTermVector> termVectorList) throws IOException {
    boolean needSecondRound = false;
    for (ComponentTermVector termVector : termVectorList) {
      if (!termVector.full && termVector.list == null) {
        boolean doCheck;
        doCheck = termVector.subComponentFunction.dataCollector.segmentRegistration != null
            && (termVector.subComponentFunction.dataCollector.segmentRegistration
                .equals(MtasDataCollector.SEGMENT_SORT_ASC)
                || termVector.subComponentFunction.dataCollector.segmentRegistration
                    .equals(MtasDataCollector.SEGMENT_SORT_DESC))
            && termVector.number > 0;
        doCheck |= termVector.subComponentFunction.dataCollector.segmentRegistration != null
            && (termVector.subComponentFunction.dataCollector.segmentRegistration
                .equals(MtasDataCollector.SEGMENT_BOUNDARY_ASC)
                || termVector.subComponentFunction.dataCollector.segmentRegistration
                    .equals(MtasDataCollector.SEGMENT_BOUNDARY_DESC))
            && termVector.number > 0;
        if (doCheck) {
          termVector.subComponentFunction.dataCollector.recomputeSegmentKeys();
          if (!termVector.subComponentFunction.dataCollector.checkExistenceNecessaryKeys()) {
            needSecondRound = true;
          }
          termVector.subComponentFunction.dataCollector.reduceToSegmentKeys();
        }
      }
    }
    return needSecondRound;
  }

  /**
   * The Class TermvectorNumberBasic.
   */
  class TermvectorNumberBasic {

    /** The value sum. */
    public final long[] valueSum;

    /** The doc number. */
    public int docNumber;

    /**
     * Instantiates a new termvector number basic.
     */
    TermvectorNumberBasic() {
      valueSum = new long[] { 0 };
      docNumber = 0;
    }
  }

  /**
   * The Class TermvectorNumberFull.
   */
  class TermvectorNumberFull {

    /** The args. */
    public final long[] args;

    /** The positions. */
    public final int[] positions;

    /** The doc number. */
    public int docNumber;

    /**
     * Instantiates a new termvector number full.
     *
     * @param maxSize
     *          the max size
     */
    TermvectorNumberFull(int maxSize) {
      args = new long[maxSize];
      positions = new int[maxSize];
      docNumber = 0;
    }
  }

  class IntervalTreeItem implements Comparable<IntervalTreeItem> {

    private int max;
    private IntervalTreeItem left;
    private IntervalTreeItem right;
    private final IndexItem indexItem;

    public IntervalTreeItem(IndexItem indexItem) {
      this.indexItem = indexItem;
      max = indexItem.endPosition;
      left = null;
      right = null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass().getSimpleName(), max, left, right, indexItem);
    }


    @Override
    public boolean equals(Object o) {
      if(o == this) {
        return true;
      }
      if(!(o instanceof IntervalTreeItem item)) {
        return false;
      }
      return((indexItem.startPosition == item.indexItem.startPosition) && indexItem.endPosition == item.indexItem.endPosition);
    }

    @Override
    public int compareTo(IntervalTreeItem item) {
      if (indexItem.startPosition < item.indexItem.startPosition) {
        return -1;
      } else if (indexItem.startPosition == item.indexItem.startPosition) {
        if(indexItem.endPosition <= item.indexItem.endPosition) {
          return 0;
        } else {
          return 1;
        }
      } else {
        return 1;
      }
    }

  }

  class IntervalTree {

    private IntervalTreeItem root = null;

    public void insertNode(IntervalTreeItem node) {
      root = insertNode(node, root);
    }

    private IntervalTreeItem insertNode(IntervalTreeItem node, IntervalTreeItem subRoot) {
      if (subRoot == null) {
        return node;
      }
      if (node.indexItem.endPosition > subRoot.max) {
        subRoot.max = node.indexItem.endPosition;
      }
      if (subRoot.compareTo(node) <= 0) {

        if (subRoot.right == null) {
          subRoot.right = node;
        } else {
          insertNode(node, subRoot.right);
        }
      } else {
        if (subRoot.left == null) {
          subRoot.left = node;
        } else {
          insertNode(node, subRoot.left);
        }
      }
      return subRoot;
    }

    public void updateInterval(int start, int end, String match) {
      incrementInterval(start, end, root, match);
    }

    public void updateInterval(int start, int end, String match, List<MtasTreeHit<String>> list) {
      incrementInterval(start, end, root, match, list);
    }

    private void incrementInterval(int start, int end, IntervalTreeItem root, String match) {
      if (root != null) {
        List<IntervalTreeItem> checkList = new ArrayList<>();
        checkList.add(root);
        int startend = Math.max(start, end);
        do {
          IntervalTreeItem checkItem = checkList.removeLast();
            switch (match) {
                case MATCH_INTERSECT -> {
                    if (!((checkItem.indexItem.startPosition > startend) || (checkItem.indexItem.endPosition < start))) {
                        checkItem.indexItem.number++;
                    }
                }
                case MATCH_COMPLETE -> {
                    if (!((checkItem.indexItem.startPosition > start) || (checkItem.indexItem.endPosition < startend))) {
                        checkItem.indexItem.number++;
                    }
                }
                case MATCH_START -> {
                    if (!((checkItem.indexItem.startPosition > start) || (checkItem.indexItem.endPosition < start))) {
                        checkItem.indexItem.number++;
                    }
                }
            }
          if ((checkItem.left != null) && (checkItem.left.max >= start)) {
            checkList.add(checkItem.left);
          }
          if (checkItem.right != null) {
            checkList.add(checkItem.right);
          }
        } while (!checkList.isEmpty());
      }
    }

    private void incrementInterval(int start, int end, IntervalTreeItem root, String match,
        List<MtasTreeHit<String>> list) {
      if (root != null) {
        ArrayList<IntervalTreeItem> checkList = new ArrayList<IntervalTreeItem>();
        checkList.add(root);
        int startend = Math.max(start, end);
        Integer value;
        List<Map<String, Set<String>>> entry = new ArrayList<Map<String, Set<String>>>();
        for (MtasTreeHit<String> item : list) {
          String prefix = MtasToken.getPrefixFromValue(item.refData);
          String postfix = MtasToken.getPostfixFromValue(item.refData);
          for (int position = 0; position < item.endPosition - item.startPosition+1; position++) {
            while (position >= entry.size()) {
              entry.add(new HashMap<String, Set<String>>());
            }
            if (!entry.get(position).containsKey(prefix)) {
              entry.get(position).put(prefix, new HashSet<String>(List.of()) {
                {
                  add(postfix);
                }
              });
            } else {
              entry.get(position).get(prefix).add(postfix);
            }
          }
        }
        do {
          IntervalTreeItem checkItem = checkList.removeLast();
          if (match.equals(MATCH_INTERSECT)) {
            if (!((checkItem.indexItem.startPosition > startend) || (checkItem.indexItem.endPosition < start))) {
              if ((value = checkItem.indexItem.list.putIfAbsent(entry, 1)) != null) {
                checkItem.indexItem.list.put(entry, value + 1);
              }
            }
          } else if (match.equals(MATCH_COMPLETE)) {
            if (!((checkItem.indexItem.startPosition > start) || (checkItem.indexItem.endPosition < startend))) {
              if ((value = checkItem.indexItem.list.putIfAbsent(entry, 1)) != null) {
                checkItem.indexItem.list.put(entry, value + 1);
              }
            }
          } else if (match.equals(MATCH_START)) {
            if (!((checkItem.indexItem.startPosition > start) || (checkItem.indexItem.endPosition < start))) {
              if ((value = checkItem.indexItem.list.putIfAbsent(entry, 1)) != null) {
                checkItem.indexItem.list.put(entry, value + 1);
              }
            }
          }
          if ((checkItem.left != null) && (checkItem.left.max >= start)) {
            checkList.add(checkItem.left);
          }
          if (checkItem.right != null) {
            checkList.add(checkItem.right);
          }
        } while (checkList.size() > 0);
      }
    }
  }

  /**
   * The Class RegisterStatus.
   */
  record RegisterStatus(long sortValue, boolean force) {
  }

  /**
   * Register value.
   *
   * @param term
   *          the term
   * @param termVector
   *          the term vector
   * @param number
   *          the number
   * @param termNumberMaximum
   *          the term number maximum
   * @param segmentNumber
   *          the segment number
   * @param forceAccept
   *          the force accept
   * @param mutableKey
   *          the mutable key
   * @return the register status
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  @SuppressWarnings("unchecked")
  private static RegisterStatus registerValue(BytesRef term, ComponentTermVector termVector,
      TermvectorNumberBasic number, Integer termNumberMaximum, Integer segmentNumber, boolean forceAccept,
      String[] mutableKey) throws IOException {
    long value = termVector.subComponentFunction.parserFunction.getValueLong(number.valueSum, new long[] {number.docNumber}, 0, number.docNumber);
    long sortValue = 0;
    if (termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_SUM)) {
      sortValue = value;
    } else if (termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_N)) {
      sortValue = number.docNumber;
    }
    boolean addItem = false;
    boolean addItemForced = false;
    MtasDataCollector<Long, ?> dataCollector = (MtasDataCollector<Long, ?>) termVector.subComponentFunction.dataCollector;
    // sort on term
    if (termVector.subComponentFunction.sortType.equals(CodecUtil.SORT_TERM)) {
      addItem = true;
      addItemForced = true;
      // sort on sum or n
    } else if (termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_SUM)
        || termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_N)) {
      // always accept
      if (forceAccept) {
        addItem = true;
        addItemForced = addItem;
        // check boundary
      } else if (termVector.boundaryRegistration) {
        addItem = dataCollector.validateSegmentBoundary(sortValue);
        if (addItem) {
          if (mutableKey[0] == null) {
            mutableKey[0] = MtasToken.getPostfixFromValue(term);
          }
          String segmentStatus = dataCollector.validateSegmentValue(mutableKey[0], sortValue, termNumberMaximum,
              segmentNumber, false);
          if (segmentStatus != null) {
            if (segmentStatus.equals(MtasDataCollector.SEGMENT_KEY)) {
              addItemForced = true;
            }
          } else {
            // shouldn't happen
          }
        }
        // no boundary
      } else {
        String segmentStatus = dataCollector.validateSegmentValue(sortValue, termNumberMaximum, segmentNumber);
        if (segmentStatus != null) {
          boolean possibleAddItem;
          if (segmentStatus.equals(MtasDataCollector.SEGMENT_KEY_OR_NEW)) {
            possibleAddItem = true;
          } else if (segmentStatus.equals(MtasDataCollector.SEGMENT_POSSIBLE_KEY)) {
            mutableKey[0] = MtasToken.getPostfixFromValue(term);
            segmentStatus = dataCollector.validateSegmentValue(mutableKey[0], sortValue, termNumberMaximum,
                segmentNumber, true);
            if (segmentStatus != null) {
              possibleAddItem = true;
            } else {
              possibleAddItem = false;
            }
          } else {
            // should never happen?
            possibleAddItem = false;
          }
          // further checks, passed initial
          if (possibleAddItem) {
            if (mutableKey[0] == null) {
              mutableKey[0] = MtasToken.getPostfixFromValue(term);
            }
            segmentStatus = dataCollector.validateSegmentValue(mutableKey[0], sortValue, termNumberMaximum,
                segmentNumber, false);
            if (segmentStatus != null) {
              addItem = true;
              if (segmentStatus.equals(MtasDataCollector.SEGMENT_KEY)) {
                addItemForced = true;
              }
            }
          }
        } else {
          addItem = false;
        }
      }
      // don't sort?
    } else {
      addItem = false;
    }
    if (addItem) {
      boolean computeFull = false;
      if (mutableKey[0] == null) {
        mutableKey[0] = MtasToken.getPostfixFromValue(term);
      }
      // check dataCollector type
      if (termVector.subComponentFunction.statsType.equals(CodecUtil.STATS_BASIC)) {
        dataCollector.add(mutableKey[0], value, number.docNumber);
      } else {
        computeFull = true;
      }
      // functions
      if (termVector.functions != null) {
        for (SubComponentFunction function : termVector.functions) {
          if (function.parserFunction.sumRule() && !function.parserFunction.needPositions()
              && function.statsType.equals(CodecUtil.STATS_BASIC)) {
            if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
              long valueFunction = function.parserFunction.getValueLong(number.valueSum, new long[] {number.docNumber}, 0, number.docNumber);
              function.dataCollector.add(mutableKey[0], valueFunction, number.docNumber);
            } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
              double valueFunction = function.parserFunction.getValueDouble(number.valueSum, new long[] {number.docNumber}, 0, number.docNumber);
              function.dataCollector.add(mutableKey[0], valueFunction, number.docNumber);
            }
          } else {
            computeFull = true;
          }
        }
      }
      // add as full?
      return computeFull ? new RegisterStatus(sortValue, addItemForced) : null;
    } else {
      return null;
    }
  }

  /**
   * Preliminary register value.
   *
   * @param term
   *          the term
   * @param termVector
   *          the term vector
   * @param number
   *          the number
   * @param termNumberMaximum
   *          the term number maximum
   * @param segmentNumber
   *          the segment number
   * @param mutableKey
   *          the mutable key
   * @return true, if successful
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static boolean preliminaryRegisterValue(BytesRef term, ComponentTermVector termVector,
      TermvectorNumberBasic number, Integer termNumberMaximum, Integer segmentNumber, String[] mutableKey)
      throws IOException {
    long sortValue = 0;
    if (termVector.subComponentFunction.sortDirection.equals(CodecUtil.SORT_DESC)
        && termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_SUM)) {
      sortValue = termVector.subComponentFunction.parserFunction.getValueLong(number.valueSum, new long[] {number.docNumber}, 0, number.docNumber);
    } else if (termVector.subComponentFunction.sortDirection.equals(CodecUtil.SORT_DESC)
        && termVector.subComponentFunction.sortType.equals(CodecUtil.STATS_TYPE_N)) {
      sortValue = number.docNumber;
    } else {
      return true;
    }
    MtasDataCollector<Long, ?> dataCollector = (MtasDataCollector<Long, ?>) termVector.subComponentFunction.dataCollector;
    if (termVector.boundaryRegistration) {
      return dataCollector.validateSegmentBoundary(sortValue);
    } else {
      String segmentStatus = dataCollector.validateSegmentValue(sortValue, termNumberMaximum, segmentNumber);
      if (segmentStatus != null) {
        if (segmentStatus.equals(MtasDataCollector.SEGMENT_KEY_OR_NEW)) {
          return true;
        } else if (segmentStatus.equals(MtasDataCollector.SEGMENT_POSSIBLE_KEY)) {
          mutableKey[0] = MtasToken.getPostfixFromValue(term);
          segmentStatus = dataCollector.validateSegmentValue(mutableKey[0], sortValue, termNumberMaximum, segmentNumber,
              true);
          return segmentStatus != null;
        } else {
          // should never happen?
          return false;
        }
      } else {
        return false;
      }
    }
  }

  /**
   * Register value.
   *
   * @param term
   *          the term
   * @param termVector
   *          the term vector
   * @param number
   *          the number
   * @param mutableKey
   *          the mutable key
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  @SuppressWarnings("unchecked")
  private static void registerValue(BytesRef term, ComponentTermVector termVector, TermvectorNumberFull number,
      String[] mutableKey) throws IOException {
    if (number.docNumber > 0) {
      if (mutableKey[0] == null) {
        mutableKey[0] = MtasToken.getPostfixFromValue(term);
      }
      MtasDataCollector<Long, ?> dataCollector = (MtasDataCollector<Long, ?>) termVector.subComponentFunction.dataCollector;
      long[] valuesLong = new long[number.docNumber];
      for (int i = 0; i < number.docNumber; i++) {
        try {
          valuesLong[i] = termVector.subComponentFunction.parserFunction.getValueLong(new long[] { number.args[i] }, new long[] { 1 },
              number.positions[i], 1);
        } catch (IOException e) {
          log.debug("Error", e);
          dataCollector.error(mutableKey[0], e.getMessage(), 1);
        }
      }
      if (!termVector.subComponentFunction.statsType.equals(CodecUtil.STATS_BASIC)) {
        dataCollector.add(mutableKey[0], valuesLong, valuesLong.length);
      }
      for (SubComponentFunction function : termVector.functions) {
        if (!function.parserFunction.sumRule() || function.parserFunction.needPositions()
            || !function.statsType.equals(CodecUtil.STATS_BASIC)) {
          if (function.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
            valuesLong = new long[number.docNumber];
            for (int i = 0; i < number.docNumber; i++) {
              try {
                valuesLong[i] = function.parserFunction.getValueLong(new long[] { number.args[i] }, new long[] {1},
                    number.positions[i], 1);
              } catch (IOException e) {
                log.debug("Error", e);
                function.dataCollector.error(mutableKey[0], e.getMessage(), 1);
              }
            }
            function.dataCollector.add(mutableKey[0], valuesLong, valuesLong.length);
          } else if (function.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
            double[] valuesDouble = new double[number.docNumber];
            for (int i = 0; i < number.docNumber; i++) {
              try {
                valuesDouble[i] = function.parserFunction.getValueDouble(new long[] { number.args[i] }, new long[] {1},
                    number.positions[i], 1);
              } catch (IOException e) {
                log.debug("Error", e);
                function.dataCollector.error(mutableKey[0], e.getMessage(), 1);
              }
            }
            function.dataCollector.add(mutableKey[0], valuesDouble, valuesDouble.length);
          }
        }
      }
    }
  }

  /**
   * Compute termvector number basic.
   *
   * @param termsEnum
   *          the terms enum
   * @param r
   *          the r
   * @return the termvector number basic
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static TermvectorNumberBasic computeTermvectorNumberBasic(TermsEnum termsEnum, LeafReader r)
      throws IOException {
    TermvectorNumberBasic result = new TermvectorNumberBasic();
    boolean hasDeletedDocuments = (r.getLiveDocs() != null);
    if (!hasDeletedDocuments) {
      result.valueSum[0] = termsEnum.totalTermFreq();
      result.docNumber = termsEnum.docFreq();
      if (result.valueSum[0] > -1) {
        return result;
      }
    }
    throw new IOException("should not call this");
  }

  /**
   * Compute termvector number basic.
   *
   * @param docSet
   *          the doc set
   * @param termDocId
   *          the term doc id
   * @param termsEnum
   *          the terms enum
   * @param r
   *          the r
   * @param lrc
   *          the lrc
   * @param postingsEnum
   *          the postings enum
   * @return the termvector number basic
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static TermvectorNumberBasic computeTermvectorNumberBasic(List<Integer> docSet, int termDocId,
      TermsEnum termsEnum, LeafReader r, LeafReaderContext lrc, PostingsEnum postingsEnum) throws IOException {
    TermvectorNumberBasic result = new TermvectorNumberBasic();
    boolean hasDeletedDocuments = (r.getLiveDocs() != null);
    if ((docSet.size() == r.numDocs()) && !hasDeletedDocuments) {
      try {
        return computeTermvectorNumberBasic(termsEnum, r);
      } catch (IOException e) {
        log.debug("problem", e);
        // problem
      }
    }
    result.docNumber = 0;
    result.valueSum[0] = 0;
    int localTermDocId = termDocId;
    Iterator<Integer> docIterator = docSet.iterator();
    postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.FREQS);
    int docId;
    while (docIterator.hasNext()) {
      docId = docIterator.next() - lrc.docBase;
      if (docId >= localTermDocId
          && ((docId == localTermDocId) || ((localTermDocId = postingsEnum.advance(docId)) == docId))) {
        result.docNumber++;
        result.valueSum[0] += postingsEnum.freq();
      }
      if (localTermDocId == DocIdSetIterator.NO_MORE_DOCS) {
        break;
      }
    }
    return result;
  }

  /**
   * Compute termvector number full.
   *
   * @param docSet
   *          the doc set
   * @param termDocId
   *          the term doc id
   * @param termsEnum
   *          the terms enum
   * @param lrc
   *          the lrc
   * @param postingsEnum
   *          the postings enum
   * @param positionsData
   *          the positions data
   * @return the termvector number full
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  private static TermvectorNumberFull computeTermvectorNumberFull(List<Integer> docSet, int termDocId,
      TermsEnum termsEnum, LeafReaderContext lrc, PostingsEnum postingsEnum, Map<Integer, Integer> positionsData)
      throws IOException {
    TermvectorNumberFull result = new TermvectorNumberFull(docSet.size());
    Iterator<Integer> docIterator = docSet.iterator();
    int localTermDocId = termDocId;
    postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.FREQS);
    while (docIterator.hasNext()) {
      int docId = docIterator.next() - lrc.docBase;
      if (docId >= localTermDocId
          && ((docId == localTermDocId) || ((localTermDocId = postingsEnum.advance(docId)) == docId))) {
        result.args[result.docNumber] = postingsEnum.freq();
        result.positions[result.docNumber] = (positionsData == null) ? 0 : positionsData.get(docId + lrc.docBase);
        result.docNumber++;
      }
    }
    return result;
  }

}
