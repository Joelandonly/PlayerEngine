package com.player2.playerengine.memory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MemoryNode {
   public static final int CURRENT_SCHEMA_VERSION = 1;
   private final String id;
   private final String content;
   private final String type;
   private final String canonicalName;
   private final String[] aliases;
   private final String[] tags;
   private final int importance;
   private final long createdTick;
   private final long timestampMs;
   private final long lastSeenTick;
   private final long lastRetrievedTick;
   private final int mentionCount;
   private final float[] vector;
   private final String vectorModel;
   private final int schemaVersion;

   public MemoryNode(
      String id,
      String content,
      String type,
      String canonicalName,
      String[] aliases,
      String[] tags,
      int importance,
      long createdTick,
      long timestampMs,
      long lastSeenTick,
      long lastRetrievedTick,
      int mentionCount,
      float[] vector,
      String vectorModel,
      int schemaVersion
   ) {
      this.id = id;
      this.content = content;
      this.type = type;
      this.canonicalName = canonicalName;
      this.aliases = aliases == null ? new String[0] : (String[])aliases.clone();
      this.tags = tags == null ? new String[0] : (String[])tags.clone();
      this.importance = importance;
      this.createdTick = createdTick;
      this.timestampMs = timestampMs;
      this.lastSeenTick = lastSeenTick;
      this.lastRetrievedTick = lastRetrievedTick;
      this.mentionCount = mentionCount;
      this.vector = vector == null ? null : (float[])vector.clone();
      this.vectorModel = vectorModel;
      this.schemaVersion = schemaVersion;
   }

   public String id() {
      return this.id;
   }

   public String content() {
      return this.content;
   }

   public String type() {
      return this.type;
   }

   public String canonicalName() {
      return this.canonicalName;
   }

   public List<String> aliases() {
      return Collections.unmodifiableList(Arrays.asList(this.aliases));
   }

   public List<String> tags() {
      return Collections.unmodifiableList(Arrays.asList(this.tags));
   }

   public int importance() {
      return this.importance;
   }

   public long createdTick() {
      return this.createdTick;
   }

   public long timestampMs() {
      return this.timestampMs;
   }

   public long lastSeenTick() {
      return this.lastSeenTick;
   }

   public long lastRetrievedTick() {
      return this.lastRetrievedTick;
   }

   public int mentionCount() {
      return this.mentionCount;
   }

   public float[] vector() {
      return this.vector == null ? null : (float[])this.vector.clone();
   }

   public boolean hasVector() {
      return this.vector != null;
   }

   public String vectorModel() {
      return this.vectorModel;
   }

   public int schemaVersion() {
      return this.schemaVersion;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof MemoryNode that)
            ? false
            : this.importance == that.importance
               && this.createdTick == that.createdTick
               && this.timestampMs == that.timestampMs
               && this.lastSeenTick == that.lastSeenTick
               && this.lastRetrievedTick == that.lastRetrievedTick
               && this.mentionCount == that.mentionCount
               && this.schemaVersion == that.schemaVersion
               && Objects.equals(this.id, that.id)
               && Objects.equals(this.content, that.content)
               && Objects.equals(this.type, that.type)
               && Objects.equals(this.canonicalName, that.canonicalName)
               && Arrays.equals(this.aliases, that.aliases)
               && Arrays.equals(this.tags, that.tags)
               && Arrays.equals(this.vector, that.vector)
               && Objects.equals(this.vectorModel, that.vectorModel);
      }
   }

   @Override
   public int hashCode() {
      int result = Objects.hash(
         this.id,
         this.content,
         this.type,
         this.canonicalName,
         this.importance,
         this.createdTick,
         this.timestampMs,
         this.lastSeenTick,
         this.lastRetrievedTick,
         this.mentionCount,
         this.vectorModel,
         this.schemaVersion
      );
      result = 31 * result + Arrays.hashCode(this.aliases);
      result = 31 * result + Arrays.hashCode(this.tags);
      return 31 * result + Arrays.hashCode(this.vector);
   }

   @Override
   public String toString() {
      return "MemoryNode{id='"
         + this.id
         + "', canonicalName='"
         + this.canonicalName
         + "', type='"
         + this.type
         + "', importance="
         + this.importance
         + ", mentionCount="
         + this.mentionCount
         + ", hasVector="
         + (this.vector != null)
         + "}";
   }
}
