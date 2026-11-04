package com.player2.playerengine.reliability;
import java.util.*;
/** Shared progress policy: simulation ticks, verified work, novel positions; bounded recovery attempts. */
public final class TargetRecoveryPolicy {
 private String target="",progress="";
 private long lastProgress,latestTick;
 private int attempts;
 private double breakHighWater;
 private final Set<String> visited=new LinkedHashSet<>();
 public boolean stalled(String target,String progress,double x,double y,double z,long tick,boolean canInteract){return stalled(target,progress,x,y,z,tick,canInteract,0);}
 public boolean stalled(String target,String progress,double x,double y,double z,long tick,boolean canInteract,double breaking){
  if(tick<latestTick||!this.target.equals(target)){this.target=target;this.progress=progress;lastProgress=tick;attempts=0;breakHighWater=0;visited.clear();}
  latestTick=tick;
  String cell=(long)Math.floor(x*2)+","+(long)Math.floor(y*2)+","+(long)Math.floor(z*2);
  boolean newPosition=visited.add(cell);
  if(visited.size()>128)visited.remove(visited.iterator().next());
  if(!this.progress.equals(progress))breakHighWater=0;
  boolean verifiedBreak=breaking>breakHighWater+.0001;breakHighWater=Math.max(breakHighWater,breaking);
  // Reachability permits interaction; it is not proof that the interaction succeeded.
  if(verifiedBreak||!this.progress.equals(progress))attempts=0;
  if(newPosition||verifiedBreak||!this.progress.equals(progress)){lastProgress=tick;this.progress=progress;}
  return tick-lastProgress>=100;
 }
 public boolean nextAttempt(){if(attempts>=3)return false;attempts++;return true;}
 public int attempts(){return attempts;}
 public void resume(long tick,boolean verifiedProgress){lastProgress=tick;if(verifiedProgress){attempts=0;breakHighWater=0;visited.clear();}}
 public void reset(){target="";progress="";attempts=0;visited.clear();lastProgress=latestTick=0;}
}
