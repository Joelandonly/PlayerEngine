package com.player2.playerengine.observatory;

import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Native chunk ownership, maintained from the global server clock, never an AI executor. */
public final class ChunkTicketWatchdog {
 private ChunkTicketWatchdog(){}
 private static final Set<UUID> stoppedOwners=new HashSet<>();
 private static final Map<Object,Map<Long,Claim>> worlds=new WeakHashMap<>();
 private static final Map<Object,Session> sessions=new WeakHashMap<>();
 private static final Map<Object,Long> warnings=new WeakHashMap<>();
 private static final class Claim {final Set<UUID> owners=new HashSet<>();boolean external;}
 private static final class Session {Object world;UUID uuid;WeakReference<Object> tracker;Set<Long> chunks=Set.of();long refreshed=Long.MIN_VALUE;}
 private static Object field(Object o,String name)throws Exception {for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 private static Object call(Object o,String name,Object...args)throws Exception {
  for(Method m:o.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(o,args);}
  throw new NoSuchMethodException(o.getClass().getName()+"."+name);
 }
 private static long key(int x,int z){return DistanceDiagnostics.packedChunk(x,z);}
 private static int x(long p){return (int)p;}private static int z(long p){return (int)(p>>>32);}
 private static boolean forced(Object world,long p)throws Exception {return Boolean.TRUE.equals(call(call(world,"method_17984"),"contains",p));}
 private static void force(Object world,long p,boolean enabled)throws Exception {call(world,"method_17988",x(p),z(p),enabled);if(forced(world,p)!=enabled)throw new IllegalStateException("Vanilla forced-set verification failed for "+x(p)+","+z(p)+" enabled="+enabled);}
 /** These methods back the existing ChunkController API; ownership is world-specific and idempotent. */
 public static synchronized void load(Object world,UUID owner,int x,int z){
  try{if(stoppedOwners.contains(owner))return;long p=key(x,z);Map<Long,Claim> claims=worlds.computeIfAbsent(world,w->new HashMap<>());Claim c=claims.get(p);
   if(c==null){c=new Claim();c.external=forced(world,p);claims.put(p,c);}c.owners.add(owner);
   if(!forced(world,p))force(world,p,true);
  }catch(Exception|LinkageError e){warn(world,0,"load",e);}
 }
 public static synchronized void unload(Object world,UUID owner,int x,int z){
  try{Map<Long,Claim> claims=worlds.get(world);long p=key(x,z);Claim c=claims==null?null:claims.get(p);if(c==null)return;
   if(c.owners.size()==1&&c.owners.contains(owner)&&!c.external&&forced(world,p))force(world,p,false);c.owners.remove(owner);if(c.owners.isEmpty())claims.remove(p);
  }catch(Exception|LinkageError e){warn(world,0,"unload",e);}
 }
 public static synchronized int size(){return worlds.values().stream().mapToInt(Map::size).sum();}
 public static synchronized void clear(){
  for(var world:new ArrayList<>(worlds.keySet()))try{for(var e:new ArrayList<>(worlds.get(world).entrySet())){if(!e.getValue().external&&forced(world,e.getKey()))force(world,e.getKey(),false);worlds.get(world).remove(e.getKey());}}catch(Exception|LinkageError e){warn(world,0,"clear",e);}
  sessions.clear();stoppedOwners.clear();
 }
 public static synchronized void release(UUID uuid){
  if(uuid==null)return;stoppedOwners.remove(uuid);
  for(var world:new ArrayList<>(worlds.keySet()))for(long p:new ArrayList<>(worlds.get(world).keySet()))unload(world,uuid,x(p),z(p));
  sessions.entrySet().removeIf(e->uuid.equals(e.getValue().uuid));
 }
 public static synchronized void stopped(Object controller){
  try{if(Boolean.TRUE.equals(call(controller,"isParkedAcrossDimensions")))return;UUID uuid=(UUID)call(call(controller,"getEntity"),"method_5667");release(uuid);stoppedOwners.add(uuid);((Collection<?>)field(field(controller,"chunkLoader"),"chunks")).clear();}
  catch(Exception|LinkageError e){warn(controller,0,"controller stop",e);}
 }
 public static synchronized void reset(Object tracker){
  try{Object entity=field(tracker,"entity");release((UUID)call(entity,"method_5667"));((Collection<?>)field(tracker,"chunks")).clear();}
  catch(Exception|LinkageError e){warn(tracker,0,"tracker reset",e);}
 }
 private static void releaseSession(Object controller)throws Exception {
  Session s=sessions.get(controller);if(s==null)return;
  Map<Long,Claim> claims=worlds.get(s.world);if(claims!=null)for(long p:new ArrayList<>(claims.keySet()))if(claims.get(p).owners.contains(s.uuid))unload(s.world,s.uuid,x(p),z(p));
  Object tracker=s.tracker.get();if(tracker!=null)((Collection<?>)field(tracker,"chunks")).clear();sessions.remove(controller);
 }
 public static synchronized void retainControllers(Collection<?> active,long tick){
  Map<UUID,Object> registeredWorlds=new HashMap<>();for(Object c:active)try{Object npc=call(c,"getEntity");registeredWorlds.put((UUID)call(npc,"method_5667"),call(npc,"method_37908"));}catch(Exception|LinkageError e){warn(c,tick,"registry identity",e);return;}
  // Weak controller references may have vanished before cleanup; ownership must still be released.
  for(Object world:new ArrayList<>(worlds.keySet()))for(var entry:new ArrayList<>(worlds.get(world).entrySet()))for(UUID owner:new ArrayList<>(entry.getValue().owners))
   if(registeredWorlds.get(owner)!=world)unload(world,owner,x(entry.getKey()),z(entry.getKey()));
  stoppedOwners.retainAll(registeredWorlds.keySet());
  for(Object c:new ArrayList<>(sessions.keySet()))if(!active.contains(c))try{releaseSession(c);}catch(Exception|LinkageError e){warn(c,tick,"registry cleanup",e);}
 }
 private static String error(Throwable e){if(e instanceof InvocationTargetException t&&t.getCause()!=null)e=t.getCause();String s=String.valueOf(e.getMessage()).replace('\n',' ').replace('\r',' ');return e.getClass().getSimpleName()+": "+s.substring(0,Math.min(s.length(),160));}
 private static void warn(Object c,long tick,String stage,Throwable e){String reason=stage+": "+error(e);ObservatoryTelemetry.set("chunkTicketsLastFailure",reason);ObservatoryTelemetry.set("chunkTicketsStatus","Maintenance unavailable: "+reason);Long last=warnings.get(c);if(last==null||tick<last||tick-last>=100){warnings.put(c,tick);ObservatoryTelemetry.event("CHUNK_TICKETS_UNAVAILABLE",reason);}}
 private static Set<Long> footprint(double x,double z){
  Set<Long> result=new LinkedHashSet<>();int a=(int)Math.floor(x/16),b=(int)Math.ceil(x/16),c=(int)Math.floor(z/16),d=(int)Math.ceil(z/16);
  result.add(key(a,c));result.add(key(b,d));result.add(key(a,d));result.add(key(b,c));return result;
 }
 private static List<Object> positions(Set<Long> cells)throws Exception {List<Object> out=new ArrayList<>();Constructor<?> ctor=Class.forName("net.minecraft.class_1923").getConstructor(int.class,int.class);for(long p:cells)out.add(ctor.newInstance(x(p),z(p)));return out;}
 /** Reassert the native forced ticket even if its saved forced-set flag already exists.
  * Vanilla uses ENTITY_TICKING (31), not the weaker FULL/BLOCK_TICKING status.
  */
 private static void reassert(Object world,long p)throws Exception {
  Object manager=call(world,"method_14178");Object pos=Class.forName("net.minecraft.class_1923").getConstructor(int.class,int.class).newInstance(x(p),z(p));
  call(manager,"method_12124",pos,true);
 }
 public static synchronized int reconcile(Object controller,long tick){
  if(controller==null)return 0;
  try{
   Object entity=call(controller,"getEntity"),tracker=field(controller,"chunkLoader");
   if(entity==null||tracker==null){releaseSession(controller);return 0;}
   UUID identity=(UUID)call(entity,"method_5667");
   boolean running=Boolean.TRUE.equals(call(call(controller,"getTaskRunner"),"isActive"));
   boolean parked=!running&&Boolean.TRUE.equals(call(controller,"isParkedAcrossDimensions"));
   if(!Boolean.TRUE.equals(call(entity,"method_5805"))||Boolean.TRUE.equals(call(entity,"method_31481"))||!running&&!parked){release(identity);stoppedOwners.add(identity);((Collection<?>)field(tracker,"chunks")).clear();return 0;}
   stoppedOwners.remove(identity);
   Object world=call(entity,"method_37908");UUID uuid=(UUID)call(entity,"method_5667");if(world==null){releaseSession(controller);return 0;}
   Session s=sessions.get(controller);
   if(s!=null&&(s.world!=world||!s.uuid.equals(uuid)||s.tracker.get()!=tracker)){releaseSession(controller);s=null;}
   if(s==null){s=new Session();s.world=world;s.uuid=uuid;s.tracker=new WeakReference<>(tracker);sessions.put(controller,s);}
   double px=((Number)call(entity,"method_23317")).doubleValue(),pz=((Number)call(entity,"method_23321")).doubleValue();
   if(!Double.isFinite(px)||!Double.isFinite(pz)||Math.abs(px)>30000000||Math.abs(pz)>30000000)throw new IllegalStateException("NPC coordinates out of world bounds");
   Set<Long> desired=parked?Set.of(key((int)Math.floor(px/16),(int)Math.floor(pz/16))):footprint(px,pz);
   // Release old footprint before acquiring new cells: never accumulate teleport trails.
   Map<Long,Claim> claims=worlds.get(world);
   if(claims!=null)for(long p:new ArrayList<>(claims.keySet()))if(claims.get(p).owners.contains(uuid)&&!desired.contains(p))unload(world,uuid,x(p),z(p));
   int restored=0;for(long p:desired){if(!forced(world,p))restored++;load(world,uuid,x(p),z(p));if(!forced(world,p))throw new IllegalStateException("Native claim did not establish forced flag at "+x(p)+","+z(p));}
   set(tracker,"chunks",positions(desired));s.chunks=desired;
   if(s.refreshed==Long.MIN_VALUE||tick<s.refreshed||tick-s.refreshed>=20){for(long p:desired)reassert(world,p);s.refreshed=tick;}
   ObservatoryTelemetry.set("chunkTicketsStatus","owned="+desired.size()+" restored="+restored+" center="+(int)Math.floor(px/16)+","+(int)Math.floor(pz/16)+" native ENTITY_TICKING ticket requested; execution checked separately");
   if(restored>0)ObservatoryTelemetry.event("CHUNK_TICKETS_RESTORED","verified="+restored+"/"+desired.size()+" world="+world+"; awaiting measured NPC/controller heartbeat");
   return restored;
  }catch(Exception|LinkageError e){warn(controller,tick,"reconcile",e);return 0;}
 }
}
