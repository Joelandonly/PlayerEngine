package com.player2.playerengine.tasks.crafting;
import java.lang.reflect.*;
import java.util.*;

/** Suppress table reuse across a ledge and recover from stationary navigation. */
public final class CraftTableApproachRecovery {
    private static final int MAX_VERTICAL_DELTA = 2;
    private static final int STALL_TICKS = 90;
    private static final long EXPIRY_NANOS=120_000_000_000L;
    private static final Map<Object,Progress> progress=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Map<String,Long>> blacklist=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Progress {String target;double x,y,z;int ticks;}
    private CraftTableApproachRecovery(){}
    private static Object call(Object obj,String name,Object... args)throws Exception{
        if(obj==null)throw new NullPointerException(name);
        for(Method m:obj.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            try{return m.invoke(obj,args);}catch(IllegalArgumentException ignored){}
        }
        throw new NoSuchMethodException(name);
    }
    private static Field field(Class<?> c,String name)throws Exception{
        for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static int[] xyz(Object p)throws Exception {
        return new int[]{((Number)call(p,"method_10263")).intValue(),((Number)call(p,"method_10264")).intValue(),((Number)call(p,"method_10260")).intValue()};
    }
    private static double[] player(Object c)throws Exception{
        Object p=call(c,"getPlayer");
        return new double[]{((Number)call(p,"method_23317")).doubleValue(),((Number)call(p,"method_23318")).doubleValue(),((Number)call(p,"method_23321")).doubleValue()};
    }
    private static String key(Object p)throws Exception{return Arrays.toString(xyz(p));}
    private static boolean allowed(Object c,Object pos)throws Exception{
        if(pos==null)return true;
        double[] p=player(c);int[] t=xyz(pos);
        if(Math.abs(t[1]-Math.floor(p[1]))>MAX_VERTICAL_DELTA)return false;
        Object world=call(c,"getWorld");
        synchronized(blacklist){
            Map<String,Long> m=blacklist.get(world);
            if(m==null)return true;
            Long until=m.get(key(pos));
            if(until==null)return true;
            if(System.nanoTime()<until)return false;
            m.remove(key(pos));
        }
        return true;
    }
    private static void event(String reason,String detail){
        try {Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry")
                .getMethod("event",String.class,String.class).invoke(null,reason,detail);}
        catch(Throwable ignored){System.out.println("[EllieCraftRecovery] "+reason+": "+detail);}
    }
    private static void reject(Object c,Object pos,String reason)throws Exception{
        if(pos==null)return;
        Object w=call(c,"getWorld");String key=key(pos);
        synchronized(blacklist){blacklist.computeIfAbsent(w,k->new HashMap<>()).put(key,System.nanoTime()+EXPIRY_NANOS);}
        event("CRAFT_TABLE_REJECTED",reason+" table="+key+" (temporary, preserved in world)");
    }
    /** The parent Task.tick() calls this even if a movement child is active. */
    public static void craftTick(Object task,Object controller){
        try {
            Field f=field(task.getClass(),"craftingTablePos");
            Object pos=f.get(task);
            if(pos==null){progress.remove(task);return;}
            if(!allowed(controller,pos)){
                reject(controller,pos,"vertical mismatch or prior stalled approach");
                f.set(task,null);progress.remove(task);return;
            }
            double[] p=player(controller);String key=key(pos);
            Progress s=progress.computeIfAbsent(task,k->new Progress());
            if(!key.equals(s.target)){
                s.target=key;s.x=p[0];s.y=p[1];s.z=p[2];s.ticks=0;return;
            }
            double dx=p[0]-s.x,dy=p[1]-s.y,dz=p[2]-s.z;
            if(dx*dx+dy*dy+dz*dz>0.36){s.x=p[0];s.y=p[1];s.z=p[2];s.ticks=0;return;}
            if(++s.ticks<STALL_TICKS)return;
            Class<?> locator=Class.forName("com.player2.playerengine.tasks.crafting.CraftingTableLocator");
            Method isReachable=null;
            for(Method m:locator.getMethods())if(m.getName().equals("isReachable")&&m.getParameterCount()==2){isReachable=m;break;}
            if(isReachable!=null&&!Boolean.TRUE.equals(isReachable.invoke(null,controller,pos))){
                reject(controller,pos,"no movement toward table for "+s.ticks+" crafting ticks");
                f.set(task,null);progress.remove(task);
            }else{s.ticks=0;}
        }catch(Throwable e){event("CRAFT_TABLE_RECOVERY_WARNING",e.getClass().getSimpleName()+" "+e.getMessage());}
    }
    public static Optional<?> filterReusable(Object c,Optional<?> found){
        if(found==null||found.isEmpty())return Optional.empty();
        try{return allowed(c,found.get())?found:Optional.empty();}catch(Throwable ignored){return found;}
    }
    public static Optional<?> filterReachable(Object c,Optional<?> found){
        if(found==null||found.isEmpty())return Optional.empty();
        try{return allowed(c,call(found.get(),"pos"))?found:Optional.empty();}catch(Throwable ignored){return found;}
    }
}
