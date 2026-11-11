package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.PlayerEngineSettings;
import com.player2.playerengine.multiversion.FoodComponentWrapper;
import com.player2.playerengine.multiversion.item.ItemVer;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.reliability.FoodSurvival;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.tasks.misc.EatFoodTask;
import com.player2.playerengine.tasks.resources.CollectFoodTask;
import com.player2.playerengine.tasks.speedrun.DragonBreathTracker;
import com.player2.playerengine.util.helpers.ConfigHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.Optional;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;

public class FoodChain extends SingleTaskChain {
   private static FoodChain.FoodChainConfig config;
   private static boolean hasFood;
   private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
   private boolean isTryingToEat = false;
   private boolean requestFillup = false;
   private boolean needsToCollectFood = false;
   private Optional<Item> cachedPerfectFood = Optional.empty();
   private boolean shouldStop = false;
   private static final int STARVING_FOOD_LEVEL = 6;
   private boolean starvationEpisodeActive = false;
   private boolean recoveryInfoPending = false;
   private Object ellieFoodRecovery;

   public FoodChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(PlayerEngineController controller) {
      if (this.isTryingToEat) {
         this.isTryingToEat = false;
         if (controller.getItemStorage().hasItem(Items.SHIELD) && !controller.getItemStorage().hasItemInOffhand(controller, Items.SHIELD)) {
            controller.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
         }
      }
   }

   private void startEat(PlayerEngineController controller, Item food) {
      this.setTask(new EatFoodTask(food));
      this.isTryingToEat = true;
      this.requestFillup = true;
   }

   private void stopEat(PlayerEngineController controller) {
      if (this.isTryingToEat) {
         this.setTask(null);
         this.isTryingToEat = false;
         this.requestFillup = false;
         if (controller.getItemStorage().hasItem(Items.SHIELD) && !controller.getItemStorage().hasItemInOffhand(controller, Items.SHIELD)) {
            controller.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
         }
      }
   }

   public boolean isTryingToEat() {
      return this.isTryingToEat;
   }

   @Override
   public float getPriority() {
      FoodSurvival.before(this);
      if (this.controller == null) {
         return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
      }

      if (WorldHelper.isInNetherPortal(this.controller)) {
         this.stopEat(this.controller);
         return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
      }

      if (this.controller.getMobDefenseChain().isShielding()) {
         this.stopEat(this.controller);
         return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
      }

      this.dragonBreathTracker.updateBreath(this.controller);

      for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(this.controller.getEntity())) {
         if (this.dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
            this.stopEat(this.controller);
            return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
         }
      }

      if (this.controller.getModSettings().isAutoEat()
         && this.controller.getModSettings().isHungerEnabled()
         && !this.controller.getEntity().isInLava()
         && !this.shouldStop) {
         if (this.controller.getMLGBucketChain().doneMLG() && !this.controller.getMLGBucketChain().isFalling(this.controller)) {
            Tuple<Integer, Optional<Item>> calculation = this.calculateFood(this.controller);
            int foodScore = (Integer)calculation.getA();
            this.cachedPerfectFood = (Optional<Item>)calculation.getB();
            hasFood = foodScore > 0;
            if (this.requestFillup && this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel() >= 20) {
               this.requestFillup = false;
            }

            if (!hasFood) {
               this.requestFillup = false;
            }

            if (hasFood && (this.needsToEat() || this.requestFillup) && this.cachedPerfectFood.isPresent()) {
               this.startEat(this.controller, this.cachedPerfectFood.get());
            } else {
               this.stopEat(this.controller);
            }

            boolean isStarving = this.controller.getModSettings().isHungerEnabled()
               && !hasFood
               && this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel() <= 6;
            FoodRecoveryInfoPolicy.State recovery = FoodRecoveryInfoPolicy.advance(
               this.starvationEpisodeActive,
               this.recoveryInfoPending,
               isStarving,
               this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel(),
               this.isTryingToEat,
               this.requestFillup
            );
            this.starvationEpisodeActive = recovery.episodeActive();
            this.recoveryInfoPending = recovery.recoveryInfoPending();
            if (recovery.notifyPlayer()) {
               this.controller.reportAgenticProgress(Component.translatable("message.playerengine.food.starving"), true);
            }

            if (recovery.deferModelInfo()) {
               AiConversationFeedback.deferInfo(this.controller, "Earlier you were starving with no food, but you have since recovered.");
            }

            if (this.isTryingToEat && this.mainTask != null && !this.mainTask.isFinished()) {
               return FoodSurvival.priority(this, 55.0F);
            }

            PlayerEngineSettings settings = this.controller.getModSettings();
            if (this.needsToCollectFood || foodScore < settings.getMinimumFoodAllowed()) {
               this.needsToCollectFood = foodScore < settings.getFoodUnitsToCollect();
               if (this.needsToCollectFood) {
                  this.setTask(new CollectFoodTask(settings.getFoodUnitsToCollect()));
                  return FoodSurvival.priority(this, 55.0F);
               }
            }

            this.setTask(null);
            return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
         } else {
            this.stopEat(this.controller);
            return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
         }
      } else {
         this.stopEat(this.controller);
         return FoodSurvival.priority(this, Float.NEGATIVE_INFINITY);
      }
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   public String getName() {
      return "Food chain";
   }

   @Override
   protected void onStop() {
      super.onStop();
      if (this.controller != null) {
         this.stopEat(this.controller);
      }
   }

   public boolean needsToEat() {
      if (hasFood && !this.shouldStop) {
         LivingEntity player = this.controller.getEntity();
         int foodLevel = this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel();
         float health = player.getHealth();
         if (foodLevel >= 20) {
            return false;
         } else if (health <= 10.0F) {
            return true;
         } else if (player.isOnFire() || player.hasEffect(MobEffects.WITHER) || health < config.alwaysEatWhenWitherOrFireAndHealthBelow) {
            return true;
         } else if (foodLevel <= config.alwaysEatWhenBelowHunger) {
            return true;
         } else if (health < config.alwaysEatWhenBelowHealth) {
            return true;
         } else if (foodLevel < config.alwaysEatWhenBelowHungerAndPerfectFit && this.cachedPerfectFood.isPresent()) {
            int need = 20 - foodLevel;
            Item best = this.cachedPerfectFood.get();
            int fills = Optional.ofNullable(ItemVer.getFoodComponent(best)).map(FoodComponentWrapper::getHunger).orElse(-1);
            return fills > 0 && fills <= need;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private Tuple<Integer, Optional<Item>> calculateFood(PlayerEngineController controller) {
      Item bestFood = null;
      double bestFoodScore = Double.NEGATIVE_INFINITY;
      int foodTotal = 0;
      LivingEntity player = controller.getEntity();
      float health = player.getHealth();
      float hunger = controller.getBaritone().getEntityContext().hungerManager().getFoodLevel();
      float saturation = controller.getBaritone().getEntityContext().hungerManager().getSaturationLevel();

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (ItemVer.isFood(stack) && !stack.is(Items.SPIDER_EYE)) {
            FoodComponentWrapper food = ItemVer.getFoodComponent(stack.getItem());
            if (food != null) {
               float hungerIfEaten = Math.min(hunger + food.getHunger(), 20.0F);
               float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.getSaturationModifier());
               float gainedSaturation = saturationIfEaten - saturation;
               float gainedHunger = hungerIfEaten - hunger;
               float hungerWasted = food.getHunger() - gainedHunger;
               float score = gainedSaturation * 2.0F - hungerWasted;
               if (stack.is(Items.ROTTEN_FLESH)) {
                  score -= 100.0F;
               }

               if (score > bestFoodScore) {
                  bestFoodScore = score;
                  bestFood = stack.getItem();
               }

               foodTotal += food.getHunger() * stack.getCount();
            }
         }
      }

      return new Tuple(foodTotal, Optional.ofNullable(bestFood));
   }

   public boolean hasFood() {
      return hasFood;
   }

   public boolean isStarving() {
      if (this.controller != null && this.controller.getModSettings().isHungerEnabled()) {
         int ownFoodScore = (Integer)this.calculateFood(this.controller).getA();
         int food = this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel();
         return ownFoodScore <= 0 && food <= 6;
      } else {
         return false;
      }
   }

   public void shouldStop(boolean shouldStopInput) {
      this.shouldStop = shouldStopInput;
   }

   public boolean isShouldStop() {
      return this.shouldStop;
   }

   static {
      ConfigHelper.loadConfig(
         "configs/food_chain_settings.json", FoodChain.FoodChainConfig::new, FoodChain.FoodChainConfig.class, newConfig -> config = newConfig
      );
   }

   static class FoodChainConfig {
      public int alwaysEatWhenWitherOrFireAndHealthBelow = 6;
      public int alwaysEatWhenBelowHunger = 10;
      public int alwaysEatWhenBelowHealth = 14;
      public int alwaysEatWhenBelowHungerAndPerfectFit = 15;
   }
}
