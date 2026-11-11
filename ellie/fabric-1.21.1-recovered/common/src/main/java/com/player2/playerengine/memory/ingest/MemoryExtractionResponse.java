package com.player2.playerengine.memory.ingest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MemoryExtractionResponse {
   private final List<MemoryExtractionResponse.Entity> entities;
   private final List<MemoryExtractionResponse.Relation> relations;
   private final List<String> keywords;
   private final int importance;

   public MemoryExtractionResponse(
      List<MemoryExtractionResponse.Entity> entities, List<MemoryExtractionResponse.Relation> relations, List<String> keywords, int importance
   ) {
      this.entities = entities == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(entities));
      this.relations = relations == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(relations));
      this.keywords = keywords == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(keywords));
      this.importance = importance;
   }

   public List<MemoryExtractionResponse.Entity> entities() {
      return this.entities;
   }

   public List<MemoryExtractionResponse.Relation> relations() {
      return this.relations;
   }

   public List<String> keywords() {
      return this.keywords;
   }

   public int importance() {
      return this.importance;
   }

   public boolean isEmpty() {
      return this.entities.isEmpty() && this.relations.isEmpty();
   }

   public static final class Entity {
      public final String name;
      public final String type;
      public final String content;
      public final List<String> aliases;
      public final List<String> tags;
      public final int importance;

      public Entity(String name, String type, String content, List<String> aliases, List<String> tags, int importance) {
         this.name = name;
         this.type = type;
         this.content = content;
         this.aliases = aliases == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(aliases));
         this.tags = tags == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
         this.importance = importance;
      }
   }

   public static final class Relation {
      public final String from;
      public final String to;
      public final String relation;
      public final String emotion;

      public Relation(String from, String to, String relation, String emotion) {
         this.from = from;
         this.to = to;
         this.relation = relation;
         this.emotion = emotion;
      }
   }
}
