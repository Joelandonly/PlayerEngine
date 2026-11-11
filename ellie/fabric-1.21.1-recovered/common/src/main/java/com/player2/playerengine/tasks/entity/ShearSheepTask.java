package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Debug;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;

public class ShearSheepTask extends AbstractDoToEntityTask {
   public ShearSheepTask() {
      super(0.0, -1.0, -1.0);
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof ShearSheepTask;
   }

   @Override
   protected Task onEntityInteract(PlayerEngineController mod, Entity entity) {
      if (!mod.getItemStorage().hasItem(Items.SHEARS)) {
         Debug.logWarning("Failed to shear sheep because you have no shears.");
         return null;
      }

      if (mod.getSlotHandler().forceEquipItem(Items.SHEARS)) {
         ((Sheep)entity).shear(SoundSource.PLAYERS);
         mod.getPlayer().getMainHandItem().hurtAndBreak(1, mod.getPlayer(), EquipmentSlot.MAINHAND);
      }

      return null;
   }

   @Override
   protected Optional<Entity> getEntityTarget(PlayerEngineController mod) {
      return mod.getEntityTracker()
         .getClosestEntity(
            mod.getPlayer().position(),
            entity -> entity instanceof Sheep sheep ? sheep.readyForShearing() && !sheep.isSheared() : false,
            Sheep.class
         );
   }

   @Override
   protected String toDebugString() {
      return "Shearing Sheep";
   }
}
