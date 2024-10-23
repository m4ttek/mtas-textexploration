package mtas.codec.payload;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The Class MtasBitInputStream.
 */
public class MtasBitInputStream extends ByteArrayInputStream {

  /** The bit buffer. */
  private int bitBuffer = 0;

  /** The bit count. */
  private int bitCount = 0;

//  private int bitPosition = 0;

  /**
   * Instantiates a new mtas bit input stream.
   *
   * @param buf the byte array buffer
   */
  public MtasBitInputStream(byte[] buf) {
    super(buf);
  }

  /**
   * Read a single bit.
   *
   * @return the bit value (0 or 1)
   * @throws IOException if there are no more bits to read
   */
  public int readBit() throws IOException {
    if (bitCount == 0) {
      bitBuffer = read();
      if (bitBuffer == -1) {
        throw new IOException("No more bits available");
      }
    }
    int value = (bitBuffer >> bitCount) & 1;
    bitCount++;
    if (bitCount == 8) {
      bitCount = 0; // Reset after reading 8 bits
    }
    return value;
  }

  /**
   * Read the remaining bytes in the stream.
   *
   * @return the remaining bytes
   * @throws IOException if no bytes are available
   */
  public byte[] readRemainingBytes() throws IOException {
    int availableBytes = this.available();
    if (availableBytes > 0) {
      byte[] b = new byte[availableBytes];
      int bytesRead = read(b);
      if (bytesRead >= 0) {
        return b;
      } else {
        throw new IOException("Error reading remaining bytes");
      }
    } else {
      throw new IOException("No more bytes available");
    }
  }

  /**
   * Read an Elias Gamma coded integer.
   *
   * @return the decoded integer
   * @throws IOException if there's an issue reading from the stream
   */
  public int readEliasGammaCodingInteger() throws IOException {
    int value = readEliasGammaCodingPositiveInteger();
    return (value % 2 == 0) ? (-value) / 2 : (value - 1) / 2;
  }

  /**
   * Read an Elias Gamma coded non-negative integer.
   *
   * @return the decoded non-negative integer
   * @throws IOException if there's an issue reading from the stream
   */
  public int readEliasGammaCodingNonNegativeInteger() throws IOException {
    return readEliasGammaCodingPositiveInteger() - 1;
  }

  /**
   * Read an Elias Gamma coded positive integer.
   *
   * @return the decoded positive integer
   * @throws IOException if there's an issue reading from the stream
   */
  public int readEliasGammaCodingPositiveInteger() throws IOException {
    int value = 1;
    int counter = 0;

    // Count leading zeroes
    while (readBit() == 0) {
      counter++;
    }

    // Build the value based on the number of zeroes
    for (int i = 0; i < counter; i++) {
      value = (value << 1) | readBit();
    }

    return value;
  }
}
