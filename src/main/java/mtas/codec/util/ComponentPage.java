package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Class ComponentPage.
 */
public final class ComponentPage implements BasicComponent {
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
     * The word list.
     */
    public final Map<Integer, Map<Integer, PageWordData>> wordList;

    /**
     * The range list.
     */
    public final Map<Integer, Map<Integer, PageRangeData>> rangeList;

    /**
     * The set list.
     */
    public final Map<Integer, Map<Integer, PageSetData>> setList;

    /**
     * The prefixes.
     */
    public final List<String> prefixes;

    /**
     * The start.
     */
    public final int start;

    /**
     * The end.
     */
    public final int end;

    /**
     * Instantiates a new component list.
     *
     * @param field  the field
     * @param key    the key
     * @param prefix the prefix
     * @param start  the start
     * @param end    the end
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public ComponentPage(String field, String key, String prefix, int start, int end) throws IOException {
        this.key = key;
        this.start = start;
        this.end = end;
        uniqueKey = new ConcurrentHashMap<>();
        minPosition = new ConcurrentHashMap<>();
        maxPosition = new ConcurrentHashMap<>();
        wordList = new ConcurrentHashMap<>();
        rangeList = new ConcurrentHashMap<>();
        setList = new ConcurrentHashMap<>();
        var prefixesGatherer = new ArrayList<String>();
        if ((prefix != null) && (!prefix.trim().isEmpty())) {
            String[] l = prefix.split(",");
            for (String ls : l) {
                if (!ls.trim().isEmpty()) {
                    prefixesGatherer.add(ls.trim());
                }
            }
        }
        this.prefixes = List.copyOf(prefixesGatherer);
    }

}
