package com.player2.playerengine.modintelligence.enrich;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.modintelligence.capability.CapabilitySchema;
import com.player2.playerengine.modintelligence.capability.CapabilitySubjectKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CapabilityEnrichmentValidator {
   private static final Gson GSON = new Gson();

   private CapabilityEnrichmentValidator() {
   }

   public static CapabilityEnrichmentResult parseAndValidate(String json, CapabilitySubjectKind kind) throws IllegalArgumentException {
      if (json != null && !json.isBlank()) {
         String trimmed = json.trim();
         int start = trimmed.indexOf(123);
         int end = trimmed.lastIndexOf(125);
         if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
         }

         JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
         CapabilityEnrichmentResult result = (CapabilityEnrichmentResult)GSON.fromJson(root, CapabilityEnrichmentResult.class);
         if (result == null) {
            throw new IllegalArgumentException("null_result");
         }

         if (result.getSchemaVersion() != 1) {
            throw new IllegalArgumentException("schema_version:got=" + result.getSchemaVersion() + ":expected=1");
         }

         validateShortDescription(result.getShortDescription());
         validateStringList("aliases", result.getAliases(), 12, 48);
         validateStringList("likelyUses", result.getLikelyUses(), 12, 96);
         validateStringList("uncertaintyNotes", result.getUncertaintyNotes(), 8, 140);
         Set<String> allowed = CapabilitySchema.allowedFor(kind);
         if (result.getCapabilityHints() != null) {
            if (result.getCapabilityHints().size() > 8) {
               throw new IllegalArgumentException("capabilityHints_cap:size=" + result.getCapabilityHints().size() + ":max=8");
            }

            for (int i = 0; i < result.getCapabilityHints().size(); i++) {
               CapabilityEnrichmentResult.CapabilityHint hint = result.getCapabilityHints().get(i);
               if (hint.getId() == null || !allowed.contains(hint.getId())) {
                  throw new IllegalArgumentException("unknown_capability:capabilityHints[" + i + "]:id=" + (hint.getId() == null ? "null" : hint.getId()));
               }

               if (hint.getConfidence() < 0.0 || hint.getConfidence() > 1.0 || Double.isNaN(hint.getConfidence())) {
                  throw new IllegalArgumentException("confidence_range:capabilityHints[" + i + "]:id=" + hint.getId() + ":confidence=" + hint.getConfidence());
               }
            }
         }

         return result;
      } else {
         throw new IllegalArgumentException("empty_response");
      }
   }

   private static void validateShortDescription(String desc) {
      if (desc == null || desc.isBlank()) {
         throw new IllegalArgumentException("shortDescription:blank");
      }

      if (!desc.contains("\n") && !desc.contains("\r")) {
         if (desc.length() > 240) {
            throw new IllegalArgumentException("shortDescription:len=" + desc.length() + ":max=240");
         }
      } else {
         throw new IllegalArgumentException("shortDescription:newline");
      }
   }

   private static void validateStringList(String field, List<String> list, int maxItems, int maxLen) {
      if (list != null) {
         if (list.size() > maxItems) {
            throw new IllegalArgumentException("list_cap:" + field + ":size=" + list.size() + ":max=" + maxItems);
         }

         Set<String> seen = new HashSet<>();

         for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s == null) {
               throw new IllegalArgumentException("string_bounds:" + field + "[" + i + "]:null:maxLen=" + maxLen);
            }

            if (s.isBlank()) {
               throw new IllegalArgumentException("string_bounds:" + field + "[" + i + "]:blank:maxLen=" + maxLen);
            }

            if (s.length() > maxLen) {
               throw new IllegalArgumentException("string_bounds:" + field + "[" + i + "]:len=" + s.length() + ":max=" + maxLen);
            }

            for (int c = 0; c < s.length(); c++) {
               if (Character.isISOControl(s.charAt(c))) {
                  throw new IllegalArgumentException("control_char:" + field + "[" + i + "]:code=" + s.charAt(c));
               }
            }

            seen.add(s.trim().toLowerCase());
         }
      }
   }
}
