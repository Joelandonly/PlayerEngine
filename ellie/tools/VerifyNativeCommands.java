import java.util.*;import java.util.zip.*;import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Check packaged entry points, not source strings, for removed model dependencies. */
public final class VerifyNativeCommands {
 static final String P="com/player2/playerengine/";
 static ClassNode read(ZipFile z,String n)throws Exception{ClassNode c=new ClassNode();new ClassReader(z.getInputStream(Objects.requireNonNull(z.getEntry(P+n+".class"))).readAllBytes()).accept(c,0);return c;}
 static void noModel(ClassNode c,Set<String> methods){
  for(MethodNode m:c.methods)if(methods.isEmpty()||methods.contains(m.name))for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode x){
   if(x.owner.contains("player2api/")&&!x.owner.endsWith("SetupDirectRoute")||x.owner.endsWith("AgenticToolContextBuilder"))throw new AssertionError(c.name+"."+m.name+" retains model/context dependency: "+x.owner+"."+x.name);
  }
 }
 public static void main(String[] a)throws Exception{try(ZipFile z=new ZipFile(a[0])){
  noModel(read(z,"agentic/AgenticPlannerService"),Set.of());
  noModel(read(z,"tasks/construction/build_structure/BuildStructureTask"),Set.of("<init>","onStart","onTick","onStop"));
  noModel(read(z,"tasks/agentic/LabelChestTask"),Set.of("tryModelLabel"));
  noModel(read(z,"agentic/elliegps/WaypointIngestionService"),Set.of("schedulePolish","schedulePolishAfterCommit"));
  var root=read(z,"tasks/construction/build_structure/BuildStructureTask");if(!root.interfaces.contains(P+"tasks/base/TrackedTaskOutcomeProvider"))throw new AssertionError("Build outcome must be verified");
  String build=P+"tasks/construction/build_structure/BuildStructureTask";
  var child=read(z,"tasks/construction/build_structure/BuildStructureTask$BuildFromCode");
  if(child.methods.stream().noneMatch(m->m.name.equals("<init>")&&m.desc.equals("(L"+build+";L"+build+";Ljava/lang/String;)V"))||!root.nestMembers.contains(build+"$BuildFromCode"))throw new AssertionError("Physical child constructor/nest contract changed");
  if(z.getEntry(P+"commands/random/DummyTaskCommand.class")!=null||z.getEntry(P+"commands/random/DummyTaskCommand$DummyTask.class")!=null)throw new AssertionError("Permanent no-op command was shipped");
  for(String helper:List.of("NativeBuildSupport","NativeFood","NativeCommandText"))noModel(read(z,"nativecommands/"+helper),Set.of());
  System.out.println("Packaged native command entry points verified: local plans/builds/labels/GPS, typed build outcome, no dummy task");
 }}
}
