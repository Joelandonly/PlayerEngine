package com.player2.playerengine.tasks.construction.build_structure;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Resource mining exclusion for an active construction site.
 * Mirrors the previous guard's lifecycle; accelerates its extremely hot isValid loop.
 * A resource miner must not excavate the blueprint, but the builder is not blocked.
 */
public final class VillageSiteGuard {
    private VillageSiteGuard() {}
    private static final Map<Object,List<Site>> SITES = new WeakHashMap<>();
    private static long lastWarn;
    private static final class Site {
        final WeakReference<Object> owner;
        final Field finished;
        final int minX,minZ,maxX,maxZ;
        long nextLogTime, avoided;
        Site(Object owner,int x,int z,int sx,int sz) throws ReflectiveOperationException {
            this.owner=new WeakReference<>(owner);
            this.finished=field(owner,"finished");
            minX=x;minZ=z;maxX=x+sx-1;maxZ=z+sz-1;
        }
        boolean same(Object job,int x,int z,int sx,int sz){return owner.get()==job&&minX==x&&minZ==z&&maxX==x+sx-1&&maxZ==z+sz-1;}
        boolean contains(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        boolean completed(){try{Object job=owner.get();return job==null||finished.getBoolean(job);}catch(Exception e){return true;}}
    }
    private static Field field(Object item,String name)throws ReflectiveOperationException {
        for(Class<?> c=item.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException missing){}
        throw new NoSuchFieldException(item.getClass().getName()+"."+name);
    }
    private static final ClassValue<Method[]> COORDINATES=new ClassValue<>() {
        @Override protected Method[] computeValue(Class<?> type){
            Method[] cached=new Method[2];
            for(int i=0;i<2;i++){
                String official=i==0?"method_10263":"method_10260";
                String fallback=i==0?"getX":"getZ";
                try{cached[i]=type.getMethod(official);}catch(NoSuchMethodException missing){
                    try{cached[i]=type.getMethod(fallback);}catch(NoSuchMethodException ignored){}
                }
            }
            for(Method m:cached)if(m!=null)m.setAccessible(true);
            return cached;
        }
    };
    private static int coordinate(Object position,String intermediary,String yarn) throws ReflectiveOperationException {
        Method method=COORDINATES.get(position.getClass())[intermediary.equals("method_10260")?1:0];
        if(method==null)throw new NoSuchMethodException(position.getClass().getName()+" coordinate");
        return ((Number)method.invoke(position)).intValue();
    }
    public static void update(Object job,Object controller){
        if(job==null||controller==null)return;
        try{
            if(field(job,"finished").getBoolean(job)){clear(job,controller);return;}
            Object schematic=field(job,"schematic").get(job);if(schematic==null)return;
            Object origin=field(schematic,"origin").get(schematic);if(origin==null)return;
            int sx=field(schematic,"sx").getInt(schematic),sz=field(schematic,"sz").getInt(schematic);
            if(sx<=0||sz<=0||sx>256||sz>256)return;
            int x=coordinate(origin,"method_10263","getX"),z=coordinate(origin,"method_10260","getZ");
            synchronized(SITES){
                List<Site> sites=SITES.computeIfAbsent(controller,key->new ArrayList<>());
                sites.removeIf(Site::completed);
                if(sites.stream().anyMatch(old->old.same(job,x,z,sx,sz)))return;
                sites.removeIf(old->old.owner.get()==job);
                sites.add(new Site(job,x,z,sx,sz));
            }
            System.out.println("[EllieMaterial] protected build site x="+x+".."+(x+sx-1)+" z="+z+".."+(z+sz-1)+" (resource mining only)");
        }catch(ReflectiveOperationException|RuntimeException ex){warn("register",ex);}
    }
    public static void clearForJob(Object job){if(job==null)return;
        synchronized(SITES){SITES.values().forEach(sites->sites.removeIf(s->s.owner.get()==job));
            SITES.values().removeIf(List::isEmpty);}
    }
    public static void clear(Object job,Object controller){if(job==null||controller==null)return;
        synchronized(SITES){List<Site> sites=SITES.get(controller);if(sites!=null){sites.removeIf(s->s.owner.get()==job);if(sites.isEmpty())SITES.remove(controller);}}
    }
    public static boolean allowResourceMining(Object controller,Object pos){
        if(controller==null||pos==null)return true;
        try{
            int x=coordinate(pos,"method_10263","getX"),z=coordinate(pos,"method_10260","getZ");
            Site site=null;
            synchronized(SITES){
                List<Site> sites=SITES.get(controller);
                if(sites==null)return true;
                sites.removeIf(Site::completed);
                for(Site candidate:sites)if(candidate.contains(x,z)){site=candidate;break;}
            }
            if(site==null)return true;
            ++site.avoided;
            long now=System.currentTimeMillis();
            if(now>=site.nextLogTime){synchronized(site){if(now>=site.nextLogTime){site.nextLogTime=now+30_000;
                String detail="excluded mining target inside village x="+x+" z="+z+" footprint=["+site.minX+".."+site.maxX+","+site.minZ+".."+site.maxZ+"] avoided="+site.avoided;
                System.out.println("[EllieMaterial] "+detail);
                com.player2.playerengine.observatory.ObservatoryTelemetry.event("PROTECTED_SOURCE_SKIP",detail);
            }}}
            return false;
        }catch(ReflectiveOperationException|RuntimeException ex){warn("filter",ex);return true;}
    }
    private static void warn(String action,Exception ex){long t=System.currentTimeMillis();if(t-lastWarn>=30_000){lastWarn=t;System.err.println("[EllieMaterial] VillageSiteGuard "+action+" unavailable: "+ex);}}
}
