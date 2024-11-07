package mtas.codec.util;

import java.util.List;
import java.util.Map;

/**
 * The Class ListHit.
 */
public record ListHit(Integer docId, Integer docPosition, int startPosition, int endPosition, Map<Integer, List<String>> hits) {

    /**
     * Instantiates a new list hit.
     *
     * @param docId       the doc id
     * @param docPosition the doc position
     * @param match       the match
     * @param hits        the hits
     */
    public ListHit(Integer docId, Integer docPosition, Match match, Map<Integer, List<String>> hits) {
        this(docId, docPosition, match.startPosition(), match.endPosition() - 1, hits);
    }
}
