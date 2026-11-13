package com.player2.playerengine.mixins;

import com.player2.playerengine.PlayerEngineController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootItemKilledByPlayerCondition.class)
public abstract class CompanionKillLootMixin {
   @Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z",
      at = @At("RETURN"), cancellable = true)
   private void playerengine$creditCompanionMobKill(LootContext context, CallbackInfoReturnable<Boolean> result) {
      if (result.getReturnValueZ() || !(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Mob)) return;
      var damage = context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
      Entity attacker = damage == null ? null : damage.getEntity();
      if (attacker == null) return;

      var controller = PlayerEngineController.staticControllers.get(attacker.getUUID());
      if (controller != null && controller.getEntity() == attacker) result.setReturnValue(true);
   }
}
