package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.blockknowledge.BlockKnowledge;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.blockknowledge.LocateBlockTask;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/** Supports 'ellie locate block diamond_ore 64' without Player2 interpreting it. */
public final class LocateBlockCommand extends Command {
    public LocateBlockCommand() throws CommandException {
        super("locate_block", "locate_block <block> [radius=64]. Native server-side underground search of already loaded chunks, up to radius 96; returns exact coordinates, never generates chunks.",
              new Arg<>(String.class,"block"), new Arg<>(Integer.class,"radius",64,1));
    }
    @Override protected void call(PlayerEngineController mod, ArgParser args) throws CommandException {
        String name = args.get(String.class);
        int radius = args.get(Integer.class);
        if (radius < 1 || radius > LocateBlockTask.MAX_RADIUS) {
            finishWithError("Radius must be 1.." + LocateBlockTask.MAX_RADIUS + " to protect server tick time");
            return;
        }
        String id = name.contains(":") ? name.toLowerCase(java.util.Locale.ROOT) : "minecraft:" + name.toLowerCase(java.util.Locale.ROOT);
        ResourceLocation key = ResourceLocation.tryParse(id);
        Block block = key == null ? null : (Block)BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        if (block == null || id.equals("minecraft:air")) {
            finishWithError("Unknown/non-useful block ID " + name + ". Suggestions: " + String.join(", ", BlockKnowledge.suggestions(name)));
            return;
        }
        // Read cached positions only after checking the backing chunk is loaded.
        // The ordinary scanner's getNearestBlock() may consult stale/unloaded positions.
        BlockPos best = null;
        double distance = (double)radius * radius;
        for (BlockPos p : mod.getBlockScanner().getKnownLocationsBounded(512, block)) {
            if (!mod.getWorld().getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (mod.getWorld().getBlockState(p).getBlock() != block) continue;
            double dx = p.getX() - mod.getPlayer().getX();
            double dy = p.getY() - mod.getPlayer().getY();
            double dz = p.getZ() - mod.getPlayer().getZ();
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 <= distance) {distance = d2;best = p;}
        }
        if (best != null) {
            finishWithInfo("Located " + id + " at " + best.getX() + " " + best.getY() + " " + best.getZ() + " (verified loaded scanner cache)");
            return;
        }
        LocateBlockTask scan = new LocateBlockTask(block, id, radius);
        ProjectTaskGateway.run(mod, scan, () -> finishWithInfo(scan.result()));
    }
}
