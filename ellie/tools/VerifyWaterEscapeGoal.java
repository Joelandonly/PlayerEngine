import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;

/** Check the packaged escape goal, unchanged public policy and the native cache ABI. */
public final class VerifyWaterEscapeGoal {
 static final String P="com/player2/playerengine/",G=P+"tasks/movement/GetOutOfWaterTask$EscapeFromWaterGoal",B=P+"automaton/utils/BlockStateInterface";
 static ClassNode read(ZipFile z,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(z.getInputStream(z.getEntry(name+".class")).readAllBytes()).accept(c,0);return c;}
 static MethodNode method(ClassNode c,String name){return c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static byte[] policy(MethodNode m){
  ClassWriter holder=new ClassWriter(0);holder.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"Policy",null,"java/lang/Object",null);m.accept(holder.visitMethod(m.access,m.name,m.desc,m.signature,null));holder.visitEnd();
  ClassWriter out=new ClassWriter(0);new ClassReader(holder.toByteArray()).accept(out,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return out.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  try(ZipFile built=new ZipFile(args[0]);ZipFile baseline=new ZipFile(args[1])){
   ClassNode current=read(built,G),original=read(baseline,G),bsi=read(built,B);
   for(String name:List.of("isInGoal","heuristic","isWaterAdjacent"))check(Arrays.equals(policy(method(current,name)),policy(method(original,name))),"native water goal policy changed: "+name);
   int loaded=0,cached=0,constructed=0;
   for(MethodNode m:current.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode c){
    check(!c.name.equals("method_8320"),"water goal still queries live world blocks");
    if(c.owner.equals(B)&&c.name.equals("isLoaded"))loaded++;
    if(c.owner.equals(B)&&c.name.equals("get0"))cached++;
    if(c.owner.equals(B)&&c.name.equals("<init>"))constructed++;
   }
   check(loaded==1&&cached==1&&constructed==1,"one loaded-cache boundary, constructed once per goal");
   check(bsi.methods.stream().anyMatch(m->m.name.equals("get0")&&m.desc.equals("(III)Lnet/minecraft/class_2680;")),"native BSI.get0 ABI");
   check(bsi.methods.stream().anyMatch(m->m.name.equals("isLoaded")&&m.desc.equals("(II)Z")),"native BSI.isLoaded ABI");
   for(String unchanged:List.of(P+"tasks/movement/GetOutOfWaterTask",P+"chains/UnstuckChain",P+"tasks/movement/CustomBaritoneGoalTask"))check(Arrays.equals(built.getInputStream(built.getEntry(unchanged+".class")).readAllBytes(),baseline.getInputStream(baseline.getEntry(unchanged+".class")).readAllBytes()),"native task/recovery orchestration changed: "+unchanged);
   System.out.println("VerifyWaterEscapeGoal: packaged non-loading boundary, native cache ABI, goal policy and recovery/task preservation verified");
  }
 }
}
