package com.player2.playerengine.player2api;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

public final class Player2NpcPersistencePaths {
   public static final String BOT_BLACKLIST_FILE_NAME = "botblacklist.json";
   public static final String USER_BLACKLIST_FILE_NAME = "userblacklist.json";
   public static final String BOT_WHITELIST_FILE_NAME = "botwhitelist.json";
   public static final String USER_WHITELIST_FILE_NAME = "userwhitelist.json";
   public static final String USER_SETTINGS_FILE_NAME = "user-settings.json";
   public static final String PLAYER_BUDGET_FILE_NAME = "player-budget.json";
   public static final String SERVER_USERNAME_UUID_CACHE_FILE_NAME = "server_username_uuid_cache.json";
   public static final String TOOL_OVERRIDES_FILE_NAME = "tool_overrides.json";
   public static final String TOOL_LEARNED_OVERRIDES_FILE_NAME = "tool_learned_overrides.json";
   public static final String LEARN_AUDIT_FILE_NAME = "learn_audit.jsonl";
   public static final String ELLIEGPS_DIR_NAME = "elliegps";
   public static final String MEMORY_DIR_NAME = "memory";
   public static final String MEMORY_GRAPH_FILE_NAME = "graph.json";
   public static final String MOOD_FILE_NAME = "mood.json";
   public static final String ADDITIONAL_PROMPT_FILE_NAME = "additional_prompt.txt";
   public static final String DEFERRED_JOBS_DIR_NAME = "deferredjobs";
   public static final String PLAYER_PLACED_DIR_NAME = "playerplaced";
   public static final String PLAYER_PLACED_FILE_NAME = "blocks.json";

   private Player2NpcPersistencePaths() {
   }

   public static Path persistentDataRoot(Path worldRoot) {
      return worldRoot.resolve("player2npc").resolve("persistentdata");
   }

   public static Path ownersRoot(Path worldRoot) {
      return persistentDataRoot(worldRoot).resolve("owners");
   }

   public static Path botBlacklistFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("botblacklist.json");
   }

   public static Path userBlacklistFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("userblacklist.json");
   }

   public static Path botWhitelistFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("botwhitelist.json");
   }

   public static Path userWhitelistFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("userwhitelist.json");
   }

   public static Path userSettingsFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("user-settings.json");
   }

   public static Path playerBudgetFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("player-budget.json");
   }

   public static Path serverUsernameUuidCacheFile(Path worldRoot) {
      return persistentDataRoot(worldRoot).resolve("server_username_uuid_cache.json");
   }

   public static Path toolOverridesFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("tool_overrides.json");
   }

   public static Path botBlacklistFile(MinecraftServer server, UUID ownerUuid) {
      return botBlacklistFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path userBlacklistFile(MinecraftServer server, UUID ownerUuid) {
      return userBlacklistFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path botWhitelistFile(MinecraftServer server, UUID ownerUuid) {
      return botWhitelistFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path userWhitelistFile(MinecraftServer server, UUID ownerUuid) {
      return userWhitelistFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path userSettingsFile(MinecraftServer server, UUID ownerUuid) {
      return userSettingsFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path playerBudgetFile(MinecraftServer server, UUID ownerUuid) {
      return playerBudgetFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path serverUsernameUuidCacheFile(MinecraftServer server) {
      return serverUsernameUuidCacheFile(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path toolOverridesFile(MinecraftServer server, UUID ownerUuid) {
      return toolOverridesFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path toolLearnedOverridesFile(Path worldRoot, UUID ownerUuid) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve("tool_learned_overrides.json");
   }

   public static Path toolLearnedOverridesFile(MinecraftServer server, UUID ownerUuid) {
      return toolLearnedOverridesFile(server.getWorldPath(LevelResource.ROOT), ownerUuid);
   }

   public static Path learnAuditFile(Path worldRoot) {
      return persistentDataRoot(worldRoot).resolve("learn_audit.jsonl");
   }

   public static Path learnAuditFile(MinecraftServer server) {
      return learnAuditFile(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path ellieGpsRoot(Path worldRoot) {
      return persistentDataRoot(worldRoot).resolve("elliegps");
   }

   public static Path ellieGpsRoot(MinecraftServer server) {
      return ellieGpsRoot(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path ellieGpsWaypointsFile(Path worldRoot) {
      return ellieGpsRoot(worldRoot).resolve("waypoints.json");
   }

   public static Path ellieGpsWaypointsFile(MinecraftServer server) {
      return ellieGpsWaypointsFile(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path ellieGpsIndexDir(Path worldRoot) {
      return ellieGpsRoot(worldRoot).resolve("index");
   }

   public static Path ellieGpsIndexDir(MinecraftServer server) {
      return ellieGpsIndexDir(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path memoryDir(Path worldRoot, UUID ownerUuid, String companionId) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve(companionId).resolve("memory");
   }

   public static Path memoryDir(MinecraftServer server, UUID ownerUuid, String companionId) {
      return memoryDir(server.getWorldPath(LevelResource.ROOT), ownerUuid, companionId);
   }

   public static Path memoryDirEntityFallback(Path worldRoot, UUID entityUuid, String companionId) {
      return persistentDataRoot(worldRoot).resolve(entityUuid.toString()).resolve(companionId).resolve("memory");
   }

   public static Path memoryDirEntityFallback(MinecraftServer server, UUID entityUuid, String companionId) {
      return memoryDirEntityFallback(server.getWorldPath(LevelResource.ROOT), entityUuid, companionId);
   }

   public static Path memoryGraphFile(Path worldRoot, UUID ownerUuid, String companionId) {
      return memoryDir(worldRoot, ownerUuid, companionId).resolve("graph.json");
   }

   public static Path memoryGraphFileEntityFallback(Path worldRoot, UUID entityUuid, String companionId) {
      return memoryDirEntityFallback(worldRoot, entityUuid, companionId).resolve("graph.json");
   }

   public static Path moodFile(Path worldRoot, UUID ownerUuid, String characterId) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve(characterId).resolve("mood.json");
   }

   public static Path moodFile(MinecraftServer server, UUID ownerUuid, String characterId) {
      return moodFile(server.getWorldPath(LevelResource.ROOT), ownerUuid, characterId);
   }

   public static Path additionalPromptFile(Path worldRoot, UUID ownerUuid, String characterId) {
      return ownersRoot(worldRoot).resolve(ownerUuid.toString()).resolve(characterId).resolve("additional_prompt.txt");
   }

   public static Path additionalPromptFile(MinecraftServer server, UUID ownerUuid, String characterId) {
      return additionalPromptFile(server.getWorldPath(LevelResource.ROOT), ownerUuid, characterId);
   }

   public static Path deferredJobsRoot(Path worldRoot) {
      return persistentDataRoot(worldRoot).resolve("deferredjobs");
   }

   public static Path deferredJobsRoot(MinecraftServer server) {
      return deferredJobsRoot(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path deferredJobsFile(Path worldRoot) {
      return deferredJobsRoot(worldRoot).resolve("jobs.json");
   }

   public static Path deferredJobsFile(MinecraftServer server) {
      return deferredJobsFile(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path playerPlacedRoot(Path worldRoot) {
      return persistentDataRoot(worldRoot).resolve("playerplaced");
   }

   public static Path playerPlacedRoot(MinecraftServer server) {
      return playerPlacedRoot(server.getWorldPath(LevelResource.ROOT));
   }

   public static Path playerPlacedFile(Path worldRoot, String dimensionId) {
      return playerPlacedRoot(worldRoot).resolve(sanitizeDimensionId(dimensionId)).resolve("blocks.json");
   }

   public static Path playerPlacedFile(MinecraftServer server, String dimensionId) {
      return playerPlacedFile(server.getWorldPath(LevelResource.ROOT), dimensionId);
   }

   private static String sanitizeDimensionId(String dimensionId) {
      return dimensionId.replaceAll("[^a-zA-Z0-9._-]", "_");
   }
}
