package mtas.codec.util;

import java.util.ArrayList;
import java.util.List;
import mtas.analysis.token.MtasTokenString;

/**
 * The Class PageWordData.
 */
public record PageWordData(List<PageWord> words) {

    /**
     * Adds the.
     *
     * @param token the token
     */
    public void add(MtasTokenString token) {
        words.add(new PageWord(token));
    }

    /**
     * Instantiates a new page word data.
     */
    public PageWordData() {
        this(new ArrayList<>());
    }
}
