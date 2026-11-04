import java.util.*;import java.util.zip.*;import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Verify the actual packaged common boundary and prevent silent fixture-only success. */
public final class VerifyRecovery {
 static final String P="com/player2/playerengine/",R=P+"reliability/";
 static ClassNode read(ZipFile z,String n)throws Exception{ClassNode c=new ClassNode();new ClassReader(z.getInputStream(Objects.requireNonNull(z.getEntry(P+n+".class"))).readAllBytes()).accept(c,0);return c;}
 static int calls(ClassNode c,String method,String owner,String name){int count=0;for(MethodNode m:c.methods)if(m.name.equals(method))for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))count++;return count;}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{try(ZipFile z=new ZipFile(args[0])){
  ClassNode task=read(z,"tasks/base/Task");require(task.fields.stream().anyMatch(f->f.name.equals("sharedRecoveryAction")),"missing root-owned recovery lifetime anchor");require(calls(task,"tick",R+"SharedTargetRecovery","beforeTask")==1,"missing shared task gate");require(calls(task,"tick",R+"SharedTargetRecovery","after")==1,"missing common target/progress observation");require(calls(task,"stop",R+"SharedTargetRecovery","stopped")==1,"missing lease cleanup");
  require(calls(read(z,"commands/BlockScanner"),"isUnreachable",R+"SharedTargetRecovery","rejected")==1,"scanner must honor shared target rejection");
  require(calls(read(z,"util/helpers/LookHelper"),"lookAt",R+"SharedTargetRecovery","allowTaskLook")==2,"both task rotation boundaries must yield to movement");
  require(calls(read(z,"automaton/behavior/LookBehavior"),"updateTarget",R+"SharedTargetRecovery","baritoneLook")==1,"missing actual look-writer instrumentation");
  require(calls(read(z,"automaton/utils/InputOverrideHandler"),"setInputForceState",R+"SharedTargetRecovery","inputWrite")==1,"missing actual input-writer instrumentation");
  ClassNode interaction=read(z,"automaton/api/entity/LivingEntityInteractionManager");require(calls(interaction,"interactBlock",R+"NpcItemUse","use")==2,"both NPC item-use paths must be guarded");require(calls(interaction,"interactBlock","net/minecraft/class_1799","method_7981")==0,"unguarded vanilla item use remains");
  require(calls(read(z,"tasks/crafting/CraftMacroResourceTask"),"onResourceTick",R+"ProductionHandoff","pending")==1,"missing production handoff");
  ClassNode smelt=read(z,"tasks/container/SmeltInFurnaceTask");require(calls(smelt,"onResourceTick",R+"ProductionHandoff","beforeSmeltTick")==1,"missing pending-furnace guard");
  for(MethodNode m:smelt.methods)if(m.name.equals("onResourceTick"))for(AbstractInsnNode n:m.instructions)if(n.getOpcode()==Opcodes.ARETURN)require(n.getPrevious() instanceof MethodInsnNode call&&call.owner.equals(R+"ProductionHandoff")&&call.name.equals("invalidate"),"every furnace return must refresh inventory accounting");
  require(z.getEntry(P+"tasks/crafting/CraftTableApproachRecovery$Progress.class")==null,"competing crafting watchdog retained");
  System.out.println("Packaged recovery boundaries verified: shared lease/targets/look, safe NPC use, native furnace handoff and no competing craft watchdog");
 }}
}
