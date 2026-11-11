package com.player2.playerengine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.player2.playerengine.control.KillAura;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.util.helpers.ConfigHelper;
import com.player2.playerengine.util.serialization.gson.BlockPosTypeAdapter;
import com.player2.playerengine.util.serialization.gson.ItemListTypeAdapter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class PlayerEngineSettingsAdminService {
   private static final String CONFIG_FILE = "playerengine_settings.json";
   private static final int MAX_ITEM_LIST_ENTRIES = 64;
   private static final int MAX_ITEM_LIST_TEXT_LENGTH = 3500;
   private static final Gson READER_GSON = new GsonBuilder()
      .registerTypeAdapter(BlockPos.class, new BlockPosTypeAdapter())
      .registerTypeAdapter((new TypeToken<List<Item>>() {}).getType(), new ItemListTypeAdapter())
      .create();
   private static final Set<String> BOOLEAN_KEYS = Set.of(
      "avoidSearchingDungeonChests",
      "avoidOceanBlocks",
      "collectPickaxeFirst",
      "replantCrops",
      "mobDefense",
      "dodgeProjectiles",
      "killOrAvoidAnnoyingHostiles",
      "avoidDrowning",
      "extinguishSelfWithWater",
      "autoEat",
      "hungerEnabled",
      "autoMLGBucket",
      "fastCraftMacrosEnabled",
      "preferLocalCraftingTable",
      "enableAgenticPlanner",
      "enableLookAtOwnerIdle",
      "agenticPlannerFallbackGather",
      "agenticStorageAllowPlacement",
      "agenticStoragePreferExisting",
      "agenticStorageAvoidLootChests",
      "agenticDepositKeepTools",
      "agenticEnableLabelChest",
      "agenticLabelUseModelText",
      "enableDeferredSmelt",
      "enableSmithing",
      "ellieGpsEnabled",
      "ellieGpsUseModelDescription",
      "dontThrowAwayCustomNameItems",
      "dontThrowAwayEnchantedItems",
      "throwAwayUnusedItems",
      "limitFuelsToSupportedFuels"
   );
   private static final Set<String> ITEM_LIST_KEYS = Set.of("throwawayItems", "importantItems", "supportedFuels");

   private PlayerEngineSettingsAdminService() {
   }

   public static Map<String, String> snapshot() {
      return snapshotResult().values();
   }

   public static PlayerEngineSettingsAdminService.SnapshotResult snapshotResult() {
      boolean loadFailed = false;

      PlayerEngineSettings settings;
      try {
         settings = readFreshCandidate();
      } catch (IOException ignored) {
         settings = null;
         loadFailed = true;
      }

      if (settings == null) {
         loadFailed = true;
         settings = liveFallback();
      }

      settings.normalizeAdminSupportedValues();
      return new PlayerEngineSettingsAdminService.SnapshotResult(snapshotOf(settings), loadFailed, PlayerEngineController.currentSettingsLiveState());
   }

   public static synchronized PlayerEngineSettingsAdminService.UpdateResult update(String key, String value, BlockPos currentPosition) {
      if (!isSupportedKey(key)) {
         return PlayerEngineSettingsAdminService.UpdateResult.UNSUPPORTED_SETTING;
      }

      PlayerEngineSettingsAdminService.CandidateMutation mutation = parseMutation(key, value, currentPosition);
      if (mutation == null) {
         return PlayerEngineSettingsAdminService.UpdateResult.INVALID_VALUE;
      }

      PlayerEngineSettings candidate;
      try {
         candidate = readFreshCandidate();
      } catch (IOException e) {
         return PlayerEngineSettingsAdminService.UpdateResult.SAVE_FAILED;
      }

      if (candidate == null) {
         return PlayerEngineSettingsAdminService.UpdateResult.INVALID_VALUE;
      }

      candidate.normalizeAdminSupportedValues();
      mutation.apply(candidate);
      if (!candidate.hasValidFoodSafety()) {
         return PlayerEngineSettingsAdminService.UpdateResult.INVALID_VALUE;
      }

      candidate.normalizeAdminSupportedValues();

      try {
         JsonElement canonicalJson = READER_GSON.toJsonTree(candidate, PlayerEngineSettings.class);
         if (!ConfigHelper.saveConfigChecked("playerengine_settings.json", canonicalJson)) {
            return PlayerEngineSettingsAdminService.UpdateResult.SAVE_FAILED;
         }
      } catch (RuntimeException e) {
         return PlayerEngineSettingsAdminService.UpdateResult.SAVE_FAILED;
      }

      PlayerEngineController.SettingsPublicationOutcome publication = PlayerEngineController.publishSettingsFromAdmin(candidate);
      if (publication.hasFailures()) {
         return PlayerEngineSettingsAdminService.UpdateResult.SAVED_LIVE_FAILED;
      } else {
         return publication.fullyApplied()
            ? PlayerEngineSettingsAdminService.UpdateResult.OK
            : PlayerEngineSettingsAdminService.UpdateResult.SAVED_LIVE_PENDING;
      }
   }

   static PlayerEngineController.SettingsPublicationOutcome reloadFromDiskAndPublish() {
      PlayerEngineSettings candidate;
      try {
         candidate = readFreshCandidate();
      } catch (IOException e) {
         candidate = null;
      }

      if (candidate == null) {
         return new PlayerEngineController.SettingsPublicationOutcome(1, 0, 0, 1);
      }

      candidate.normalizeAdminSupportedValues();
      return PlayerEngineController.publishSettingsFromAdmin(candidate);
   }

   private static PlayerEngineSettings readFreshCandidate() throws IOException {
      Path path = PlayerEnginePaths.userFile("playerengine_settings.json");
      if (Files.notExists(path)) {
         return new PlayerEngineSettings();
      }

      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         JsonElement root = JsonParser.parseReader(reader);
         return !hasValidStoredItemLists(root) ? null : (PlayerEngineSettings)READER_GSON.fromJson(root, PlayerEngineSettings.class);
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static boolean hasValidStoredItemLists(JsonElement root) {
      if (root != null && root.isJsonObject()) {
         label49:
         for (String key : ITEM_LIST_KEYS) {
            if (root.getAsJsonObject().has(key)) {
               JsonElement list = root.getAsJsonObject().get(key);
               if (list != null && list.isJsonArray()) {
                  int entryCount = 0;
                  Iterator var5 = list.getAsJsonArray().iterator();

                  Item item;
                  do {
                     if (!var5.hasNext()) {
                        continue label49;
                     }

                     JsonElement entry = (JsonElement)var5.next();
                     if (++entryCount > 64) {
                        return false;
                     }

                     if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                        return false;
                     }

                     item = ItemListTypeAdapter.resolveItem(entry.getAsString());
                  } while (item != null);

                  return false;
               }

               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static PlayerEngineSettings liveFallback() {
      for (PlayerEngineController controller : PlayerEngineController.staticControllers.values()) {
         if (controller != null) {
            PlayerEngineSettings live = controller.getModSettings();
            if (live != null) {
               return live.defensiveCopy();
            }
         }
      }

      return new PlayerEngineSettings();
   }

   private static Map<String, String> snapshotOf(PlayerEngineSettings settings) {
      LinkedHashMap<String, String> values = new LinkedHashMap<>();
      values.put("commandPrefix", settings.getCommandPrefix());
      values.put("resourcePickupDropRange", whole(settings.getResourcePickupRange()));
      values.put("minimumFoodAllowed", String.valueOf(settings.getMinimumFoodAllowed()));
      values.put("foodUnitsToCollect", String.valueOf(settings.getFoodUnitsToCollect()));
      values.put("resourceChestLocateRange", whole(settings.getResourceChestLocateRange()));
      values.put("resourceMineRange", whole(settings.getResourceMineRange()));
      values.put("avoidSearchingDungeonChests", String.valueOf(settings.shouldAvoidSearchingForDungeonChests()));
      values.put("avoidOceanBlocks", String.valueOf(settings.shouldAvoidOcean()));
      values.put("entityReachRange", whole(settings.getEntityReachRange()));
      values.put("collectPickaxeFirst", String.valueOf(settings.shouldCollectPickaxeFirst()));
      values.put("replantCrops", String.valueOf(settings.shouldReplantCrops()));
      values.put("mobDefense", String.valueOf(settings.isMobDefense()));
      values.put("forceFieldStrategy", settings.getForceFieldStrategy().name());
      values.put("dodgeProjectiles", String.valueOf(settings.isDodgeProjectiles()));
      values.put("killOrAvoidAnnoyingHostiles", String.valueOf(settings.shouldDealWithAnnoyingHostiles()));
      values.put("avoidDrowning", String.valueOf(settings.shouldAvoidDrowning()));
      values.put("extinguishSelfWithWater", String.valueOf(settings.shouldExtinguishSelfWithWater()));
      values.put("autoEat", String.valueOf(settings.isAutoEat()));
      values.put("hungerEnabled", String.valueOf(settings.isHungerEnabled()));
      values.put("autoMLGBucket", String.valueOf(settings.shouldAutoMLGBucket()));
      values.put("overworldToNetherBehaviour", settings.getOverworldToNetherBehaviour().name());
      values.put("netherFastTravelWalkingRange", String.valueOf(settings.getNetherFastTravelWalkingRange()));
      values.put("idleCommand", settings.getIdleCommand());
      values.put("fastCraftMacrosEnabled", String.valueOf(settings.isFastCraftMacrosEnabled()));
      values.put("craftDelaySeconds", milliseconds(settings.getCraftDelaySeconds()));
      values.put("craftTableLookHoldSeconds", milliseconds(settings.getCraftTableLookHoldSeconds()));
      values.put("preferLocalCraftingTable", String.valueOf(settings.isPreferLocalCraftingTable()));
      values.put("craftingTableReuseRadius", whole(settings.getCraftingTableReuseRadius()));
      values.put("enableAgenticPlanner", String.valueOf(settings.isEnableAgenticPlanner()));
      values.put("enableLookAtOwnerIdle", String.valueOf(settings.isEnableLookAtOwnerIdle()));
      values.put("agenticPlannerRagTopK", String.valueOf(settings.getAgenticPlannerRagTopK()));
      values.put("agenticPlannerMaxSteps", String.valueOf(settings.getAgenticPlannerMaxSteps()));
      values.put("agenticPlannerFallbackGather", String.valueOf(settings.isAgenticPlannerFallbackGather()));
      values.put("gatherLooseItemsRadius", whole(settings.getGatherLooseItemsRadius()));
      values.put("gatherLooseItemsMaxItems", String.valueOf(settings.getGatherLooseItemsMaxItems()));
      values.put("gatherLooseItemsTimeoutSeconds", whole(settings.getGatherLooseItemsTimeoutSeconds()));
      values.put("gatherLooseItemsSettleSeconds", milliseconds(settings.getGatherLooseItemsSettleSeconds()));
      values.put("agenticStorageSearchRadius", whole(settings.getAgenticStorageSearchRadius()));
      values.put("agenticStoragePlacementRadius", whole(settings.getAgenticStoragePlacementRadius()));
      values.put("agenticMaxTravelRadius", whole(settings.getAgenticMaxTravelRadius()));
      values.put("agenticStorageAllowPlacement", String.valueOf(settings.isAgenticStorageAllowPlacement()));
      values.put("agenticStoragePreferExisting", String.valueOf(settings.isAgenticStoragePreferExisting()));
      values.put("agenticStorageAvoidLootChests", String.valueOf(settings.isAgenticStorageAvoidLootChests()));
      values.put("agenticStorageResolveTimeoutSeconds", whole(settings.getAgenticStorageResolveTimeoutSeconds()));
      values.put("agenticDepositTimeoutSeconds", whole(settings.getAgenticDepositTimeoutSeconds()));
      values.put("agenticDepositKeepTools", String.valueOf(settings.isAgenticDepositKeepTools()));
      values.put("agenticEnableLabelChest", String.valueOf(settings.isAgenticEnableLabelChest()));
      values.put("agenticLabelTimeoutSeconds", whole(settings.getAgenticLabelTimeoutSeconds()));
      values.put("agenticLabelUseModelText", String.valueOf(settings.isAgenticLabelUseModelText()));
      values.put("enableDeferredSmelt", String.valueOf(settings.isEnableDeferredSmelt()));
      values.put("deferredSmeltMaxBatch", String.valueOf(settings.getDeferredSmeltMaxBatch()));
      values.put("enableSmithing", String.valueOf(settings.isEnableSmithing()));
      values.put("smithMaxBatch", String.valueOf(settings.getSmithMaxBatch()));
      values.put("deferredSmeltStallPolls", String.valueOf(settings.getDeferredSmeltStallPolls()));
      values.put("deferredSmeltTimeoutSeconds", String.valueOf(settings.getDeferredSmeltTimeoutSeconds()));
      values.put("aggregateCountDropRadius", whole(settings.getAggregateCountDropRadius()));
      values.put("aggregateLocalSourceBlockRadius", whole(settings.getAggregateLocalSourceBlockRadius()));
      values.put("mineCollectSettleSeconds", milliseconds(settings.getMineCollectSettleSeconds()));
      values.put("wanderBoundDefaultSeconds", String.valueOf(settings.getWanderBoundDefaultSeconds()));
      values.put("wanderNoImprovementSeconds", String.valueOf(settings.getWanderNoImprovementSeconds()));
      values.put("ellieGpsEnabled", String.valueOf(settings.getEllieGpsEnabled()));
      values.put("ellieGpsSnapshotSlotThreshold", String.valueOf(settings.getEllieGpsSnapshotSlotThreshold()));
      values.put("ellieGpsUseModelDescription", String.valueOf(settings.getEllieGpsUseModelDescription()));
      values.put("throwawayItems", formatItems(settings.adminThrowawayItems()));
      values.put("dontThrowAwayCustomNameItems", String.valueOf(settings.getDontThrowAwayCustomNameItems()));
      values.put("dontThrowAwayEnchantedItems", String.valueOf(settings.getDontThrowAwayEnchantedItems()));
      values.put("throwAwayUnusedItems", String.valueOf(settings.shouldThrowawayUnusedItems()));
      values.put("importantItems", formatItems(settings.adminImportantItems()));
      values.put("limitFuelsToSupportedFuels", String.valueOf(settings.shouldLimitFuelsToSupportedFuels()));
      values.put("supportedFuels", formatItems(settings.adminSupportedFuels()));
      BlockPos home = settings.getHomeBasePosition();
      values.put("homeBasePosition", home.getX() + "," + home.getY() + "," + home.getZ());
      return Collections.unmodifiableMap(values);
   }

   private static PlayerEngineSettingsAdminService.CandidateMutation parseMutation(String key, String value, BlockPos currentPosition) {
      if (BOOLEAN_KEYS.contains(key)) {
         Boolean parsed = parseBoolean(value);
         return parsed == null ? null : settings -> settings.setAdminBoolean(key, parsed);
      }

      PlayerEngineSettingsAdminService.IntRange range = integerRange(key);
      if (range != null) {
         Integer parsed = parseInteger(value);
         return parsed != null && range.contains(parsed) ? settings -> settings.setAdminInt(key, parsed) : null;
      }

      range = wholeNumberRange(key);
      if (range != null) {
         Integer parsed = parseInteger(value);
         return parsed != null && range.contains(parsed) ? settings -> settings.setAdminWholeNumber(key, parsed) : null;
      }

      range = millisecondRange(key);
      if (range == null) {
         if (ITEM_LIST_KEYS.contains(key)) {
            List<Item> parsed = parseItems(value);
            return parsed == null ? null : settings -> settings.setAdminItems(key, parsed);
         }

         return switch (key) {
            case "commandPrefix" -> isValidCommandPrefix(value) ? settings -> settings.setAdminString(key, value) : null;
            case "idleCommand" -> isValidIdleCommand(value) ? settings -> settings.setAdminString(key, value) : null;
            case "forceFieldStrategy" -> forceFieldMutation(value);
            case "overworldToNetherBehaviour" -> dimensionMutation(value);
            case "homeBasePosition" -> currentPosition == null ? null : settings -> settings.setAdminHomeBasePosition(currentPosition);
            default -> null;
         };
      } else {
         Integer parsed = parseInteger(value);
         return parsed != null && range.contains(parsed) ? settings -> settings.setAdminMilliseconds(key, parsed) : null;
      }
   }

   private static PlayerEngineSettingsAdminService.CandidateMutation forceFieldMutation(String value) {
      try {
         KillAura.Strategy strategy = KillAura.Strategy.valueOf(value);
         return settings -> settings.setAdminForceFieldStrategy(strategy);
      } catch (IllegalArgumentException | NullPointerException e) {
         return null;
      }
   }

   private static PlayerEngineSettingsAdminService.CandidateMutation dimensionMutation(String value) {
      try {
         DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR behavior = DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR.valueOf(value);
         return settings -> settings.setAdminOverworldToNetherBehaviour(behavior);
      } catch (IllegalArgumentException | NullPointerException e) {
         return null;
      }
   }

   private static boolean isSupportedKey(String key) {
      if (key == null) {
         return false;
      }

      if (BOOLEAN_KEYS.contains(key)
         || ITEM_LIST_KEYS.contains(key)
         || integerRange(key) != null
         || wholeNumberRange(key) != null
         || millisecondRange(key) != null) {
         return true;
      }

      switch (key) {
         case "commandPrefix":
         case "idleCommand":
         case "forceFieldStrategy":
         case "overworldToNetherBehaviour":
         case "homeBasePosition":
            return true;
         default:
            return false;
      }
   }

   private static PlayerEngineSettingsAdminService.IntRange integerRange(String key) {
      return switch (key) {
         case "minimumFoodAllowed", "foodUnitsToCollect" -> new PlayerEngineSettingsAdminService.IntRange(0, 64);
         case "netherFastTravelWalkingRange" -> new PlayerEngineSettingsAdminService.IntRange(0, 100000);
         case "agenticPlannerRagTopK" -> new PlayerEngineSettingsAdminService.IntRange(1, 20);
         case "agenticPlannerMaxSteps" -> new PlayerEngineSettingsAdminService.IntRange(1, 4);
         case "gatherLooseItemsMaxItems" -> new PlayerEngineSettingsAdminService.IntRange(1, 1024);
         case "deferredSmeltMaxBatch", "smithMaxBatch" -> new PlayerEngineSettingsAdminService.IntRange(1, 512);
         case "deferredSmeltStallPolls" -> new PlayerEngineSettingsAdminService.IntRange(20, 2000);
         case "deferredSmeltTimeoutSeconds" -> new PlayerEngineSettingsAdminService.IntRange(60, 3600);
         case "wanderBoundDefaultSeconds" -> new PlayerEngineSettingsAdminService.IntRange(0, 600);
         case "wanderNoImprovementSeconds" -> new PlayerEngineSettingsAdminService.IntRange(0, 300);
         case "ellieGpsSnapshotSlotThreshold" -> new PlayerEngineSettingsAdminService.IntRange(1, 54);
         default -> null;
      };
   }

   private static PlayerEngineSettingsAdminService.IntRange wholeNumberRange(String key) {
      return switch (key) {
         case "resourcePickupDropRange" -> new PlayerEngineSettingsAdminService.IntRange(0, 128);
         case "resourceChestLocateRange", "resourceMineRange" -> new PlayerEngineSettingsAdminService.IntRange(0, 4096);
         case "entityReachRange" -> new PlayerEngineSettingsAdminService.IntRange(1, 6);
         case "craftingTableReuseRadius" -> new PlayerEngineSettingsAdminService.IntRange(4, 128);
         case "gatherLooseItemsRadius" -> new PlayerEngineSettingsAdminService.IntRange(2, 64);
         case "gatherLooseItemsTimeoutSeconds" -> new PlayerEngineSettingsAdminService.IntRange(5, 300);
         case "agenticStorageSearchRadius" -> new PlayerEngineSettingsAdminService.IntRange(4, 64);
         case "agenticStoragePlacementRadius" -> new PlayerEngineSettingsAdminService.IntRange(2, 8);
         case "agenticMaxTravelRadius" -> new PlayerEngineSettingsAdminService.IntRange(16, 160);
         case "agenticStorageResolveTimeoutSeconds", "agenticDepositTimeoutSeconds" -> new PlayerEngineSettingsAdminService.IntRange(10, 300);
         case "agenticLabelTimeoutSeconds" -> new PlayerEngineSettingsAdminService.IntRange(10, 180);
         case "aggregateCountDropRadius" -> new PlayerEngineSettingsAdminService.IntRange(0, 64);
         case "aggregateLocalSourceBlockRadius" -> new PlayerEngineSettingsAdminService.IntRange(0, 128);
         default -> null;
      };
   }

   private static PlayerEngineSettingsAdminService.IntRange millisecondRange(String key) {
      return switch (key) {
         case "craftDelaySeconds" -> new PlayerEngineSettingsAdminService.IntRange(0, 5000);
         case "craftTableLookHoldSeconds" -> new PlayerEngineSettingsAdminService.IntRange(0, 3000);
         case "gatherLooseItemsSettleSeconds" -> new PlayerEngineSettingsAdminService.IntRange(500, 20000);
         case "mineCollectSettleSeconds" -> new PlayerEngineSettingsAdminService.IntRange(0, 5000);
         default -> null;
      };
   }

   private static Boolean parseBoolean(String value) {
      if ("true".equals(value)) {
         return Boolean.TRUE;
      } else {
         return "false".equals(value) ? Boolean.FALSE : null;
      }
   }

   private static Integer parseInteger(String value) {
      if (value != null && !value.isEmpty()) {
         for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') {
               return null;
            }
         }

         try {
            return Integer.valueOf(value);
         } catch (NumberFormatException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isValidCommandPrefix(String value) {
      return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 8
         ? value.codePoints().noneMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp))
         : false;
   }

   private static boolean isValidIdleCommand(String value) {
      return value != null && value.codePointCount(0, value.length()) <= 128 && value.codePoints().noneMatch(Character::isISOControl);
   }

   private static List<Item> parseItems(String value) {
      if (value != null && value.length() <= 3500) {
         if (value.isBlank()) {
            return List.of();
         }

         LinkedHashSet<Item> items = new LinkedHashSet<>();
         String[] var2 = value.split(",", -1);
         int var3 = var2.length;
         int var4 = 0;

         while (var4 < var3) {
            String part = var2[var4];
            String token = part.trim();
            if (token.isEmpty()) {
               return null;
            }

            ResourceLocation id = ResourceLocation.tryParse(token);
            if (id != null && id.toString().equals(token) && BuiltInRegistries.ITEM.containsKey(id)) {
               Item item = (Item)BuiltInRegistries.ITEM.get(id);
               if (item != null && item != Items.AIR) {
                  items.add(item);
                  if (items.size() > 64) {
                     return null;
                  }

                  var4++;
                  continue;
               }

               return null;
            }

            return null;
         }

         return List.copyOf(items);
      } else {
         return null;
      }
   }

   private static String formatItems(List<Item> items) {
      return items.stream()
         .filter(item -> item != null && item != Items.AIR)
         .map(item -> BuiltInRegistries.ITEM.getResourceKey(item).map(key -> key.location()).orElse(null))
         .filter(id -> id != null)
         .<CharSequence>map(ResourceLocation::toString)
         .distinct()
         .limit(64L)
         .collect(Collectors.joining(","));
   }

   private static String whole(double value) {
      return String.valueOf(Math.round(value));
   }

   private static String milliseconds(double seconds) {
      return String.valueOf(Math.round(seconds * 1000.0));
   }

   @FunctionalInterface
   private interface CandidateMutation {
      void apply(PlayerEngineSettings var1);
   }

   private record IntRange(int min, int max) {
      boolean contains(int value) {
         return value >= this.min && value <= this.max;
      }
   }

   public record SnapshotResult(Map<String, String> values, boolean loadFailed, PlayerEngineController.SettingsLiveState liveState) {
   }

   public enum UpdateResult {
      OK,
      INVALID_VALUE,
      UNSUPPORTED_SETTING,
      SAVE_FAILED,
      SAVED_LIVE_PENDING,
      SAVED_LIVE_FAILED;
   }
}
