package mtas.codec.payload;

import java.io.IOException;
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
        MtasBitInputStream byteStream = new MtasBitInputStream(payload);
        // analyse initial bits - position
        boolean getOffset;
        boolean getRealOffset;
        String mtasPositionType;
        if (byteStream.readBit() == 1) {
            if (byteStream.readBit() == 1) {
                mtasPositionType = null;
            } else {
                mtasPositionType = MtasPosition.POSITION_RANGE;
            }
        } else {
            if (byteStream.readBit() == 1) {
                mtasPositionType = MtasPosition.POSITION_SET;
            } else {
                mtasPositionType = MtasPosition.POSITION_SINGLE;
            }
        }
        // analyze initial bits - offset
        getOffset = byteStream.readBit() == 1;
        // analyze initial bits - realOffset
        getRealOffset = byteStream.readBit() == 1;
        // analyze initial bits - parent

        mtasParent = byteStream.readBit() == 1;
        // analyse initial bits - payload
        mtasPayload = byteStream.readBit() == 1;
        if (byteStream.readBit() == 0) {
            // string
        } else {
            // other
        }
        // get id
        mtasId = byteStream.readEliasGammaCodingNonNegativeInteger();
        // get position info
        if (mtasPositionType != null
                && mtasPositionType.equals(MtasPosition.POSITION_SINGLE)) {
            mtasPosition = new MtasPosition(startPosition);
        } else if (mtasPositionType != null
                && mtasPositionType.equals(MtasPosition.POSITION_RANGE)) {
            mtasPosition = new MtasPosition(startPosition, (startPosition
                    + byteStream.readEliasGammaCodingPositiveInteger() - 1));
        } else if (mtasPositionType != null) {

            int numberOfPoints = byteStream.readEliasGammaCodingPositiveInteger();
            int[] positionList = new int[numberOfPoints];
            positionList[0] = startPosition;
            int previousPosition;
            int currentPosition = startPosition;
            for (int i = 1; i < numberOfPoints; i++) {
                previousPosition = currentPosition;
                currentPosition = previousPosition
                        + byteStream.readEliasGammaCodingPositiveInteger();
                positionList[i] = currentPosition;
            }
            mtasPosition = new MtasPosition(positionList);
        } else {
            mtasPosition = null;
        }
        // get offset and realOffset info
        if (getOffset) {
            int offsetStart = byteStream.readEliasGammaCodingNonNegativeInteger();
            int offsetEnd = offsetStart
                    + byteStream.readEliasGammaCodingPositiveInteger() - 1;
            mtasOffset = new MtasOffset(offsetStart, offsetEnd);
            if (getRealOffset) {
                int realOffsetStart = byteStream.readEliasGammaCodingInteger()
                        + offsetStart;
                int realOffsetEnd = realOffsetStart
                        + byteStream.readEliasGammaCodingPositiveInteger() - 1;
                mtasRealOffset = new MtasOffset(realOffsetStart, realOffsetEnd);
            }
        } else if (getRealOffset) {
            int realOffsetStart = byteStream.readEliasGammaCodingNonNegativeInteger();
            int realOffsetEnd = realOffsetStart
                    + byteStream.readEliasGammaCodingPositiveInteger() - 1;
            mtasRealOffset = new MtasOffset(realOffsetStart, realOffsetEnd);
        }
        if (mtasParent) {
            mtasParentId = byteStream.readEliasGammaCodingInteger() + mtasId;
        }
        if (mtasPayload) {
            mtasPayloadValue = byteStream.readRemainingBytes();
        }
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
