package org.agmip.data.json;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.agmip.data.json.JsonFSM.Event;
import org.agmip.data.json.JsonFSM.State;

public class JsonGenerator {
  private ByteBuffer buffer;
  private JsonFSM fsm;
  private JsonParser parser;
  private boolean debug = false;

  public JsonGenerator(Builder builder) throws IOException {
    this.debug = builder.debug;
    if (builder.parser.isPresent()) {
      this.parser = builder.parser.get();
      this.fsm = this.parser.fsm();
      this.buffer = this.parser.buffer();
    } else {
      this.fsm = new JsonFSM();
      this.parser = new JsonParser.Builder(builder.json)
          .setDebug(builder.debug)
          .setSharedFSM(this.fsm)
          .build();
      this.buffer = this.parser.buffer();
    }
  }

  public JsonParser parser() {
    return this.parser;
  }

  public JsonGenerator startObject() throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.START_OBJECT);
    JsonWriter.write(this.buffer, "{");
    return this;
  }

  public JsonGenerator endObject() throws IOException {
    this.fsm.transition(Event.END_OBJECT);
    JsonWriter.write(this.buffer, "}");
    return this;
  }

  public JsonGenerator startArray() throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.START_ARRAY);
    JsonWriter.write(this.buffer, "[");
    return this;
  }

  public JsonGenerator endArray() throws IOException {
    this.fsm.transition(Event.END_ARRAY);
    JsonWriter.write(this.buffer, "]");
    return this;
  }

  public JsonGenerator writeName(String name) throws IOException {
    if (this.fsm.current() == State.OBJECT_VALUE_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_STRING);
    this.writeString(name);
    this.fsm.transition(Event.WRITE_OBJECT_SEPARATOR);
    JsonWriter.write(this.buffer, ":");
    return this;
  }

  public JsonGenerator writeValue() throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, "null");
    return this;
  }

  public JsonGenerator writeValue(String value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_STRING);
    this.writeString(value);
    return this;
  }

  public JsonGenerator writeValue(boolean value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, (value) ? "true" : "false");
    return this;
  }

  public JsonGenerator writeValue(short value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, Short.toString(value));
    return this;
  }

  public JsonGenerator writeValue(int value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, Integer.toString(value));
    return this;
  }

  public JsonGenerator writeValue(long value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, Long.toString(value));
    return this;
  }

  public JsonGenerator writeValue(float value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, Float.toString(value));
    return this;
  }

  public JsonGenerator writeValue(double value) throws IOException {
    if (this.fsm.current() == State.ARRAY_ELEMENT_READ) {
      this.writeValueSeparator();
    }
    this.fsm.transition(Event.WRITE_VALUE);
    JsonWriter.write(this.buffer, Double.toString(value));
    return this;
  }

  public JsonGenerator writeMember(String name) throws IOException {
    this.writeName(name).writeValue();
    return this;
  }

  public JsonGenerator writeMember(String name, String value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

  public JsonGenerator writeMember(String name, boolean value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

  public JsonGenerator writeMember(String name, short value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

   public JsonGenerator writeMember(String name, int value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

   public JsonGenerator writeMember(String name, long value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

   public JsonGenerator writeMember(String name, float value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

   public JsonGenerator writeMember(String name, double value) throws IOException {
    this.writeName(name).writeValue(value);
    return this;
  }

   public void close() {
    this.parser.close();
  }

  private void writeString(String str) throws IOException {
    JsonWriter.write(this.buffer, "\"");
    JsonWriter.write(this.buffer, str);
    JsonWriter.write(this.buffer, "\"");
  }

  private void writeValueSeparator() throws IOException {
    this.fsm.transition(Event.WRITE_VALUE_SEPARATOR);
    JsonWriter.write(this.buffer, ",");
  }

  public static class Builder {
    private final Json json;
    private boolean debug = false;
    private Optional<JsonParser> parser = Optional.empty();
    private Optional<JsonFSM> fsm = Optional.empty();

    public Builder(Json json) {
      this.json = json;
    }

    public Builder setDebug(boolean debug) {
      this.debug = debug;
      return this;
    }

    public Builder setJsonParser(JsonParser parser) {
      this.parser = Optional.ofNullable(parser);
      this.fsm = Optional.ofNullable(parser.fsm());
      return this;
    }

    public JsonGenerator build() throws IOException {
      return new JsonGenerator(this);
    }
  }
}
