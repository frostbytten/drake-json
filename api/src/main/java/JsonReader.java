package org.agmip.data.json;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public final class JsonReader {
  private JsonReader() {}

  public static int read(ByteBuffer buffer) throws IOException {
    if (buffer != null) {
      if (buffer.hasRemaining()) {
        byte b = buffer.get();
        return (b >= 0) ? b : multiRead(buffer, b, 0, 0);
      } else {
        return -1;
      }
    } else {
      throw new IOException("Invalid buffer");
    }
  }

  private static int multiRead(ByteBuffer buffer, byte b, int more, int cp) throws IOException {
    try {
      if ((b >= 0) && (more == 0)) {
        return b;
      } else if (((b & 0xc0) == 0x80) && (more != 0)) {
        cp = (cp << 6) | (b & 0x3f);
        if (--more == 0) {
          return cp;
        } else
          return multiRead(buffer, buffer.get(), more, cp);
      } else if (((b & 0xe0) == 0xc0) && (more == 0)) {
        cp = b & 0x1f;
        more = 1;
        return multiRead(buffer, buffer.get(), more, cp);
      } else if (((b & 0xf0) == 0xe0) && (more == 0)) {
        cp = b & 0x0f;
        more = 2;
        return multiRead(buffer, buffer.get(), more, cp);
      } else if (((b & 0xf8) == 0xf0) && (more == 0)) {
        cp = b & 0x07;
        more = 3;
        return multiRead(buffer, buffer.get(), more, cp);
      } else if (((b & 0xfc) == 0xf8) && (more == 0)) {
        cp = b & 0x03;
        more = 4;
        return multiRead(buffer, buffer.get(), more, cp);
      } else if (((b & 0xfe) == 0xfc) && (more == 0)) {
        cp = b & 0x01;
        more = 5;
        return multiRead(buffer, buffer.get(), more, cp);
      } else {
        throw new CharConversionException("Invalid UTF-8 encoding");
      }
    } catch (BufferUnderflowException ex)  {
      throw new CharConversionException("Incomplete UTF-8 sequence");
    }
  }
}
