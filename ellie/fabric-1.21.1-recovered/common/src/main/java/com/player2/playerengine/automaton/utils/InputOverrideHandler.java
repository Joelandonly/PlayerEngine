package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.utils.IInputOverrideHandler;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.behavior.Behavior;
import com.player2.playerengine.reliability.SharedTargetRecovery;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;

public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {
   private final Set<Input> inputForceStateMap = EnumSet.noneOf(Input.class);
   private final BlockBreakHelper blockBreakHelper;
   private final BlockPlaceHelper blockPlaceHelper;
   private boolean needsUpdate;
   private boolean wasJumping = false;

   public InputOverrideHandler(Baritone baritone) {
      super(baritone);
      this.blockBreakHelper = new BlockBreakHelper(baritone.getEntityContext());
      this.blockPlaceHelper = new BlockPlaceHelper(baritone.getEntityContext());
   }

   @Override
   public final synchronized boolean isInputForcedDown(Input input) {
      return input != null && this.inputForceStateMap.contains(input);
   }

   @Override
   public final synchronized void setInputForceState(Input input, boolean forced) {
      SharedTargetRecovery.inputWrite(this, input, forced);
      if (forced) {
         this.inputForceStateMap.add(input);
      } else {
         this.inputForceStateMap.remove(input);
      }

      this.needsUpdate = true;
   }

   @Override
   public final synchronized void clearAllKeys() {
      if (this.ctx.entity().isSprinting()) {
         this.ctx.entity().setSprinting(false);
      }

      this.inputForceStateMap.clear();
      this.needsUpdate = true;
   }

   @Override
   public final void onTickServer() {
      if (this.needsUpdate) {
         if (this.isInputForcedDown(Input.CLICK_LEFT)) {
            this.setInputForceState(Input.CLICK_RIGHT, false);
         }

         LivingEntity entity = this.ctx.entity();
         entity.xxa = 0.0F;
         entity.zza = 0.0F;
         entity.setShiftKeyDown(false);
         entity.setJumping(this.isInputForcedDown(Input.JUMP));
         float speed = 0.3F;
         if (this.isInputForcedDown(Input.MOVE_FORWARD)) {
            entity.zza += speed;
         }

         if (this.isInputForcedDown(Input.MOVE_BACK)) {
            entity.zza -= speed;
         }

         if (this.isInputForcedDown(Input.MOVE_LEFT)) {
            entity.xxa += speed;
         }

         if (this.isInputForcedDown(Input.MOVE_RIGHT)) {
            entity.xxa -= speed;
         }

         if (this.isInputForcedDown(Input.SNEAK)) {
            entity.setShiftKeyDown(true);
            entity.xxa = (float)(entity.xxa * 0.3);
            entity.zza = (float)(entity.zza * 0.3);
         }

         boolean jumpNow = this.isInputForcedDown(Input.JUMP);
         LivingEntityHungerManager hm = this.ctx.hungerManager();
         if (hm != null) {
            if (jumpNow && !this.wasJumping) {
               float jumpExhaustion = entity.isSprinting() ? 0.2F : 0.05F;
               hm.addExhaustion(jumpExhaustion);
            }

            if (entity.isInWater() && (entity.zza != 0.0F || entity.xxa != 0.0F)) {
               hm.addExhaustion(0.01F);
            }
         }

         this.wasJumping = jumpNow;
         this.blockBreakHelper.tick(this.isInputForcedDown(Input.CLICK_LEFT));
         this.blockPlaceHelper.tick(this.isInputForcedDown(Input.CLICK_RIGHT));
         this.needsUpdate = false;
      }
   }

   public BlockBreakHelper getBlockBreakHelper() {
      return this.blockBreakHelper;
   }
}
