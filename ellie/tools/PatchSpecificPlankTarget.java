import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Fix the SECOND native plank handoff: an existing inventory containing 188
 * planks must not turn a request for one MORE plank into an absolute target 1.
 * Patch only the reviewed Item-specific native catalogue call.
 */
public final class PatchSpecificPlankTarget implements Opcodes {
    static final String OWNER="com/player2/playerengine/tasks/resources/CraftWithMatchingPlanksTask";
    static final String HELPER="com/player2/playerengine/reliability/PlankTargetReconciliation";

    public static byte[] patch(byte[] original) {
        ClassNode type=new ClassNode();
        new ClassReader(original).accept(type,0);
        if (!OWNER.equals(type.name)) throw new IllegalArgumentException("Unexpected "+type.name);
        MethodNode method=null;
        for (MethodNode m:type.methods)
            if (m.name.equals("getSpecificSameResourceTask")
                && m.desc.equals("(Lcom/player2/playerengine/PlayerEngineController;[Lnet/minecraft/class_1792;)Lcom/player2/playerengine/tasks/base/Task;"))
                method=m;
        if (method==null) throw new IllegalStateException("Missing native specific-plank method");

        int matches=0;
        for (AbstractInsnNode ins:method.instructions.toArray()) {
            if (!(ins instanceof MethodInsnNode call) || call.getOpcode()!=INVOKESTATIC
                || !call.owner.equals("com/player2/playerengine/TaskCatalogue")
                || !call.name.equals("getItemTask")
                || !call.desc.equals("(Lnet/minecraft/class_1792;I)Lcom/player2/playerengine/tasks/ResourceTask;"))
                continue;
            AbstractInsnNode before=call.getPrevious();
            while(before!=null&&before.getOpcode()<0)before=before.getPrevious();
            if(before==null||before.getOpcode()!=ICONST_1)
                throw new IllegalStateException("Unknown native specific-plank target layout");
            AbstractInsnNode itemLoad=before.getPrevious();
            while(itemLoad!=null&&itemLoad.getOpcode()<0)itemLoad=itemLoad.getPrevious();
            if(!(itemLoad instanceof VarInsnNode v) || v.getOpcode()!=ALOAD || v.var!=6)
                throw new IllegalStateException("Unknown specific plank item local");
            // Stack just before old ICONST_1 already holds selected Item.
            // Compute absolute target from a fresh count of that exact item.
            InsnList conversion=new InsnList();
            conversion.add(new VarInsnNode(ALOAD,1)); // controller
            conversion.add(new VarInsnNode(ALOAD,6)); // selected plank item
            conversion.add(new InsnNode(ICONST_1)); // one additional plank is enough
            conversion.add(new MethodInsnNode(INVOKESTATIC,HELPER,"absoluteTargetForItem",
                "(Ljava/lang/Object;Ljava/lang/Object;I)I",false));
            method.instructions.insertBefore(before,conversion);
            method.instructions.remove(before);
            matches++;
        }
        if(matches!=1)throw new IllegalStateException("Expected exactly one specific-plank catalogue call: "+matches);
        ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(output);
        return output.toByteArray();
    }
    public static void main(String[]args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("input output");
        Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));
    }
}
