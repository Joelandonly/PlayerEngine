import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests the real JAR dispatcher/tokenizer, not Minecraft command handlers. */
public final class CommandDispatchTest {
    static final class Probe extends Command {
        int calls;
        Probe(String name) { super(name, "Dispatch-only probe", new ArgBase[0]); }
        protected void call(PlayerEngineController controller, ArgParser parser) { calls++; finish(); }
    }
    public static void main(String[] args) throws Exception {
        CommandExecutor executor = new CommandExecutor(new PlayerEngineController(null));
        List<Probe> probes = new ArrayList<>(); List<String> unavailable = new ArrayList<>();
        for (String row : Files.readAllLines(Path.of(args[0])).subList(1, Files.readAllLines(Path.of(args[0])).size())) {
            String[] fields = row.split("\t", -1);
            if (fields[0].equals("registered")) {
                Probe probe = new Probe(fields[1]); probes.add(probe); executor.registerNewCommand(probe);
            } else unavailable.add(fields[1]);
        }
        if (probes.size() != 48 || unavailable.size() != 8) throw new AssertionError("Registry inventory changed; review coverage");
        for (Probe probe : probes) {
            AtomicInteger completed = new AtomicInteger();
            executor.execute("@" + probe.getName(), completed::incrementAndGet, error -> { throw new AssertionError(error); });
            if (probe.calls != 1 || completed.get() != 1) throw new AssertionError(probe.getName());
        }
        for (String name : unavailable) {
            AtomicInteger errors = new AtomicInteger();
            executor.execute("@" + name, () -> { throw new AssertionError("Unavailable command completed: " + name); }, error -> errors.incrementAndGet());
            if (errors.get() != 1) throw new AssertionError("Not rejected: " + name);
        }
        if (!CommandExecutor.resolveName("drop").equals("give")) throw new AssertionError("drop alias");
        executor.execute("@drop", () -> {}, error -> { throw new AssertionError(error); });
        Probe give = probes.stream().filter(p -> p.getName().equals("give")).findFirst().orElseThrow();
        if (give.calls != 2) throw new AssertionError("drop did not dispatch to give");
        if (!ArgParser.splitLineIntoKeywords("build_structure 1 64 1 \"small house\" \"cobblestone cube\"")
                .equals(List.of("build_structure","1","64","1","\"small house\"","\"cobblestone cube\""))) {
            throw new AssertionError("Quoted argument tokenization");
        }
        System.out.println("CommandDispatchTest: 48 canonical lookups pass, 8 unregistered IDs rejected, drop alias and quoted tokenizer pass; actual game handlers NOT executed");
    }
}
