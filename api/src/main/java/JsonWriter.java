package org.agmip.data.json;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class JsonWriter {
  private JsonWriter() {}

  public static char write(ByteBuffer buffer, char c) throws IOException {
    return write(buffer, c, (char) 0);
  }

  public static char write(ByteBuffer buffer, char c, char high) throws IOException {
    if ((c < 0xd800) || (c > 0xdfff)) {
      write(buffer, (int) c);
      return 0;
    } else if (c < 0xdc00) {
      return c;
    } else {
      int cp = ((high - 0xd800) << 10) + (c - 0xdc00) + 0x10000;
      write(buffer, cp);
      return 0;
    }
  }

  public static void write(ByteBuffer buffer, int cp) throws IOException {
    if ((cp & 0xffffff80) == 0) {
      buffer.put((byte) cp);
    } else {
      multiWrite(buffer, cp);
    }
  }

  public static void write(ByteBuffer buffer, String s) throws IOException {
    int l = s.length();
    for(int i=0; i < l; i++) {
      char c = s.charAt(i);
      if (c < 0x80) {
        buffer.put((byte) c);
      } else {
        char more = write(buffer, c);
        if (more != 0) {
          c = s.charAt(i++);
          write(buffer, c, more);
        }
      }
    }
  }
    
  public static void multiWrite(ByteBuffer buffer, int cp) throws IOException {
    if ((cp & 0xfffff800) == 0) {
      buffer.put((byte) (0xc0 | (cp >> 6)));
      buffer.put((byte) (0x80 | (cp & 0x3f)));
    } else if ((cp & 0xffff0000) == 0) {
      buffer.put((byte) (0xe0 | (cp >> 12)));
      buffer.put((byte) (0x80 | ((cp >> 6) & 0x3f)));
      buffer.put((byte) (0x80 | (cp & 0x3f)));
    } else if ((cp & 0xff200000) == 0) {
      buffer.put((byte) (0xf0 | (cp >> 18)));
      buffer.put((byte) (0x80 | ((cp >> 12) & 0x3f)));
      buffer.put((byte) (0x80 | ((cp >> 6) & 0x3f)));
      buffer.put((byte) (0x80 | (cp & 0x3f)));
    } else if ((cp & 0xf4000000) == 0) {
      buffer.put((byte) (0xf8 | (cp >> 24)));
      buffer.put((byte) (0x80 | ((cp >> 18) & 0x3f)));
      buffer.put((byte) (0x80 | ((cp >> 12) & 0x3f)));
      buffer.put((byte) (0x80 | ((cp >> 6) & 0x3f)));
      buffer.put((byte) (0x80 | (cp & 0x3f)));
    } else if ((cp & 0x80000000) == 0) {
      buffer.put((byte) (0xfc | (cp >> 30)));
      buffer.put((byte) (0x80 | ((cp >> 24) & 0x3f)));
      buffer.put((byte) (0x80 | ((cp >> 18) & 0x3f)));
      buffer.put((byte) (0x80 | ((cp >> 12) & 0x3f)));
      buffer.put((byte) (0x80 | ((cp >> 6) & 0x3f)));
      buffer.put((byte) (0x80 | (cp & 0x3f)));
    } else {
      throw new CharConversionException("Invalid UTF-8 character (U+" + Integer.toHexString(cp));
    }
  }
}
