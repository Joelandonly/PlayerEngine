package com.player2.playerengine.player2api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public final class Player2NpcInitiatorUuidResolve {
   private static final Logger LOGGER = LogManager.getLogger();

   private Player2NpcInitiatorUuidResolve() {
   }

   @Nullable
   public static UUID resolve(MinecraftServer server, String username) {
      if (username != null && !username.isBlank()) {
         String trimmed = username.trim();

         for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(trimmed)) {
               return sp.getUUID();
            }
         }

         UUID fromFile = lookupUuidFromServerUsernameCache(server, trimmed);
         if (fromFile != null) {
            return fromFile;
         }

         try {
            GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
               Optional<GameProfile> opt = cache.get(trimmed);
               if (opt.isPresent()) {
                  return opt.get().getId();
               }
            }
         } catch (Exception e) {
            LOGGER.debug("Player2NpcInitiatorUuidResolve profile cache failed for {}", trimmed, e);
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   private static UUID lookupUuidFromServerUsernameCache(MinecraftServer server, String username) {
      Path path = Player2NpcPersistencePaths.serverUsernameUuidCacheFile(server);
      if (!Files.isRegularFile(path)) {
         return null;
      }

      try {
         String raw = Files.readString(path, StandardCharsets.UTF_8);
         JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
         if (!root.has("byLowerName")) {
            return null;
         } else {
            String key = username.toLowerCase(Locale.ROOT);
            JsonObject byLower = root.getAsJsonObject("byLowerName");
            if (!byLower.has(key)) {
               return null;
            } else {
               JsonElement el = byLower.get(key);
               if (el != null && el.isJsonPrimitive()) {
                  String uuidStr = el.getAsString();
                  return uuidStr != null && !uuidStr.isBlank() ? UUID.fromString(uuidStr.trim()) : null;
               } else {
                  return null;
               }
            }
         }
      } catch (Exception e) {
         LOGGER.debug("lookupUuidFromServerUsernameCache failed for {}", username, e);
         return null;
      }
   }
}
