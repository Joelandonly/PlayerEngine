package com.player2.playerengine.reliability;
import java.util.*;
/** Progress belongs to the running job, not to whichever target the selector last returned. */
public final class TargetRecoveryPolicy {
 private String target="";
 private long lastProgress,latestTick,movementWindowStart;
 private boolean initialized,verifiedWork,inventorySeen;
 private int attempts;
 private final Map<String,Double> closest=new HashMap<>(), damage=new HashMap<>();
 private final Map<String,String> blocks=new HashMap<>();
 private final Map<String,Integer> inventory=new HashMap<>();
 private final Set<String> visited=new HashSet<>();
 public boolean stalled(String target,String progress,double x,double y,double z,long tick,boolean canInteract){return stalled(target,progress,x,y,z,tick,canInteract,0);}
 public boolean stalled(String target,String progress,double x,double y,double z,long tick,boolean canInteract,double breaking){
  if(initialized&&tick<latestTick)reset();
  boolean first=!initialized;
  if(first){initialized=true;lastProgress=movementWindowStart=tick;}
  latestTick=tick;this.target=target;verifiedWork=observeWork(target,progress);
  double previousDamage=damage.getOrDefault(target,0d);
  // Damage grants time to finish a slow block, but never replenishes recovery attempts.
  boolean advancingDamage=breaking>previousDamage+.0001;
  if(damage.size()<2048||damage.containsKey(target))damage.put(target,Math.max(previousDamage,breaking));
  if(verifiedWork){lastProgress=movementWindowStart=tick;attempts=0;damage.clear();closest.clear();visited.clear();}
  if(advancingDamage&&tick-movementWindowStart<1800)lastProgress=tick;
  String cell=(long)Math.floor(x*2)+","+(long)Math.floor(y*2)+","+(long)Math.floor(z*2);
  boolean novel=visited.size()<4096&&visited.add(cell);
  double distance=distanceToTarget(target,x,y,z);
  double best=closest.getOrDefault(target,Double.POSITIVE_INFINITY);
  // Retain high-water distances for ALL targets. Switching A/B cannot credit the
  // same positions again, and novel wandering has one bounded exploration window.
  if(novel&&!verifiedWork){
   if(Double.isFinite(distance)){
    if(distance+.4<best && (closest.size()<2048||closest.containsKey(target))){closest.put(target,distance);lastProgress=tick;}
   }else if(tick-movementWindowStart<900)lastProgress=tick;
  }
  return tick-lastProgress>=100||(!Double.isFinite(distance)&&tick-movementWindowStart>=900);
 }
 private boolean observeWork(String target,String signature){
  boolean work=false;String[] parts=signature.split("\\|",-1);String state=parts[0];
  String previous=blocks.get(target);
  // Same block with changed properties or a changed target is not a broken block.
  if(previous!=null&&!previous.equals(state)){
   String oldId=previous.split("\\[",2)[0],newId=state.split("\\[",2)[0];
   work=!oldId.equals(newId);
  }
  if(blocks.size()<2048||previous!=null)blocks.put(target,state);
  for(int i=1;i<parts.length;i++){
   int equals=parts[i].lastIndexOf('=');if(equals<=0)continue;
   try{String item=parts[i].substring(0,equals);int count=Integer.parseInt(parts[i].substring(equals+1));
    Integer prior=inventory.get(item);if(inventorySeen&&count>(prior==null?0:prior))work=true;
    inventory.put(item,Math.max(count,prior==null?count:prior));
   }catch(NumberFormatException ignored){}
  }
  inventorySeen=true;return work;
 }
 private static double distanceToTarget(String key,double x,double y,double z){
  try{String[] parts=key.split(",",-1);if(parts.length!=3)return Double.POSITIVE_INFINITY;
   return Math.sqrt(Math.pow(Double.parseDouble(parts[0])+.5-x,2)+Math.pow(Double.parseDouble(parts[1])-y,2)+Math.pow(Double.parseDouble(parts[2])+.5-z,2));
  }catch(NumberFormatException missing){return Double.POSITIVE_INFINITY;}
 }
 public boolean verifiedWork(){return verifiedWork;}
 public long idleTicks(long tick){return Math.max(0,tick-lastProgress);}
 public boolean nextAttempt(){if(attempts>=3)return false;attempts++;return true;}
 public int attempts(){return attempts;}
 public void resume(long tick,boolean verifiedProgress){lastProgress=tick;if(verifiedProgress){attempts=0;movementWindowStart=tick;damage.clear();closest.clear();visited.clear();}}
 public void reset(){target="";initialized=false;verifiedWork=false;inventorySeen=false;attempts=0;visited.clear();closest.clear();damage.clear();blocks.clear();inventory.clear();lastProgress=latestTick=movementWindowStart=0;}
}
