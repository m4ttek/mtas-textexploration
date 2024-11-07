package mtas.codec.util;

import java.util.ArrayList;
import java.util.List;
import mtas.analysis.token.MtasTokenString;

/**
 * The Class PageRangeData.
 */
public record PageRangeData(List<PageRange> ranges) {

    /**
     * Adds the.
     *
     * @param token the token
     */
    public void add(MtasTokenString token) {
        ranges.add(new PageRange(token));
    }

    /**
     * Instantiates a new page range data.
     */
    public PageRangeData() {
        this(new ArrayList<>());
    }
}
