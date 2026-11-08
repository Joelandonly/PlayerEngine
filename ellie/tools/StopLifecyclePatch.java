import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Reproduce the verified v18.3 stop-loop fix against the packaged Task bytecode. */
public final class StopLifecyclePatch implements Opcodes {
    private static final String OWNER = "com/player2/playerengine/tasks/base/Task";
    private static final String FIELD = "ellieStopInProgress";

    private static MethodNode method(ClassNode cn, String name, String descriptor) {
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(descriptor)) return m;
        throw new IllegalArgumentException("Missing required method " + name + descriptor);
    }

    public static byte[] patch(byte[] original) {
        ClassNode cn = new ClassNode();
        new ClassReader(original).accept(cn, 0);
        if (!OWNER.equals(cn.name)) throw new IllegalArgumentException("Unexpected class " + cn.name);
        if (cn.fields.stream().anyMatch(f -> FIELD.equals(f.name)))
            throw new IllegalArgumentException("Task already patched; refusing duplicate instrumentation");
        MethodNode stop = method(cn, "stop", "(L" + OWNER + ";)V");
        MethodNode reset = method(cn, "reset", "()V");
        var exits = Arrays.stream(stop.instructions.toArray()).filter(i -> i.getOpcode() == RETURN).toList();
        if (exits.size() != 1 || !stop.tryCatchBlocks.isEmpty()
            || Arrays.stream(stop.instructions.toArray())
               .filter(i -> i instanceof MethodInsnNode)
               .map(i -> (MethodInsnNode)i)
               .noneMatch(i -> i.name.equals("onTransientResumeAbandoned")))
            throw new IllegalArgumentException("Unknown stop() layout; do not patch blind");

        cn.fields.add(new FieldNode(ACC_PRIVATE | ACC_SYNTHETIC, FIELD, "Z", null, null));
        LabelNode start = new LabelNode(), end = new LabelNode();
        LabelNode handler = new LabelNode(), proceed = new LabelNode();
        InsnList before = new InsnList();
        before.add(new VarInsnNode(ALOAD, 0));
        before.add(new FieldInsnNode(GETFIELD, OWNER, FIELD, "Z"));
        before.add(new JumpInsnNode(IFEQ, proceed));
        before.add(new InsnNode(RETURN));
        before.add(proceed);
        before.add(new VarInsnNode(ALOAD, 0));
        before.add(new InsnNode(ICONST_1));
        before.add(new FieldInsnNode(PUTFIELD, OWNER, FIELD, "Z"));
        before.add(start);
        stop.instructions.insert(before);

        InsnList exit = new InsnList();
        exit.add(new VarInsnNode(ALOAD, 0));
        exit.add(new InsnNode(ICONST_0));
        exit.add(new FieldInsnNode(PUTFIELD, OWNER, FIELD, "Z"));
        exit.add(end);
        stop.instructions.insertBefore(exits.get(0), exit);

        int thrown = stop.maxLocals;
        InsnList onError = new InsnList();
        onError.add(handler);
        onError.add(new VarInsnNode(ASTORE, thrown));
        onError.add(new VarInsnNode(ALOAD, 0));
        onError.add(new InsnNode(ICONST_0));
        onError.add(new FieldInsnNode(PUTFIELD, OWNER, FIELD, "Z"));
        onError.add(new VarInsnNode(ALOAD, thrown));
        onError.add(new InsnNode(ATHROW));
        stop.instructions.add(onError);
        stop.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));

        var resetExits = Arrays.stream(reset.instructions.toArray()).filter(i -> i.getOpcode() == RETURN).toList();
        if (resetExits.size() != 1) throw new IllegalArgumentException("Unknown reset() layout");
        InsnList resetGuard = new InsnList();
        resetGuard.add(new VarInsnNode(ALOAD, 0));
        resetGuard.add(new InsnNode(ICONST_0));
        resetGuard.add(new FieldInsnNode(PUTFIELD, OWNER, FIELD, "Z"));
        reset.instructions.insertBefore(resetExits.get(0), resetGuard);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) {
                return a.equals(b) ? a : "java/lang/Object";
            }
        };
        cn.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input Task.class output Task.class");
        Files.write(Path.of(args[1]), patch(Files.readAllBytes(Path.of(args[0]))));
    }
}
