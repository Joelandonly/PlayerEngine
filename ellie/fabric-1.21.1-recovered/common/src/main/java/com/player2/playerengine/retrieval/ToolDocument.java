package com.player2.playerengine.retrieval;

import java.util.Collections;
import java.util.List;

public final class ToolDocument {
   private final String id;
   private final String name;
   private final String description;
   private final String whenToUse;
   private final List<String> examples;
   private final List<String> keywords;
   private final List<String> categoryTags;

   public ToolDocument(String id, String name, String description, String whenToUse, List<String> examples, List<String> keywords, List<String> categoryTags) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.whenToUse = whenToUse;
      this.examples = Collections.unmodifiableList(examples);
      this.keywords = Collections.unmodifiableList(keywords);
      this.categoryTags = Collections.unmodifiableList(categoryTags);
   }

   public String id() {
      return this.id;
   }

   public String name() {
      return this.name;
   }

   public String description() {
      return this.description;
   }

   public String whenToUse() {
      return this.whenToUse;
   }

   public List<String> examples() {
      return this.examples;
   }

   public List<String> keywords() {
      return this.keywords;
   }

   public List<String> categoryTags() {
      return this.categoryTags;
   }

   public String indexedText() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.name).append(' ');
      sb.append(this.description).append(' ');
      sb.append(this.whenToUse).append(' ');

      for (String e : this.examples) {
         sb.append(e).append(' ');
      }

      for (String k : this.keywords) {
         sb.append(k).append(' ');
      }

      return sb.toString();
   }
}
