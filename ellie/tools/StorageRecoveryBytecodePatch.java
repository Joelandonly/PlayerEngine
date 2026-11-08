import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Apply the two v18.3 storage reconciliation hooks to the packaged schematic coordinator. */
public final class StorageRecoveryBytecodePatch implements Opcodes {
    private static final String OWNER = "com/player2/playerengine/schematic/SchematicProjectTask";
    private static final String HELPER = "com/player2/playerengine/schematic/SchematicStorageReconciler";

    private static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("Missing " + name + desc);
    }

    public static byte[] patch(byte[] original) {
        ClassNode cn = new ClassNode();
        new ClassReader(original).accept(cn, 0);
        if (!OWNER.equals(cn.name)) throw new IllegalArgumentException("Wrong class");
        MethodNode block = method(cn, "block", "(Ljava/lang/String;Ljava/lang/String;)Lcom/player2/playerengine/tasks/base/Task;");
        AbstractInsnNode first = block.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (!(first instanceof VarInsnNode v) || first.getOpcode() != ALOAD || v.var != 0)
            throw new IllegalStateException("Unexpected block() entry");
        LabelNode original = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(ALOAD,0));
        guard.add(new VarInsnNode(ALOAD,1));
        guard.add(new VarInsnNode(ALOAD,2));
        guard.add(new MethodInsnNode(INVOKESTATIC, HELPER, "interceptBlock", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z", false));
        guard.add(new JumpInsnNode(IFEQ,original));
        guard.add(new InsnNode(ACONST_NULL));
        guard.add(new InsnNode(ARETURN));
        guard.add(original);
        guard.add(new FrameNode(F_SAME,0,null,0,null));
        block.instructions.insert(guard);

        MethodNode transfer = method(cn,"transfer","(Ljava/util/List;Ljava/lang/String;IZLjava/util/Map;)Lcom/player2/playerengine/tasks/base/Task;");
        MethodInsnNode begin = null;
        for (AbstractInsnNode ins : transfer.instructions.toArray()) {
            if (ins instanceof MethodInsnNode m && OWNER.equals(m.owner) && m.name.equals("begin")) {
                if (begin != null) throw new IllegalStateException("Multiple begin() sites");
                begin = m;
            }
        }
        if (begin == null) throw new IllegalStateException("transfer() begin() not found");
        // v18.3 keeps the Action in local slot 10. Reject changed layouts in verification.
        InsnList snapshot = new InsnList();
        snapshot.add(new VarInsnNode(ALOAD,0));
        snapshot.add(new VarInsnNode(ALOAD,10));
        snapshot.add(new VarInsnNode(ALOAD,1));
        snapshot.add(new MethodInsnNode(INVOKESTATIC,HELPER,"recordStart",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;)V",false));
        transfer.instructions.insertBefore(begin,snapshot);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input SchematicProjectTask.class output SchematicProjectTask.class");
        Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));
    }
}
