package mtas.codec.util;

import java.util.List;
import mtas.analysis.token.MtasTokenString;

/**
 * The Class KwicToken.
 */
public record KwicToken(int startPosition, int endPosition, List<MtasTokenString> tokens) {

    /**
     * Instantiates a new kwic token.
     *
     * @param match  the match
     * @param tokens the tokens
     */
    public KwicToken(Match match, List<MtasTokenString> tokens) {
        this(match.startPosition(), match.endPosition() - 1, tokens);
    }
}
