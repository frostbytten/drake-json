package org.agmip.data.json;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Json {
  private FileChannel channel;
  private MappedByteBuffer store;

  protected Json(){}

  public static Json load(Path jsonFile, boolean isGzip) throws IOException {
    Json j = new Json().openFile(jsonFile, isGzip);
    return j;
  }

  public static Json load(Path jsonFile) throws IOException {
    return load(jsonFile, false);
  }

  private Json openFile(Path jsonFile, boolean isGzip) throws IOException {
    this.channel = FileChannel.open(jsonFile, StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    this.store = this.channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size());
    return this;
  }

  protected ByteBuffer getStore() {
    return this.store;
  }

  public int size() {
    return this.store.limit();
  }

  public void close() throws IOException {
    this.store = null;
    this.channel.close();
  }
}
