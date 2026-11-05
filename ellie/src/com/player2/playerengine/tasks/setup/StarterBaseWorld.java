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
    private final Set<StarterBaseLayout.Pos> reservedStations=new HashSet<>();
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
        // A recorded site is persistent intent, even while its chunk is unloaded.
        // Never silently move the base just because a remote block lookup returned air.
        protect(plan);
        return plan;
    }
    public List<StarterBaseLayout.Placement> loadOrSelect() throws Exception {
        List<StarterBaseLayout.Placement> existing=loadExisting();
        if(!existing.isEmpty())return existing;
        CoreGoalJournal journal=baseJournal();
        if (!journal.isPersistent()) throw new IllegalStateException("Persistent base journal unavailable; refusing unrecorded placement");
        var ready=StarterBaseLayout.reuse(origin,this);
        if(ready.isEmpty())ready=StarterBaseLayout.select(origin,this);
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
        if(supportBreakGuard==null){
            // VillageSiteGuard blocks *resource mining*. This guard also tells Baritone
            // not to excavate workstation/support cells, including naturally supported sites.
            reservedSupport.addAll(StarterBaseLayout.supportCells(plan));
            for(var station:plan){reservedStations.add(station.pos());retained.put(station.pos(),station);}
            Object extra=call(controller,"getExtraBaritoneSettings");
            Object mutex=call(extra,"getBreakMutex");
            java.util.function.Predicate<Object> guard=blockPos -> {
                try {
                    var cell=new StarterBaseLayout.Pos(((Number)call(blockPos,"method_10263")).intValue(),((Number)call(blockPos,"method_10264")).intValue(),((Number)call(blockPos,"method_10260")).intValue());
                    if(reservedSupport.contains(cell))return true;
                    var station=retained.get(cell);return reservedStations.contains(cell)&&station!=null&&matches(station);
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
            supportBreakGuard=null;guardedBaritoneSettings=null;reservedSupport.clear();reservedStations.clear();
        }
        if(zone!=null){zone.close();zone=null;}
    }
    public Task approach(List<StarterBaseLayout.Placement> plan)throws Exception {
        var chest=plan.get(2).pos();Object player=call(controller,"getPlayer");
        double dx=number(player,"method_23317","getX")-chest.x(),dz=number(player,"method_23321","getZ")-chest.z();
        if(dx*dx+dz*dz<=144&&(Boolean)call(call(controller,"getWorld"),"method_22340",pos(chest)))return null;
        return (Task)Class.forName("com.player2.playerengine.tasks.movement.GetToBlockTask").getConstructor(blockPos).newInstance(pos(chest.offset(0,0,1)));
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
        if(com.player2.playerengine.reliability.WorkshopPlacement.isTreeSupport(id))return false;
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
        return reservedSupport.contains(job.pos()) && job.resource().equals("cobblestone") && canFound(job.pos());
    }
    public Task prepareTerrain(StarterBaseLayout.Placement job)throws Exception {
        if(!canPrepare(job))throw new IllegalStateException("Unsafe terrain preparation");
        if(job.resource().equals("air"))return (Task)Class.forName("com.player2.playerengine.tasks.construction.DestroyBlockTask")
            .getConstructor(blockPos).newInstance(pos(job.pos()));
        // Native placement only: never edit the world directly or fake completion.
        Class<?> blockType=Class.forName("net.minecraft.class_2248");
        Object alternatives=Array.newInstance(blockType,1);
        String material=com.player2.playerengine.reliability.StructuralMaterials.preferred(com.player2.playerengine.reliability.StructuralMaterials.carried(controller),com.player2.playerengine.reliability.StructuralMaterials.fallback(controller));
        Array.set(alternatives,0,block(material));
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
    public List<StarterBaseLayout.Placement> repairPlan(StarterBaseLayout.Placement station)throws Exception {
        var jobs=new ArrayList<StarterBaseLayout.Placement>();var p=station.pos();
        if(!safeFloor(p.offset(0,-1,0))&&canFound(p.offset(0,-1,0)))jobs.add(new StarterBaseLayout.Placement(p.offset(0,-1,0),"cobblestone"));
        if(!air(p.offset(0,1,0))&&canClear(p.offset(0,1,0)))jobs.add(new StarterBaseLayout.Placement(p.offset(0,1,0),"air"));
        if(!air(p)&&!matches(station)&&canClear(p))jobs.add(new StarterBaseLayout.Placement(p,"air"));
        if(!StarterBaseLayout.hasAccess(p,this)){
            // Repair only the compact workshop's recorded service lane, not arbitrary surroundings.
            var side=p.offset(0,0,1);var floor=side.offset(0,-1,0);var head=side.offset(0,1,0);
            if(canFound(floor)&&canClear(side)&&canClear(head)){
                if(!safeFloor(floor))jobs.add(new StarterBaseLayout.Placement(floor,"cobblestone"));
                if(!air(side))jobs.add(new StarterBaseLayout.Placement(side,"air"));
                if(!air(head))jobs.add(new StarterBaseLayout.Placement(head,"air"));
            }
        }
        return List.copyOf(jobs);
    }
    public String unsafeReason(StarterBaseLayout.Placement station)throws Exception {
        var p=station.pos();var issues=new ArrayList<String>();
        if(!safeFloor(p.offset(0,-1,0)))issues.add("unsafe support "+p.offset(0,-1,0));
        if(!air(p.offset(0,1,0)))issues.add("blocked headroom "+p.offset(0,1,0));
        if(!air(p)&&!matches(station))issues.add("wrong block "+p);
        if(!StarterBaseLayout.hasAccess(p,this))issues.add("no supported standing position beside workstation");
        return String.join("; ",issues);
    }
    public int count(String resource) throws Exception {
        if(resource.equals("cobblestone"))return com.player2.playerengine.reliability.StructuralMaterials.carried(controller).values().stream().mapToInt(Integer::intValue).sum();
        Object matches=callStatic("com.player2.playerengine.TaskCatalogue","getItemMatches",com.player2.playerengine.reliability.StructuralMaterials.catalogueName(resource));
        List<?> main=(List<?>)com.player2.playerengine.nativecommands.NativeAccess.get(call(controller,"getInventory"),"main");
        if(main.size()!=36)throw new IllegalStateException("Expected 36 actual workshop inventory slots");
        int total=0;
        for(Object stack:main){
            if(Boolean.TRUE.equals(call(stack,"method_7960")))continue;
            Object item=call(stack,"method_7909");
            for(int i=0;i<Array.getLength(matches);i++)if(item.equals(Array.get(matches,i))){total+=((Number)call(stack,"method_7947")).intValue();break;}
        }
        return total;
    }
    public Task acquire(String resource) throws Exception {
        if(resource.equals("cobblestone"))resource=com.player2.playerengine.reliability.StructuralMaterials.preferred(com.player2.playerengine.reliability.StructuralMaterials.available(controller),com.player2.playerengine.reliability.StructuralMaterials.fallback(controller));
        return EllieSetupTask.nativeAcquisition(com.player2.playerengine.reliability.StructuralMaterials.catalogueName(resource),1);
    }
    /** Scan actual block light on surface cells at/above the base. No cave searches. */
    public SurfaceLightingPlan.Survey surveyLighting(List<StarterBaseLayout.Placement> plan,
            Set<StarterBaseLayout.Pos> excluded) throws Exception {
        if (!Objects.equals(expectedDimension, dimension(controller)))
            throw new IllegalStateException("Outdoor lighting dimension changed");
        var surveyProbe = new SurfaceLightingPlan.Probe() {
            public StarterBaseLayout.Pos surface(int x, int z, int baseY) throws Exception {
                Object world = call(controller, "getWorld");
                Class<?> heightmap = Class.forName("net.minecraft.class_2902$class_2903");
                Object surfaceType = heightmap.getField("field_13203").get(null); // MOTION_BLOCKING_NO_LEAVES
                int y = ((Number)call(world, "method_8624", surfaceType, x, z)).intValue();
                // Never light below the workshop or follow a heightmap into a cave.
                if (y < baseY || y > baseY + 3) return null;
                var spot = new StarterBaseLayout.Pos(x, y, z);
                if (!withinLimits(spot) || !air(spot) || !air(spot.offset(0, 1, 0))
                        || !safeFloor(spot.offset(0, -1, 0))) return null;
                if (!(Boolean)call(world, "method_8311", pos(spot))) return null; // direct sky view
                return spot;
            }
            public int blockLight(StarterBaseLayout.Pos spot) throws Exception {
                Object type = Class.forName("net.minecraft.class_1944").getField("field_9282").get(null); // BLOCK, not SKY
                return ((Number)call(call(controller, "getWorld"), "method_8314", type, pos(spot))).intValue();
            }
            public boolean canPlace(StarterBaseLayout.Pos spot) throws Exception {
                // Torches stand on solid ground near the workshop, not on protected
                // machinery, in water or on a cliff with no interaction pose.
                if (retained.containsKey(spot) || !air(spot) || !safeFloor(spot.offset(0, -1, 0))) return false;
                for (var station : plan) {
                    var machine = station.pos();
                    if (spot.y() == machine.y()
                            && Math.abs(spot.x() - machine.x()) + Math.abs(spot.z() - machine.z()) <= 1)
                        return false; // preserve the native workstation standing/interaction lanes
                }
                return StarterBaseLayout.hasAccess(spot, StarterBaseWorld.this);
            }
        };
        return SurfaceLightingPlan.inspect(plan, surveyProbe, excluded);
    }
    public void retainTorch(StarterBaseLayout.Placement placement) {
        if (!placement.resource().equals("torch")) return;
        // Reuse the already-installed native break guard for verified torches.
        retained.put(placement.pos(), placement);
        reservedStations.add(placement.pos());
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
            Object matches=callStatic("com.player2.playerengine.TaskCatalogue","getItemMatches",com.player2.playerengine.reliability.StructuralMaterials.catalogueName(id));
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
