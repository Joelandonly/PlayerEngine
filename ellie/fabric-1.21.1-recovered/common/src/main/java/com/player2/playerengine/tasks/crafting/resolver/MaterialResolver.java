package com.player2.playerengine.tasks.crafting.resolver;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccess;
import com.player2.playerengine.tasks.crafting.CraftMacroStep;
import com.player2.playerengine.tasks.crafting.CraftMacroStepKind;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class MaterialResolver {
   private static final int MAX_DEPTH = 8;
   private final IngredientInspector inspector;
   private final RecipeAccess recipes;
   private final CookingRecipeAccess cooking;
   @Nullable
   private MaterialReservationService ledger;
   private String lastArithmeticDiagSig = "";

   public MaterialResolver(IngredientInspector inspector, RecipeAccess recipes, CookingRecipeAccess cooking) {
      this.inspector = inspector;
      this.recipes = recipes;
      this.cooking = cooking;
   }

   public void setLedger(@Nullable MaterialReservationService ledger) {
      this.ledger = ledger;
   }

   public ResolverResult resolve(PlayerEngineController controller, Item target, int count) {
      if (target != null && count > 0) {
         MinecraftServer server = controller.getPlayer() != null ? controller.getPlayer().getServer() : null;
         if (server == null) {
            return new ResolverResult(
               ResolverResult.Status.UNOBTAINABLE, List.of(), List.of(), count, "Cannot resolve " + itemName(target) + ": no server context to read recipes."
            );
         }

         RecipeManager mgr = server.getRecipeManager();
         RegistryAccess registries = server.registryAccess();
         int have = controller.getItemStorage().getItemCount(target);
         if (have >= count) {
            return new ResolverResult(ResolverResult.Status.READY, List.of(), List.of(), 0, "");
         }

         List<CraftMacroStep> steps = new ArrayList<>();
         List<ItemTarget> external = new ArrayList<>();
         List<Integer> externalCredited = new ArrayList<>();
         List<String> failures = new ArrayList<>();
         Map<Item, Integer> reserved = new HashMap<>();
         Map<Item, Integer> plannedDelta = new HashMap<>();
         Map<Item, Integer> surplus = new HashMap<>();
         List<String> passDiag = new ArrayList<>();
         Set<Item> resolving = new HashSet<>();
         ResolverResult.Status status = this.resolveInto(
            controller,
            mgr,
            registries,
            target,
            count,
            null,
            0,
            steps,
            external,
            externalCredited,
            failures,
            reserved,
            plannedDelta,
            surplus,
            passDiag,
            false,
            resolving,
            0
         );
         MaterialResolver.CoalescedExternals merged = coalesceExternals(external, externalCredited);
         int deficit = 0;

         for (CraftMacroStep step : steps) {
            deficit += Math.max(0, step.craftsNeeded());
         }

         for (ItemTarget t : merged.targets()) {
            deficit += Math.max(0, t.getTargetCount());
         }

         String header = "resolve target=" + itemName(target) + " x" + count;
         if (status == ResolverResult.Status.UNOBTAINABLE) {
            String reason = unobtainableReason(itemName(target), failures, merged.targets());
            logResolvedPlan(controller, header, ResolverResult.Status.UNOBTAINABLE, steps, merged.targets(), merged.creditedHeld(), deficit);
            this.emitArithmeticDiag(passDiag);
            return new ResolverResult(ResolverResult.Status.UNOBTAINABLE, steps, merged.targets(), deficit, reason);
         } else if (steps.isEmpty() && merged.targets().isEmpty()) {
            logResolvedPlan(controller, header, ResolverResult.Status.READY, List.of(), List.of(), List.of(), 0);
            this.emitArithmeticDiag(passDiag);
            return new ResolverResult(ResolverResult.Status.READY, List.of(), List.of(), 0, "");
         } else {
            logResolvedPlan(controller, header, ResolverResult.Status.NEEDS_WORK, steps, merged.targets(), merged.creditedHeld(), deficit);
            this.emitArithmeticDiag(passDiag);
            return new ResolverResult(ResolverResult.Status.NEEDS_WORK, steps, merged.targets(), deficit, "");
         }
      } else {
         return new ResolverResult(ResolverResult.Status.READY, List.of(), List.of(), 0, "");
      }
   }

   public ResolverResult resolveBudget(PlayerEngineController controller, List<MaterialResolver.BudgetTarget> targets) {
      if (targets != null && !targets.isEmpty()) {
         MinecraftServer server = controller.getPlayer() != null ? controller.getPlayer().getServer() : null;
         if (server == null) {
            return new ResolverResult(
               ResolverResult.Status.UNOBTAINABLE,
               List.of(),
               List.of(),
               0,
               "Cannot obtain materials for " + describeBudgetTargets(targets) + ": no server context to read recipes."
            );
         }

         RecipeManager mgr = server.getRecipeManager();
         RegistryAccess registries = server.registryAccess();
         List<CraftMacroStep> steps = new ArrayList<>();
         List<ItemTarget> external = new ArrayList<>();
         List<Integer> externalCredited = new ArrayList<>();
         List<String> failures = new ArrayList<>();
         Map<Item, Integer> reserved = new HashMap<>();
         Map<Item, Integer> plannedDelta = new HashMap<>();
         Map<Item, Integer> surplus = new HashMap<>();
         List<String> passDiag = new ArrayList<>();
         ResolverResult.Status worst = ResolverResult.Status.READY;

         for (MaterialResolver.BudgetTarget bt : targets) {
            if (bt != null && bt.item() != null && bt.count() > 0) {
               int free = this.freeCount(controller, reserved, bt.item());
               if (free >= bt.count()) {
                  reserve(reserved, bt.item(), bt.count());
               } else {
                  Set<Item> resolving = new HashSet<>();
                  ResolverResult.Status sub = this.resolveInto(
                     controller,
                     mgr,
                     registries,
                     bt.item(),
                     bt.count(),
                     null,
                     0,
                     steps,
                     external,
                     externalCredited,
                     failures,
                     reserved,
                     plannedDelta,
                     surplus,
                     passDiag,
                     false,
                     resolving,
                     0
                  );
                  if (sub == ResolverResult.Status.UNOBTAINABLE) {
                     worst = ResolverResult.Status.UNOBTAINABLE;
                  } else if (worst != ResolverResult.Status.UNOBTAINABLE) {
                     worst = ResolverResult.Status.NEEDS_WORK;
                  }
               }
            }
         }

         MaterialResolver.CoalescedExternals merged = coalesceExternals(external, externalCredited);
         int deficit = 0;

         for (CraftMacroStep step : steps) {
            deficit += Math.max(0, step.craftsNeeded());
         }

         for (ItemTarget t : merged.targets()) {
            deficit += Math.max(0, t.getTargetCount());
         }

         String header = "resolveBudget requirements=" + describeBudgetTargetList(targets);
         if (worst == ResolverResult.Status.UNOBTAINABLE) {
            String reason = unobtainableReason(describeBudgetTargets(targets), failures, merged.targets());
            logResolvedPlan(controller, header, ResolverResult.Status.UNOBTAINABLE, steps, merged.targets(), merged.creditedHeld(), deficit);
            this.emitArithmeticDiag(passDiag);
            return new ResolverResult(ResolverResult.Status.UNOBTAINABLE, steps, merged.targets(), deficit, reason);
         } else if (steps.isEmpty() && merged.targets().isEmpty()) {
            logResolvedPlan(controller, header, ResolverResult.Status.READY, List.of(), List.of(), List.of(), 0);
            this.emitArithmeticDiag(passDiag);
            return new ResolverResult(ResolverResult.Status.READY, List.of(), List.of(), 0, "");
         } else {
            logResolvedPlan(controller, header, ResolverResult.Status.NEEDS_WORK, steps, merged.targets(), merged.creditedHeld(), deficit);
            this.emitArithmeticDiag(passDiag);
            return new ResolverResult(ResolverResult.Status.NEEDS_WORK, steps, merged.targets(), deficit, "");
         }
      } else {
         return new ResolverResult(ResolverResult.Status.READY, List.of(), List.of(), 0, "");
      }
   }

   private static String unobtainableReason(String targetsDesc, List<String> failures, List<ItemTarget> owedExternals) {
      String owed = describeOwedExternals(owedExternals);
      String detail;
      if (!failures.isEmpty()) {
         detail = String.join("; ", failures);
      } else if (!owed.isEmpty()) {
         detail = "need " + owed + " and no reachable source";
      } else {
         detail = "a required ingredient has no obtainable source";
      }

      return "Cannot obtain materials for " + targetsDesc + ": " + detail + ".";
   }

   private static String describeOwedExternals(List<ItemTarget> external) {
      StringBuilder sb = new StringBuilder();

      for (ItemTarget t : external) {
         if (!ItemTarget.nullOrEmpty(t) && t.getTargetCount() > 0) {
            if (sb.length() > 0) {
               sb.append(" and ");
            }

            sb.append(t.getTargetCount()).append(" more ").append(externalDisplayName(t));
         }
      }

      return sb.toString();
   }

   private static String externalDisplayName(ItemTarget t) {
      if (t.isCatalogueItem() && t.getCatalogueName() != null) {
         return t.getCatalogueName();
      }

      Item[] m = t.getMatches();
      return m.length > 0 && m[0] != null ? itemName(m[0]) : "unknown";
   }

   private static String describeBudgetTargets(List<MaterialResolver.BudgetTarget> targets) {
      List<String> names = new ArrayList<>();

      for (MaterialResolver.BudgetTarget bt : targets) {
         if (bt != null && bt.item() != null && bt.count() > 0) {
            names.add(itemName(bt.item()));
         }
      }

      if (names.isEmpty()) {
         return "the requested items";
      } else {
         return names.size() == 2 && "crafting_table".equals(names.get(1)) ? names.get(0) + " and its crafting table" : String.join(" and ", names);
      }
   }

   private static String describeBudgetTargetList(List<MaterialResolver.BudgetTarget> targets) {
      StringBuilder sb = new StringBuilder("[");

      for (MaterialResolver.BudgetTarget bt : targets) {
         if (bt != null && bt.item() != null) {
            if (sb.length() > 1) {
               sb.append(", ");
            }

            sb.append(itemName(bt.item())).append(" x").append(bt.count());
         }
      }

      return sb.append(']').toString();
   }

   private static MaterialResolver.CoalescedExternals coalesceExternals(List<ItemTarget> external, List<Integer> credited) {
      LinkedHashMap<String, ItemTarget> byKey = new LinkedHashMap<>();
      LinkedHashMap<String, Integer> sum = new LinkedHashMap<>();
      LinkedHashMap<String, Integer> creditMax = new LinkedHashMap<>();

      for (int i = 0; i < external.size(); i++) {
         ItemTarget t = external.get(i);
         if (!ItemTarget.nullOrEmpty(t) && t.getTargetCount() > 0) {
            String key = externalCoalesceKey(t);
            byKey.putIfAbsent(key, t);
            sum.merge(key, t.getTargetCount(), Integer::sum);
            int c = i < credited.size() ? credited.get(i) : 0;
            creditMax.merge(key, c, Integer::max);
         }
      }

      List<ItemTarget> mergedTargets = new ArrayList<>(byKey.size());
      List<Integer> mergedCredited = new ArrayList<>(byKey.size());

      for (Entry<String, ItemTarget> e : byKey.entrySet()) {
         mergedTargets.add(new ItemTarget(e.getValue(), sum.get(e.getKey())));
         mergedCredited.add(creditMax.get(e.getKey()));
      }

      return new MaterialResolver.CoalescedExternals(mergedTargets, mergedCredited);
   }

   private static String externalCoalesceKey(ItemTarget t) {
      if (t.isCatalogueItem() && t.getCatalogueName() != null) {
         return "cat:" + t.getCatalogueName();
      }

      StringBuilder sb = new StringBuilder("set:");
      Item[] matches = t.getMatches();
      String[] names = new String[matches.length];

      for (int i = 0; i < matches.length; i++) {
         names[i] = matches[i] == null ? "null" : matches[i].toString();
      }

      Arrays.sort(names);

      for (String n : names) {
         sb.append(n).append('|');
      }

      return sb.toString();
   }

   private ResolverResult.Status resolveInto(
      PlayerEngineController controller,
      RecipeManager mgr,
      RegistryAccess registries,
      Item want,
      int need,
      Item[] outputPool,
      int satisfyThreshold,
      List<CraftMacroStep> steps,
      List<ItemTarget> external,
      List<Integer> externalCredited,
      List<String> failures,
      Map<Item, Integer> reserved,
      Map<Item, Integer> plannedDelta,
      Map<Item, Integer> surplus,
      List<String> passDiag,
      boolean agnosticAncestry,
      Set<Item> resolving,
      int depth
   ) {
      int threshold = satisfyThreshold > 0 ? satisfyThreshold : need;
      int free = this.freeCount(controller, reserved, want);
      int shortfall = need - free;
      if (shortfall <= 0) {
         reserve(reserved, want, need);
         return ResolverResult.Status.NEEDS_WORK;
      }

      reserve(reserved, want, free);
      if (depth >= 8) {
         addExternal(controller, external, externalCredited, new Item[]{want}, externalNameFor(want, agnosticAncestry), shortfall);
         return ResolverResult.Status.NEEDS_WORK;
      }

      Optional<CraftingRecipe> recipeOpt = this.selectFundedRecipe(controller, reserved, mgr, registries, want, shortfall, resolving, passDiag);
      if (recipeOpt.isEmpty()) {
         addExternal(controller, external, externalCredited, new Item[]{want}, externalNameFor(want, agnosticAncestry), shortfall);
         return ResolverResult.Status.NEEDS_WORK;
      }

      CraftingRecipe recipe = recipeOpt.get();
      int yield = Math.max(1, this.recipes.outputCountOf(recipe, registries));
      int crafts = ceilDiv(shortfall, yield);
      NonNullList<Ingredient> ingredients = recipe.getIngredients();
      ResolverResult.Status worst = ResolverResult.Status.NEEDS_WORK;
      List<ItemTarget> slotTargets = new ArrayList<>();
      List<String> slotSigs = new ArrayList<>();
      LinkedHashMap<String, MaterialResolver.SlotDemand> distinct = new LinkedHashMap<>();

      for (Ingredient ing : ingredients) {
         if (ing != null && !ing.isEmpty()) {
            IngredientInspector.SlotKind kind = this.inspector.classify(ing);
            Set<Item> accepted = this.inspector.acceptedItems(ing);
            Item[] matchSet = toItemArray(accepted);
            if (matchSet.length == 0) {
               failures.add("a " + itemName(want) + " ingredient has no obtainable item");
               slotTargets.add(ItemTarget.EMPTY);
               slotSigs.add(null);
               worst = ResolverResult.Status.UNOBTAINABLE;
            } else {
               slotTargets.add(new ItemTarget(matchSet, 1));
               String sig = slotSignature(matchSet);
               slotSigs.add(sig);
               MaterialResolver.SlotDemand agg = distinct.get(sig);
               if (agg == null) {
                  distinct.put(sig, new MaterialResolver.SlotDemand(matchSet, kind, crafts));
               } else {
                  agg.demand += crafts;
               }
            }
         } else {
            slotTargets.add(ItemTarget.EMPTY);
            slotSigs.add(null);
         }
      }

      LinkedHashMap<String, Map<Item, Integer>> slotConsumption = new LinkedHashMap<>();
      boolean addedSelf = resolving.add(want);

      try {
         for (Entry<String, MaterialResolver.SlotDemand> e : distinct.entrySet()) {
            MaterialResolver.SlotDemand slot = e.getValue();
            Map<Item, Integer> consumed = new HashMap<>();
            slotConsumption.put(e.getKey(), consumed);
            Item[] pool = slot.kind == IngredientInspector.SlotKind.AGNOSTIC && slot.matchSet.length > 1 ? slot.matchSet : null;
            ResolverResult.Status sub = this.resolvePooledSlot(
               controller,
               mgr,
               registries,
               slot.matchSet,
               pool,
               slot.demand,
               steps,
               external,
               externalCredited,
               failures,
               reserved,
               plannedDelta,
               surplus,
               consumed,
               passDiag,
               agnosticAncestry,
               resolving,
               depth + 1
            );
            if (sub == ResolverResult.Status.UNOBTAINABLE) {
               worst = ResolverResult.Status.UNOBTAINABLE;
            }
         }
      } finally {
         if (addedSelf) {
            resolving.remove(want);
         }
      }

      for (int i = 0; i < slotTargets.size(); i++) {
         String sig = slotSigs.get(i);
         if (sig != null) {
            MaterialResolver.SlotDemand slot = distinct.get(sig);
            Map<Item, Integer> consumed = slotConsumption.get(sig);
            Item[] narrowed = slot != null && consumed != null ? narrowedSlotMatches(slot, consumed) : null;
            if (narrowed != null) {
               slotTargets.set(i, new ItemTarget(narrowed, 1));
            }
         }
      }

      CraftMacroStepKind stepKind = stepKindFor(want, recipe);
      RecipeTarget recipeTarget = toRecipeTarget(want, threshold, yield, slotTargets, recipe, registries);
      steps.add(new CraftMacroStep(stepKind, recipeTarget, crafts, debugLabel(want), outputPool));
      int produced = crafts * yield;
      if (stepKind != CraftMacroStepKind.CRAFT_OUTPUT_IN_TABLE) {
         plannedDelta.merge(want, produced, Integer::sum);

         for (Map<Item, Integer> consumed : slotConsumption.values()) {
            for (Entry<Item, Integer> c : consumed.entrySet()) {
               if (c.getValue() > 0) {
                  plannedDelta.merge(c.getKey(), -c.getValue(), Integer::sum);
               }
            }
         }
      }

      passDiag.add("stepEmit output=" + itemName(want) + " u=" + produced + " targetCount=" + threshold + " kind=" + stepKind);
      int overshoot = produced - shortfall;
      if (overshoot > 0) {
         int total = surplus.merge(want, overshoot, Integer::sum);
         passDiag.add("surplusCredit(" + itemName(want) + ") +" + overshoot + " -> " + total);
      }

      return worst;
   }

   private static Item[] narrowedSlotMatches(MaterialResolver.SlotDemand slot, Map<Item, Integer> consumed) {
      int total = 0;

      for (int n : consumed.values()) {
         total += n;
      }

      if (total < slot.demand) {
         return null;
      }

      List<Item> kept = new ArrayList<>();

      for (Item it : slot.matchSet) {
         if (consumed.getOrDefault(it, 0) > 0) {
            kept.add(it);
         }
      }

      return !kept.isEmpty() && kept.size() != slot.matchSet.length ? kept.toArray(new Item[0]) : null;
   }

   private static String slotSignature(Item[] matchSet) {
      StringBuilder sb = new StringBuilder();

      for (Item it : matchSet) {
         sb.append(it.getDescriptionId()).append('|');
      }

      return sb.toString();
   }

   private ResolverResult.Status resolvePooledSlot(
      PlayerEngineController controller,
      RecipeManager mgr,
      RegistryAccess registries,
      Item[] accepted,
      Item[] outputPool,
      int demand,
      List<CraftMacroStep> steps,
      List<ItemTarget> external,
      List<Integer> externalCredited,
      List<String> failures,
      Map<Item, Integer> reserved,
      Map<Item, Integer> plannedDelta,
      Map<Item, Integer> surplus,
      Map<Item, Integer> slotConsumed,
      List<String> passDiag,
      boolean agnosticAncestry,
      Set<Item> resolving,
      int depth
   ) {
      String poolName = poolLabel(accepted);
      int pooledFree = 0;

      for (Item it : accepted) {
         pooledFree += this.freeCount(controller, reserved, it);
      }

      int shortfall = demand - pooledFree;
      if (shortfall <= 0) {
         this.drainPooledReservation(controller, reserved, accepted, demand, slotConsumed);
         passDiag.add("poolDrain pool=" + poolName + " drained=" + demand);
         return ResolverResult.Status.NEEDS_WORK;
      }

      this.drainPooledReservation(controller, reserved, accepted, pooledFree, slotConsumed);
      if (pooledFree > 0) {
         passDiag.add("poolDrain pool=" + poolName + " drained=" + pooledFree);
      }

      int drawn = 0;

      for (Item v : accepted) {
         if (drawn >= shortfall) {
            break;
         }

         int avail = surplus.getOrDefault(v, 0);
         int take = Math.min(avail, shortfall - drawn);
         if (take > 0) {
            surplus.put(v, avail - take);
            slotConsumed.merge(v, take, Integer::sum);
            drawn += take;
         }
      }

      if (drawn > 0) {
         passDiag.add("surplusDraw pool=" + poolName + " drawn=" + drawn);
         shortfall -= drawn;
         if (shortfall <= 0) {
            return ResolverResult.Status.NEEDS_WORK;
         }
      }

      if (depth >= 8) {
         addExternal(controller, external, externalCredited, accepted, externalNameForPool(accepted, agnosticAncestry), shortfall);
         return ResolverResult.Status.NEEDS_WORK;
      }

      for (Item it : accepted) {
         if (!this.hasNonCircularRecipe(mgr, registries, it, resolving)) {
            addExternal(controller, external, externalCredited, accepted, externalNameForPool(accepted, agnosticAncestry), shortfall);
            return ResolverResult.Status.NEEDS_WORK;
         }

         if (this.cooking.isSmeltingResult(mgr, it, registries)
            && this.allCandidatesUnfundedSingleIngredient(controller, mgr, registries, reserved, it, resolving)) {
            passDiag.add("rawProbe smeltDivert output=" + itemName(it));
            addExternal(controller, external, externalCredited, accepted, externalNameForPool(accepted, agnosticAncestry), shortfall);
            return ResolverResult.Status.NEEDS_WORK;
         }
      }

      int residual = shortfall;
      boolean descentAncestry = agnosticAncestry || outputPool != null;
      ResolverResult.Status worst = ResolverResult.Status.NEEDS_WORK;

      for (Item variant : accepted) {
         if (residual <= 0) {
            break;
         }

         Optional<CraftingRecipe> variantRecipe = this.selectFundedRecipe(controller, reserved, mgr, registries, variant, residual, resolving, passDiag);
         if (!variantRecipe.isEmpty()) {
            int yield = Math.max(1, this.recipes.outputCountOf(variantRecipe.get(), registries));
            int convertible = this.convertibleCrafts(controller, reserved, variantRecipe.get());
            int craftsForV = Math.min(convertible, ceilDiv(residual, yield));
            if (craftsForV > 0) {
               int u = craftsForV * yield;
               int threshold = pooledThreshold(controller, plannedDelta, accepted, u, poolName, passDiag);
               int consumedHere = Math.min(residual, u);
               ResolverResult.Status sub = this.resolveInto(
                  controller,
                  mgr,
                  registries,
                  variant,
                  consumedHere,
                  outputPool,
                  threshold,
                  steps,
                  external,
                  externalCredited,
                  failures,
                  reserved,
                  plannedDelta,
                  surplus,
                  passDiag,
                  descentAncestry,
                  resolving,
                  depth
               );
               if (sub == ResolverResult.Status.UNOBTAINABLE) {
                  worst = ResolverResult.Status.UNOBTAINABLE;
               }

               slotConsumed.merge(variant, consumedHere, Integer::sum);
               residual -= u;
            }
         }
      }

      if (residual <= 0) {
         return worst;
      }

      Item producible = null;

      for (Item it : accepted) {
         if (this.hasNonCircularRecipe(mgr, registries, it, resolving)) {
            producible = it;
            break;
         }
      }

      if (producible != null) {
         Optional<CraftingRecipe> producibleRecipe = this.selectFundedRecipe(controller, reserved, mgr, registries, producible, residual, resolving, passDiag);
         if (producibleRecipe.isEmpty()) {
            addExternal(controller, external, externalCredited, accepted, externalNameForPool(accepted, agnosticAncestry), residual);
            return worst;
         }

         int yield = Math.max(1, this.recipes.outputCountOf(producibleRecipe.get(), registries));
         int u = ceilDiv(residual, yield) * yield;
         int threshold = pooledThreshold(controller, plannedDelta, accepted, u, poolName, passDiag);
         ResolverResult.Status sub = this.resolveInto(
            controller,
            mgr,
            registries,
            producible,
            residual,
            outputPool,
            threshold,
            steps,
            external,
            externalCredited,
            failures,
            reserved,
            plannedDelta,
            surplus,
            passDiag,
            descentAncestry,
            resolving,
            depth
         );
         if (sub == ResolverResult.Status.UNOBTAINABLE) {
            worst = ResolverResult.Status.UNOBTAINABLE;
         }

         slotConsumed.merge(producible, residual, Integer::sum);
         return worst;
      } else {
         addExternal(controller, external, externalCredited, accepted, externalNameForPool(accepted, agnosticAncestry), residual);
         return worst;
      }
   }

   private Optional<CraftingRecipe> selectFundedRecipe(
      PlayerEngineController controller,
      Map<Item, Integer> reserved,
      RecipeManager mgr,
      RegistryAccess registries,
      Item want,
      int shortfall,
      Set<Item> resolving,
      List<String> passDiag
   ) {
      List<CraftingRecipe> candidates = this.recipes.recipesForResult(mgr, want, registries);
      if (candidates.isEmpty()) {
         return Optional.empty();
      }

      CraftingRecipe bestT0 = null;
      boolean bestT0Fits = false;
      int bestT0Yield = 0;
      CraftingRecipe bestAny = null;
      boolean bestAnyFits = false;
      int bestAnyYield = 0;
      int chosenFundable = 0;
      int chosenCraftsNeeded = 0;

      for (CraftingRecipe candidate : candidates) {
         if (!this.recipeIsCircular(candidate, resolving)) {
            Optional<Item> unpackIng = this.recipes.storageUnpackIngredient(mgr, want, registries, candidate);
            if (unpackIng.isPresent() && this.freeCount(controller, reserved, unpackIng.get()) == 0) {
               passDiag.add("recipeSelect skip unfundedUnpack output=" + itemName(want) + " via=" + itemName(unpackIng.get()));
            } else {
               if (this.cooking.isSmeltingResult(mgr, want, registries)) {
                  Optional<Item> smeltDivertIng = this.recipes.singleConsumedIngredient(candidate);
                  if (smeltDivertIng.isPresent() && this.freeCount(controller, reserved, smeltDivertIng.get()) == 0) {
                     passDiag.add("recipeSelect skip smeltDivert output=" + itemName(want) + " via=" + itemName(smeltDivertIng.get()));
                     continue;
                  }
               }

               int yield = Math.max(1, this.recipes.outputCountOf(candidate, registries));
               int craftsNeeded = ceilDiv(Math.max(1, shortfall), yield);
               int fundable = this.convertibleCrafts(controller, reserved, candidate);
               boolean fits = candidate.canCraftInDimensions(2, 2);
               boolean tier0 = fundable >= craftsNeeded;
               if (tier0 && betterWithinTier(fits, yield, bestT0 != null, bestT0Fits, bestT0Yield)) {
                  bestT0 = candidate;
                  bestT0Fits = fits;
                  bestT0Yield = yield;
                  chosenFundable = fundable;
                  chosenCraftsNeeded = craftsNeeded;
               }

               if (betterWithinTier(fits, yield, bestAny != null, bestAnyFits, bestAnyYield)) {
                  bestAny = candidate;
                  bestAnyFits = fits;
                  bestAnyYield = yield;
                  if (bestT0 == null) {
                     chosenFundable = fundable;
                     chosenCraftsNeeded = craftsNeeded;
                  }
               }
            }
         }
      }

      CraftingRecipe chosen = bestT0 != null ? bestT0 : bestAny;
      if (chosen == null) {
         passDiag.add("recipeSelect output=" + itemName(want) + " shortfall=" + shortfall + " candidates=" + candidates.size() + " allCircular -> raw external");
         return Optional.empty();
      }

      if (candidates.size() > 1) {
         passDiag.add(
            "recipeSelect output="
               + itemName(want)
               + " shortfall="
               + shortfall
               + " candidates="
               + candidates.size()
               + " tier="
               + (bestT0 != null ? 0 : 1)
               + " yield="
               + (bestT0 != null ? bestT0Yield : bestAnyYield)
               + " fundable="
               + chosenFundable
               + " craftsNeeded="
               + chosenCraftsNeeded
         );
      }

      return Optional.of(chosen);
   }

   private boolean isUnfundedSingleIngredientCraft(PlayerEngineController controller, Map<Item, Integer> reserved, CraftingRecipe candidate) {
      Optional<Item> single = this.recipes.singleConsumedIngredient(candidate);
      return single.isPresent() && this.freeCount(controller, reserved, single.get()) == 0;
   }

   private boolean allCandidatesUnfundedSingleIngredient(
      PlayerEngineController controller, RecipeManager mgr, RegistryAccess registries, Map<Item, Integer> reserved, Item want, Set<Item> resolving
   ) {
      boolean sawNonCircular = false;

      for (CraftingRecipe candidate : this.recipes.recipesForResult(mgr, want, registries)) {
         if (!this.recipeIsCircular(candidate, resolving)) {
            sawNonCircular = true;
            if (!this.isUnfundedSingleIngredientCraft(controller, reserved, candidate)) {
               return false;
            }
         }
      }

      return sawNonCircular;
   }

   private static boolean betterWithinTier(boolean fits, int yield, boolean hasIncumbent, boolean incumbentFits, int incumbentYield) {
      if (!hasIncumbent) {
         return true;
      } else {
         return fits != incumbentFits ? fits : yield > incumbentYield;
      }
   }

   private boolean hasNonCircularRecipe(RecipeManager mgr, RegistryAccess registries, Item want, Set<Item> resolving) {
      for (CraftingRecipe candidate : this.recipes.recipesForResult(mgr, want, registries)) {
         if (!this.recipeIsCircular(candidate, resolving)) {
            return true;
         }
      }

      return false;
   }

   private boolean recipeIsCircular(CraftingRecipe recipe, Set<Item> resolving) {
      if (resolving.isEmpty()) {
         return false;
      }

      for (Ingredient ing : recipe.getIngredients()) {
         if (ing != null && !ing.isEmpty()) {
            Set<Item> accepted = this.inspector.acceptedItems(ing);
            if (!accepted.isEmpty()) {
               boolean allAncestors = true;

               for (Item it : accepted) {
                  if (!resolving.contains(it)) {
                     allAncestors = false;
                     break;
                  }
               }

               if (allAncestors) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private int convertibleCrafts(PlayerEngineController controller, Map<Item, Integer> reserved, CraftingRecipe recipe) {
      LinkedHashMap<String, Item[]> slotSets = new LinkedHashMap<>();
      LinkedHashMap<String, Integer> slotCounts = new LinkedHashMap<>();

      for (Ingredient ing : recipe.getIngredients()) {
         if (ing != null && !ing.isEmpty()) {
            Item[] matchSet = toItemArray(this.inspector.acceptedItems(ing));
            if (matchSet.length == 0) {
               return 0;
            }

            String sig = slotSignature(matchSet);
            slotSets.putIfAbsent(sig, matchSet);
            slotCounts.merge(sig, 1, Integer::sum);
         }
      }

      if (slotSets.isEmpty()) {
         return 0;
      }

      int convertible = Integer.MAX_VALUE;

      for (Entry<String, Item[]> e : slotSets.entrySet()) {
         int free = 0;

         for (Item it : e.getValue()) {
            free += this.freeCount(controller, reserved, it);
         }

         convertible = Math.min(convertible, free / slotCounts.get(e.getKey()));
      }

      return convertible;
   }

   private static int pooledThreshold(
      PlayerEngineController controller, Map<Item, Integer> plannedDelta, Item[] accepted, int u, String poolName, List<String> passDiag
   ) {
      int held = 0;
      int delta = 0;

      for (Item it : accepted) {
         held += controller.getItemStorage().getItemCount(it);
         delta += plannedDelta.getOrDefault(it, 0);
      }

      int threshold = u + Math.max(0, held + delta);
      passDiag.add("poolThreshold pool=" + poolName + " heldRaw=" + held + " delta=" + delta + " u=" + u + " -> " + threshold);
      return threshold;
   }

   private int freeCount(PlayerEngineController controller, Map<Item, Integer> reserved, Item item) {
      int held = controller.getItemStorage().getItemCount(item);
      int taken = reserved.getOrDefault(item, 0);
      int ledgerReserved = this.ledger != null ? held - this.ledger.free(controller, item) : 0;
      return Math.max(0, held - taken - ledgerReserved);
   }

   private static void reserve(Map<Item, Integer> reserved, Item item, int amount) {
      if (amount > 0) {
         reserved.merge(item, amount, Integer::sum);
      }
   }

   private void drainPooledReservation(
      PlayerEngineController controller, Map<Item, Integer> reserved, Item[] accepted, int amount, Map<Item, Integer> consumedOut
   ) {
      int toReserve = amount;

      for (Item it : accepted) {
         if (toReserve <= 0) {
            break;
         }

         int avail = this.freeCount(controller, reserved, it);
         int take = Math.min(avail, toReserve);
         if (take > 0) {
            reserve(reserved, it, take);
            consumedOut.merge(it, take, Integer::sum);
            toReserve -= take;
         }
      }
   }

   private static int ceilDiv(int a, int b) {
      return (a + b - 1) / b;
   }

   private static Item[] toItemArray(Set<Item> items) {
      Set<Item> ordered = new LinkedHashSet<>();

      for (Item it : items) {
         if (it != null) {
            ordered.add(it);
         }
      }

      return ordered.toArray(new Item[0]);
   }

   public static RecipeTarget toRecipeTarget(
      Item output, int targetCount, int yield, List<ItemTarget> slotTargets, CraftingRecipe recipe, RegistryAccess registries
   ) {
      ItemTarget[] grid;
      if (recipe.canCraftInDimensions(2, 2) && slotTargets.size() <= 4) {
         grid = padTo(slotTargets, 4);
      } else {
         grid = padTo(slotTargets, 9);
      }

      com.player2.playerengine.util.CraftingRecipe wrapper = com.player2.playerengine.util.CraftingRecipe.newShapedRecipe(grid, yield);
      ItemStack mcResult = registries != null ? recipe.getResultItem(registries) : ItemStack.EMPTY;
      return new RecipeTarget(output, targetCount, wrapper, recipe, mcResult, registries);
   }

   public static ItemTarget[] padTo(List<ItemTarget> slots, int size) {
      ItemTarget[] out = new ItemTarget[size];

      for (int i = 0; i < size; i++) {
         out[i] = i < slots.size() && slots.get(i) != null ? slots.get(i) : ItemTarget.EMPTY;
      }

      return out;
   }

   private static CraftMacroStepKind stepKindFor(Item want, CraftingRecipe recipe) {
      if (isPlank(want)) {
         return CraftMacroStepKind.CRAFT_PLANKS_IN_INVENTORY;
      } else if (want == Items.STICK) {
         return CraftMacroStepKind.CRAFT_STICKS_IN_INVENTORY;
      } else if (want == Items.CRAFTING_TABLE) {
         return CraftMacroStepKind.CRAFT_CRAFTING_TABLE_IN_INVENTORY;
      } else {
         return recipe.canCraftInDimensions(2, 2) ? CraftMacroStepKind.CRAFT_2X2_IN_INVENTORY : CraftMacroStepKind.CRAFT_OUTPUT_IN_TABLE;
      }
   }

   private static boolean isPlank(Item item) {
      for (Item p : ItemHelper.PLANKS) {
         if (p == item) {
            return true;
         }
      }

      return false;
   }

   private static String externalNameFor(Item item, boolean agnosticAncestry) {
      if (isLog(item)) {
         return agnosticAncestry ? "log" : narrowCatalogueName(item);
      } else {
         return isBambooBlock(item) ? "bamboo_block" : ItemHelper.stripItemName(item);
      }
   }

   private static String externalNameForPool(Item[] accepted, boolean agnosticAncestry) {
      boolean anyLog = false;

      for (Item it : accepted) {
         if (isLog(it)) {
            anyLog = true;
            break;
         }
      }

      if (anyLog) {
         if (agnosticAncestry) {
            return "log";
         }

         for (Item it : accepted) {
            if (isNaturalLog(it)) {
               return narrowCatalogueName(it);
            }
         }

         return narrowCatalogueName(accepted[0]);
      } else {
         for (Item it : accepted) {
            if (isBambooBlock(it)) {
               return "bamboo_block";
            }
         }

         return accepted.length > 0 ? ItemHelper.stripItemName(accepted[0]) : "unknown";
      }
   }

   private static String narrowCatalogueName(Item item) {
      String candidate = ItemHelper.stripItemName(item);
      if (TaskCatalogue.taskExists(candidate)) {
         return candidate;
      }

      if (candidate.endsWith("_stem")) {
         String logForm = candidate.substring(0, candidate.length() - "_stem".length()) + "_log";
         if (TaskCatalogue.taskExists(logForm)) {
            return logForm;
         }
      }

      return candidate;
   }

   private static void addExternal(
      PlayerEngineController controller, List<ItemTarget> external, List<Integer> externalCredited, Item[] creditSet, String name, int count
   ) {
      external.add(new ItemTarget(name, count));
      externalCredited.add(creditedHeldFor(controller, creditSet, name));
   }

   private static int creditedHeldFor(PlayerEngineController controller, Item[] creditSet, String externalName) {
      Item[] membership = "log".equals(externalName) ? ItemHelper.LOG : creditSet;
      int sum = 0;

      for (Item it : membership) {
         if (it != null) {
            sum += controller.getItemStorage().getItemCount(it);
         }
      }

      return sum;
   }

   private static String poolLabel(Item[] accepted) {
      for (Item it : accepted) {
         if (isLog(it)) {
            return "log";
         }
      }

      boolean allPlanks = accepted.length > 0;

      for (Item it : accepted) {
         if (!isPlank(it)) {
            allPlanks = false;
            break;
         }
      }

      if (allPlanks) {
         return "planks";
      } else {
         return accepted.length > 0 ? itemName(accepted[0]) : "unknown";
      }
   }

   private static boolean isLog(Item item) {
      for (Item l : ItemHelper.LOG) {
         if (l == item) {
            return true;
         }
      }

      return false;
   }

   private static boolean isNaturalLog(Item item) {
      for (Item l : ItemHelper.NATURAL_LOG) {
         if (l == item) {
            return true;
         }
      }

      return false;
   }

   private static boolean isBambooBlock(Item item) {
      return item == Items.BAMBOO_BLOCK || item == Items.STRIPPED_BAMBOO_BLOCK;
   }

   private static String debugLabel(Item item) {
      return ItemHelper.stripItemName(item);
   }

   private static String itemName(Item item) {
      return ItemHelper.stripItemName(item);
   }

   private static void logResolvedPlan(
      PlayerEngineController controller,
      String header,
      ResolverResult.Status status,
      List<CraftMacroStep> steps,
      List<ItemTarget> external,
      List<Integer> externalCredited,
      int deficit
   ) {
      Debug.logInternal(
         "[[CRAFT-RESOLVER-DIAG]] " + header + " status=" + status + " deficit=" + deficit + " steps=" + steps.size() + " externals=" + external.size()
      );
      logHeldWood(controller, "logs", ItemHelper.LOG);
      logHeldWood(controller, "planks", ItemHelper.PLANKS);

      for (int i = 0; i < steps.size(); i++) {
         CraftMacroStep s = steps.get(i);
         String om;
         if (s.outputMatches() == null) {
            om = "null";
         } else {
            StringBuilder sb = new StringBuilder();

            for (Item it : s.outputMatches()) {
               sb.append(itemName(it)).append(',');
            }

            om = "[" + s.outputMatches().length + ":" + sb + "]";
         }

         Debug.logInternal(
            "[[CRAFT-RESOLVER-DIAG]]   step#"
               + i
               + " kind="
               + s.kind()
               + " output="
               + itemName(s.recipeTarget().getOutputItem())
               + " threshold(targetCount)="
               + s.recipeTarget().getTargetCount()
               + " yield="
               + s.recipeTarget().getRecipe().outputCount()
               + " craftsNeeded="
               + s.craftsNeeded()
               + " outputMatches="
               + om
         );
      }

      for (int i = 0; i < external.size(); i++) {
         ItemTarget t = external.get(i);
         int credited = i < externalCredited.size() ? externalCredited.get(i) : 0;
         Debug.logInternal("[[CRAFT-RESOLVER-DIAG]]   external#" + i + " " + t + " net=" + t.getTargetCount() + " creditedHeld=" + credited);
      }
   }

   private void emitArithmeticDiag(List<String> passDiag) {
      String sig = String.join(";", passDiag);
      if (!sig.equals(this.lastArithmeticDiagSig)) {
         this.lastArithmeticDiagSig = sig;

         for (String line : passDiag) {
            Debug.logInternal("[[CRAFT-RESOLVER-DIAG]] " + line);
         }
      }
   }

   private static void logHeldWood(PlayerEngineController controller, String label, Item[] family) {
      StringBuilder sb = new StringBuilder();

      for (Item it : family) {
         if (it != null) {
            int held = controller.getItemStorage().getItemCount(it);
            if (held > 0) {
               sb.append(itemName(it)).append('=').append(held).append(' ');
            }
         }
      }

      Debug.logInternal("[[CRAFT-RESOLVER-DIAG]]   held " + label + ": " + (sb.length() == 0 ? "(none)" : sb.toString().trim()));
   }

   public record BudgetTarget(Item item, int count) {
   }

   private record CoalescedExternals(List<ItemTarget> targets, List<Integer> creditedHeld) {
   }

   private static final class SlotDemand {
      final Item[] matchSet;
      final IngredientInspector.SlotKind kind;
      int demand;

      SlotDemand(Item[] matchSet, IngredientInspector.SlotKind kind, int demand) {
         this.matchSet = matchSet;
         this.kind = kind;
         this.demand = demand;
      }
   }
}
