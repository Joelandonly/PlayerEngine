import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Patch only the exact, existing SchematicProjectTask class.
 * It retains an ACQUIRE Action on approved transient suspension, creates
 * a fresh native leaf on resume, and leaves terminal cancellation unchanged.
 */
public final class PatchCombatAcquireResume implements Opcodes {
    static final String OWNER="com/player2/playerengine/schematic/SchematicProjectTask";
    static final String HELPER="com/player2/playerengine/schematic/SchematicAcquireResume";
    static final String TASK="com/player2/playerengine/tasks/base/Task";

    static MethodNode method(ClassNode cn,String name,String desc){
        MethodNode found=null;
        for(MethodNode m:cn.methods)if(m.name.equals(name)&&m.desc.equals(desc)){
            if(found!=null)throw new IllegalStateException("Duplicate "+name);
            found=m;
        }
        if(found==null)throw new IllegalStateException("Pinned method missing: "+name+desc);
        return found;
    }

    static void forget(MethodNode method){
        InsnList hook=new InsnList();
        hook.add(new VarInsnNode(ALOAD,0));
        hook.add(new MethodInsnNode(INVOKESTATIC,HELPER,"forget",
            "(L"+OWNER+";)V",false));
        method.instructions.insert(hook);
    }

    public static byte[] patch(byte[] source){
        ClassNode type=new ClassNode();
        new ClassReader(source).accept(type,0);
        if(!OWNER.equals(type.name))throw new IllegalStateException("Wrong class "+type.name);

        MethodNode suspension=method(type,"afterChildrenStopped","()V");
        // The old body resets action even when a HIGHER_PRIORITY_CHAIN only
        // stopped the native child. Retain only ACQUIRE on accepted suspension.
        LabelNode oldBehavior=new LabelNode();
        InsnList suspend=new InsnList();
        suspend.add(new VarInsnNode(ALOAD,0));
        suspend.add(new MethodInsnNode(INVOKESTATIC,HELPER,"hold",
            "(L"+OWNER+";)Z",false));
        suspend.add(new JumpInsnNode(IFEQ,oldBehavior));
        suspend.add(new InsnNode(RETURN));
        suspend.add(oldBehavior);
        suspend.add(new FrameNode(F_SAME,0,null,0,null));
        suspension.instructions.insert(suspend);

        MethodNode poll=method(type,"pollAction","()L"+TASK+";");
        // The native runner stopped the prior child. Recreate just that leaf
        // in the parent's onTick error boundary; never reuse a stopped Task.
        InsnList resume=new InsnList();
        resume.add(new VarInsnNode(ALOAD,0));
        resume.add(new MethodInsnNode(INVOKESTATIC,HELPER,"beforePoll",
            "(L"+OWNER+";)V",false));
        poll.instructions.insert(resume);

        // Explicit cancellation and abandoned resume must not hold an
        // abandoned session capable of resurrecting the old objective.
        forget(method(type,"onStop","(L"+TASK+";)V"));
        forget(method(type,"onTransientResumeAbandoned","()V"));
        forget(method(type,"block","(Ljava/lang/String;Ljava/lang/String;)L"+TASK+";"));
        forget(method(type,"success","(Ljava/lang/String;)L"+TASK+";"));

        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[]args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("input output");
        Files.write(Path.of(args[1]),patch(Files.readAllBytes(Path.of(args[0]))));
    }
}
