import java.nio.file.*;import java.util.*;import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;import jdk.internal.org.objectweb.asm.tree.*;
import ellie.verification.NativeMethodShape;
/** Verify the packaged native hooks, and undo just those hooks to prove original method bodies. */
public final class VerifySchematicProjects implements Opcodes {
 static final String P="com/player2/playerengine/",H=P+"schematic/SchematicBuildSupport";
 static ClassNode read(ZipFile jar,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(name+".class")).readAllBytes()).accept(c,0);return c;}
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 public static void main(String[]a)throws Exception{
  try(ZipFile built=new ZipFile(a[0]);ZipFile original=new ZipFile(a[1])){
   ClassNode b=read(built,P+"automaton/process/BuilderProcess"),o=read(original,b.name);int hooks=0;
   for(MethodNode m:b.methods){
    if(m.name.equals("valid")){
     AbstractInsnNode n=m.instructions.getFirst();while(n!=null&&!(n instanceof MethodInsnNode c&&c.owner.equals(H)&&c.name.equals("valid")))n=n.getNext();check(n!=null,"state-validation gate missing");
     AbstractInsnNode end=n;while(end!=null&&end.getOpcode()!=POP)end=end.getNext();check(end!=null,"legacy state-validation fallthrough missing");
     AbstractInsnNode start=m.instructions.getFirst(),after=end.getNext();while(start!=after){AbstractInsnNode next=start.getNext();m.instructions.remove(start);start=next;}hooks++;
    }
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode c&&c.owner.equals(H)&&c.name.equals("predicted")){
     check(c.getPrevious() instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==0,"prediction must bind owning builder");check(c.getNext() instanceof TypeInsnNode t&&t.getOpcode()==CHECKCAST,"prediction result cast missing");m.instructions.remove(c.getPrevious());m.instructions.remove(c.getNext());c.setOpcode(INVOKEVIRTUAL);c.owner="net/minecraft/class_2248";c.name="method_9605";c.desc="(Lnet/minecraft/class_1750;)Lnet/minecraft/class_2680;";c.itf=false;hooks++;
    }
   }
   check(hooks==3&&NativeMethodShape.equivalent(b,o),"unreviewed native builder body changed");
   b=read(built,P+"mixins/MixinBlockPlaceContext");o=read(original,b.name);int added=0;
   for(MethodNode m:new ArrayList<>(b.methods))if(m.name.equals("method_8042")||m.name.equals("method_8044")){
    check(m.instructions.size()<=4,"placement context override must remain one helper delegation");check(Arrays.stream(m.instructions.toArray()).anyMatch(n->n instanceof MethodInsnNode c&&c.owner.equals(H)),"context helper missing");b.methods.remove(m);added++;
   }check(added==2&&NativeMethodShape.equivalent(b,o),"original placement context injections changed");
   b=read(built,P+"PlayerEngineCommands");Set<String> registered=new HashSet<>();for(MethodNode m:b.methods)for(AbstractInsnNode n:m.instructions)if(n instanceof TypeInsnNode t&&t.getOpcode()==NEW)registered.add(t.desc);
   for(String name:List.of("PrepareSchematicCommand","BuildSchematicCommand"))check(registered.contains(P+"commands/"+name),"new command not registered");
   check(built.stream().noneMatch(e->e.getName().startsWith("org/slf4j/")||e.getName().startsWith("net/minecraft/")),"test game/logger boundary leaked");
   System.out.println("Schematic packaged hooks verified: 3 scoped builder hooks, 2 context overrides, commands registered; all original builder bodies/context injections preserved");
  }
 }
}
