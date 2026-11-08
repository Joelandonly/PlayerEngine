import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Add a permanent project-storage break guard without changing normal child cleanup. */
public final class PatchSchematicDepotGuard implements Opcodes {
    static final String WORLD="com/player2/playerengine/schematic/SchematicWorld";
    static final String PROJECT="com/player2/playerengine/schematic/SchematicProjectTask";
    static final String GUARD="com/player2/playerengine/schematic/SchematicDepotGuard";
    static final String POS="com/player2/playerengine/schematic/SchematicPlan$Pos";
    static final String CONTROLLER="com/player2/playerengine/PlayerEngineController";

    private static MethodNode method(ClassNode cn,String name,String desc) {
        for(MethodNode m:cn.methods) if(m.name.equals(name)&&m.desc.equals(desc)) return m;
        throw new IllegalStateException(cn.name+" missing "+name+desc);
    }
    private static void beforeOnlyReturn(MethodNode method,InsnList addition) {
        AbstractInsnNode last=null;
        for(AbstractInsnNode node:method.instructions.toArray())
            if(node.getOpcode()==RETURN) {
                if(last!=null)throw new IllegalStateException("Unexpected multiple RETURN paths "+method.name);
                last=node;
            }
        if(last==null)throw new IllegalStateException("No RETURN in "+method.name);
        method.instructions.insertBefore(last,addition);
    }
    private static void worldFields(InsnList instructions) {
        instructions.add(new VarInsnNode(ALOAD,0));
        instructions.add(new FieldInsnNode(GETFIELD,WORLD,"controller","L"+CONTROLLER+";"));
        instructions.add(new VarInsnNode(ALOAD,0));
        instructions.add(new FieldInsnNode(GETFIELD,WORLD,"world","Ljava/lang/Object;"));
        instructions.add(new VarInsnNode(ALOAD,0));
        instructions.add(new FieldInsnNode(GETFIELD,WORLD,"extra","Ljava/lang/Object;"));
    }
    public static byte[] patchWorld(byte[] input) {
        ClassNode cn=new ClassNode();new ClassReader(input).accept(cn,0);
        if(!WORLD.equals(cn.name))throw new IllegalStateException("Wrong world class");
        MethodNode init=method(cn,"initialize","(Lcom/player2/playerengine/schematic/SchematicPlan;L"+POS+";)V");
        InsnList restore=new InsnList();
        worldFields(restore);
        restore.add(new VarInsnNode(ALOAD,0));
        restore.add(new FieldInsnNode(GETFIELD,WORLD,"positions","Ljava/util/List;"));
        restore.add(new MethodInsnNode(INVOKESTATIC,GUARD,"retainAll",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;)V",false));
        beforeOnlyReturn(init,restore);

        MethodNode protect=method(cn,"protectDepot","(L"+POS+";)V");
        InsnList register=new InsnList();
        worldFields(register);
        register.add(new VarInsnNode(ALOAD,1));
        register.add(new MethodInsnNode(INVOKESTATIC,GUARD,"retain",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;L"+POS+";)V",false));
        beforeOnlyReturn(protect,register);
        ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(output);
        return output.toByteArray();
    }
    public static byte[] patchProject(byte[] input) {
        ClassNode cn=new ClassNode();new ClassReader(input).accept(cn,0);
        if(!PROJECT.equals(cn.name))throw new IllegalStateException("Wrong schematic project class");
        MethodNode selected=method(cn,"reservedSource","(Ljava/lang/Object;Ljava/lang/Object;)Z");
        LabelNode original=new LabelNode();
        InsnList guard=new InsnList();
        guard.add(new VarInsnNode(ALOAD,0));
        guard.add(new VarInsnNode(ALOAD,1));
        guard.add(new MethodInsnNode(INVOKESTATIC,GUARD,"protects",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
        guard.add(new JumpInsnNode(IFEQ,original));
        guard.add(new InsnNode(ICONST_1));
        guard.add(new InsnNode(IRETURN));
        guard.add(original);
        guard.add(new FrameNode(F_SAME,0,null,0,null));
        selected.instructions.insert(guard);
        ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(output);
        return output.toByteArray();
    }
    public static void main(String[]args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("world-input world-output project-input project-output");
        Files.write(Path.of(args[1]),patchWorld(Files.readAllBytes(Path.of(args[0]))));
        Files.write(Path.of(args[3]),patchProject(Files.readAllBytes(Path.of(args[2]))));
    }
}
