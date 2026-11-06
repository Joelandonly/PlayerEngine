import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Keep the native water escape policy, but make its A* callbacks non-loading. */
public final class PatchWaterEscapeGoal implements Opcodes {
 static final String P="com/player2/playerengine/",GOAL=P+"tasks/movement/GetOutOfWaterTask$EscapeFromWaterGoal";
 static final String BSI=P+"automaton/utils/BlockStateInterface",CTL=P+"PlayerEngineController";
 static MethodNode method(ClassNode c,String name,String desc){return c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
 static void reset(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;}
 public static void main(String[] args)throws Exception {
  ClassNode c=new ClassNode();try(ZipFile z=new ZipFile(args[0])){new ClassReader(z.getInputStream(Objects.requireNonNull(z.getEntry(GOAL+".class"))).readAllBytes()).accept(c,0);}
  c.fields.add(new FieldNode(ACC_PRIVATE|ACC_FINAL,"safeWaterView","L"+BSI+";",null,null));
  c.fields.add(new FieldNode(ACC_PRIVATE|ACC_FINAL,"safeWaterWorld","Lnet/minecraft/class_3218;",null,null));
  MethodNode ctor=method(c,"<init>","(L"+P+"tasks/movement/GetOutOfWaterTask;L"+CTL+";)V");reset(ctor);
  InsnList i=ctor.instructions;LabelNode missing=new LabelNode(),store=new LabelNode();
  i.add(new VarInsnNode(ALOAD,0));i.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
  i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,2));i.add(new FieldInsnNode(PUTFIELD,GOAL,"mod","L"+CTL+";"));
  i.add(new VarInsnNode(ALOAD,2));i.add(new MethodInsnNode(INVOKEVIRTUAL,CTL,"getWorld","()Lnet/minecraft/class_3218;",false));i.add(new VarInsnNode(ASTORE,3));
  i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,3));i.add(new FieldInsnNode(PUTFIELD,GOAL,"safeWaterWorld","Lnet/minecraft/class_3218;"));
  i.add(new VarInsnNode(ALOAD,3));i.add(new JumpInsnNode(IFNULL,missing));
  i.add(new TypeInsnNode(NEW,BSI));i.add(new InsnNode(DUP));i.add(new VarInsnNode(ALOAD,3));i.add(new MethodInsnNode(INVOKESPECIAL,BSI,"<init>","(Lnet/minecraft/class_1937;)V",false));i.add(new VarInsnNode(ASTORE,4));i.add(new JumpInsnNode(GOTO,store));
  i.add(missing);i.add(new InsnNode(ACONST_NULL));i.add(new VarInsnNode(ASTORE,4));
  i.add(store);i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,4));i.add(new FieldInsnNode(PUTFIELD,GOAL,"safeWaterView","L"+BSI+";"));i.add(new InsnNode(RETURN));
  MethodNode water=method(c,"isWater","(III)Z");reset(water);i=water.instructions;LabelNode unknown=new LabelNode();
  i.add(new VarInsnNode(ALOAD,0));i.add(new FieldInsnNode(GETFIELD,GOAL,"safeWaterView","L"+BSI+";"));i.add(new JumpInsnNode(IFNULL,unknown));
  i.add(new VarInsnNode(ALOAD,0));i.add(new FieldInsnNode(GETFIELD,GOAL,"mod","L"+CTL+";"));i.add(new MethodInsnNode(INVOKEVIRTUAL,CTL,"getWorld","()Lnet/minecraft/class_3218;",false));
  i.add(new VarInsnNode(ALOAD,0));i.add(new FieldInsnNode(GETFIELD,GOAL,"safeWaterWorld","Lnet/minecraft/class_3218;"));i.add(new JumpInsnNode(IF_ACMPNE,unknown));
  i.add(new VarInsnNode(ALOAD,0));i.add(new FieldInsnNode(GETFIELD,GOAL,"safeWaterView","L"+BSI+";"));i.add(new VarInsnNode(ILOAD,1));i.add(new VarInsnNode(ILOAD,3));i.add(new MethodInsnNode(INVOKEVIRTUAL,BSI,"isLoaded","(II)Z",false));i.add(new JumpInsnNode(IFEQ,unknown));
  i.add(new VarInsnNode(ALOAD,0));i.add(new FieldInsnNode(GETFIELD,GOAL,"safeWaterView","L"+BSI+";"));for(int n=1;n<=3;n++)i.add(new VarInsnNode(ILOAD,n));
  i.add(new MethodInsnNode(INVOKEVIRTUAL,BSI,"get0","(III)Lnet/minecraft/class_2680;",false));i.add(new MethodInsnNode(INVOKESTATIC,P+"automaton/pathing/movement/MovementHelper","isWater","(Lnet/minecraft/class_2680;)Z",true));i.add(new InsnNode(IRETURN));
  // Unknown data is not a verified dry escape destination or dry neighbor.
  i.add(unknown);i.add(new InsnNode(ICONST_1));i.add(new InsnNode(IRETURN));
  ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);c.accept(out);Path path=Path.of(args[1],GOAL+".class");Files.createDirectories(path.getParent());Files.write(path,out.toByteArray());
  System.out.println("Water escape: native goal callbacks use non-loading loaded-chunk view; unknown/stale worlds are not dry exits");
 }
}
