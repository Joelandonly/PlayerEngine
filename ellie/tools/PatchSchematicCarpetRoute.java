import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Restrict white-carpet override to one pinned method in SchematicWorld.
 * This does not modify other recipe routes, the native executor, or combat.
 */
public final class PatchSchematicCarpetRoute implements Opcodes {
    private static final String WORLD="com/player2/playerengine/schematic/SchematicWorld";
    private static final String TASK="com/player2/playerengine/tasks/base/Task";
    private static final String ROUTE="com/player2/playerengine/schematic/SchematicWhiteCarpetAcquisition";

    public static byte[] patch(byte[] original){
        ClassNode cn=new ClassNode();
        new ClassReader(original).accept(cn,0);
        if(!WORLD.equals(cn.name))throw new IllegalStateException("Unexpected class: "+cn.name);
        MethodNode acquire=null;
        for(MethodNode m:cn.methods)
            if(m.name.equals("acquire") &&
               m.desc.equals("(Ljava/lang/String;I)L"+TASK+";"))acquire=m;
        if(acquire==null)throw new IllegalStateException("No pinned native SchematicWorld.acquire");
        for(AbstractInsnNode node:acquire.instructions.toArray())
            if(node instanceof TypeInsnNode t && ROUTE.equals(t.desc))
                throw new IllegalStateException("White carpet route already patched");
        LabelNode fallthrough=new LabelNode();
        InsnList head=new InsnList();
        head.add(new LdcInsnNode("minecraft:white_carpet"));
        head.add(new VarInsnNode(ALOAD,1));
        head.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
        head.add(new JumpInsnNode(IFEQ,fallthrough));
        head.add(new TypeInsnNode(NEW,ROUTE));
        head.add(new InsnNode(DUP));
        head.add(new VarInsnNode(ALOAD,0));
        head.add(new VarInsnNode(ILOAD,2));
        head.add(new MethodInsnNode(INVOKESPECIAL,ROUTE,"<init>","(L"+WORLD+";I)V",false));
        head.add(new InsnNode(ARETURN));
        head.add(fallthrough);
        head.add(new FrameNode(F_SAME,0,null,0,null));
        acquire.instructions.insert(head);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);
        return writer.toByteArray();
    }
    public static void main(String[]args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("input output");
        Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));
    }
}
