package mtas.analysis.token;

/**
 * A factory for creating MtasTokenId objects.
 */
public final class MtasTokenIdFactory {

  /** The token id. */
  private int tokenId;

  /**
   * Creates a new MtasTokenId object.
   *
   * @return the integer
   */
  public int createTokenId() {
    return tokenId++;
  }

}
