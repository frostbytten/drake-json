package org.agmip.data.json;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

//TODO: Documentation (Javadoc)

public class JsonParser {
  public enum State {
    UNKNOWN,
    OBJECT_STARTED,
    AWAIT_OBJECT_NAME,
    READ_OBJECT_NAME,
    AWAIT_OBJECT_VALUE,
    READ_OBJECT_VALUE,
    ARRAY_STARTED,
    AWAIT_ARRAY_ELEMENT,
    READ_ARRAY_ELEMENT,
  }

  private ByteBuffer buffer;
  private int maxDepth = -1;
  private boolean debugMode = false;
  private boolean debugTheStack = false;
  private int currentDepth = 0;
  private Deque<State> stack = new ArrayDeque<>();
  private Deque<Integer> arrayTrace = new ArrayDeque<>();
  private String current;

  private JsonParser(Builder builder) throws IOException {
    this.buffer = builder.json.getStore().duplicate();
    this.debugMode = builder.debug;
    this.debugTheStack = builder.debugStack;
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

  private void startNestedEvent() {
    if (stack.peekLast() == State.AWAIT_OBJECT_VALUE) {
      stack.removeLast();
      stack.add(State.READ_OBJECT_VALUE);
    } else if (stack.peekLast() == State.AWAIT_ARRAY_ELEMENT) {
      stack.removeLast();
      stack.add(State.READ_ARRAY_ELEMENT);
    } else if (stack.peekLast() == State.AWAIT_OBJECT_NAME) {
      throw new ParseException("Cannot start new nested event when awaiting an object name");
    }
  }

  public JsonToken next() throws IOException {
    int start, end;
    State s;
    while (buffer.hasRemaining()) {
      if (this.debugTheStack) {
        debugStack();
      }
      char c = (char) JsonReader.read(buffer);
      switch (c) {
      case '{':
        startNestedEvent();
        stack.add(State.OBJECT_STARTED);
        stack.add(State.AWAIT_OBJECT_NAME);
        currentDepth++;
        return JsonToken.START_OBJECT;
      case '}':
        s = stack.removeLast();
        if (s != State.OBJECT_STARTED && s != State.READ_OBJECT_VALUE && s != State.AWAIT_OBJECT_NAME) {
          throw new ParseException("Unbalanced JSON structure: Found " + s + " instead of START_OBJECT");
        }
        if (s == State.READ_OBJECT_VALUE || s == State.AWAIT_OBJECT_NAME) {
          s = stack.removeLast();
        }
        if (s != State.OBJECT_STARTED) {
          throw new ParseException("Unbalanced JSON structure: Found " + s + " instead of START_OBJECT");
        }
        currentDepth--;
        return JsonToken.END_OBJECT;
      case '[':
        startNestedEvent();
        stack.add(State.ARRAY_STARTED);
        stack.add(State.AWAIT_ARRAY_ELEMENT);
        arrayTrace.add(0);
        currentDepth++;
        return JsonToken.START_ARRAY;
      case ']':
        s = stack.removeLast();
        currentDepth--;
        if (s != State.ARRAY_STARTED && s != State.READ_ARRAY_ELEMENT && s != State.AWAIT_ARRAY_ELEMENT) {
          throw new ParseException("Unbalanced JSON structure: Found " + s + " instead of START_ARRAY");
        }
        if (s == State.AWAIT_ARRAY_ELEMENT) {
          Integer length = arrayTrace.removeLast();
          if (length > 0) {
            throw new ParseException("Hanging comma in array structure");
          }
          s = stack.removeLast();
        }
        if (s == State.READ_ARRAY_ELEMENT ) {
          s = stack.removeLast();
        }
        if (s != State.ARRAY_STARTED) {
          throw new ParseException("Unbalanced JSON structure: Found " + s + " instead of START_ARRAY");
        }
        return JsonToken.END_ARRAY;
      case '"':
        s = stack.removeLast();
        switch (s) {
        case AWAIT_OBJECT_NAME:
          stack.add(State.READ_OBJECT_NAME);
          break;
        case AWAIT_OBJECT_VALUE:
          stack.add(State.READ_OBJECT_VALUE);
          break;
        case AWAIT_ARRAY_ELEMENT:
          stack.add(State.READ_ARRAY_ELEMENT);
          Integer adder = arrayTrace.removeLast();
          adder++;
          arrayTrace.add(adder);
          break;
        default:
          throw new ParseException("Should not be reading string value at this point." + s);
        }
        start = buffer.position();
        end = nextUnescapedDoubleQuotePosition();
        if (end == -1) {
          throw new ParseException("Reached end of file before resolving");
        }
        current = extract(start, end);
        if (this.debugMode) {
          debugValue(current);
        }
        s = stack.peekLast();
        if (s == State.READ_OBJECT_NAME) {
          return JsonToken.OBJECT_NAME;
        } else if (s == State.READ_OBJECT_VALUE || s == State.READ_ARRAY_ELEMENT) {
          return JsonToken.VALUE_STRING;
        } else {
          return JsonToken.UNKNOWN;
        }
      case ':':
        s = stack.removeLast();
        if (s == State.READ_OBJECT_NAME) {
          stack.add(State.AWAIT_OBJECT_VALUE);
        } else {
          throw new ParseException("Object value separator without object name");
        }
        break;
      case ',':
        s = stack.removeLast();
        switch (s) {
        case READ_OBJECT_VALUE:
          stack.add(State.AWAIT_OBJECT_NAME);
          break;
        case READ_ARRAY_ELEMENT:
          stack.add(State.AWAIT_ARRAY_ELEMENT);
          break;
        default:
          throw new ParseException("Found a comma in an invalid place");
        }
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
        s = stack.removeLast();
        switch (s) {
        case AWAIT_OBJECT_VALUE:
          stack.add(State.READ_OBJECT_VALUE);
          break;
        case AWAIT_ARRAY_ELEMENT:
          stack.add(State.READ_ARRAY_ELEMENT);
          Integer adder = arrayTrace.removeLast();
          adder++;
          arrayTrace.add(adder);
          break;
        default:
          throw new ParseException("Found a number in an invalid position.");
        }
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
        s = stack.removeLast();
        switch (s) {
        case AWAIT_OBJECT_VALUE:
          stack.add(State.READ_OBJECT_VALUE);
          break;
        case AWAIT_ARRAY_ELEMENT:
          stack.add(State.READ_ARRAY_ELEMENT);
          Integer adder = arrayTrace.removeLast();
          adder++;
          arrayTrace.add(adder);
          break;
        default:
          throw new ParseException("Found a boolean in an invalid position");
        }
        start = buffer.position()-1;
        end = start + 4;
        current = extract(start, end).toLowerCase();
        buffer.position(buffer.position()-1);
        if (! current.equals("true")) {
          throw new ParseException("Unquoted string found: Expected true, found " + current);
        }
        return JsonToken.VALUE_BOOLEAN;
      case 'f':
        s = stack.removeLast();
        switch (s) {
        case AWAIT_OBJECT_VALUE:
          stack.add(State.READ_OBJECT_VALUE);
          break;
        case AWAIT_ARRAY_ELEMENT:
          stack.add(State.READ_ARRAY_ELEMENT);
          Integer adder = arrayTrace.removeLast();
          adder++;
          arrayTrace.add(adder);
          break;
        default:
          throw new ParseException("Found a boolean in an invalid position");
        }
        start = buffer.position()-1;
        end = start + 5;
        current = extract(start, end).toLowerCase();
        buffer.position(buffer.position()-1);
        if (! current.equals("false")) {
          throw new ParseException("Unquoted string found: Expected false, found " + current);
        }
        return JsonToken.VALUE_BOOLEAN;
      case 'n':
        s = stack.removeLast();
        switch (s) {
        case AWAIT_OBJECT_VALUE:
          stack.add(State.READ_OBJECT_VALUE);
          break;
        case AWAIT_ARRAY_ELEMENT:
          stack.add(State.READ_ARRAY_ELEMENT);
          Integer adder = arrayTrace.removeLast();
          adder++;
          arrayTrace.add(adder);
          break;
        default:
          throw new ParseException("Found a boolean in an invalid position");
        }
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

  public int getMaxDepth() throws IOException {
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

  public ByteBuffer getBuffer() {
    return this.buffer;
  }

  public int getDepth() {
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

  private void debugValue(String value) {
    StringBuilder sb = new StringBuilder();
    sb.append("[DEBUG] ");
    for (int i=1; i < currentDepth; i++) {
      sb.append("\t");
    }
    sb.append(value);
    System.out.println(sb.toString());
  }

  private void debugStack() {
    StringBuilder sb = new StringBuilder();
    sb.append("[DEBUG:STACK] ");
    for(State s: stack) {
      sb.append(s);
      sb.append(" ");
    }
    System.out.println(sb.toString());
  }

  public static class Builder {
    private final Json json;
    private boolean debug = false;
    private boolean debugStack = false;

    public Builder(Json json) {
      this.json = json;
    }

    public Builder setDebug(boolean debug) {
      this.debug = debug;
      return this;
    }

    public Builder setDebugStack(boolean debug) {
      this.debugStack = debug;
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
