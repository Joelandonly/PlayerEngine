package com.player2.playerengine.player2api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.utils.DirUtil;
import com.player2.playerengine.memory.MemoryScope;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.MemoryStoreRegistry;
import com.player2.playerengine.memory.ingest.MemoryIngestionService;
import com.player2.playerengine.memory.reflection.RelationshipSummary;
import com.player2.playerengine.player2api.mood.CompanionMood;
import com.player2.playerengine.util.Debug;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

public class AIPersistantData {
   public static final int ADDITIONAL_PROMPT_MAX_CHARS = 300;
   private ConversationHistory conversationHistory;
   private Character character;
   private PlayerEngineController mod;
   private String characterId;
   private Path conversationHistoryFile;
   private CompanionMood currentMood = CompanionMood.neutral();
   private Path moodFile;
   private String additionalPrompt = "";
   private Path additionalPromptFile;

   public AIPersistantData(PlayerEngineController mod, Character character) {
      this.character = character;
      this.mod = mod;
      String systemPrompt = Prompts.getAINPCSystemPrompt(character, mod.getCommandExecutor().allCommands(), mod.getOwnerUsername());
      this.characterId = character == null ? null : character.id();
      Path worldRoot = resolveWorldRootOrNull(mod);
      this.conversationHistoryFile = getConversationHistoryFileOrNull(mod, worldRoot, this.characterId);
      String basePrompt = this.withRelationshipSuffix(systemPrompt);
      if (this.conversationHistoryFile != null) {
         migrateLegacyHistoryIfPresent(character, this.characterId, worldRoot, this.conversationHistoryFile, mod);
         this.conversationHistory = new ConversationHistory(basePrompt, this.conversationHistoryFile);
      } else {
         this.conversationHistory = new ConversationHistory(basePrompt);
      }

      this.moodFile = getMoodFileOrNull(mod, worldRoot, this.characterId);
      this.loadMoodFromDiskTolerant();
      this.additionalPromptFile = getAdditionalPromptFileOrNull(mod, worldRoot, this.characterId);
      this.loadAdditionalPromptFromDiskTolerant();
   }

   public void rebindOwnerPersistenceIfChanged() {
      Path worldRoot = resolveWorldRootOrNull(this.mod);
      Path targetHistoryFile = getConversationHistoryFileOrNull(this.mod, worldRoot, this.characterId);
      if (targetHistoryFile != null && !Objects.equals(this.conversationHistoryFile, targetHistoryFile)) {
         Path previousHistoryFile = this.conversationHistoryFile;
         migrateLegacyHistoryIfPresent(this.character, this.characterId, worldRoot, targetHistoryFile, this.mod);
         copyFileIfTargetMissing(previousHistoryFile, targetHistoryFile);
         this.conversationHistoryFile = targetHistoryFile;
         String systemPrompt = Prompts.getAINPCSystemPrompt(this.character, this.mod.getCommandExecutor().allCommands(), this.mod.getOwnerUsername());
         this.conversationHistory = new ConversationHistory(this.withRelationshipSuffix(systemPrompt), this.conversationHistoryFile);
      }

      Path targetMoodFile = getMoodFileOrNull(this.mod, worldRoot, this.characterId);
      if (targetMoodFile != null && !Objects.equals(this.moodFile, targetMoodFile)) {
         copyFileIfTargetMissing(this.moodFile, targetMoodFile);
         this.moodFile = targetMoodFile;
         this.loadMoodFromDiskTolerant();
      }

      Path targetAdditionalPromptFile = getAdditionalPromptFileOrNull(this.mod, worldRoot, this.characterId);
      if (targetAdditionalPromptFile != null && !Objects.equals(this.additionalPromptFile, targetAdditionalPromptFile)) {
         copyFileIfTargetMissing(this.additionalPromptFile, targetAdditionalPromptFile);
         this.additionalPromptFile = targetAdditionalPromptFile;
         this.loadAdditionalPromptFromDiskTolerant();
      }
   }

   private static void copyFileIfTargetMissing(Path source, Path target) {
      if (source != null && target != null && !Objects.equals(source, target)) {
         try {
            if (!Files.exists(source) || Files.exists(target)) {
               return;
            }

            if (target.getParent() != null) {
               Files.createDirectories(target.getParent());
            }

            Files.copy(source, target);
         } catch (Exception var3) {
         }
      }
   }

   private String withRelationshipSuffix(String basePrompt) {
      String base = basePrompt != null ? basePrompt : "";
      String summary = this.currentRelationshipSummaryOrEmpty();
      return base + RelationshipSummary.suffixFor(summary);
   }

   private String currentRelationshipSummaryOrEmpty() {
      try {
         MemoryScope scope = this.resolveMemoryScopeOrNull();
         if (scope == null) {
            return "";
         }

         MemoryStore store = MemoryStoreRegistry.peek(scope);
         if (store == null) {
            return "";
         }

         String summary = store.relationshipSummary();
         return summary != null ? summary : "";
      } catch (Exception e) {
         return "";
      }
   }

   private MemoryScope resolveMemoryScopeOrNull() {
      if (this.characterId != null && !this.characterId.isBlank()) {
         UUID ownerUuid = resolveOwnerUuidOrNull(this.mod);
         if (ownerUuid != null) {
            return MemoryScope.of(ownerUuid, this.characterId);
         } else {
            return this.mod != null && this.mod.getPlayer() != null ? MemoryScope.ofEntityFallback(this.mod.getPlayer().getUUID(), this.characterId) : null;
         }
      } else {
         return null;
      }
   }

   public void clearHistory() {
      this.conversationHistory.clear();
   }

   public Event getGreetingEvent() {
      String suffix = " IMPORTANT: SINCE THIS IS THE FIRST MESSAGE, ONLY USE COMMAND `bodylang greeting`";
      return this.conversationHistory.isLoadedFromFile()
         ? new Event.InfoMessage("You want to welcome user back." + suffix)
         : new Event.InfoMessage(this.character.greetingInfo() + suffix);
   }

   public Event getReturnEvent(String ownerName) {
      if (!this.conversationHistory.isLoadedFromFile()) {
         return this.getGreetingEvent();
      }

      String who = ownerName != null && !ownerName.isBlank() ? ownerName : "Your owner";
      return new Event.InfoMessage(who + " has respawned you. You are returning to them; do not greet as if meeting for the first time.");
   }

   public Event getDeathRevivalEvent(String deathCause) {
      String cause = deathCause != null && !deathCause.isBlank() ? deathCause : "You died.";
      return new Event.InfoMessage(
         "You just died and the game automatically revived you. Death: "
            + cause
            + ". Do not claim you respawned yourself, and do not greet as if meeting for the first time; you may briefly react to how you died."
      );
   }

   public Event dumpEventQueueToConversationHistoryAndReturnLastEvent(Deque<Event> eventQueue, Player2APIService player2apiService) {
      Event lastEvent = null;
      List<String> committedTurns = new ArrayList<>();

      while (!eventQueue.isEmpty()) {
         Event event = eventQueue.poll();
         String turn = event.getConversationHistoryString();
         this.conversationHistory.addUserMessage(turn, player2apiService);
         committedTurns.add(turn);
         lastEvent = event;
      }

      MemoryIngestionService.onCuratedTurnsReady(this.mod, committedTurns);
      return lastEvent;
   }

   public ConversationHistory getConversationHistoryWrappedWithStatus(
      String worldStatus,
      String agentStatus,
      String altoClefDebugMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock
   ) {
      return this.getConversationHistoryWrappedWithStatus(
         worldStatus, agentStatus, altoClefDebugMsgs, player2apiService, reminderString, validCommandsBlock, Optional.empty()
      );
   }

   public ConversationHistory getConversationHistoryWrappedWithStatus(
      String worldStatus,
      String agentStatus,
      String altoClefDebugMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock,
      Optional<String> memoryBlock
   ) {
      return this.getConversationHistoryWrappedWithStatus(
         worldStatus, agentStatus, altoClefDebugMsgs, player2apiService, reminderString, validCommandsBlock, memoryBlock, Optional.empty()
      );
   }

   public ConversationHistory getConversationHistoryWrappedWithStatus(
      String worldStatus,
      String agentStatus,
      String altoClefDebugMsgs,
      Player2APIService player2apiService,
      Optional<String> reminderString,
      Optional<String> validCommandsBlock,
      Optional<String> memoryBlock,
      Optional<String> moodBlock
   ) {
      return this.conversationHistory
         .copyThenWrapLatestWithStatus(
            worldStatus,
            agentStatus,
            altoClefDebugMsgs,
            player2apiService,
            reminderString,
            validCommandsBlock,
            memoryBlock,
            moodBlock,
            this.additionalPromptBlock()
         );
   }

   public void addAssistantMessage(String llmMessage, Player2APIService player2apiService) {
      this.conversationHistory.addAssistantMessage(llmMessage, player2apiService);
   }

   public Optional<String> getLastAssistantContent() {
      return this.conversationHistory.getLastAssistantContent();
   }

   public Character getCharacter() {
      return this.character;
   }

   public void updateSystemPrompt() {
      String systemPrompt = Prompts.getAINPCSystemPrompt(this.character, this.mod.getCommandExecutor().allCommands(), this.mod.getOwnerUsername());
      this.conversationHistory.setBaseSystemPrompt(this.withRelationshipSuffix(systemPrompt));
   }

   public void updateSystemPromptWithBlock(String validCommandsBlock) {
      String block = validCommandsBlock != null ? validCommandsBlock : "";
      String systemPrompt = Prompts.getAINPCSystemPromptWithValidCommandsBlock(this.character, block, this.mod.getOwnerUsername());
      this.conversationHistory.setBaseSystemPrompt(this.withRelationshipSuffix(systemPrompt));
   }

   public void updateSystemPromptStatic() {
      String systemPrompt = Prompts.getAINPCSystemPromptNoCommandsBlock(this.character, this.mod.getOwnerUsername());
      this.conversationHistory.setBaseSystemPrompt(this.withRelationshipSuffix(systemPrompt));
   }

   public void saveHistoryNow() {
      this.conversationHistory.saveNow();
   }

   public void reloadHistoryFromDisk() {
      this.conversationHistory.reloadNow();
      this.updateSystemPrompt();
   }

   public String getAdditionalPrompt() {
      return this.additionalPrompt;
   }

   public void updateAdditionalPrompt(String prompt) {
      this.additionalPrompt = cleanAdditionalPrompt(prompt);
   }

   public void saveAdditionalPromptNow() {
      try {
         writeAdditionalPromptFile(this.additionalPromptFile, this.additionalPrompt);
      } catch (Exception e) {
         Debug.logWarning("saveAdditionalPromptNow: failed to write additional prompt for character %s", this.characterId);
      }
   }

   public void reloadAdditionalPromptFromDisk() {
      this.loadAdditionalPromptFromDiskTolerant();
   }

   public static String cleanAdditionalPrompt(String raw) {
      if (raw == null) {
         return "";
      }

      String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
      StringBuilder cleaned = new StringBuilder(Math.min(normalized.length(), 300));

      for (int i = 0; i < normalized.length() && cleaned.length() < 300; i++) {
         char c = normalized.charAt(i);
         if (!java.lang.Character.isISOControl(c) || c == '\n' || c == '\t') {
            cleaned.append(c);
         }
      }

      return cleaned.toString().trim();
   }

   public static String readAdditionalPrompt(MinecraftServer server, UUID ownerUuid, String characterId) {
      if (server != null && ownerUuid != null && characterId != null && !characterId.isBlank()) {
         try {
            Path file = Player2NpcPersistencePaths.additionalPromptFile(server, ownerUuid, characterId);
            return !Files.exists(file) ? "" : cleanAdditionalPrompt(Files.readString(file, StandardCharsets.UTF_8));
         } catch (Exception e) {
            return "";
         }
      } else {
         return "";
      }
   }

   public static void saveAdditionalPrompt(MinecraftServer server, UUID ownerUuid, String characterId, String prompt) throws IOException {
      if (server != null && ownerUuid != null && characterId != null && !characterId.isBlank()) {
         writeAdditionalPromptFile(Player2NpcPersistencePaths.additionalPromptFile(server, ownerUuid, characterId), prompt);
      } else {
         throw new IOException("missing_target");
      }
   }

   private Optional<String> additionalPromptBlock() {
      String prompt = cleanAdditionalPrompt(this.additionalPrompt);
      return prompt.isBlank() ? Optional.empty() : Optional.of(prompt);
   }

   private void loadAdditionalPromptFromDiskTolerant() {
      if (this.additionalPromptFile != null && Files.exists(this.additionalPromptFile)) {
         try {
            this.additionalPrompt = cleanAdditionalPrompt(Files.readString(this.additionalPromptFile, StandardCharsets.UTF_8));
         } catch (Exception e) {
            this.additionalPrompt = "";
            Debug.logWarning("loadAdditionalPrompt: failed to read additional prompt for character %s", this.characterId);
         }
      } else {
         this.additionalPrompt = "";
      }
   }

   private static void writeAdditionalPromptFile(Path file, String prompt) throws IOException {
      if (file != null) {
         if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
         }

         Path tmp = file.resolveSibling("additional_prompt.txt.tmp");
         Files.writeString(tmp, cleanAdditionalPrompt(prompt), StandardCharsets.UTF_8);

         try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      }
   }

   public CompanionMood getCurrentMood() {
      return this.currentMood;
   }

   public void updateMood(CompanionMood newMood) {
      this.currentMood = newMood != null ? newMood : CompanionMood.neutral();
   }

   public void saveMoodNow() {
      if (this.moodFile != null) {
         try {
            if (this.moodFile.getParent() != null) {
               Files.createDirectories(this.moodFile.getParent());
            }

            String json = this.currentMood.toJson().toString();
            Path tmp = this.moodFile.resolveSibling("mood.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);

            try {
               Files.move(tmp, this.moodFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
               Files.move(tmp, this.moodFile, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (Exception e) {
            Debug.logWarning("saveMoodNow: failed to write mood.json for character %s", this.characterId);
         }
      }
   }

   public void reloadMoodFromDisk() {
      this.loadMoodFromDiskTolerant();
   }

   private void loadMoodFromDiskTolerant() {
      if (this.moodFile == null) {
         this.currentMood = CompanionMood.neutral();
      } else if (!Files.exists(this.moodFile)) {
         this.currentMood = CompanionMood.neutral();
      } else {
         try {
            String raw = Files.readString(this.moodFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            this.currentMood = CompanionMood.fromJson(obj);
         } catch (Exception e) {
            this.currentMood = CompanionMood.neutral();
            Debug.logWarning("loadMood: corrupt mood.json for character %s; using neutral", this.characterId);
         }
      }
   }

   private static Path getMoodFileOrNull(PlayerEngineController mod, Path worldRoot, String characterId) {
      if (mod == null || mod.getPlayer() == null) {
         return null;
      }

      if (characterId == null || characterId.isBlank()) {
         return null;
      }

      if (worldRoot == null) {
         return null;
      }

      try {
         UUID ownerUuid = resolveOwnerUuidOrNull(mod);
         if (ownerUuid != null) {
            return Player2NpcPersistencePaths.moodFile(worldRoot, ownerUuid, characterId);
         }

         UUID entityUuid = mod.getPlayer().getUUID();
         return Player2NpcPersistencePaths.persistentDataRoot(worldRoot).resolve(entityUuid.toString()).resolve(characterId).resolve("mood.json");
      } catch (Exception e) {
         return null;
      }
   }

   private static Path getAdditionalPromptFileOrNull(PlayerEngineController mod, Path worldRoot, String characterId) {
      if (mod == null || mod.getPlayer() == null) {
         return null;
      }

      if (characterId == null || characterId.isBlank()) {
         return null;
      }

      if (worldRoot == null) {
         return null;
      }

      try {
         UUID ownerUuid = resolveOwnerUuidOrNull(mod);
         if (ownerUuid != null) {
            return Player2NpcPersistencePaths.additionalPromptFile(worldRoot, ownerUuid, characterId);
         }

         UUID entityUuid = mod.getPlayer().getUUID();
         return Player2NpcPersistencePaths.persistentDataRoot(worldRoot).resolve(entityUuid.toString()).resolve(characterId).resolve("additional_prompt.txt");
      } catch (Exception e) {
         return null;
      }
   }

   public Path getConversationHistoryFile() {
      return this.conversationHistoryFile;
   }

   public String getCharacterId() {
      return this.characterId;
   }

   private static Path resolveWorldRootOrNull(PlayerEngineController mod) {
      if (mod != null && mod.getPlayer() != null) {
         try {
            MinecraftServer server = Objects.requireNonNull(mod.getPlayer().level().getServer(), "server");
            return server.getWorldPath(LevelResource.ROOT);
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Path getConversationHistoryFileOrNull(PlayerEngineController mod, Path worldRoot, String characterId) {
      if (mod == null || mod.getPlayer() == null) {
         return null;
      }

      if (characterId == null || characterId.isBlank()) {
         return null;
      }

      if (worldRoot == null) {
         return null;
      }

      try {
         UUID ownerUuid = resolveOwnerUuidOrNull(mod);
         if (ownerUuid != null) {
            return worldRoot.resolve("player2npc")
               .resolve("persistentdata")
               .resolve("owners")
               .resolve(ownerUuid.toString())
               .resolve(characterId)
               .resolve("conversation.jsonl");
         }

         UUID entityUuid = mod.getPlayer().getUUID();
         return worldRoot.resolve("player2npc").resolve("persistentdata").resolve(entityUuid.toString()).resolve(characterId).resolve("conversation.jsonl");
      } catch (Exception e) {
         return null;
      }
   }

   public UUID progressionOwnerId() {
      return resolveOwnerUuidOrNull(this.mod);
   }

   private static UUID resolveOwnerUuidOrNull(PlayerEngineController mod) {
      try {
         if (mod.getOwner() != null) {
            return mod.getOwner().getUUID();
         }
      } catch (Exception var4) {
      }

      try {
         String ownerName = mod.getOwnerUsername();
         if (ownerName != null && !ownerName.isBlank() && mod.getPlayer() != null) {
            MinecraftServer server = mod.getPlayer().level().getServer();
            if (server != null) {
               return Player2NpcInitiatorUuidResolve.resolve(server, ownerName);
            }
         }
      } catch (Exception var3) {
      }

      return null;
   }

   private static void migrateLegacyHistoryIfPresent(Character character, String characterId, Path worldRoot, Path newHistoryFile, PlayerEngineController mod) {
      if (newHistoryFile != null) {
         try {
            if (Files.exists(newHistoryFile)) {
               return;
            }

            if (newHistoryFile.getParent() != null) {
               Files.createDirectories(newHistoryFile.getParent());
            }

            if (character != null) {
               String characterName = character.name();
               if (characterName != null && !characterName.isBlank()) {
                  Path legacyFile = resolveLegacyGlobalHistoryFile(characterName);
                  if (legacyFile != null) {
                     Files.copy(legacyFile, newHistoryFile);
                  }
               }
            }

            if (Files.exists(newHistoryFile)) {
               return;
            }

            if (worldRoot != null && characterId != null && !characterId.isBlank()) {
               Path intermediate = worldRoot.resolve("player2npc").resolve("persistentdata").resolve(characterId).resolve("conversation.jsonl");
               if (Files.exists(intermediate)) {
                  Files.copy(intermediate, newHistoryFile);
               }
            }

            if (Files.exists(newHistoryFile)) {
               return;
            }

            migrateStrandedOwnerHistoryIfPresent(mod.getOwnerUsername(), characterId, worldRoot, newHistoryFile);
            if (Files.exists(newHistoryFile)) {
               return;
            }

            if (worldRoot != null && characterId != null && !characterId.isBlank() && mod.getPlayer() != null) {
               UUID entityUuid = mod.getPlayer().getUUID();
               Path oldEntityScoped = worldRoot.resolve("player2npc")
                  .resolve("persistentdata")
                  .resolve(entityUuid.toString())
                  .resolve(characterId)
                  .resolve("conversation.jsonl");
               if (Files.exists(oldEntityScoped)) {
                  Files.copy(oldEntityScoped, newHistoryFile);
               }
            }
         } catch (Exception var7) {
         }
      }
   }

   private static void migrateStrandedOwnerHistoryIfPresent(String ownerUsername, String characterId, Path worldRoot, Path newHistoryFile) {
      if (worldRoot != null && newHistoryFile != null) {
         if (characterId != null && !characterId.isBlank()) {
            if (ownerUsername != null && !ownerUsername.isBlank() && !"UNKNOWN OWNER".equals(ownerUsername)) {
               Path ownersRoot = Player2NpcPersistencePaths.ownersRoot(worldRoot);
               if (Files.isDirectory(ownersRoot)) {
                  Path target = newHistoryFile.normalize();
                  Path[] best = new Path[1];

                  try (Stream<Path> owners = Files.list(ownersRoot)) {
                     owners.filter(x$0 -> Files.isDirectory(x$0)).forEach(ownerDir -> {
                        Path candidate = ownerDir.resolve(characterId).resolve("conversation.jsonl");
                        if (!candidate.normalize().equals(target) && Files.isRegularFile(candidate)) {
                           if (historyHeaderNamesOwner(candidate, ownerUsername)) {
                              if (best[0] == null || isNewerFile(candidate, best[0])) {
                                 best[0] = candidate;
                              }
                           }
                        }
                     });
                     if (best[0] != null && !Files.exists(newHistoryFile)) {
                        Files.copy(best[0], newHistoryFile);
                     }
                  } catch (Exception var12) {
                  }
               }
            }
         }
      }
   }

   private static boolean historyHeaderNamesOwner(Path historyFile, String ownerUsername) {
      try (BufferedReader reader = Files.newBufferedReader(historyFile, StandardCharsets.UTF_8)) {
         String firstLine = reader.readLine();
         if (firstLine != null && !firstLine.isBlank()) {
            String lower = firstLine.toLowerCase(Locale.ROOT);
            String owner = ownerUsername.toLowerCase(Locale.ROOT);
            return lower.contains("owner") && lower.contains("username") && lower.contains("\"" + owner + "\"");
         } else {
            return false;
         }
      } catch (Exception ignored) {
         return false;
      }
   }

   private static boolean isNewerFile(Path candidate, Path current) {
      try {
         return Files.getLastModifiedTime(candidate).toMillis() > Files.getLastModifiedTime(current).toMillis();
      } catch (Exception ignored) {
         return false;
      }
   }

   private static Path resolveLegacyGlobalHistoryFile(String characterName) {
      String slug = characterName.replaceAll("\\s+", "_");
      Path configDir = DirUtil.getConfigDir();
      Path duplicateNameFile = configDir.resolve(slug + "_" + slug + ".txt");
      if (Files.exists(duplicateNameFile)) {
         return duplicateNameFile;
      }

      Path canonicalFile = configDir.resolve(slug + "_conversation.txt");
      return Files.exists(canonicalFile) ? canonicalFile : null;
   }
}
