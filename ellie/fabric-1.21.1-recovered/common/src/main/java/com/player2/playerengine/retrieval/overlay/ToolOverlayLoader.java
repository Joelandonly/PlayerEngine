package com.player2.playerengine.retrieval.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.player2.playerengine.PlayerEnginePaths;
import com.player2.playerengine.player2api.Player2NpcPersistencePaths;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ToolOverlayLoader {
   private static final Logger LOGGER = LogManager.getLogger(ToolOverlayLoader.class);
   private static final Gson GSON = new GsonBuilder().create();

   private ToolOverlayLoader() {
   }

   public static Optional<ToolOverlay> loadGlobal() {
      Path file = PlayerEnginePaths.userFile("tool_overrides.json");
      return loadFromFile(file, "global");
   }

   public static Optional<ToolOverlay> loadPerOwner(MinecraftServer server, UUID ownerUuid) {
      Path file = Player2NpcPersistencePaths.toolOverridesFile(server, ownerUuid);
      return loadFromFile(file, "per-owner-manual[" + ownerUuid + "]");
   }

   public static Optional<ToolOverlay> loadLearnedPerOwner(MinecraftServer server, UUID ownerUuid) {
      Path file = Player2NpcPersistencePaths.toolLearnedOverridesFile(server, ownerUuid);
      return loadFromFile(file, "per-owner-learned[" + ownerUuid + "]");
   }

   public static Optional<ToolOverlay> loadLearnedFromFile(Path file) {
      return loadFromFile(file, "learned");
   }

   private static Optional<ToolOverlay> loadFromFile(Path file, String label) {
      if (!Files.exists(file)) {
         LOGGER.debug("RAG overlay: no {} overlay file at {}", label, file);
         return Optional.empty();
      }

      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         ToolOverlay overlay = (ToolOverlay)GSON.fromJson(reader, ToolOverlay.class);
         if (overlay == null) {
            LOGGER.warn("RAG overlay: {} file at {} parsed as null — skipping.", label, file);
            return Optional.empty();
         } else if (overlay.schemaVersion != 1) {
            LOGGER.warn("RAG overlay: {} file at {} has unsupported schemaVersion={} (expected {}); file rejected.", label, file, overlay.schemaVersion, 1);
            return Optional.empty();
         } else {
            LOGGER.info("RAG overlay: loaded {} overlay from {} ({} tool entries).", label, file, overlay.safeTools().size());
            return Optional.of(overlay);
         }
      } catch (JsonSyntaxException e) {
         LOGGER.warn("RAG overlay: {} file at {} has invalid JSON — skipping: {}", label, file, e.getMessage());
         return Optional.empty();
      } catch (IOException e) {
         LOGGER.warn("RAG overlay: could not read {} file at {} — skipping: {}", label, file, e.getMessage());
         return Optional.empty();
      }
   }
}
