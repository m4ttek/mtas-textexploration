package mtas.codec.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class ComponentField.
 */
public final class ComponentField implements BasicComponent {

    /**
     * The unique key field.
     */
    public final String uniqueKeyField;

    /**
     * The document list.
     */
    public final List<ComponentDocument> documentList;

    /**
     * The kwic list.
     */
    public final List<ComponentKwic> kwicList;

    /**
     * The list list.
     */
    public final List<ComponentList> listList;

    /**
     * The page list.
     */
    public final List<ComponentPage> pageList;

    /**
     * The index list.
     */
    public final List<ComponentIndex> indexList;

    /**
     * The heatmap list.
     */
    public final List<ComponentHeatmap> heatmapList;

    /**
     * The group list.
     */
    public final List<ComponentGroup> groupList;

    /**
     * The facet list.
     */
    public final List<ComponentFacet> facetList;

    /**
     * The term vector list.
     */
    public final List<ComponentTermVector> termVectorList;

    /**
     * The stats position list.
     */
    public final List<ComponentPosition> statsPositionList;

    /**
     * The stats token list.
     */
    public final List<ComponentToken> statsTokenList;

    /**
     * The stats span list.
     */
    public final List<ComponentSpan> statsSpanList;

    /**
     * The span query list.
     */
    public final List<MtasSpanQuery> spanQueryList;

    /**
     * The prefix.
     */
    public final AtomicReference<ComponentPrefix> prefix;

    /**
     * Instantiates a new component field.
     *
     * @param uniqueKeyField the unique key field
     */
    public ComponentField(String uniqueKeyField) {
        this.uniqueKeyField = uniqueKeyField;
        // initialise
        documentList = new ArrayList<>();
        kwicList = new ArrayList<>();
        listList = new ArrayList<>();
        pageList = new ArrayList<>();
        indexList = new ArrayList<>();
        heatmapList = new ArrayList<>();
        groupList = new ArrayList<>();
        facetList = new ArrayList<>();
        termVectorList = new ArrayList<>();
        statsPositionList = new ArrayList<>();
        statsTokenList = new ArrayList<>();
        statsSpanList = new ArrayList<>();
        spanQueryList = new ArrayList<>();
        prefix = new AtomicReference<>();
    }
}
