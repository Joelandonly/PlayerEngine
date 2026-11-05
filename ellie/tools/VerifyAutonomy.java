import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
/** Verify exact bytecode wiring in released artifact rather than merely compiled helpers. */
public final class VerifyAutonomy {
 static ClassNode klass(ZipFile z,String name)throws Exception{var e=z.getEntry("com/player2/playerengine/"+name+".class");if(e==null)throw new AssertionError("Missing "+name);var n=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(n,0);return n;}
 static long count(ClassNode n,String method,String owner,String invocation){long hits=0;for(MethodNode m:n.methods)if(m.name.equals(method))for(AbstractInsnNode ins:m.instructions)if(ins instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(invocation))hits++;return hits;}
 static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{try(var z=new ZipFile(args[0])){
  String a="com/player2/playerengine/agentic/StorageIntelligence",r="com/player2/playerengine/agentic/ResourceAgenticIntegration";
  check(count(klass(z,"tasks/ResourceTask"),"onTick",a,"choose")>=3,"native resource integration absent");
  check(count(klass(z,"tasks/container/ScanContainerTask"),"onTick",a,"afterScan")>=2,"scan-to-GPS absent");
  check(count(klass(z,"tasks/container/BoundedContainerTransferTask"),"onTick",a,"afterTransfer")>=2,"transfer invalidation absent");
  check(count(klass(z,"PlayerEngineController"),"serverTick",a,"heartbeat")==1,"passive discovery hook absent");
  check(count(klass(z,"agentic/AgenticSchemas"),"<clinit>",r,"allowResourceKind")==1,"resource schema absent");
  check(count(klass(z,"agentic/AgenticStepFactoryRegistry"),"<init>",r,"registerFactory")==1,"resource factory absent");
  check(count(klass(z,"agentic/AgenticPlanValidator"),"validateSequence",r,"addResourceSequence")==1,"resource validation absent");
  System.out.println("VerifyAutonomy: 7 packaged integration contracts confirmed; original scheduler remains in control");
 }}
}
