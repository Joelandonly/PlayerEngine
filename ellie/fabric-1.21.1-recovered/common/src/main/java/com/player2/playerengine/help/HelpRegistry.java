package com.player2.playerengine.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HelpRegistry {
   private static final LinkedHashMap<String, LinkedHashMap<String, HelpEntry>> BY_ROOT = new LinkedHashMap<>();

   private HelpRegistry() {
   }

   public static synchronized void register(HelpEntry entry) {
      if (entry == null) {
         throw new IllegalArgumentException("HelpRegistry.register: entry must not be null");
      }

      BY_ROOT.computeIfAbsent(entry.rootId(), k -> new LinkedHashMap<>()).put(entry.path(), entry);
   }

   public static synchronized void clear(String rootId) {
      BY_ROOT.remove(rootId);
   }

   public static synchronized boolean has(String rootId, String path) {
      LinkedHashMap<String, HelpEntry> bucket = BY_ROOT.get(rootId);
      return bucket != null && bucket.containsKey(HelpPath.normalize(path));
   }

   public static synchronized HelpEntry get(String rootId, String path) {
      LinkedHashMap<String, HelpEntry> bucket = BY_ROOT.get(rootId);
      return bucket == null ? null : bucket.get(HelpPath.normalize(path));
   }

   public static synchronized List<HelpEntry> entriesFor(String rootId) {
      LinkedHashMap<String, HelpEntry> bucket = BY_ROOT.get(rootId);
      if (bucket == null) {
         return List.of();
      }

      List<HelpEntry> list = new ArrayList<>(bucket.values());
      list.sort(Comparator.comparing(e -> categorySortKey(e.category())));
      return list;
   }

   public static synchronized Set<String> roots() {
      return new LinkedHashSet<>(BY_ROOT.keySet());
   }

   private static String categorySortKey(String category) {
      return category != null && !category.isBlank() ? category : String.valueOf('\uffff');
   }
}
