package mtas.codec.util;

import java.util.ArrayList;
import java.util.List;
import mtas.analysis.token.MtasTokenString;

/**
 * The Class PageSetData.
 */
public record PageSetData(List<PageSet> sets) {

    /**
     * Adds the.
     *
     * @param token the token
     */
    public void add(MtasTokenString token) {
        sets.add(new PageSet(token));
    }

    /**
     * Instantiates a new page set data.
     */
    public PageSetData() {
        this(new ArrayList<>());
    }
}
