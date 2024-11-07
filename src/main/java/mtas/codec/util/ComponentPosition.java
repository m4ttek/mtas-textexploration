package mtas.codec.util;

import java.io.IOException;
import java.util.SortedSet;
import mtas.codec.util.collector.MtasDataCollector;
import mtas.parser.function.ParseException;

/**
 * The Class ComponentPosition.
 */
public final class ComponentPosition implements ComponentStats {

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
     * Instantiates a new component position.
     *
     * @param key           the key
     * @param minimumDouble the minimum double
     * @param maximumDouble the maximum double
     * @param statsType     the stats type
     * @throws IOException    Signals that an I/O exception has occurred.
     * @throws ParseException the parse exception
     */
    public ComponentPosition(String key, Double minimumDouble, Double maximumDouble, String statsType)
            throws IOException, ParseException {
        this.key = key;
        dataType = CodecUtil.DATA_TYPE_LONG;
        this.statsItems = CodecUtil.createStatsItems(statsType);
        this.statsType = CodecUtil.createStatsType(this.statsItems, null, null);
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
}
