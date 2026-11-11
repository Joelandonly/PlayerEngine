package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FarmRepairToolPlan {
   private static final FarmRepairToolPlan EMPTY = new FarmRepairToolPlan(List.of(), List.of());
   private final List<FarmRepairToolPlan.Requirement> standardRequirements;
   private final List<FarmRepairToolPlan.CustomNeed> customNeeds;
   private final String identity;

   private FarmRepairToolPlan(List<FarmRepairToolPlan.Requirement> standardRequirements, List<FarmRepairToolPlan.CustomNeed> customNeeds) {
      this.standardRequirements = List.copyOf(Objects.requireNonNull(standardRequirements, "standardRequirements"));
      this.customNeeds = List.copyOf(Objects.requireNonNull(customNeeds, "customNeeds"));
      this.identity = buildIdentity(this.standardRequirements, this.customNeeds);
   }

   static FarmRepairToolPlan fromClearActions(List<FarmPlotRepairPlan.ClearAction> actions) {
      Objects.requireNonNull(actions, "actions");
      if (actions.isEmpty()) {
         return EMPTY;
      }

      EnumMap<FarmBreakToolRequirement.Family, FarmRepairToolPlan.StandardDraft> standard = new EnumMap<>(FarmBreakToolRequirement.Family.class);
      LinkedHashMap<BlockState, Integer> customCounts = new LinkedHashMap<>();
      LinkedHashMap<BlockState, FarmBreakToolRequirement> customRequirements = new LinkedHashMap<>();

      for (FarmPlotRepairPlan.ClearAction action : actions) {
         FarmBreakToolRequirement requirement = Objects.requireNonNull(action.breakToolRequirement(), "clear action breakToolRequirement");
         if (requirement.isStandard()) {
            FarmRepairToolPlan.StandardDraft draft = standard.get(requirement.family());
            if (draft == null) {
               standard.put(requirement.family(), new FarmRepairToolPlan.StandardDraft(requirement));
            } else {
               draft.add(requirement);
            }
         } else if (requirement.isCustom()) {
            BlockState state = requirement.expectedState();
            customCounts.merge(state, 1, Integer::sum);
            customRequirements.putIfAbsent(state, requirement);
         }
      }

      ArrayList<FarmRepairToolPlan.Requirement> resolvedStandard = new ArrayList<>();

      for (FarmBreakToolRequirement.Family family : FarmBreakToolRequirement.Family.values()) {
         FarmRepairToolPlan.StandardDraft draft = standard.get(family);
         if (draft != null) {
            resolvedStandard.add(new FarmRepairToolPlan.Requirement(family, draft.minimumTier, draft.blocks.size(), draft.blocks));
         }
      }

      ArrayList<FarmRepairToolPlan.CustomNeed> resolvedCustom = new ArrayList<>();

      for (Entry<BlockState, Integer> entry : customCounts.entrySet()) {
         resolvedCustom.add(new FarmRepairToolPlan.CustomNeed(entry.getKey(), entry.getValue(), customRequirements.get(entry.getKey())));
      }

      return new FarmRepairToolPlan(resolvedStandard, resolvedCustom);
   }

   public List<FarmRepairToolPlan.Requirement> standardRequirements() {
      return this.standardRequirements;
   }

   public String identity() {
      return this.identity;
   }

   public boolean isSatisfied(LivingEntityInventory inventory) {
      FarmRepairToolPlan.AllocationResult allocation = this.allocate(inventory);
      return !allocation.customUnsatisfied() && allocation.missingStandard().isEmpty();
   }

   public List<FarmRepairToolPlan.Requirement> missingStandardRequirements(LivingEntityInventory inventory) {
      return this.allocate(inventory).missingStandard();
   }

   public boolean hasUnsatisfiedCustom(LivingEntityInventory inventory) {
      return this.allocate(inventory).customUnsatisfied();
   }

   public static int bestSlot(LivingEntityInventory inventory, FarmBreakToolRequirement requirement, int requiredUses) {
      Objects.requireNonNull(inventory, "inventory");
      Objects.requireNonNull(requirement, "requirement");
      if (requiredUses < 1) {
         throw new IllegalArgumentException("requiredUses must be positive");
      }

      if (!requirement.requiresTool()) {
         return bestNoToolSlot(inventory);
      }

      int best = -1;
      float bestSpeed = Float.NEGATIVE_INFINITY;
      int bestRemaining = -1;

      for (int i = 0; i < inventory.main.size(); i++) {
         ItemStack stack = (ItemStack)inventory.main.get(i);
         int remaining = FarmBreakToolRequirement.remainingUses(stack);
         if (remaining >= requiredUses && requirement.accepts(stack)) {
            float speed = stack.getDestroySpeed(requirement.expectedState());
            if (best < 0 || speed > bestSpeed || speed == bestSpeed && remaining > bestRemaining) {
               best = i;
               bestSpeed = speed;
               bestRemaining = remaining;
            }
         }
      }

      return best;
   }

   private FarmRepairToolPlan.AllocationResult allocate(LivingEntityInventory inventory) {
      Objects.requireNonNull(inventory, "inventory");
      int[] remaining = new int[inventory.main.size()];

      for (int slot = 0; slot < inventory.main.size(); slot++) {
         remaining[slot] = FarmBreakToolRequirement.remainingUses((ItemStack)inventory.main.get(slot));
      }

      ArrayList<FarmRepairToolPlan.CustomNeed> pendingCustom = new ArrayList<>(this.customNeeds);
      boolean customUnsatisfied = false;

      while (!pendingCustom.isEmpty()) {
         int needIndex = this.mostConstrainedCustom(pendingCustom, inventory, remaining);
         FarmRepairToolPlan.CustomNeed need = pendingCustom.remove(needIndex);
         int slot = this.bestCustomSlot(need, pendingCustom, inventory, remaining);
         if (slot < 0) {
            customUnsatisfied = true;
         } else {
            remaining[slot] -= need.useCount();
         }
      }

      ArrayList<FarmRepairToolPlan.Requirement> pendingStandard = new ArrayList<>(this.standardRequirements);
      EnumSet<FarmBreakToolRequirement.Family> missingFamilies = EnumSet.noneOf(FarmBreakToolRequirement.Family.class);

      while (!pendingStandard.isEmpty()) {
         int requirementIndex = this.mostConstrainedStandard(pendingStandard, inventory, remaining);
         FarmRepairToolPlan.Requirement requirement = pendingStandard.remove(requirementIndex);
         int slot = this.bestStandardSlot(requirement, pendingStandard, inventory, remaining);
         if (slot < 0) {
            missingFamilies.add(requirement.family());
         } else {
            remaining[slot] -= requirement.useCount();
         }
      }

      ArrayList<FarmRepairToolPlan.Requirement> missing = new ArrayList<>();

      for (FarmRepairToolPlan.Requirement requirement : this.standardRequirements) {
         if (missingFamilies.contains(requirement.family())) {
            missing.add(requirement);
         }
      }

      return new FarmRepairToolPlan.AllocationResult(customUnsatisfied, missing);
   }

   private int mostConstrainedCustom(List<FarmRepairToolPlan.CustomNeed> pending, LivingEntityInventory inventory, int[] remaining) {
      int selected = 0;
      int selectedCandidates = Integer.MAX_VALUE;

      for (int index = 0; index < pending.size(); index++) {
         FarmRepairToolPlan.CustomNeed need = pending.get(index);
         int candidates = this.customCandidateCount(need, inventory, remaining);
         FarmRepairToolPlan.CustomNeed previous = pending.get(selected);
         if (candidates < selectedCandidates
            || candidates == selectedCandidates && need.useCount() > previous.useCount()
            || candidates == selectedCandidates
               && need.useCount() == previous.useCount()
               && stateIdentity(need.state()).compareTo(stateIdentity(previous.state())) < 0) {
            selected = index;
            selectedCandidates = candidates;
         }
      }

      return selected;
   }

   private int customCandidateCount(FarmRepairToolPlan.CustomNeed need, LivingEntityInventory inventory, int[] remaining) {
      int count = 0;

      for (int slot = 0; slot < inventory.main.size(); slot++) {
         if (remaining[slot] >= need.useCount() && accepts(need.requirement(), (ItemStack)inventory.main.get(slot))) {
            count++;
         }
      }

      return count;
   }

   private int bestCustomSlot(
      FarmRepairToolPlan.CustomNeed need, List<FarmRepairToolPlan.CustomNeed> otherCustom, LivingEntityInventory inventory, int[] remaining
   ) {
      int best = -1;
      int bestCustomFlexibility = Integer.MAX_VALUE;
      int bestStandardFlexibility = Integer.MAX_VALUE;
      int bestResidual = Integer.MAX_VALUE;

      for (int slot = 0; slot < inventory.main.size(); slot++) {
         if (remaining[slot] >= need.useCount() && accepts(need.requirement(), (ItemStack)inventory.main.get(slot))) {
            int residual = remaining[slot] - need.useCount();
            int customFlexibility = 0;

            for (FarmRepairToolPlan.CustomNeed other : otherCustom) {
               if (residual >= other.useCount() && accepts(other.requirement(), (ItemStack)inventory.main.get(slot))) {
                  customFlexibility++;
               }
            }

            int standardFlexibility = 0;

            for (FarmRepairToolPlan.Requirement standard : this.standardRequirements) {
               if (residual >= standard.useCount() && acceptsEveryBlock(standard, (ItemStack)inventory.main.get(slot))) {
                  standardFlexibility++;
               }
            }

            if (best < 0
               || customFlexibility < bestCustomFlexibility
               || customFlexibility == bestCustomFlexibility && standardFlexibility < bestStandardFlexibility
               || customFlexibility == bestCustomFlexibility && standardFlexibility == bestStandardFlexibility && residual < bestResidual) {
               best = slot;
               bestCustomFlexibility = customFlexibility;
               bestStandardFlexibility = standardFlexibility;
               bestResidual = residual;
            }
         }
      }

      return best;
   }

   private int mostConstrainedStandard(List<FarmRepairToolPlan.Requirement> pending, LivingEntityInventory inventory, int[] remaining) {
      int selected = 0;
      int selectedCandidates = Integer.MAX_VALUE;

      for (int index = 0; index < pending.size(); index++) {
         FarmRepairToolPlan.Requirement requirement = pending.get(index);
         int candidates = this.standardCandidateCount(requirement, inventory, remaining);
         FarmRepairToolPlan.Requirement previous = pending.get(selected);
         if (candidates < selectedCandidates
            || candidates == selectedCandidates && requirement.useCount() > previous.useCount()
            || candidates == selectedCandidates
               && requirement.useCount() == previous.useCount()
               && requirement.family().ordinal() < previous.family().ordinal()) {
            selected = index;
            selectedCandidates = candidates;
         }
      }

      return selected;
   }

   private int standardCandidateCount(FarmRepairToolPlan.Requirement requirement, LivingEntityInventory inventory, int[] remaining) {
      int count = 0;

      for (int slot = 0; slot < inventory.main.size(); slot++) {
         if (remaining[slot] >= requirement.useCount() && acceptsEveryBlock(requirement, (ItemStack)inventory.main.get(slot))) {
            count++;
         }
      }

      return count;
   }

   private int bestStandardSlot(
      FarmRepairToolPlan.Requirement requirement, List<FarmRepairToolPlan.Requirement> otherStandard, LivingEntityInventory inventory, int[] remaining
   ) {
      int best = -1;
      int bestFlexibility = Integer.MAX_VALUE;
      int bestResidual = Integer.MAX_VALUE;

      for (int slot = 0; slot < inventory.main.size(); slot++) {
         ItemStack stack = (ItemStack)inventory.main.get(slot);
         if (remaining[slot] >= requirement.useCount() && acceptsEveryBlock(requirement, stack)) {
            int residual = remaining[slot] - requirement.useCount();
            int flexibility = 0;

            for (FarmRepairToolPlan.Requirement other : otherStandard) {
               if (residual >= other.useCount() && acceptsEveryBlock(other, stack)) {
                  flexibility++;
               }
            }

            if (best < 0 || flexibility < bestFlexibility || flexibility == bestFlexibility && residual < bestResidual) {
               best = slot;
               bestFlexibility = flexibility;
               bestResidual = residual;
            }
         }
      }

      return best;
   }

   private static boolean acceptsEveryBlock(FarmRepairToolPlan.Requirement requirement, ItemStack stack) {
      for (FarmBreakToolRequirement block : requirement.blockRequirements()) {
         if (!accepts(block, stack)) {
            return false;
         }
      }

      return true;
   }

   private static boolean accepts(FarmBreakToolRequirement requirement, ItemStack stack) {
      try {
         return stack != null && requirement.accepts(stack);
      } catch (RuntimeException incompatibleModItem) {
         return false;
      }
   }

   private static int bestNoToolSlot(LivingEntityInventory inventory) {
      int best = -1;
      int bestRank = Integer.MAX_VALUE;

      for (int slot = 0; slot < inventory.main.size(); slot++) {
         ItemStack stack = (ItemStack)inventory.main.get(slot);

         int rank;
         try {
            if (stack == null || !stack.isEmpty() && stack.isDamageableItem()) {
               if (stack != null && !(stack.getItem() instanceof HoeItem)) {
                  rank = 1;
               } else {
                  rank = 2;
               }
            } else {
               rank = 0;
            }
         } catch (RuntimeException invalidModItem) {
            rank = 2;
         }

         if (best < 0 || rank < bestRank || rank == bestRank && slot == inventory.selectedSlot) {
            best = slot;
            bestRank = rank;
         }
      }

      return best;
   }

   private static String buildIdentity(List<FarmRepairToolPlan.Requirement> standard, List<FarmRepairToolPlan.CustomNeed> custom) {
      StringBuilder result = new StringBuilder("repair-tools-v1");

      for (FarmRepairToolPlan.Requirement requirement : standard) {
         result.append('|').append(requirement.family().name()).append(':').append(requirement.minimumTier().name()).append(':').append(requirement.useCount());
         ArrayList<String> states = new ArrayList<>();

         for (FarmBreakToolRequirement block : requirement.blockRequirements()) {
            states.add(stateIdentity(block.expectedState()));
         }

         states.sort(Comparator.naturalOrder());

         for (String state : states) {
            result.append(':').append(state);
         }
      }

      for (FarmRepairToolPlan.CustomNeed need : custom) {
         result.append("|CUSTOM:").append(need.useCount()).append(':').append(stateIdentity(need.state()));
      }

      return "repair-tools-v1:" + UUID.nameUUIDFromBytes(result.toString().getBytes(StandardCharsets.UTF_8));
   }

   private static String stateIdentity(BlockState state) {
      return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "@" + state;
   }

   private record AllocationResult(boolean customUnsatisfied, List<FarmRepairToolPlan.Requirement> missingStandard) {
      private AllocationResult {
         missingStandard = List.copyOf(missingStandard);
      }
   }

   private record CustomNeed(BlockState state, int useCount, FarmBreakToolRequirement requirement) {
      private CustomNeed(BlockState state, int useCount, FarmBreakToolRequirement requirement) {
         Objects.requireNonNull(state, "state");
         Objects.requireNonNull(requirement, "requirement");
         if (useCount >= 1 && requirement.isCustom()) {
            this.state = state;
            this.useCount = useCount;
            this.requirement = requirement;
         } else {
            throw new IllegalArgumentException("invalid custom repair tool need");
         }
      }
   }

   public record Requirement(
      FarmBreakToolRequirement.Family family, FarmBreakToolRequirement.StandardTier minimumTier, int useCount, List<FarmBreakToolRequirement> blockRequirements
   ) {
      public Requirement(
         FarmBreakToolRequirement.Family family,
         FarmBreakToolRequirement.StandardTier minimumTier,
         int useCount,
         List<FarmBreakToolRequirement> blockRequirements
      ) {
         Objects.requireNonNull(family, "family");
         Objects.requireNonNull(minimumTier, "minimumTier");
         if (useCount < 1) {
            throw new IllegalArgumentException("repair tool use count must be positive");
         }

         blockRequirements = List.copyOf(Objects.requireNonNull(blockRequirements, "blockRequirements"));
         if (blockRequirements.size() == useCount
            && !blockRequirements.stream().anyMatch(requirement -> requirement == null || !requirement.isStandard() || requirement.family() != family)) {
            this.family = family;
            this.minimumTier = minimumTier;
            this.useCount = useCount;
            this.blockRequirements = blockRequirements;
         } else {
            throw new IllegalArgumentException("repair tool requirement blocks do not match their family/use count");
         }
      }

      public boolean isSatisfiedBy(ItemStack stack) {
         return FarmBreakToolRequirement.remainingUses(stack) >= this.useCount && FarmRepairToolPlan.acceptsEveryBlock(this, stack);
      }

      public List<Item> markedStorageItems() {
         ArrayList<Item> compatible = new ArrayList<>();

         for (Item candidate : FarmBreakToolRequirement.standardItemsAtOrAbove(this.family, this.minimumTier, this.useCount)) {
            if (this.isSatisfiedBy(candidate.getDefaultInstance())) {
               compatible.add(candidate);
            }
         }

         return List.copyOf(compatible);
      }

      public Item craftFallbackItem() {
         for (FarmBreakToolRequirement.StandardTier tier : FarmBreakToolRequirement.StandardTier.values()) {
            if (tier.ordinal() >= this.minimumTier.ordinal()) {
               Item candidate = FarmBreakToolRequirement.itemFor(this.family, tier);
               if (this.isSatisfiedBy(candidate.getDefaultInstance())) {
                  return candidate;
               }
            }
         }

         return null;
      }
   }

   private static final class StandardDraft {
      private FarmBreakToolRequirement.StandardTier minimumTier;
      private final List<FarmBreakToolRequirement> blocks = new ArrayList<>();

      private StandardDraft(FarmBreakToolRequirement requirement) {
         this.minimumTier = requirement.minimumTier();
         this.add(requirement);
      }

      private void add(FarmBreakToolRequirement requirement) {
         if (requirement.minimumTier().ordinal() > this.minimumTier.ordinal()) {
            this.minimumTier = requirement.minimumTier();
         }

         this.blocks.add(requirement);
      }
   }
}
