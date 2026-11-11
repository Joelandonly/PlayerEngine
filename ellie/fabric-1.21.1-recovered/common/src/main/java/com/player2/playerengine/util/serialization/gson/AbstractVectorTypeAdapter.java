package com.player2.playerengine.util.serialization.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Collection;

public abstract class AbstractVectorTypeAdapter<T> extends TypeAdapter<T> {
   protected abstract Collection<String> getParts(T var1);

   protected abstract T fromParts(String[] var1) throws IOException;

   public void write(JsonWriter out, T value) throws IOException {
      if (value == null) {
         out.nullValue();
      } else {
         Collection<String> parts = this.getParts(value);
         out.value(String.join(",", parts));
      }
   }

   public T read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
         in.nextNull();
         return null;
      } else {
         String value = in.nextString();
         String[] parts = value.split(",");
         return this.fromParts(parts);
      }
   }
}
