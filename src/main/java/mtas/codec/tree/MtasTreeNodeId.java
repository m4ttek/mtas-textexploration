package mtas.codec.tree;

import java.util.Objects;

/**
 * The Class MtasTreeNodeId.
 *
 * @param ref           The ref.
 * @param additionalId  The additional id.
 * @param additionalRef The additional ref.
 */
public record MtasTreeNodeId(long ref, int additionalId, long additionalRef) implements Comparable<MtasTreeNodeId> {

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo(MtasTreeNodeId o) {
    return Long.compare(ref, o.ref);
  }

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
    final MtasTreeNodeId that = (MtasTreeNodeId) obj;
    return ref == that.ref && additionalId == that.additionalId
            && additionalRef == that.additionalRef;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return Objects.hash(this.getClass().getSimpleName(), ref, additionalId, additionalRef);
  }

}
