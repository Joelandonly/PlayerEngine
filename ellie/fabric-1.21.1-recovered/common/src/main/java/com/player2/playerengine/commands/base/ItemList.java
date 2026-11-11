package com.player2.playerengine.commands.base;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.modintelligence.ModIntelligenceService;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.capability.CapabilitySubjectKind;
import com.player2.playerengine.modintelligence.capability.EnrichmentSummary;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.FuzzySearchHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class ItemList {
   public ItemTarget[] items;
   private static volatile Set<String> craftingOutputCorpus = Collections.emptySet();

   public ItemList(ItemTarget[] items) {
      this.items = items;
   }

   public static void updateCraftingCorpus(Set<Item> recipeOutputItems) {
      if (recipeOutputItems != null && !recipeOutputItems.isEmpty()) {
         Set<String> corpus = new HashSet<>(recipeOutputItems.size() * 2);

         for (Item item : recipeOutputItems) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key != null) {
               if ("minecraft".equals(key.getNamespace())) {
                  corpus.add(key.getPath());
               } else {
                  corpus.add(key.toString());
               }
            }
         }

         craftingOutputCorpus = Collections.unmodifiableSet(corpus);
      }
   }

   private static Collection<String> buildSuggestionCorpus() {
      Collection<String> catalogueNames = TaskCatalogue.resourceNames();
      Set<String> corpus = craftingOutputCorpus;
      if (corpus.isEmpty()) {
         return catalogueNames;
      }

      Set<String> combined = new HashSet<>(corpus.size() + catalogueNames.size());
      combined.addAll(corpus);
      combined.addAll(catalogueNames);
      return combined;
   }

   private static Item resolveItem(String token) {
      String idText = token.contains(":") ? token : "minecraft:" + token;
      ResourceLocation id = ResourceLocation.tryParse(idText.toLowerCase(Locale.ROOT));
      if (id != null) {
         Item item = (Item)BuiltInRegistries.ITEM.get(id);
         if (item != Items.AIR) {
            return item;
         }
      }

      String mapped = DescriptionIdIndex.resolveToRegistryId(token.toLowerCase(Locale.ROOT));
      if (mapped != null) {
         ResourceLocation mappedId = ResourceLocation.tryParse(mapped);
         if (mappedId != null) {
            Item mappedItem = (Item)BuiltInRegistries.ITEM.get(mappedId);
            if (mappedItem != Items.AIR) {
               return mappedItem;
            }
         }
      }

      return null;
   }

   private static String queryModIntelligenceAlias(String token) {
      try {
         String mapped = DescriptionIdIndex.resolveToRegistryId(token.toLowerCase(Locale.ROOT));
         String idText = mapped != null ? mapped : (token.contains(":") ? token : "minecraft:" + token);
         ResourceLocation id = ResourceLocation.tryParse(idText.toLowerCase(Locale.ROOT));
         if (id == null) {
            return null;
         }

         Optional<CapabilityMap> mapOpt = ModIntelligenceService.queryService().get(CapabilitySubjectKind.ITEM, id.toString());
         if (mapOpt.isEmpty()) {
            return null;
         }

         EnrichmentSummary enrichment = mapOpt.get().getEnrichment();
         if (enrichment == null) {
            return null;
         }

         List<String> aliases = enrichment.getAliases();
         if (aliases != null && !aliases.isEmpty()) {
            StringBuilder sb = new StringBuilder(id.toString());
            sb.append(" (also known as: ");

            for (int i = 0; i < aliases.size(); i++) {
               if (i > 0) {
                  sb.append(", ");
               }

               sb.append(aliases.get(i));
            }

            sb.append(")");
            return sb.toString();
         } else {
            return null;
         }
      } catch (Exception ignored) {
         return null;
      }
   }

   private static String rejectionFor(String token) {
      String aliasHint = queryModIntelligenceAlias(token);
      String aliasSuffix = aliasHint != null ? " Related: " + aliasHint + "." : "";
      if (DescriptionIdIndex.looksLikeLangKey(token)) {
         return "Item does not exist: \""
            + token
            + "\". That looks like a translation/lang key, not a registry id. Use the full registry id in the form namespace:path (e.g. iceandfire:podium_oak). Find it via `inspect ITEM <id>` or an advanced tooltip (F3+H)."
            + aliasSuffix;
      }

      if (!token.contains(":")) {
         String closestMatch = FuzzySearchHelper.getClosestMatchWithinThreshold(token, buildSuggestionCorpus());
         if (closestMatch != null && !closestMatch.equalsIgnoreCase(token) && resolveItem(closestMatch) != null) {
            return "Item does not exist: \"" + token + "\". Did you mean \"" + closestMatch + "\"?" + aliasSuffix;
         } else {
            return closestMatch != null && !closestMatch.equalsIgnoreCase(token) && TaskCatalogue.taskExists(closestMatch)
               ? "Item does not exist: \"" + token + "\". Did you mean the generic name \"" + closestMatch + "\"?" + aliasSuffix
               : "Item does not exist: \""
                  + token
                  + "\". If it is a modded item, use its full registry id (namespace:path), e.g. iceandfire:podium_oak."
                  + aliasSuffix;
         }
      } else {
         String candidateSuffix = "";
         ResourceLocation parsedId = ResourceLocation.tryParse(token.toLowerCase(Locale.ROOT));
         if (parsedId != null) {
            List<String> candidates = DescriptionIdIndex.candidatesInNamespace(parsedId.getNamespace(), parsedId.getPath(), 8);
            if (!candidates.isEmpty()) {
               StringBuilder sb = new StringBuilder(" Did you mean one of: ");

               for (int i = 0; i < candidates.size(); i++) {
                  if (i > 0) {
                     sb.append(", ");
                  }

                  sb.append(candidates.get(i));
               }

               sb.append("?");
               candidateSuffix = sb.toString();
            }
         }

         return "Item does not exist: \""
            + token
            + "\". No item with that registry id is registered — check the namespace and path, or the mod may not be installed."
            + candidateSuffix
            + aliasSuffix;
      }
   }

   public static ItemList parseRemainder(String line) throws CommandException {
      line = line.trim();
      if (line.startsWith("[") && line.endsWith("]")) {
         line = line.substring(1, line.length() - 1);
         String[] parts = line.split(",");
         Map<Item, Integer> items = new LinkedHashMap<>();
         Map<String, Integer> catalogueItems = new LinkedHashMap<>();

         for (String part : parts) {
            part = part.trim();
            String[] itemQuantityPair = part.split(" ");
            if (itemQuantityPair.length > 2 || itemQuantityPair.length <= 0) {
               throw new CommandException(
                  "Resource array element must be either \"item count\" or \"item\", but \"" + part + "\" has " + itemQuantityPair.length + " parts."
               );
            }

            String nameToken = itemQuantityPair[0];
            int count = 1;
            if (itemQuantityPair.length > 1) {
               try {
                  count = Integer.parseInt(itemQuantityPair[1]);
               } catch (Exception var13) {
                  throw new CommandException("Failed to parse count for array element \"" + part + "\".");
               }
            }

            Item item = resolveItem(nameToken);
            if (item == null) {
               if (!TaskCatalogue.taskExists(nameToken)) {
                  throw new CommandException(rejectionFor(nameToken));
               }

               catalogueItems.merge(nameToken, count, Integer::sum);
            } else {
               items.merge(item, count, Integer::sum);
            }
         }

         if (!items.isEmpty() || !catalogueItems.isEmpty()) {
            List<ItemTarget> targets = new ArrayList<>(items.size() + catalogueItems.size());

            for (Entry<Item, Integer> entry : items.entrySet()) {
               targets.add(new ItemTarget(entry.getKey(), entry.getValue()));
            }

            for (Entry<String, Integer> entry : catalogueItems.entrySet()) {
               targets.add(new ItemTarget(entry.getKey(), entry.getValue()));
            }

            return new ItemList(targets.toArray(new ItemTarget[0]));
         }
      } else {
         String[] items = line.split(" ");
         if (items.length >= 1) {
            String name = items[0];
            int countx = 1;
            if (items.length == 2) {
               try {
                  countx = Integer.parseInt(items[1]);
               } catch (NumberFormatException var12) {
                  throw new CommandException("Failed to parse the following argument into type " + Integer.class + ": " + items[1] + ".");
               }
            } else if (items.length > 2) {
               throw new CommandException("Invalid item argument structure: Must be of form `<item>` or `<item> <count>`");
            }

            Item item = resolveItem(name);
            if (item == null) {
               if (TaskCatalogue.taskExists(name)) {
                  return new ItemList(new ItemTarget[]{new ItemTarget(name, countx)});
               }

               throw new CommandException(rejectionFor(name));
            }

            return new ItemList(new ItemTarget[]{new ItemTarget(item, countx)});
         }
      }

      return new ItemList(new ItemTarget[0]);
   }
}
