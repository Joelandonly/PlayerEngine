package com.player2.playerengine.tasks.misc;
import com.player2.playerengine.tasks.base.Task;import net.minecraft.class_1792;
/** Native task boundary; selection/handoff only, no consumption is simulated. */
public final class EatFoodTask extends Task{public final class_1792 item;public EatFoodTask(class_1792 item){this.item=item;}
 protected void onStart(){}
 protected com.player2.playerengine.tasks.base.Task onTick(){return null;}
 protected void onStop(com.player2.playerengine.tasks.base.Task next){}
 protected boolean isEqual(com.player2.playerengine.tasks.base.Task other){return this==other;}
 protected String toDebugString(){return "fixture";}
}
