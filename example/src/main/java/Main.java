package org.agmip.example.drake;

import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.agmip.data.json.Json;
import org.agmip.data.json.JsonGenerator;
import org.agmip.data.json.JsonParser;
import org.agmip.data.json.JsonToken;


public class Main {
  public static void main(String[] args) {
    try {
      if (args.length == 0) {
        System.err.println("Invalid number of arguments.");
        System.exit(-1);
      }
      switch( args[0].toLowerCase()) {
      case "parse":
        if (args.length < 2) {
          System.err.println("Invalid number of arguments.");
          System.exit(-1);
        }
        parse(args[1]);
        break;
      case "create":
        if (args.length == 2) {
        } else {
          create(null);
        }
        break;
      default:
        System.err.println("Invalid command");
        System.exit(-1);
        break;
      }
    } catch (IOException ex) { // | InterruptedException ex) {
      System.err.println(ex.getMessage());
      System.exit(-1);
    }
  }

  public static void create(String fileName) throws IOException {
    Path jsonFile = null;
    if (fileName == null) {
      jsonFile = Files.createTempFile("drake",".json");
    } else {
      jsonFile = Paths.get(fileName);
    }
    Json json = Json.load(jsonFile);
    System.out.println("File: " + jsonFile.toString() + " [" + json.size() + "]");
    JsonGenerator g = new JsonGenerator.Builder(json).build();
    g.startArray();
    for(int i=0; i < 180000; i++) {
    g.startObject().writeMember("abc", "123").writeName("def").startObject().writeName("ghi");
    g.startArray().writeValue("jkl").writeValue("mno").endArray().endObject();
    g.writeMember("pqr", 456);
    g.endObject();
    }
    g.endArray();
    json.sync();
    json.close();
  }

  public static void parse(String fileName) throws IOException {
    Path jsonFile = Paths.get(fileName);
    Json json = Json.load(jsonFile);
    JsonParser p = new JsonParser.Builder(json).build();
    JsonToken prev = JsonToken.UNKNOWN;
    System.out.println("Parser in debug mode: " + p.isDebug());
    long z=0;
    while (p.hasNext()) {
      z++;
      System.out.print(z + ". ");
      JsonToken t = p.next();
      switch( t ) {
      case OBJECT_NAME:
        for (int i=0; i < p.depth()-1; i++) {
          System.out.print("\t");
        }
        System.out.print(p.get() + ": ");
        break;
      case VALUE_STRING:
        System.out.println(p.get());
        break;
      case VALUE_NUMBER:
        System.out.println(p.getAsInt());
        break;
      case VALUE_BOOLEAN:
        System.out.println(p.getAsBoolean());
        break;
      case VALUE_NULL:
        System.out.println("NULL");
        break;
      case START_OBJECT:
      case START_ARRAY:
        if (prev == JsonToken.OBJECT_NAME) {
          System.out.println();
        }
        int d = p.depth();
        if (d == 4) {
          System.out.print("--SKIPPED--");
          p.skip();
        }
      }
      prev = t;
    }
    json.close();
  }
}
