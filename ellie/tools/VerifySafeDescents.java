package ellie.descent;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import static ellie.descent.SafeDescentTest.*;
import ellie.descent.SafeDescentTest.*;

/** Executes the actual packaged gate with only native interfaces/body substituted. */
public final class VerifySafeDescents implements Opcodes {
 static final String P="com/player2/playerengine/automaton/",H="com/player2/playerengine/reliability/SafeDescent",PROBE="ellie/descent/DescentGateProbe";
 static ClassNode read(ZipFile jar,String name)throws Exception{
  ClassNode node=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(name+".class")).readAllBytes()).accept(node,0);return node;
 }
 static class Loader extends ClassLoader {Class<?> define(byte[] bytes){return defineClass(PROBE.replace('/','.'),bytes,0,bytes.length);}}
 public static void main(String[] args)throws Exception {
  ClassNode movement,astar,executor;
  try(ZipFile jar=new ZipFile(args[0]);ZipFile baseline=new ZipFile(args[1])){
   movement=read(jar,P+"pathing/movement/Movement");astar=read(jar,P+"pathing/calc/AStarPathFinder");executor=read(jar,P+"pathing/path/PathExecutor");
   int retained=0;
   for(var entries=baseline.entries();entries.hasMoreElements();){var entry=entries.nextElement();String n=entry.getName();
    if(n.startsWith(P+"pathing/movement/movements/")||n.equals(P+"pathing/movement/Moves.class")||n.equals(P+"pathing/path/PathExecutor.class")){
     check(Arrays.equals(baseline.getInputStream(entry).readAllBytes(),jar.getInputStream(jar.getEntry(n)).readAllBytes()),"native bridging/construction/executor changed: "+n);retained++;
    }
   }
   check(retained>10,"movement preservation coverage");
  }
  int filters=0;
  for(MethodNode m:astar.methods)for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode call&&call.owner.equals(H)){
   check(m.name.equals("calculate0")&&call.name.equals("filter"),"only candidate-cost boundary filtered");
   AbstractInsnNode prev=n.getPrevious();check(prev instanceof VarInsnNode&&prev.getOpcode()==ALOAD,"candidate result passed");
   prev=prev.getPrevious();check(prev instanceof VarInsnNode&&prev.getOpcode()==ILOAD,"source Y passed");
   prev=prev.getPrevious();check(prev instanceof VarInsnNode&&prev.getOpcode()==ALOAD,"context passed");
   prev=prev.getPrevious();check(prev instanceof MethodInsnNode c&&c.owner.equals(P+"pathing/movement/Moves")&&c.name.equals("apply"),"filter immediately after native Moves.apply before cost use");filters++;
  }
  check(filters==1,"one shared planner filter");
  MethodNode update=movement.methods.stream().filter(m->m.name.equals("update")).findFirst().orElseThrow();
  AbstractInsnNode first=update.instructions.getFirst();check(first.getOpcode()==ALOAD,"gate must start method before native preparation");
  check(first.getNext() instanceof MethodInsnNode c&&c.owner.equals(H)&&c.name.equals("rejectMovement"),"first call is pre-commit check");
  JumpInsnNode branch=(JumpInsnNode)first.getNext().getNext();check(branch.getOpcode()==IFEQ,"allowed moves resume original body");
  boolean cancellation=false;
  for(MethodNode m:executor.methods)if(m.name.equals("onTick")){
   boolean unreachable=false;
   for(AbstractInsnNode n:m.instructions){
    if(n instanceof FieldInsnNode f&&f.name.equals("UNREACHABLE"))unreachable=true;
    if(unreachable&&n instanceof MethodInsnNode c&&c.name.equals("cancel"))cancellation=true;
   }
  }
  check(cancellation,"native executor cancels unreachable movement");

  // Retain the gate exactly, replace the old movement body with a sentinel.
  // Remap native interface calls to minimal boundary fixtures; never run a game executor here.
  AbstractInsnNode n=branch.label.getNext();while(n!=null){AbstractInsnNode next=n.getNext();update.instructions.remove(n);n=next;}
  update.tryCatchBlocks.clear();update.localVariables=null;
  update.instructions.add(new FrameNode(F_SAME,0,null,0,null));
  update.instructions.add(new VarInsnNode(ALOAD,0));update.instructions.add(new InsnNode(DUP));
  update.instructions.add(new FieldInsnNode(GETFIELD,PROBE,"bodyEntered","I"));update.instructions.add(new InsnNode(ICONST_1));update.instructions.add(new InsnNode(IADD));update.instructions.add(new FieldInsnNode(PUTFIELD,PROBE,"bodyEntered","I"));
  update.instructions.add(new FieldInsnNode(GETSTATIC,P+"api/pathing/movement/MovementStatus","RUNNING","L"+P+"api/pathing/movement/MovementStatus;"));update.instructions.add(new InsnNode(ARETURN));
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V21,ACC_PUBLIC,PROBE,null,"java/lang/Object",null);
  for(String f:List.of("src","dest","ctx"))w.visitField(ACC_PUBLIC,f,"Ljava/lang/Object;",null,null).visitEnd();
  w.visitField(ACC_PUBLIC,"baritone","Lellie/descent/SafeDescentTest$Baritone;",null,null).visitEnd();w.visitField(ACC_PUBLIC,"bodyEntered","I",null,null).visitEnd();
  MethodVisitor init=w.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(ALOAD,0);init.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(RETURN);init.visitMaxs(0,0);init.visitEnd();
  update.accept(new MethodVisitor(ASM8,w.visitMethod(ACC_PUBLIC,"update",update.desc,null,null)){
   public void visitFieldInsn(int op,String owner,String name,String desc){super.visitFieldInsn(op,owner.equals(movement.name)?PROBE:owner,name,name.equals("baritone")?"Lellie/descent/SafeDescentTest$Baritone;":desc);}
   public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
    if(owner.equals(P+"api/IBaritone")){super.visitMethodInsn(INVOKEVIRTUAL,"ellie/descent/SafeDescentTest$Baritone",name,"()Lellie/descent/SafeDescentTest$Inputs;",false);return;}
    if(owner.equals(P+"api/utils/IInputOverrideHandler")){super.visitMethodInsn(INVOKEVIRTUAL,"ellie/descent/SafeDescentTest$Inputs",name,desc,false);return;}
    super.visitMethodInsn(op,owner,name,desc,itf);
   }
  });w.visitEnd();Class<?> probe=new Loader().define(w.toByteArray());Object move=probe.getConstructor().newInstance();
  World world=new World();world.put(3,"stone",1);EntityContext ctx=new EntityContext(world);Baritone baritone=new Baritone();
  probe.getField("src").set(move,new Pos(0,134,0));probe.getField("dest").set(move,new Pos(1,4,0));probe.getField("ctx").set(move,ctx);probe.getField("baritone").set(move,baritone);
  var invoke=probe.getMethod("update");
  check(invoke.invoke(move).toString().equals("UNREACHABLE"),"packaged gate returns unreachable for fatal drop");
  check(probe.getField("bodyEntered").getInt(move)==0&&baritone.inputs.cleared==1,"reject before any native break/input body; clear held keys");
  probe.getField("dest").set(move,new Pos(1,134,0));
  check(invoke.invoke(move).toString().equals("RUNNING")&&probe.getField("bodyEntered").getInt(move)==1,"packaged outward bridge reaches unchanged native body");
  probe.getField("dest").set(move,new Pos(1,135,0));check(invoke.invoke(move).toString().equals("RUNNING"),"packaged ascent allowed");
  probe.getField("src").set(move,new Pos(0,7,0));ctx.e.y=7;probe.getField("dest").set(move,new Pos(1,4,0));
  check(invoke.invoke(move).toString().equals("RUNNING"),"packaged safe drop allowed");
  world.cells.clear();int before=probe.getField("bodyEntered").getInt(move);
  check(invoke.invoke(move).toString().equals("UNREACHABLE")&&probe.getField("bodyEntered").getInt(move)==before,"removed landing veto before native body");
  System.out.println("VerifySafeDescents: "+checks+" packaged hook/preservation checks passed; native interfaces and movement body substituted");
 }
}
