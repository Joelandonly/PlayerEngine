package com.player2.playerengine.tasks.crafting;
import java.util.Optional;
import com.player2.playerengine.reliability.SharedTargetRecovery;
import com.player2.playerengine.nativecommands.NativeAccess;
/** Compatibility hooks; shared task-layer recovery now owns stalls and target rejection. */
public final class CraftTableApproachRecovery {
 private CraftTableApproachRecovery(){}
 public static void craftTick(Object task,Object controller){} // No independent crafting watchdog.
 public static Optional<?> filterReusable(Object controller,Optional<?> found){
  Optional<?> existing=found==null?Optional.empty():found;
  if(existing.isPresent()&&SharedTargetRecovery.rejected(controller,existing.get()))existing=Optional.empty();
  return com.player2.playerengine.reliability.WorksiteAnchor.preferBench(controller,existing);
 }
 public static Optional<?> filterReachable(Object controller,Optional<?> found){
  if(found==null||found.isEmpty())return Optional.empty();
  try{Object pos=NativeAccess.call(found.get(),"pos");if(SharedTargetRecovery.rejected(controller,pos))return Optional.empty();com.player2.playerengine.reliability.WorksiteAnchor.recordBench(controller,pos);return found;}
  catch(Exception error){return found;}
 }
}
