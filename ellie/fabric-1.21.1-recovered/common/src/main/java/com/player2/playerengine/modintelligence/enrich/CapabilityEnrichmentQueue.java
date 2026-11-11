package com.player2.playerengine.modintelligence.enrich;

import com.player2.playerengine.modintelligence.capability.CapabilityGson;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.ingest.CapabilityStoreWriter;
import com.player2.playerengine.modintelligence.ingest.ModIntelligencePaths;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class CapabilityEnrichmentQueue {
   private CapabilityEnrichmentQueue() {
   }

   public static List<CapabilityMap> loadQueued() {
      try {
         return loadQueuedChecked();
      } catch (IOException ignored) {
         return new ArrayList<>();
      }
   }

   public static List<CapabilityMap> loadQueuedChecked() throws IOException {
      List<CapabilityMap> out = new ArrayList<>();
      if (!ModIntelligencePaths.enrichmentQueueFile().exists()) {
         return out;
      }

      try (BufferedReader reader = Files.newBufferedReader(ModIntelligencePaths.enrichmentQueueFile().toPath(), StandardCharsets.UTF_8)) {
         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
               CapabilityMap map = (CapabilityMap)CapabilityGson.instance().fromJson(line, CapabilityMap.class);
               if (map != null) {
                  out.add(map);
               }
            }
         }

         return out;
      } catch (RuntimeException e) {
         throw new IOException("Invalid enrichment queue data", e);
      }
   }

   public static int countQueuedLines() {
      return loadQueued().size();
   }

   public static int countQueuedLinesChecked() throws IOException {
      return loadQueuedChecked().size();
   }

   public static int countFailureLogLines() {
      if (!ModIntelligencePaths.enrichmentFailuresFile().exists()) {
         return 0;
      }

      int count = 0;

      String line;
      try (BufferedReader reader = Files.newBufferedReader(ModIntelligencePaths.enrichmentFailuresFile().toPath(), StandardCharsets.UTF_8)) {
         while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
               count++;
            }
         }
      } catch (Exception var6) {
      }

      return count;
   }

   public static void saveQueued(List<CapabilityMap> maps) throws IOException {
      if (maps != null && !maps.isEmpty()) {
         CapabilityStoreWriter.writeJsonl(ModIntelligencePaths.enrichmentQueueFile(), maps);
      } else {
         Files.deleteIfExists(ModIntelligencePaths.enrichmentQueueFile().toPath());
      }
   }
}
