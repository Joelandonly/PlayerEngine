import java.nio.file.*;import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Preserve native movement bodies; veto unsafe downward candidates and stale live descents only. */
public final class PatchSafeDescents implements Opcodes {
 static final String P="com/player2/playerengine/automaton/",H="com/player2/playerengine/reliability/SafeDescent";
 static ClassNode read(ZipFile z,Path out,String name)throws Exception{Path p=out.resolve(name+".class");ClassNode c=new ClassNode();new ClassReader(Files.exists(p)?Files.readAllBytes(p):z.getInputStream(Objects.requireNonNull(z.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;}
 static void save(ClassNode c,Path out)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
 public static void main(String[] args)throws Exception{Path out=Path.of(args[1]);try(ZipFile z=new ZipFile(args[0])){
  ClassNode astar=read(z,out,P+"pathing/calc/AStarPathFinder");int hooks=0;
  for(MethodNode m:astar.methods)if(m.name.equals("calculate0"))for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode call&&call.owner.equals(P+"pathing/movement/Moves")&&call.name.equals("apply")){
   if(!call.desc.equals("(L"+P+"pathing/movement/CalculationContext;IIIL"+P+"utils/pathing/MutableMoveResult;)V"))throw new AssertionError("Native Moves.apply ABI changed");
   int v=m.maxLocals;m.maxLocals+=5;InsnList before=new InsnList();
   before.add(new VarInsnNode(ASTORE,v));before.add(new VarInsnNode(ISTORE,v+1));before.add(new VarInsnNode(ISTORE,v+2));before.add(new VarInsnNode(ISTORE,v+3));before.add(new VarInsnNode(ASTORE,v+4));
   before.add(new VarInsnNode(ALOAD,v+4));before.add(new VarInsnNode(ILOAD,v+3));before.add(new VarInsnNode(ILOAD,v+2));before.add(new VarInsnNode(ILOAD,v+1));before.add(new VarInsnNode(ALOAD,v));m.instructions.insertBefore(i,before);
   InsnList after=new InsnList();after.add(new VarInsnNode(ALOAD,v+4));after.add(new VarInsnNode(ILOAD,v+2));after.add(new VarInsnNode(ALOAD,v));after.add(new MethodInsnNode(INVOKESTATIC,H,"filter","(Ljava/lang/Object;ILjava/lang/Object;)V",false));m.instructions.insert(i,after);hooks++;
  }
  if(hooks!=1)throw new AssertionError("Expected one native candidate boundary, got "+hooks);save(astar,out);
  ClassNode movement=read(z,out,P+"pathing/movement/Movement");int runtime=0;
  for(MethodNode m:movement.methods)if(m.name.equals("update")&&m.desc.equals("()L"+P+"api/pathing/movement/MovementStatus;")){
   LabelNode normal=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(ALOAD,0));gate.add(new MethodInsnNode(INVOKESTATIC,H,"rejectMovement","(Ljava/lang/Object;)Z",false));gate.add(new JumpInsnNode(IFEQ,normal));
   gate.add(new VarInsnNode(ALOAD,0));gate.add(new FieldInsnNode(GETFIELD,movement.name,"baritone","L"+P+"api/IBaritone;"));gate.add(new MethodInsnNode(INVOKEINTERFACE,P+"api/IBaritone","getInputOverrideHandler","()L"+P+"api/utils/IInputOverrideHandler;",true));gate.add(new MethodInsnNode(INVOKEINTERFACE,P+"api/utils/IInputOverrideHandler","clearAllKeys","()V",true));
   gate.add(new FieldInsnNode(GETSTATIC,P+"api/pathing/movement/MovementStatus","UNREACHABLE","L"+P+"api/pathing/movement/MovementStatus;"));gate.add(new InsnNode(ARETURN));gate.add(normal);gate.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(gate);runtime++;
  }
  if(runtime!=1)throw new AssertionError("Expected one native movement pre-commit boundary");save(movement,out);
  System.out.println("Safe descents: A* downward-edge filter + pre-input movement gate; native bridging bodies unchanged");
 }}
}
