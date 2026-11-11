package com.player2.playerengine.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MergePlan {
   private final List<MergePlan.NodeUpsert> upserts;
   private final List<MergePlan.EdgeUpsert> edges;
   private final List<String> removals;

   private MergePlan(List<MergePlan.NodeUpsert> upserts, List<MergePlan.EdgeUpsert> edges, List<String> removals) {
      this.upserts = Collections.unmodifiableList(upserts);
      this.edges = Collections.unmodifiableList(edges);
      this.removals = Collections.unmodifiableList(removals);
   }

   public List<MergePlan.NodeUpsert> upserts() {
      return this.upserts;
   }

   public List<MergePlan.EdgeUpsert> edges() {
      return this.edges;
   }

   public List<String> removals() {
      return this.removals;
   }

   public boolean isEmpty() {
      return this.upserts.isEmpty() && this.edges.isEmpty() && this.removals.isEmpty();
   }

   public static MergePlan.Builder builder() {
      return new MergePlan.Builder();
   }

   public static final class Builder {
      private final List<MergePlan.NodeUpsert> upserts = new ArrayList<>();
      private final List<MergePlan.EdgeUpsert> edges = new ArrayList<>();
      private final List<String> removals = new ArrayList<>();

      public MergePlan.Builder upsert(MergePlan.NodeUpsert n) {
         if (n != null) {
            this.upserts.add(n);
         }

         return this;
      }

      public MergePlan.Builder edge(MergePlan.EdgeUpsert e) {
         if (e != null) {
            this.edges.add(e);
         }

         return this;
      }

      public MergePlan.Builder remove(String id) {
         if (id != null) {
            this.removals.add(id);
         }

         return this;
      }

      public MergePlan build() {
         return new MergePlan(this.upserts, this.edges, this.removals);
      }
   }

   public static final class EdgeUpsert {
      public final String fromId;
      public final String toId;
      public final String relation;
      public final double addWeight;
      public final long tick;

      public EdgeUpsert(String fromId, String toId, String relation, double addWeight, long tick) {
         this.fromId = fromId;
         this.toId = toId;
         this.relation = relation;
         this.addWeight = addWeight;
         this.tick = tick;
      }
   }

   public static final class NodeUpsert {
      public final String id;
      public final String content;
      public final String type;
      public final String canonicalName;
      public final List<String> aliases;
      public final List<String> tags;
      public final int importance;
      public final long timestampMs;
      public final long tick;

      public NodeUpsert(
         String id, String content, String type, String canonicalName, List<String> aliases, List<String> tags, int importance, long timestampMs, long tick
      ) {
         this.id = id;
         this.content = content;
         this.type = type;
         this.canonicalName = canonicalName;
         this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
         this.tags = tags == null ? List.of() : List.copyOf(tags);
         this.importance = importance;
         this.timestampMs = timestampMs;
         this.tick = tick;
      }
   }
}
