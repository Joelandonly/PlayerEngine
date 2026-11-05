package com.player2.playerengine.observatory;

import java.lang.reflect.*;
import java.util.*;
/** Server-tick reconciliation for already tracked NPC chunks. Never expands the
 * original four-chunk footprint or creates tickets for a missing controller.
 * Runs even when the NPC/controller is not ticking, provided Minecraft's global tick continues.
 */
public final class ChunkTicketWatchdog {
 private ChunkTicketWatchdog() {}
 private static final Map<Object,Long> lastRepair=new WeakHashMap<>();
 private static Object field(Object o,String name)throws Exception{
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{
   Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);
  }catch(NoSuchFieldException ignored){}return null;
 }
 private static Object call(Object obj,String name)throws Exception{return obj.getClass().getMethod(name).invoke(obj);}
 private static int number(Object o,String n)throws Exception{return ((Number)field(o,n)).intValue();}
 public static synchronized int reconcile(Object controller,long tick){
  if(controller==null)return 0;
  try{
   Object tracker=field(controller,"chunkLoader");if(tracker==null)return 0;
   Object requested=field(tracker,"chunks");if(!(requested instanceof Collection<?> chunks)||chunks.isEmpty()||chunks.size()>4)return 0;
   Object entity=call(controller,"getEntity");if(entity==null)return 0;
   // Skip known dead NPCs. Missing version-specific lifecycle methods fail open for diagnostics,
   // not for extra chunk loads: we only restore tickets that the existing tracker holds.
   try{if(Boolean.FALSE.equals(call(entity,"method_5805")))return 0;}catch(NoSuchMethodException ignored){}
   Object world=call(entity,"method_37908");if(world==null)return 0;
   Object forced=call(world,"method_17984");if(forced==null)return 0;
   int repaired=0;
   for(Object chunk:chunks){
    int x=number(chunk,"field_9181"),z=number(chunk,"field_9180");
    long packed=DistanceDiagnostics.packedChunk(x,z);
    Object present=forced.getClass().getMethod("contains",long.class).invoke(forced,packed);
    if(Boolean.TRUE.equals(present))continue;
    Object result=world.getClass().getMethod("method_17988",int.class,int.class,boolean.class).invoke(world,x,z,true);
    if(!Boolean.FALSE.equals(result))repaired++;
   }
   if(repaired>0){
    Long last=lastRepair.get(controller);
    lastRepair.put(controller,tick);
    if(last==null||tick-last>=100)ObservatoryTelemetry.event("CHUNK_TICKETS_RESTORED","npc="+call(entity,"method_5667")+" restored="+repaired+"/"+chunks.size()+" existing tracked chunks; independent global tick");
   }
   return repaired;
  }catch(Exception|LinkageError ignored){return 0;}
 }
}
