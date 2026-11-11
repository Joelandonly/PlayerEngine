package com.player2.playerengine.retrieval;

import com.player2.playerengine.PlayerEnginePaths;
import com.player2.playerengine.retrieval.overlay.ToolOverlay;
import com.player2.playerengine.retrieval.overlay.ToolOverlayLoader;
import com.player2.playerengine.retrieval.overlay.ToolOverlayMerger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RagIndex {
   private static final Logger LOGGER = LogManager.getLogger(RagIndex.class);
   private static volatile ToolRetriever globalInstance;
   private static final Map<UUID, ToolRetriever> perOwnerCache = new ConcurrentHashMap<>();

   private RagIndex() {
   }

   public static synchronized void initialize() {
      if (globalInstance == null) {
         globalInstance = buildGlobal();
      }
   }

   public static ToolRetriever getGlobal() {
      return globalInstance;
   }

   public static ToolRetriever getForOwner(MinecraftServer server, UUID ownerUuid) {
      return ownerUuid != null && server != null ? perOwnerCache.computeIfAbsent(ownerUuid, uuid -> {
         try {
            return buildForOwner(server, uuid);
         } catch (Exception e) {
            LOGGER.warn("RAG: per-owner build failed for {} — falling back to global: {}", uuid, e.getMessage());
            return globalInstance;
         }
      }) : getGlobal();
   }

   @Deprecated
   public static ToolRetriever get() {
      return globalInstance;
   }

   public static synchronized void reloadGlobal() {
      LOGGER.info("RAG: reloading global retriever.");
      globalInstance = buildGlobal();
   }

   public static void invalidateOwnerCache() {
      int count = perOwnerCache.size();
      perOwnerCache.clear();
      LOGGER.info("RAG: evicted {} per-owner retriever(s) from cache.", count);
   }

   public static void invalidateOwner(UUID ownerUuid) {
      if (ownerUuid != null && perOwnerCache.remove(ownerUuid) != null) {
         LOGGER.debug("RAG: evicted per-owner retriever for {}.", ownerUuid);
      }
   }

   public static void reloadAll(MinecraftServer server) {
      reloadGlobal();
      invalidateOwnerCache();
      LOGGER.info("RAG: full reload complete. Global token: {}...", globalInstance != null ? tokenShort() : "null");
   }

   private static ToolRetriever buildGlobal() {
      try {
         List<ToolOverlay> overlays = new ArrayList<>();
         ToolOverlayLoader.loadGlobal().ifPresent(overlays::add);
         Collection<ToolDocument> merged = ToolOverlayMerger.merge(SeedToolMetadata.all(), overlays);
         ToolMetadataRegistry registry = ToolMetadataRegistry.create(merged);
         Path indexDir = PlayerEnginePaths.ragIndexRoot();
         ToolRetriever retriever = ToolRetriever.loadOrRebuild(registry, indexDir);
         LOGGER.info("RAG: global retriever ready ({} documents, token {}...).", retriever.documentCount(), registry.getVersionToken().substring(0, 8));
         return retriever;
      } catch (Exception e) {
         LOGGER.error("RAG: global build failed — retrieval unavailable: {}", e.getMessage(), e);
         return null;
      }
   }

   private static ToolRetriever buildForOwner(MinecraftServer server, UUID ownerUuid) {
      List<ToolOverlay> overlays = new ArrayList<>();
      ToolOverlayLoader.loadGlobal().ifPresent(overlays::add);
      ToolOverlayLoader.loadPerOwner(server, ownerUuid).ifPresent(overlays::add);
      ToolOverlayLoader.loadLearnedPerOwner(server, ownerUuid).ifPresent(overlays::add);
      Collection<ToolDocument> merged = ToolOverlayMerger.merge(SeedToolMetadata.all(), overlays);
      ToolMetadataRegistry registry = ToolMetadataRegistry.create(merged);
      ToolRetriever retriever = ToolRetriever.buildInMemory(registry);
      LOGGER.debug("RAG: per-owner retriever built for {} ({} documents).", ownerUuid, retriever.documentCount());
      return retriever;
   }

   private static String tokenShort() {
      ToolRetriever g = globalInstance;
      return g == null ? "null" : g.documentCount() + "docs";
   }
}
