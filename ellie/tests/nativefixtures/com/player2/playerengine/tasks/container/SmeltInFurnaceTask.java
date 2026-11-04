package com.player2.playerengine.tasks.container;
import com.player2.playerengine.tasks.base.Task;
/** Production boundary, never packaged. */
public final class SmeltInFurnaceTask extends Task{public Object furnacePos=new Object();public boolean isSmelting=true;public Object[] targets;public SmeltInFurnaceTask(Object target){targets=new Object[]{target};}}
