package mtas.codec.util;

/**
 * The Interface BasicComponent.
 */
sealed public interface BasicComponent
        permits ComponentCollection, ComponentDocument, ComponentFacet, ComponentField, ComponentGroup, ComponentHeatmap, ComponentIndex,
        ComponentKwic, ComponentList, ComponentPage, ComponentPrefix, ComponentStats, ComponentStatus, ComponentTermVector, ComponentVersion {
}
