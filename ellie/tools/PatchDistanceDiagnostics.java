import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Read-only diagnostics instrumentation. Fail closed if method shapes change. */
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
   write(controller,existing);
   ClassNode overlay=read(fromJar(z,P+"observatory/ObservatoryOverlay"));
   MethodNode render=method(overlay,"render","(Ljava/lang/Object;)V");
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
   System.out.println("Distance diagnostics: independent server tick + NPC controller tick + Navigation overlay; no chunk/ticking changes");
  }
 }
}
