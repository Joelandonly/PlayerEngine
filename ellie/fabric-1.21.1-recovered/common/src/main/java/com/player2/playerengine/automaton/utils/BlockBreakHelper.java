package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.safezone.SafeZoneService;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public final class BlockBreakHelper {
   private final IEntityContext ctx;
   @Nullable
   private BlockPos lastPos;

   BlockBreakHelper(IEntityContext ctx) {
      this.ctx = ctx;
   }

   public void stopBreakingBlock() {
      if (this.ctx.entity() != null && this.lastPos != null) {
         if (!this.ctx.playerController().hasBrokenBlock()) {
            this.ctx.playerController().setHittingBlock(true);
         }

         this.ctx.playerController().resetBlockRemoving();
         this.lastPos = null;
      }
   }

   public void tick(boolean isLeftClick) {
      HitResult trace = this.ctx.objectMouseOver();
      boolean isBlockTrace = trace != null && trace.getType() == Type.BLOCK;
      if (isLeftClick && isBlockTrace) {
         BlockPos pos = ((BlockHitResult)trace).getBlockPos();
         if (SafeZoneService.protectedAt(this.ctx.world(), pos)
            || this.ctx.baritone().getBuilderProcess() instanceof com.player2.playerengine.automaton.process.BuilderProcess builder
               && !builder.permitsFootprintEdit(pos, this.ctx.world().getBlockState(pos), null)) {
            this.stopBreakingBlock();
            return;
         }
         if (!Objects.equals(this.lastPos, pos)) {
            this.ctx.playerController().clickBlock(pos, ((BlockHitResult)trace).getDirection());
            this.ctx.entity().swing(InteractionHand.MAIN_HAND);
         }

         boolean mining = this.ctx.playerController().onPlayerDamageBlock(pos, ((BlockHitResult)trace).getDirection());
         if (mining) {
            this.ctx.entity().swing(InteractionHand.MAIN_HAND);
            if (this.ctx.world().isEmptyBlock(pos)) {
               LivingEntityHungerManager hm = this.ctx.hungerManager();
               if (hm != null) {
                  hm.addExhaustion(0.005F);
               }
            }
         }

         this.ctx.playerController().setHittingBlock(false);
         // A cancelled or rejected break needs a new START even if the cursor never left this block.
         this.lastPos = mining ? pos : null;
      } else if (this.lastPos != null) {
         this.stopBreakingBlock();
         this.lastPos = null;
      }
   }
}
