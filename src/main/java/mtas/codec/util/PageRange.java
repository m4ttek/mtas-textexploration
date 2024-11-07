package mtas.codec.util;

import mtas.analysis.token.MtasTokenString;

/**
 * The Class PageRange.
 */
public record PageRange(int id, int start, int end, String prefix, String postfix, Integer parentId) {

    /**
     * Instantiates a new page range.
     *
     * @param token the token
     */
    public PageRange(MtasTokenString token) {
        this(token.getId(), token.getPositionStart(), token.getPositionEnd(), token.getPrefix(), token.getPostfix(), token.getParentId());
    }

}
