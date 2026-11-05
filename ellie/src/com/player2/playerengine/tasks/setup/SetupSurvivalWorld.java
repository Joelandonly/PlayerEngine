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
    private int evaluatingFloor;
    private boolean foodFromStorage;
    private String firstFuel="charcoal";
    SetupSurvivalWorld(PlayerEngineController controller){this.controller=controller;}
    public void anchor(StarterBaseTask base)throws Exception {
        base.prepare();
        var plan=base.layout();chest=plan.stream().filter(p->p.resource().equals("chest")).findFirst().orElseThrow().pos();
        dimension=StarterBaseWorld.dimension(controller);
        Object store=callStatic(P+"tasks.deferred.DeferredJobStore","get");
        journal=CoreGoalJournal.open((Path)call(store,"worldRoot"),String.valueOf(call(call(controller,"getAIPersistantData"),"getCharacterId")),"setup_survival");
        if(!journal.isPersistent())throw new IllegalStateException("Cannot persist survival base anchor");
        String saved=journal.get("detail","");
        if(journal.get("status","").equals("SETUP_SHELTER_V1")){
            String[] fields=saved.split("\\|",-1);
            if(!journal.get("resource","").equals(dimension)||fields.length!=3||!fields[0].equals(key(chest)))throw new IllegalStateException("Saved survival site differs from recorded workshop; refusing silent relocation");
            shelter=new SetupShelter.Plan(parse(fields[1]),fields[2]);
        }
        StorageIntelligence.setupChest(controller,pos(chest));
        ObservatoryTelemetry.event("SETUP_BASE_ANCHOR","Saved workshop chest "+key(chest)+" in "+dimension+"; no relocation or personal-home change");
    }
    public SetupSurvival.Snapshot snapshot()throws Exception {
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
    public boolean shelterVerified()throws Exception{return shelter!=null&&SetupShelter.verified(shelter,this);}
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
        if(!shelterVerified()&&!SetupShelter.buildable(shelter,this))throw new IllegalStateException("Shelter site changed or contains protected/foreign blocks; refusing demolition at "+key(shelter.floor()));
        int count=0;for(var cell:SetupShelter.cells(shelter))if(cell.resource().equals("air")?open(cell.pos()):cell.resource().endsWith("_door")?door(cell.pos()):solid(cell.pos()))count++;
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
                Object build=Class.forName(P+"tasks.construction.build_structure.BuildStructureTask").getConstructor(Class.forName("net.minecraft.class_2338"),String.class,String.class,PlayerEngineController.class)
                    .newInstance(pos(shelter.floor()),"small shelter","setup shelter using local wood",controller);
                NativeBuildSupport.blueprint(build,SetupShelter.code(shelter));yield (Task)build;
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
        if(shelter!=null){if(!shelterVerified()&&!SetupShelter.buildable(shelter,this))throw new IllegalStateException("Saved shelter cannot be repaired without destroying foreign blocks");return;}
        String wood=localWood();
        search: for(int radius:new int[]{6,8,10,12})for(int[] side:new int[][]{{radius,0},{-radius-4,0},{0,radius},{0,-radius-4}})for(int dy:new int[]{-1,0,1}){
            SetupShelter.Plan candidate=new SetupShelter.Plan(chest.offset(side[0],dy,side[1]),wood);
            evaluatingFloor=candidate.floor().y();
            if(SetupShelter.verified(candidate,this)||SetupShelter.buildable(candidate,this)){shelter=candidate;break search;}
        }
        if(shelter==null)throw new IllegalStateException("No supported clear 5x5 shelter site within the bounded workshop search; clear a patch near the saved chest");
        journal.put("SETUP_SHELTER_V1",0,dimension,key(chest)+"|"+key(shelter.floor())+"|"+shelter.wood());
        if(!journal.isPersistent())throw new IllegalStateException("Cannot persist shelter position; refusing unrecorded construction");
        ObservatoryTelemetry.event("SETUP_SHELTER_SITE",location()+"; shelter floor="+key(shelter.floor())+" wood="+shelter.wood());
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
        Pos p=cell.pos();if(!loaded(p))return false;
        String id=blockId(p);
        if(id.endsWith(":"+cell.resource()+"}"))return true;
        Object s=state(p);if(Boolean.TRUE.equals(call(s,"method_26215")))return true;
        if(call(call(controller,"getWorld"),"method_8321",pos(p))!=null)return false;
        Object protectedStore=callStatic(P+"structureprotection.PlayerPlacedBlockStore","get");
        if(Boolean.TRUE.equals(call(protectedStore,"contains",dimension,pos(p))))return false;
        // Only naturally supported ground is replaced by the floor. No tree,
        // player structure or interior obstacle is cleared to force a shelter fit.
        return p.y()==(shelter==null?evaluatingFloor:shelter.floor().y())&&cell.resource().endsWith("_planks")&&solid(p)
            &&List.of(":dirt}",":grass_block}",":stone}",":sand}",":gravel}",":deepslate}").stream().anyMatch(id::endsWith)
            &&Boolean.TRUE.equals(call(state(p.offset(0,1,0)),"method_26215"));
    }
    public String location(){return "chest "+key(chest)+" in "+dimension;}
    public void close(){StorageIntelligence.setupChest(controller,null);}
    private static Object pos(Pos p)throws Exception{return Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(p.x(),p.y(),p.z());}
    private static String key(Pos p){return p.x()+","+p.y()+","+p.z();}
    private static Pos parse(String value){String[] a=value.split(",");if(a.length!=3)throw new IllegalArgumentException("Invalid shelter coordinates");return new Pos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));}
    private static double[] coordinates(Object p)throws Exception{return new double[]{((Number)call(p,"method_23317")).doubleValue(),((Number)call(p,"method_23318")).doubleValue(),((Number)call(p,"method_23321")).doubleValue()};}
    private static double squared(double[] a,double[] b){double n=0;for(int i=0;i<3;i++)n+=Math.pow(a[i]-b[i],2);return n;}
}
