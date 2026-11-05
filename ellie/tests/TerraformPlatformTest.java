import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.setup.StarterBaseLayout.*;
import com.player2.playerengine.reliability.StructuralMaterials;
import java.util.*;
import java.util.function.ToIntBiFunction;
import java.util.regex.*;

/** Real planner/code generation on voxel terrain; no successful-builder flags. */
public final class TerraformPlatformTest {
    static final SetupShelter.Plan PLAN=new SetupShelter.Plan(new Pos(0,64,0),"oak");
    static final Pattern COMMAND=Pattern.compile("setBlock\\((-?\\d+),(-?\\d+),(-?\\d+),\"([^\"]+)\"\\);");
    static final class World implements SetupShelter.Probe {
        final Map<Pos,String> changes=new HashMap<>();final Set<Pos> protectedCells=new HashSet<>();
        final ToIntBiFunction<Integer,Integer> height;
        World(ToIntBiFunction<Integer,Integer> height){this.height=height;}
        String block(Pos p){return changes.getOrDefault(p,p.y()<=height.applyAsInt(p.x(),p.z())?"stone":"air");}
        public boolean solid(Pos p){String id=block(p);return !id.equals("air")&&!id.equals("water")&&!id.equals("lava")&&!id.endsWith("_door");}
        public boolean open(Pos p){return block(p).equals("air");}
        public boolean door(Pos p){return block(p).endsWith("_door");}
        public boolean canBuild(Placement p){
            if(protectedCells.contains(p.pos()))return false;String id=block(p.pos());
            return id.equals("air")||!p.resource().equals("air")&&id.equals("water")||StructuralMaterials.naturalToClear(id);
        }
        void apply(String code){for(var entry:commands(code).entrySet())changes.put(entry.getKey(),entry.getValue().replace("minecraft:",""));}
    }
    static Map<Pos,String> commands(String code){Map<Pos,String> out=new LinkedHashMap<>();Matcher m=COMMAND.matcher(code);while(m.find()){
        Pos p=new Pos(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
        if(out.put(p,m.group(4))!=null)throw new AssertionError("Contradictory duplicate operation at "+p);
    }return out;}
    static List<String> materials(World w,List<Pos> path)throws Exception{
        List<Boolean> missing=new ArrayList<>();for(Pos p:SetupShelter.structuralPositions(PLAN,path))missing.add(!w.solid(p));
        return StructuralMaterials.allocate(missing,new LinkedHashMap<>(Map.of("minecraft:dirt",12,"minecraft:cobblestone",9,"minecraft:stone",7)),"minecraft:dirt");
    }
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void connected(String code,World before){
        Set<Pos> pending=new HashSet<>();for(var item:commands(code).entrySet())if(!item.getValue().equals("air")&&!item.getValue().endsWith("_door"))pending.add(item.getKey());
        Set<Pos> placed=new HashSet<>();boolean progress=true;
        while(progress&&!pending.isEmpty()){
            progress=false;
            for(var it=pending.iterator();it.hasNext();){Pos p=it.next();for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}){
                Pos n=p.offset(d[0],d[1],d[2]);if(before.solid(n)||placed.contains(n)){placed.add(p);it.remove();progress=true;break;}
            }}
        }
        check(pending.isEmpty(),"Every new full cube must admit a real neighboring-block placement connection");
    }
    public static void main(String[] args)throws Exception{
        int scenarios=0;
        World flat=new World((x,z)->64);var flatPath=SetupShelter.access(PLAN,flat).orElseThrow();
        String mixed=SetupShelter.code(PLAN,materials(flat,flatPath),flatPath,flat);
        check(mixed.contains("minecraft:dirt")&&mixed.contains("minecraft:cobblestone")&&mixed.contains("minecraft:stone"),"mixed safe carried/stored blocks are used before collecting a single prescribed material");
        check(commands(mixed).keySet().stream().noneMatch(p->p.y()==64),"existing natural floor reused, not turned into a stone/silk-touch acquisition request");
        connected(mixed,flat);check(commands(mixed).get(PLAN.center()).equals("air"),"already-clear interior remains an explicit blueprint obligation so temporary scaffolds cannot be ignored");flat.apply(mixed);check(SetupShelter.verified(PLAN,flat)&&SetupShelter.accessVerified(flatPath,flat),"mixed-material enclosure verified by actual resulting voxel geometry");scenarios++;
        World hill=new World((x,z)->x>=3?66:64);
        check(SetupShelter.buildable(PLAN,hill),"natural uneven hillside is preparable instead of rejected for lacking a flat 5x5 pad");
        var hillPath=SetupShelter.access(PLAN,hill).orElseThrow();String carve=SetupShelter.code(PLAN,materials(hill,hillPath),hillPath,hill);
        check(commands(carve).values().contains("air"),"hill terrain is explicitly excavated through the existing native builder");
        hill.apply(carve);check(SetupShelter.verified(PLAN,hill),"carved hillside with reused stone walls/floor is a valid enclosure");scenarios++;
        World cliff=new World((x,z)->z<=-3?62:-100);
        check(SetupShelter.buildable(PLAN,cliff),"unsupported floor can become a platform connected to a nearby lower ledge");
        var bridgePath=SetupShelter.access(PLAN,cliff).orElseThrow();String bridge=SetupShelter.code(PLAN,materials(cliff,bridgePath),bridgePath,cliff);
        check(bridgePath.size()>1&&SetupShelter.accessBlocks(bridgePath).size()>bridgePath.size(),"height changes include face-connected stair backing blocks");
        connected(bridge,cliff);cliff.apply(bridge);
        check(SetupShelter.verified(PLAN,cliff)&&SetupShelter.accessVerified(bridgePath,cliff),"platform and stair/bridge access physically verified");
        check(cliff.block(PLAN.floor().offset(2,-1,2)).equals("air"),"nongravity platform needs no invented pillar under every floor block");scenarios++;
        World highLedge=new World((x,z)->z<=-6?69:-100);
        var highPath=SetupShelter.access(PLAN,highLedge).orElseThrow();
        check(highPath.size()>=6,"bounded approach can reach a nearby higher ledge instead of demanding the same elevation");
        connected(SetupShelter.code(PLAN,materials(highLedge,highPath),highPath,highLedge),highLedge);scenarios++;
        World voidWorld=new World((x,z)->-100);check(!SetupShelter.buildable(PLAN,voidWorld),"unconnected air with no reachable placement anchor is a genuine blocker");scenarios++;
        World owned=new World((x,z)->66);owned.protectedCells.add(PLAN.center());owned.protectedCells.add(PLAN.door());check(!SetupShelter.buildable(PLAN,owned),"leveling cannot demolish a protected structure");scenarios++;
        World storage=new World((x,z)->64);storage.changes.put(PLAN.center(),"chest");check(!SetupShelter.buildable(PLAN,storage),"existing chest cannot be removed to fit the room");scenarios++;
        World hazard=new World((x,z)->64);hazard.changes.put(PLAN.center(),"lava");check(!SetupShelter.buildable(PLAN,hazard),"interior lava is not treated as ordinary excavation");scenarios++;
        World partial=new World((x,z)->64);partial.changes.put(PLAN.floor().offset(0,1,0),"terracotta");var partialPath=SetupShelter.access(PLAN,partial).orElseThrow();
        String resume=SetupShelter.code(PLAN,materials(partial,partialPath),partialPath,partial);
        check(!commands(resume).containsKey(PLAN.floor().offset(0,1,0)),"already placed structural blocks survive a material change on resume");
        partial.apply(resume);check(SetupShelter.verified(PLAN,partial),"partial mixed building can finish without replacing existing valid blocks");scenarios++;
        check(StructuralMaterials.naturalToClear("minecraft:poppy")&&StructuralMaterials.naturalToClear("minecraft:sandstone")&&StructuralMaterials.commonOre("minecraft:coal_ore"),"ordinary plants, sandstone and tool-checked common ore are preparable terrain");
        check(StructuralMaterials.safe("minecraft:dirt",true,false,false)&&StructuralMaterials.safe("minecraft:terracotta",true,false,false),"common and decorative full cubes can be used");
        check(!StructuralMaterials.safe("minecraft:sand",true,true,false)&&!StructuralMaterials.safe("minecraft:gravel",true,true,false),"falling blocks excluded from floating structural roles");
        check(!StructuralMaterials.safe("minecraft:chest",true,false,true)&&!StructuralMaterials.safe("minecraft:diamond_block",true,false,false)&&!StructuralMaterials.safe("minecraft:tnt",true,false,false),"containers/valuables/explosives not consumed as fill");
        check(!StructuralMaterials.safe("minecraft:oak_slab",false,false,false),"partial shapes cannot masquerade as a complete floor cube");scenarios++;
        System.out.println("TerraformPlatformTest: "+scenarios+" terrain/platform/material scenarios passed (voxel world substituted; live placement unverified)");
    }
}
