import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Register ONLY the explicit excavate command in the pinned PR28 binary. */
public final class PatchExcavationCommand implements Opcodes {
    private static final String P="com/player2/playerengine/";
    private static final String COMMANDS=P+"PlayerEngineCommands";
    private static final String ROUTE=P+"nativecommands/NativeCommandText";
    private static final String HANDLER=P+"commands/ExcavateAreaCommand";
    private static final String PARSER=P+"nativecommands/ExcavationCommandRoute";

    private static MethodNode find(ClassNode type,String name,String desc){
        MethodNode found=null;
        for(MethodNode m:type.methods)if(m.name.equals(name)&&m.desc.equals(desc)){
            if(found!=null)throw new IllegalStateException("Duplicate "+name);
            found=m;
        }
        if(found==null)throw new IllegalStateException("Missing pinned "+type.name+"."+name+desc);
        return found;
    }
    public static byte[] patchCommands(byte[] data){
        ClassNode c=new ClassNode();new ClassReader(data).accept(c,0);
        if(!COMMANDS.equals(c.name))throw new IllegalStateException("Unknown commands class "+c.name);
        MethodNode init=null;
        for(MethodNode m:c.methods)if(m.name.equals("init")&&m.desc.equals("()V"))init=m;
        if(init==null)throw new IllegalStateException("Pinned PlayerEngineCommands.init missing");
        int sites=0;
        for(AbstractInsnNode node:init.instructions.toArray()){
            if(node.getOpcode()!=RETURN)continue;
            InsnList a=new InsnList();
            a.add(new VarInsnNode(ALOAD,0));
            a.add(new MethodInsnNode(INVOKEVIRTUAL,P+"PlayerEngineController",
                "getCommandExecutor","()L"+P+"commands/base/CommandExecutor;",false));
            a.add(new InsnNode(ICONST_1));
            a.add(new TypeInsnNode(ANEWARRAY,P+"commands/base/Command"));
            a.add(new InsnNode(DUP));
            a.add(new InsnNode(ICONST_0));
            a.add(new TypeInsnNode(NEW,HANDLER));
            a.add(new InsnNode(DUP));
            a.add(new MethodInsnNode(INVOKESPECIAL,HANDLER,"<init>","()V",false));
            a.add(new InsnNode(AASTORE));
            a.add(new MethodInsnNode(INVOKEVIRTUAL,P+"commands/base/CommandExecutor",
                "registerNewCommand","([L"+P+"commands/base/Command;)V",false));
            init.instructions.insertBefore(node,a);
            sites++;
        }
        if(sites!=1)throw new IllegalStateException("Expected one init return; found "+sites);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
    public static byte[] patchText(byte[] data){
        ClassNode c=new ClassNode();new ClassReader(data).accept(c,0);
        if(!ROUTE.equals(c.name))throw new IllegalStateException("Unexpected command text "+c.name);
        MethodNode resolve=find(c,"resolve","(Ljava/lang/String;)Ljava/lang/String;");
        // Null from exact() delegates unchanged to the original resolver.
        LabelNode old=new LabelNode();
        InsnList a=new InsnList();
        a.add(new VarInsnNode(ALOAD,0));
        a.add(new MethodInsnNode(INVOKESTATIC,PARSER,"exact",
            "(Ljava/lang/String;)Ljava/lang/String;",false));
        a.add(new InsnNode(DUP));
        a.add(new JumpInsnNode(IFNULL,old));
        a.add(new InsnNode(ARETURN));
        a.add(old);
        a.add(new FrameNode(F_SAME1,0,null,1,new Object[]{"java/lang/String"}));
        a.add(new InsnNode(POP));
        resolve.instructions.insert(a);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
    public static void main(String[]args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("commands-in/out route-in/out");
        Files.write(Path.of(args[1]),patchCommands(Files.readAllBytes(Path.of(args[0]))));
        Files.write(Path.of(args[3]),patchText(Files.readAllBytes(Path.of(args[2]))));
    }
}
