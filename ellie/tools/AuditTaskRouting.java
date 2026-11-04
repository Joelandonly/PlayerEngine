import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;

/** Static call-site inventory; this is not proof that a command executed at runtime. */
public final class AuditTaskRouting {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("jar report-path");
        List<String> rows = new ArrayList<>();
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class") || !entry.getName().startsWith("com/player2/playerengine/")) continue;
                new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(new ClassVisitor(Opcodes.ASM8) {
                    String caller;
                    public void visitPermittedSubclass(String name) { /* hierarchy is irrelevant to calls */ }
                    public void visit(int v, int a, String n, String s, String p, String[] i) { caller = n; }
                    public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM8) {
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                String route = null;
                                if (owner.endsWith("/ProjectTaskGateway") && name.equals("run")) route = "supervised-tool";
                                else if (owner.endsWith("/PlayerEngineController") && name.equals("runUserTaskTracked")) route = "tracked";
                                else if (owner.endsWith("/CoreGoalSupervisor") && name.equals("submit")) route = "supervised";
                                else if (owner.endsWith("/PlayerEngineController") && name.equals("runUserTask")) route = "legacy-user-task";
                                else if (owner.endsWith("/UserTaskChain") && (name.equals("runTask") || name.equals("runTaskReplacing"))) route = "native-chain";
                                else if (owner.endsWith("/PlayerEngineController") && name.equals("runIdleUserTask")) route = "idle-policy";
                                if (route != null) rows.add(route + "\t" + caller + "#" + method + desc + "\t" + owner + "#" + name + descriptor);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        Collections.sort(rows);
        Path output = Path.of(args[1]); Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, "route\tcaller\ttarget\n" + String.join("\n", rows) + "\n");
        System.out.println("Task routing inventory: " + rows.size() + " static call sites -> " + output);
    }
}
