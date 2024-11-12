package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.regex.Pattern;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentList.
 */
public final class ComponentList implements BasicComponent {

    /**
     * The span query.
     */
    private final MtasSpanQuery spanQuery;

    /**
     * The field.
     */
    private final String field;

    /**
     * The query value.
     */
    private final String queryValue;

    /**
     * The query type.
     */
    private final String queryType;

    /**
     * The query prefix.
     */
    private final String queryPrefix;

    /**
     * The query ignore.
     */
    private final String queryIgnore;

    /**
     * The query maximum ignore length.
     */
    private final String queryMaximumIgnoreLength;

    /**
     * The key.
     */
    private final String key;

    /**
     * The query variables.
     */
    private final Map<String, String[]> queryVariables;

    /**
     * The tokens.
     */
    private final List<ListToken> tokens;

    /**
     * The hits.
     */
    private final List<ListHit> hits;

    /**
     * The unique key.
     */
    private final Map<Integer, String> uniqueKey;

    /**
     * The sub total.
     */
    private final Map<Integer, Integer> subTotal;

    /**
     * The min position.
     */
    private final Map<Integer, Integer> minPosition;

    /**
     * The max position.
     */
    private final Map<Integer, Integer> maxPosition;

    /**
     * The prefixes.
     */
    private final List<String> prefixes;

    /**
     * The field values.
     */
    private final Map<Integer, Map<String, Object>> fieldValues;

    /**
     * The field names.
     */
    private final List<String> fieldNames;

    /**
     * The left.
     */
    private final int left;

    /**
     * The right.
     */
    private final int right;

    /**
     * The total.
     */
    private final LongAccumulator total;

    /**
     * The position.
     */
    private final AtomicInteger position;

    /**
     * The start.
     */
    private final int start;

    /**
     * The number.
     */
    private final int number;

    /**
     * The field list.
     */
    private final String fieldList;

    /**
     * The prefix.
     */
    private final String prefix;

    /**
     * The output.
     */
    private final String output;

    /**
     * The Constant LIST_OUTPUT_TOKEN.
     */
    public static final String LIST_OUTPUT_TOKEN = "token";

    /**
     * The Constant LIST_OUTPUT_HIT.
     */
    public static final String LIST_OUTPUT_HIT = "hit";

    /**
     * Instantiates a new component list.
     *
     * @param spanQuery                the span query
     * @param field                    the field
     * @param queryValue               the query value
     * @param queryType                the query type
     * @param queryPrefix              the query prefix
     * @param queryVariables           the query variables
     * @param queryIgnore              the query ignore
     * @param queryMaximumIgnoreLength the query maximum ignore length
     * @param key                      the key
     * @param fieldList                the field list
     * @param prefix                   the prefix
     * @param start                    the start
     * @param number                   the number
     * @param left                     the left
     * @param right                    the right
     * @param output                   the output
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public ComponentList(MtasSpanQuery spanQuery, String field, String queryValue, String queryType, String queryPrefix,
                         Map<String, String[]> queryVariables, String queryIgnore, String queryMaximumIgnoreLength, String key,
                         String fieldList, String prefix, int start, int number, int left, int right, String output) throws IOException {
        this.spanQuery = spanQuery;
        this.field = field;
        this.queryValue = queryValue;
        this.queryType = queryType;
        this.queryPrefix = queryPrefix;
        this.queryIgnore = queryIgnore;
        this.queryMaximumIgnoreLength = queryMaximumIgnoreLength;
        this.queryVariables = Map.copyOf(queryVariables);
        this.key = key;
        this.fieldList = fieldList;
        this.left = left;
        this.right = right;
        this.start = start;
        this.number = number;
        this.prefix = prefix;
        this.total = new LongAccumulator(Long::max, 0);
        this.position = new AtomicInteger();
        this.tokens = new CopyOnWriteArrayList<>();
        this.hits = new CopyOnWriteArrayList<>();
        this.uniqueKey = new ConcurrentHashMap<>();
        this.subTotal = new ConcurrentHashMap<>();
        this.minPosition = new ConcurrentHashMap<>();
        this.maxPosition = new ConcurrentHashMap<>();

        var prefixesGatherer = new ArrayList<String>();
        if ((prefix != null) && (!prefix.trim().isEmpty())) {
            for (String ls : prefix.split(Pattern.quote(","))) {
                if (!ls.trim().isEmpty()) {
                    prefixesGatherer.add(ls.trim());
                }
            }
        }
        prefixes = List.copyOf(prefixesGatherer);

        fieldValues = new HashMap<>();

        var fieldNamesGatherer = new ArrayList<String>();
        if ((fieldList != null) && (!fieldList.trim().isEmpty())) {
            for (String ls : fieldList.split(Pattern.quote(","))) {
                if (!ls.trim().isEmpty()) {
                    fieldNamesGatherer.add(ls.trim());
                }
            }
        }
        this.fieldNames = List.copyOf(fieldNamesGatherer);

        // check output
        if (output == null) {
            if (!this.prefixes.isEmpty()) {
                this.output = ComponentList.LIST_OUTPUT_HIT;
            } else {
                this.output = ComponentList.LIST_OUTPUT_TOKEN;
            }
        } else if (!output.equals(ComponentList.LIST_OUTPUT_HIT)
                && !output.equals(ComponentList.LIST_OUTPUT_TOKEN)) {
            throw new IOException("unrecognized output '" + output + "'");
        } else {
            this.output = output;
        }
    }

    public MtasSpanQuery getSpanQuery() {
        return spanQuery;
    }

    public String getField() {
        return field;
    }

    public String getQueryMaximumIgnoreLength() {
        return queryMaximumIgnoreLength;
    }

    public String getQueryIgnore() {
        return queryIgnore;
    }

    public String getKey() {
        return key;
    }

    public String getQueryPrefix() {
        return queryPrefix;
    }

    public String getQueryValue() {
        return queryValue;
    }

    public String getQueryType() {
        return queryType;
    }

    public Map<String, String[]> getQueryVariables() {
        return queryVariables;
    }

    public List<ListToken> getTokens() {
        return tokens;
    }

    public List<ListHit> getHits() {
        return hits;
    }

    public Map<Integer, String> getUniqueKey() {
        return uniqueKey;
    }

    public Map<Integer, Integer> getSubTotal() {
        return subTotal;
    }

    public Map<Integer, Integer> getMinPosition() {
        return minPosition;
    }

    public Map<Integer, Integer> getMaxPosition() {
        return maxPosition;
    }

    public List<String> getPrefixes() {
        return prefixes;
    }

    public Map<Integer, Map<String, Object>> getFieldValues() {
        return fieldValues;
    }

    public List<String> getFieldNames() {
        return fieldNames;
    }

    public int getLeft() {
        return left;
    }

    public int getRight() {
        return right;
    }

    public long getTotal() {
        return total.longValue();
    }

    public int getPosition() {
        return position.get();
    }

    public int getStart() {
        return start;
    }

    public int getNumber() {
        return number;
    }

    public String getFieldList() {
        return fieldList;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getOutput() {
        return output;
    }

    public void addHit(ListHit listHit) {
        this.hits.add(listHit);
    }

    public void addToken(ListToken listToken) {
        this.tokens.add(listToken);
    }

    public void incrementPosition() {
        this.position.incrementAndGet();
    }

    public void accumulatePosition(int size) {
        this.position.addAndGet(size);
    }

    public void overwriteTotal(int position) {
        this.total.accumulate(position);
    }
}
