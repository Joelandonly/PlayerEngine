import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Add commands and scope native property/prediction fixes strictly to imported schematic layers. */
public final class PatchSchematicProjects implements Opcodes {
 static final String P="com/player2/playerengine/",H=P+"schematic/SchematicBuildSupport";
 static ClassNode read(ZipFile jar,Path out,String name)throws Exception {
  Path path=out.resolve(name+".class");ClassNode c=new ClassNode();new ClassReader(Files.exists(path)?Files.readAllBytes(path):jar.getInputStream(Objects.requireNonNull(jar.getEntry(name+".class"))).readAllBytes()).accept(c,0);return c;
 }
 static void write(ClassNode c,Path out)throws Exception {ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Path p=out.resolve(c.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());}
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[1]);try(ZipFile jar=new ZipFile(args[0])){
   ClassNode commands=read(jar,out,P+"PlayerEngineCommands");int registrations=0;
   for(MethodNode m:commands.methods)if(m.name.equals("init"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==RETURN){
    InsnList a=new InsnList();a.add(new VarInsnNode(ALOAD,0));a.add(new MethodInsnNode(INVOKEVIRTUAL,P+"PlayerEngineController","getCommandExecutor","()L"+P+"commands/base/CommandExecutor;",false));a.add(new InsnNode(ICONST_2));a.add(new TypeInsnNode(ANEWARRAY,P+"commands/base/Command"));
    int index=0;for(String command:List.of("PrepareSchematicCommand","BuildSchematicCommand")){a.add(new InsnNode(DUP));a.add(new InsnNode(index++==0?ICONST_0:ICONST_1));a.add(new TypeInsnNode(NEW,P+"commands/"+command));a.add(new InsnNode(DUP));a.add(new MethodInsnNode(INVOKESPECIAL,P+"commands/"+command,"<init>","()V",false));a.add(new InsnNode(AASTORE));}
    a.add(new MethodInsnNode(INVOKEVIRTUAL,P+"commands/base/CommandExecutor","registerNewCommand","([L"+P+"commands/base/Command;)V",false));m.instructions.insertBefore(n,a);registrations++;
   }
   if(registrations!=1)throw new AssertionError("Command registration count "+registrations);write(commands,out);
   ClassNode builder=read(jar,out,P+"automaton/process/BuilderProcess");int valid=0,predictions=0;
   for(MethodNode m:builder.methods){
    if(m.name.equals("valid")){
     LabelNode legacy=new LabelNode();InsnList a=new InsnList();for(int v=0;v<3;v++)a.add(new VarInsnNode(ALOAD,v));a.add(new MethodInsnNode(INVOKESTATIC,H,"valid","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)I",false));a.add(new InsnNode(DUP));a.add(new InsnNode(ICONST_M1));a.add(new JumpInsnNode(IF_ICMPEQ,legacy));a.add(new InsnNode(IRETURN));a.add(legacy);a.add(new FrameNode(F_SAME1,0,null,1,new Object[]{INTEGER}));a.add(new InsnNode(POP));m.instructions.insert(a);valid++;
    }
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/class_2248")&&call.name.equals("method_9605")){
     m.instructions.insertBefore(n,new VarInsnNode(ALOAD,0));call.setOpcode(INVOKESTATIC);call.owner=H;call.name="predicted";call.desc="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";call.itf=false;m.instructions.insert(call,new TypeInsnNode(CHECKCAST,"net/minecraft/class_2680"));predictions++;
    }
   }
   if(valid!=1||predictions!=2)throw new AssertionError("Builder boundaries "+valid+"/"+predictions);write(builder,out);
   // BlockPlaceContext inherits these two null-player fallbacks. Keep normal players and all
   // legacy NPCs unchanged; an imported builder supplies its NPC through the existing context.
   ClassNode mixin=read(jar,out,P+"mixins/MixinBlockPlaceContext");
   for(boolean rotation:new boolean[]{false,true}){
    MethodNode m=new MethodNode(ACC_PUBLIC,rotation?"method_8044":"method_8042",rotation?"()F":"()Lnet/minecraft/class_2350;",null,null);
    m.instructions.add(new VarInsnNode(ALOAD,0));m.instructions.add(new MethodInsnNode(INVOKESTATIC,H,rotation?"rotation":"horizontal",rotation?"(Ljava/lang/Object;)F":"(Ljava/lang/Object;)Ljava/lang/Object;",false));
    if(!rotation)m.instructions.add(new TypeInsnNode(CHECKCAST,"net/minecraft/class_2350"));m.instructions.add(new InsnNode(rotation?FRETURN:ARETURN));mixin.methods.add(m);
   }write(mixin,out);
  }
  System.out.println("Schematic projects: two supervised commands, scoped native state validation/prediction; existing placement context extended");
 }
}
