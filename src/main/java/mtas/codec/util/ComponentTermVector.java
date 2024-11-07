package mtas.codec.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import mtas.analysis.token.MtasToken;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.parser.function.ParseException;
import mtas.parser.function.util.MtasFunctionParserFunctionDefault;
import org.apache.lucene.util.BytesRef;

/**
 * The Class ComponentTermVector.
 */
public final class ComponentTermVector implements BasicComponent {

    /**
     * The key.
     */
    public final String key;

    /**
     * The prefix.
     */
    public final String prefix;

    /**
     * The distances.
     */
    public final List<SubComponentDistance> distances;

    /**
     * The regexp.
     */
    public final String regexp;

    /**
     * The ignore regexp.
     */
    public final String ignoreRegexp;

    /**
     * The boundary.
     */
    public final String boundary;

    /**
     * The full.
     */
    public final boolean full;

    /**
     * The list.
     */
    public final Set<String> list;

    /**
     * The ignore list.
     */
    public final Set<String> ignoreList;

    /**
     * The list regexp.
     */
    public final boolean listRegexp;

    /**
     * The ignore list regexp.
     */
    public final boolean ignoreListRegexp;

    /**
     * The functions.
     */
    public final List<SubComponentFunction> functions;

    /**
     * The number.
     */
    public final int number;

    /**
     * The start value.
     */
    public BytesRef startValue;

    /**
     * The sub component function.
     */
    public final SubComponentFunction subComponentFunction;

    /**
     * The boundary registration.
     */
    public final boolean boundaryRegistration;

    /**
     * The sort type.
     */
    public final String sortType;

    /**
     * The sort direction.
     */
    public final String sortDirection;

    /**
     * Instantiates a new component term vector.
     *
     * @param key                the key
     * @param prefix             the prefix
     * @param distanceKey        the distance key
     * @param distanceType       the distance type
     * @param distanceBase       the distance base
     * @param distanceParameter  the distance parameter
     * @param distanceMinimum    the distance minimum
     * @param distanceMaximum    the distance maximum
     * @param regexp             the regexp
     * @param full               the full
     * @param type               the type
     * @param sortType           the sort type
     * @param sortDirection      the sort direction
     * @param startValue         the start value
     * @param number             the number
     * @param functionKey        the function key
     * @param functionExpression the function expression
     * @param functionType       the function type
     * @param boundary           the boundary
     * @param list               the list
     * @param listRegexp         the list regexp
     * @param ignoreRegexp       the ignore regexp
     * @param ignoreList         the ignore list
     * @param ignoreListRegexp   the ignore list regexp
     * @throws IOException    Signals that an I/O exception has occurred.
     * @throws ParseException the parse exception
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ComponentTermVector(String key, String prefix, String[] distanceKey, String[] distanceType,
                               String[] distanceBase, Map[] distanceParameter, String[] distanceMinimum, String[] distanceMaximum,
                               String regexp, Boolean full, String type, String sortType, String sortDirection, String startValue, int number,
                               String[] functionKey, String[] functionExpression, String[] functionType, String boundary, String[] list,
                               Boolean listRegexp, String ignoreRegexp, String[] ignoreList, Boolean ignoreListRegexp)
            throws IOException, ParseException {

        String boundaryDefer;
        String sortTypeDefer;
        String sortDirectionDefer;
        this.key = key;
        this.prefix = prefix;
        distances = new ArrayList<>();
        if (distanceKey != null && distanceType != null && distanceBase != null && distanceParameter != null
                && distanceMaximum != null) {
            if (distanceKey.length == distanceType.length && distanceKey.length == distanceBase.length
                    && distanceKey.length == distanceParameter.length && distanceKey.length == distanceMaximum.length) {
                for (int i = 0; i < distanceKey.length; i++) {
                    SubComponentDistance item = new SubComponentDistance(distanceKey[i], distanceType[i], this.prefix,
                            distanceBase[i], distanceParameter[i], distanceMinimum[i], distanceMaximum[i]);
                    distances.add(item);
                }
            }
        }
        this.regexp = regexp;
        this.full = full != null && full;
        sortTypeDefer = Objects.requireNonNullElse(sortType, CodecUtil.SORT_TERM);
        if (sortDirection == null) {
            if (sortTypeDefer.equals(CodecUtil.SORT_TERM)) {
                sortDirectionDefer = CodecUtil.SORT_ASC;
            } else {
                sortDirectionDefer = CodecUtil.SORT_DESC;
            }
        } else {
            sortDirectionDefer = sortDirection;
        }
        if (list != null && list.length > 0) {
            this.list = new HashSet(Arrays.asList(list));
            this.listRegexp = listRegexp != null ? listRegexp : false;
            boundaryDefer = null;
            this.number = Integer.MAX_VALUE;
            if (!this.full) {
                sortTypeDefer = CodecUtil.SORT_TERM;
                sortDirectionDefer = CodecUtil.SORT_ASC;
            }
            this.startValue = null;
        } else {
            this.list = null;
            this.listRegexp = false;
            this.startValue = (startValue != null) ? new BytesRef(prefix + MtasToken.DELIMITER + startValue) : null;
            if (boundary == null) {
                boundaryDefer = null;
                if (number < -1) {
                    throw new IOException("number should not be " + number);
                } else if (number >= 0) {
                    this.number = number;
                } else {
                    if (!full) {
                        throw new IOException("number " + number + " only supported for full termvector");
                    } else {
                        this.number = Integer.MAX_VALUE;
                    }
                }
            } else {
                boundaryDefer = boundary;
                this.number = Integer.MAX_VALUE;
            }
        }
        this.sortType = sortTypeDefer;
        this.sortDirection = sortDirectionDefer;
        this.ignoreRegexp = ignoreRegexp;
        if (ignoreList != null && ignoreList.length > 0) {
            this.ignoreList = new HashSet(Arrays.asList(ignoreList));
            this.ignoreListRegexp = ignoreListRegexp != null ? ignoreListRegexp : false;
        } else {
            this.ignoreList = null;
            this.ignoreListRegexp = false;
        }
        functions = new ArrayList<>();
        if (functionKey != null && functionExpression != null && functionType != null) {
            if (functionKey.length == functionExpression.length && functionKey.length == functionType.length) {
                for (int i = 0; i < functionKey.length; i++) {
                    functions.add(new SubComponentFunction(DataCollector.COLLECTOR_TYPE_LIST, functionKey[i],
                            functionExpression[i], functionType[i]));
                }
            }
        }
        if (!this.sortType.equals(CodecUtil.SORT_TERM) && !CodecUtil.isStatsType(this.sortType)) {
            throw new IOException("unknown sortType '" + this.sortType + "'");
        } else if (!full && !this.sortType.equals(CodecUtil.SORT_TERM)) {
            if (!(this.sortType.equals(CodecUtil.STATS_TYPE_SUM) || this.sortType.equals(CodecUtil.STATS_TYPE_N))) {
                throw new IOException("sortType '" + this.sortType + "' only supported with full termVector");
            }
        }
        if (!this.sortType.equals(CodecUtil.SORT_TERM)) {
            if (startValue != null) {
                throw new IOException(
                        "startValue '" + startValue + "' only supported with termVector sorted on " + CodecUtil.SORT_TERM);
            }
        }
        if (!this.sortDirection.equals(CodecUtil.SORT_ASC) && !this.sortDirection.equals(CodecUtil.SORT_DESC)) {
            throw new IOException("unrecognized sortDirection '" + this.sortDirection + "'");
        }
        boundaryRegistration = boundaryDefer != null;
        String segmentRegistration = null;
        if (this.full) {
            boundaryDefer = null;
        } else if (boundaryDefer != null) {
            if (this.sortDirection.equals(CodecUtil.SORT_ASC)) {
                segmentRegistration = MtasDataCollector.SEGMENT_BOUNDARY_ASC;
            } else {
                segmentRegistration = MtasDataCollector.SEGMENT_BOUNDARY_DESC;
            }
        } else if (!this.sortType.equals(CodecUtil.SORT_TERM)) {
            if (this.sortDirection.equals(CodecUtil.SORT_ASC)) {
                segmentRegistration = MtasDataCollector.SEGMENT_SORT_ASC;
            } else {
                segmentRegistration = MtasDataCollector.SEGMENT_SORT_DESC;
            }
        }
        // create main subComponentFunction
        this.boundary = boundaryDefer;
        this.subComponentFunction = new SubComponentFunction(DataCollector.COLLECTOR_TYPE_LIST, key, type,
                new MtasFunctionParserFunctionDefault(1), this.sortType, this.sortDirection, 0, this.number,
                segmentRegistration, boundary);
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

}
