package com.player2.playerengine.navigation;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.chains.SingleTaskChain;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskRunner;
import java.lang.reflect.Constructor;

public final class NavRecoveryChain extends SingleTaskChain {
   private int age;
   private boolean active;

   public NavRecoveryChain(TaskRunner var1) {
      super(var1);
   }

   @Override
   public float getPriority() {
      return 70.0F;
   }

   @Override
   public String getName() {
      return "Navigation Recovery";
   }

   @Override
   public boolean isActive() {
      return this.active && super.isActive();
   }

   public boolean begin(int var1, int var2, int var3) {
      if (this.active) {
         return false;
      }

      try {
         Class var4 = Class.forName("net.minecraft.class_2338");
         Object var5 = var4.getConstructor(int.class, int.class, int.class).newInstance(var1, var2, var3);
         Class var6 = Class.forName("com.player2.playerengine.tasks.construction.DestroyBlockTask");
         Constructor var7 = var6.getConstructor(var4);
         Task var8 = (Task)var7.newInstance(var5);
         this.age = 0;
         this.active = true;
         this.setTask(var8);
         return true;
      } catch (Throwable var9) {
         this.active = false;
         return false;
      }
   }

   @Override
   protected void onTick() {
      if (++this.age > 160) {
         this.cancelRecovery();
      } else {
         super.onTick();
      }
   }

   @Override
   protected void onTaskFinish(PlayerEngineController var1) {
      this.active = false;
      this.setTask(null);
      NavStallRecovery.recoveryDone(var1, "completed_or_stopped");
   }

   public void cancelRecovery() {
      if (this.active) {
         this.active = false;
         this.setTask(null);
         NavStallRecovery.recoveryDone(this.controller, "timed_out");
      }
   }

   @Override
   protected void onStop() {
      this.active = false;
      super.onStop();
   }
}
