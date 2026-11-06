import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Assert shipped ConversationManager calls the real owner gate, not merely compiled helper. */
public final class VerifyRemoteOwnerCommands implements Opcodes {
 public static void main(String[] args)throws Exception{
  try(ZipFile jar=new ZipFile(args[0])){
   String path="com/player2/playerengine/player2api/manager/ConversationManager.class";
   ClassNode cls=new ClassNode();
   new ClassReader(jar.getInputStream(Objects.requireNonNull(jar.getEntry(path))).readAllBytes()).accept(cls,0);
   int patched=0,proximity=0,stop=0;
   for(MethodNode m:cls.methods){
    for(AbstractInsnNode n:m.instructions){
     if(!(n instanceof MethodInsnNode call))continue;
     if(m.desc.equals("(Lcom/player2/playerengine/player2api/Event$UserMessage;Lcom/player2/playerengine/player2api/AgentConversationData;)Z")){
      if(call.owner.endsWith("/RemoteOwnerCommandGate")&&call.name.equals("include"))patched++;
      if(call.name.equals("isCloseToPlayer"))proximity++;
     }
     if(m.name.equals("onUserChatMessage")&&call.name.equals("handleAuthenticatedOwnerStop"))stop++;
    }
   }
   if(patched!=1||proximity!=1||stop!=1)
    throw new AssertionError("Range patch missing or normal owner-stop policy changed: "+patched+","+proximity+","+stop);
   if(jar.getEntry("com/player2/playerengine/player2api/RemoteOwnerCommandGate.class")==null)
    throw new AssertionError("Remote policy not shipped");
   System.out.println("VerifyRemoteOwnerCommands: patched chat predicate, proximity and pre-existing owner-stop path intact");
  }
 }
}
