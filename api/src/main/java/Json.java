package org.agmip.data.json;

import java.io.IOException;
import static java.lang.Math.toIntExact;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Json {
  private FileChannel channel;
  private ByteBuffer store;
  private Path workingPath;
  private static final int MIN_BUFFER_SIZE = 10485760;
  protected Json(){}

  public static Json load(Path jsonFile, boolean isGzip) throws IOException {
    Json j = new Json().openFile(jsonFile, isGzip);
    return j;
  }

  public static Json load(Path jsonFile) throws IOException {
    return load(jsonFile, false);
  }

  private Json openFile(Path jsonFile, boolean isGzip) throws IOException {
    this.workingPath = jsonFile;
    this.channel = FileChannel.open(this.workingPath, StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    if (Files.exists(this.workingPath)) {
      if (this.channel.size() > MIN_BUFFER_SIZE) {
        int bump = (toIntExact(this.channel.size() - 1)) / (MIN_BUFFER_SIZE + 1);
        this.store = ByteBuffer.allocateDirect(bump * MIN_BUFFER_SIZE);
        // Still need to copy all the data into the store
      } else {
        this.store = ByteBuffer.allocateDirect(MIN_BUFFER_SIZE);
      }
    } else {
      this.store = ByteBuffer.allocateDirect(MIN_BUFFER_SIZE);
    }
    return this;
  }

  protected ByteBuffer store() {
    return this.store;
  }

  public int size() {
    return this.store.limit();
  }

  protected void resize() {
  }

  public void sync() throws IOException {
    this.tail();
    this.store.flip();
    this.channel.position(0L);
    this.channel.write(this.store);
  }

  private void tail() {
    long pos = this.store.position();
    this.store.rewind();
    while(this.store.hasRemaining()) {
      byte b = this.store.get();
      if (b == 0) {
        break;
      }
    }
  }

  public void close() throws IOException {
    this.store = null;
    this.channel.close();
  }
}
