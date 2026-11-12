package com.player2.playerengine.nativecommands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.construction.WaterFillTask;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Exact owner-authenticated route; the model and other players cannot launch terrain edits. */
public final class WaterFillNativeCommand {
    private WaterFillNativeCommand() { }

    public static String execute(PlayerEngineController controller, UUID authenticatedOwner, String input) {
        if (controller == null || authenticatedOwner == null
                || !(controller.getOwner() instanceof ServerPlayer owner)
                || !authenticatedOwner.equals(owner.getUUID()))
            throw new IllegalArgumentException("Waterfill requires an authenticated owner");
        if (controller.getWorld() == null || owner.level() != controller.getWorld())
            throw new IllegalArgumentException("Owner and Ellie must be in the same dimension");
        String[] t = input.trim().split("\\s+");
        if (t.length < 4 || t.length > 5 || !t[0].equals("waterfill") || !t[1].equalsIgnoreCase("here"))
            return "Use: waterfill here <horizontal_radius> <vertical_radius> [auto|cobblestone|cobbled_deepslate|dirt|stone|oak_planks]";
        int radius = number(t[2]);
        int height = number(t[3]);
        if (radius < 0 || radius > 16 || height < 0 || height > 8)
            throw new IllegalArgumentException("Radius must be 0..16, vertical radius 0..8");
        long volume = (2L*radius+1L)*(2L*radius+1L)*(2L*height+1L);
        if (volume > 8192L) throw new IllegalArgumentException("Region would scan " + volume + " cells, limit 8192. Choose smaller radii.");
        BlockPos center = owner.blockPosition().immutable();
        if (center.getY() - height < controller.getWorld().getMinBuildHeight()
                || center.getY() + height >= controller.getWorld().getMaxBuildHeight())
            throw new IllegalArgumentException("Selected region extends outside the dimension build height");
        String blockName = t.length == 5 ? t[4] : "auto";
        Block block = inventoryBlock(blockName, controller);
        if (block == null)
            throw new IllegalArgumentException("No permitted solid replacement block in Ellie's carried inventory. Provide cobblestone, cobbled_deepslate, dirt, stone, or oak_planks.");
        WaterFillTask task = new WaterFillTask(authenticatedOwner, center, radius, height, block);
        ProjectTaskGateway.run(controller, task, () -> {
            String summary = task.isSuccessful() ? "DONE: " : "PARTIAL/FAILED: ";
            String result = "waterfill " + summary + task.controlledReason();
            try {
                controller.reportAgenticProgress(result, true);
                com.player2.playerengine.observatory.ObservatoryTelemetry.event("WATERFILL_RESULT", result);
            } catch (RuntimeException | LinkageError ignored) {
                controller.log(result);
            }
        });
        return "Waterfill started at " + center.toShortString() + " r=" + radius + " h=" + height
                + " material=" + block.getDescriptionId() + ". Report will distinguish verified placements from unreachable/protected sources.";
    }

    private static int number(String text) {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Not an integer radius: " + text); }
    }

    private static Block inventoryBlock(String raw, PlayerEngineController controller) {
        String name = raw.toLowerCase(Locale.ROOT).trim();
        if (name.startsWith("minecraft:")) name = name.substring(10);
        Block[] blocks = {Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.DIRT, Blocks.STONE, Blocks.OAK_PLANKS};
        String[] ids = {"cobblestone", "cobbled_deepslate", "dirt", "stone", "oak_planks"};
        for (int i = 0; i < blocks.length; i++) {
            if ((name.equals("auto") || ids[i].equals(name))
                    && controller.getItemStorage().getItemCountInventoryOnly(blocks[i].asItem()) > 0)
                return blocks[i];
        }
        return null;
    }
}
