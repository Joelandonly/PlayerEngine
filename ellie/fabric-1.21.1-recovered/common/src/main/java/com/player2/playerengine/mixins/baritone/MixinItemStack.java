package com.player2.playerengine.mixins.baritone;

import com.player2.playerengine.automaton.api.utils.BaritoneStackDamage;
import com.player2.playerengine.automaton.api.utils.accessor.IItemStack;
import com.player2.playerengine.PlayerEngineController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements IItemStack {
   @Shadow
   @Final
   private Item item;
   @Unique
   private int baritoneHash;
   @Unique
   private boolean baritoneHashValid;

   @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
      at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private int playerengine$companionToolWear(int wear, int originalWear, LivingEntity user, EquipmentSlot slot) {
      if (wear <= 0 || !(user.level() instanceof ServerLevel) || user instanceof Player
          || !(this.item instanceof TieredItem tool)
          || (tool.getTier() != Tiers.DIAMOND && tool.getTier() != Tiers.NETHERITE)) return wear;
      var controller = PlayerEngineController.staticControllers.get(user.getUUID());
      if (controller == null || controller.getEntity() != user) return wear;
      return playerengine$rollCompanionWear(wear, user.getRandom());
   }

   @Unique
   private static int playerengine$rollCompanionWear(int wear, RandomSource random) {
      int remainingWear = 0;
      for (int point = 0; point < wear; point++) {
         if (random.nextInt(10) == 0) remainingWear++;
      }
      return remainingWear;
   }

   private void recalculateHash() {
      ItemStack self = (ItemStack)(Object)this;
      this.baritoneHash = this.item == null ? -1 : this.item.hashCode() + BaritoneStackDamage.storedDamage(self);
      this.baritoneHashValid = true;
   }

   @Inject(method = "setDamageValue", at = @At("TAIL"))
   private void onItemDamageSet(CallbackInfo ci) {
      this.recalculateHash();
   }

   @Override
   public int getBaritoneHash() {
      if (!this.baritoneHashValid) {
         this.recalculateHash();
      }

      return this.baritoneHash;
   }
}
