package mtas.codec.util;

import java.util.Objects;

/**
 * The Class Match.
 *
 * @param startPosition The start position.
 * @param endPosition   The end position.
 */
public record Match(int startPosition, int endPosition) {

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Match that = (Match) obj;
        return startPosition == that.startPosition && endPosition == that.endPosition;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.getClass().getSimpleName(), startPosition, endPosition);
    }

}
