package com.player2.playerengine.retrieval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class ToolMetadataRegistry {
   private final Map<String, ToolDocument> documents;
   private final String versionToken;

   private ToolMetadataRegistry(Map<String, ToolDocument> documents, String versionToken) {
      this.documents = Collections.unmodifiableMap(documents);
      this.versionToken = versionToken;
   }

   public static ToolMetadataRegistry create(Collection<ToolDocument> docs) {
      Map<String, ToolDocument> map = new LinkedHashMap<>();

      for (ToolDocument doc : docs) {
         map.put(doc.id(), doc);
      }

      return new ToolMetadataRegistry(map, computeToken(map));
   }

   public Map<String, ToolDocument> documents() {
      return this.documents;
   }

   public Collection<ToolDocument> values() {
      return this.documents.values();
   }

   public ToolDocument getDocument(String id) {
      return this.documents.get(id);
   }

   public String getVersionToken() {
      return this.versionToken;
   }

   private static String computeToken(Map<String, ToolDocument> docs) {
      TreeMap<String, ToolDocument> sorted = new TreeMap<>(docs);
      StringBuilder sb = new StringBuilder();

      for (Entry<String, ToolDocument> entry : sorted.entrySet()) {
         ToolDocument d = entry.getValue();
         sb.append(d.id()).append('=');
         sb.append(d.name()).append('|');
         sb.append(d.description()).append('|');
         sb.append(d.whenToUse()).append('|');
         sb.append(String.join(",", d.examples())).append('|');
         sb.append(String.join(",", d.keywords())).append('|');
         sb.append(String.join(",", d.categoryTags())).append('\n');
      }

      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(64);

         for (byte b : hash) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      } catch (NoSuchAlgorithmException e) {
         throw new RuntimeException("SHA-256 unavailable", e);
      }
   }
}
