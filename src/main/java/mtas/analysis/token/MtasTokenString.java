package mtas.analysis.token;

/**
 * The Class MtasTokenString.
 */
public class MtasTokenString extends MtasToken {

  /** The Constant TOKEN_TYPE. */
  public static final String TOKEN_TYPE = "string";

  /**
   * Instantiates a new mtas token string.
   *
   * @param tokenId the token id
   * @param value the value
   */
  public MtasTokenString(int tokenId, String value) {
    super(tokenId, value);
  }

  /**
   * Instantiates a new mtas token string.
   *
   * @param tokenId the token id
   * @param prefix the prefix
   * @param postfix the postfix
   */
  public MtasTokenString(int tokenId, String prefix, String postfix) {
    super(tokenId, prefix, postfix);
  }

  /**
   * Instantiates a new mtas token string.
   *
   * @param tokenId the token id
   * @param value the value
   * @param position the position
   */
  public MtasTokenString(int tokenId, String value, Integer position) {
    super(tokenId, value, position);
  }

  /**
   * Instantiates a new mtas token string.
   *
   * @param tokenId the token id
   * @param prefix the prefix
   * @param postfix the postfix
   * @param position the position
   */
  public MtasTokenString(int tokenId, String prefix, String postfix,
      Integer position) {
    super(tokenId, prefix, postfix, position);
  }

  /*
   * (non-Javadoc)
   * 
   * @see mtas.analysis.token.MtasToken#setType()
   */
  @Override
  public void setType() {
    tokenType = TOKEN_TYPE;
  }

}
