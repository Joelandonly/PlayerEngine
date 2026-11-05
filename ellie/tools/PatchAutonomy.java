import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Exact, audited hooks into the original task and command path; no new scheduler. */
public final class PatchAutonomy implements Opcodes {
 static final String P="com/player2/playerengine/", A=P+"agentic/StorageIntelligence", AI=P+"agentic/ResourceAgenticIntegration";
 static ClassNode read(ZipFile jar,String name)throws Exception{ClassNode n=new ClassNode();new ClassReader(jar.getInputStream(Objects.requireNonNull(jar.getEntry(name+".class"))).readAllBytes()).accept(n,0);return n;}
 static MethodNode one(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow(()->new AssertionError(n.name+"."+name));}
 static void write(ClassNode n,Path dir)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Path path=dir.resolve(n.name+".class");Files.createDirectories(path.getParent());Files.write(path,w.toByteArray());}
 static int afterObjectReturns(ClassNode n,String methodName,String owner,String name,String descriptor){
  MethodNode m=one(n,methodName);int count=0;
  for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()==ARETURN){InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,0));add.add(new MethodInsnNode(INVOKESTATIC,owner,name,descriptor,false));m.instructions.insertBefore(ins,add);count++;}return count;
 }
 public static void main(String[] args)throws Exception{
  try(ZipFile jar=new ZipFile(args[0])){
   Path out=Path.of(args[1]);
   ClassNode resource=read(jar,P+"tasks/ResourceTask");int returns=0;
   for(AbstractInsnNode ins:one(resource,"onTick").instructions.toArray())if(ins.getOpcode()==ARETURN){
    InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,0));add.add(new InsnNode(SWAP));add.add(new MethodInsnNode(INVOKESTATIC,A,"choose","(Ljava/lang/Object;L"+P+"tasks/base/Task;)L"+P+"tasks/base/Task;",false));one(resource,"onTick").instructions.insertBefore(ins,add);returns++;}
   if(returns<3)throw new AssertionError("ResourceTask.onTick returns changed: "+returns);resource.fields.add(new FieldNode(ACC_PRIVATE,"ellieStorageSession","Ljava/lang/Object;",null,null));write(resource,out);
   ClassNode scan=read(jar,P+"tasks/container/ScanContainerTask");int s=afterObjectReturns(scan,"onTick",A,"afterScan","(Ljava/lang/Object;)V");if(s<2)throw new AssertionError("scan tick changed");scan.fields.add(new FieldNode(ACC_PRIVATE,"ellieAutonomyReported","Z",null,null));write(scan,out);
   ClassNode transfer=read(jar,P+"tasks/container/BoundedContainerTransferTask");int t=afterObjectReturns(transfer,"onTick",A,"afterTransfer","(Ljava/lang/Object;)V");if(t<2)throw new AssertionError("transfer tick changed");transfer.fields.add(new FieldNode(ACC_PRIVATE,"ellieAutonomyReported","Z",null,null));write(transfer,out);
   ClassNode controller=read(jar,P+"PlayerEngineController");MethodNode tick=one(controller,"serverTick");InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,0));add.add(new MethodInsnNode(INVOKESTATIC,A,"heartbeat","(Ljava/lang/Object;)V",false));tick.instructions.insert(add);write(controller,out);
   ClassNode schemas=read(jar,P+"agentic/AgenticSchemas");MethodNode clinit=one(schemas,"<clinit>");int cl=0;
   for(AbstractInsnNode ins:clinit.instructions.toArray())if(ins.getOpcode()==RETURN){
    InsnList b=new InsnList();b.add(new FieldInsnNode(GETSTATIC,P+"agentic/AgenticSchemas","ALLOWED_STEP_KINDS","Ljava/util/Set;"));b.add(new MethodInsnNode(INVOKESTATIC,AI,"allowResourceKind","(Ljava/util/Set;)Ljava/util/Set;",false));b.add(new FieldInsnNode(PUTSTATIC,P+"agentic/AgenticSchemas","ALLOWED_STEP_KINDS","Ljava/util/Set;"));clinit.instructions.insertBefore(ins,b);cl++;}
   if(cl!=1)throw new AssertionError("AgenticSchemas init changed");write(schemas,out);
   ClassNode validator=read(jar,P+"agentic/AgenticPlanValidator");MethodNode validate=one(validator,"validateSequence");
   // A single new resource step is appended as a recognized comparison candidate.
   for(AbstractInsnNode ins:validate.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("java/util/List")&&call.name.equals("of")&&call.desc.equals("([Ljava/lang/Object;)Ljava/util/List;")){
    call.setOpcode(INVOKESTATIC);call.owner=AI;call.name="addResourceSequence";call.desc="([Ljava/lang/Object;)Ljava/util/List;";call.itf=false;}
   write(validator,out);
   ClassNode registry=read(jar,P+"agentic/AgenticStepFactoryRegistry");MethodNode ctor=one(registry,"<init>");int rg=0;
   for(AbstractInsnNode ins:ctor.instructions.toArray())if(ins.getOpcode()==RETURN){InsnList b=new InsnList();b.add(new VarInsnNode(ALOAD,0));b.add(new MethodInsnNode(INVOKESTATIC,AI,"registerFactory","(L"+P+"agentic/AgenticStepFactoryRegistry;)V",false));ctor.instructions.insertBefore(ins,b);rg++;}
   if(rg!=1)throw new AssertionError("Registry ctor changed");write(registry,out);
   System.out.println("Autonomy hooks: ResourceTask="+returns+" ScanContainerTask="+s+" Transfer="+t+" Controller.serverTick=1 Agentic=3");
  }
 }
}
