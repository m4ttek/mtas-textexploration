package mtas.codec.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Class IndexItem.
 */
public class IndexItem {

    /**
     * The start position.
     */
    final int startPosition;

    /**
     * The end position.
     */
    final int endPosition;

    /**
     * The name.
     */
    final String name;

    /**
     * The number.
     */
    int number;

    /**
     * The list.
     */
    final Map<List<Map<String, Set<String>>>, Integer> list;

    /**
     * Instantiates a new index item.
     *
     * @param startPosition the start position
     * @param endPosition   the end position
     * @param name          the name
     */
    public IndexItem(int startPosition, int endPosition, String name) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.name = name;
        this.number = 0;
        this.list = new HashMap<>();
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }

    public Map<List<Map<String, Set<String>>>, Integer> getList() {
        return list;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return (name == null ? startPosition + "-" + endPosition : name) + ":" + number;
    }
}
