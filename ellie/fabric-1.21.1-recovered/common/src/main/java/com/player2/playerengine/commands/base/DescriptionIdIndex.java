package com.player2.playerengine.commands.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class DescriptionIdIndex {
   private static volatile Map<String, String> descriptionIdToRegistryId = null;
   private static volatile Map<String, String> uniqueModdedPathToRegistryId = null;
   private static volatile Map<String, List<String>> namespaceToPaths = null;

   private DescriptionIdIndex() {
   }

   private static void ensureBuilt() {
      if (descriptionIdToRegistryId == null) {
         synchronized (DescriptionIdIndex.class) {
            if (descriptionIdToRegistryId == null) {
               Map<String, String> descMap = new HashMap<>();
               Map<String, String> pathMap = new HashMap<>();
               Set<String> ambiguousPaths = new HashSet<>();
               Map<String, List<String>> nsMap = new HashMap<>();

               for (Item item : BuiltInRegistries.ITEM) {
                  ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                  if (key != null) {
                     String registryId = key.toString();
                     String descId = item.getDescriptionId();
                     if (descId != null && !descId.isEmpty()) {
                        descMap.putIfAbsent(descId, registryId);
                     }

                     if (!"minecraft".equals(key.getNamespace())) {
                        String path = key.getPath();
                        if (!ambiguousPaths.contains(path)) {
                           if (pathMap.containsKey(path) && !pathMap.get(path).equals(registryId)) {
                              pathMap.remove(path);
                              ambiguousPaths.add(path);
                           } else {
                              pathMap.put(path, registryId);
                           }
                        }
                     }

                     nsMap.computeIfAbsent(key.getNamespace(), k -> new ArrayList<>()).add(key.getPath());
                  }
               }

               Map<String, List<String>> frozenNsMap = new HashMap<>(nsMap.size() * 2);

               for (Entry<String, List<String>> e : nsMap.entrySet()) {
                  frozenNsMap.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
               }

               namespaceToPaths = Collections.unmodifiableMap(frozenNsMap);
               uniqueModdedPathToRegistryId = Collections.unmodifiableMap(pathMap);
               descriptionIdToRegistryId = Collections.unmodifiableMap(descMap);
            }
         }
      }
   }

   public static String resolveToRegistryId(String token) {
      if (token != null && !token.isEmpty()) {
         ensureBuilt();
         String exact = descriptionIdToRegistryId.get(token);
         if (exact != null) {
            return exact;
         }

         if (token.startsWith("block.")) {
            String stripped = token.substring(6);
            String viaStrip = descriptionIdToRegistryId.get("item." + stripped);
            if (viaStrip != null) {
               return viaStrip;
            }
         } else if (token.startsWith("item.")) {
            String stripped = token.substring(5);
            String viaStrip = descriptionIdToRegistryId.get("block." + stripped);
            if (viaStrip != null) {
               return viaStrip;
            }
         }

         String viaPath = uniqueModdedPathToRegistryId.get(token);
         return viaPath != null ? viaPath : null;
      } else {
         return null;
      }
   }

   public static boolean looksLikeLangKey(String token) {
      return token != null && (token.startsWith("block.") || token.startsWith("item.")) && token.indexOf(46, 6) > 0;
   }

   public static List<String> candidatesInNamespace(String namespace, String typedPath, int limit) {
      if (namespace != null && !namespace.isEmpty() && typedPath != null && limit > 0) {
         ensureBuilt();
         List<String> paths = namespaceToPaths.get(namespace.toLowerCase(Locale.ROOT));
         if (paths != null && !paths.isEmpty()) {
            String lowerTyped = typedPath.toLowerCase(Locale.ROOT);
            Set<String> typedStems = stemTokens(lowerTyped);
            List<DescriptionIdIndex.ScoredPath> scored = new ArrayList<>(paths.size());
            Iterator normalizedTyped = paths.iterator();

            while (true) {
               String path;
               String lowerPath;
               int sharedCount;
               while (true) {
                  if (!normalizedTyped.hasNext()) {
                     if (scored.isEmpty()) {
                        return Collections.emptyList();
                     }

                     String normalizedTypedx = lowerTyped;
                     scored.sort(
                        Comparator.<DescriptionIdIndex.ScoredPath, Integer>comparing(sp -> sp.sharedStems, Comparator.reverseOrder())
                           .thenComparingInt(sp -> sp.prefixMatch ? 0 : 1)
                           .thenComparingInt(sp -> simpleEditDistance(normalizedTypedx, sp.path.toLowerCase(Locale.ROOT)))
                     );
                     int resultSize = Math.min(limit, scored.size());
                     List<String> result = new ArrayList<>(resultSize);

                     for (int i = 0; i < resultSize; i++) {
                        result.add(namespace + ":" + scored.get(i).path);
                     }

                     return Collections.unmodifiableList(result);
                  }

                  path = (String)normalizedTyped.next();
                  lowerPath = path.toLowerCase(Locale.ROOT);
                  if (!lowerPath.equals(lowerTyped)) {
                     Set<String> pathStems = stemTokens(lowerPath);
                     sharedCount = sharedStemCount(typedStems, pathStems);
                     if (sharedCount != 0) {
                        break;
                     }

                     boolean substringMatch = false;

                     for (String stem : typedStems) {
                        if (lowerPath.contains(stem)) {
                           substringMatch = true;
                           break;
                        }
                     }

                     if (substringMatch) {
                        break;
                     }
                  }
               }

               boolean prefixMatch = hasSamePrimaryPrefix(lowerTyped, lowerPath);
               scored.add(new DescriptionIdIndex.ScoredPath(path, sharedCount, prefixMatch));
            }
         } else {
            return Collections.emptyList();
         }
      } else {
         return Collections.emptyList();
      }
   }

   private static Set<String> stemTokens(String path) {
      if (path.indexOf(95) < 0) {
         return Collections.singleton(path);
      }

      String[] parts = path.split("_", -1);
      Set<String> stems = new HashSet<>(parts.length * 2);

      for (String p : parts) {
         if (!p.isEmpty()) {
            stems.add(p);
         }
      }

      return stems.isEmpty() ? Collections.singleton(path) : stems;
   }

   private static int sharedStemCount(Set<String> a, Set<String> b) {
      int count = 0;

      for (String s : a) {
         if (b.contains(s)) {
            count++;
         }
      }

      return count;
   }

   private static boolean hasSamePrimaryPrefix(String typed, String path) {
      int idx = typed.indexOf(95);
      String prefix = idx >= 0 ? typed.substring(0, idx) : typed;
      return !prefix.isEmpty() && path.startsWith(prefix);
   }

   private static int simpleEditDistance(String a, String b) {
      int m = a.length();
      int n = b.length();
      if (m == 0) {
         return n;
      }

      if (n == 0) {
         return m;
      }

      if (m > n) {
         String tmp = a;
         a = b;
         b = tmp;
         int t = m;
         m = n;
         n = t;
      }

      int[] prev = new int[m + 1];
      int[] curr = new int[m + 1];
      int i = 0;

      while (i <= m) {
         prev[i] = i++;
      }

      for (int j = 1; j <= n; j++) {
         curr[0] = j;

         for (int ix = 1; ix <= m; ix++) {
            int cost = a.charAt(ix - 1) == b.charAt(j - 1) ? 0 : 1;
            curr[ix] = Math.min(Math.min(curr[ix - 1] + 1, prev[ix] + 1), prev[ix - 1] + cost);
         }

         int[] swap = prev;
         prev = curr;
         curr = swap;
      }

      return prev[m];
   }

   private static final class ScoredPath {
      final String path;
      final int sharedStems;
      final boolean prefixMatch;

      ScoredPath(String path, int sharedStems, boolean prefixMatch) {
         this.path = path;
         this.sharedStems = sharedStems;
         this.prefixMatch = prefixMatch;
      }
   }
}
