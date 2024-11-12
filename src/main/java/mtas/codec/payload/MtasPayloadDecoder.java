package mtas.codec.payload;

import java.io.IOException;
import java.nio.ByteBuffer;
import mtas.analysis.token.MtasOffset;
import mtas.analysis.token.MtasPosition;

/**
 * The Class MtasPayloadDecoder.
 */
public class MtasPayloadDecoder {

    /**
     * The mtas position.
     */
    private MtasPosition mtasPosition;

    /**
     * The mtas id.
     */
    private int mtasId = -1;

    /**
     * The mtas payload value.
     */
    private byte[] mtasPayloadValue;

    /**
     * The mtas parent id.
     */
    private int mtasParentId = -1;

    /**
     * The mtas payload.
     */
    private boolean mtasPayload;

    private boolean mtasParent;

    /**
     * The mtas offset.
     */
    private MtasOffset mtasOffset;

    /**
     * The mtas real offset.
     */
    private MtasOffset mtasRealOffset;

    /**
     * Inits the.
     *
     * @param startPosition the start position
     * @param payload       the payload
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void init(int startPosition, byte[] payload) throws IOException {
//        MtasBitInputStream byteStream = new MtasBitInputStream(payload);
        // analyse initial bits - position
//        boolean getOffset;
//        boolean getRealOffset;
//        String mtasPositionType;
//        if (byteStream.readBit() == 1) {
//            if (byteStream.readBit() == 1) {
//                mtasPositionType = null;
//            } else {
//                mtasPositionType = MtasPosition.POSITION_RANGE;
//            }
//        } else {
//            if (byteStream.readBit() == 1) {
//                mtasPositionType = MtasPosition.POSITION_SET;
//            } else {
//                mtasPositionType = MtasPosition.POSITION_SINGLE;
//            }
//        }
//        // analyze initial bits - offset
//        getOffset = byteStream.readBit() == 1;
//        // analyze initial bits - realOffset
//        getRealOffset = byteStream.readBit() == 1;
//        // analyze initial bits - parent
//
//        mtasParent = byteStream.readBit() == 1;
//        // analyse initial bits - payload
//        mtasPayload = byteStream.readBit() == 1;
//        if (byteStream.readBit() == 0) {
//            // string
//        } else {
//            // other
//        }
        var byteBuffer = ByteBuffer.wrap(payload);

        int initialBits = byteBuffer.get();
        // Analyze initial bits - position
        String mtasPositionType = getMtasPositionType(initialBits);
        // Analyze initial bits - offset (3rd bit)
        boolean getOffset = (initialBits & 0b00000100) != 0;
        // Analyze initial bits - realOffset (4th bit)
        boolean getRealOffset = (initialBits & 0b00001000) != 0;
        // Analyze initial bits - parent (5th bit)
        mtasParent = (initialBits & 0b00010000) != 0;
        // Analyze initial bits - payload (6th bit)
        mtasPayload = (initialBits & 0b00100000) != 0;
        // Analyze initial bits - string or other (7th bit)
        if ((initialBits & 0b01000000) == 0) {
            // string
        } else {
            // other
        }
//        byteStream.bitBuffer = initialBits;
//        byteStream.bitCount = 7;

        // get id
        mtasId = byteBuffer.getInt();
        // get position info
        if (mtasPositionType != null
                && mtasPositionType.equals(MtasPosition.POSITION_SINGLE)) {
            mtasPosition = new MtasPosition(startPosition);
        } else if (mtasPositionType != null
                && mtasPositionType.equals(MtasPosition.POSITION_RANGE)) {
            mtasPosition = new MtasPosition(startPosition, (startPosition + byteBuffer.getInt() - 1));
        } else if (mtasPositionType != null) {

            int numberOfPoints = byteBuffer.getInt();
            int[] positionList = new int[numberOfPoints];
            positionList[0] = startPosition;
            int previousPosition;
            int currentPosition = startPosition;
            for (int i = 1; i < numberOfPoints; i++) {
                previousPosition = currentPosition;
                currentPosition = previousPosition + byteBuffer.getInt();
                positionList[i] = currentPosition;
            }
            mtasPosition = new MtasPosition(positionList);
        } else {
            mtasPosition = null;
        }
        // get offset and realOffset info
        if (getOffset) {
            int offsetStart = byteBuffer.getInt();
            int offsetEnd = offsetStart + byteBuffer.getInt() - 1;
            mtasOffset = new MtasOffset(offsetStart, offsetEnd);
            if (getRealOffset) {
                int realOffsetStart = byteBuffer.getInt() + offsetStart;
                int realOffsetEnd = realOffsetStart + byteBuffer.getInt() - 1;
                mtasRealOffset = new MtasOffset(realOffsetStart, realOffsetEnd);
            }
        } else if (getRealOffset) {
            int realOffsetStart = byteBuffer.getInt();
            int realOffsetEnd = realOffsetStart + byteBuffer.getInt() - 1;
            mtasRealOffset = new MtasOffset(realOffsetStart, realOffsetEnd);
        }
        if (mtasParent) {
            mtasParentId = byteBuffer.getInt() + mtasId;
        }
        if (mtasPayload) {
            var mtasPayloadBytes = new byte[byteBuffer.remaining()];
            byteBuffer.slice().get(mtasPayloadBytes);
            mtasPayloadValue = mtasPayloadBytes;
        }
    }

    private static String getMtasPositionType(int initialBits) {
        String mtasPositionType;
        if ((initialBits & 0b00000001) != 0) {  // Check the 1st bit (least significant)
            if ((initialBits & 0b00000010) != 0) {  // Check the 2nd bit
                mtasPositionType = null;
            } else {
                mtasPositionType = MtasPosition.POSITION_RANGE;
            }
        } else {
            if ((initialBits & 0b00000010) != 0) {  // Check the 2nd bit
                mtasPositionType = MtasPosition.POSITION_SET;
            } else {
                mtasPositionType = MtasPosition.POSITION_SINGLE;
            }
        }
        return mtasPositionType;
    }

    /**
     * Gets the mtas id.
     *
     * @return the mtas id
     */
    public int getMtasId() {
        return mtasId;
    }

    /**
     * Gets the mtas parent id.
     *
     * @return the mtas parent id
     */
    public int getMtasParentId() {
        return mtasParentId;
    }

    /**
     * Gets the mtas payload.
     *
     * @return the mtas payload
     */
    public byte[] getMtasPayload() {
        return mtasPayload ? mtasPayloadValue : null;
    }

    /**
     * Gets the mtas position.
     *
     * @return the mtas position
     */
    public MtasPosition getMtasPosition() {
        return mtasPosition;
    }

    /**
     * Gets the mtas offset.
     *
     * @return the mtas offset
     */
    public MtasOffset getMtasOffset() {
        return mtasOffset;
    }

    /**
     * Gets the mtas real offset.
     *
     * @return the mtas real offset
     */
    public MtasOffset getMtasRealOffset() {
        return mtasRealOffset;
    }

    public boolean isMtasPayload() {
        return mtasPayload;
    }

    public boolean hasMtasParent() {
        return mtasParent;
    }
}
