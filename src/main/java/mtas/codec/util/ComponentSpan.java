package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.parser.function.ParseException;
import mtas.parser.function.util.MtasFunctionParserFunction;
import mtas.parser.function.util.MtasFunctionParserFunctionDefault;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentSpan.
 */
public final class ComponentSpan implements ComponentStats {

    /**
     * The queries.
     */
    private final List<MtasSpanQuery> queries;

    /**
     * The key.
     */
    public final String key;

    /**
     * The data type.
     */
    public final String dataType;

    /**
     * The stats type.
     */
    public final String statsType;

    /**
     * The stats items.
     */
    public final SortedSet<String> statsItems;

    /**
     * The minimum long.
     */
    public final Long minimumLong;

    /**
     * The maximum long.
     */
    public final Long maximumLong;

    /**
     * The data collector.
     */
    public final MtasDataCollector<?, ?> dataCollector;

    /**
     * The functions.
     */
    public final List<SubComponentFunction> functions;

    /**
     * The parser.
     */
    public final MtasFunctionParserFunction parser;

    /**
     * Instantiates a new component span.
     *
     * @param queries            the queries
     * @param key                the key
     * @param minimumDouble      the minimum double
     * @param maximumDouble      the maximum double
     * @param type               the type
     * @param functionKey        the function key
     * @param functionExpression the function expression
     * @param functionType       the function type
     * @throws IOException    Signals that an I/O exception has occurred.
     * @throws ParseException the parse exception
     */
    public ComponentSpan(MtasSpanQuery[] queries, String key, Double minimumDouble, Double maximumDouble, String type,
                         String[] functionKey, String[] functionExpression, String[] functionType) throws IOException, ParseException {
        this.queries = List.of(queries.clone());
        this.key = key;
        functions = new ArrayList<>();
        if (functionKey != null && functionExpression != null && functionType != null) {
            if (functionKey.length == functionExpression.length && functionKey.length == functionType.length) {
                for (int i = 0; i < functionKey.length; i++) {
                    functions.add(new SubComponentFunction(DataCollector.COLLECTOR_TYPE_DATA, functionKey[i],
                            functionExpression[i], functionType[i]));
                }
            }
        }
        parser = new MtasFunctionParserFunctionDefault(queries.length);
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
        dataCollector = DataCollector.getCollector(DataCollector.COLLECTOR_TYPE_DATA, dataType, this.statsType,
                this.statsItems, null, null, null, null, null, null);
    }

    /**
     * Function sum rule.
     *
     * @return true, if successful
     */
    public boolean functionSumRule() {
        if (functions != null) {
            for (SubComponentFunction function : functions) {
                if (!function.parserFunction.sumRule()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Function basic.
     *
     * @return true, if successful
     */
    public boolean functionBasic() {
        if (functions != null) {
            for (SubComponentFunction function : functions) {
                if (!function.statsType.equals(CodecUtil.STATS_BASIC)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Function need positions.
     *
     * @return true, if successful
     */
    public boolean functionNeedPositions() {
        if (functions != null) {
            for (SubComponentFunction function : functions) {
                if (function.parserFunction.needPositions()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Function need arguments.
     *
     * @return the sets the
     */
    public Set<Integer> functionNeedArguments() {
        Set<Integer> list = new HashSet<>();
        if (functions != null) {
            for (SubComponentFunction function : functions) {
                list.addAll(function.parserFunction.needArgument());
            }
        }
        return list;
    }

    public List<MtasSpanQuery> getQueries() {
        return queries;
    }
}
