import java.nio.file.*;
import java.util.Objects;
import java.util.zip.ZipFile;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/**
 * Patch ONLY the working baseline's nearby-chat predicate, preserving the
 * original stop interception, blacklist checks, call-by-name selection,
 * duplicate handling and ordinary conversations.
 *
 * New predicate: nearby || (authenticated owner && explicit named command).
 * Exact commands still execute through the already-installed SetupDirectRoute.
 */
public final class PatchRemoteOwnerCommands implements Opcodes {
    private static final String BASE="com/player2/playerengine/player2api/";
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("baseline.jar patched-dir");
        String path=BASE+"manager/ConversationManager";
        byte[] bytes;
        try(ZipFile jar=new ZipFile(args[0])){
            bytes=jar.getInputStream(Objects.requireNonNull(jar.getEntry(path+".class"))).readAllBytes();
        }
        ClassNode cls=new ClassNode();
        new ClassReader(bytes).accept(cls,0);
        int changed=0;
        for(MethodNode method:cls.methods){
            if(!method.desc.equals("(L"+BASE+"Event$UserMessage;L"+BASE+"AgentConversationData;)Z"))continue;
            boolean proximity=false;
            for(AbstractInsnNode insn:method.instructions)
                if(insn instanceof MethodInsnNode call &&
                        call.owner.equals(BASE+"manager/ConversationManager") &&
                        call.name.equals("isCloseToPlayer"))proximity=true;
            if(!proximity)continue;
            for(AbstractInsnNode insn:method.instructions.toArray()) {
                if(insn.getOpcode()!=IRETURN)continue;
                InsnList overlay=new InsnList();
                // Existing boolean result of isCloseToPlayer is already on stack.
                overlay.add(new VarInsnNode(ALOAD,0));
                overlay.add(new VarInsnNode(ALOAD,1));
                overlay.add(new MethodInsnNode(INVOKESTATIC,BASE+"RemoteOwnerCommandGate",
                    "include","(ZL"+BASE+"Event$UserMessage;L"+BASE+"AgentConversationData;)Z",false));
                method.instructions.insertBefore(insn,overlay);
                changed++;
            }
        }
        if(changed!=1)throw new IllegalStateException("Expected exactly one proximity lambda return, got "+changed);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        Path out=Path.of(args[1],path+".class");
        Files.createDirectories(out.getParent());
        Files.write(out,writer.toByteArray());
        System.out.println("Remote owner commands: patched one authenticated-command chat filter, normal range preserved");
    }
}
