package com.player2.playerengine.memory.reflection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.MemoryNodeType;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.MergePlan;
import com.player2.playerengine.memory.budget.Layer3Context;
import com.player2.playerengine.memory.budget.MemoryGate;
import com.player2.playerengine.memory.budget.MemoryGateDecision;
import com.player2.playerengine.memory.budget.MemoryLlmClient;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.Player2PayerResolution;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

public final class ReflectionService {
   public static final int MAX_INSIGHTS = 5;
   private static final int MIN_INSIGHTS = 3;
   public static final int QUESTION_COUNT = 3;
   public static final String RELATION_REFLECTED_ABOUT = "reflected_about";

   private ReflectionService() {
   }

   public static void scheduleReflection(
      MinecraftServer server,
      MemoryStore store,
      Layer3Context layer3Context,
      AtomicBoolean inFlight,
      ReflectionService.Retriever retriever,
      long nowTick,
      Consumer<String> onSummaryUpdated,
      ReflectionService.DegradationSink degradation
   ) {
      if (server != null && store != null) {
         if (layer3Context != null && layer3Context.layer3Enabled()) {
            Player2PayerResolution.ApiBillingContext ownerBilling = layer3Context.ownerBilling();
            if (ownerBilling == null || ownerBilling.billingKey() == null) {
               reportClosedGate(layer3Context, degradation);
            } else if (inFlight == null || inFlight.compareAndSet(false, true)) {
               String currentSummary = store.relationshipSummary();
               String recentDigest = buildRecentDigest(store);
               MemoryLlmClient.MEMORY_EXECUTOR.submit(() -> {
                  try {
                     runReflection(server, store, ownerBilling, retriever, nowTick, currentSummary, recentDigest, onSummaryUpdated, degradation);
                  } catch (Exception e) {
                     PlayerEngine.LOGGER.warn("Memory reflection failed (reason: {}); will retry next trigger.", e.getClass().getSimpleName());
                  } finally {
                     if (inFlight != null) {
                        inFlight.set(false);
                     }
                  }
               });
            }
         } else {
            reportClosedGate(layer3Context, degradation);
         }
      }
   }

   private static void runReflection(
      MinecraftServer server,
      MemoryStore store,
      Player2PayerResolution.ApiBillingContext ownerBilling,
      ReflectionService.Retriever retriever,
      long nowTick,
      String currentSummary,
      String recentDigest,
      Consumer<String> onSummaryUpdated,
      ReflectionService.DegradationSink degradation
   ) throws Exception {
      List<String> questions = askSalientQuestions(server, ownerBilling, currentSummary, recentDigest);
      if (questions == null) {
         reportBudgetSkip(degradation);
      } else if (!questions.isEmpty()) {
         List<MemoryNode> retrieved = new ArrayList<>();
         if (retriever != null) {
            for (String q : questions) {
               List<MemoryNode> hits = retriever.retrieve(q, 5);
               if (hits != null) {
                  retrieved.addAll(hits);
               }
            }
         }

         String retrievedDigest = buildNodeDigest(retrieved);
         List<ReflectionService.Insight> insights = synthesizeInsights(server, ownerBilling, currentSummary, retrievedDigest);
         if (insights == null) {
            reportBudgetSkip(degradation);
         } else {
            MergePlan plan = buildReflectionPlan(insights, retrieved, nowTick);
            String rawSummary = synthesizeSummary(server, ownerBilling, currentSummary, retrievedDigest, insights);
            if (rawSummary == null) {
               reportBudgetSkip(degradation);
               applyOnServerThread(server, store, plan, null, onSummaryUpdated);
            } else {
               RelationshipSummary summary = RelationshipSummary.of(rawSummary);
               applyOnServerThread(server, store, plan, summary, onSummaryUpdated);
            }
         }
      }
   }

   private static List<String> askSalientQuestions(
      MinecraftServer server, Player2PayerResolution.ApiBillingContext billing, String currentSummary, String recentDigest
   ) throws Exception {
      ConversationHistory history = new ConversationHistory(
         "You help a game companion reflect on its relationship with a player. Given the current relationship summary and a digest of recent memories, propose exactly 3 short, distinct questions worth answering about the relationship. Reply with ONLY a JSON object {\"questions\": [\"...\", ...]} and nothing else."
      );
      history.addUserMessage("Current summary: " + nz(currentSummary) + "\nRecent memories: " + nz(recentDigest), null);
      if (!MemoryGate.reserveSlot(billing)) {
         return null;
      }

      String reply = MemoryLlmClient.completeConversationToString(server, billing, history, AiTaskClass.SUMMARIZATION);
      return parseStringArray(reply, "questions", 3);
   }

   private static List<ReflectionService.Insight> synthesizeInsights(
      MinecraftServer server, Player2PayerResolution.ApiBillingContext billing, String currentSummary, String retrievedDigest
   ) throws Exception {
      ConversationHistory history = new ConversationHistory(
         "You synthesize durable relationship insights for a game companion. From the retrieved memories, write 3 to 5 concise insights, each grounded ONLY in the provided memories (cite the entity name it is about). Reply with ONLY a JSON object {\"insights\": [{\"text\": \"...\", \"about\": \"<entity name>\"}, ...]} and nothing else."
      );
      history.addUserMessage("Current summary: " + nz(currentSummary) + "\nRetrieved memories: " + nz(retrievedDigest), null);
      if (!MemoryGate.reserveSlot(billing)) {
         return null;
      }

      String reply = MemoryLlmClient.completeConversationToString(server, billing, history, AiTaskClass.SUMMARIZATION);
      return parseInsights(reply);
   }

   private static String synthesizeSummary(
      MinecraftServer server,
      Player2PayerResolution.ApiBillingContext billing,
      String currentSummary,
      String retrievedDigest,
      List<ReflectionService.Insight> insights
   ) throws Exception {
      ConversationHistory history = new ConversationHistory(
         "You write a one or two sentence summary of how a game companion sees its relationship with a player, grounded ONLY in the provided context. Keep it under "
            + MemoryStore.effectiveSummaryCharCap()
            + " characters. Reply with ONLY a JSON object {\"summary\": \"...\"} and nothing else."
      );
      history.addUserMessage(
         "Previous summary: " + nz(currentSummary) + "\nNew insights: " + insightDigest(insights) + "\nRetrieved memories: " + nz(retrievedDigest), null
      );
      if (!MemoryGate.reserveSlot(billing)) {
         return null;
      }

      String reply = MemoryLlmClient.completeConversationToString(server, billing, history, AiTaskClass.SUMMARIZATION);
      return parseStringField(reply, "summary");
   }

   private static void applyOnServerThread(
      MinecraftServer server, MemoryStore store, MergePlan plan, RelationshipSummary summary, Consumer<String> onSummaryUpdated
   ) {
      server.execute(
         () -> {
            if (plan != null && !plan.isEmpty()) {
               store.mergeCandidates(plan);
            }

            if (summary != null && !summary.isEmpty()) {
               store.setRelationshipSummary(summary.text());
               PlayerEngine.LOGGER
                  .info(
                     "Memory: reflection updated relationship summary (v{}, {} chars) for {}.",
                     store.summaryVersion(),
                     store.relationshipSummary().length(),
                     store.scope()
                  );
               if (onSummaryUpdated != null) {
                  onSummaryUpdated.accept(store.relationshipSummary());
               }
            } else {
               store.resetReflectionCounterOnly();
            }
         }
      );
   }

   private static MergePlan buildReflectionPlan(List<ReflectionService.Insight> insights, List<MemoryNode> retrieved, long nowTick) {
      MergePlan.Builder builder = MergePlan.builder();
      long timestampMs = System.currentTimeMillis();
      int written = 0;

      for (ReflectionService.Insight insight : insights) {
         if (written >= 5) {
            break;
         }

         if (insight.text != null && !insight.text.isBlank()) {
            String reflectionId = "reflection:" + UUID.randomUUID();
            String content = MemoryCaps.capContent(insight.text);
            String name = MemoryCaps.capName(insight.about != null && !insight.about.isBlank() ? insight.about : "reflection");
            builder.upsert(
               new MergePlan.NodeUpsert(
                  reflectionId, content, MemoryNodeType.REFLECTION.wire(), name, List.of(), List.of("reflection"), 0, timestampMs, nowTick
               )
            );
            String targetId = matchEntityId(insight.about, retrieved);
            if (targetId != null) {
               builder.edge(new MergePlan.EdgeUpsert(reflectionId, targetId, "reflected_about", 1.0, nowTick));
            }

            written++;
         }
      }

      return builder.build();
   }

   private static String matchEntityId(String about, List<MemoryNode> retrieved) {
      if (about != null && !about.isBlank() && retrieved != null) {
         String target = about.trim();

         for (MemoryNode n : retrieved) {
            if (n.canonicalName() != null && n.canonicalName().equalsIgnoreCase(target)) {
               return n.id();
            }
         }

         for (MemoryNode n : retrieved) {
            for (String alias : n.aliases()) {
               if (alias != null && alias.equalsIgnoreCase(target)) {
                  return n.id();
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String buildRecentDigest(MemoryStore store) {
      MemoryStore.Snapshot snap = store.snapshot();
      if (snap == null) {
         return "";
      }

      List<MemoryNode> nodes = new ArrayList<>(snap.graph().nodes());
      nodes.sort((a, b) -> Long.compare(b.lastSeenTick(), a.lastSeenTick()));
      return buildNodeDigest(nodes.size() > 12 ? nodes.subList(0, 12) : nodes);
   }

   private static String buildNodeDigest(List<MemoryNode> nodes) {
      if (nodes != null && !nodes.isEmpty()) {
         StringBuilder sb = new StringBuilder();
         int shown = 0;

         for (MemoryNode n : nodes) {
            if (shown >= 24) {
               break;
            }

            if (sb.length() > 0) {
               sb.append("; ");
            }

            sb.append(nz(n.canonicalName())).append(": ").append(nz(n.content()));
            shown++;
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static String insightDigest(List<ReflectionService.Insight> insights) {
      if (insights != null && !insights.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (ReflectionService.Insight i : insights) {
            if (i.text != null && !i.text.isBlank()) {
               if (sb.length() > 0) {
                  sb.append("; ");
               }

               sb.append(i.text);
            }
         }

         return sb.toString();
      } else {
         return "";
      }
   }

   private static void reportClosedGate(Layer3Context ctx, ReflectionService.DegradationSink sink) {
      if (sink != null) {
         MemoryGateDecision decision = ctx != null ? ctx.gateDecision() : null;
         if (decision == null) {
            decision = MemoryGateDecision.skip(MemoryGateDecision.SkipReason.NOT_PATRON);
         }

         sink.report(decision.playerMessage(), decision.modelFeedbackToken());
      }
   }

   private static void reportBudgetSkip(ReflectionService.DegradationSink sink) {
      if (sink != null) {
         MemoryGateDecision d = MemoryGateDecision.skip(MemoryGateDecision.SkipReason.EXTRACTION_CAP_SKIP);
         sink.report(d.playerMessage(), d.modelFeedbackToken());
      }
   }

   private static List<String> parseStringArray(String reply, String key, int max) {
      List<String> out = new ArrayList<>();
      if (reply != null && !reply.isBlank()) {
         try {
            JsonObject obj = JsonParser.parseString(reply).getAsJsonObject();
            if (obj != null && obj.has(key) && obj.get(key).isJsonArray()) {
               for (JsonElement el : obj.getAsJsonArray(key)) {
                  if (out.size() >= max) {
                     break;
                  }

                  if (el != null && el.isJsonPrimitive()) {
                     String s = el.getAsString();
                     if (s != null && !s.isBlank()) {
                        out.add(s.trim());
                     }
                  }
               }

               return out;
            } else {
               return out;
            }
         } catch (Exception ignored) {
            return new ArrayList<>();
         }
      } else {
         return out;
      }
   }

   private static String parseStringField(String reply, String key) {
      if (reply != null && !reply.isBlank()) {
         try {
            JsonObject obj = JsonParser.parseString(reply).getAsJsonObject();
            return obj != null && obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
         } catch (Exception ignored) {
            return "";
         }
      } else {
         return "";
      }
   }

   private static List<ReflectionService.Insight> parseInsights(String reply) {
      List<ReflectionService.Insight> out = new ArrayList<>();
      if (reply != null && !reply.isBlank()) {
         try {
            JsonObject obj = JsonParser.parseString(reply).getAsJsonObject();
            if (obj != null && obj.has("insights") && obj.get("insights").isJsonArray()) {
               for (JsonElement el : obj.getAsJsonArray("insights")) {
                  if (out.size() >= 5) {
                     break;
                  }

                  if (el != null && el.isJsonObject()) {
                     JsonObject o = el.getAsJsonObject();
                     String text = o.has("text") && o.get("text").isJsonPrimitive() ? o.get("text").getAsString() : null;
                     String about = o.has("about") && o.get("about").isJsonPrimitive() ? o.get("about").getAsString() : null;
                     if (text != null && !text.isBlank()) {
                        out.add(new ReflectionService.Insight(text.trim(), about != null ? about.trim() : null));
                     }
                  }
               }

               return out;
            } else {
               return out;
            }
         } catch (Exception ignored) {
            return new ArrayList<>();
         }
      } else {
         return out;
      }
   }

   private static String nz(String s) {
      return s == null ? "" : s;
   }

   @FunctionalInterface
   public interface DegradationSink {
      void report(String var1, String var2);
   }

   private static final class Insight {
      final String text;
      final String about;

      Insight(String text, String about) {
         this.text = text;
         this.about = about;
      }
   }

   @FunctionalInterface
   public interface Retriever {
      List<MemoryNode> retrieve(String var1, int var2);
   }
}
