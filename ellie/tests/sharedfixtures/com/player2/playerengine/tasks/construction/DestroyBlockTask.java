package com.player2.playerengine.tasks.construction;
import com.player2.playerengine.tasks.base.Task;import net.minecraft.class_2338;
import com.player2.playerengine.reliability.SharedRecoveryRuntimeTest.Controller;
/** Simulated native excavation boundary, never packaged. */
public final class DestroyBlockTask extends Task {
 private final class_2338 pos;public DestroyBlockTask(class_2338 pos){this.pos=pos;}
 protected void onStart(){((Controller)controller).baritone.builder.schematic=new Object();}
 protected Task onTick(){Controller c=(Controller)controller;c.world.blocks.remove(pos);c.world.obstruction=null;c.recoveryClears++;return null;}
 protected void onStop(Task next){Controller c=(Controller)controller;c.baritone.builder.schematic=null;c.recoveryClearStops++;}protected boolean isEqual(Task other){return this==other;}protected String toDebugString(){return "clear fixture";}
 public boolean isFinished(){return ((Controller)controller).world.method_8320(pos).method_26215();}
}
