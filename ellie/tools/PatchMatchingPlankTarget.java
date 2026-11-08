import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Restrict the plank-count correction to the native shortfall -> ItemTarget
 * handoff. Refuse a changed class/method shape instead of silently patching
 * the wrong task. The helper is tested separately.
 */
public final class PatchMatchingPlankTarget implements Opcodes {
    static final String OWNER="com/player2/playerengine/tasks/resources/CraftWithMatchingMaterialsTask";
    static final String TARGET="com/player2/playerengine/util/ItemTarget";
    static final String HELPER="com/player2/playerengine/reliability/PlankTargetReconciliation";

    public static byte[] patch(byte[] original) {
        ClassNode type=new ClassNode();
        new ClassReader(original).accept(type,0);
        if (!OWNER.equals(type.name)) throw new IllegalArgumentException("Unexpected "+type.name);
        MethodNode method=null;
        for (MethodNode m:type.methods)
            if (m.name.equals("getAllSameResourcesTask") &&
                m.desc.equals("(Lcom/player2/playerengine/PlayerEngineController;)Lcom/player2/playerengine/tasks/base/Task;"))
                method=m;
        if (method==null) throw new IllegalStateException("Missing matching-materials source method");

        int matches=0;
        for (AbstractInsnNode ins:method.instructions.toArray()) {
            if (!(ins instanceof MethodInsnNode ctor) || ins.getOpcode()!=INVOKESPECIAL
                || !ctor.owner.equals(TARGET) || !ctor.name.equals("<init>")
                || !ctor.desc.equals("(L"+TARGET+";I)V")) continue;
            AbstractInsnNode count=ins.getPrevious();
            while (count!=null && count.getOpcode()<0) count=count.getPrevious();
            if (!(count instanceof VarInsnNode local) || count.getOpcode()!=ILOAD || local.var!=3)
                throw new IllegalStateException("Native shortfall instruction changed");
            InsnList replacement=new InsnList();
            replacement.add(new VarInsnNode(ALOAD,1)); // live controller
            replacement.add(new VarInsnNode(ALOAD,0)); // matching resource task
            replacement.add(new FieldInsnNode(GETFIELD,OWNER,"sameResourceTarget","L"+TARGET+";"));
            replacement.add(new VarInsnNode(ILOAD,3)); // the computed missing planks
            replacement.add(new MethodInsnNode(INVOKESTATIC,HELPER,"absoluteTarget",
                "(Ljava/lang/Object;Ljava/lang/Object;I)I",false));
            method.instructions.insertBefore(count,replacement);
            method.instructions.remove(count);
            matches++;
        }
        if (matches!=1) throw new IllegalStateException("Expected one native plank shortfall target; got "+matches);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }
    public static void main(String[]args)throws Exception {
        if (args.length!=2) throw new IllegalArgumentException("input output");
        Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));
    }
}
