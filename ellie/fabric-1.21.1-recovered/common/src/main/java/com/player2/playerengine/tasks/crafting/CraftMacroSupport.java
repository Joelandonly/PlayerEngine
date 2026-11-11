package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccess;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.WoodType;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.MaterialAvailability;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;

public final class CraftMacroSupport {
   private static final RecipeAccess RECIPE_ACCESS = new RecipeAccessImpl();
   public static final int SIGN_PLANK_NEED = 6;

   private CraftMacroSupport() {
   }

   public static boolean isMacroEnabled(PlayerEngineController mod) {
      return mod.getModSettings().isFastCraftMacrosEnabled();
   }

   public static boolean isSupportedItem(Item item) {
      if (item != Items.CHEST && item != Items.CRAFTING_TABLE && item != Items.STICK) {
         return Arrays.asList(ItemHelper.PLANKS).contains(item) ? true : Arrays.asList(ItemHelper.WOOD_SIGN).contains(item);
      } else {
         return true;
      }
   }

   public static boolean isSupportedTarget(PlayerEngineController mod, ItemTarget target) {
      Optional<Item> outputItemOpt = resolveSingleOutputItem(mod, target);
      if (outputItemOpt.isEmpty()) {
         return false;
      }

      MinecraftServer server = mod.getPlayer() != null ? mod.getPlayer().getServer() : null;
      if (server == null) {
         return false;
      }

      RecipeManager recipeManager = server.getRecipeManager();
      RegistryAccess registries = server.registryAccess();
      return RECIPE_ACCESS.hasRecipe(recipeManager, outputItemOpt.get(), registries);
   }

   public static Optional<Item> resolveSingleOutputItem(PlayerEngineController mod, ItemTarget target) {
      if (target.getMatches().length == 1) {
         return Optional.of(target.getMatches()[0]);
      }

      if (target.isCatalogueItem()) {
         String name = target.getCatalogueName();
         if (name.equals("sign")) {
            return Optional.of(pickSignSpecies(mod).sign);
         }

         if (name.equals("planks")) {
            return Optional.of(Items.OAK_PLANKS);
         }

         if (name.equals("chest")) {
            return Optional.of(Items.CHEST);
         }

         if (name.equals("crafting_table")) {
            return Optional.of(Items.CRAFTING_TABLE);
         }

         if (name.equals("stick")) {
            return Optional.of(Items.STICK);
         }

         if (name.endsWith("_sign") && TaskCatalogue.taskExists(name)) {
            return Optional.of(TaskCatalogue.getItemMatches(name)[0]);
         }

         if (name.endsWith("_planks") && TaskCatalogue.taskExists(name)) {
            return Optional.of(TaskCatalogue.getItemMatches(name)[0]);
         }
      }

      return Optional.empty();
   }

   public static CraftMacroSupport.SignRequestAdjustment adjustSignRequestForFunding(PlayerEngineController mod, ItemTarget target) {
      Item requestedSign = explicitSignItem(target);
      if (requestedSign == null) {
         return new CraftMacroSupport.SignRequestAdjustment(target, null);
      }

      ItemHelper.WoodItems requested = woodForSign(requestedSign);
      if (requested == null) {
         return new CraftMacroSupport.SignRequestAdjustment(target, null);
      }

      List<CraftMacroSupport.SpeciesFunding> fundings = computeFundings(mod);
      CraftMacroSupport.SpeciesFunding requestedFunding = null;

      for (CraftMacroSupport.SpeciesFunding f : fundings) {
         if (f.wood() == requested) {
            requestedFunding = f;
            break;
         }
      }

      if (requestedFunding == null) {
         requestedFunding = fundingFor(mod, requested);
      }

      String reqName = ItemHelper.stripItemName(requestedSign);
      if (requestedFunding.fundable()) {
         Debug.logInternal("[[CRAFT-RESOLVER-DIAG]] SPECIES-PICK explicit=" + reqName + " " + describeFunding(requestedFunding) + " -> honored");
         return new CraftMacroSupport.SignRequestAdjustment(target, null);
      } else {
         CraftMacroSupport.SpeciesFunding best = selectBestFundable(mod, fundings);
         if (best != null && best.wood() != requested) {
            String subName = ItemHelper.stripItemName(best.wood().sign);
            Debug.logInternal(
               "[[CRAFT-RESOLVER-DIAG]] SPECIES-PICK explicit="
                  + reqName
                  + " "
                  + describeFunding(requestedFunding)
                  + " -> NOT fundable; substituted="
                  + subName
                  + " scores="
                  + describeScores(fundings)
            );
            String species = reqName.endsWith("_sign") ? reqName.substring(0, reqName.length() - "_sign".length()) : reqName;
            String notice = species + " wood not available nearby; making " + subName + " instead of " + reqName;
            return new CraftMacroSupport.SignRequestAdjustment(new ItemTarget(best.wood().sign, target.getTargetCount()), notice);
         } else {
            Debug.logInternal(
               "[[CRAFT-RESOLVER-DIAG]] SPECIES-PICK explicit="
                  + reqName
                  + " "
                  + describeFunding(requestedFunding)
                  + " -> NOT fundable; no fundable species, honoring as-is (bounded terminate downstream) scores="
                  + describeScores(fundings)
            );
            return new CraftMacroSupport.SignRequestAdjustment(target, null);
         }
      }
   }

   public static ItemHelper.WoodItems pickSignSpecies(PlayerEngineController mod) {
      List<CraftMacroSupport.SpeciesFunding> fundings = computeFundings(mod);
      CraftMacroSupport.SpeciesFunding best = selectBestFundable(mod, fundings);
      if (best != null) {
         Debug.logInternal(
            "[[CRAFT-RESOLVER-DIAG]] SPECIES-PICK bare-sign chosen="
               + ItemHelper.stripItemName(best.wood().sign)
               + " "
               + describeFunding(best)
               + " scores="
               + describeScores(fundings)
         );
         return best.wood();
      }

      CraftMacroSupport.SpeciesFunding fallback = null;

      for (CraftMacroSupport.SpeciesFunding f : fundings) {
         if (f.total() > 0 && (fallback == null || f.total() > fallback.total())) {
            fallback = f;
         }
      }

      Debug.logInternal(
         "[[CRAFT-RESOLVER-DIAG]] SPECIES-PICK bare-sign no fundable species; fallback="
            + (fallback != null ? ItemHelper.stripItemName(fallback.wood().sign) : "oak_sign(all-zero)")
            + " scores="
            + describeScores(fundings)
      );
      return fallback != null ? fallback.wood() : ItemHelper.getWoodItems(WoodType.OAK);
   }

   private static CraftMacroSupport.SpeciesFunding selectBestFundable(PlayerEngineController mod, List<CraftMacroSupport.SpeciesFunding> fundings) {
      CraftMacroSupport.SpeciesFunding bestHeld = null;

      for (CraftMacroSupport.SpeciesFunding f : fundings) {
         if (f.fundable() && f.heldScore() > 0 && (bestHeld == null || f.heldScore() > bestHeld.heldScore())) {
            bestHeld = f;
         }
      }

      if (bestHeld != null) {
         return bestHeld;
      }

      int bestTotal = 0;
      List<CraftMacroSupport.SpeciesFunding> tied = new ArrayList<>();

      for (CraftMacroSupport.SpeciesFunding f : fundings) {
         if (f.fundable()) {
            if (f.total() > bestTotal) {
               bestTotal = f.total();
               tied.clear();
               tied.add(f);
            } else if (f.total() == bestTotal) {
               tied.add(f);
            }
         }
      }

      if (tied.isEmpty()) {
         return null;
      } else {
         return tied.size() == 1 ? tied.get(0) : nearestLogSpecies(mod, tied);
      }
   }

   private static CraftMacroSupport.SpeciesFunding nearestLogSpecies(PlayerEngineController mod, List<CraftMacroSupport.SpeciesFunding> tied) {
      if (mod.getWorld() == null) {
         return tied.get(0);
      }

      List<CraftMacroSupport.SpeciesFunding> candidates = new ArrayList<>();
      List<Block> logBlocks = new ArrayList<>();

      for (CraftMacroSupport.SpeciesFunding f : tied) {
         Item log = f.wood().log;
         if (log != null) {
            Block block = Block.byItem(log);
            if (block != Blocks.AIR) {
               candidates.add(f);
               logBlocks.add(block);
            }
         }
      }

      if (candidates.isEmpty()) {
         return tied.get(0);
      }

      Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(logBlocks.toArray(new Block[0]));
      if (nearest.isPresent()) {
         Block found = mod.getWorld().getBlockState(nearest.get()).getBlock();

         for (int i = 0; i < logBlocks.size(); i++) {
            if (logBlocks.get(i) == found) {
               return candidates.get(i);
            }
         }
      }

      return tied.get(0);
   }

   private static List<CraftMacroSupport.SpeciesFunding> computeFundings(PlayerEngineController mod) {
      List<CraftMacroSupport.SpeciesFunding> fundings = new ArrayList<>();
      ItemHelper.WoodItems oak = ItemHelper.getWoodItems(WoodType.OAK);
      if (oak != null && oak.sign != null) {
         fundings.add(fundingFor(mod, oak));
      }

      for (WoodType type : WoodType.values()) {
         ItemHelper.WoodItems w = ItemHelper.getWoodItems(type);
         if (w != null && w.sign != null && w != oak) {
            fundings.add(fundingFor(mod, w));
         }
      }

      return fundings;
   }

   private static CraftMacroSupport.SpeciesFunding fundingFor(PlayerEngineController mod, ItemHelper.WoodItems w) {
      return new CraftMacroSupport.SpeciesFunding(w, heldWoodScore(mod, w), mineableLogCount(mod, w));
   }

   private static int mineableLogCount(PlayerEngineController mod, ItemHelper.WoodItems w) {
      if (w.log != null && mod.getPlayer() != null && mod.getWorld() != null) {
         Vec3 origin = mod.getPlayer().position();
         double dropRadius = mod.getModSettings().getAggregateCountDropRadius();
         double localSourceBlockRadius = Math.min(mod.getModSettings().getAggregateLocalSourceBlockRadius(), mod.getModSettings().getAgenticMaxTravelRadius());
         return MaterialAvailability.count(mod, new ItemTarget(w.log, 1), origin, dropRadius, localSourceBlockRadius).mineableYield();
      } else {
         return 0;
      }
   }

   private static Item explicitSignItem(ItemTarget target) {
      if (target.isCatalogueItem() && target.getCatalogueName().equals("sign")) {
         return null;
      }

      if (target.getMatches().length == 1 && Arrays.asList(ItemHelper.WOOD_SIGN).contains(target.getMatches()[0])) {
         return target.getMatches()[0];
      }

      if (target.isCatalogueItem()) {
         String name = target.getCatalogueName();
         if (name.endsWith("_sign") && TaskCatalogue.taskExists(name)) {
            Item[] matches = TaskCatalogue.getItemMatches(name);
            if (matches != null && matches.length >= 1 && Arrays.asList(ItemHelper.WOOD_SIGN).contains(matches[0])) {
               return matches[0];
            }
         }
      }

      return null;
   }

   private static ItemHelper.WoodItems woodForSign(Item sign) {
      for (WoodType type : WoodType.values()) {
         ItemHelper.WoodItems w = ItemHelper.getWoodItems(type);
         if (w != null && w.sign == sign) {
            return w;
         }
      }

      return null;
   }

   private static String describeFunding(CraftMacroSupport.SpeciesFunding f) {
      return "(held=" + f.heldScore() + " mineableLogs=" + f.mineableLogs() + " total=" + f.total() + " need=6)";
   }

   private static String describeScores(List<CraftMacroSupport.SpeciesFunding> fundings) {
      StringBuilder sb = new StringBuilder("[");

      for (CraftMacroSupport.SpeciesFunding f : fundings) {
         if (f.total() > 0) {
            if (sb.length() > 1) {
               sb.append(", ");
            }

            sb.append(ItemHelper.stripItemName(f.wood().sign))
               .append(":h")
               .append(f.heldScore())
               .append("+4*m")
               .append(f.mineableLogs())
               .append("=")
               .append(f.total());
         }
      }

      if (sb.length() == 1) {
         sb.append("all-zero");
      }

      return sb.append("]").toString();
   }

   private static int heldWoodScore(PlayerEngineController mod, ItemHelper.WoodItems w) {
      int logEquivalents = heldCount(mod, w.log) + heldCount(mod, w.strippedLog) + heldCount(mod, w.wood) + heldCount(mod, w.strippedWood);
      return heldCount(mod, w.planks) + 4 * logEquivalents;
   }

   private static int heldCount(PlayerEngineController mod, Item item) {
      return item == null ? 0 : mod.getItemStorage().getItemCount(item);
   }

   public record SignRequestAdjustment(ItemTarget target, String notice) {
   }

   private record SpeciesFunding(ItemHelper.WoodItems wood, int heldScore, int mineableLogs) {
      int total() {
         return this.heldScore + 4 * this.mineableLogs;
      }

      boolean fundable() {
         return this.total() >= 6;
      }
   }
}
