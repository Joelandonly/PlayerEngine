package com.player2.playerengine.modintelligence.enrich;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.player2.playerengine.modintelligence.capability.CapabilityDecl;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.capability.CapabilitySchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

public final class CapabilityEnrichmentPrompt {
   private static final Gson GSON = new Gson();
   private static final int MAX_TAGS = 64;
   private static final int MAX_PROPS = 40;
   private static final int MAX_CAPS = 20;

   private CapabilityEnrichmentPrompt() {
   }

   public static String buildUserJson(CapabilityMap map) {
      JsonObject o = new JsonObject();
      o.addProperty("schemaVersion", 1);
      o.addProperty("promptVersion", "b4-v2");
      o.addProperty("subjectKind", map.getSubjectKind().name());
      o.addProperty("subjectId", map.getSubjectId());
      o.addProperty("sourceModId", map.getSourceModId());
      o.addProperty("minecraftVersion", map.getMinecraftVersion());
      o.addProperty("loader", map.getLoader());
      List<String> tags = new ArrayList<>(map.getTags());
      Collections.sort(tags);
      if (tags.size() > 64) {
         tags = tags.subList(0, 64);
      }

      o.add("tags", GSON.toJsonTree(tags));
      JsonObject props = new JsonObject();
      int propCount = 0;

      for (Entry<String, String> e : map.getStableProperties().entrySet()) {
         if (propCount++ >= 40) {
            break;
         }

         props.addProperty(e.getKey(), e.getValue());
      }

      o.add("stableProperties", props);
      List<CapabilityDecl> caps = new ArrayList<>(map.getCapabilities());
      caps.sort((a, b) -> a.getId().compareTo(b.getId()));
      if (caps.size() > 20) {
         caps = caps.subList(0, 20);
      }

      o.add("capabilitiesSoFar", GSON.toJsonTree(caps.stream().map(c -> {
         JsonObject co = new JsonObject();
         co.addProperty("id", c.getId());
         co.addProperty("confidence", c.getConfidence());
         return co;
      }).toList()));
      o.add("warnings", GSON.toJsonTree(map.getWarnings()));
      o.add("allowedCapabilityIds", GSON.toJsonTree(new ArrayList<>(CapabilitySchema.allowedFor(map.getSubjectKind()))));
      return GSON.toJson(o);
   }

   public static String systemPrompt() {
      return "You classify Minecraft registry entries for a modpack assistant.\nRespond with a single JSON object only (no markdown fences). Required top-level fields:\nschemaVersion (must be %d), shortDescription, aliases, likelyUses, capabilityHints, uncertaintyNotes.\nStrict length limits (responses that exceed any limit are rejected):\n- shortDescription: 1-%d characters, single line, plain text (no markdown).\n- aliases: 0-%d strings, each 1-%d characters (short search phrases, lowercase ok).\n- likelyUses: 0-%d strings, each 1-%d characters (brief phrases, not full sentences).\n- uncertaintyNotes: 0-%d strings, each 1-%d characters.\n- capabilityHints: 0-%d objects with id, confidence (0.0-1.0), optional reason;\n  each id must be from allowedCapabilityIds in the user message.\nDo not invent safety-sensitive capabilities without evidence in the input.\n"
         .formatted(1, 240, 12, 48, 12, 96, 8, 140, 8)
         .stripIndent();
   }
}
