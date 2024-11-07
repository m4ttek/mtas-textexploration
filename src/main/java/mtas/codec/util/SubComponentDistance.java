package mtas.codec.util;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import mtas.codec.util.distance.Distance;

/**
 * The Class SubComponentDistance.
 */
public class SubComponentDistance implements Serializable {

    /**
     * The Constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The key.
     */
    public String key;

    /**
     * The type.
     */
    public String type;

    /**
     * The base.
     */
    public String base;

    /**
     * The prefix.
     */
    public String prefix;

    /**
     * The minimum.
     */
    public Double minimum;

    /**
     * The maximum.
     */
    public Double maximum;

    /**
     * The parameters.
     */
    public Map<String, String> parameters;

    /**
     * The distance.
     */
    public transient Distance distance = null;

    /**
     * Instantiates a new sub component distance.
     *
     * @param key        the key
     * @param type       the type
     * @param prefix     the prefix
     * @param base       the base
     * @param parameters the parameters
     * @param minimum    the minimum
     * @param maximum    the maximum
     */
    public SubComponentDistance(String key, String type, String prefix, String base, Map<String, String> parameters,
                                String minimum, String maximum) {
        this.key = key;
        this.prefix = prefix;
        this.type = type;
        this.base = base;
        this.parameters = parameters;
        this.minimum = minimum != null ? Double.parseDouble(minimum) : null;
        this.maximum = maximum != null ? Double.parseDouble(maximum) : null;
    }

    /**
     * Gets the distance.
     *
     * @return the distance
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Distance getDistance() throws IOException {
        if (distance == null) {
            if (type != null) {
                try {
                    Constructor<Distance> constructor = (Constructor<Distance>) Class
                            .forName("mtas.codec.util.distance." + createClassName(type) + "Distance")
                            .getConstructor(String.class, String.class, Double.class, Double.class, Map.class);
                    distance = constructor.newInstance(prefix, base, minimum, maximum, parameters);
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
                         | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
                // distance = new MorseDistance(prefix, base, maximum, parameters);
            } else {
                throw new IOException("unrecognized distance " + type);
            }
        }
        return distance;
    }

    /**
     * Creates the class name.
     *
     * @param type the type
     * @return the string
     */
    private String createClassName(String type) {
        final char DASH = '-';
        Objects.requireNonNull(type, "Type is obligatory");
        final StringBuilder output = new StringBuilder(type.length());
        boolean lastCharacterWasDash = true;
        boolean thisCharacterWasDash;
        for (final char currentCharacter : type.toCharArray()) {
            thisCharacterWasDash = (currentCharacter == DASH);
            if (!thisCharacterWasDash) {
                if (lastCharacterWasDash) {
                    output.append(Character.toTitleCase(currentCharacter));
                } else {
                    output.append(currentCharacter);
                }
            }
            lastCharacterWasDash = thisCharacterWasDash;
        }
        return output.toString();
    }

}
