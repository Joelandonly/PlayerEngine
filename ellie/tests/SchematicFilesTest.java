import com.player2.playerengine.schematic.*;
import java.nio.file.*;import java.util.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;
public final class SchematicFilesTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void refused(Path p,String f)throws Exception{try{SchematicFiles.read(p,f);throw new AssertionError("Accepted "+f);}catch(java.io.IOException|ReflectiveOperationException|IllegalArgumentException expected){checks++;}}
 public static void main(String[]a)throws Exception{
  Path folder=Path.of(a[0]);var p=SchematicFiles.read(folder,"small.litematic");check(p.width==3&&p.height==2&&p.length==3,"real litematica dimensions");check(p.materialCounts.equals(Map.of("minecraft:cobblestone",9,"minecraft:oak_trapdoor",1,"minecraft:water_bucket",1)),"independent fixture material bill");check(p.targets.size()==11,"saved zombie and NBT data ignored");check(p.layers.equals(List.of(0,1)),"deterministic section order");
  var n=SchematicFiles.read(folder,"negative.litematic");check(n.width==3&&n.length==3&&n.targets.size()==11,"negative region axes normalized");
  var m=SchematicFiles.read(folder,"multi.litematic");check(m.width==7&&m.targets.size()==22,"multiple regions combined with correct gap");
  for(String file:List.of("simple.schem")){var s=SchematicFiles.read(folder,file);check(s.materialCounts.get("minecraft:cobblestone")==2,"real parser supports "+file);}
  for(String bad:List.of("../small.litematic","/tmp/small.litematic","missing.litematic","bad.litematic","trailing.litematic","deep.litematic","huge.litematic","small.zip","simple.schematic"))refused(folder,bad);
  Path external=Files.createTempFile("ellie-external", ".litematic");Files.createSymbolicLink(folder.resolve("escape.litematic"),external);try{refused(folder,"escape.litematic");}finally{Files.deleteIfExists(external);Files.deleteIfExists(folder.resolve("escape.litematic"));}
  check(new State("minecraft:stone_slab",Map.of("type","double")).placementItems()==2,"double slab needs two items");check(new State("minecraft:oak_door",Map.of("half","upper")).placementItems()==0,"upper door is not a second door");check(new State("minecraft:white_bed",Map.of("part","head")).placementItems()==0,"bed head is not a second bed");
  check(p.layer(1).getFirst().state().placementState().properties().get("open").equals("false"),"open target normalized only for placement");
  try{p.materialCounts.put("cheat",10);throw new AssertionError("Mutable plan");}catch(UnsupportedOperationException expected){checks++;}
  System.out.println("SchematicFilesTest: "+checks+" checks using the bundled parser; entities/NBT never become supplies");
 }
}
