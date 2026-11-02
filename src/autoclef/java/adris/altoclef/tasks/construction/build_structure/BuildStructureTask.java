package adris.altoclef.tasks.construction.build_structure;

import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import baritone.api.schematic.ISchematic;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.datafixers.util.Either;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class BuildStructureTask extends Task {
    private static final int maxNumErrors = 2;
    private static Logger LOGGER = LogManager.getLogger();

    private boolean isDone = false;
    private String description;
    private AltoClefController mod;
    private Player2APIService service;
    private int numErrors;
    private Task actuallyRunningTask;
    private ConversationHistory history;
    private LLMCompleter completer;

    private class RequestLLMCode extends Task {
        // outer option: isDone, either: (left=code (success), right=errStr)
        Optional<Either<String, String>> llmResult = Optional.empty();

        @Override
        protected boolean isEqual(Task var1) {
            return var1 instanceof RequestLLMCode && ((RequestLLMCode) var1).llmResult == llmResult;
        }

        @Override
        protected void onStart() {
            // call LLM and either output err or code result.
            completer.processToString(service, history, codeResult -> {
                LOGGER.info("LLM generated code={}", codeResult);
                llmResult = Optional.of(Either.left(codeResult));
            }, errStr -> {
                LOGGER.info("LLM Transport Error={}", errStr);
                llmResult = Optional.of(Either.right(errStr));
            }, false);
        }

        @Override
        protected void onStop(Task var1) {

        }

        @Override
        protected Task onTick() {
            return null;
        }

        @Override
        protected String toDebugString() {
            return String.format("Thinking about how to build structure with description (%s)", description);
        }

        @Override
        public boolean isFinished() {
            return llmResult.isPresent();
        }
    }

    // A sparse blueprint preserves all blocks outside the LLM's explicit setBlock commands.
    // Construction itself is performed by Automatone using the NPC's inventory and movement.
    private static final class Blueprint implements ISchematic {
        final BlockPos origin;
        final int width, height, length;
        final Map<BlockPos, BlockState> blocks;

        Blueprint(Map<BlockPos, BlockState> worldBlocks) {
            if (worldBlocks.isEmpty()) throw new IllegalArgumentException("Generated structure contains no blocks");
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : worldBlocks.keySet()) {
                minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
            }
            this.origin = new BlockPos(minX, minY, minZ);
            this.width = maxX - minX + 1;
            this.height = maxY - minY + 1;
            this.length = maxZ - minZ + 1;
            if (width > 64 || height > 48 || length > 64 || (long) width * height * length > 131072L)
                throw new IllegalArgumentException("Generated building exceeds safe schematic dimensions");
            this.blocks = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : worldBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                this.blocks.put(new BlockPos(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ), entry.getValue());
            }
        }

        @Override public int widthX() { return width; }
        @Override public int heightY() { return height; }
        @Override public int lengthZ() { return length; }
        @Override public boolean inSchematic(int x, int y, int z, BlockState current) {
            return blocks.containsKey(new BlockPos(x, y, z));
        }
        @Override public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approximate) {
            return blocks.getOrDefault(new BlockPos(x, y, z), current);
        }
    }

    private class BuildFromCode extends Task {
        final String code;
        private boolean started;
        private boolean finished;
        private boolean builderWasActive;
        private String error;

        BuildFromCode(String code) { this.code = code; }

        @Override protected boolean isEqual(Task other) {
            return other instanceof BuildFromCode && ((BuildFromCode) other).code.equals(code);
        }

        @Override protected void onStart() {
            try {
                // Parse on the game thread; only the builder modifies the world.
                // Limiting output prevents enormous AI generated structures.
                Map<BlockPos, BlockState> requested = new HashMap<>();
                StructureFromCode.runCode(code, instruction -> {
                    if (requested.size() >= 4096)
                        throw new IllegalArgumentException("Building exceeds 4096 blocks");
                    ResourceLocation id = new ResourceLocation("minecraft", instruction.blockName);
                    if (!BuiltInRegistries.BLOCK.containsKey(id))
                        throw new IllegalArgumentException("Unknown block: " + id);
                    requested.put(new BlockPos(instruction.x, instruction.y, instruction.z),
                            BuiltInRegistries.BLOCK.get(id).defaultBlockState());
                }, mod);
                Blueprint blueprint = new Blueprint(requested);
                mod.getBaritone().getBuilderProcess().build("AI structure", blueprint, blueprint.origin);
                started = true;
                builderWasActive = true;
                LOGGER.info("Started survival construction of {} planned blocks", requested.size());
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                LOGGER.warn("Failed to start AI structure: {}", error);
                finished = true;
            }
        }

        @Override protected Task onTick() {
            if (started && !mod.getBaritone().getBuilderProcess().isActive()) {
                // Inactive does not necessarily imply every placement succeeded.
                finished = true;
            }
            return null;
        }

        @Override protected void onStop(Task next) {
            if (started && !finished) mod.getBaritone().getBuilderProcess().pause();
        }

        @Override public boolean isFinished() { return finished; }
        @Override protected String toDebugString() { return "Building AI structure via Automatone"; }
    }

    public BuildStructureTask(String description, AltoClefController mod) {
        this.description = description;
        this.mod = mod;
        this.service = mod.getPlayer2APIService();
        this.numErrors = 0;
        this.history = new ConversationHistory(Prompts.getBuildStructurePrompt());
        history.addUserMessage(
                String.format("Build with the following description: (%s)", description),
                service);
        this.completer = new LLMCompleter();
    }

    @Override
    protected void onStart() {
        actuallyRunningTask = new RequestLLMCode();
    }

    @Override
    protected Task onTick() {
        if (numErrors > maxNumErrors) {
            LOGGER.info("Too many errors, finishing.");
            // TODO: change to error from Task
            isDone = true;
            return null;
        }
        if (actuallyRunningTask == null || !actuallyRunningTask.isFinished()) {
            return actuallyRunningTask;
        }
        // ---------- now task is finished, switch to next task: -------

        if (actuallyRunningTask instanceof RequestLLMCode) {
            LOGGER.info("Requesting llm code for description={}", description);
            Either<String, String> result = ((RequestLLMCode) actuallyRunningTask).llmResult.get();
            // set actually running task to next task:
            result.mapBoth(
                    code -> {
                        LOGGER.info("LLM returned code={}", code);
                        actuallyRunningTask = new BuildFromCode(code);
                        return null;
                    }, errStr -> {
                        ++numErrors;
                        String tryAgainMessage = String.format(
                                "When trying to call the llm with the description, got this error: \n(%s)\n. Try again and generate code using the same description:\n(%s)",
                                errStr, description);
                        history.addUserMessage(tryAgainMessage, service);
                        LOGGER.info(tryAgainMessage);
                        actuallyRunningTask = new RequestLLMCode();
                        return null;
                    });
            return actuallyRunningTask;
        }
        if (actuallyRunningTask instanceof BuildFromCode) {
            Optional<String> result = Optional.ofNullable(((BuildFromCode) actuallyRunningTask).error);
            // set actually running task in both cases
            result.ifPresentOrElse(
                    errStr -> {
                        String code = ((BuildFromCode) actuallyRunningTask).code;
                        history.addAssistantMessage(code, service);
                        String tryAgainMessage = String.format(
                                "The code was executed, but got error \n(%s)\nTry again and generate code with the same description:\n(%s)",
                                errStr, description);
                        LOGGER.info(tryAgainMessage);
                        history.addUserMessage(tryAgainMessage, service);
                        actuallyRunningTask = new RequestLLMCode();
                    }, () -> {
                        isDone = true;
                        actuallyRunningTask = null;
                    });
            return actuallyRunningTask;
        }
        LOGGER.error("actually running task in buildStructureTask set to incorrect type");
        return null;
    }

    @Override
    public boolean isFinished() {
        return isDone;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (!(other instanceof BuildStructureTask))
            return false;
        BuildStructureTask o = (BuildStructureTask) other;
        return o.description.equals(this.description);
    }

    @Override
    protected void onStop(Task next) {
    }

    @Override
    protected String toDebugString() {
        return "BuildingStructure(" + description + ")";
    }
}