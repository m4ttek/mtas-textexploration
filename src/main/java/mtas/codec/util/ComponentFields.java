package mtas.codec.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Class ComponentFields.
 */
public class ComponentFields {

    /**
     * The status.
     */
    public ComponentStatus status;

    /**
     * The version.
     */
    public ComponentVersion version;

    /**
     * The list.
     */
    public final Map<String, ComponentField> list;

    /**
     * The collection.
     */
    public final List<ComponentCollection> collection;

    /**
     * The do document.
     */
    public boolean doDocument;

    /**
     * The do kwic.
     */
    public boolean doKwic;

    /**
     * The do list.
     */
    public boolean doList;

    /**
     * The do page.
     */
    public boolean doPage;

    /**
     * The do page.
     */
    public boolean doIndex;

    /**
     * The do heatmap.
     */
    public boolean doHeatmap;

    /**
     * The do group.
     */
    public boolean doGroup;

    /**
     * The do term vector.
     */
    public boolean doTermVector;

    /**
     * The do stats.
     */
    public boolean doStats;

    /**
     * The do stats spans.
     */
    public boolean doStatsSpans;

    /**
     * The do stats positions.
     */
    public boolean doStatsPositions;

    /**
     * The do stats tokens.
     */
    public boolean doStatsTokens;

    /**
     * The do prefix.
     */
    public boolean doPrefix;

    /**
     * The do facet.
     */
    public boolean doFacet;

    /**
     * The do collection.
     */
    public boolean doCollection;

    /**
     * The do status.
     */
    public boolean doStatus;

    /**
     * The do version.
     */
    public boolean doVersion;

    /**
     * Instantiates a new component fields.
     */
    public ComponentFields() {
        status = null;
        list = new HashMap<>();
        collection = new ArrayList<>();
        doDocument = false;
        doKwic = false;
        doList = false;
        doPage = false;
        doIndex = false;
        doHeatmap = false;
        doGroup = false;
        doStats = false;
        doTermVector = false;
        doStatsSpans = false;
        doStatsPositions = false;
        doStatsTokens = false;
        doPrefix = false;
        doFacet = false;
        doCollection = false;
        doStatus = false;
        doVersion = false;
    }
}
