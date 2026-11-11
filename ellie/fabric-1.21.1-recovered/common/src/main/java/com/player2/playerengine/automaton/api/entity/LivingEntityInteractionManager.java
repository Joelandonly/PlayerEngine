package com.player2.playerengine.automaton.api.entity;

import com.mojang.logging.LogUtils;
import com.player2.playerengine.automaton.api.utils.IBucketAccessor;
import com.player2.playerengine.reliability.NpcItemUse;
import com.player2.playerengine.util.EnchantmentUtils;
import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import com.player2.playerengine.safezone.SafeZoneService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class LivingEntityInteractionManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   protected Level world;
   protected final LivingEntity livingEntity;
   private GameType gameMode = GameType.SURVIVAL;
   @Nullable
   private GameType previousGameMode;
   private boolean mining;
   private int startMiningTime;
   private BlockPos miningPos = BlockPos.ZERO;
   private int tickCounter;
   private boolean failedToMine;
   private BlockPos failedMiningPos = BlockPos.ZERO;
   private int failedStartMiningTime;
   private int blockBreakingProgress = -1;
   private boolean brokeBlock;

   public LivingEntityInteractionManager(LivingEntity livingEntity) {
      this.livingEntity = livingEntity;
      this.world = livingEntity.level();
   }

   public GameType getGameMode() {
      return this.gameMode;
   }

   @Nullable
   public GameType getPreviousGameMode() {
      return this.previousGameMode;
   }

   public boolean isSurvivalLike() {
      return this.gameMode.isSurvival();
   }

   public boolean isCreative() {
      return this.gameMode.isCreative();
   }

   public void update() {
      // A claim can be created while Ellie is already mining: cancel the in-flight action.
      if ((this.mining && SafeZoneService.protectedAt(this.world,this.miningPos)) ||
          (this.failedToMine && SafeZoneService.protectedAt(this.world,this.failedMiningPos))) {
         if (this.mining) this.world.destroyBlockProgress(this.livingEntity.getId(),this.miningPos,-1);
         if (this.failedToMine) this.world.destroyBlockProgress(this.livingEntity.getId(),this.failedMiningPos,-1);
         this.mining=false;this.failedToMine=false;this.blockBreakingProgress=-1;
      }
      this.tickCounter++;
      if (this.failedToMine) {
         BlockState blockState = this.world.getBlockState(this.failedMiningPos);
         if (blockState.isAir()) {
            this.failedToMine = false;
         } else {
            float f = this.continueMining(blockState, this.failedMiningPos, this.failedStartMiningTime);
            if (f >= 1.0F) {
               this.failedToMine = false;
               this.tryBreakBlock(this.failedMiningPos);
            }
         }
      } else if (this.mining) {
         BlockState blockState = this.world.getBlockState(this.miningPos);
         if (blockState.isAir()) {
            this.world.destroyBlockProgress(this.livingEntity.getId(), this.miningPos, -1);
            this.blockBreakingProgress = -1;
            this.mining = false;
         } else {
            this.continueMining(blockState, this.miningPos, this.startMiningTime);
         }
      }
   }

   private float continueMining(BlockState state, BlockPos pos, int progress) {
      int i = this.tickCounter - progress;
      float f = this.calcBlockBreakingDelta(state, this.livingEntity, this.livingEntity.level(), pos) * (i + 1);
      int j = (int)(f * 10.0F);
      if (j != this.blockBreakingProgress) {
         this.world.destroyBlockProgress(this.livingEntity.getId(), pos, j);
         this.blockBreakingProgress = j;
      }

      return f;
   }

   private void debugLogging(BlockPos pos, boolean bl, int i, String string) {
   }

   public void processBlockBreakingAction(BlockPos pos, Action action, Direction direction, int worldHeight, int i) {
      if(action!=Action.ABORT_DESTROY_BLOCK && SafeZoneService.deny(this.world,pos,"break")) {
         if(this.mining)this.world.destroyBlockProgress(this.livingEntity.getId(),this.miningPos,-1);
         if(this.failedToMine)this.world.destroyBlockProgress(this.livingEntity.getId(),this.failedMiningPos,-1);
         this.mining=false;this.failedToMine=false;this.blockBreakingProgress=-1;
         return;
      }
      if (this.livingEntity.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > Mth.square(6.0)) {
         this.debugLogging(pos, false, i, "too far");
      } else if (pos.getY() >= worldHeight) {
         this.debugLogging(pos, false, i, "too high");
      } else if (action == Action.START_DESTROY_BLOCK) {
         if (this.isCreative()) {
            this.finishMining(pos, i, "creative destroy");
            return;
         }

         this.startMiningTime = this.tickCounter;
         float f = 1.0F;
         BlockState blockState = this.world.getBlockState(pos);
         if (!blockState.isAir()) {
            f = this.calcBlockBreakingDelta(blockState, this.livingEntity, this.livingEntity.level(), pos);
         }

         if (!blockState.isAir() && f >= 1.0F) {
            this.finishMining(pos, i, "insta mine");
         } else {
            if (this.mining) {
               this.debugLogging(pos, false, i, "abort destroying since another started (client insta mine, server disagreed)");
            }

            this.mining = true;
            this.miningPos = pos.immutable();
            this.brokeBlock = true;
            int j = (int)(f * 10.0F);
            this.world.destroyBlockProgress(this.livingEntity.getId(), pos, j);
            this.debugLogging(pos, true, i, "actual start of destroying");
            this.blockBreakingProgress = j;
         }
      } else if (action == Action.STOP_DESTROY_BLOCK) {
         if (pos.equals(this.miningPos)) {
            int k = this.tickCounter - this.startMiningTime;
            BlockState blockStatex = this.world.getBlockState(pos);
            if (!blockStatex.isAir()) {
               float g = this.calcBlockBreakingDelta(blockStatex, this.livingEntity, this.livingEntity.level(), pos) * (k + 1);
               if (g >= 0.7F) {
                  this.mining = false;
                  this.world.destroyBlockProgress(this.livingEntity.getId(), pos, -1);
                  this.finishMining(pos, i, "destroyed");
                  return;
               }

               if (!this.failedToMine) {
                  this.mining = false;
                  this.failedToMine = true;
                  this.failedMiningPos = pos;
                  this.failedStartMiningTime = this.startMiningTime;
               }
            }
         }

         this.debugLogging(pos, true, i, "stopped destroying");
      } else if (action == Action.ABORT_DESTROY_BLOCK) {
         this.mining = false;
         if (!Objects.equals(this.miningPos, pos)) {
            LOGGER.warn("Mismatch in destroy block pos: {} {}", this.miningPos, pos);
            this.world.destroyBlockProgress(this.livingEntity.getId(), this.miningPos, -1);
            this.debugLogging(pos, true, i, "aborted mismatched destroying");
         }

         this.world.destroyBlockProgress(this.livingEntity.getId(), pos, -1);
         this.debugLogging(pos, true, i, "aborted destroying");
      }
   }

   public float calcBlockBreakingDelta(BlockState state, LivingEntity player, BlockGetter world, BlockPos pos) {
      float f = state.getDestroySpeed(world, pos);
      if (f == -1.0F) {
         return 0.0F;
      }

      int i = this.canHarvest(state, player.getItemInHand(InteractionHand.MAIN_HAND)) ? 30 : 100;
      return this.getBlockBreakingSpeed(player, state) / f / i;
   }

   public boolean canHarvest(BlockState state, ItemStack heldItem) {
      return !state.requiresCorrectToolForDrops() || heldItem.isCorrectToolForDrops(state);
   }

   public float getBlockBreakingSpeed(LivingEntity entity, BlockState block) {
      float f = this.livingEntity.getItemInHand(InteractionHand.MAIN_HAND).getDestroySpeed(block);
      if (f > 1.0F) {
         ItemStack itemStack = this.livingEntity.getItemInHand(InteractionHand.MAIN_HAND);
         int i = EnchantmentUtils.getEnchantmentLevel(itemStack, Enchantments.EFFICIENCY);
         if (i > 0 && !itemStack.isEmpty()) {
            f += i * i + 1;
         }
      }

      if (MobEffectUtil.hasDigSpeed(entity)) {
         f *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(entity) + 1) * 0.2F;
      }

      if (entity.hasEffect(MobEffects.DIG_SLOWDOWN)) {
         f *= switch (entity.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      if (entity.isEyeInFluid(FluidTags.WATER)
         && EnchantmentUtils.getEnchantmentLevel(entity.getItemBySlot(EquipmentSlot.HEAD), Enchantments.AQUA_AFFINITY) != 0) {
         f /= 5.0F;
      }

      if (!entity.onGround()) {
         f /= 5.0F;
      }

      return f;
   }

   public void finishMining(BlockPos pos, int i, String reason) {
      if (this.tryBreakBlock(pos)) {
         this.debugLogging(pos, true, i, reason);
      } else {
         this.debugLogging(pos, false, i, reason);
      }
   }

   public boolean tryBreakBlock(BlockPos pos) {
      if (SafeZoneService.deny(this.world,pos,"break-final"))return false;
      BlockState blockState = this.world.getBlockState(pos);
      BlockEntity blockEntity = this.world.getBlockEntity(pos);
      Block block = blockState.getBlock();
      if (block instanceof GameMasterBlock) {
         this.world.sendBlockUpdated(pos, blockState, blockState, 3);
         return false;
      }

      this.world.gameEvent(GameEvent.BLOCK_DESTROY, pos, Context.of(this.livingEntity, blockState));
      boolean bl = this.world.removeBlock(pos, false);
      if (bl) {
         block.destroy(this.world, pos, blockState);
      }

      if (this.isCreative()) {
         return true;
      }

      ItemStack itemStack = this.livingEntity.getMainHandItem();
      ItemStack itemStack2 = itemStack.copy();
      boolean bl2 = this.canHarvest(blockState, itemStack2);
      itemStack.getItem().mineBlock(itemStack, this.world, blockState, pos, this.livingEntity);
      if (bl && bl2) {
         Block.dropResources(blockState, this.world, pos, blockEntity, this.livingEntity, itemStack2);
      }

      return true;
   }

   public InteractionResult interactItem(LivingEntity player, Level world, ItemStack stack, InteractionHand hand) {
      if (this.gameMode == GameType.SPECTATOR) {
         return InteractionResult.PASS;
      }

      int i = stack.getCount();
      int j = stack.getDamageValue();

      try {
         InteractionResultHolder<ItemStack> typedActionResult;
         if (stack.getItem() instanceof BucketItem bucketItem) {
            typedActionResult = this.useBucket(bucketItem, world, player, hand);
         } else {
            typedActionResult = stack.use(world, null, hand);
         }

         ItemStack itemStack = (ItemStack)typedActionResult.getObject();
         if (itemStack == stack && itemStack.getCount() == i && itemStack.getUseDuration(player) <= 0 && itemStack.getDamageValue() == j) {
            return typedActionResult.getResult();
         }

         if (typedActionResult.getResult() == InteractionResult.FAIL && itemStack.getUseDuration(player) > 0 && !player.isUsingItem()) {
            return typedActionResult.getResult();
         }

         if (stack != itemStack) {
            player.setItemInHand(hand, itemStack);
         }

         if (this.isCreative() && itemStack != ItemStack.EMPTY) {
            itemStack.setCount(i);
            if (itemStack.isDamageableItem() && itemStack.getDamageValue() != j) {
               itemStack.setDamageValue(j);
            }
         }

         if (itemStack.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
         }

         return typedActionResult.getResult();
      } catch (Exception var10) {
         return InteractionResult.PASS;
      }
   }

   public InteractionResultHolder<ItemStack> useBucket(BucketItem bucket, Level world, LivingEntity user, InteractionHand hand) {
      ItemStack itemStack = user.getItemInHand(hand);
      BlockHitResult blockHitResult = raycast(
         world, user, ((IBucketAccessor)bucket).getFluid() == Fluids.EMPTY ? Fluid.SOURCE_ONLY : Fluid.NONE
      );
      if (blockHitResult.getType() == Type.MISS) {
         return InteractionResultHolder.pass(itemStack);
      }

      if (blockHitResult.getType() != Type.BLOCK) {
         return InteractionResultHolder.pass(itemStack);
      }

      BlockPos blockPos = blockHitResult.getBlockPos();
      Direction direction = blockHitResult.getDirection();
      if(SafeZoneService.deny(world,blockPos,"bucket")||
         SafeZoneService.deny(world,blockPos.relative(direction),"bucket"))
         return InteractionResultHolder.fail(itemStack);
      BlockPos blockPos2 = blockPos.relative(direction);
      if (((IBucketAccessor)bucket).getFluid() == Fluids.EMPTY) {
         BlockState blockState = world.getBlockState(blockPos);
         if (blockState.getBlock() instanceof BucketPickup) {
            BucketPickup fluidDrainable = (BucketPickup)blockState.getBlock();
            ItemStack itemStack2 = fluidDrainable.pickupBlock(null, world, blockPos, blockState);
            if (!itemStack2.isEmpty()) {
               fluidDrainable.getPickupSound().ifPresent(sound -> user.playSound(sound, 1.0F, 1.0F));
               world.gameEvent(user, GameEvent.FLUID_PICKUP, blockPos);
               ItemStack itemStack3 = exchangeStack(itemStack, user, itemStack2);
               return InteractionResultHolder.sidedSuccess(itemStack3, world.isClientSide());
            }
         }

         return InteractionResultHolder.fail(itemStack);
      } else {
         BlockState blockState = world.getBlockState(blockPos);
         BlockPos blockPos3 = blockState.getBlock() instanceof LiquidBlockContainer && ((IBucketAccessor)bucket).getFluid() == Fluids.WATER
            ? blockPos
            : blockPos2;
         if (bucket.emptyContents(null, world, blockPos3, blockHitResult)) {
            bucket.checkExtraContent(null, world, itemStack, blockPos3);
            return InteractionResultHolder.sidedSuccess(new ItemStack(Items.BUCKET), world.isClientSide());
         } else {
            return InteractionResultHolder.fail(itemStack);
         }
      }
   }

   public boolean canPlaceOn(LivingEntity entity, BlockPos pos, Direction facing, ItemStack stack) {
      BlockPos blockPos = pos.relative(facing.getOpposite());
      BlockInWorld cachedBlockPosition = new BlockInWorld(entity.level(), blockPos, false);
      return stack.canPlaceOnBlockInAdventureMode(cachedBlockPosition);
   }

   protected static BlockHitResult raycast(Level world, LivingEntity player, Fluid fluidHandling) {
      float f = player.getXRot();
      float g = player.getYRot();
      Vec3 vec3d = player.getEyePosition();
      float h = Mth.cos(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
      float i = Mth.sin(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
      float j = -Mth.cos(-f * (float) (Math.PI / 180.0));
      float k = Mth.sin(-f * (float) (Math.PI / 180.0));
      float l = i * j;
      float n = h * j;
      double d = 5.0;
      Vec3 vec3d2 = vec3d.add(l * 5.0, k * 5.0, n * 5.0);
      return world.clip(new ClipContext(vec3d, vec3d2, ClipContext.Block.OUTLINE, fluidHandling, player));
   }

   public static ItemStack exchangeStack(ItemStack inputStack, LivingEntity player, ItemStack outputStack) {
      inputStack.shrink(1);
      if (inputStack.isEmpty()) {
         return outputStack;
      }

      if (!((IInventoryProvider)player).getLivingInventory().insertStack(outputStack)) {
         player.spawnAtLocation(outputStack);
      }

      return inputStack;
   }

   public boolean shouldCancelInteraction() {
      return false;
   }

   public InteractionResult interactBlock(LivingEntity player, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
      BlockPos blockPos = hitResult.getBlockPos();
      BlockState blockState = world.getBlockState(blockPos);
      if (!blockState.getBlock().isEnabled(world.enabledFeatures())) {
         return InteractionResult.FAIL;
      }

      boolean bl = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
      boolean bl2 = this.shouldCancelInteraction() && bl;
      ItemStack itemStack = stack.copy();
      if (!bl2) {
         ItemInteractionResult itemResult = ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

         try {
            itemResult = blockState.useItemOn(stack, world, null, hand, hitResult);
            if (itemResult.consumesAction()) {
               return itemResult.result();
            }
         } catch (NullPointerException var22) {
         }

         if (itemResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
            try {
               InteractionResult blockResult = blockState.useWithoutItem(world, null, hitResult);
               if (blockResult.consumesAction()) {
                  return blockResult;
               }
            } catch (NullPointerException var21) {
            }
         }
      }

      if (!stack.isEmpty()) {
         final LivingEntity placer = player;
         final Player vanillaPlayer = player instanceof Player p ? p : null;
         UseOnContext itemUsageContext = new UseOnContext(player.level(), vanillaPlayer, hand, player.getItemInHand(hand), hitResult) {
            public Direction getHorizontalDirection() {
               return vanillaPlayer != null ? super.getHorizontalDirection() : Direction.fromYRot(placer.getYRot());
            }

            public float getRotation() {
               return vanillaPlayer != null ? super.getRotation() : placer.getYRot();
            }

            public boolean isSecondaryUseActive() {
               return placer.isShiftKeyDown();
            }
         };
         // OWNER_SAFEZONE_PLACE: block all item changes to claimed blocks, including
         // scaffold and builder BlockItem uses; vanilla chest/door use ran above.
         BlockPos exactPlace = new BlockPlaceContext(itemUsageContext).getClickedPos();
         if(SafeZoneService.deny(world,blockPos,"interact-item") ||
            SafeZoneService.deny(world,exactPlace,"place"))return InteractionResult.FAIL;
         boolean trackLivingForPlacement = vanillaPlayer == null;
         if (trackLivingForPlacement) {
            LivingEntityPlacementContext.push(player);
         }

         InteractionResult actionResult2;
         try {
            if (this.isCreative()) {
               int i = stack.getCount();
               actionResult2 = (InteractionResult)NpcItemUse.use(stack, itemUsageContext);
               stack.setCount(i);
            } else {
               actionResult2 = (InteractionResult)NpcItemUse.use(stack, itemUsageContext);
            }
         } finally {
            if (trackLivingForPlacement) {
               LivingEntityPlacementContext.pop();
            }
         }

         return actionResult2;
      } else {
         return InteractionResult.PASS;
      }
   }

   public void setWorld(ServerLevel world) {
      this.world = world;
   }

   public boolean isMining() {
      return this.mining;
   }

   public BlockPos getMiningPos() {
      return this.miningPos;
   }

   public int getBlockBreakingProgress() {
      return this.blockBreakingProgress;
   }

   public boolean hasBrokenBlock() {
      return this.brokeBlock;
   }
}
