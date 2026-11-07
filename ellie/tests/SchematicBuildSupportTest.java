import com.player2.playerengine.schematic.*;
import com.player2.playerengine.automaton.api.schematic.ISchematic;
import com.player2.playerengine.automaton.api.entity.LivingEntityPlacementContext;
import net.minecraft.*;
import java.util.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;
public final class SchematicBuildSupportTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static final class Builder {public Object schematic,realSchematic,ctx;}
 public record Entity(float yaw){public class_2350 method_5735(){return class_2350.EAST;}public float method_36454(){return yaw;}}
 public record Context(Object entity){}
 public record Placement(Object player){public Object method_8036(){return player;}}
 public static final class Block {
  boolean throwsError;public Object method_9605(Placement placement)throws Exception {if(throwsError)throw new IllegalStateException("cannot predict");return SchematicBuildSupport.horizontal(placement);}
 }
 public static void main(String[]a)throws Exception{
  Object stone="Block{minecraft:cobblestone}",chest="Block{minecraft:chest}",trap="Block{minecraft:oak_trapdoor}";
  class_2680 cobble=class_2680.state(stone),closed=class_2680.state(trap,"open","false","facing","north"),open=class_2680.state(trap,"open","true","facing","north");
  var desired=Map.<Long,Object>of(0L,cobble,1L,closed,2L,cobble);var finals=Map.<Long,Object>of(0L,cobble,1L,open,2L,cobble);
  var specs=Map.of(0L,new State("minecraft:cobblestone",Map.of()),1L,new State("minecraft:oak_trapdoor",Map.of("open","true")),2L,new State("minecraft:water",Map.of("level","0")));
  ISchematic layer=(ISchematic)SchematicBuildSupport.section(1,3,desired,finals,specs);check(layer.widthX()==1&&layer.heightY()==1&&layer.lengthZ()==3,"one bounded horizontal native section");check(!layer.inSchematic(0,1,0,cobble)&&layer.inSchematic(0,0,2,cobble),"outside layer never exposed to native builder");
  check(layer.desiredState(0,0,1,open,List.of()).equals(open),"completed open state never undone during resume");check(layer.desiredState(0,0,1,cobble,List.of()).equals(closed),"native placement receives normalized closed state");check(layer.desiredState(0,0,2,open,List.of()).equals(open),"native builder leaves fluid cells for explicit phase");
  Builder builder=new Builder();check(SchematicBuildSupport.valid(builder,closed,open)==-1,"legacy state validation unchanged");builder.schematic=layer;check(SchematicBuildSupport.valid(builder,closed,open)==0,"same block with wrong properties is not complete");check(SchematicBuildSupport.valid(builder,open,open)==1,"actual correct state recognized");
  class_2680 left=class_2680.state(chest,"type","left","facing","north"),single=class_2680.state(chest,"type","single","facing","north"),wrong=class_2680.state(chest,"type","single","facing","south");
  check(SchematicBuildSupport.valid(builder,single,left)==1,"temporary single chest allowed while joining pair");check(SchematicBuildSupport.valid(builder,wrong,left)==0,"chest facing still required");
  Entity entity=new Entity(93);builder.ctx=new Context(entity);SchematicBuildSupport.bindEntity(entity,builder);Block block=new Block();Placement empty=new Placement(null);
  check(SchematicBuildSupport.horizontal(empty)==class_2350.NORTH,"normal null-player fallback preserved");check(SchematicBuildSupport.predicted(block,empty,builder)==class_2350.EAST,"imported prediction uses NPC direction");check(LivingEntityPlacementContext.peek()==null,"prediction context popped after success");
  LivingEntityPlacementContext.push(entity);try{check(SchematicBuildSupport.rotation(empty)==93,"NPC yaw used during imported placement");check(SchematicBuildSupport.horizontal(new Placement(new Entity(10)))==class_2350.EAST,"normal player direction preserved");}finally{LivingEntityPlacementContext.pop();}
  block.throwsError=true;check(SchematicBuildSupport.predicted(block,empty,builder)==null&&LivingEntityPlacementContext.peek()==null,"prediction error pops context and lets builder restore yaw/pitch");
  builder.schematic=null;try{SchematicBuildSupport.predicted(block,empty,builder);throw new AssertionError("legacy exception swallowed");}catch(ReflectiveOperationException expected){checks++;}
  System.out.println("SchematicBuildSupportTest: "+checks+" real proxy/property/context checks; Minecraft block/entity boundary substituted");
 }
}
