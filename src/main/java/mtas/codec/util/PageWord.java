package mtas.codec.util;

import mtas.analysis.token.MtasTokenString;

/**
 * The Class PageWord.
 */
public record PageWord(int id, String prefix, String postfix, Integer parentId) {

    /**
     * Instantiates a new page word.
     *
     * @param token the token
     */
    public PageWord(MtasTokenString token) {
        this(token.getId(), token.getPrefix(), token.getPostfix(), token.getParentId());
    }

}
