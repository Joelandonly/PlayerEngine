package com.player2.playerengine.agentic;

import com.player2.playerengine.observatory.ObservatoryTelemetry;
import java.lang.reflect.*;
import java.util.*;
/** Independent read-only check of the NPC's actual main inventory slots.
 * Never treats missing/unavailable inventory data as a full inventory.
 */
public final class InventorySpace {
 private InventorySpace() {}
 public record Snapshot(int total,int used,int free,int trackerEmpty) {
  public boolean known(){return total>0 && free>=0;}
  public String summary(){return known()?"main="+used+"/"+total+" occupied; "+free+" empty; tracker="+trackerEmpty:"main inventory unavailable";}
 }
 private static Object call(Object obj,String method) throws Exception{return obj==null?null:obj.getClass().getMethod(method).invoke(obj);}
 public static Snapshot inspect(Object controller){return inspect(controller,true);}
 private static Snapshot inspect(Object controller,boolean includeTracker){
  try{
   if(controller instanceof com.player2.playerengine.PlayerEngineController mod){
    var inventory=mod.getInventory();
    if(inventory==null || inventory.main.isEmpty())return new Snapshot(0,0,-1,-1);
    int total=inventory.main.size();
    int occupied=0;for(var stack:inventory.main)if(!stack.isEmpty())occupied++;
    return new Snapshot(total,occupied,total-occupied,-1);
   }
   Object entity=call(controller,"getEntity"),inv=call(entity,"getLivingInventory");
   if(inv==null)return new Snapshot(0,0,-1,-1);
   Object list=inv.getClass().getField("main").get(inv);
   if(!(list instanceof List<?> stacks) || stacks.isEmpty())return new Snapshot(0,0,-1,-1);
   int occupied=0;
   for(Object s:stacks){if(s==null || !Boolean.TRUE.equals(call(s,"method_7960")))occupied++;}
   int tracker=-1;
   try{if(!includeTracker)return new Snapshot(stacks.size(),occupied,stacks.size()-occupied,-1);Object storage=call(controller,"getItemStorage");Object result=call(storage,"hasEmptyInventorySlot");tracker=Boolean.TRUE.equals(result)?1:0;}
   catch(Exception ignored){} // tracker may be unavailable during NPC lifecycle changes
   return new Snapshot(stacks.size(),occupied,stacks.size()-occupied,tracker);
  }catch(Exception | LinkageError ignored){return new Snapshot(0,0,-1,-1);}
 }
 public static boolean hasEmptyMainSlot(Object controller){Snapshot s=inspect(controller,false);return !s.known() || s.free()>0;}
 public static boolean mainFull(Object controller){Snapshot s=inspect(controller);return s.known() && s.free()==0;}
 public static void publish(Object controller){
  Snapshot s=inspect(controller);
  if(!s.known())return;
  ObservatoryTelemetry.set("inventoryMainSlots",s.used()+"/"+s.total()+" occupied ("+s.free()+" free)");
  if(s.trackerEmpty()!=-1 && (s.free()>0)!=(s.trackerEmpty()==1)){
   ObservatoryTelemetry.event("INVENTORY_CHECK_MISMATCH",s.summary());
  }
 }
}
