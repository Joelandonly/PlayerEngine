package com.player2.playerengine.player2api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.player2.playerengine.player2api.config.PlayerBudgetConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PlayerBudgetConfigHolder {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String FILE_NAME = "player-budget.json";
   private static final ConcurrentHashMap<UUID, PlayerBudgetConfig> CACHE = new ConcurrentHashMap<>();

   private PlayerBudgetConfigHolder() {
   }

   public static PlayerBudgetConfig load(MinecraftServer server, UUID playerUuid) {
      return playerUuid == null ? new PlayerBudgetConfig() : CACHE.computeIfAbsent(playerUuid, uuid -> loadFromDisk(server, uuid));
   }

   public static void save(MinecraftServer server, UUID playerUuid, PlayerBudgetConfig config) {
      if (playerUuid != null && config != null) {
         Path path = Player2NpcPersistencePaths.playerBudgetFile(server, playerUuid);

         try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
            CACHE.put(playerUuid, config);
            LOGGER.debug("PlayerBudgetConfigHolder: saved budget for player {}", playerUuid);
         } catch (IOException e) {
            LOGGER.warn("PlayerBudgetConfigHolder: failed to save budget for {}: {}", playerUuid, e.getMessage());
         }
      }
   }

   public static void invalidate(UUID playerUuid) {
      if (playerUuid != null) {
         CACHE.remove(playerUuid);
      }
   }

   private static PlayerBudgetConfig loadFromDisk(MinecraftServer server, UUID playerUuid) {
      Path path = Player2NpcPersistencePaths.playerBudgetFile(server, playerUuid);
      if (!Files.isRegularFile(path)) {
         return new PlayerBudgetConfig();
      }

      try {
         String raw = Files.readString(path, StandardCharsets.UTF_8);
         PlayerBudgetConfig cfg = (PlayerBudgetConfig)GSON.fromJson(raw, PlayerBudgetConfig.class);
         return cfg != null ? cfg : new PlayerBudgetConfig();
      } catch (Exception e) {
         LOGGER.warn("PlayerBudgetConfigHolder: failed to load budget for {}, using defaults: {}", playerUuid, e.getMessage());
         return new PlayerBudgetConfig();
      }
   }
}
