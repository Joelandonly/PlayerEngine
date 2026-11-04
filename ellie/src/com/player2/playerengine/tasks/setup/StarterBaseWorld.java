package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

/** Native game boundary. Uses existing placement/gather tasks, never world edits or LLM calls. */
final class StarterBaseWorld implements StarterBaseTask.Environment, StarterBaseLayout.Probe {
    private static final Map<String,Method> METHODS=new java.util.concurrent.ConcurrentHashMap<>();
    private final PlayerEngineController controller;
    private final StarterBaseLayout.Pos origin;
    private final String expectedDimension;
    private SetupBuildZone zone;
    private boolean worksiteRegistered;
    private boolean platformMode;
    private final Set<StarterBaseLayout.Pos> reservedSupport=new HashSet<>();
    private java.util.function.Predicate<Object> supportBreakGuard;
    private Object guardedBaritoneSettings;
    private final Class<?> blockPos;
    private final Map<String,Object> blocks=new HashMap<>();
    private final Map<StarterBaseLayout.Pos,StarterBaseLayout.Placement> retained=new HashMap<>();
    StarterBaseWorld(PlayerEngineController controller, StarterBaseLayout.Pos origin, String dimension) {
        this.controller=controller;this.origin=origin;this.expectedDimension=dimension;
        try { blockPos=Class.forName("net.minecraft.class_2338"); }
        catch(ClassNotFoundException error) { throw new IllegalStateException("Minecraft block positions unavailable",error); }
    }
    static StarterBaseLayout.Pos origin(PlayerEngineController controller) throws Exception {
        Object player=call(controller,"getPlayer");
        double x=number(player,"method_23317","getX"), y=number(player,"method_23318","getY"), z=number(player,"method_23321","getZ");
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalStateException("Setup starting coordinates are unavailable");
        return new StarterBaseLayout.Pos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
    }
    static String dimension(PlayerEngineController controller) throws Exception {
        return call(call(call(controller,"getWorld"),"method_27983"),"method_29177").toString();
    }
    private CoreGoalJournal baseJournal() throws Exception {
        if (!Objects.equals(expectedDimension,dimension(controller))) throw new IllegalStateException("Setup changed dimension; original base is not in this world");
        Object store=callStatic("com.player2.playerengine.tasks.deferred.DeferredJobStore","get");
        Path root=(Path)call(store,"worldRoot");
        String character=(String)call(call(controller,"getAIPersistantData"),"getCharacterId");
        return CoreGoalJournal.open(root,character,"starter_base_layout");
    }
    public List<StarterBaseLayout.Placement> loadExisting() throws Exception {
        CoreGoalJournal journal=baseJournal();
        String mode=journal.get("status","");
        if (!mode.equals("BASE_LAYOUT_COMPACT_V3") && !mode.equals("BASE_LAYOUT_PLATFORM_V3")) return List.of();
        platformMode=mode.equals("BASE_LAYOUT_PLATFORM_V3");
        if (!journal.isPersistent()) throw new IllegalStateException("Recorded base journal unavailable");
        if (!journal.get("resource","").equals(expectedDimension)) throw new IllegalStateException("Existing base is in another dimension");
        String[] coords=journal.get("detail","").split(",");
        if(coords.length!=3) throw new IllegalStateException("Invalid saved base anchor");
        var plan=StarterBaseLayout.at(new StarterBaseLayout.Pos(Integer.parseInt(coords[0]),Integer.parseInt(coords[1]),Integer.parseInt(coords[2])));
        protect(plan);
        return plan;
    }
    public List<StarterBaseLayout.Placement> loadOrSelect() throws Exception {
        List<StarterBaseLayout.Placement> existing=loadExisting();
        if(!existing.isEmpty())return existing;
        CoreGoalJournal journal=baseJournal();
        if (!journal.isPersistent()) throw new IllegalStateException("Persistent base journal unavailable; refusing unrecorded placement");
        var ready=StarterBaseLayout.select(origin,this);
        if(ready.isEmpty())ready=StarterBaseLayout.selectBuildable(origin,this);
        if(ready.isEmpty()){
            platformMode=true;
            ready=StarterBaseLayout.selectPlatform(origin,this);
        }
        var plan=ready.orElseThrow(()->new IllegalStateException("No safe three-station ground or reachable compact platform site nearby"));
        var anchor=plan.getFirst().pos();
        journal.put(platformMode?"BASE_LAYOUT_PLATFORM_V3":"BASE_LAYOUT_COMPACT_V3",0,expectedDimension,anchor.x()+","+anchor.y()+","+anchor.z());
        if(!journal.isPersistent()) throw new IllegalStateException("Cannot save base anchor; refusing unrecorded placement");
        protect(plan);
        return plan;
    }
    public void protect(List<StarterBaseLayout.Placement> plan) throws Exception {
        if(!Objects.equals(expectedDimension,dimension(controller)))throw new IllegalStateException("Setup build zone changed dimension");
        var anchor=plan.getFirst().pos();
        if(!worksiteRegistered){com.player2.playerengine.reliability.WorksiteAnchor.open(this,controller,pos(anchor),"starter_setup");worksiteRegistered=true;}
        if(zone==null)zone=new SetupBuildZone(controller,anchor.x()-1,anchor.z()-1,7,3);
        else zone.ensure();
        if(platformMode && supportBreakGuard==null){
            // VillageSiteGuard blocks *resource mining*. This guard also tells Baritone
            // not to route through / excavate the new platform's load-bearing cells.
            reservedSupport.addAll(StarterBaseLayout.supportCells(plan));
            Object extra=call(controller,"getExtraBaritoneSettings");
            Object mutex=call(extra,"getBreakMutex");
            java.util.function.Predicate<Object> guard=blockPos -> {
                try {
                    return reservedSupport.contains(new StarterBaseLayout.Pos(
                        ((Number)call(blockPos,"method_10263")).intValue(),
                        ((Number)call(blockPos,"method_10264")).intValue(),
                        ((Number)call(blockPos,"method_10260")).intValue()));
                } catch(Exception e){return false;}
            };
            synchronized(mutex){((List<Object>)call(extra,"getBreakAvoiders")).add(guard);}
            supportBreakGuard=guard;guardedBaritoneSettings=extra;
        }
    }
    public void releaseProtection(){
        com.player2.playerengine.reliability.WorksiteAnchor.close(this);worksiteRegistered=false;
        if(supportBreakGuard!=null){
            try {
                Object mutex=call(guardedBaritoneSettings,"getBreakMutex");
                synchronized(mutex){((List<?>)call(guardedBaritoneSettings,"getBreakAvoiders")).remove(supportBreakGuard);}
            } catch(Exception e){System.err.println("[EllieSetup] Could not remove temporary platform break guard: "+e);}
            supportBreakGuard=null;guardedBaritoneSettings=null;reservedSupport.clear();
        }
        if(zone!=null){zone.close();zone=null;}
    }
    public boolean air(StarterBaseLayout.Pos pos) throws Exception { return (Boolean)helper("isAir",controller,pos(pos)); }
    private Object state(StarterBaseLayout.Pos p)throws Exception {return call(call(controller,"getWorld"),"method_8320",pos(p));}
    private boolean withinLimits(StarterBaseLayout.Pos p)throws Exception {
        Object world=call(controller,"getWorld");
        return (Boolean)call(world,"method_24794",pos(p))&&(Boolean)call(call(world,"method_8621"),"method_11952",pos(p));
    }
    private boolean fluid(StarterBaseLayout.Pos p)throws Exception {return !(Boolean)call(call(state(p),"method_26227"),"method_15769");}
    private boolean protectedBlock(StarterBaseLayout.Pos p)throws Exception {return call(call(controller,"getWorld"),"method_8321",pos(p))!=null;}
    public boolean safeFloor(StarterBaseLayout.Pos p) throws Exception {
        if(!withinLimits(p)||fluid(p))return false;
        Object world=call(controller,"getWorld");Object state=state(p);
        String id=call(state,"method_26204").toString();
        if(id.contains(":magma_block")||id.contains(":cactus")||id.contains("campfire"))return false;
        return (Boolean)call(state,"method_26234",world,pos(p));
    }
    public boolean canClear(StarterBaseLayout.Pos p)throws Exception {
        if(!withinLimits(p)||fluid(p))return false;
        if(air(p))return true;
        if(protectedBlock(p))return false;
        return ((Number)call(state(p),"method_26214",call(controller,"getWorld"),pos(p))).floatValue()>=0;
    }
    public boolean canFound(StarterBaseLayout.Pos p)throws Exception {
        if(!withinLimits(p))return false;
        if(safeFloor(p))return true;
        if(protectedBlock(p))return false;
        // Replaceable air/fluid only: don't grind down existing builds for the pad.
        return air(p)||fluid(p);
    }
    public List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> plan) {
        retained.clear();for(var p:plan)retained.put(p.pos(),p);
        return StarterBaseLayout.terrain(plan,origin,platformMode);
    }
    public boolean prepared(StarterBaseLayout.Placement job)throws Exception {
        if(job.resource().equals("air"))return air(job.pos())||(retained.containsKey(job.pos())&&matches(retained.get(job.pos())));
        return safeFloor(job.pos());
    }
    public boolean canPrepare(StarterBaseLayout.Placement job)throws Exception {
        if(!Objects.equals(expectedDimension,dimension(controller)))return false;
        if(job.resource().equals("air"))return canClear(job.pos());
        return platformMode && job.resource().equals("cobblestone") && canFound(job.pos());
    }
    public Task prepareTerrain(StarterBaseLayout.Placement job)throws Exception {
        if(!canPrepare(job))throw new IllegalStateException("Unsafe terrain preparation");
        if(job.resource().equals("air"))return (Task)Class.forName("com.player2.playerengine.tasks.construction.DestroyBlockTask")
            .getConstructor(blockPos).newInstance(pos(job.pos()));
        // Native placement only: never edit the world directly or fake completion.
        Class<?> blockType=Class.forName("net.minecraft.class_2248");
        Object alternatives=Array.newInstance(blockType,1);
        Array.set(alternatives,0,block("cobblestone"));
        return (Task)Class.forName("com.player2.playerengine.tasks.construction.PlaceBlockTask")
            .getConstructor(blockPos,alternatives.getClass(),boolean.class,boolean.class)
            .newInstance(pos(job.pos()),alternatives,false,false);
    }
    public boolean matches(StarterBaseLayout.Placement placement) throws Exception {
        return (Boolean)helper("isBlock",controller,pos(placement.pos()),block(placement.resource()));
    }
    public boolean safe(StarterBaseLayout.Placement placement) throws Exception {
        return Objects.equals(expectedDimension,dimension(controller)) && safeFloor(placement.pos().offset(0,-1,0))
            && air(placement.pos().offset(0,1,0)) && (air(placement.pos())||matches(placement))
            && StarterBaseLayout.hasAccess(placement.pos(),this);
    }
    public int count(String resource) throws Exception {
        Object matches=callStatic("com.player2.playerengine.TaskCatalogue","getItemMatches",resource);
        return ((Number)call(controller.getItemStorage(),"getItemCountInventoryOnly",matches)).intValue();
    }
    public Task acquire(String resource) throws Exception {
        Object target=callStatic("com.player2.playerengine.TaskCatalogue","getItemTarget",resource,1);
        Object selection=callStatic("com.player2.playerengine.tasks.crafting.CraftMacroTasks","macroOutcome",controller,target);
        if(selection!=null && "MACRO_CREATED".equals(String.valueOf(call(selection,"outcome")))) return (Task)call(selection,"task");
        return (Task)callStatic("com.player2.playerengine.TaskCatalogue","getItemTask",target);
    }
    public Task place(StarterBaseLayout.Placement placement) throws Exception {
        Class<?> blockType=Class.forName("net.minecraft.class_2248");
        return (Task)Class.forName("com.player2.playerengine.tasks.construction.PlaceItemBlockAtPosTask")
            .getConstructor(blockPos,blockType).newInstance(pos(placement.pos()),block(placement.resource()));
    }
    private Object pos(StarterBaseLayout.Pos pos) throws Exception {
        return blockPos.getConstructor(int.class,int.class,int.class).newInstance(pos.x(),pos.y(),pos.z());
    }
    private Object block(String id) throws Exception {
        if(!blocks.containsKey(id)) {
            Object matches=callStatic("com.player2.playerengine.TaskCatalogue","getItemMatches",id);
            if(matches==null || Array.getLength(matches)!=1)throw new IllegalStateException("Expected one concrete base item: "+id);
            Object item=Array.get(matches,0);
            blocks.put(id,call(item,"method_7711"));
        }
        return blocks.get(id);
    }
    private static Object helper(String name,Object...args) throws Exception {return callStatic("com.player2.playerengine.util.helpers.WorldHelper",name,args);}
    private static double number(Object object,String mapped,String named) throws Exception {
        try{return ((Number)call(object,mapped)).doubleValue();}catch(NoSuchMethodException e){return ((Number)call(object,named)).doubleValue();}
    }
    private static Object callStatic(String type,String name,Object...args) throws Exception {return invoke(Class.forName(type),null,name,args);}
    private static Object call(Object object,String name,Object...args) throws Exception {return invoke(object.getClass(),object,name,args);}
    private static Object invoke(Class<?> type,Object receiver,String name,Object[] args) throws Exception {
        String key=type.getName()+"#"+name+Arrays.toString(Arrays.stream(args).map(a->a==null?"null":a.getClass().getName()).toArray());
        Method cached=METHODS.get(key);
        if(cached!=null)return cached.invoke(receiver,args);
        outer:for(Method method:type.getMethods()) {
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            Class<?>[] params=method.getParameterTypes();
            for(int i=0;i<args.length;i++) {
                Class<?> param=params[i];
                if(param==int.class)param=Integer.class;
                if(args[i]!=null&&!param.isInstance(args[i]))continue outer;
            }
            method.setAccessible(true);
            METHODS.put(key,method);
            return method.invoke(receiver,args);
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
}
