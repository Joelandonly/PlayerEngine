import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Overlay one private Player2APIService method without changing the direct setup route. */
public final class PatchInterpreter implements Opcodes {
    private static final String OWNER = "com/player2/playerengine/player2api/Player2APIService";
    private static final String DESC = "(Lcom/google/gson/JsonObject;Lcom/player2/playerengine/player2api/AiTaskClass;)Ljava/util/Map;";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("baseline.jar patches-dir");
        byte[] old;
        try (ZipFile zip = new ZipFile(args[0])) {
            old = zip.getInputStream(zip.getEntry(OWNER+".class")).readAllBytes();
        }
        ClassNode node = new ClassNode();
        new ClassReader(old).accept(node, 0);
        int found=0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("sendChatCompletionRequest") && method.desc.equals(DESC)) {
                method.name="sendChatCompletionRequestViaPlayer2";
                found++;
            }
        }
        if(found!=1) throw new IllegalStateException("Expected exact existing sendChatCompletionRequest, got "+found);
        MethodNode wrapper = new MethodNode(ACC_PRIVATE, "sendChatCompletionRequest", DESC, "(Lcom/google/gson/JsonObject;Lcom/player2/playerengine/player2api/AiTaskClass;)Ljava/util/Map<Ljava/lang/String;Lcom/google/gson/JsonElement;>;", new String[]{"java/lang/Exception"});
        LabelNode fallback = new LabelNode();
        InsnList ins = wrapper.instructions;
        ins.add(new VarInsnNode(ALOAD,1));
        ins.add(new VarInsnNode(ALOAD,2));
        ins.add(new MethodInsnNode(INVOKESTATIC, "com/player2/playerengine/interpreter/LocalChatGPTBridge", "maybeComplete", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;", false));
        ins.add(new VarInsnNode(ASTORE,3));
        ins.add(new VarInsnNode(ALOAD,3));
        ins.add(new JumpInsnNode(IFNULL,fallback));
        ins.add(new VarInsnNode(ALOAD,3));
        ins.add(new InsnNode(ARETURN));
        ins.add(fallback);
        ins.add(new VarInsnNode(ALOAD,0));
        ins.add(new VarInsnNode(ALOAD,1));
        ins.add(new VarInsnNode(ALOAD,2));
        ins.add(new MethodInsnNode(INVOKESPECIAL,OWNER,"sendChatCompletionRequestViaPlayer2",DESC,false));
        ins.add(new InsnNode(ARETURN));
        wrapper.maxLocals=4;
        wrapper.maxStack=3;
        node.methods.add(wrapper);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String a,String b) { return "java/lang/Object"; }
        };
        node.accept(out);
        Path path = Path.of(args[1], OWNER+".class");
        Files.createDirectories(path.getParent());
        Files.write(path,out.toByteArray());
        System.out.println("Patched one private chat-completion method: "+old.length+" -> "+out.toByteArray().length);
    }
}
