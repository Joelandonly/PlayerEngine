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
  System.out.println("Packaged setup startup verified: restore-only handoff, no eager site selection");
 }
}
