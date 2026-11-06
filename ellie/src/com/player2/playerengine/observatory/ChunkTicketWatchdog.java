package com.player2.playerengine.observatory;

import java.lang.reflect.*;
import java.util.*;

/**
 * Server-tick reconciliation for the four chunk tickets ALREADY owned by the
 * native NPC tracker. Does not expand the footprint or keep offline NPCs alive.
 * Works while the NPC/controller tick is suspended because the global server
 * tick is independent of the NPC chunk.
 *
 * Important: failures must never be silent. In the field, the old watchdog
 * reported "4 tracked" while the NPC was unforced and not ticking hundreds
 * of blocks from the owner.
 */
public final class ChunkTicketWatchdog {
 private ChunkTicketWatchdog() {}
 private static final Map<Object,Long> lastRepair=new WeakHashMap<>();
 private static final Map<Object,Long> lastWarning=new WeakHashMap<>();
 private static Object field(Object o,String name)throws Exception{
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{
   Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);
  }catch(NoSuchFieldException ignored){}return null;
 }
 private static Object call(Object obj,String name)throws Exception{
  Method m=obj.getClass().getMethod(name);
  // A public method on a non-public runtime implementation can still throw
  // IllegalAccessException. Use the same checked accessibility path as the
  // known native bridge fixes.
  m.setAccessible(true);
  return m.invoke(obj);
 }
 private static int number(Object o,String n)throws Exception{return ((Number)field(o,n)).intValue();}
 private static boolean contains(Object forced,long packed)throws Exception{
  Method m=forced.getClass().getMethod("contains",long.class);
  m.setAccessible(true);
  return Boolean.TRUE.equals(m.invoke(forced,packed));
 }
 private static Object setForced(Object world,int x,int z)throws Exception{
  Method m=world.getClass().getMethod("method_17988",int.class,int.class,boolean.class);
  m.setAccessible(true);
  return m.invoke(world,x,z,true);
 }
 private static String error(Throwable t){
  if(t instanceof InvocationTargetException wrapped && wrapped.getCause()!=null)t=wrapped.getCause();
  String msg=t.getMessage();
  return t.getClass().getSimpleName()+(msg==null?"":": "+msg.replace('\n',' ').replace('\r',' ').substring(0,Math.min(140,msg.length())));
 }
 private static void warning(Object controller,long tick,String reason){
  Long last=lastWarning.get(controller);
  ObservatoryTelemetry.set("chunkTicketsLastFailure",reason);
  if(last==null||tick<last||tick-last>=100){
   lastWarning.put(controller,tick);
   ObservatoryTelemetry.event("CHUNK_TICKETS_UNAVAILABLE",reason);
  }
 }
 private static double coordinate(Object entity,String method)throws Exception{
  Object v=call(entity,method);
  return ((Number)v).doubleValue();
 }
 public static synchronized int reconcile(Object controller,long tick){
  if(controller==null)return 0;
  try{
   Object tracker=field(controller,"chunkLoader");
   if(tracker==null)return 0;
   Object requested=field(tracker,"chunks");
   if(!(requested instanceof Collection<?> chunks)||chunks.isEmpty())return 0;
   if(chunks.size()>4){warning(controller,tick,"Tracker has "+chunks.size()+" chunks; refusing to change ownership");return 0;}
   Object entity=call(controller,"getEntity");
   if(entity==null)return 0;
   try{if(Boolean.FALSE.equals(call(entity,"method_5805")))return 0;}catch(NoSuchMethodException ignored){}
   Object world=call(entity,"method_37908");
   if(world==null)return 0;
   Object forced=call(world,"method_17984");
   if(forced==null){warning(controller,tick,"Current world did not expose forced chunks");return 0;}
   Set<Long> tracked=new HashSet<>();
   List<int[]> missing=new ArrayList<>();
   for(Object chunk:chunks){
    int x=number(chunk,"field_9181"),z=number(chunk,"field_9180");
    long packed=DistanceDiagnostics.packedChunk(x,z);
    tracked.add(packed);
    if(!contains(forced,packed))missing.add(new int[]{x,z});
   }
   // A 4-item list in the NPC loader is not proof that it still covers the
   // entity. Detect stale coordinates without creating unowned forced chunks.
   int cx=(int)Math.floor(coordinate(entity,"method_23317")/16.0);
   int cz=(int)Math.floor(coordinate(entity,"method_23321")/16.0);
   if(!tracked.contains(DistanceDiagnostics.packedChunk(cx,cz))){
    warning(controller,tick,"NPC current chunk "+cx+","+cz+" absent from four tracked chunks; controller loader must refresh");
    return 0;
   }
   int repaired=0;
   for(int[] chunk:missing){
    setForced(world,chunk[0],chunk[1]);
    // Verify the real world after calling setChunkForced. Its return value
    // alone cannot establish that the ticket was installed.
    if(contains(call(world,"method_17984"),DistanceDiagnostics.packedChunk(chunk[0],chunk[1])))repaired++;
   }
   if(!missing.isEmpty()){
    ObservatoryTelemetry.set("chunkTicketsStatus","tracked="+chunks.size()+" missing="+missing.size()+" verified_restored="+repaired+" current="+cx+","+cz);
    if(repaired<missing.size())warning(controller,tick,"Force request did not establish "+(missing.size()-repaired)+"/"+missing.size()+" native tracked tickets");
   }
   if(repaired>0){
    Long last=lastRepair.get(controller);
    lastRepair.put(controller,tick);
    if(last==null||tick<last||tick-last>=100)
     ObservatoryTelemetry.event("CHUNK_TICKETS_RESTORED","verified="+repaired+"/"+missing.size()+" tracked="+chunks.size()+" center="+cx+","+cz);
   }
   return repaired;
  }catch(Exception|LinkageError e){
   warning(controller,tick,"Reconciliation failed: "+error(e));
   return 0;
  }
 }
}
