import java.nio.file.*;import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Preserve native physical-builder classes/nest metadata while removing model orchestration. */
public final class PatchNativeCommands implements Opcodes {
 static final String BASE="com/player2/playerengine/",BUILD=BASE+"tasks/construction/build_structure/BuildStructureTask",TASK=BASE+"tasks/base/Task",HELP=BASE+"nativecommands/NativeBuildSupport";
 static ClassNode read(ZipFile jar,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(Objects.requireNonNull(jar.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;}
 static MethodNode method(ClassNode c,String name,String desc){return c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->new IllegalStateException("Missing exact method "+c.name+"."+name+desc));}
 static InsnList reset(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;m.maxStack=8;return m.instructions;}
 static void save(ClassNode c,Path out)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
 static void delegate(MethodNode m,String name,String desc,int ret){InsnList i=reset(m);i.add(new VarInsnNode(ALOAD,0));i.add(new MethodInsnNode(INVOKESTATIC,HELP,name,desc,false));i.add(new InsnNode(ret));}
 public static void main(String[] args)throws Exception{try(ZipFile jar=new ZipFile(args[0])){Path out=Path.of(args[1]);
  ClassNode root=read(jar,BUILD);
  String ctor="(Lnet/minecraft/class_2338;Ljava/lang/String;Ljava/lang/String;L"+BASE+"PlayerEngineController;)V";
  InsnList i=reset(method(root,"<init>",ctor));i.add(new VarInsnNode(ALOAD,0));i.add(new MethodInsnNode(INVOKESPECIAL,TASK,"<init>","()V",false));
  String[] fields={"buildPosition","schematicQuery","description","mod"};String[] types={"Lnet/minecraft/class_2338;","Ljava/lang/String;","Ljava/lang/String;","L"+BASE+"PlayerEngineController;"};
  for(int n=0;n<4;n++){i.add(new VarInsnNode(ALOAD,0));i.add(new VarInsnNode(ALOAD,n+1));i.add(new FieldInsnNode(PUTFIELD,BUILD,fields[n],types[n]));}i.add(new InsnNode(RETURN));
  root.fields.add(new FieldNode(ACC_PRIVATE,"nativeFailure","Ljava/lang/String;",null,null));
  delegate(method(root,"onStart","()V"),"start","(Ljava/lang/Object;)V",RETURN);
  delegate(method(root,"onTick","()L"+TASK+";"),"tick","(Ljava/lang/Object;)L"+TASK+";",ARETURN);
  delegate(method(root,"onStop","(L"+TASK+";)V"),"stop","(Ljava/lang/Object;)V",RETURN);
  root.interfaces.add(BASE+"tasks/base/TrackedTaskOutcomeProvider");
  MethodNode terminal=new MethodNode(ACC_PUBLIC,"isTerminal","()Z",null,null);terminal.instructions.add(new VarInsnNode(ALOAD,0));terminal.instructions.add(new FieldInsnNode(GETFIELD,BUILD,"isDone","Z"));terminal.instructions.add(new InsnNode(IRETURN));root.methods.add(terminal);
  MethodNode success=new MethodNode(ACC_PUBLIC,"isSuccessful","()Z",null,null);delegate(success,"successful","(Ljava/lang/Object;)Z",IRETURN);root.methods.add(success);
  MethodNode reason=new MethodNode(ACC_PUBLIC,"controlledReason","()Ljava/lang/String;",null,null);delegate(reason,"reason","(Ljava/lang/Object;)Ljava/lang/String;",ARETURN);root.methods.add(reason);save(root,out);
  ClassNode label=read(jar,BASE+"tasks/agentic/LabelChestTask");reset(method(label,"tryModelLabel","([Ljava/lang/String;)[Ljava/lang/String;")).add(new InsnNode(ACONST_NULL));method(label,"tryModelLabel","([Ljava/lang/String;)[Ljava/lang/String;").instructions.add(new InsnNode(ARETURN));save(label,out);
  ClassNode ingestion=read(jar,BASE+"agentic/elliegps/WaypointIngestionService");
  for(MethodNode m:ingestion.methods)if(m.name.equals("schedulePolish")||m.name.equals("schedulePolishAfterCommit")){if(!m.desc.endsWith(")Z"))throw new IllegalStateException("Unexpected polish signature");InsnList b=reset(m);b.add(new InsnNode(ICONST_0));b.add(new InsnNode(IRETURN));}save(ingestion,out);
  for(String name:List.of("BuildStructureCommand","AgenticCommand")){
   ClassNode cmd=read(jar,BASE+"commands/"+name);MethodNode init=cmd.methods.stream().filter(m->m.name.equals("<init>")).findFirst().orElseThrow();int strings=0;
   for(AbstractInsnNode n:init.instructions)if(n instanceof LdcInsnNode ldc&&ldc.cst instanceof String){if(++strings==2){ldc.cst=name.equals("AgenticCommand")?"Run a local bounded pickup/storage plan. Supported goals: collect loose drops, find/prepare chest storage, deposit and label. No model planning.":"Build a supported local blueprint using real materials and verified physical layers. Templates: starter shelter, cabin, lodge, stone cottage, workshop, storage shed, greenhouse, 47x47 fortified village. x y z search_query description. Unsupported requests fail without AI.";break;}}
   save(cmd,out);
  }
  ClassNode scan=read(jar,BASE+"commands/random/ScanCommand");int normalizations=0;
  for(MethodNode m:scan.methods){
   if(m.name.equals("<init>"))for(AbstractInsnNode n:m.instructions)if(n instanceof LdcInsnNode ldc&&"DIRT".equals(ldc.cst))ldc.cst="dirt";
   if(m.name.equals("call"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(BASE+"commands/base/ArgParser")&&call.name.equals("get")){
    AbstractInsnNode cast=n.getNext();if(!(cast instanceof TypeInsnNode t)||!t.desc.equals("java/lang/String"))throw new IllegalStateException("Unexpected scan argument cast");
    InsnList fold=new InsnList();fold.add(new FieldInsnNode(GETSTATIC,"java/util/Locale","ROOT","Ljava/util/Locale;"));fold.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","toLowerCase","(Ljava/util/Locale;)Ljava/lang/String;",false));m.instructions.insert(cast,fold);normalizations++;
   }
  }
  if(normalizations!=1)throw new IllegalStateException("Expected one scan argument normalization");save(scan,out);
  ClassNode executor=read(jar,BASE+"commands/base/CommandExecutor");int splits=0;
  for(MethodNode m:executor.methods)if(m.name.equals("execute"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("java/lang/String")&&call.name.equals("split")){
   AbstractInsnNode prev=n.getPrevious();if(!(prev instanceof LdcInsnNode ldc)||!";".equals(ldc.cst))throw new IllegalStateException("Unreviewed command split");
   m.instructions.remove(prev);call.setOpcode(INVOKESTATIC);call.owner=BASE+"nativecommands/NativeCommandText";call.name="splitCommands";call.desc="(Ljava/lang/String;)[Ljava/lang/String;";splits++;
  }
  if(splits!=1)throw new IllegalStateException("Expected one command sequence split");save(executor,out);
  System.out.println("Native commands: physical builder, template labels, deterministic GPS descriptions; no model fallback");
 }}
}
