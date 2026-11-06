import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Global chunk maintenance and diagnostics instrumentation. Fail closed if method shapes change. */
public final class PatchDistanceDiagnostics implements Opcodes {
 static final String P="com/player2/playerengine/", D=P+"observatory/DistanceDiagnostics";
 static ClassNode read(byte[] content){ClassNode n=new ClassNode();new ClassReader(content).accept(n,0);return n;}
 static byte[] fromJar(ZipFile z,String name)throws Exception{return z.getInputStream(Objects.requireNonNull(z.getEntry(name+".class"))).readAllBytes();}
 static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->new AssertionError("Missing "+n.name+"."+name+desc));}
 static void write(ClassNode n,Path out)throws Exception{ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);Path p=out.resolve(n.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
 static void hook(MethodNode m,String callee){InsnList x=new InsnList();x.add(new VarInsnNode(ALOAD,0));x.add(new MethodInsnNode(INVOKESTATIC,D,callee,"(Ljava/lang/Object;)V",false));m.instructions.insert(x);}
 public static void main(String[] args)throws Exception{
  Path existing=Path.of(args[1]);try(ZipFile z=new ZipFile(args[0])){
   String ctl=P+"PlayerEngineController";
   Path ctlPath=existing.resolve(ctl+".class");
   if(!Files.isRegularFile(ctlPath))throw new AssertionError("Controller overlay missing; patch order changed");
   ClassNode controller=read(Files.readAllBytes(ctlPath));
   hook(method(controller,"serverTick","()V"),"onControllerTick");
   hook(method(controller,"staticServerTick","(Lnet/minecraft/server/MinecraftServer;)V"),"onServerTick");

   String watchdog=P+"observatory/ChunkTicketWatchdog",nativeChunks=P+"util/ChunkController";
   ClassNode chunks=read(fromJar(z,nativeChunks));
   for(String name:List.of("load","unload")){
    MethodNode m=method(chunks,name,"(Lnet/minecraft/class_3218;Ljava/util/UUID;II)V");m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;
    m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new VarInsnNode(ALOAD,2));m.instructions.add(new VarInsnNode(ILOAD,3));m.instructions.add(new VarInsnNode(ILOAD,4));m.instructions.add(new MethodInsnNode(INVOKESTATIC,watchdog,name,"(Ljava/lang/Object;Ljava/util/UUID;II)V",false));m.instructions.add(new InsnNode(RETURN));
   }
   for(String name:List.of("clear","size")){MethodNode m=method(chunks,name,name.equals("clear")?"()V":"()I");m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;m.instructions.add(new MethodInsnNode(INVOKESTATIC,watchdog,name,m.desc,false));m.instructions.add(new InsnNode(name.equals("clear")?RETURN:IRETURN));}
   write(chunks,existing);
   ClassNode tracker=read(fromJar(z,P+"trackers/ChunkLoadingTracker"));
   MethodNode reset=method(tracker,"reset","()V");InsnList release=new InsnList();release.add(new VarInsnNode(ALOAD,0));release.add(new MethodInsnNode(INVOKESTATIC,watchdog,"reset","(Ljava/lang/Object;)V",false));reset.instructions.insert(release);write(tracker,existing);
   MethodNode stop=method(controller,"stop","(L"+P+"executor/StopReason;)V");InsnList stopHook=new InsnList();stopHook.add(new VarInsnNode(ALOAD,0));stopHook.add(new MethodInsnNode(INVOKESTATIC,watchdog,"stopped","(Ljava/lang/Object;)V",false));stop.instructions.insert(stopHook);
   MethodNode unregister=method(controller,"unregisterFromGlobalRegistry","(Ljava/util/UUID;)V");InsnList unregisterHook=new InsnList();unregisterHook.add(new VarInsnNode(ALOAD,0));unregisterHook.add(new MethodInsnNode(INVOKESTATIC,watchdog,"release","(Ljava/util/UUID;)V",false));unregister.instructions.insert(unregisterHook);
   write(controller,existing);
   ClassNode overlay=read(fromJar(z,P+"observatory/ObservatoryOverlay"));
   InsnList identity=new InsnList();identity.add(new MethodInsnNode(INVOKESTATIC,D,"announceBuild","()V",false));method(overlay,"onInitializeClient","()V").instructions.insert(identity);
   MethodNode render=method(overlay,"render","(Ljava/lang/Object;)V");
   InsnList stallHook=new InsnList();stallHook.add(new FieldInsnNode(GETSTATIC,overlay.name,"client","Ljava/lang/Object;"));stallHook.add(new MethodInsnNode(INVOKESTATIC,D,"clientTick","(Ljava/lang/Object;)V",false));method(overlay,"poll","()V").instructions.insert(stallHook);
   int hits=0;
   for(AbstractInsnNode i:render.instructions.toArray()) {
    if(i instanceof LdcInsnNode ldc && "Exact Baritone target and path nodes are NOT exposed".equals(ldc.cst)) {
     AbstractInsnNode preceding=i.getPrevious();
     if(preceding==null||preceding.getOpcode()!=ALOAD||((VarInsnNode)preceding).var!=11)throw new AssertionError("Navigation append anchor changed");
     InsnList injected=new InsnList();injected.add(new VarInsnNode(ALOAD,11));injected.add(new VarInsnNode(ALOAD,10));injected.add(new MethodInsnNode(INVOKESTATIC,D,"appendNavigationLines","(Ljava/util/List;Ljava/util/Map;)V",false));
     render.instructions.insertBefore(preceding,injected);hits++;
    }
   }
   if(hits!=1)throw new AssertionError("Navigation hook count "+hits);
   write(overlay,existing);
   System.out.println("Distance diagnostics: independent server tick + NPC controller tick + Navigation overlay; world-scoped native chunk ownership and independent maintenance");
  }
 }
}
