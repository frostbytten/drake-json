package org.agmip.data.json;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.agmip.data.json.JsonFSM.State;
import org.agmip.data.json.JsonFSM.Event;

//TODO: Documentation (Javadoc)

public class JsonParser {
  private ByteBuffer buffer;
  private int maxDepth = -1;
  private boolean debugMode = false;
  private boolean debugTheStack = false;
  private int currentDepth = 0;
  private JsonFSM fsm;
  private String current;

  private JsonParser(Builder builder) throws IOException {
    this.buffer = builder.json.store().duplicate();
    this.debugMode = builder.debug;
    //    this.debugTheStack = builder.debugStack;
    this.fsm = builder.sharedFSM.orElse(new JsonFSM());
  }

  private void setDebug(boolean debug) {
    this.debugMode = debug;
  }

  private void setDebugStack(boolean debug) {
    this.debugTheStack = debug;
  }

  public boolean isDebug() {
    return this.debugMode;
  }

  private int nextUnescapedDoubleQuotePosition() throws IOException {
    boolean inEscape = false;
    while (buffer.hasRemaining()) {
      char c = (char) JsonReader.read(buffer);
      if (inEscape) {
        switch (c) {
          case '"':
          case '\\':
          case '/':
          case 'b':
          case 'f':
          case 'n':
          case 'r':
          case 't':
            if (this.debugMode) {
              System.out.println("[DEBUG] Found escape sequence \\" + c);
            }
            inEscape = false;
            break;
          case 'u':
            char[] hex = new char[4];
            for(int i=0;i < 4; i++) {
              hex[i] = (char) JsonReader.read(buffer);
            }
            if (this.debugMode) {
              System.out.print("[DEBUG] Found escape sequence \\");
              for(int i=0;i < 4;i++) {
                System.out.print(hex[i]);
              }
              System.out.println();
            }
            inEscape = false;
            break;
          default:
            throw new ParseException("Invalid escape sequence: \\" + c);
        }
      } else {
        if ('\\' == c) {
          inEscape = true;
        } else if ('"' == c) {
          return buffer.position()-1;
        }
      }
    }
    return -1;
  }

  private int lastNumberPosition() throws IOException {
    boolean isDecimal = false;
    boolean isE = false;
 outer: while (buffer.hasRemaining()) {
      char c = (char) JsonReader.read(buffer);
      if (! Character.isDigit(c)) {
        switch (c) {
          case '.':
            if (isDecimal) {
              throw new ParseException("Invalid number: Too many decimal points");
            } else {
              isDecimal = true;
            }
            break;
          case 'e':
          case 'E':
            if (!isDecimal) {
              throw new ParseException("Invalid number: Improper E notation");
            } else {
              isE = true;
            }
            break;
          case '+':
          case '-':
            if (! isE) {
              throw new ParseException("Invalid symbol: " + c);
            } else {
              isE = false;
            }
            break;
          case ' ':
          case '\n':
          case '\r':
          case '\t':
          case '\f':
            return buffer.position()-1;
          case ',':
            return buffer.position()-1;
          default:
            throw new ParseException("Invalid number");
        }
      }
    }
    return -1;
  }

  private String extract(int start, int end) {
    byte[] ba = new byte[end-start];
    buffer.position(start);
    buffer.get(ba);
    buffer.position(end+1);
    return new String(ba, StandardCharsets.UTF_8);
  }

  public boolean hasNext() {
    return buffer.hasRemaining();
  }

  public JsonToken next() throws IOException {
    int start, end;
    JsonFSM.State s;
    while (buffer.hasRemaining()) {
      char c = (char) JsonReader.read(buffer);
      switch (c) {
        case '{':
          this.fsm.transition(Event.START_OBJECT);
          currentDepth++;
          return JsonToken.START_OBJECT;
        case '}':
          this.fsm.transition(Event.END_OBJECT);
          currentDepth--;
          return JsonToken.END_OBJECT;
        case '[':
          this.fsm.transition(Event.START_ARRAY);
          currentDepth++;
          return JsonToken.START_ARRAY;
        case ']':
          this.fsm.transition(Event.END_ARRAY);
          currentDepth--;
          return JsonToken.END_ARRAY;
        case '"':
          this.fsm.transition(Event.READ_STRING);
          start = buffer.position();
          end = nextUnescapedDoubleQuotePosition();
          if (end == -1) {
            throw new ParseException("Reached end of file before resolving");
          }
          current = extract(start, end);
          if (this.debugMode) {
            debugValue(current);
          }
          s = fsm.current();
          if (s == State.OBJECT_NAME_READ) {
            return JsonToken.OBJECT_NAME;
          } else if (s == State.OBJECT_VALUE_READ || s == State.ARRAY_ELEMENT_READ) {
            return JsonToken.VALUE_STRING;
          } else {
            return JsonToken.UNKNOWN;
          }
        case ':':
          this.fsm.transition(Event.READ_OBJECT_SEPARATOR);
          break;
        case ',':
          this.fsm.transition(Event.READ_VALUE_SEPARATOR);
          break;
        case '-':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          this.fsm.transition(Event.READ_VALUE);
          start = buffer.position()-1;
          end = lastNumberPosition();
          if (end == -1) {
            throw new ParseException("Reached end of file before resolving");
          }
          current = extract(start, end);
          buffer.position(buffer.position()-1);
          if (this.debugMode) {
            BigDecimal d = new BigDecimal(current);
            debugValue(current);
            System.out.println("[DEBUG] Number Conversion [int] " + d.intValue());
            System.out.println("[DEBUG] Number Conversion [double] " + d.doubleValue());
          }
          return JsonToken.VALUE_NUMBER;
        case 't':
          this.fsm.transition(Event.READ_VALUE);
          start = buffer.position()-1;
          end = start + 4;
          current = extract(start, end).toLowerCase();
          buffer.position(buffer.position()-1);
          if (! current.equals("true")) {
            throw new ParseException("Unquoted string found: Expected true, found " + current);
          }
          return JsonToken.VALUE_BOOLEAN;
        case 'f':
          this.fsm.transition(Event.READ_VALUE);
          start = buffer.position()-1;
          end = start + 5;
          current = extract(start, end).toLowerCase();
          buffer.position(buffer.position()-1);
          if (! current.equals("false")) {
            throw new ParseException("Unquoted string found: Expected false, found " + current);
          }
          return JsonToken.VALUE_BOOLEAN;
        case 'n':
          this.fsm.transition(Event.READ_VALUE);
          start = buffer.position()-1;
          end = start + 4;
          current = extract(start, end).toLowerCase();
          buffer.position(buffer.position()-1);
          if (! current.equals("null")) {
            throw new ParseException("Unquoted string found: Expected null, found " + current);
          }
          current = null;
          return JsonToken.VALUE_NULL;
      }
    }
    return JsonToken.UNKNOWN;
  }

  public JsonToken skip() throws IOException {
    return skip(1);
  }

  public JsonToken skip(int depth) throws IOException {
    int targetDepth = currentDepth - depth;
    JsonToken t = JsonToken.UNKNOWN;
    if (targetDepth < 1) {
      buffer.position(buffer.limit());
      return t;
    }
    while(this.hasNext() && currentDepth != targetDepth) {
      t = this.next();
    }
    return t;
  }

  public int maxDepth() throws IOException {
    if (maxDepth != -1) {
      return maxDepth;
    }
    int currentPos = buffer.position();
    int max = 0;
    buffer.rewind();
    JsonToken t = JsonToken.UNKNOWN;
    while(this.hasNext()) {
      this.next();
      if (currentDepth > max) {
        max = currentDepth;
      }
    }
    buffer.position(currentPos);
    maxDepth = max;
    return max;
  }

  public void rewind() {
    buffer.rewind();
  }

  public int depth() {
    return this.currentDepth;
  }

  public String get() {
    return current;
  }

  public BigDecimal getAsBigDecimal() {
    return new BigDecimal(current);
  }

  public double getAsDouble() {
    return getAsBigDecimal().doubleValue();
  }

  public int getAsInt() {
    return getAsBigDecimal().intValue();
  }

  public boolean getAsBoolean() {
    return Boolean.parseBoolean(current);
  }

  protected JsonFSM fsm() {
    return this.fsm;
  }

  protected ByteBuffer buffer() {
    return this.buffer;
  }

  public void close() {
    this.buffer = null;
    this.fsm = null;
  }

  private void debugValue(String value) {
    StringBuilder sb = new StringBuilder();
    sb.append("[DEBUG] ");
    for (int i=1; i < currentDepth; i++) {
      sb.append("\t");
    }
    sb.append(value);
    System.out.println(sb.toString());
  }

  public static class Builder {
    private final Json json;
    private boolean debug = false;
    private Optional<JsonFSM> sharedFSM = Optional.empty();

    public Builder(Json json) {
      this.json = json;
    }

    public Builder setDebug(boolean debug) {
      this.debug = debug;
      return this;
    }

    public Builder setSharedFSM(JsonFSM fsm) {
      sharedFSM = Optional.ofNullable(fsm);
      return this;
    }

    public JsonParser build() throws IOException {
      return new JsonParser(this);
    }
  }

  public static class ParseException extends RuntimeException {
    public ParseException(String msg) {
      super(msg);
    }
  }
}
