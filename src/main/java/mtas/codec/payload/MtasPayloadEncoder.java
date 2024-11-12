package mtas.codec.payload;

import java.io.IOException;
import java.nio.ByteBuffer;
import mtas.analysis.token.MtasPosition;
import mtas.analysis.token.MtasToken;
import mtas.analysis.token.MtasTokenString;
import org.apache.lucene.util.BytesRef;

/**
 * The Class MtasPayloadEncoder.
 */
public class MtasPayloadEncoder {

  /** The mtas token. */
  private final MtasToken mtasToken;

  /** The encoding flags. */
  private final int encodingFlags;

  /** The Constant ENCODE_PAYLOAD. */
  public static final int ENCODE_PAYLOAD = 1;

  /** The Constant ENCODE_OFFSET. */
  public static final int ENCODE_OFFSET = 2;

  /** The Constant ENCODE_REALOFFSET. */
  public static final int ENCODE_REALOFFSET = 4;

  /** The Constant ENCODE_PARENT. */
  public static final int ENCODE_PARENT = 8;

  /** The Constant ENCODE_DEFAULT. */
  public static final int ENCODE_DEFAULT = ENCODE_PAYLOAD | ENCODE_OFFSET
      | ENCODE_PARENT;

  /** The Constant ENCODE_ALL. */
  public static final int ENCODE_ALL = ENCODE_PAYLOAD | ENCODE_OFFSET
      | ENCODE_REALOFFSET | ENCODE_PARENT;

  /**
   * Instantiates a new mtas payload encoder.
   *
   * @param token the token
   * @param flags the flags
   */
  public MtasPayloadEncoder(MtasToken token, int flags) {
    mtasToken = token;
    encodingFlags = flags;
  }

  /**
   * Instantiates a new mtas payload encoder.
   *
   * @param token the token
   */
  public MtasPayloadEncoder(MtasToken token) {
    this(token, ENCODE_DEFAULT);
  }

  /**
   * Gets the payload.
   *
   * @return the payload
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public BytesRef getPayload() throws IOException {
    var byteBuffer = ByteBuffer.allocate(calculateRequiredSpaceForByteBuffer());

    byte initialByte = 0;

    // Set position type bits (bits 0 and 1)
    if (mtasToken.checkPositionType(MtasPosition.POSITION_SINGLE)) {
        initialByte |= 0b00000000; // 00 in the first two bits
    } else if (mtasToken.checkPositionType(MtasPosition.POSITION_RANGE)) {
        initialByte |= 0b00000001; // 10 in the first two bits
    } else if (mtasToken.checkPositionType(MtasPosition.POSITION_SET)) {
        initialByte |= 0b00000010; // 01 in the first two bits
    } else {
        initialByte |= 0b00000011; // 11 in the first two bits
    }

    // Set offset bit (bit 2)
    if ((encodingFlags & ENCODE_OFFSET) == ENCODE_OFFSET && mtasToken.checkOffset()) {
        initialByte |= 0b00000100;
    }

    // Set realOffset bit (bit 3)
    if ((encodingFlags & ENCODE_REALOFFSET) == ENCODE_REALOFFSET && mtasToken.checkRealOffset()) {
        initialByte |= 0b00001000;
    }

    // Set parentId bit (bit 4)
    if ((encodingFlags & ENCODE_PARENT) == ENCODE_PARENT && mtasToken.checkParentId()) {
        initialByte |= 0b00010000;
    }

    // Set original payload bit (bit 5)
    if ((encodingFlags & ENCODE_PAYLOAD) == ENCODE_PAYLOAD && mtasToken.getPayload() != null) {
        initialByte |= 0b00100000;
    }

    // Set token type bit (bit 6)
    if (!mtasToken.getType().equals(MtasTokenString.TOKEN_TYPE)) {
        initialByte |= 0b01000000; // Set to 1 for non-default token type
    }
    byteBuffer.put(initialByte);

//    // initial bits - position
//    if (mtasToken.checkPositionType(MtasPosition.POSITION_SINGLE)) {
//      byteStream.writeBit(0);
//      byteStream.writeBit(0);
//    } else if (mtasToken.checkPositionType(MtasPosition.POSITION_RANGE)) {
//      byteStream.writeBit(1);
//      byteStream.writeBit(0);
//    } else if (mtasToken.checkPositionType(MtasPosition.POSITION_SET)) {
//      byteStream.writeBit(0);
//      byteStream.writeBit(1);
//    } else {
//      byteStream.writeBit(1);
//      byteStream.writeBit(1);
//    }
//    // initial bits - offset
//    if ((encodingFlags & ENCODE_OFFSET) == ENCODE_OFFSET
//        && mtasToken.checkOffset()) {
//      byteStream.writeBit(1);
//    } else {
//      byteStream.writeBit(0);
//    }
//    // initial bits - realOffset
//    if ((encodingFlags & ENCODE_REALOFFSET) == ENCODE_REALOFFSET
//        && mtasToken.checkRealOffset()) {
//      byteStream.writeBit(1);
//    } else {
//      byteStream.writeBit(0);
//    }
//    // initial bits - parentId
//    if ((encodingFlags & ENCODE_PARENT) == ENCODE_PARENT
//        && mtasToken.checkParentId()) {
//      byteStream.writeBit(1);
//    } else {
//      byteStream.writeBit(0);
//    }
//    // initial bits - original payload
//    if ((encodingFlags & ENCODE_PAYLOAD) == ENCODE_PAYLOAD
//        && mtasToken.getPayload() != null) {
//      byteStream.writeBit(1);
//    } else {
//      byteStream.writeBit(0);
//    }
//    if (mtasToken.getType().equals(MtasTokenString.TOKEN_TYPE)) {
//      byteStream.writeBit(0);
//    } else {
//      // to add other token types later on
//      byteStream.writeBit(1);
//    }
    // add id (EliasGammaCoding)

    // TODO jeden integer
    byteBuffer.putInt(mtasToken.getId());
//    byteStream.writeEliasGammaCodingNonNegativeInteger(mtasToken.getId());
    // add position info (EliasGammaCoding)
    if (mtasToken.checkPositionType(MtasPosition.POSITION_SINGLE)) {
      // do nothing
    } else if (mtasToken.checkPositionType(MtasPosition.POSITION_RANGE)) {
      // TODO jeden integer
      // write length
      byteBuffer.putInt(1 + mtasToken.getPositionEnd() - mtasToken.getPositionStart());
//      byteStream.writeEliasGammaCodingPositiveInteger(
//          1 + mtasToken.getPositionEnd() - mtasToken.getPositionStart());
    } else if (mtasToken.checkPositionType(MtasPosition.POSITION_SET)) {
      // write number of positions

      int[] positionList = mtasToken.getPositions();

      // TODO jeden integer
      byteBuffer.putInt(positionList.length);
//      byteStream.writeEliasGammaCodingPositiveInteger(positionList.length);
      int previousPosition = positionList[0];

      // TODO poistionList.length - 1 integerów
      for (int i = 1; i < positionList.length; i++) {
        byteBuffer.putInt(positionList[i] - previousPosition);
//        byteStream.writeEliasGammaCodingPositiveInteger(
//            positionList[i] - previousPosition);
        previousPosition = positionList[i];
      }
    } else {
      // do nothing
    }
    // add offset info (EliasGammaCoding)
    if ((encodingFlags & ENCODE_OFFSET) == ENCODE_OFFSET
        && mtasToken.checkOffset()) {

      /// TODO dwa integery
      byteBuffer.putInt(mtasToken.getOffsetStart());
      byteBuffer.putInt(1 + mtasToken.getOffsetEnd() - mtasToken.getOffsetStart());
//      byteStream
//          .writeEliasGammaCodingNonNegativeInteger(mtasToken.getOffsetStart());
//      byteStream.writeEliasGammaCodingPositiveInteger(
//          1 + mtasToken.getOffsetEnd() - mtasToken.getOffsetStart());
    }
    // add realOffset info (EliasGammaCoding)
    if ((encodingFlags & ENCODE_REALOFFSET) == ENCODE_REALOFFSET
        && mtasToken.checkRealOffset()) {
      // TODO dwa integery
      if ((encodingFlags & ENCODE_OFFSET) == ENCODE_OFFSET
          && mtasToken.checkOffset()) {
        byteBuffer.putInt(mtasToken.getRealOffsetStart() - mtasToken.getOffsetStart());
        byteBuffer.putInt(1 + mtasToken.getRealOffsetEnd() - mtasToken.getRealOffsetStart());
//        byteStream.writeEliasGammaCodingInteger(
//            mtasToken.getRealOffsetStart() - mtasToken.getOffsetStart());
//        byteStream.writeEliasGammaCodingPositiveInteger(
//            1 + mtasToken.getRealOffsetEnd() - mtasToken.getRealOffsetStart());
      } else {
        byteBuffer.putInt(mtasToken.getRealOffsetStart());
        byteBuffer.putInt(1 + mtasToken.getRealOffsetEnd() - mtasToken.getRealOffsetStart());
//        byteStream.writeEliasGammaCodingNonNegativeInteger(
//            mtasToken.getRealOffsetStart());
//        byteStream.writeEliasGammaCodingPositiveInteger(
//            1 + mtasToken.getRealOffsetEnd() - mtasToken.getRealOffsetStart());
      }
    }
    // add parent info (EliasGammaCoding)
    if ((encodingFlags & ENCODE_PARENT) == ENCODE_PARENT
        && mtasToken.checkParentId()) {

      // TODO jeden integer
      byteBuffer.putInt(mtasToken.getParentId() - mtasToken.getId());
//      byteStream.writeEliasGammaCodingInteger(
//          mtasToken.getParentId() - mtasToken.getId());
    }
    // add minimal number of zero-bits to get round number of bytes
//    byteStream.createByte();
    // finally add original payload bytes
    if ((encodingFlags & ENCODE_PAYLOAD) == ENCODE_PAYLOAD
        && mtasToken.getPayload() != null) {

      // TODO (payload.offset + payload.length) - payload.offset bajtów
      BytesRef payload = mtasToken.getPayload();
      byteBuffer.put(payload.bytes, payload.offset, payload.length);
//      byteStream.write(Arrays.copyOfRange(payload.bytes, payload.offset,
//          (payload.offset + payload.length)));
    }
    // construct new payload
    return new BytesRef(byteBuffer.array());
  }

  private int calculateRequiredSpaceForByteBuffer() {
    BytesRef payload = mtasToken.getPayload();
    return 1 + 4 * ( // int == 4 bytes
            1 // MTAS ID
            + (mtasToken.checkPositionType(MtasPosition.POSITION_RANGE) ? 1 : 0)
            + (mtasToken.checkPositionType(MtasPosition.POSITION_SET) ? mtasToken.getPositions().length : 0)
            + ((encodingFlags & ENCODE_OFFSET) == ENCODE_OFFSET && mtasToken.checkOffset() ? 2 : 0)
            + ((encodingFlags & ENCODE_REALOFFSET) == ENCODE_REALOFFSET && mtasToken.checkRealOffset() ? 2 : 0)
            + ((encodingFlags & ENCODE_PARENT) == ENCODE_PARENT && mtasToken.checkParentId() ? 1 : 0)
    ) + ((encodingFlags & ENCODE_PAYLOAD) == ENCODE_PAYLOAD && mtasToken.getPayload() != null ? payload.length : 0);
  }

}
