package com.player2.playerengine.tasks.container;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.tasks.misc.EquipArmorTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.squashed.CataloguedResourceTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

public class UpgradeInSmithingTableTask extends ResourceTask {
   private final ItemTarget tool;
   private final ItemTarget template;
   private final ItemTarget material;
   private final ItemTarget output;
   private BlockPos tablePos = null;
   @Nullable
   private final MaterialReservationService ledger;
   private boolean reservedRoles = false;
   private final Map<Item, Integer> grantedReservations = new HashMap<>();
   private UpgradeInSmithingTableTask.SmithStepResult stepResult = UpgradeInSmithingTableTask.SmithStepResult.IN_PROGRESS;

   public UpgradeInSmithingTableTask(ItemTarget tool, ItemTarget material, ItemTarget output) {
      super(output);
      this.tool = new ItemTarget(tool, output.getTargetCount());
      this.material = new ItemTarget(material, output.getTargetCount());
      this.template = new ItemTarget(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, output.getTargetCount());
      this.output = output;
      this.ledger = null;
   }

   public UpgradeInSmithingTableTask(ItemTarget tool, ItemTarget material, ItemTarget templateTarget, ItemTarget output) {
      this(tool, material, templateTarget, output, null);
   }

   public UpgradeInSmithingTableTask(
      ItemTarget tool, ItemTarget material, ItemTarget templateTarget, ItemTarget output, @Nullable MaterialReservationService ledger
   ) {
      super(output);
      this.tool = new ItemTarget(tool, output.getTargetCount());
      this.material = new ItemTarget(material, output.getTargetCount());
      this.template = new ItemTarget(templateTarget, output.getTargetCount());
      this.output = output;
      this.ledger = ledger;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController controller) {
      controller.getBehaviour().addProtectedItems(this.tool.getMatches());
      controller.getBehaviour().addProtectedItems(this.material.getMatches());
      controller.getBehaviour().addProtectedItems(this.template.getMatches());
      controller.getBehaviour().addProtectedItems(Items.SMITHING_TABLE);
   }

   public UpgradeInSmithingTableTask.SmithStepResult getStepResult() {
      return this.stepResult;
   }

   @Override
   protected Task onResourceTick(PlayerEngineController controller) {
      int desiredOutputCount = this.output.getTargetCount();
      int currentOutputCount = controller.getItemStorage().getItemCount(this.output);
      if (currentOutputCount >= desiredOutputCount) {
         this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.UPGRADED;
         return null;
      }

      int needed = desiredOutputCount - currentOutputCount;
      if (this.effectiveFree(controller, this.tool) < needed
         || this.effectiveFree(controller, this.material) < needed
         || this.effectiveFree(controller, this.template) < needed) {
         this.setDebugState("Getting materials for upgrade");
         this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.NEEDS_GATHER;
         return new CataloguedResourceTask(new ItemTarget(this.tool, needed), new ItemTarget(this.material, needed), new ItemTarget(this.template, needed));
      }

      if (StorageHelper.isArmorEquipped(controller, this.tool.getMatches())) {
         this.setDebugState("Unequipping armor before upgrading.");
         this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.IN_PROGRESS;
         return new EquipArmorTask(new ItemTarget[0]);
      }

      if (this.tablePos == null || !controller.getWorld().getBlockState(this.tablePos).is(Blocks.SMITHING_TABLE)) {
         Optional<BlockPos> nearestTable = controller.getBlockScanner().getNearestBlock(Blocks.SMITHING_TABLE);
         if (!nearestTable.isPresent()) {
            if (controller.getItemStorage().hasItem(Items.SMITHING_TABLE)) {
               this.setDebugState("Placing smithing table.");
               this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.IN_PROGRESS;
               return new PlaceBlockNearbyTask(Blocks.SMITHING_TABLE);
            }

            this.setDebugState("Obtaining smithing table.");
            this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.NO_TABLE;
            return TaskCatalogue.getItemTask(Items.SMITHING_TABLE, 1);
         }

         this.tablePos = nearestTable.get();
      }

      if (!this.tablePos
         .closerThan(
            new Vec3i(
               (int)controller.getEntity().position().x,
               (int)controller.getEntity().position().y,
               (int)controller.getEntity().position().z
            ),
            4.5
         )) {
         this.setDebugState("Going to smithing table.");
         this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.IN_PROGRESS;
         return new GetToBlockTask(this.tablePos);
      }

      this.setDebugState("Upgrading item...");
      if (this.ledger != null && !this.reservedRoles) {
         this.reserveRole(controller, this.template);
         this.reserveRole(controller, this.tool);
         this.reserveRole(controller, this.material);
         this.reservedRoles = true;
      }

      LivingEntityInventory inventory = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
      inventory.remove(stack -> this.template.matches(stack.getItem()), 1, inventory);
      inventory.remove(stack -> this.tool.matches(stack.getItem()), 1, inventory);
      inventory.remove(stack -> this.material.matches(stack.getItem()), 1, inventory);
      inventory.insertStack(new ItemStack(this.output.getMatches()[0], 1));
      controller.getItemStorage().registerSlotAction();
      this.stepResult = UpgradeInSmithingTableTask.SmithStepResult.UPGRADED;
      return null;
   }

   @Override
   protected void onResourceStop(PlayerEngineController controller, Task interruptTask) {
      controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof UpgradeInSmithingTableTask task
         ? task.tool.equals(this.tool) && task.output.equals(this.output) && task.material.equals(this.material) && task.template.equals(this.template)
         : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Upgrading in Smithing Table";
   }

   public ItemTarget getMaterials() {
      return this.material;
   }

   public ItemTarget getTools() {
      return this.tool;
   }

   public ItemTarget getTemplate() {
      return this.template;
   }

   @Nullable
   private Item resolveSpecies(PlayerEngineController controller, ItemTarget role) {
      Item[] matches = role.getMatches();
      if (matches != null && matches.length != 0) {
         if (matches.length == 1) {
            return matches[0];
         }

         Item best = null;
         int bestCount = 0;

         for (Item candidate : matches) {
            if (candidate != null && candidate != Items.AIR) {
               int count = controller.getItemStorage().getItemCount(candidate);
               if (count > bestCount) {
                  bestCount = count;
                  best = candidate;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private int effectiveFree(PlayerEngineController controller, ItemTarget role) {
      int raw = controller.getItemStorage().getItemCount(role);
      if (this.ledger == null) {
         return raw;
      }

      Item species = this.resolveSpecies(controller, role);
      return species == null ? raw : this.ledger.free(controller, species);
   }

   private void reserveRole(PlayerEngineController controller, ItemTarget role) {
      if (this.ledger != null) {
         Item species = this.resolveSpecies(controller, role);
         if (species != null) {
            int granted = this.ledger.reserve(controller, species, 1);
            if (granted > 0) {
               this.grantedReservations.merge(species, granted, Integer::sum);
            }
         }
      }
   }

   public void releaseReservations() {
      if (this.ledger != null && !this.grantedReservations.isEmpty()) {
         for (Entry<Item, Integer> e : this.grantedReservations.entrySet()) {
            this.ledger.release(e.getKey(), e.getValue());
         }

         this.grantedReservations.clear();
      }
   }

   public enum SmithStepResult {
      IN_PROGRESS,
      NEEDS_GATHER,
      NO_TABLE,
      UPGRADED;
   }
}
