package com.player2.playerengine.tasks.container;
import com.player2.playerengine.tasks.base.Task;
/** Production boundary, never packaged. */
public final class SmeltInFurnaceTask extends Task{public Object furnacePos=new Object();public boolean isSmelting=true;public Object[] targets;public SmeltInFurnaceTask(Object target){targets=new Object[]{target};}
 protected void onStart(){}
 protected com.player2.playerengine.tasks.base.Task onTick(){return null;}
 protected void onStop(com.player2.playerengine.tasks.base.Task next){}
 protected boolean isEqual(com.player2.playerengine.tasks.base.Task other){return this==other;}
 protected String toDebugString(){return "fixture";}
}
