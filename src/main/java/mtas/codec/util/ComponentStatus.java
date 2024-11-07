package mtas.codec.util;

import java.util.Objects;

/**
 * The Class ComponentStatus.
 */
public final class ComponentStatus implements BasicComponent {

    /**
     * The handler.
     */
    public String handler;

    /**
     * The name.
     */
    public String name;

    /**
     * The key.
     */
    public String key;

    /**
     * The number of documents.
     */
    public Integer numberOfDocuments;

    /**
     * The number of segments.
     */
    public Integer numberOfSegments;

    /**
     * The get mtas handler.
     */
    public boolean getMtasHandler;

    /**
     * The get number of documents.
     */
    public boolean getNumberOfDocuments;

    /**
     * The get number of segments.
     */
    public boolean getNumberOfSegments;

    /**
     * Instantiates a new component status.
     *
     * @param name                 the name
     * @param key                  the key
     * @param getMtasHandler       the get mtas handler
     * @param getNumberOfDocuments the get number of documents
     * @param getNumberOfSegments  the get number of segments
     */
    public ComponentStatus(String name, String key, boolean getMtasHandler, boolean getNumberOfDocuments,
                           boolean getNumberOfSegments) {
        this.name = Objects.requireNonNull(name, "no name");
        this.key = key;
        this.getMtasHandler = getMtasHandler;
        this.getNumberOfDocuments = getNumberOfDocuments;
        this.getNumberOfSegments = getNumberOfSegments;
        handler = null;
        numberOfDocuments = null;
        numberOfSegments = null;
    }

}
