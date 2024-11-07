package mtas.codec.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import mtas.codec.util.collector.MtasDataCollector;

/**
 * The Class ComponentDocument.
 */
public final class ComponentDocument implements BasicComponent {

    /**
     * The key.
     */
    private final String key;

    /**
     * The prefix.
     */
    private final String prefix;

    /**
     * The regexp.
     */
    private final String regexp;

    /**
     * The ignore regexp.
     */
    private final String ignoreRegexp;

    /**
     * The list.
     */
    private final Set<String> list;

    /**
     * The ignore list.
     */
    private final Set<String> ignoreList;

    /**
     * The list regexp.
     */
    private final boolean listRegexp;

    /**
     * The list expand.
     */
    private final boolean listExpand;

    /**
     * The ignore list regexp.
     */
    private final boolean ignoreListRegexp;

    /**
     * The list expand number.
     */
    private final int listExpandNumber;

    /**
     * The data type.
     */
    private final String dataType;

    /**
     * The stats type.
     */
    private final String statsType;

    /**
     * The stats items.
     */
    private final SortedSet<String> statsItems;

    /**
     * The list number.
     */
    private final int listNumber;

    /**
     * The unique key.
     */
    private final Map<Integer, String> uniqueKey;

    /**
     * The stats data.
     */
    private final Map<Integer, MtasDataCollector<?, ?>> statsData;

    /**
     * The stats list.
     */
    private final Map<Integer, MtasDataCollector<?, ?>> statsList;

    /**
     * Instantiates a new component document.
     *
     * @param key              the key
     * @param prefix           the prefix
     * @param statsType        the stats type
     * @param regexp           the regexp
     * @param list             the list
     * @param listNumber       the list number
     * @param listRegexp       the list regexp
     * @param listExpand       the list expand
     * @param listExpandNumber the list expand number
     * @param ignoreRegexp     the ignore regexp
     * @param ignoreList       the ignore list
     * @param ignoreListRegexp the ignore list regexp
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public ComponentDocument(String key, String prefix, String statsType, String regexp, String[] list, int listNumber,
                             Boolean listRegexp, Boolean listExpand, int listExpandNumber, String ignoreRegexp, String[] ignoreList,
                             Boolean ignoreListRegexp) throws IOException {
        this.key = key;
        this.prefix = prefix;
        this.regexp = regexp;
        if (list != null && list.length > 0) {
            this.list = new HashSet<>(Arrays.asList(list));
            this.listRegexp = listRegexp != null ? listRegexp : false;
            this.listExpand = (listExpand != null && listExpandNumber > 0) ? listExpand : false;
            if (this.listExpand) {
                this.listExpandNumber = listExpandNumber;
            } else {
                this.listExpandNumber = 0;
            }
        } else {
            this.list = null;
            this.listRegexp = false;
            this.listExpand = false;
            this.listExpandNumber = 0;
        }
        this.ignoreRegexp = ignoreRegexp;
        if (ignoreList != null && ignoreList.length > 0) {
            this.ignoreList = new HashSet<>(Arrays.asList(ignoreList));
            this.ignoreListRegexp = ignoreListRegexp != null ? ignoreListRegexp : false;
        } else {
            this.ignoreList = null;
            this.ignoreListRegexp = false;
        }
        this.listNumber = listNumber;
        uniqueKey = new HashMap<>();
        dataType = CodecUtil.DATA_TYPE_LONG;
        statsItems = CodecUtil.createStatsItems(statsType);
        this.statsType = CodecUtil.createStatsType(statsItems, null, null);
        this.statsData = new HashMap<>();
        if (this.listNumber > 0) {
            this.statsList = new HashMap<>();
        } else {
            this.statsList = null;
        }
    }

    public String getKey() {
        return key;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRegexp() {
        return regexp;
    }

    public String getIgnoreRegexp() {
        return ignoreRegexp;
    }

    public Set<String> getList() {
        return list;
    }

    public Set<String> getIgnoreList() {
        return ignoreList;
    }

    public boolean isListRegexp() {
        return listRegexp;
    }

    public boolean isListExpand() {
        return listExpand;
    }

    public boolean isIgnoreListRegexp() {
        return ignoreListRegexp;
    }

    public int getListExpandNumber() {
        return listExpandNumber;
    }

    public String getDataType() {
        return dataType;
    }

    public String getStatsType() {
        return statsType;
    }

    public SortedSet<String> getStatsItems() {
        return statsItems;
    }

    public int getListNumber() {
        return listNumber;
    }

    public Map<Integer, String> getUniqueKey() {
        return uniqueKey;
    }

    public Map<Integer, MtasDataCollector<?, ?>> getStatsData() {
        return statsData;
    }

    public Map<Integer, MtasDataCollector<?, ?>> getStatsList() {
        return statsList;
    }
}
