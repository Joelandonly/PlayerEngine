import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Proves the JAR itself contains the independent and per-NPC tick hooks. */
public class VerifyDistanceDiagnostics {
 static ClassNode get(ZipFile z,String path)throws Exception{
  ZipEntry e=z.getEntry(path+".class");if(e==null)throw new AssertionError("Missing "+path);
  ClassNode n=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(n,0);return n;
 }
 static int calls(ClassNode n,String name,String owner,String callee){
  int count=0;for(MethodNode m:n.methods)if(m.name.equals(name))for(AbstractInsnNode i:m.instructions){
   if(i instanceof MethodInsnNode min&&min.owner.equals(owner)&&min.name.equals(callee))count++;
  }return count;
 }
 static void check(boolean b,String desc){if(!b)throw new AssertionError(desc);System.out.println("PASS "+desc);}
 public static void main(String[] args)throws Exception{
  try(ZipFile z=new ZipFile(args[0])){
   String p="com/player2/playerengine/",d=p+"observatory/DistanceDiagnostics";
   ClassNode controller=get(z,p+"PlayerEngineController");
   ClassNode overlay=get(z,p+"observatory/ObservatoryOverlay");
   get(z,d);
   check(calls(controller,"staticServerTick",d,"onServerTick")==1,"global tick observer installed");
   check(calls(controller,"serverTick",d,"onControllerTick")==1,"controller heartbeat installed");
   check(calls(overlay,"render",d,"appendNavigationLines")==1,"Navigation HUD diagnostics installed");
   check(calls(get(z,p+"trackers/ChunkLoadingTracker"),"tick",p+"util/ChunkController","load")>=1,"existing chunk loader left in place");
   String w=p+"observatory/ChunkTicketWatchdog";
   check(calls(get(z,d),"onServerTick",d,"checkMissing")==1,"global callback reports missing registered controllers");
   check(calls(get(z,d),"onServerTick",w,"reconcile")==1,"independent global maintenance invokes reconciler");
   check(calls(get(z,p+"util/ChunkController"),"load",w,"load")==1,"native chunk acquisition uses world-scoped owner accounting");
   check(calls(get(z,p+"util/ChunkController"),"unload",w,"unload")==1,"native chunk release uses shared owner accounting");
   check(calls(get(z,p+"util/ChunkController"),"clear",w,"clear")==1,"native clear releases owned tickets");
   check(calls(get(z,p+"trackers/ChunkLoadingTracker"),"reset",w,"reset")==1,"tracker reset releases complete footprint");
   check(calls(controller,"stop",w,"stopped")==1,"controller stop immediately releases ticket ownership");
   check(calls(controller,"unregisterFromGlobalRegistry",w,"release")==1,"unregistration cleans owned tickets");
   check(calls(get(z,w),"reconcile",p+"PlayerEngineController","serverTick")==0,"ticket maintenance does not introduce another AI executor");
   System.out.println("VerifyDistanceDiagnostics: all packaged hooks verified");
  }
 }
}
