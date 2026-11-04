import java.util.*;import java.util.zip.*;import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Verify the packaged startup path cannot accidentally reinstate eager site selection. */
public final class VerifySetupStartup {
 public static void main(String[] args)throws Exception{
  ClassNode type=new ClassNode();try(ZipFile jar=new ZipFile(args[0])){
   var entry=Objects.requireNonNull(jar.getEntry("com/player2/playerengine/tasks/setup/EllieSetupTask.class"));
   new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(type,0);
  }
  int restore=0;
  for(MethodNode method:type.methods)if(method.name.equals("onStart"))for(AbstractInsnNode insn:method.instructions){
   if(insn instanceof MethodInsnNode call && call.owner.equals("com/player2/playerengine/tasks/setup/StarterBaseTask")){
    if(call.name.equals("prepare"))throw new AssertionError("Setup startup must not perform new site selection");
    if(call.name.equals("prepareExisting"))restore++;
   }
  }
  if(restore!=1)throw new AssertionError("Expected one restore-only startup handoff");
  for(String name:List.of("onStop","onTransientResumeAbandoned"))
   requireCall(type,name,"com/player2/playerengine/tasks/setup/StarterBaseTask","releaseProtection");
  ClassNode world=new ClassNode();try(ZipFile jar=new ZipFile(args[0])){
   new ClassReader(jar.getInputStream(jar.getEntry("com/player2/playerengine/tasks/setup/StarterBaseWorld.class")).readAllBytes()).accept(world,0);
  }
  requireCall(world,"acquire",type.name,"nativeAcquisition");
  ClassNode resource=new ClassNode();try(ZipFile jar=new ZipFile(args[0])){new ClassReader(jar.getInputStream(jar.getEntry("com/player2/playerengine/tasks/ResourceTask.class")).readAllBytes()).accept(resource,0);}
  for(String fieldName:List.of("mineIfPresent","mineLastClosest")){
   if(resource.fields.stream().noneMatch(f->f.name.equals(fieldName)))throw new AssertionError("Unknown native reclaim field "+fieldName);
   boolean bound=false;for(MethodNode method:type.methods)if(method.name.equals("nativeAcquisition"))for(AbstractInsnNode n:method.instructions)if(n instanceof LdcInsnNode ldc&&fieldName.equals(ldc.cst))bound=true;
   if(!bound)throw new AssertionError("Missing setup production policy for "+fieldName);
  }
  requireCall(type,"nativeAcquisition","com/player2/playerengine/nativecommands/NativeAccess","set");
  for(String name:List.of("loadExisting","loadOrSelect"))
   requireCall(world,name,"com/player2/playerengine/tasks/setup/StarterBaseWorld","protect");
  System.out.println("Packaged setup startup/lifecycle verified: restore-only handoff, protection on restored/new sites, root cleanup, native production policy");
 }
 static void requireCall(ClassNode type,String methodName,String owner,String name){
  for(MethodNode method:type.methods)if(method.name.equals(methodName))for(AbstractInsnNode insn:method.instructions)
   if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return;
  throw new AssertionError(type.name+"."+methodName+" must call "+name);
 }
}
