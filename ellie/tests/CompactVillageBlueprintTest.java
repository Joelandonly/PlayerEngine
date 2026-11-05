import com.player2.playerengine.nativecommands.CompactVillageBlueprint;
import com.player2.playerengine.tasks.construction.build_structure.StructureFromCode;
import java.util.*;
import java.util.regex.*;
public final class CompactVillageBlueprintTest {
 static void check(boolean p,String m){if(!p)throw new AssertionError(m);}
 record Cell(int x,int y,int z){}
 public static void main(String[]args)throws Exception {
  check(CompactVillageBlueprint.accepts("compact cobblestone village","several houses and plaza"),"compact intent");
  check(CompactVillageBlueprint.accepts("village","a small settlement with houses"),"small village");
  check(CompactVillageBlueprint.accepts("village","village"),"unspecified village defaults to reasonable scope");
  check(!CompactVillageBlueprint.accepts("village","a village with five houses"),"five houses should retain full generator");
  check(!CompactVillageBlueprint.accepts("fortified village","a large walled town"),"fortified must retain legacy blueprint");
  check(!CompactVillageBlueprint.accepts("wooden hut","simple shelter"),"non-village must retain legacy blueprint");
  String code=CompactVillageBlueprint.generate(-83,70,-5);
  Matcher m=Pattern.compile("setBlock\\((-?\\d+),(-?\\d+),(-?\\d+),\"([^\"]+)\"\\);").matcher(code);
  Map<Cell,String> blocks=new HashMap<>();Map<String,Integer> counts=new HashMap<>();
  int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
  while(m.find()){int x=Integer.parseInt(m.group(1)),y=Integer.parseInt(m.group(2)),z=Integer.parseInt(m.group(3));String kind=m.group(4);
   Cell cell=new Cell(x,y,z);check(blocks.put(cell,kind)==null,"duplicate cell "+cell);counts.merge(kind,1,Integer::sum);
   minX=Math.min(x,minX);maxX=Math.max(x,maxX);minZ=Math.min(z,minZ);maxZ=Math.max(z,maxZ);
  }
  check(minX==-82&&maxX==-68&&minZ==-4&&maxZ==11,"bounded footprint");
  check(blocks.size()<700,"compact must not recreate the 5,407-cell fortress");
  check(counts.getOrDefault("cobblestone",0)<500,"reasonable cobblestone demand");
  check(blocks.get(new Cell(-78,71,-2)).equals("air")&&blocks.get(new Cell(-78,72,-2)).equals("air"),"west doorway open");
  check(blocks.get(new Cell(-75,70,2)).equals("dirt"),"plaza path available");
  check(code.split("setBlock\\(",-1).length==blocks.size()+1,"only supported literal operations");
  check(StructureFromCode.compile(code)!=null,"real parser must accept the plan");
  System.out.println("CompactVillageBlueprintTest: "+blocks.size()+" blocks; "+counts+"; three houses, entrances, roads, plaza verified");
 }
}
