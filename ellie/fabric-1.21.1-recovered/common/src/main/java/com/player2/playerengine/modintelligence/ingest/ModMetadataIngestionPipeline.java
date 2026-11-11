package com.player2.playerengine.modintelligence.ingest;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.capability.CapabilityStatus;
import com.player2.playerengine.modintelligence.capability.CapabilitySubjectKind;
import com.player2.playerengine.modintelligence.enrich.CapabilityEnrichmentQueue;
import com.player2.playerengine.modintelligence.inspect.InspectionContext;
import com.player2.playerengine.modintelligence.inspect.ModBlockInspector;
import com.player2.playerengine.modintelligence.inspect.ModEntityTypeInspector;
import com.player2.playerengine.modintelligence.inspect.ModItemInspector;
import com.player2.playerengine.modintelligence.inspect.StablePropertyCollector;
import com.player2.playerengine.modintelligence.inspect.TagCollector;
import com.player2.playerengine.modintelligence.query.CapabilityIndex;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import dev.architectury.platform.Platform;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

public final class ModMetadataIngestionPipeline {
   private static final Logger LOGGER = PlayerEngine.LOGGER;
   private final MinecraftServer server;
   private final String minecraftVersion;
   private final String loader;
   private final Provider registryAccess;

   public ModMetadataIngestionPipeline(MinecraftServer server) {
      this.server = server;
      this.minecraftVersion = Platform.getMinecraftVersion();
      this.loader = detectLoader();
      this.registryAccess = server.registryAccess();
   }

   public ModMetadataIngestionPipeline.IngestSummary run(boolean forceRebuild) throws IOException {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (!cfg.isModIntelligenceEnabled()) {
         return new ModMetadataIngestionPipeline.IngestSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
      }

      ModIntelligencePaths.rootDir().mkdirs();
      if (!this.acquireLock()) {
         LOGGER.info("ModIntelligence: ingestion already in progress, skipping");
         return new ModMetadataIngestionPipeline.IngestSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
      }

      try {
         this.cleanupStaleStaging();
         CapabilityStore store = new CapabilityStore();
         store.loadFromDisk();
         Map<String, ModFingerprint> modFingerprints = this.buildModFingerprints();
         Map<String, EntryFingerprint> previousEntries = store.getManifest() == null ? Map.of() : new HashMap<>(store.getManifest().getEntries());
         InspectionContext ctx = new InspectionContext(this.minecraftVersion, this.loader, 1, this.registryAccess);
         ModItemInspector itemInspector = new ModItemInspector();
         ModBlockInspector blockInspector = new ModBlockInspector();
         ModEntityTypeInspector entityInspector = new ModEntityTypeInspector();
         Map<String, CapabilityMap> nextMaps = new LinkedHashMap<>();
         Map<String, EntryFingerprint> nextEntries = new LinkedHashMap<>();
         List<String> enrichmentQueue = new ArrayList<>();
         List<CapabilityMap> tombstones = new ArrayList<>();
         int unchanged = 0;
         int changed = 0;
         int newly = 0;
         int deleted = 0;
         int inspected = 0;
         int failed = 0;
         Set<String> currentKeys = new HashSet<>();
         List<ModMetadataIngestionPipeline.RegistryEntry> registryEntries = this.collectRegistryEntries();
         int inspectBudget = cfg.getModIntelligenceMaxInspectEntriesPerLaunch();
         if (inspectBudget <= 0) {
            inspectBudget = Integer.MAX_VALUE;
         }

         for (ModMetadataIngestionPipeline.RegistryEntry re : registryEntries) {
            currentKeys.add(re.entryKey());
            String modHash = modFingerprints.containsKey(re.sourceModId()) ? modFingerprints.get(re.sourceModId()).getSourceHash() : "unknown";
            List<String> tags = re.tags();
            Map<String, String> props = re.stableProperties();
            Map<String, String> nbt = re.sanitizedNbt();
            String detHash = CapabilityFingerprintHasher.deterministicInputHash(
               re.kind(), re.subjectId(), re.sourceModId(), modHash, this.minecraftVersion, this.loader, tags, props, nbt
            );
            EntryFingerprint prev = previousEntries.get(re.entryKey());
            CapabilityMap existing = store.get(re.entryKey());
            boolean needInspect = forceRebuild
               || prev == null
               || prev.getDeterministicInputHash() == null
               || !detHash.equals(prev.getDeterministicInputHash())
               || store.getManifest() == null
               || store.getManifest().getInspectorVersion() != 1
               || store.getManifest().getHeuristicVersion() != 1
               || store.getManifest().getSchemaVersion() != 1;
            CapabilityMap map;
            if (needInspect && inspected < inspectBudget) {
               map = this.inspectEntry(ctx, re, itemInspector, blockInspector, entityInspector);
               inspected++;
               if (prev == null) {
                  newly++;
               } else {
                  changed++;
               }
            } else if (existing != null && !needInspect) {
               map = existing;
               unchanged++;
            } else if (existing != null) {
               map = existing;
               unchanged++;
            } else {
               map = this.inspectEntry(ctx, re, itemInspector, blockInspector, entityInspector);
               inspected++;
               newly++;
            }

            String enrHash = CapabilityFingerprintHasher.enrichmentInputHash(detHash, map);
            String fp = CapabilityFingerprintHasher.entryFingerprint(detHash, enrHash);
            map.setEntryFingerprint(fp);
            EntryFingerprint ef = new EntryFingerprint();
            ef.setSubjectKind(re.kind().name());
            ef.setSubjectId(re.subjectId());
            ef.setSourceModId(re.sourceModId());
            ef.setFingerprint(fp);
            ef.setDeterministicInputHash(detHash);
            ef.setEnrichmentInputHash(enrHash);
            ef.setCapabilityStatus(map.getStatus().name());
            ef.setEnriched(map.getEnrichment() != null);
            nextMaps.put(re.entryKey(), map);
            nextEntries.put(re.entryKey(), ef);
            if (cfg.isModIntelligenceEnrichmentEnabled() && this.shouldQueueEnrichment(map, prev, ef, detHash, needInspect)) {
               enrichmentQueue.add(re.entryKey());
            }
         }

         for (Entry<String, EntryFingerprint> e : previousEntries.entrySet()) {
            if (!currentKeys.contains(e.getKey())) {
               deleted++;
               CapabilityMap tomb = store.get(e.getKey());
               if (tomb != null) {
                  tomb.setStatus(CapabilityStatus.TOMBSTONED);
                  tombstones.add(tomb);
               }
            }
         }

         String packFp = CapabilityFingerprintHasher.packFingerprint(this.minecraftVersion, this.loader, modFingerprints);
         ModIntelligenceManifest manifest = new ModIntelligenceManifest();
         manifest.setSchemaVersion(1);
         manifest.setInspectorVersion(1);
         manifest.setHeuristicVersion(1);
         manifest.setMinecraftVersion(this.minecraftVersion);
         manifest.setLoader(this.loader);
         manifest.setPackFingerprint(packFp);
         manifest.setMods(modFingerprints);
         manifest.setEntries(nextEntries);
         manifest.setUpdatedAtEpochMillis(System.currentTimeMillis());
         String runId = UUID.randomUUID().toString().substring(0, 8);
         File staging = ModIntelligencePaths.stagingDir(runId);
         staging.mkdirs();
         CapabilityStoreWriter.writeJsonl(new File(staging, "capability_maps.jsonl"), nextMaps.values());
         if (!tombstones.isEmpty()) {
            CapabilityStoreWriter.writeJsonl(new File(staging, "deleted_entries.jsonl"), tombstones);
         }

         CapabilityStoreWriter.writeJson(new File(staging, "manifest.json"), manifest);
         CapabilityStoreWriter.promoteStaging(runId, manifest);
         CapabilityIndex.buildAndPersist(nextMaps.values(), packFp);
         LinkedHashMap<String, CapabilityMap> queueMaps = new LinkedHashMap<>();

         for (CapabilityMap pending : CapabilityEnrichmentQueue.loadQueued()) {
            queueMaps.put(CapabilityStore.entryKey(pending), pending);
         }

         for (String key : enrichmentQueue) {
            CapabilityMap m = nextMaps.get(key);
            if (m != null) {
               queueMaps.put(key, m);
            }
         }

         if (!queueMaps.isEmpty()) {
            CapabilityStoreWriter.writeJsonl(ModIntelligencePaths.enrichmentQueueFile(), queueMaps.values());
         }

         LOGGER.info(
            "ModIntelligence: mods={} entries={} unchanged={} changed={} new={} deleted={} inspected={} queuedEnrichment={} failed={}",
            modFingerprints.size(),
            nextMaps.size(),
            unchanged,
            changed,
            newly,
            deleted,
            inspected,
            enrichmentQueue.size(),
            failed
         );
         return new ModMetadataIngestionPipeline.IngestSummary(
            modFingerprints.size(), nextMaps.size(), unchanged, changed, newly, deleted, inspected, enrichmentQueue.size(), failed
         );
      } finally {
         this.releaseLock();
      }
   }

   private boolean shouldQueueEnrichment(CapabilityMap map, EntryFingerprint prev, EntryFingerprint next, String detHash, boolean deterministicChanged) {
      if (map.getStatus() == CapabilityStatus.FAILED || map.getStatus() == CapabilityStatus.TOMBSTONED) {
         return false;
      } else if (prev != null
         && prev.isEnriched()
         && map.getEnrichment() != null
         && detHash.equals(prev.getDeterministicInputHash())
         && prev.getEnrichmentInputHash() != null
         && prev.getEnrichmentInputHash().equals(next.getEnrichmentInputHash())) {
         return false;
      } else {
         boolean enrichmentPromptChanged = prev != null
            && prev.getEnrichmentInputHash() != null
            && !prev.getEnrichmentInputHash().equals(next.getEnrichmentInputHash())
            && detHash.equals(prev.getDeterministicInputHash());
         if (prev == null || deterministicChanged || enrichmentPromptChanged) {
            return meetsEnrichmentCriteria(map, true);
         } else {
            return prev != null && !prev.isEnriched() && map.getEnrichment() == null ? meetsEnrichmentCriteria(map, false) : false;
         }
      }
   }

   private static boolean meetsEnrichmentCriteria(CapabilityMap map, boolean afterDataChange) {
      if (map.getStatus() == CapabilityStatus.FAILED || map.getStatus() == CapabilityStatus.TOMBSTONED) {
         return false;
      }

      if (afterDataChange) {
         if ("minecraft".equals(map.getSourceModId())) {
            return false;
         } else {
            return map.getStatus() != CapabilityStatus.PARTIAL && map.getStatus() != CapabilityStatus.UNKNOWN ? map.getEnrichment() == null : true;
         }
      } else {
         return "minecraft".equals(map.getSourceModId()) ? false : map.getStatus() == CapabilityStatus.PARTIAL || map.getStatus() == CapabilityStatus.UNKNOWN;
      }
   }

   private CapabilityMap inspectEntry(
      InspectionContext ctx,
      ModMetadataIngestionPipeline.RegistryEntry re,
      ModItemInspector itemInspector,
      ModBlockInspector blockInspector,
      ModEntityTypeInspector entityInspector
   ) {
      ResourceLocation id = ResourceLocation.parse(re.subjectId());

      return switch (re.kind()) {
         case ITEM -> {
            Item item = (Item)BuiltInRegistries.ITEM.get(id);
            yield itemInspector.inspect(ctx, id, item);
         }
         case BLOCK -> {
            Block block = (Block)BuiltInRegistries.BLOCK.get(id);
            yield blockInspector.inspect(ctx, id, block);
         }
         case ENTITY_TYPE -> {
            EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.get(id);
            yield entityInspector.inspect(ctx, id, type);
         }
      };
   }

   private static String detectLoader() {
      if (Platform.isFabric()) {
         return "fabric";
      } else if (Platform.isNeoForge()) {
         return "neoforge";
      } else {
         return Platform.isForge() ? "forge" : "unknown";
      }
   }

   private Map<String, ModFingerprint> buildModFingerprints() {
      Map<String, ModFingerprint> out = new LinkedHashMap<>();

      for (LoadedModMetadata m : LoadedModMetadataProvider.loadedMods()) {
         ModFingerprint fp = new ModFingerprint();
         fp.setModId(m.getModId());
         fp.setDisplayName(m.getDisplayName());
         fp.setVersion(m.getVersion());
         fp.setSourceHash(m.getSourceHash());
         fp.setMetadataHash(m.getSourceHash());
         fp.setNamespaces(m.getNamespaces());
         out.put(m.getModId(), fp);
      }

      return out;
   }

   private List<ModMetadataIngestionPipeline.RegistryEntry> collectRegistryEntries() {
      List<ModMetadataIngestionPipeline.RegistryEntry> entries = new ArrayList<>();

      for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
         entries.add(ModMetadataIngestionPipeline.RegistryEntry.item(id));
      }

      for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
         entries.add(ModMetadataIngestionPipeline.RegistryEntry.block(id, this.registryAccess));
      }

      for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
         entries.add(ModMetadataIngestionPipeline.RegistryEntry.entity(id));
      }

      return entries;
   }

   private boolean acquireLock() throws IOException {
      File lock = ModIntelligencePaths.ingestLockFile();
      if (lock.exists()) {
         String pid = Files.readString(lock.toPath(), StandardCharsets.UTF_8).trim();
         if (pid.equals(String.valueOf(ProcessHandle.current().pid()))) {
            return false;
         }

         LOGGER.warn("ModIntelligence: replacing stale ingest lock (was pid {})", pid);
      }

      Files.writeString(lock.toPath(), String.valueOf(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
      return true;
   }

   private void releaseLock() {
      File lock = ModIntelligencePaths.ingestLockFile();
      if (lock.exists()) {
         lock.delete();
      }
   }

   private void cleanupStaleStaging() {
      File root = ModIntelligencePaths.rootDir();
      File stagingParent = new File(root, ".staging");
      if (stagingParent.exists()) {
         File[] runs = stagingParent.listFiles();
         if (runs != null) {
            for (File run : runs) {
               CapabilityStoreWriter.deleteRecursive(run);
            }
         }
      }
   }

   public record IngestSummary(int mods, int entries, int unchanged, int changed, int newly, int deleted, int inspected, int queuedEnrichment, int failed) {
   }

   private record RegistryEntry(
      CapabilitySubjectKind kind,
      String subjectId,
      String sourceModId,
      List<String> tags,
      Map<String, String> stableProperties,
      Map<String, String> sanitizedNbt
   ) {
      String entryKey() {
         return this.kind.name() + "|" + this.subjectId;
      }

      static ModMetadataIngestionPipeline.RegistryEntry item(ResourceLocation id) {
         List<String> tags = TagCollector.collectTagIds(BuiltInRegistries.ITEM, id);
         Item item = (Item)BuiltInRegistries.ITEM.get(id);
         return new ModMetadataIngestionPipeline.RegistryEntry(
            CapabilitySubjectKind.ITEM, id.toString(), id.getNamespace(), tags, StablePropertyCollector.forItem(item), Map.of()
         );
      }

      static ModMetadataIngestionPipeline.RegistryEntry block(ResourceLocation id, Provider access) {
         List<String> tags = TagCollector.collectTagIds(BuiltInRegistries.BLOCK, id);
         Block block = (Block)BuiltInRegistries.BLOCK.get(id);
         return new ModMetadataIngestionPipeline.RegistryEntry(
            CapabilitySubjectKind.BLOCK, id.toString(), id.getNamespace(), tags, StablePropertyCollector.forBlock(block), Map.of()
         );
      }

      static ModMetadataIngestionPipeline.RegistryEntry entity(ResourceLocation id) {
         List<String> tags = TagCollector.collectTagIds(BuiltInRegistries.ENTITY_TYPE, id);
         EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.get(id);
         return new ModMetadataIngestionPipeline.RegistryEntry(
            CapabilitySubjectKind.ENTITY_TYPE, id.toString(), id.getNamespace(), tags, StablePropertyCollector.forEntityType(type), Map.of()
         );
      }
   }
}
