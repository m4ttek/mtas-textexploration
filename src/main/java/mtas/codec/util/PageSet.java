package mtas.codec.util;

import mtas.analysis.token.MtasTokenString;

/**
 * The Class PageSet.
 */
public record PageSet(int id, int[] positions, String prefix, String postfix, Integer parentId) {

    /**
     * Instantiates a new page set.
     *
     * @param token the token
     */
    public PageSet(MtasTokenString token) {
        this(token.getId(), token.getPositions(), token.getPrefix(), token.getPostfix(), token.getParentId());
    }
}
