package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.equip.PendingArmorPickup;
import com.player2.playerengine.equip.PendingWeaponPickup;
import com.player2.playerengine.player2api.Player2NpcOwnerSettingsReader;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.tasks.misc.ProcessPickupArmorTask;
import com.player2.playerengine.tasks.misc.ProcessPickupWeaponTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ForceEquipGearChain extends SingleTaskChain {
   public static final Logger LOGGER = LogManager.getLogger();

   public ForceEquipGearChain(TaskRunner runner) {
      super(runner);
   }

   private boolean isAutoEquipEnabled(PlayerEngineController controller) {
      Player owner = controller.getOwner();
      if (owner == null) {
         return true;
      }

      MinecraftServer server = controller.getWorld().getServer();
      return server == null ? true : Player2NpcOwnerSettingsReader.isAutoEquipEnabled(server, owner.getUUID());
   }

   private boolean shouldEquipShield(PlayerEngineController controller) {
      return !StorageHelper.isItemInOffhand(controller, Items.SHIELD) && controller.getItemStorage().hasItem(ItemTarget.of(Items.SHIELD));
   }

   private boolean isProcessingPickupTask() {
      return this.mainTask instanceof ProcessPickupArmorTask armorTask && !armorTask.isFinished() && !armorTask.stopped()
         ? true
         : this.mainTask instanceof ProcessPickupWeaponTask weaponTask && !weaponTask.isFinished() && !weaponTask.stopped();
   }

   private void startNextPickupTaskIfQueued() {
      if (!this.isProcessingPickupTask()) {
         Optional<PendingArmorPickup> armorNext = this.controller.getPickupArmorEvalQueue().pollNext();
         if (armorNext.isPresent()) {
            this.setTask(new ProcessPickupArmorTask(armorNext.get()));
         } else {
            Optional<PendingWeaponPickup> weaponNext = this.controller.getPickupWeaponEvalQueue().pollNext();
            weaponNext.ifPresent(p -> this.setTask(new ProcessPickupWeaponTask(p)));
         }
      }
   }

   private boolean hasQueuedPickups() {
      return !this.controller.getPickupArmorEvalQueue().isEmpty() || !this.controller.getPickupWeaponEvalQueue().isEmpty();
   }

   @Override
   protected void onTick() {
      if (this.isAutoEquipEnabled(this.controller)) {
         if (this.hasQueuedPickups() && !this.isProcessingPickupTask()) {
            this.startNextPickupTaskIfQueued();
         }

         if (this.mainTask != null && !this.mainTask.isFinished() && !this.mainTask.stopped()) {
            super.onTick();
         } else {
            if (this.shouldEquipShield(this.controller)) {
               this.controller.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
            }
         }
      }
   }

   @Override
   public float getPriority() {
      if (!this.isAutoEquipEnabled(this.controller)) {
         return 0.0F;
      } else {
         return !this.isProcessingPickupTask() && !this.hasQueuedPickups() ? 0.0F : 100.0F;
      }
   }

   @Override
   protected void onTaskFinish(PlayerEngineController controller) {
      this.startNextPickupTaskIfQueued();
   }

   @Override
   public String getName() {
      return "ForceEquipGearChain";
   }

   @Override
   public boolean isActive() {
      return true;
   }
}
