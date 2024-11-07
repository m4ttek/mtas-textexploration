package mtas.codec.util;

/**
 * The Interface ComponentStats.
 */
sealed public interface ComponentStats extends BasicComponent permits ComponentPosition, ComponentSpan, ComponentToken {
}
