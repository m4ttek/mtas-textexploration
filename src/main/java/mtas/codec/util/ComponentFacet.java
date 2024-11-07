package mtas.codec.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.parser.function.MtasFunctionParser;
import mtas.parser.function.ParseException;
import mtas.parser.function.util.MtasFunctionParserFunction;
import mtas.parser.function.util.MtasFunctionParserFunctionDefault;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentFacet.
 */
public final class ComponentFacet implements BasicComponent {

    /**
     * The span queries.
     */
    public final List<MtasSpanQuery> spanQueries;

    /**
     * The base fields.
     */
    public final String[] baseFields;

    /**
     * The base field types.
     */
    public final String[] baseFieldTypes;

    /**
     * The base types.
     */
    public final String[] baseTypes;

    /**
     * The base sort types.
     */
    public final String[] baseSortTypes;

    /**
     * The base sort directions.
     */
    public final String[] baseSortDirections;

    /**
     * The base range sizes.
     */
    public final Double[] baseRangeSizes;

    /**
     * The base range bases.
     */
    public final Double[] baseRangeBases;

    /**
     * The base collector types.
     */
    public final String[] baseCollectorTypes;

    /**
     * The base data types.
     */
    public final String[] baseDataTypes;

    /**
     * The base stats types.
     */
    public final String[] baseStatsTypes;

    /**
     * The base stats items.
     */
    public final SortedSet<String>[] baseStatsItems;

    /**
     * The key.
     */
    public final String key;

    /**
     * The data collector.
     */
    public final MtasDataCollector<?, ?> dataCollector;

    /**
     * The base function list.
     */
    public final HashMap<MtasDataCollector<?, ?>, SubComponentFunction[]>[] baseFunctionList;

    /**
     * The base numbers.
     */
    public final Integer[] baseNumbers;

    /**
     * The base minimum longs.
     */
    public final Long[] baseMinimumLongs;

    /**
     * The base maximum longs.
     */
    public final Long[] baseMaximumLongs;

    /**
     * The base parsers.
     */
    public final MtasFunctionParserFunction[] baseParsers;

    /**
     * The base function keys.
     */
    String[][] baseFunctionKeys;

    /**
     * The base function types.
     */
    String[][] baseFunctionTypes;

    /**
     * The base function parser functions.
     */
    public final MtasFunctionParserFunction[][] baseFunctionParserFunctions;

    /**
     * The Constant TYPE_STRING.
     */
    public static final String TYPE_STRING = "string";

    /**
     * The Constant TYPE_POINTFIELD_WITHOUT_DOCVALUES.
     */
    public static final String TYPE_POINTFIELD_WITHOUT_DOCVALUES = "pointfield_without_docvalues";

    /**
     * Instantiates a new component facet.
     *
     * @param spanQueries             the span queries
     * @param field                   the field
     * @param key                     the key
     * @param baseFields              the base fields
     * @param baseFieldTypes          the base field types
     * @param baseTypes               the base types
     * @param baseRangeSizes          the base range sizes
     * @param baseRangeBases          the base range bases
     * @param baseSortTypes           the base sort types
     * @param baseSortDirections      the base sort directions
     * @param baseNumbers             the base numbers
     * @param baseMinimumDoubles      the base minimum doubles
     * @param baseMaximumDoubles      the base maximum doubles
     * @param baseFunctionKeys        the base function keys
     * @param baseFunctionExpressions the base function expressions
     * @param baseFunctionTypes       the base function types
     * @throws IOException    Signals that an I/O exception has occurred.
     * @throws ParseException the parse exception
     */
    @SuppressWarnings("unchecked")
    public ComponentFacet(MtasSpanQuery[] spanQueries, String field, String key, String[] baseFields,
                          String[] baseFieldTypes, String[] baseTypes, Double[] baseRangeSizes, Double[] baseRangeBases,
                          String[] baseSortTypes, String[] baseSortDirections, Integer[] baseNumbers, Double[] baseMinimumDoubles,
                          Double[] baseMaximumDoubles, String[][] baseFunctionKeys, String[][] baseFunctionExpressions,
                          String[][] baseFunctionTypes) throws IOException, ParseException {
        this.spanQueries = List.of(spanQueries);
        this.key = key;
        this.baseFields = (String[]) baseFields.clone();
        this.baseFieldTypes = (String[]) baseFieldTypes.clone();
        this.baseTypes = (String[]) baseTypes.clone();
        this.baseRangeSizes = (Double[]) baseRangeSizes.clone();
        this.baseRangeBases = (Double[]) baseRangeBases.clone();
        this.baseSortTypes = (String[]) baseSortTypes.clone();
        this.baseSortDirections = (String[]) baseSortDirections.clone();
        this.baseNumbers = (Integer[]) baseNumbers.clone();
        // compute types
        this.baseMinimumLongs = new Long[baseFields.length];
        this.baseMaximumLongs = new Long[baseFields.length];
        this.baseCollectorTypes = new String[baseFields.length];
        this.baseStatsItems = new SortedSet[baseFields.length];
        this.baseStatsTypes = new String[baseFields.length];
        this.baseDataTypes = new String[baseFields.length];
        this.baseParsers = new MtasFunctionParserFunction[baseFields.length];
        this.baseFunctionList = new HashMap[baseFields.length];
        this.baseFunctionParserFunctions = new MtasFunctionParserFunction[baseFields.length][];
        for (int i = 0; i < baseFields.length; i++) {
            if (baseMinimumDoubles[i] != null) {
                this.baseMinimumLongs[i] = baseMinimumDoubles[i].longValue();
            } else {
                this.baseMinimumLongs[i] = null;
            }
            if (baseMaximumDoubles[i] != null) {
                this.baseMaximumLongs[i] = baseMaximumDoubles[i].longValue();
            } else {
                this.baseMaximumLongs[i] = null;
            }
            baseDataTypes[i] = CodecUtil.DATA_TYPE_LONG;
            baseFunctionList[i] = new HashMap<>();
            baseFunctionParserFunctions[i] = null;
            baseParsers[i] = new MtasFunctionParserFunctionDefault(this.spanQueries.size());
            if (this.baseSortDirections[i] == null) {
                this.baseSortDirections[i] = CodecUtil.SORT_ASC;
            } else if (!this.baseSortDirections[i].equals(CodecUtil.SORT_ASC)
                    && !this.baseSortDirections[i].equals(CodecUtil.SORT_DESC)) {
                throw new IOException("unrecognized sortDirection " + this.baseSortDirections[i]);
            }
            if (this.baseSortTypes[i] == null) {
                this.baseSortTypes[i] = CodecUtil.SORT_TERM;
            } else if (!this.baseSortTypes[i].equals(CodecUtil.SORT_TERM)
                    && !CodecUtil.isStatsType(this.baseSortTypes[i])) {
                throw new IOException("unrecognized sortType " + this.baseSortTypes[i]);
            }
            this.baseCollectorTypes[i] = DataCollector.COLLECTOR_TYPE_LIST;
            this.baseStatsItems[i] = CodecUtil.createStatsItems(this.baseTypes[i]);
            this.baseStatsTypes[i] = CodecUtil.createStatsType(baseStatsItems[i], this.baseSortTypes[i],
                    new MtasFunctionParserFunctionDefault(1));
        }
        boolean doFunctions = baseFunctionKeys != null && baseFunctionExpressions != null && baseFunctionTypes != null;
        doFunctions = doFunctions && baseFunctionKeys.length == baseFields.length;
        doFunctions = doFunctions && baseFunctionTypes.length == baseFields.length;
        if (doFunctions) {
            this.baseFunctionKeys = new String[baseFields.length][];
            /** The base function expressions. */
//          String[][] baseFunctionExpressions1 = new String[baseFields.length][];
            this.baseFunctionTypes = new String[baseFields.length][];
            for (int i = 0; i < baseFields.length; i++) {
                if (baseFunctionKeys[i].length == baseFunctionExpressions[i].length
                        && baseFunctionKeys[i].length == baseFunctionTypes[i].length) {
                    this.baseFunctionKeys[i] = new String[baseFunctionKeys[i].length];
//            baseFunctionExpressions1[i] = new String[baseFunctionExpressions[i].length];
                    this.baseFunctionTypes[i] = new String[baseFunctionTypes[i].length];
                    baseFunctionParserFunctions[i] = new MtasFunctionParserFunction[baseFunctionExpressions[i].length];
                    for (int j = 0; j < baseFunctionKeys[i].length; j++) {
                        this.baseFunctionKeys[i][j] = baseFunctionKeys[i][j];
//              baseFunctionExpressions1[i][j] = baseFunctionExpressions[i][j];
                        this.baseFunctionTypes[i][j] = baseFunctionTypes[i][j];
                        baseFunctionParserFunctions[i][j] = new MtasFunctionParser(
                                new BufferedReader(new StringReader(baseFunctionExpressions[i][j]))).parse();
                    }
                } else {
                    this.baseFunctionKeys[i] = new String[0];
//            baseFunctionExpressions1[i] = new String[0];
                    this.baseFunctionTypes[i] = new String[0];
                    baseFunctionParserFunctions[i] = new MtasFunctionParserFunction[0];
                }
            }
        }
        if (baseFields.length > 0) {
            if (baseFields.length == 1) {
                dataCollector = DataCollector.getCollector(this.baseCollectorTypes[0], this.baseDataTypes[0],
                        this.baseStatsTypes[0], this.baseStatsItems[0], this.baseSortTypes[0], this.baseSortDirections[0], 0,
                        this.baseNumbers[0], null, null);
            } else {
                String[] subBaseCollectorTypes = Arrays.copyOfRange(baseCollectorTypes, 1, baseDataTypes.length);
                String[] subBaseDataTypes = Arrays.copyOfRange(baseDataTypes, 1, baseDataTypes.length);
                String[] subBaseStatsTypes = Arrays.copyOfRange(baseStatsTypes, 1, baseStatsTypes.length);
                SortedSet<String>[] subBaseStatsItems = Arrays.copyOfRange(baseStatsItems, 1, baseStatsItems.length);
                String[] subBaseSortTypes = Arrays.copyOfRange(baseSortTypes, 1, baseSortTypes.length);
                String[] subBaseSortDirections = Arrays.copyOfRange(baseSortDirections, 1, baseSortDirections.length);
                Integer[] subNumbers = Arrays.copyOfRange(baseNumbers, 1, baseNumbers.length);
                Integer[] subStarts = Arrays.stream(new int[subNumbers.length]).boxed().toArray(Integer[]::new);

                dataCollector = DataCollector.getCollector(this.baseCollectorTypes[0], this.baseDataTypes[0],
                        this.baseStatsTypes[0], this.baseStatsItems[0], this.baseSortTypes[0], this.baseSortDirections[0], 0,
                        this.baseNumbers[0], subBaseCollectorTypes, subBaseDataTypes, subBaseStatsTypes, subBaseStatsItems,
                        subBaseSortTypes, subBaseSortDirections, subStarts, subNumbers, null, null);
            }
        } else {
            throw new IOException("no baseFields");
        }
    }

    /**
     * Function need positions.
     *
     * @return true, if successful
     */
    public boolean functionNeedPositions() {
        if (baseFunctionParserFunctions != null) {
            for (int i = 0; i < baseFields.length; i++) {
                for (MtasFunctionParserFunction function : baseFunctionParserFunctions[i]) {
                    if (function.needPositions()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Base parser need positions.
     *
     * @return true, if successful
     */
    public boolean baseParserNeedPositions() {
        for (int i = 0; i < baseFields.length; i++) {
            if (baseParsers[i].needPositions()) {
                return true;
            }
        }
        return false;
    }

}
