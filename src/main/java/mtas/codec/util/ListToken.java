package mtas.codec.util;

import java.util.List;
import mtas.analysis.token.MtasTokenString;

/**
 * The Class ListToken.
 */
public record ListToken(Integer docId, Integer docPosition, int startPosition, int endPosition, List<MtasTokenString> tokens) {

    /**
     * Instantiates a new list token.
     *
     * @param docId       the doc id
     * @param docPosition the doc position
     * @param match       the match
     * @param tokens      the tokens
     */
    public ListToken(Integer docId, Integer docPosition, Match match, List<MtasTokenString> tokens) {
        this(docId, docPosition, match.startPosition(), match.endPosition() - 1, tokens);
    }
}
