package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentKwic.
 */
public final class ComponentKwic implements BasicComponent {

    /**
     * The query.
     */
    private final MtasSpanQuery query;

    /**
     * The key.
     */
    private final String key;

    /**
     * The tokens.
     */
    private final Map<Integer, List<KwicToken>> tokens;

    /**
     * The hits.
     */
    private final Map<Integer, List<KwicHit>> hits;

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
     * The left.
     */
    private final int left;

    /**
     * The right.
     */
    private final int right;

    /**
     * The start.
     */
    private final int start;

    /**
     * The number.
     */
    private final Integer number;

    /**
     * The page start.
     */
    private final Integer pageStart;

    /**
     * The page end.
     */
    private final Integer pageEnd;

    /**
     * The output.
     */
    private final String output;

    /**
     * The Constant KWIC_OUTPUT_TOKEN.
     */
    public static final String KWIC_OUTPUT_TOKEN = "token";

    /**
     * The Constant KWIC_OUTPUT_HIT.
     */
    public static final String KWIC_OUTPUT_HIT = "hit";

    /**
     * Instantiates a new component kwic.
     *
     * @param query     the query
     * @param key       the key
     * @param prefixes  the prefixes
     * @param number    the number
     * @param start     the start
     * @param pageStart the page start
     * @param pageEnd   the page end
     * @param left      the left
     * @param right     the right
     * @param output    the output
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public ComponentKwic(MtasSpanQuery query, String key, String prefixes, Integer number, int start, Integer pageStart,
                         Integer pageEnd, int left, int right, String output) throws IOException {
        this.query = query;
        this.key = key;
        this.left = Math.max(left, 0);
        this.right = Math.max(right, 0);
        this.start = Math.max(start, 0);
        this.number = (number != null && number >= 0) ? number : null;
        this.pageStart = (pageStart != null && pageEnd != null) ? pageStart : null;
        this.pageEnd = (pageStart != null && pageEnd != null) ? pageEnd : null;
        tokens = new ConcurrentHashMap<>();
        hits = new ConcurrentHashMap<>();
        uniqueKey = new ConcurrentHashMap<>();
        subTotal = new ConcurrentHashMap<>();
        minPosition = new ConcurrentHashMap<>();
        maxPosition = new ConcurrentHashMap<>();
        if ((prefixes != null) && (!prefixes.trim().isEmpty())) {
            var prefixesGatherer = new ArrayList<String>();
            for (String ls : prefixes.split(",")) {
                if (!ls.trim().isEmpty()) {
                    prefixesGatherer.add(ls.trim());
                }
            }
            this.prefixes = List.copyOf(prefixesGatherer);
        } else {
            this.prefixes = List.of();
        }
        if (output == null) {
            if (!this.prefixes.isEmpty()) {
                this.output = ComponentKwic.KWIC_OUTPUT_HIT;
            } else {
                this.output = ComponentKwic.KWIC_OUTPUT_TOKEN;
            }
        } else if (!output.equals(ComponentKwic.KWIC_OUTPUT_HIT)
                && !output.equals(ComponentKwic.KWIC_OUTPUT_TOKEN)) {
            throw new IOException("unrecognized output '" + output + "'");
        } else {
            this.output = output;
        }
    }

    public MtasSpanQuery getQuery() {
        return query;
    }

    public String getKey() {
        return key;
    }

    public Map<Integer, List<KwicToken>> getTokens() {
        return tokens;
    }

    public Map<Integer, List<KwicHit>> getHits() {
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

    public int getLeft() {
        return left;
    }

    public int getRight() {
        return right;
    }

    public int getStart() {
        return start;
    }

    public Integer getNumber() {
        return number;
    }

    public Integer getPageStart() {
        return pageStart;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public String getOutput() {
        return output;
    }
}
