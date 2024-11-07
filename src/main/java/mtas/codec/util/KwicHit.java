package mtas.codec.util;

import java.util.List;
import java.util.Map;

/**
 * The Class KwicHit.
 */
public record KwicHit(int startPosition, int endPosition, Map<Integer, List<String>> hits) {

    /**
     * Instantiates a new kwic hit.
     *
     * @param match the match
     * @param hits  the hits
     */
    public KwicHit(Match match, Map<Integer, List<String>> hits) {
        this(match.startPosition(), match.endPosition() - 1, hits);
    }
}
