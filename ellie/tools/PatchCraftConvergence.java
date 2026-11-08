import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** One bounded phase handoff after collection; preserve native failed-action and stall guards. */
public final class PatchCraftConvergence implements Opcodes {
 static final String P="com/player2/playerengine/",NAME=P+"tasks/crafting/CraftMacroResourceTask";
 static final String PHASE=P+"tasks/crafting/CraftMacroPhase",RESULT=P+"tasks/crafting/resolver/ResolverResult";
 public static void main(String[] args)throws Exception{
  Path path=Path.of(args[1],NAME+".class");ClassNode type=new ClassNode();
  try(ZipFile jar=new ZipFile(args[0])){new ClassReader(Files.exists(path)?Files.readAllBytes(path):jar.getInputStream(jar.getEntry(NAME+".class")).readAllBytes()).accept(type,0);}
  if(type.fields.stream().anyMatch(f->f.name.equals("ellieCollectHandoffGranted")))throw new IllegalStateException("Convergence patch already applied");
  type.fields.add(new FieldNode(ACC_PRIVATE,"ellieCollectHandoffGranted","Z",null,null));
  type.fields.add(new FieldNode(ACC_PRIVATE,"ellieCollectHandoffCount","I",null,null));
  MethodNode helper=new MethodNode(ACC_PRIVATE,"ellieCollectionHandoff","()Z",null,null);
  InsnList h=helper.instructions;LabelNode collecting=new LabelNode(),reject=new LabelNode();
  h.add(new VarInsnNode(ALOAD,0));h.add(new FieldInsnNode(GETFIELD,NAME,"phase","L"+PHASE+";"));h.add(new FieldInsnNode(GETSTATIC,PHASE,"COLLECT_MISSING_MATERIALS","L"+PHASE+";"));h.add(new JumpInsnNode(IF_ACMPEQ,collecting));
  h.add(new VarInsnNode(ALOAD,0));h.add(new InsnNode(ICONST_0));h.add(new FieldInsnNode(PUTFIELD,NAME,"ellieCollectHandoffGranted","Z"));h.add(new InsnNode(ICONST_0));h.add(new InsnNode(IRETURN));
  h.add(collecting);h.add(new FrameNode(F_SAME,0,null,0,null));
  h.add(new VarInsnNode(ALOAD,0));h.add(new FieldInsnNode(GETFIELD,NAME,"ellieCollectHandoffGranted","Z"));h.add(new JumpInsnNode(IFNE,reject));
  h.add(new VarInsnNode(ALOAD,0));h.add(new FieldInsnNode(GETFIELD,NAME,"ellieCollectHandoffCount","I"));h.add(new IntInsnNode(BIPUSH,8));h.add(new JumpInsnNode(IF_ICMPGE,reject));
  h.add(new VarInsnNode(ALOAD,0));h.add(new MethodInsnNode(INVOKEVIRTUAL,NAME,"resolvedExternals","()Ljava/util/List;",false));h.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/List","isEmpty","()Z",true));h.add(new JumpInsnNode(IFEQ,reject));
  h.add(new VarInsnNode(ALOAD,0));h.add(new FieldInsnNode(GETFIELD,NAME,"lastResult","L"+RESULT+";"));h.add(new MethodInsnNode(INVOKEVIRTUAL,RESULT,"status","()L"+RESULT+"$Status;",false));h.add(new FieldInsnNode(GETSTATIC,RESULT+"$Status","NEEDS_WORK","L"+RESULT+"$Status;"));h.add(new JumpInsnNode(IF_ACMPNE,reject));
  h.add(new VarInsnNode(ALOAD,0));h.add(new InsnNode(ICONST_1));h.add(new FieldInsnNode(PUTFIELD,NAME,"ellieCollectHandoffGranted","Z"));
  h.add(new VarInsnNode(ALOAD,0));h.add(new InsnNode(DUP));h.add(new FieldInsnNode(GETFIELD,NAME,"ellieCollectHandoffCount","I"));h.add(new InsnNode(ICONST_1));h.add(new InsnNode(IADD));h.add(new FieldInsnNode(PUTFIELD,NAME,"ellieCollectHandoffCount","I"));
  for(String field:List.of("noProgressCount","resolveAttempts")){h.add(new VarInsnNode(ALOAD,0));h.add(new InsnNode(ICONST_0));h.add(new FieldInsnNode(PUTFIELD,NAME,field,"I"));}
  h.add(new VarInsnNode(ALOAD,0));h.add(new VarInsnNode(ALOAD,0));h.add(new FieldInsnNode(GETFIELD,NAME,"lastResult","L"+RESULT+";"));h.add(new MethodInsnNode(INVOKEVIRTUAL,RESULT,"remainingDeficit","()I",false));h.add(new FieldInsnNode(PUTFIELD,NAME,"lastDeficit","I"));h.add(new InsnNode(ICONST_1));h.add(new InsnNode(IRETURN));
  h.add(reject);h.add(new FrameNode(F_SAME,0,null,0,null));h.add(new InsnNode(ICONST_0));h.add(new InsnNode(IRETURN));type.methods.add(helper);
  int hooks=0;
  for(MethodNode method:type.methods)if(method.name.equals("resolveNow"))for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode call&&call.owner.equals(RESULT)&&call.name.equals("remainingDeficit")){
   AbstractInsnNode start=call.getPrevious().getPrevious();
   if(!(start instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==0))throw new IllegalStateException("Unknown native convergence boundary");
   LabelNode normal=new LabelNode();InsnList guard=new InsnList();guard.add(new VarInsnNode(ALOAD,0));guard.add(new MethodInsnNode(INVOKEVIRTUAL,NAME,"ellieCollectionHandoff","()Z",false));guard.add(new JumpInsnNode(IFEQ,normal));guard.add(new InsnNode(ICONST_0));guard.add(new InsnNode(IRETURN));guard.add(normal);guard.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insertBefore(start,guard);hooks++;
  }
  if(hooks!=1)throw new IllegalStateException("Expected one post-UNOBTAINABLE convergence boundary, got "+hooks);
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);Files.createDirectories(path.getParent());Files.write(path,writer.toByteArray());
  System.out.println("Craft convergence: one handoff per collection episode, at most eight per macro; native no-progress, table and failed-craft bounds retained");
 }
}
