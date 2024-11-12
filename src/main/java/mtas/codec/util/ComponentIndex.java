package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentIndex.
 */
public final class ComponentIndex implements BasicComponent {

    /**
     * The query.
     */
    public final MtasSpanQuery query;

    /**
     * The key.
     */
    public final String key;

    /**
     * The unique key.
     */
    public final Map<Integer, String> uniqueKey;

    /**
     * The min position.
     */
    public final Map<Integer, Integer> minPosition;

    /**
     * The max position.
     */
    public final Map<Integer, Integer> maxPosition;

    /**
     * The block size.
     */
    public final Integer blockSize;

    /**
     * The block number.
     */
    public Integer blockNumber;

    /**
     * The block number.
     */
    public final MtasSpanQuery blockQuery;

    /**
     * The match.
     */
    public final String match;

    /**
     * The list prefix.
     */
    public final List<String> listPrefixes;

    /**
     * The list number.
     */
    public final Integer listNumber;

    /**
     * The list sort.
     */
    public final String listSort;

    /**
     * The index items.
     */
    public final Map<Integer, List<IndexItem>> indexItems;

    /**
     * The Constant DEFAULT_INDEX_BLOCK_NUMBER.
     */
    public static final Integer DEFAULT_INDEX_BLOCK_NUMBER = 10;

    /**
     * The Constant INDEX_LIST_SORT_INDEX.
     */
    public static final String INDEX_LIST_SORT_INDEX = "index";

    /**
     * The Constant INDEX_LIST_SORT_COUNT.
     */
    public static final String INDEX_LIST_SORT_COUNT = "count";

    /**
     * The Constant INDEX_LIST_SORT_TFIBF.
     */
    public static final String INDEX_LIST_SORT_TFIBF = "tfibf";


    /**
     * Instantiates a new component index.
     *
     * @param query       the query
     * @param key         the key
     * @param blockSize   the block size
     * @param blockNumber the block number
     * @param blockQuery  the block query
     * @param match       the match
     * @param listPrefix  the list prefix
     * @param listNumber  the list number
     * @param listSort    the list sort
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public ComponentIndex(MtasSpanQuery query, String key, int blockSize,
                          int blockNumber, MtasSpanQuery blockQuery,
                          String match, String listPrefix, Integer listNumber, String listSort) throws IOException {
        uniqueKey = new ConcurrentHashMap<>();
        minPosition = new ConcurrentHashMap<>();
        maxPosition = new ConcurrentHashMap<>();
        this.query = query;
        this.key = key;
        //set and check block settings
        var absBlockSize = Math.abs(blockSize);
        this.blockSize = (absBlockSize > 0) ? absBlockSize : null;
        this.blockNumber = Math.abs(blockNumber);
        this.blockNumber = (this.blockNumber > 0) ? this.blockNumber : null;
        this.blockQuery = blockQuery;
        this.indexItems = new ConcurrentHashMap<>();
        if (this.blockSize == null && this.blockNumber == null && this.blockQuery == null) {
            this.blockNumber = DEFAULT_INDEX_BLOCK_NUMBER;
        } else if (this.blockSize != null && (this.blockNumber != null || this.blockQuery != null)) {
            throw new IOException("invalid block specification, mixture of blockTypes not allowed");
        } else if (this.blockNumber != null && this.blockQuery != null) {
            throw new IOException("invalid block specification, mixture of blockTypes not allowed");
        }
        //set and check match
        if (match == null) {
            this.match = CodecCollector.MATCH_INTERSECT;
        } else if (!(match.equals(CodecCollector.MATCH_INTERSECT) || match.equals(CodecCollector.MATCH_START) ||
                match.equals(CodecCollector.MATCH_COMPLETE))) {
            throw new IOException("unknown match specification for index");
        } else {
            this.match = match;
        }
        //set and check list
        this.listPrefixes = new ArrayList<>();
        if ((listPrefix != null) && (!listPrefix.trim().isEmpty())) {
            String[] l = listPrefix.split(Pattern.quote(","));
            for (String ls : l) {
                if (!ls.trim().isEmpty()) {
                    this.listPrefixes.add(ls.trim());
                }
            }
        }
        if (!listPrefixes.isEmpty()) {
            this.listNumber = (listNumber != null && listNumber > 0) ? listNumber : null;
            if (listSort == null) {
                this.listSort = INDEX_LIST_SORT_INDEX;
            } else if (!listSort.equals(INDEX_LIST_SORT_INDEX) && !listSort.equals(INDEX_LIST_SORT_COUNT) &&
                    !listSort.equals(INDEX_LIST_SORT_TFIBF)) {
                throw new IOException("unknown list sort specification for index");
            } else {
                this.listSort = listSort;
            }
        } else {
            this.listNumber = null;
            this.listSort = null;
        }
    }
}
