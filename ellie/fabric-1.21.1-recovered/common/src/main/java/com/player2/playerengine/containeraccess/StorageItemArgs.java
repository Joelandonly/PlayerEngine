package com.player2.playerengine.containeraccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class StorageItemArgs {
   public static final int COUNT_ALL = -1;
   public static final int MAX_COUNT = 3456;

   private StorageItemArgs() {
   }

   public static StorageItemArgs.ParseResult parse(String[] tailUnits) {
      List<StorageItemArgs.ParseFailure> failures = new ArrayList<>();
      if (tailUnits != null && tailUnits.length != 0) {
         String joined = String.join(" ", tailUnits);
         String[] entries = joined.split(",", -1);
         Map<Item, StorageItemArgs.ItemQuery> merged = new LinkedHashMap<>();

         for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
               failures.add(new StorageItemArgs.ParseFailure(StorageAccessCode.INVALID_ARGUMENT, rawEntry, "empty item entry (stray comma?)"));
            } else {
               String[] parts = entry.split("\\s+");
               if (parts.length > 2) {
                  failures.add(
                     new StorageItemArgs.ParseFailure(
                        StorageAccessCode.INVALID_ARGUMENT, entry, "entry \"" + entry + "\" must be \"<item>\" or \"<item> <count>\""
                     )
                  );
               } else {
                  Item item = resolveItem(parts[0]);
                  if (item == null) {
                     failures.add(
                        new StorageItemArgs.ParseFailure(
                           StorageAccessCode.INVALID_ITEM_NAME, parts[0], "\"" + parts[0] + "\" did not resolve to a registered item"
                        )
                     );
                  } else {
                     String displayId = displayIdFor(item);
                     int count = -1;
                     if (parts.length == 2) {
                        Integer parsedCount = parseCount(parts[1]);
                        if (parsedCount == null) {
                           failures.add(
                              new StorageItemArgs.ParseFailure(
                                 StorageAccessCode.INVALID_COUNT, parts[1], "count for \"" + displayId + "\" must be 1..3456, got \"" + parts[1] + "\""
                              )
                           );
                           continue;
                        }

                        count = parsedCount;
                     }

                     StorageItemArgs.ItemQuery existing = merged.get(item);
                     if (existing == null) {
                        merged.put(item, new StorageItemArgs.ItemQuery(item, displayId, count));
                     } else if (!existing.isAll() || count != -1) {
                        if (!existing.isAll() && count != -1) {
                           long sum = (long)existing.countOrAll() + count;
                           if (sum > 3456L) {
                              failures.add(
                                 new StorageItemArgs.ParseFailure(
                                    StorageAccessCode.INVALID_COUNT,
                                    displayId,
                                    "merged count for \"" + displayId + "\" is " + sum + ", above the maximum of 3456"
                                 )
                              );
                           } else {
                              merged.put(item, new StorageItemArgs.ItemQuery(item, displayId, (int)sum));
                           }
                        } else {
                           failures.add(
                              new StorageItemArgs.ParseFailure(
                                 StorageAccessCode.INVALID_ARGUMENT, displayId, "\"" + displayId + "\" appears both with and without a count"
                              )
                           );
                        }
                     }
                  }
               }
            }
         }

         return !failures.isEmpty()
            ? new StorageItemArgs.ParseResult(List.of(), List.copyOf(failures))
            : new StorageItemArgs.ParseResult(List.copyOf(merged.values()), List.of());
      } else {
         failures.add(
            new StorageItemArgs.ParseFailure(
               StorageAccessCode.INVALID_ARGUMENT, "", "no items given; expected \"<item>\" or \"<item> <count>\" entries separated by \",\""
            )
         );
         return new StorageItemArgs.ParseResult(List.of(), List.copyOf(failures));
      }
   }

   public static String displayIdFor(Item item) {
      ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
      return "minecraft".equals(key.getNamespace()) ? key.getPath() : key.toString();
   }

   private static Item resolveItem(String token) {
      String idText = token.contains(":") ? token : "minecraft:" + token;
      ResourceLocation id = ResourceLocation.tryParse(idText.toLowerCase(Locale.ROOT));
      if (id == null) {
         return null;
      }

      Item item = (Item)BuiltInRegistries.ITEM.get(id);
      return item == Items.AIR ? null : item;
   }

   private static Integer parseCount(String token) {
      int value;
      try {
         value = Integer.parseInt(token.trim());
      } catch (NumberFormatException e) {
         return null;
      }

      return value >= 1 && value <= 3456 ? value : null;
   }

   public record ItemQuery(Item item, String displayId, int countOrAll) {
      public boolean isAll() {
         return this.countOrAll == -1;
      }
   }

   public record ParseFailure(StorageAccessCode code, String token, String detail) {
   }

   public record ParseResult(List<StorageItemArgs.ItemQuery> queries, List<StorageItemArgs.ParseFailure> failures) {
      public boolean ok() {
         return this.failures.isEmpty();
      }
   }
}
