import java.nio.file.*;import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Exact native interaction and production handoff hooks; preserve previous overlay edits. */
public final class PatchReliability implements Opcodes {
 static final String P="com/player2/playerengine/";
 static ClassNode read(ZipFile z,Path out,String name)throws Exception{ClassNode c=new ClassNode();Path f=out.resolve(name+".class");new ClassReader(Files.isRegularFile(f)?Files.readAllBytes(f):z.getInputStream(Objects.requireNonNull(z.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;}
 static void save(ClassNode c,Path out)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
 public static void main(String[] a)throws Exception{try(ZipFile z=new ZipFile(a[0])){Path out=Path.of(a[1]);
  ClassNode interaction=read(z,out,P+"automaton/api/entity/LivingEntityInteractionManager");int uses=0;
  for(MethodNode m:interaction.methods)if(m.name.equals("interactBlock"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/class_1799")&&call.name.equals("method_7981")&&call.desc.equals("(Lnet/minecraft/class_1838;)Lnet/minecraft/class_1269;")){
   call.setOpcode(INVOKESTATIC);call.owner=P+"reliability/NpcItemUse";call.name="use";call.desc="(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
   m.instructions.insert(call,new TypeInsnNode(CHECKCAST,"net/minecraft/class_1269"));uses++;
  }
  if(uses!=2)throw new IllegalStateException("Expected two NPC item-use boundaries, got "+uses);save(interaction,out);
  ClassNode macro=read(z,out,P+"tasks/crafting/CraftMacroResourceTask");int hooks=0;
  for(MethodNode m:macro.methods)if(m.name.equals("onResourceTick")&&m.desc.equals("(L"+P+"PlayerEngineController;)L"+P+"tasks/base/Task;")){
   LabelNode normal=new LabelNode();InsnList i=new InsnList();i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,1));i.add(new MethodInsnNode(INVOKESTATIC,P+"reliability/ProductionHandoff","pending","(Ljava/lang/Object;Ljava/lang/Object;)L"+P+"tasks/base/Task;",false));i.add(new InsnNode(DUP));i.add(new JumpInsnNode(IFNULL,normal));i.add(new InsnNode(ARETURN));i.add(normal);i.add(new FrameNode(F_SAME1,0,null,1,new Object[]{P+"tasks/base/Task"}));i.add(new InsnNode(POP));m.instructions.insert(i);hooks++;
  }
  if(hooks!=1)throw new IllegalStateException("Expected one production handoff");save(macro,out);
  System.out.println("Reliability: NPC axe-use guard and in-flight native production handoff installed");
 }}
}
