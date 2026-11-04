import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Redirect reviewed command submission sites; preserve native survival and idle paths. */
public final class PatchTaskRouting implements Opcodes {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("baseline patches-dir reviewed-commands compiled-dir");
        List<String> commands = Files.readAllLines(Path.of(args[2]));
        if (new HashSet<>(commands).size() != commands.size()) throw new IllegalStateException("Duplicate command");
        try (ZipFile jar = new ZipFile(args[0])) {
            for (String command : commands) {
                if (!command.matches("[A-Za-z]+Command")) throw new IllegalArgumentException("Invalid reviewed command");
                String entry = "com/player2/playerengine/commands/" + command + ".class";
                ClassNode type = new ClassNode();
                Path compiled=Path.of(args[3],entry);
                Path patched=Path.of(args[1],entry);
                if(Files.isRegularFile(patched))compiled=patched;
                new ClassReader(Files.isRegularFile(compiled)?Files.readAllBytes(compiled):jar.getInputStream(Objects.requireNonNull(jar.getEntry(entry), entry)).readAllBytes()).accept(type, 0);
                int count = 0;
                for (MethodNode method : type.methods) {
                    for (AbstractInsnNode insn : method.instructions) {
                        if(insn instanceof MethodInsnNode existing && existing.owner.equals("com/player2/playerengine/supervisor/ProjectTaskGateway") && existing.name.equals("run")) count++;
                        if (insn instanceof MethodInsnNode call
                                && call.owner.equals("com/player2/playerengine/PlayerEngineController")
                                && call.name.equals("runUserTask")) {
                            if (call.getOpcode() != INVOKEVIRTUAL || !call.desc.equals("(Lcom/player2/playerengine/tasks/base/Task;Ljava/lang/Runnable;)V")) {
                                throw new IllegalStateException("Unreviewed task submission signature: " + entry);
                            }
                            call.setOpcode(INVOKESTATIC);
                            call.owner = "com/player2/playerengine/supervisor/ProjectTaskGateway";
                            call.name = "run";
                            call.desc = "(Lcom/player2/playerengine/PlayerEngineController;Lcom/player2/playerengine/tasks/base/Task;Ljava/lang/Runnable;)V";
                            call.itf = false;
                            count++;
                        }
                    }
                }
                int expected = command.equals("EquipCommand") || command.equals("LocateStructureCommand") ? 2 : 1;
                if (count != expected) throw new IllegalStateException(entry + " expected " + expected + " call sites, got " + count);
                // Static descriptor explicitly includes the former receiver: same operand stack and frames.
                ClassWriter writer = new ClassWriter(0); type.accept(writer);
                Path output = Path.of(args[1], entry); Files.createDirectories(output.getParent());
                Files.write(output, writer.toByteArray());
                System.out.println("Supervised command: " + command + " (" + count + " call sites)");
            }
        }
    }
}
