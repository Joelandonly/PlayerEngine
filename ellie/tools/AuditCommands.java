import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Inventory metadata and registration from the actual preview JAR, without loading game classes. */
public final class AuditCommands implements Opcodes {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("jar output.tsv");
        Map<String, ClassNode> classes = new TreeMap<>();
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class") || !entry.getName().startsWith("com/player2/playerengine/")) continue;
                ClassNode type = new ClassNode();
                new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                classes.put(type.name, type);
            }
        }
        Set<String> registered = new TreeSet<>();
        ClassNode registry = Objects.requireNonNull(classes.get("com/player2/playerengine/PlayerEngineCommands"));
        for (MethodNode method : registry.methods) if (method.name.equals("init")) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof TypeInsnNode type && type.getOpcode() == NEW
                        && type.desc.contains("/commands/")) registered.add(type.desc);
            }
        }
        List<String> rows = new ArrayList<>(); Set<String> ids = new HashSet<>();
        int registeredCount = 0;
        for (ClassNode type : classes.values()) {
            if (!"com/player2/playerengine/commands/base/Command".equals(type.superName)) continue;
            List<String> literals = new ArrayList<>();
            for (MethodNode method : type.methods) if (method.name.equals("<init>")) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) literals.add(s);
                    if (insn instanceof MethodInsnNode call && call.owner.equals(type.superName) && call.name.equals("<init>")) break;
                }
                break;
            }
            if (literals.size() < 2) throw new IllegalStateException("Cannot read metadata: " + type.name);
            boolean active = registered.contains(type.name);
            if (active) { registeredCount++; if (!ids.add(literals.get(0))) throw new IllegalStateException("Duplicate registered command ID"); }
            rows.add((active ? "registered" : "unregistered") + "\t" + clean(literals.get(0)) + "\t" + type.name
                    + "\t" + clean(literals.get(1)) + "\t" + clean(String.join(" | ", literals.subList(2, literals.size()))));
        }
        if (registeredCount != registered.size()) throw new IllegalStateException("Unresolved registered command classes");
        Collections.sort(rows);
        Path out = Path.of(args[1]); Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, "registration\tname\tclass\tdescription\targument_literals\n" + String.join("\n", rows) + "\n");
        System.out.println("Command inventory: " + registeredCount + " registered, " + (rows.size()-registeredCount) + " unregistered command classes");
    }
    private static String clean(String s) { return s.replace('\t',' ').replace('\n',' ').replace('\r',' '); }
}
