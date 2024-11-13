package mtas.analysis.token;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import mtas.analysis.util.MtasParserException;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.block.comparator.primitive.IntComparator;
import org.eclipse.collections.api.factory.primitive.IntIntMaps;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

/**
 * The Class MtasTokenCollection.
 */
public class MtasTokenCollection {

  /** The token collection. */
  private MutableIntObjectMap<MtasToken> tokenCollection = IntObjectMaps.mutable.empty();

  /** The token collection index. */
  private MutableIntList tokenCollectionIndex = IntLists.mutable.empty();

  /**
   * Adds the.
   *
   * @param token the token
   * @return the integer
   */
  public int add(MtasToken token) {
    int id = token.getId();
    tokenCollection.put(id, token);
    return id;
  }

  /**
   * Gets the.
   *
   * @param id the id
   * @return the mtas token
   */
  public MtasToken get(int id) {
    return tokenCollection.get(id);
  }

  /**
   * Iterator.
   *
   * @return the iterator
   * @throws MtasParserException the mtas parser exception
   */
  public Iterator<MtasToken> iterator() throws MtasParserException {
    checkTokenCollectionIndex();
    return new Iterator<>() {

      private final IntIterator indexIterator = tokenCollectionIndex.intIterator();

      @Override
      public boolean hasNext() {
        return indexIterator.hasNext();
      }

      @Override
      public MtasToken next() {
        return tokenCollection.get(indexIterator.next());
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Prints the.
   *
   * @throws MtasParserException the mtas parser exception
   */
  public void print() throws MtasParserException {
    Iterator<MtasToken> it = this.iterator();
    while (it.hasNext()) {
      MtasToken token = it.next();
      System.out.println(token);
    }
  }

  /**
   * Gets the list.
   *
   * @return the list
   * @throws MtasParserException the mtas parser exception
   */
  public String[][] getList() throws MtasParserException {
    String[][] result = new String[(tokenCollection.size() + 1)][];
    result[0] = new String[] { "id", "start real offset", "end real offset",
        "provide real offset", "start offset", "end offset", "provide offset",
        "start position", "end position", "multiple positions", "parent",
        "provide parent", "payload", "prefix", "postfix" };
    int number = 1;
    Iterator<MtasToken> it = this.iterator();
    while (it.hasNext()) {
      MtasToken token = it.next();
      String[] row = new String[15];
      row[0] = Integer.toString(token.getId());
      if (token.getRealOffsetStart() != null) {
        row[1] = token.getRealOffsetStart().toString();
        row[2] = token.getRealOffsetEnd().toString();
        row[3] = token.getProvideRealOffset() ? "1" : null;
      }
      if (token.getOffsetStart() != -1) {
        row[4] = Integer.toString(token.getOffsetStart());
        row[5] = Integer.toString(token.getOffsetEnd());
        row[6] = token.getProvideOffset() ? "1" : null;
      }
      if (token.getPositionLength() != -1) {
        if (token.getPositionStart() == token.getPositionEnd()) {
          row[7] = Integer.toString(token.getPositionStart());
          row[8] = Integer.toString(token.getPositionEnd());
          row[9] = null;
        } else if ((token.getPositions() == null)
            || (token.getPositions().length == (1 + token.getPositionEnd()
                - token.getPositionStart()))) {
          row[7] = Integer.toString(token.getPositionStart());
          row[8] = Integer.toString(token.getPositionEnd());
          row[9] = null;
        } else {
          row[7] = null;
          row[8] = null;
          row[9] = Arrays.toString(token.getPositions());
        }
      }
      if (token.getParentId() != null) {
        row[10] = token.getParentId().toString();
        row[11] = token.getProvideParentId() ? "1" : null;
      }
      if (token.getPayload() != null) {
        BytesRef payload = token.getPayload();
        row[12] = Float.toString(PayloadHelper.decodeFloat(Arrays.copyOfRange(
            payload.bytes, payload.offset, (payload.offset + payload.length))));
      }
      row[13] = token.getPrefix();
      row[14] = token.getPostfix();
      result[number] = row;
      number++;
    }
    return result;
  }

  /**
   * Check.
   *
   * @param autoRepair the auto repair
   * @param makeUnique the make unique
   * @throws MtasParserException the mtas parser exception
   */
  public void check(Boolean autoRepair, Boolean makeUnique)
      throws MtasParserException {
    if (autoRepair) {
      autoRepair();
    }
    if (makeUnique) {
      makeUnique();
    }
    checkTokenCollectionIndex();
    tokenCollectionIndex
            .primitiveStream()
            .filter(idx -> {
              var mtasToken = tokenCollection.get(idx);
              return mtasToken.getPositionStart() == -1 || mtasToken.getPositionEnd() == -1 || mtasToken.getValue() == null;
            })
            .findAny()
            .ifPresent(idx -> clear());
  }

  /**
   * Make unique.
   */
  private void makeUnique() {
    // TODO WHY IT DOESN'T MAKE ANY CHANGE?!!
//    HashMap<String, ArrayList<MtasToken>> currentPositionTokens = new HashMap<>();
//    ArrayList<MtasToken> currentValueTokens;
//    int currentStartPosition = -1;
//    for (Entry<Integer, MtasToken> entry : tokenCollection.entrySet()) {
//      MtasToken currentToken = entry.getValue();
//      if (currentToken.getPositionStart() > currentStartPosition) {
//        currentPositionTokens.clear();
//        currentStartPosition = currentToken.getPositionStart();
//      } else {
//        if (currentPositionTokens.containsKey(currentToken.getValue())) {
//          currentValueTokens = currentPositionTokens.get(currentToken.getValue());
//        } else {
//          currentValueTokens = new ArrayList<>();
//          currentPositionTokens.put(currentToken.getValue(), currentValueTokens);
//        }
//        currentValueTokens.add(currentToken);
//      }
//    }
  }

  /**
   * Auto repair.
   */
  private void autoRepair() {
    MutableIntSet trash = IntSets.mutable.empty();
    tokenCollection.forEachKeyValue((id, token) -> {
      boolean putInTrash;
      putInTrash = (token.getPositionStart() == -1) || (token.getPositionEnd() == -1);
      putInTrash |= token.getValue() == null || (token.getValue().isEmpty());
      putInTrash |= token.getPrefix() == null || (token.getPrefix().isEmpty());
      if (putInTrash) {
        trash.add(id);
      }
    });

//    for (Entry<Integer, MtasToken> entry : entrySet()) {
//      MtasToken token = entry.getValue();
//
//    }
    // check parentId and offset
    tokenCollection
            .select((i, mtasToken) -> mtasToken.getParentId() != null
                    && (!tokenCollection.containsKey(mtasToken.getParentId()) || trash.contains(mtasToken.getParentId())))
            .forEach(mtasToken -> mtasToken.setParentId(null));
    // empty bin
    if (!trash.isEmpty()) {
      trash.each(i -> tokenCollection.remove(i));
    }
    // always check ids
    if (!tokenCollection.isEmpty()) {
      int maxId = tokenCollection.keySet().max();
      int minId = tokenCollection.keySet().min();
      // check
      if ((minId > 0) || ((1 + maxId - minId) != tokenCollection.size())) {
        // create translation
        AtomicInteger newId = new AtomicInteger();
        MutableIntIntMap translation = IntIntMaps.mutable.withInitialCapacity(tokenCollection.size());
        tokenCollection.forEachKey(i -> translation.put(i, newId.getAndIncrement()));

        // translate objects
        tokenCollection.forEachKeyValue((key, token) -> {
          Integer parentId = token.getParentId();
          token.setId(translation.get(key));
          if (parentId != null) {
            token.setParentId(translation.get(parentId));
          }
        });

        // new tokenCollection
        MutableIntObjectMap<MtasToken> newTokenCollection = IntObjectMaps.mutable.withInitialCapacity(tokenCollection.size());
        tokenCollection.forEachKeyValue((key, token) -> newTokenCollection.put(translation.get(key), token));

        tokenCollection = newTokenCollection;
      }
    }
  }

  /**
   * Check token collection index.
   *
   * @throws MtasParserException the mtas parser exception
   */
  private void checkTokenCollectionIndex() throws MtasParserException {
    if (tokenCollectionIndex.size() != tokenCollection.size()) {
      tokenCollectionIndex = IntLists.mutable.empty();
      tokenCollection.forEachKeyValue((key, token) -> {
//        maxId = ((maxId == -1) ? entry.getKey(): Math.max(maxId, entry.getKey()));
//        minId = ((minId == -1) ? entry.getKey(): Math.min(minId, entry.getKey()));
        try {
          if ((token.getPositionStart() == -1) || (token.getPositionEnd() == -1)) {
            throw new MtasParserException("no position for token with id " + token.getId() + " (" + token.getValue() + ")");
          } else if (token.getValue() == null || (token.getValue().isEmpty())) {
            throw new MtasParserException("no value for token with id " + token.getId());
          } else if (token.getPrefix() == null || (token.getPrefix().isEmpty())) {
            throw new MtasParserException("no prefix for token with id " + token.getId());
          } else if ((token.getParentId() != null)
              && !tokenCollection.containsKey(token.getParentId())) {
            throw new MtasParserException("missing parentId for token with id " + token.getId());
          } else if ((token.getOffsetStart() == -1) || (token.getOffsetEnd() == -1)) {
            throw new MtasParserException("missing offset for token with id " + token.getId() + " (" + token.getValue() + ")");
          }
        } catch (MtasParserException e) {
          throw new RuntimeException(e);
        }
        tokenCollectionIndex.add(key);
      });
      int maxId = tokenCollection.keySet().max();
      int minId = tokenCollection.keySet().min();
      if ((!tokenCollection.isEmpty())
          && ((minId > 0) || ((1 + maxId - minId) != tokenCollection.size()))) {
        throw new MtasParserException("missing ids");
      }
      tokenCollectionIndex.sortThis(getCompByName());
    }
  }

  /**
   * Gets the comp by name.
   *
   * @return the comp by name
   */
  public IntComparator getCompByName() {
    return (t1, t2) -> {
      MtasToken firstToken = tokenCollection.get(t1);
      MtasToken secondToken = tokenCollection.get(t2);
      int p1 = firstToken.getPositionStart();
      int p2 = secondToken.getPositionStart();
      assert p1 != -1 : "no position for " + firstToken;
      assert p2 != -1 : "no position for " + secondToken;
      if (p1 == p2) {
        int o1 = firstToken.getOffsetStart();
        int o2 = secondToken.getOffsetStart();
        if (o1 != -1 && o2 != -1) {
          if (o1 == o2) {
            return firstToken.getValue().compareTo(secondToken.getValue());
          } else {
            return Integer.compare(o1, o2);
          }
        } else {
          return firstToken.getValue().compareTo(secondToken.getValue());
        }
      }
      return Integer.compare(p1, p2);
    };
  }

  /**
   * Clear.
   */
  private void clear() {
    tokenCollectionIndex = IntLists.mutable.empty();
    tokenCollection = IntObjectMaps.mutable.empty();
  }

}
