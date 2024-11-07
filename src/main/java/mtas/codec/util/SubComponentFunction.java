package mtas.codec.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.SortedSet;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.parser.function.MtasFunctionParser;
import mtas.parser.function.ParseException;
import mtas.parser.function.util.MtasFunctionParserFunction;

/**
 * The Class SubComponentFunction.
 */
public class SubComponentFunction {

    /**
     * The key.
     */
    public final String key;

    /**
     * The expression.
     */
    public final String expression;

    /**
     * The type.
     */
    public final String type;

    /**
     * The parser function.
     */
    public final MtasFunctionParserFunction parserFunction;

    /**
     * The stats type.
     */
    public final String statsType;

    /**
     * The data type.
     */
    public final String dataType;

    /**
     * The sort type.
     */
    public final String sortType;

    /**
     * The sort direction.
     */
    public final String sortDirection;

    /**
     * The stats items.
     */
    public final SortedSet<String> statsItems;

    /**
     * The data collector.
     */
    public final MtasDataCollector<?, ?> dataCollector;

    /**
     * Instantiates a new sub component function.
     *
     * @param collectorType       the collector type
     * @param key                 the key
     * @param type                the type
     * @param parserFunction      the parser function
     * @param sortType            the sort type
     * @param sortDirection       the sort direction
     * @param start               the start
     * @param number              the number
     * @param segmentRegistration the segment registration
     * @param boundary            the boundary
     * @throws ParseException the parse exception
     * @throws IOException    Signals that an I/O exception has occurred.
     */
    public SubComponentFunction(String collectorType, String key, String type,
                                MtasFunctionParserFunction parserFunction, String sortType, String sortDirection, Integer start, Integer number,
                                String segmentRegistration, String boundary) throws ParseException, IOException {
        this.key = key;
        this.expression = null;
        this.type = type;
        this.parserFunction = parserFunction;
        this.sortType = sortType;
        this.sortDirection = sortDirection;
        this.dataType = parserFunction.getType();
        this.statsItems = CodecUtil.createStatsItems(this.type);
        this.statsType = CodecUtil.createStatsType(statsItems, sortType, parserFunction);
        if (collectorType.equals(DataCollector.COLLECTOR_TYPE_LIST)) {
            dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_LIST, dataType, statsType, statsItems,
                    sortType, sortDirection, start, number, null, null, null, null, null, null, null, null, segmentRegistration,
                    boundary);
        } else if (collectorType.equals(DataCollector.COLLECTOR_TYPE_DATA)) {
            dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_DATA, dataType, statsType, statsItems,
                    sortType, sortDirection, start, number, segmentRegistration, boundary);
        } else {
            dataCollector = null;
        }
    }

    /**
     * Instantiates a new sub component function.
     *
     * @param collectorType the collector type
     * @param key           the key
     * @param expression    the expression
     * @param type          the type
     * @throws ParseException the parse exception
     * @throws IOException    Signals that an I/O exception has occurred.
     */
    public SubComponentFunction(String collectorType, String key, String expression, String type)
            throws ParseException, IOException {
        this.key = key;
        this.expression = expression;
        this.type = type;
        this.sortType = null;
        this.sortDirection = null;
        parserFunction = new MtasFunctionParser(new BufferedReader(new StringReader(this.expression))).parse();
        dataType = parserFunction.getType();
        statsItems = CodecUtil.createStatsItems(this.type);
        statsType = CodecUtil.createStatsType(statsItems, null, parserFunction);
        if (collectorType.equals(DataCollector.COLLECTOR_TYPE_LIST)) {
            dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_LIST, dataType, statsType, statsItems,
                    sortType, sortDirection, 0, Integer.MAX_VALUE, null, null);
        } else if (collectorType.equals(DataCollector.COLLECTOR_TYPE_DATA)) {
            dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_DATA, dataType, statsType, statsItems,
                    sortType, sortDirection, null, null, null, null);
        } else {
            dataCollector = null;
        }
    }
}
