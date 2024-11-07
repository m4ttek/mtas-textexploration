package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentGroup.
 */
public final class ComponentGroup implements BasicComponent {

    /**
     * The span query.
     */
    private final MtasSpanQuery spanQuery;

    /**
     * The data type.
     */
    private final String dataType;

    /**
     * The stats type.
     */
    private final String statsType;

    /**
     * The sort type.
     */
    private final String sortType;

    /**
     * The sort direction.
     */
    private final String sortDirection;

    /**
     * The stats items.
     */
    private final SortedSet<String> statsItems;

    /**
     * The start.
     */
    private final Integer start;

    /**
     * The number.
     */
    private final Integer number;

    /**
     * The key.
     */
    private final String key;

    /**
     * The data collector.
     */
    private final MtasDataCollector<?, ?> dataCollector;

    /**
     * The prefixes.
     */
    private final List<String> prefixes;

    /**
     * The hit inside.
     */
    private final Set<String> hitInside;

    /**
     * The hit inside left.
     */
    private final Set<String>[] hitInsideLeft;

    /**
     * The hit inside right.
     */
    private final Set<String>[] hitInsideRight;

    /**
     * The hit left.
     */
    private final Set<String>[] hitLeft;

    /**
     * The hit right.
     */
    private final Set<String>[] hitRight;

    /**
     * The left.
     */
    private final Set<String>[] left;

    /**
     * The right.
     */
    private final Set<String>[] right;


    /**
     * Instantiates a new component group.
     *
     * @param spanQuery                      the span query
     * @param key                            the key
     * @param number                         the number
     * @param start                          the start
     * @param groupingHitInsidePrefixes      the grouping hit inside prefixes
     * @param groupingHitInsideLeftPosition  the grouping hit inside left position
     * @param groupingHitInsideLeftPrefixes  the grouping hit inside left prefixes
     * @param groupingHitInsideRightPosition the grouping hit inside right position
     * @param groupingHitInsideRightPrefixes the grouping hit inside right prefixes
     * @param groupingHitLeftPosition        the grouping hit left position
     * @param groupingHitLeftPrefixes        the grouping hit left prefixes
     * @param groupingHitRightPosition       the grouping hit right position
     * @param groupingHitRightPrefixes       the grouping hit right prefixes
     * @param groupingLeftPosition           the grouping left position
     * @param groupingLeftPrefixes           the grouping left prefixes
     * @param groupingRightPosition          the grouping right position
     * @param groupingRightPrefixes          the grouping right prefixes
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public ComponentGroup(MtasSpanQuery spanQuery, String key, int number, int start, String groupingHitInsidePrefixes,
                          String[] groupingHitInsideLeftPosition, String[] groupingHitInsideLeftPrefixes,
                          String[] groupingHitInsideRightPosition, String[] groupingHitInsideRightPrefixes,
                          String[] groupingHitLeftPosition, String[] groupingHitLeftPrefixes, String[] groupingHitRightPosition,
                          String[] groupingHitRightPrefixes, String[] groupingLeftPosition, String[] groupingLeftPrefixes,
                          String[] groupingRightPosition, String[] groupingRightPrefixes) throws IOException {
        this.spanQuery = spanQuery;
        this.key = key;
        this.dataType = CodecUtil.DATA_TYPE_LONG;
        this.sortType = CodecUtil.STATS_TYPE_SUM;
        this.sortDirection = CodecUtil.SORT_DESC;
        this.statsItems = CodecUtil.createStatsItems("n,sum,mean");
        this.statsType = CodecUtil.createStatsType(this.statsItems, this.sortType, null);
        this.start = start;
        this.number = number;
        HashSet<String> tmpPrefixes = new HashSet<>();
        // analyze grouping condition
        if (groupingHitInsidePrefixes != null) {
            hitInside = new HashSet<>();
            String[] tmpList = groupingHitInsidePrefixes.split(",");
            for (String tmpItem : tmpList) {
                if (!tmpItem.trim().isEmpty()) {
                    hitInside.add(tmpItem.trim());
                }
            }
            tmpPrefixes.addAll(hitInside);
        } else {
            hitInside = null;
        }
        hitInsideLeft = createPositionedPrefixes(tmpPrefixes, groupingHitInsideLeftPosition,
                groupingHitInsideLeftPrefixes);
        hitInsideRight = createPositionedPrefixes(tmpPrefixes, groupingHitInsideRightPosition,
                groupingHitInsideRightPrefixes);
        hitLeft = createPositionedPrefixes(tmpPrefixes, groupingHitLeftPosition, groupingHitLeftPrefixes);
        hitRight = createPositionedPrefixes(tmpPrefixes, groupingHitRightPosition, groupingHitRightPrefixes);
        left = createPositionedPrefixes(tmpPrefixes, groupingLeftPosition, groupingLeftPrefixes);
        right = createPositionedPrefixes(tmpPrefixes, groupingRightPosition, groupingRightPrefixes);
        prefixes = List.copyOf(tmpPrefixes);
        // datacollector
        dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_LIST, this.dataType, this.statsType,
                this.statsItems, this.sortType, this.sortDirection, this.start, this.number, null, null);
    }

    /**
     * Creates the positioned prefixes.
     *
     * @param prefixList the prefix list
     * @param position   the position
     * @param prefixes   the prefixes
     * @return the hash set[]
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private static HashSet<String>[] createPositionedPrefixes(HashSet<String> prefixList, String[] position,
                                                              String[] prefixes) throws IOException {
        Pattern p = Pattern.compile("^([0-9]+)(\\-([0-9]+))?$");
        Matcher m;
        if (position == null && prefixes == null) {
            return null;
        } else if (prefixes == null || position == null || position.length != prefixes.length) {
            throw new IOException("incorrect position/prefixes");
        } else if (position.length == 0) {
            return null;
        } else {
            // analyze positions
            int[][] tmpPosition = new int[position.length][];
            int maxPosition = -1;
            for (int i = 0; i < position.length; i++) {
                m = p.matcher(position[i]);
                if (m.find()) {
                    if (m.group(3) == null) {
                        int start = Integer.parseInt(m.group(1));
                        tmpPosition[i] = new int[] {start};
                        maxPosition = Math.max(maxPosition, start);
                    } else {
                        int start = Integer.parseInt(m.group(1));
                        int end = Integer.parseInt(m.group(3));
                        if (start > end) {
                            throw new IOException("incorrect position " + position[i]);
                        } else {
                            tmpPosition[i] = new int[end - start + 1];
                            for (int t = start; t <= end; t++) {
                                tmpPosition[i][t - start] = t;
                            }
                            maxPosition = Math.max(maxPosition, end);
                        }
                    }
                } else {
                    throw new IOException("incorrect position " + position[i]);
                }
            }
            @SuppressWarnings("unchecked")
            HashSet<String>[] result = new HashSet[maxPosition + 1];
            Arrays.fill(result, null);
            List<String> tmpPrefixList;
            String[] tmpList;
            for (int i = 0; i < tmpPosition.length; i++) {
                tmpList = prefixes[i].split(",");
                tmpPrefixList = new ArrayList<>();
                for (String tmpItem : tmpList) {
                    if (!tmpItem.trim().isEmpty()) {
                        tmpPrefixList.add(tmpItem.trim());
                    }
                }
                if (tmpPrefixList.isEmpty()) {
                    throw new IOException("incorrect prefixes " + prefixes[i]);
                }
                for (int t = 0; t < tmpPosition[i].length; t++) {
                    if (result[tmpPosition[i][t]] == null) {
                        result[tmpPosition[i][t]] = new HashSet<>();
                    }
                    result[tmpPosition[i][t]].addAll(tmpPrefixList);
                }
                prefixList.addAll(tmpPrefixList);
            }
            return result;
        }
    }


    boolean hasHitsInside() {
        return hitInside != null;
    }

    boolean hasHitsInsideLeft() {
        return hitInsideLeft != null;
    }

    boolean hasHitsInsideRight() {
        return hitInsideRight != null;
    }

    boolean hasLeft() {
        return left != null;
    }

    Set<String> getHitInside() {
        return hitInside;
    }

    Set<String>[] getHitInsideLeft() {
        return hitInsideLeft;
    }

    Set<String>[] getHitInsideRight() {
        return hitInsideRight;
    }

    Set<String>[] getHitLeft() {
        return hitLeft;
    }

    Set<String>[] getHitRight() {
        return hitRight;
    }

    Set<String>[] getLeft() {
        return left;
    }

    Set<String>[] getRight() {
        return right;
    }

    int leftSize() {
        return left != null ? left.length : 0;
    }

    boolean hasRight() {
        return right != null;
    }

    int rightSize() {
        return right != null ? right.length : 0;
    }

    boolean hasHitLeft() {
        return hitLeft != null;
    }

    int hitLeftSize() {
        return hitLeft != null ? hitLeft.length : 0;
    }

    boolean hasHitRight() {
        return hitRight != null;
    }

    int hitRightSize() {
        return hitRight != null ? hitRight.length : 0;
    }

    public MtasSpanQuery getSpanQuery() {
        return spanQuery;
    }

    public String getDataType() {
        return dataType;
    }

    public String getStatsType() {
        return statsType;
    }

    public String getSortType() {
        return sortType;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public SortedSet<String> getStatsItems() {
        return statsItems;
    }

    public Integer getStart() {
        return start;
    }

    public Integer getNumber() {
        return number;
    }

    public String getKey() {
        return key;
    }

    public MtasDataCollector<?, ?> getDataCollector() {
        return dataCollector;
    }

    List<String> getPrefixes() {
        return prefixes;
    }
}
