package com.player2.playerengine.modintelligence.ingest;

import com.google.gson.JsonSyntaxException;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.modintelligence.capability.CapabilityGson;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CapabilityStore {
   private final Map<String, CapabilityMap> activeMaps = new LinkedHashMap<>();
   private ModIntelligenceManifest manifest;

   public Map<String, CapabilityMap> getActiveMaps() {
      return this.activeMaps;
   }

   public ModIntelligenceManifest getManifest() {
      return this.manifest;
   }

   public void setManifest(ModIntelligenceManifest manifest) {
      this.manifest = manifest;
   }

   public CapabilityMap get(String entryKey) {
      return this.activeMaps.get(entryKey);
   }

   public static String entryKey(CapabilityMap map) {
      return map.getSubjectKind().name() + "|" + map.getSubjectId();
   }

   public void loadFromDisk() throws IOException {
      this.activeMaps.clear();
      this.manifest = null;
      File manifestFile = ModIntelligencePaths.manifestFile();
      if (manifestFile.exists()) {
         try (FileReader reader = new FileReader(manifestFile, StandardCharsets.UTF_8)) {
            this.manifest = (ModIntelligenceManifest)CapabilityGson.instance().fromJson(reader, ModIntelligenceManifest.class);
         } catch (JsonSyntaxException e) {
            quarantine(manifestFile);
         }
      }

      File mapsFile = ModIntelligencePaths.capabilityMapsFile();
      if (mapsFile.exists()) {
         int badLines = 0;

         String line;
         try (BufferedReader reader = Files.newBufferedReader(mapsFile.toPath(), StandardCharsets.UTF_8)) {
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty()) {
                  try {
                     CapabilityMap map = (CapabilityMap)CapabilityGson.instance().fromJson(line, CapabilityMap.class);
                     if (map != null && map.getSubjectId() != null) {
                        if (map.getSchemaVersion() > 1) {
                           badLines++;
                        } else {
                           this.activeMaps.put(entryKey(map), map);
                        }
                     } else {
                        badLines++;
                     }
                  } catch (Exception e) {
                     badLines++;
                  }
               }
            }
         }

         if (badLines > 100) {
            quarantine(mapsFile);
            this.activeMaps.clear();
         }
      }
   }

   private static void quarantine(File file) throws IOException {
      String name = file.getName() + ".corrupt." + System.currentTimeMillis();
      File dest = new File(file.getParentFile(), name);
      Files.move(file.toPath(), dest.toPath());
      PlayerEngine.LOGGER.warn("ModIntelligence: quarantined corrupt file {}", dest.getName());
   }
}
