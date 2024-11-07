package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import mtas.codec.util.heatmap.HeatmapMtasCounter;
import mtas.parser.function.ParseException;
import mtas.parser.function.util.MtasFunctionParserFunction;
import mtas.parser.function.util.MtasFunctionParserFunctionDefault;
import mtas.search.spans.util.MtasSpanQuery;
import org.apache.lucene.spatial.prefix.PrefixTreeStrategy;
import org.locationtech.spatial4j.shape.Shape;

/**
 * The Class ComponentHeatmap.
 */
public final class ComponentHeatmap implements BasicComponent {

    /**
     * The key.
     */
    public String key;

    /**
     * The queries.
     */
    public List<MtasSpanQuery> queries;

    /**
     * The strategy.
     */
    public PrefixTreeStrategy strategy;

    /**
     * The bounds shape.
     */
    public Shape boundsShape;

    /**
     * The grid level.
     */
    public Integer gridLevel;

    /**
     * The max cells.
     */
    public int maxCells;

    /**
     * The data type.
     */
    public String dataType;

    /**
     * The stats type.
     */
    public String statsType;

    /**
     * The stats items.
     */
    public SortedSet<String> statsItems;

    /**
     * The minimum long.
     */
    public Long minimumLong;

    /**
     * The maximum long.
     */
    public Long maximumLong;

    /**
     * The parser.
     */
    public MtasFunctionParserFunction parser;

    /**
     * The hm.
     */
    public HeatmapMtasCounter.Heatmap hm;

    /**
     * The Constant DEFAULT_MAX_CELLS.
     */
    private static final int DEFAULT_MAX_CELLS = 100000;

    /**
     * Instantiates a new component heatmap.
     *
     * @param key                the key
     * @param queries            the queries
     * @param minimumDouble      the minimum double
     * @param maximumDouble      the maximum double
     * @param type               the type
     * @param functionKey        the function key
     * @param functionExpression the function expression
     * @param functionType       the function type
     * @param strategy           the strategy
     * @param boundsShape        the bounds shape
     * @param gridLevel          the grid level
     * @param maxCells           the max cells
     * @throws IOException    Signals that an I/O exception has occurred.
     * @throws ParseException the parse exception
     */
    public ComponentHeatmap(String key, MtasSpanQuery[] queries, Double minimumDouble, Double maximumDouble,
                            String type, String[] functionKey, String[] functionExpression, String[] functionType,
                            PrefixTreeStrategy strategy, Shape boundsShape, Integer gridLevel, Integer maxCells)
            throws IOException, ParseException {
        this.key = key;
        this.queries = List.of(queries);
        this.strategy = strategy;
        this.boundsShape = boundsShape;
        this.gridLevel = gridLevel;
        this.maxCells = maxCells == null ? DEFAULT_MAX_CELLS : maxCells;
        this.parser = new MtasFunctionParserFunctionDefault(queries.length);
        dataType = parser.getType();
        statsItems = CodecUtil.createStatsItems(type);
        statsType = CodecUtil.createStatsType(this.statsItems, null, parser);
        if (minimumDouble != null) {
            this.minimumLong = minimumDouble.longValue();
        } else {
            this.minimumLong = null;
        }
        if (maximumDouble != null) {
            this.maximumLong = maximumDouble.longValue();
        } else {
            this.maximumLong = null;
        }
        hm = null;
        HeatmapMtasCounter.createHeatmap(this);
        // init functions
        if (hm != null) {
            hm.functions = new ArrayList<>();
            if (functionKey != null && functionExpression != null && functionType != null) {
                if (functionKey.length == functionExpression.length && functionKey.length == functionType.length) {
                    for (int i = 0; i < functionKey.length; i++) {
                        SubComponentFunction scf =
                                new SubComponentFunction(DataCollector.COLLECTOR_TYPE_LIST, functionKey[i],
                                        functionExpression[i], functionType[i]);
                        scf.dataCollector.initNewList(hm.columns * hm.rows);
                        hm.functions.add(scf);
                    }
                }
            }
            // init datacollector
            hm.dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_LIST, dataType, statsType, statsItems,
                    null, null, null, null, null, null);
            hm.dataCollector.initNewList(hm.columns * hm.rows);
        } else {
            throw new IOException("couldn't define hm");
        }
    }

}
