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
import adris.altoclef.TaskCatalogue;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;

public class BuildStructureTask extends Task {
    private static final int maxNumErrors = 2;
    private static final Logger LOGGER = LogManager.getLogger();

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

    /**
     * Builds a structure using the same inventory, crafting, movement and placement
     * mechanics as the NPC's normal tasks. Never places blocks directly.
     */
    private class BuildFromCode extends Task {
        final String code;
        private Blueprint blueprint;
        private String error;
        private boolean finished;
        private boolean building;
        private int restarts;
        private int stalledTicks;
        private int gatheringTicks;
        private int previousMissing = Integer.MAX_VALUE;
        private int previousInventoryCount = -1;
        private int observedMissing = Integer.MAX_VALUE;
        private Item collectingItem;
        private Task collectingTask;

        BuildFromCode(String code) { this.code = code; }

        @Override protected boolean isEqual(Task other) {
            return other instanceof BuildFromCode && ((BuildFromCode) other).code.equals(code);
        }

        @Override protected void onStart() {
            if (blueprint != null || finished) return;
            try {
                Map<BlockPos, BlockState> requested = new HashMap<>();
                StructureFromCode.runCode(code, instruction -> {
                    BlockPos pos = new BlockPos(instruction.x, instruction.y, instruction.z);
                    if (!requested.containsKey(pos) && requested.size() >= 4096)
                        throw new IllegalArgumentException("Blueprint exceeds 4096 unique blocks");
                    ResourceLocation id = new ResourceLocation("minecraft", instruction.blockName);
                    if (!BuiltInRegistries.BLOCK.containsKey(id))
                        throw new IllegalArgumentException("Unknown block in generated plan: " + id);
                    requested.put(pos, BuiltInRegistries.BLOCK.get(id).defaultBlockState());
                }, mod);
                if (mod.isStopping) throw new IllegalStateException("Construction cancelled");
                blueprint = new Blueprint(requested);
                // Fail before the first build if the model specifies materials no NPC can collect.
                for (Map.Entry<Item, Integer> entry : requiredMaterials().entrySet()) {
                    if (!TaskCatalogue.taskExists(entry.getKey()))
                        throw new IllegalArgumentException("Cannot obtain " +
                                BuiltInRegistries.ITEM.getKey(entry.getKey()) +
                                " using current resource tasks. Generate a simpler survival build.");
                }
                LOGGER.info("Planning physical construction of {} blocks at {}", requested.size(), blueprint.origin);
            } catch (Exception e) {
                failBuild("Cannot prepare building: " + e.getMessage());
            }
        }

        private void failBuild(String reason) {
            error = reason;
            finished = true;
            if (building) {
                mod.getBaritone().getBuilderProcess().onLostControl();
                building = false;
            }
            LOGGER.warn("AI construction failed: {}", reason);
            mod.log("AI construction failed: " + reason);
        }

        /** Only count desired blocks which are not yet in the world. */
        private Map<Item, Integer> requiredMaterials() {
            Map<Item, Integer> required = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : blueprint.blocks.entrySet()) {
                BlockPos position = blueprint.origin.offset(entry.getKey());
                BlockState desired = entry.getValue();
                if (mod.getWorld().getBlockState(position).getBlock() == desired.getBlock()) continue;
                if (desired.isAir()) continue;
                Item item = desired.getBlock().asItem();
                if (item == Items.AIR || !(item instanceof BlockItem))
                    throw new IllegalArgumentException("Cannot physically place " + desired.getBlock());
                required.merge(item, 1, Integer::sum);
            }
            return required;
        }

        private int countMissing() {
            int count = 0;
            for (Map.Entry<BlockPos, BlockState> entry : blueprint.blocks.entrySet()) {
                BlockPos position = blueprint.origin.offset(entry.getKey());
                if (mod.getWorld().getBlockState(position).getBlock() != entry.getValue().getBlock()) count++;
            }
            return count;
        }

        private void stopBuilder() {
            if (building) {
                mod.getBaritone().getBuilderProcess().onLostControl();
                building = false;
            }
        }

        @Override protected Task onTick() {
            if (finished || blueprint == null) return null;
            try {
                if (building) {
                    var builder = mod.getBaritone().getBuilderProcess();
                    if (builder.isPaused() || !builder.isActive()) {
                        stopBuilder();
                        int stillMissing = countMissing();
                        if (stillMissing == 0) {
                            finished = true;
                            LOGGER.info("AI survival structure completed successfully");
                            mod.log("Finished physically constructing the requested building.");
                            return null;
                        }
                        if (++restarts > 3) {
                            failBuild("Builder stopped or paused repeatedly with " + stillMissing + " blocks incomplete.");
                            return null;
                        }
                        // Do not request another blueprint: reuse the original and its completed blocks.
                        setDebugState("Replanning remaining " + stillMissing + " blocks");
                        return null;
                    }
                    if (++stalledTicks % 20 == 0) {
                        int missing = countMissing();
                        if (missing < observedMissing) {
                            observedMissing = missing;
                            stalledTicks = 0;
                        }
                        if (missing == 0) {
                            stopBuilder();
                            finished = true;
                            mod.log("Finished physically constructing the requested building.");
                            return null;
                        }
                        if (stalledTicks > 2400) {
                            stopBuilder();
                            failBuild("No construction progress for 120 seconds of game ticks.");
                        }
                    }
                    return null;
                }

                Map<Item, Integer> required = requiredMaterials();
                if (required.isEmpty() && countMissing() == 0) {
                    finished = true;
                    mod.log("Building already matches the requested blueprint.");
                    return null;
                }

                // Gather and craft the TOTAL remaining requirement, not merely the
                // difference, because TaskCatalogue expects a final inventory target.
                for (Map.Entry<Item, Integer> entry : required.entrySet()) {
                    Item item = entry.getKey();
                    int totalNeeded = entry.getValue();
                    int owned = mod.getItemStorage().getItemCount(item);
                    if (owned >= totalNeeded) continue;
                    if (!TaskCatalogue.taskExists(item)) {
                        failBuild("No gathering or crafting task exists for " + BuiltInRegistries.ITEM.getKey(item));
                        return null;
                    }
                    if (!item.equals(collectingItem) || collectingTask == null) {
                        collectingItem = item;
                        collectingTask = TaskCatalogue.getItemTask(item, totalNeeded);
                        gatheringTicks = 0;
                        previousInventoryCount = owned;
                    }
                    if (collectingTask == null) {
                        failBuild("Cannot create resource task for " + BuiltInRegistries.ITEM.getKey(item));
                        return null;
                    }
                    if (owned > previousInventoryCount) {
                        previousInventoryCount = owned;
                        gatheringTicks = 0;
                    }
                    if (++gatheringTicks > 3600) {
                        failBuild("Timed out gathering " + BuiltInRegistries.ITEM.getKey(item) + ": " + owned + "/" + totalNeeded);
                        return null;
                    }
                    setDebugState("Gathering " + BuiltInRegistries.ITEM.getKey(item) + " " + owned + "/" + totalNeeded);
                    return collectingTask;
                }

                collectingTask = null;
                collectingItem = null;
                gatheringTicks = 0;
                int missing = countMissing();
                if (missing == 0) {
                    finished = true;
                    mod.log("Finished physically constructing the requested building.");
                    return null;
                }
                if (restarts > 3) {
                    failBuild("Construction could not finish remaining " + missing + " blocks");
                    return null;
                }
                mod.getBaritone().getBuilderProcess().build("AI survival structure", blueprint, blueprint.origin);
                building = true;
                observedMissing = missing;
                stalledTicks = 0;
                setDebugState("Physically placing " + missing + " blueprint blocks");
                return null;
            } catch (Exception e) {
                failBuild("Construction error: " + e.getMessage());
                return null;
            }
        }

        @Override protected void onStop(Task next) {
            // Never leave a background construction process building after cancellation.
            stopBuilder();
        }

        @Override public boolean isFinished() { return finished; }
        @Override protected String toDebugString() {
            return "Constructing survival building" + (error == null ? "" : " (failed: " + error + ")");
        }
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
        if (actuallyRunningTask == null && !isDone) actuallyRunningTask = new RequestLLMCode();
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
                        ++numErrors;
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