package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.nativecommands.*;
import com.player2.playerengine.agentic.StorageIntelligence;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.tasks.setup.StarterBaseLayout.*;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Version-specific observations and adapters to existing food, placement and equip APIs. */
final class SetupSurvivalWorld implements SetupSurvival.Environment,SetupShelter.Probe {
    private static final String P="com.player2.playerengine.";
    private final PlayerEngineController controller;
    private Pos chest;
    private String dimension;
    private CoreGoalJournal journal;
    private SetupShelter.Plan shelter;
    private long lastScan=Long.MIN_VALUE;
    private List<Pos> shelterAccess=List.of();
    private List<String> structuralMaterials=List.of();
    private boolean foodFromStorage;
    private String firstFuel="charcoal";
    private SetupBuildZone shelterZone;
    private java.util.function.Predicate<Object> structureBreakGuard;
    private Object guardedSettings;
    private final Set<Pos> structuralCells=new HashSet<>();
    SetupSurvivalWorld(PlayerEngineController controller){this.controller=controller;}
    public void anchor(StarterBaseTask base)throws Exception {
        base.prepare();
        var plan=base.layout();chest=plan.stream().filter(p->p.resource().equals("chest")).findFirst().orElseThrow().pos();
        dimension=StarterBaseWorld.dimension(controller);
        Object store=callStatic(P+"tasks.deferred.DeferredJobStore","get");
        journal=CoreGoalJournal.open((Path)call(store,"worldRoot"),String.valueOf(call(call(controller,"getAIPersistantData"),"getCharacterId")),"setup_survival");
        if(!journal.isPersistent())throw new IllegalStateException("Cannot persist survival base anchor");
        String saved=journal.get("detail","");
        String status=journal.get("status","");
        if(Set.of("SETUP_SHELTER_V1","SETUP_SHELTER_V2","SETUP_SHELTER_V3").contains(status)){
            String[] fields=restoreSite(status,saved,dimension,journal.get("resource",""),chest);
            shelter=new SetupShelter.Plan(parse(fields[1]),fields[2]);
            if(status.equals("SETUP_SHELTER_V3"))shelterAccess=Arrays.stream(fields[3].split(";")).map(SetupSurvivalWorld::parse).toList();
            // V2 stored a per-cell bill in a 360-character journal field. Its tail
            // was truncated. Preserve validated coordinates, reobserve supplies/access.
            if(status.equals("SETUP_SHELTER_V2"))ObservatoryTelemetry.event("SETUP_SHELTER_JOURNAL_REPAIR","Retaining V2 chest/shelter coordinates; reconstructing truncated material/approach data from world");
            if(shelterAccess.isEmpty())shelterAccess=SetupShelter.access(shelter,this).orElseThrow(()->new IllegalStateException("Saved shelter entrance connection cannot be safely restored"));
            protectStructure();
        }
        StorageIntelligence.setupChest(controller,pos(chest));
        ObservatoryTelemetry.event("SETUP_BASE_ANCHOR","Planned workshop chest location "+key(chest)+" in "+dimension+"; no relocation or personal-home change");
    }
    public SetupSurvival.Snapshot snapshot()throws Exception {
        if(shelter!=null)protectStructure();
        if(!dimension.equals(StarterBaseWorld.dimension(controller)))throw new IllegalStateException("Survival setup changed dimension");
        Object player=call(controller,"getPlayer"),world=call(controller,"getWorld");
        long tick=((Number)call(world,"method_8510")).longValue();
        if(lastScan==Long.MIN_VALUE||tick<lastScan||tick-lastScan>=100){lastScan=tick;StorageIntelligence.refreshSetupChest(controller,pos(chest));}
        int hunger=((Number)call(call(call(call(controller,"getBaritone"),"getEntityContext"),"hungerManager"),"getFoodLevel")).intValue();
        float health=((Number)call(player,"method_6032")).floatValue();
        long day=Math.floorMod(((Number)call(world,"method_8532")).longValue(),24000);
        boolean danger=false;
        List<?> hostiles=(List<?>)call(call(controller,"getEntityTracker"),"getHostiles");
        double[] at=coordinates(player);
        for(Object hostile:hostiles.subList(0,Math.min(hostiles.size(),64))){
            if(!Boolean.TRUE.equals(call(hostile,"method_5805")))continue;
            double[] h=coordinates(hostile);
            if(squared(at,h)<64){danger=true;break;}
        }
        Object inv=call(controller,"getInventory");
        List<?> main=(List<?>)get(inv,"main");
        if(main.size()!=36)throw new IllegalStateException("Expected 36 actual main inventory slots");
        int nutrition=0,fuel=0;boolean bed=false;
        for(Object stack:main){
            if(Boolean.TRUE.equals(call(stack,"method_7960")))continue;
            Object item=call(stack,"method_7909");int count=((Number)call(stack,"method_7947")).intValue();String id=String.valueOf(item);
            if(id.equals("coal")||id.equals("charcoal")||id.endsWith(":coal")||id.endsWith(":charcoal"))fuel+=count;
            if(id.endsWith("_bed"))bed=true;
            if(Boolean.TRUE.equals(callStatic(P+"multiversion.item.ItemVer","isFood",item)) && safeFood(id)){
                Object food=callStatic(P+"multiversion.item.ItemVer","getFoodComponent",item);
                nutrition+=count*((Number)call(food,"getHunger")).intValue();
            }
        }
        String hand=itemId(call(inv,"getMainHandStack"));
        List<?> off=(List<?>)get(inv,"offHand");String offhand=off.isEmpty()?"":itemId(off.getFirst());
        boolean equipped=(hand.endsWith("iron_sword")||hand.endsWith("diamond_sword")||hand.endsWith("netherite_sword"))&&offhand.endsWith("shield");
        return new SetupSurvival.Snapshot(hunger,health,day>=11500&&day<23500||Boolean.TRUE.equals(call(world,"method_8546")),danger,nutrition,fuel,equipped,bed);
    }
    private static boolean safeFood(String id){return List.of("rotten_flesh","spider_eye","pufferfish","poisonous_potato","chorus_fruit","chicken").stream().noneMatch(bad->id.equals(bad)||id.endsWith(":"+bad));}
    private static String itemId(Object stack)throws Exception{return Boolean.TRUE.equals(call(stack,"method_7960"))?"":String.valueOf(call(stack,"method_7909"));}
    public boolean baseNearby()throws Exception{return baseDistance()<=24&&loaded(chest);}
    public double baseDistance()throws Exception{return Math.sqrt(squared(coordinates(call(controller,"getPlayer")),new double[]{chest.x()+.5,chest.y(),chest.z()+.5}));}
    public boolean shelterVerified()throws Exception{return shelter!=null&&SetupShelter.verified(shelter,this)&&(shelterAccess.isEmpty()||SetupShelter.accessVerified(shelterAccess,this));}
    public String shelterFailure()throws Exception {
        if(shelter==null)return "No recorded shelter plan";
        for(var cell:SetupShelter.cells(shelter)){
            boolean valid=cell.resource().equals("air")?open(cell.pos()):cell.resource().endsWith("_door")?door(cell.pos()):solid(cell.pos());
            if(!valid)return "Shelter mismatch at "+key(cell.pos())+" expected="+cell.resource()+" actual="+(loaded(cell.pos())?String.valueOf(state(cell.pos())):"unloaded");
        }
        for(Pos p:SetupShelter.accessBlocks(shelterAccess))if(!solid(p))return "Entrance support missing at "+key(p);
        for(Pos p:shelterAccess)for(int y=1;y<=2;y++)if(!open(p.offset(0,y,0)))return "Entrance clearance blocked at "+key(p.offset(0,y,0))+" actual="+state(p.offset(0,y,0));
        return "Native builder did not report successful completion despite verified geometry";
    }
    public boolean inside()throws Exception {
        if(!shelterVerified())return false;
        double[] at=coordinates(call(controller,"getPlayer"));Pos p=shelter.floor();
        return at[0]>=p.x()+1&&at[0]<p.x()+4&&at[1]>=p.y()+1&&at[1]<p.y()+4&&at[2]>=p.z()+1&&at[2]<p.z()+4;
    }
    public boolean doorClosed()throws Exception {
        if(shelter==null)return false;
        return String.valueOf(state(shelter.door())).contains("open=false");
    }
    public int shelterProgress()throws Exception {
        if(shelter==null)return 0;
        if(!shelterVerified()&&!SetupShelter.buildable(shelter,shelterAccess,this))throw new IllegalStateException("Shelter site changed or contains protected/foreign blocks; refusing demolition at "+key(shelter.floor()));
        int count=0;for(var cell:SetupShelter.cells(shelter))if(cell.resource().equals("air")?open(cell.pos()):cell.resource().endsWith("_door")?door(cell.pos()):solid(cell.pos()))count++;
        for(Pos p:SetupShelter.accessBlocks(shelterAccess))if(solid(p))count++;
        for(Pos p:shelterAccess)for(int y=1;y<=2;y++)if(open(p.offset(0,y,0)))count++;
        return count;
    }
    public Task task(SetupSurvival.Action action,int goal,int strategy)throws Exception {
        return switch(action){
            case FOOD -> {
                if(strategy==1){
                    foodFromStorage=false;
                    int missing=Math.max(1,goal-snapshot().food());
                    for(String id:List.of("cooked_beef","cooked_porkchop","cooked_mutton","cooked_chicken","cooked_salmon","cooked_cod","bread","baked_potato","cooked_rabbit","apple","carrot")){
                        if(StorageIntelligence.availableSetupItem(controller,id)<1)continue;
                        foodFromStorage=true;
                        Object item=Array.get(callStatic(P+"TaskCatalogue","getItemMatches",id),0);
                        int nutrition=((Number)call(callStatic(P+"multiversion.item.ItemVer","getFoodComponent",item),"getHunger")).intValue();
                        ObservatoryTelemetry.event("SETUP_STORED_SUPPLIES","Food "+id+" available in verified base chest before hunting/crafting");
                        yield EllieSetupTask.nativeAcquisition(id,count(id)+(missing+nutrition-1)/nutrition);
                    }
                    yield collectFood(goal);
                }
                if(foodFromStorage)yield collectFood(goal);
                // Existing native production/storage route, not a second agent planner.
                String food=count("beef")>0?"cooked_beef":count("porkchop")>0?"cooked_porkchop":count("mutton")>0?"cooked_mutton":count("potato")>0?"baked_potato":"bread";
                yield EllieSetupTask.nativeAcquisition(food,(goal+4)/5);
            }
            case EAT -> NativeFood.task(controller,"");
            case FUEL -> {
                if(strategy==1)firstFuel=StorageIntelligence.availableSetupItem(controller,"coal")>0?"coal":"charcoal";
                String selected=strategy==1?firstFuel:firstFuel.equals("coal")?"charcoal":"coal";
                yield EllieSetupTask.nativeAcquisition(selected,Math.max(1,goal-count(selected.equals("coal")?"charcoal":"coal")));
            }
            case SHELTER -> {
                selectShelter();
                String code=SetupShelter.code(shelter,structuralMaterials,shelterAccess,this);
                if(code.isBlank())yield (Task)Class.forName(P+"tasks.movement.GetToBlockTask").getConstructor(Class.forName("net.minecraft.class_2338")).newInstance(pos(shelter.center()));
                Object build=Class.forName(P+"tasks.construction.build_structure.BuildStructureTask").getConstructor(Class.forName("net.minecraft.class_2338"),String.class,String.class,PlayerEngineController.class)
                    .newInstance(pos(shelter.floor()),"small shelter","setup shelter using local wood",controller);
                NativeBuildSupport.blueprint(build,code);yield (Task)build;
            }
            case RETURN -> (Task)Class.forName(P+"tasks.movement.GetToBlockTask").getConstructor(Class.forName("net.minecraft.class_2338")).newInstance(pos(chest.offset(0,0,1)));
            case ENTER -> (Task)Class.forName(P+"tasks.movement.GetToBlockTask").getConstructor(Class.forName("net.minecraft.class_2338")).newInstance(pos(shelter.center()));
            case CLOSE_DOOR -> (Task)Class.forName(P+"tasks.InteractWithBlockTask").getConstructor(Class.forName("net.minecraft.class_2338")).newInstance(pos(shelter.door()));
            case EQUIP -> {
                Object shield=Array.get(callStatic(P+"TaskCatalogue","getItemMatches","shield"),0);
                call(call(controller,"getSlotHandler"),"forceEquipItemToOffhand",shield);
                String sword=count("netherite_sword")>0?"netherite_sword":count("diamond_sword")>0?"diamond_sword":"iron_sword";
                Object items=callStatic(P+"TaskCatalogue","getItemMatches",sword);
                yield (Task)Class.forName(P+"tasks.misc.EquipWeaponTask").getConstructor(items.getClass()).newInstance(items);
            }
        };
    }
    private Task collectFood(int goal)throws Exception{
        int nativeScore=((Number)callStatic(P+"util.helpers.StorageHelper","calculateInventoryFoodScore",controller)).intValue();
        int needed=Math.max(1,goal-snapshot().food());
        return (Task)Class.forName(P+"tasks.resources.CollectFoodTask").getConstructor(double.class).newInstance((double)(nativeScore+needed));
    }
    private void selectShelter()throws Exception {
        if(shelter==null){
            String wood=localWood();int tested=0;
            search: for(int radius:new int[]{6,8,10,12})for(int[] side:new int[][]{{radius,0},{-radius-4,0},{0,radius},{0,-radius-4}})for(int dy:new int[]{-1,0,1,2,3}){
                tested++;SetupShelter.Plan candidate=new SetupShelter.Plan(chest.offset(side[0],dy,side[1]),wood);
                // Natural slopes can be excavated and gaps bridged. A flat pad
                // is an OUTPUT of construction, not a pre-existing prerequisite.
                var blocked=SetupShelter.obstruction(candidate,this);
                if(blocked.isPresent()){
                    if(tested<=4){var cell=blocked.get();String state=loaded(cell.pos())?String.valueOf(state(cell.pos())):"unloaded/out of bounds";ObservatoryTelemetry.event("SETUP_SITE_REJECT","candidate="+key(candidate.floor())+" cell="+cell.pos()+" intended="+cell.resource()+" actual="+state+"; protected or unsafe to prepare");}
                    continue;
                }
                var route=SetupShelter.access(candidate,this);
                if(route.isEmpty()){if(tested<=4)ObservatoryTelemetry.event("SETUP_SITE_REJECT","candidate="+key(candidate.floor())+"; no bounded connected stair/bridge approach to existing walkable terrain");continue;}
                shelter=candidate;shelterAccess=route.get();break search;
            }
            if(shelter==null)throw new IllegalStateException("No safely preparable shelter/bridge connection in "+tested+" bounded candidates near saved chest; protected blocks, hazards or no reachable placement connection prevent construction");
        }
        if(shelterAccess.isEmpty())shelterAccess=SetupShelter.access(shelter,this).orElseThrow(()->new IllegalStateException("Saved shelter has no safely preparable entrance connection"));
        protectStructure();
        if(!SetupShelter.buildable(shelter,shelterAccess,this))throw new IllegalStateException("Recorded shelter/entrance now contains protected blocks or hazards; refusing demolition");
        List<Boolean> missing=new ArrayList<>();for(Pos p:SetupShelter.structuralPositions(shelter,shelterAccess))missing.add(!solid(p));
        structuralMaterials=com.player2.playerengine.reliability.StructuralMaterials.allocate(missing,com.player2.playerengine.reliability.StructuralMaterials.available(controller),com.player2.playerengine.reliability.StructuralMaterials.fallback(controller));
        journal.put("SETUP_SHELTER_V3",0,dimension,siteDetail(chest,shelter,shelterAccess));
        if(!journal.isPersistent())throw new IllegalStateException("Cannot persist shelter position; refusing unrecorded construction");
        Map<String,Long> bill=new LinkedHashMap<>();for(int i=0;i<missing.size();i++)if(missing.get(i))bill.merge(structuralMaterials.get(i),1L,Long::sum);
        ObservatoryTelemetry.event("SETUP_SHELTER_SITE",location()+"; shelter floor="+key(shelter.floor())+" entrance="+shelterAccess+" mixed structural supplies="+bill+"; native excavation/platform construction permitted");
    }
    private void protectStructure()throws Exception {
        if(shelterZone!=null){
            shelterZone.ensure();
            if(structureBreakGuard!=null){Object mutex=call(guardedSettings,"getBreakMutex");synchronized(mutex){List<Object> guards=(List<Object>)call(guardedSettings,"getBreakAvoiders");if(!guards.contains(structureBreakGuard))guards.add(structureBreakGuard);}}
            return;
        }
        structuralCells.addAll(SetupShelter.structuralPositions(shelter,shelterAccess));structuralCells.add(shelter.door());structuralCells.add(shelter.door().offset(0,1,0));
        int minX=shelter.floor().x(),maxX=minX+4,minZ=shelter.floor().z(),maxZ=minZ+4;
        for(Pos p:shelterAccess){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}
        shelterZone=new SetupBuildZone(controller,minX,minZ,maxX-minX+1,maxZ-minZ+1);
        guardedSettings=call(controller,"getExtraBaritoneSettings");Object mutex=call(guardedSettings,"getBreakMutex");
        structureBreakGuard=raw->{
            try{Pos p=new Pos(((Number)call(raw,"method_10263")).intValue(),((Number)call(raw,"method_10264")).intValue(),((Number)call(raw,"method_10260")).intValue());
                return structuralCells.contains(p)&&(solid(p)||door(p));
            }catch(Exception e){return false;}
        };
        synchronized(mutex){((List<Object>)call(guardedSettings,"getBreakAvoiders")).add(structureBreakGuard);}
    }
    private String localWood()throws Exception {
        Map<String,Integer> scores=new LinkedHashMap<>();
        for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry"))scores.put(wood,(count(wood+"_log")+StorageIntelligence.availableSetupItem(controller,wood+"_log"))*4+count(wood+"_planks")+StorageIntelligence.availableSetupItem(controller,wood+"_planks"));
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=0;y<=5;y++){
            Pos p=chest.offset(x,y,z);if(!loaded(p))continue;String id=blockId(p);
            for(String wood:scores.keySet())if(id.contains("minecraft:"+wood+"_log"))scores.put(wood,scores.get(wood)+1);
        }
        return scores.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }
    private int count(String id)throws Exception{return ((Number)call(controller.getItemStorage(),"getItemCountInventoryOnly",callStatic(P+"TaskCatalogue","getItemMatches",id))).intValue();}
    private Object state(Pos p)throws Exception {if(!loaded(p))throw new IllegalStateException("Saved base/shelter chunk is unloaded at "+key(p));return call(call(controller,"getWorld"),"method_8320",pos(p));}
    private boolean loaded(Pos p)throws Exception {Object w=call(controller,"getWorld");return Boolean.TRUE.equals(call(w,"method_22340",pos(p)))&&Boolean.TRUE.equals(call(w,"method_24794",pos(p)))&&Boolean.TRUE.equals(call(call(w,"method_8621"),"method_11952",pos(p)));}
    private String blockId(Pos p)throws Exception{return String.valueOf(call(state(p),"method_26204"));}
    public boolean solid(Pos p)throws Exception {
        if(!loaded(p))return false;Object s=state(p);String id=String.valueOf(call(s,"method_26204"));
        if(List.of("magma","campfire","cactus","fire","powder_snow").stream().anyMatch(id::contains))return false;
        return Boolean.TRUE.equals(call(call(s,"method_26227"),"method_15769"))&&Boolean.TRUE.equals(call(s,"method_26234",call(controller,"getWorld"),pos(p)));
    }
    public boolean open(Pos p)throws Exception{return loaded(p)&&(Boolean.TRUE.equals(call(state(p),"method_26215"))||blockId(p).contains(":torch}"));}
    public boolean door(Pos p)throws Exception{return loaded(p)&&blockId(p).endsWith("_door}")&&!blockId(p).endsWith("_trapdoor}");}
    public boolean canBuild(Placement cell)throws Exception {
        Pos p=cell.pos();if(!loaded(p))return false;Object s=state(p);
        if(cell.resource().equals("air")&&open(p))return true;
        if(call(call(controller,"getWorld"),"method_8321",pos(p))!=null||playerPlaced(p))return false;
        if(Boolean.TRUE.equals(call(s,"method_26215")))return true;
        String id=blockId(p);
        if(!cell.resource().equals("air")&&id.equals("Block{minecraft:water}"))return true;
        // Only excavation is restricted to natural terrain. Full-cube platforms
        // can use mixed inventory blocks and need not have ground under each cell.
        if(!com.player2.playerengine.reliability.StructuralMaterials.naturalToClear(id)||((Number)call(s,"method_26214",call(controller,"getWorld"),pos(p))).floatValue()<0)return false;
        if(com.player2.playerengine.reliability.StructuralMaterials.commonOre(id)){
            boolean stoneTier=count("stone_pickaxe")+count("iron_pickaxe")+count("diamond_pickaxe")+count("netherite_pickaxe")>0;
            if(!stoneTier&&(!id.contains("coal_ore")||count("wooden_pickaxe")==0))return false;
        }
        for(int y=1;y<=4;y++){
            Pos above=p.offset(0,y,0);if(!loaded(above)||(playerPlaced(above)&&!structuralCells.contains(above))||call(call(controller,"getWorld"),"method_8321",pos(above))!=null)return false;
        }
        Object above=state(p.offset(0,1,0));
        if(Class.forName("net.minecraft.class_2346").isInstance(call(above,"method_26204")))return false;
        for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}){
            Pos n=p.offset(d[0],d[1],d[2]);if(!loaded(n)||!Boolean.TRUE.equals(call(call(state(n),"method_26227"),"method_15769")))return false;
        }
        return true;
    }
    private boolean playerPlaced(Pos p)throws Exception {return Boolean.TRUE.equals(call(callStatic(P+"structureprotection.PlayerPlacedBlockStore","get"),"contains",dimension,pos(p)));}
    public String location(){return "chest "+key(chest)+" in "+dimension;}
    public void close(){
        StorageIntelligence.setupChest(controller,null);
        if(shelterZone!=null){shelterZone.close();shelterZone=null;}
        if(structureBreakGuard!=null)try{Object mutex=call(guardedSettings,"getBreakMutex");synchronized(mutex){((List<?>)call(guardedSettings,"getBreakAvoiders")).remove(structureBreakGuard);}}catch(Exception e){ObservatoryTelemetry.event("SETUP_GUARD_RELEASE_FAILED",e.getClass().getSimpleName());}
        structureBreakGuard=null;guardedSettings=null;
    }
    static String[] restoreSite(String status,String saved,String dimension,String savedDimension,Pos chest){
        String[] fields=saved.split("\\|",-1);
        int minimum=status.equals("SETUP_SHELTER_V3")?4:3;
        if(!savedDimension.equals(dimension)||fields.length<minimum||!fields[0].equals(key(chest)))throw new IllegalStateException("Saved survival site differs from recorded workshop; refusing silent relocation");
        parse(fields[1]);if(!Set.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry").contains(fields[2]))throw new IllegalStateException("Invalid recorded shelter wood");
        return fields;
    }
    static String siteDetail(Pos chest,SetupShelter.Plan plan,List<Pos> access){
        String detail=key(chest)+"|"+key(plan.floor())+"|"+plan.wood()+"|"+access.stream().map(SetupSurvivalWorld::key).collect(java.util.stream.Collectors.joining(";"));
        if(access.isEmpty()||detail.length()>360)throw new IllegalStateException("Shelter checkpoint exceeds bounded journal capacity");
        return detail;
    }
    private static Object pos(Pos p)throws Exception{return Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(p.x(),p.y(),p.z());}
    private static String key(Pos p){return p.x()+","+p.y()+","+p.z();}
    private static Pos parse(String value){String[] a=value.split(",");if(a.length!=3)throw new IllegalArgumentException("Invalid shelter coordinates");return new Pos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));}
    private static double[] coordinates(Object p)throws Exception{return new double[]{((Number)call(p,"method_23317")).doubleValue(),((Number)call(p,"method_23318")).doubleValue(),((Number)call(p,"method_23321")).doubleValue()};}
    private static double squared(double[] a,double[] b){double n=0;for(int i=0;i<3;i++)n+=Math.pow(a[i]-b[i],2);return n;}
}
