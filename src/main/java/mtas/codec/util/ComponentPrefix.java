package mtas.codec.util;

import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * The Class ComponentPrefix.
 */
public final class ComponentPrefix implements BasicComponent {

    /**
     * The key.
     */
    public final String key;

    /**
     * The single position list.
     */
    public final SortedSet<String> singlePositionList;

    /**
     * The multiple position list.
     */
    public final SortedSet<String> multiplePositionList;

    /**
     * The set position list.
     */
    public final SortedSet<String> setPositionList;

    /**
     * The intersecting list.
     */
    public final SortedSet<String> intersectingList;

    /**
     * Instantiates a new component prefix.
     *
     * @param key the key
     */
    public ComponentPrefix(String key) {
        this.key = key;
        singlePositionList = new ConcurrentSkipListSet<>();
        multiplePositionList = new ConcurrentSkipListSet<>();
        setPositionList = new ConcurrentSkipListSet<>();
        intersectingList = new ConcurrentSkipListSet<>();
    }

    /**
     * Adds the single position.
     *
     * @param prefix the prefix
     */
    public void addSinglePosition(String prefix) {
        if (!prefix.trim().isEmpty() && !singlePositionList.contains(prefix) && !multiplePositionList.contains(prefix)) {
            singlePositionList.add(prefix);
        }
    }

    /**
     * Adds the multiple position.
     *
     * @param prefix the prefix
     */
    public void addMultiplePosition(String prefix) {
        if (!prefix.trim().isEmpty()) {
            if (!singlePositionList.contains(prefix)) {
                multiplePositionList.add(prefix);
            } else {
                singlePositionList.remove(prefix);
                multiplePositionList.add(prefix);
            }
        }
    }

    /**
     * Adds the set position.
     *
     * @param prefix the prefix
     */
    public void addSetPosition(String prefix) {
        if (!prefix.trim().isEmpty()) {
            if (!singlePositionList.contains(prefix)) {
                setPositionList.add(prefix);
            } else {
                singlePositionList.remove(prefix);
                setPositionList.add(prefix);
            }
        }
    }

    /**
     * Adds the intersecting.
     *
     * @param prefix the prefix
     */
    public void addIntersecting(String prefix) {
        if (!prefix.trim().isEmpty()) {
            intersectingList.add(prefix);
        }
    }

}
