package com.player2.playerengine.player2api;

import com.google.gson.JsonObject;
import com.player2.playerengine.automaton.utils.DirUtil;
import com.player2.playerengine.player2api.status.ObjectStatus;
import com.player2.playerengine.player2api.utils.LlmJsonParseException;
import com.player2.playerengine.player2api.utils.Utils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConversationHistory {
   private static final Logger LOGGER = LogManager.getLogger();
   private final List<JsonObject> conversationHistory = new ArrayList<>();
   private final Path historyFile;
   private boolean loadedFromFile = false;
   static final int MAX_HISTORY = 16;
   static final int COMPACTION_TAIL_COUNT = 8;

   public ConversationHistory(String initialSystemPrompt, Path historyFile) {
      this.historyFile = historyFile;
      if (this.historyFile != null && Files.exists(this.historyFile)) {
         this.loadFromFile();
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = true;
      } else {
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = false;
      }
   }

   public ConversationHistory(String initialSystemPrompt, String characterName, String characterShortName) {
      Path configDir = DirUtil.getConfigDir();
      String nameSlug = characterName.replaceAll("\\s+", "_");
      String shortSlug = characterShortName != null && !characterShortName.isBlank() ? characterShortName.replaceAll("\\s+", "_") : "conversation";
      Path canonicalFile = configDir.resolve(nameSlug + "_" + shortSlug + ".txt");
      Path legacyDuplicateNameFile = configDir.resolve(nameSlug + "_" + nameSlug + ".txt");
      if (Files.exists(canonicalFile)) {
         this.historyFile = canonicalFile;
      } else if (Files.exists(legacyDuplicateNameFile)) {
         this.historyFile = legacyDuplicateNameFile;
      } else {
         this.historyFile = canonicalFile;
      }

      if (Files.exists(this.historyFile)) {
         this.loadFromFile();
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = true;
      } else {
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = false;
      }
   }

   public ConversationHistory(String initialSystemPrompt) {
      this.historyFile = null;
      this.setBaseSystemPrompt(initialSystemPrompt);
      this.loadedFromFile = false;
   }

   public boolean isLoadedFromFile() {
      return this.loadedFromFile;
   }

   public void addHistory(JsonObject text, boolean doCutOff, Player2APIService player2apiService) {
      this.conversationHistory.add(text);
      if (!doCutOff && this.conversationHistory.size() > 16) {
         trimOldestToLimit(this.conversationHistory);
      }

      if (doCutOff && this.conversationHistory.size() > 16) {
         int tailStart = compactionTailStart(this.conversationHistory.size());
         List<JsonObject> toSummarize = new ArrayList<>(this.conversationHistory.subList(1, tailStart));
         String summary = this.summarizeHistory(toSummarize, player2apiService);
         if (summary != null && !summary.isBlank()) {
            JsonObject systemPrompt = this.conversationHistory.get(0);
            List<JsonObject> tail = new ArrayList<>(this.conversationHistory.subList(tailStart, this.conversationHistory.size()));
            this.conversationHistory.clear();
            this.conversationHistory.add(systemPrompt);
            JsonObject summaryMsg = new JsonObject();
            summaryMsg.addProperty("role", "assistant");
            summaryMsg.addProperty("content", "Summary of earlier events: " + summary);
            this.conversationHistory.add(summaryMsg);
            this.conversationHistory.addAll(tail);
         } else {
            trimOldestToLimit(this.conversationHistory);
         }

         if (this.historyFile != null) {
            this.saveToFile();
         }
      } else if (doCutOff && this.conversationHistory.size() % 8 == 0 && this.historyFile != null) {
         this.saveToFile();
      }
   }

   private String summarizeHistory(List<JsonObject> messages, Player2APIService player2apiService) {
      String summarizationPrompt = "    Our AI agent that has been chatting with user and playing minecraft.\n    Update agent's memory by summarizing the following conversation in the next response.\n\n    Use natural language, not JSON format.\n\n    Prioritize preserving important facts, things user asked agent to remember, useful tips.\n    Do not record stats, inventory, code or docs; limit to 500 chars.\n";
      ConversationHistory temp = new ConversationHistory(summarizationPrompt);

      for (JsonObject msg : messages) {
         temp.addHistory(Utils.deepCopy(msg), false, player2apiService);
      }

      try {
         String resp = player2apiService.completeConversationToString(temp, AiTaskClass.SUMMARIZATION);
         return resp;
      } catch (Exception var6) {
         var6.printStackTrace();
         System.err.println("Error communicating with API");
         return "";
      }
   }

   private void saveToFile() {
      try {
         if (this.historyFile != null && this.historyFile.getParent() != null) {
            Files.createDirectories(this.historyFile.getParent());
         }

         BufferedWriter writer = Files.newBufferedWriter(this.historyFile);

         try {
            for (JsonObject msg : this.conversationHistory) {
               writer.write(msg.toString());
               writer.newLine();
            }

            if (writer != null) {
               writer.close();
            }
         } catch (Throwable var5) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   private void loadFromFile() {
      List<JsonObject> loaded = new ArrayList<>();

      try {
         BufferedReader reader = Files.newBufferedReader(this.historyFile);

         try {
            String line;
            while ((line = reader.readLine()) != null) {
               JsonObject obj;
               try {
                  obj = Utils.parseCleanedJson(line);
               } catch (LlmJsonParseException ex) {
                  LOGGER.warn("loadFromFile: skipping unparseable history line");
                  continue;
               }

               if (obj.has("content")) {
                  String content = obj.get("content").getAsString();
                  if (content.length() > 500) {
                     obj.addProperty("content", content.substring(0, 500));
                  }
               }

               loaded.add(obj);
               trimOldestToLimit(loaded);
            }

            this.conversationHistory.clear();
            this.conversationHistory.addAll(loaded);
            if (reader != null) {
               reader.close();
            }
         } catch (Throwable var7) {
            if (reader != null) {
               try {
                  reader.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }
      } catch (IOException var8) {
         var8.printStackTrace();
         this.conversationHistory.clear();
      }
   }

   public void addUserMessage(String userText, Player2APIService player2apiService) {
      JsonObject objectToAdd = new JsonObject();
      objectToAdd.addProperty("role", "user");
      objectToAdd.addProperty("content", LogEgressGuard.capForModel(userText, "user"));
      this.addHistory(objectToAdd, false, player2apiService);
   }

   public void setBaseSystemPrompt(String newPrompt) {
      if (!this.conversationHistory.isEmpty() && "system".equals(this.conversationHistory.get(0).get("role").getAsString())) {
         this.conversationHistory.get(0).addProperty("content", LogEgressGuard.capForModel(newPrompt, "system"));
      } else {
         JsonObject systemMessage = new JsonObject();
         systemMessage.addProperty("role", "system");
         systemMessage.addProperty("content", LogEgressGuard.capForModel(newPrompt, "system"));
         this.conversationHistory.add(0, systemMessage);
      }

      trimOldestToLimit(this.conversationHistory);
   }

   public void addSystemMessage(String systemText, Player2APIService player2apiService) {
      JsonObject objectToAdd = new JsonObject();
      objectToAdd.addProperty("role", "system");
      objectToAdd.addProperty("content", LogEgressGuard.capForModel(systemText, "system"));
      this.addHistory(objectToAdd, false, player2apiService);
   }

   public void addAssistantMessage(String messageText, Player2APIService player2apiService) {
      JsonObject objectToAdd = new JsonObject();
      objectToAdd.addProperty("role", "assistant");
      objectToAdd.addProperty("content", LogEgressGuard.capForModel(messageText, "assistant"));
      this.addHistory(objectToAdd, true, player2apiService);
   }

   public List<JsonObject> getListJSON() {
      return this.conversationHistory;
   }

   public Optional<String> getLastAssistantContent() {
      for (int i = this.conversationHistory.size() - 1; i >= 0; i--) {
         JsonObject m = this.conversationHistory.get(i);
         if (m.has("role") && "assistant".equals(m.get("role").getAsString())) {
            if (!m.has("content")) {
               return Optional.of("");
            }

            return Optional.of(m.get("content").getAsString());
         }
      }

      return Optional.empty();
   }

   private static String normalizeAssistantTextForComparison(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('‑', '-').replace('–', '-').replace('—', '-').replaceAll("\\s+", " ").trim();
   }

   private static Set<String> significantTokens(String normalizedLowercase) {
      String stripped = normalizedLowercase.replace('\'', ' ').replaceAll("[^a-z0-9\\s]", " ");
      return Arrays.stream(stripped.split("\\s+")).filter(w -> w.length() > 2).collect(Collectors.toCollection(HashSet::new));
   }

   private static double tokenJaccard(String a, String b) {
      Set<String> sa = significantTokens(normalizeAssistantTextForComparison(a));
      Set<String> sb = significantTokens(normalizeAssistantTextForComparison(b));
      if (sa.size() >= 4 && sb.size() >= 4) {
         int inter = 0;

         for (String w : sa) {
            if (sb.contains(w)) {
               inter++;
            }
         }

         int union = sa.size() + sb.size() - inter;
         return union == 0 ? 0.0 : (double)inter / union;
      } else {
         return 0.0;
      }
   }

   public static boolean isRedundantAssistantAfterInfo(String previousAssistant, String newAssistant) {
      String p = normalizeAssistantTextForComparison(previousAssistant);
      String n = normalizeAssistantTextForComparison(newAssistant);
      if (p.isEmpty() || n.isEmpty()) {
         return false;
      } else if (p.equals(n)) {
         return true;
      } else {
         return !p.contains(n) && !n.contains(p) ? tokenJaccard(previousAssistant, newAssistant) >= 0.45 : true;
      }
   }

   public ConversationHistory copyThenWrapLatestWithStatus(
      String worldStatus,
      String agentStatus,
      String altoclefStatusMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock
   ) {
      return this.copyThenWrapLatestWithStatus(
         worldStatus, agentStatus, altoclefStatusMsgs, player2apiService, reminderString, validCommandsBlock, Optional.empty()
      );
   }

   public ConversationHistory copyThenWrapLatestWithStatus(
      String worldStatus,
      String agentStatus,
      String altoclefStatusMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock,
      Optional<String> memoryBlock
   ) {
      return this.copyThenWrapLatestWithStatus(
         worldStatus, agentStatus, altoclefStatusMsgs, player2apiService, reminderString, validCommandsBlock, memoryBlock, Optional.empty()
      );
   }

   public ConversationHistory copyThenWrapLatestWithStatus(
      String worldStatus,
      String agentStatus,
      String altoclefStatusMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock,
      Optional<String> memoryBlock,
      Optional<String> moodBlock
   ) {
      return this.copyThenWrapLatestWithStatus(
         worldStatus, agentStatus, altoclefStatusMsgs, player2apiService, reminderString, validCommandsBlock, memoryBlock, moodBlock, Optional.empty()
      );
   }

   public ConversationHistory copyThenWrapLatestWithStatus(
      String worldStatus,
      String agentStatus,
      String altoclefStatusMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock,
      Optional<String> memoryBlock,
      Optional<String> moodBlock,
      Optional<String> additionalPromptBlock
   ) {
      ConversationHistory copy = new ConversationHistory(this.conversationHistory.get(0).get("content").getAsString());

      for (int i = 1; i < this.conversationHistory.size() - 1; i++) {
         copy.addHistory(Utils.deepCopy(this.conversationHistory.get(i)), false, player2apiService);
      }

      if (this.conversationHistory.size() > 1) {
         JsonObject last = Utils.deepCopy(this.conversationHistory.get(this.conversationHistory.size() - 1));
         if ("user".equals(last.get("role").getAsString())) {
            String originalContent = last.get("content").getAsString();
            ObjectStatus msgObj = new ObjectStatus();
            msgObj.add("userMessage", originalContent);
            reminderString.ifPresent(remind -> msgObj.add("reminders", remind));
            msgObj.add("worldStatus", worldStatus);
            msgObj.add("agentStatus", agentStatus);
            if (!altoclefStatusMsgs.isBlank()) {
               msgObj.add("gameDebugMessages", altoclefStatusMsgs);
            }

            validCommandsBlock.filter(s -> !s.isBlank()).ifPresent(block -> msgObj.add("validCommands", block));
            memoryBlock.filter(s -> !s.isBlank()).ifPresent(block -> msgObj.add("memory", block));
            moodBlock.filter(s -> !s.isBlank()).ifPresent(block -> msgObj.add("currentMood", block));
            additionalPromptBlock.filter(s -> !s.isBlank()).ifPresent(block -> msgObj.add("additionalPrompt", block));
            last.addProperty("content", msgObj.toString());
         }

         copy.addHistory(last, false, player2apiService);
      }

      return copy;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ConversationHistory {\n");

      for (JsonObject message : this.conversationHistory) {
         String role = message.has("role") ? message.get("role").getAsString() : "unknown";
         String content = message.has("content") ? message.get("content").getAsString() : "";
         sb.append("  [").append(role).append("] ").append(content).append("\n");
      }

      sb.append("}");
      return sb.toString();
   }

   public void clear() {
      if (!this.conversationHistory.isEmpty()) {
         JsonObject systemPrompt = this.conversationHistory.get(0);
         this.conversationHistory.clear();
         this.conversationHistory.add(systemPrompt);
      }

      if (this.historyFile != null) {
         try {
            Files.deleteIfExists(this.historyFile);
         } catch (IOException var2) {
            var2.printStackTrace();
         }
      }
   }

   public void saveNow() {
      if (this.historyFile != null) {
         this.saveToFile();
      }
   }

   public void reloadNow() {
      if (this.historyFile == null) {
         this.loadedFromFile = false;
      } else {
         if (Files.exists(this.historyFile)) {
            this.loadFromFile();
            this.loadedFromFile = true;
         } else {
            this.loadedFromFile = false;
         }
      }
   }

   public Path getHistoryFile() {
      return this.historyFile;
   }

   static int compactionTailStart(int historySize) {
      return Math.max(1, historySize - 8);
   }

   static void trimOldestToLimit(List<JsonObject> history) {
      if (history != null) {
         while (history.size() > 16) {
            int removeIndex = !history.isEmpty() && history.get(0).has("role") && "system".equals(history.get(0).get("role").getAsString()) ? 1 : 0;
            history.remove(removeIndex);
         }
      }
   }
}
