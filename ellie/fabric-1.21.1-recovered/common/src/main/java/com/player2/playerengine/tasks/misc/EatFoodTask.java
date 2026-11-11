package com.player2.playerengine.tasks.misc;

import com.player2.playerengine.tasks.base.SurvivalInterruptTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.trackers.storage.SurvivalConsumptionReceiptClassifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EatFoodTask extends Task implements SurvivalInterruptTask {
   private final Item foodItem;
   private EatFoodTask.State state = EatFoodTask.State.EQUIP;
   private boolean hasAte = false;
   private boolean consumptionRecorded = false;
   private boolean foodCountBaselineCaptured = false;
   private int foodCountBeforeUse = 0;
   private int watchdogTicks = 0;
   private int watchdogMax = 0;

   public EatFoodTask(Item foodItem) {
      this.foodItem = foodItem;
   }

   @Override
   protected void onStart() {
      this.state = EatFoodTask.State.EQUIP;
      this.hasAte = false;
      this.consumptionRecorded = false;
      this.foodCountBaselineCaptured = false;
      this.foodCountBeforeUse = 0;
      this.watchdogTicks = 0;
      this.watchdogMax = 0;
   }

   @Override
   protected Task onTick() {
      LivingEntity entity = this.controller.getEntity();
      switch (this.state) {
         case EQUIP:
            int foodLevel = this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel();
            if (foodLevel >= 20) {
               this.fail("Hunger already at 20 (full), cannot eat.");
               this.hasAte = true;
               return null;
            }

            if (this.controller.getSlotHandler().forceEquipItem(this.foodItem)) {
               this.state = EatFoodTask.State.BEGIN_USE;
               this.setDebugState("Equipped food, starting use");
            }

            return null;
         case BEGIN_USE:
            ItemStack inHand = entity.getItemInHand(InteractionHand.MAIN_HAND);
            int useDuration = inHand.isEmpty() ? 32 : inHand.getUseDuration(entity);
            this.watchdogMax = useDuration + 10;
            this.watchdogTicks = 0;
            this.foodCountBeforeUse = this.countFoodItem();
            this.foodCountBaselineCaptured = true;
            entity.startUsingItem(InteractionHand.MAIN_HAND);
            this.state = EatFoodTask.State.WAIT;
            this.setDebugState("startUsingItem called, waiting for animation to complete");
            return null;
         case WAIT:
            this.watchdogTicks++;
            if (!entity.isUsingItem()) {
               this.state = EatFoodTask.State.COMPLETE;
               this.setDebugState("Use complete, refilling hunger");
               return null;
            } else {
               if (this.watchdogTicks >= this.watchdogMax) {
                  entity.stopUsingItem();
                  this.state = EatFoodTask.State.COMPLETE;
                  this.fail("Eat watchdog expired (" + this.watchdogTicks + " ticks), aborting without refill.");
                  this.hasAte = true;
                  return null;
               }

               return null;
            }
         case COMPLETE:
            if (SurvivalConsumptionReceiptClassifier.shouldRecord(
               this.consumptionRecorded, this.foodCountBaselineCaptured, this.foodCountBeforeUse, this.countFoodItem()
            )) {
               this.controller.getSurvivalConsumptionLedger().recordConsumption(this.foodItem);
               this.consumptionRecorded = true;
            }

            this.controller.getBaritone().getEntityContext().hungerManager().eat(this.foodItem);
            this.hasAte = true;
            this.setDebugState("Hunger refilled after animation");
            return null;
         default:
            return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      LivingEntity entity = this.controller.getEntity();
      if (this.state == EatFoodTask.State.WAIT && entity != null && entity.isUsingItem()) {
         entity.stopUsingItem();
      }
   }

   private int countFoodItem() {
      return this.controller.getItemStorage().getItemCountInventoryOnly(this.foodItem);
   }

   @Override
   public boolean isFinished() {
      return this.hasAte;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof EatFoodTask task && task.foodItem == this.foodItem;
   }

   @Override
   protected String toDebugString() {
      return "Eating food (animation): " + this.foodItem.toString() + " [" + this.state + "]";
   }

   private enum State {
      EQUIP,
      BEGIN_USE,
      WAIT,
      COMPLETE;
   }
}
