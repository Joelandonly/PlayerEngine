package com.player2.playerengine.warehouse;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.safezone.SafeZoneService;
import com.player2.playerengine.tasks.construction.ConstructionTools;
import com.player2.playerengine.tasks.construction.GroundedFoundation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The exact terrain cells an authenticated warehouse plan authorizes. */
record WarehouseSite(List<BlockPos> supports, List<BlockPos> clearance, Set<ConstructionTools> tools) {
    static WarehouseSite inspect(PlayerEngineController mod, WarehouseLedger saved) throws IOException {
        String issue = WarehouseManager.unavailable(mod, saved);
        if (issue != null) throw new IOException(issue);

        int count = saved.number("requested", 4);
        boolean bunker = WarehouseManager.bunker(saved);
        var shell = WarehouseLayout.shell(count, bunker);
        Set<BlockPos> chests = new HashSet<>();
        for (var pair : WarehouseLayout.chests(count)) {
            chests.add(WarehouseManager.at(saved, pair.first()));
            chests.add(WarehouseManager.at(saved, pair.second()));
        }
        var supports = new ArrayList<BlockPos>();
        var foundationClearance = new ArrayList<BlockPos>();
        var clearance = new ArrayList<BlockPos>();
        var tools = EnumSet.noneOf(ConstructionTools.class);
        for (var cell : WarehouseLayout.floor(count, bunker)) {
            BlockPos floor = WarehouseManager.at(saved, cell);
            inspectSupport(mod, floor.below(), supports, foundationClearance, tools);
            for (int y = 3; y >= 0; y--) {
                BlockPos pos = floor.above(y);
                checkProtection(mod, pos);
                BlockState state = mod.getWorld().getBlockState(pos);
                boolean owned = "true".equals(saved.get("placed." + WarehouseManager.key(pos), "false"));
                var local = new WarehouseLayout.Cell(cell.x(), y, cell.z());
                if (owned && ((y == 0 || shell.contains(local)) && WarehouseManager.isStructure(saved, state)
                    || y == 1 && chests.contains(pos) && state.is(Blocks.CHEST))) continue;
                inspectClearance(mod, pos, state, clearance, tools);
            }
        }
        BlockPos entry = WarehouseManager.origin(saved).west();
        if (!bunker) {
            inspectSupport(mod, entry.below(), supports, foundationClearance, tools);
            for (BlockPos pos : List.of(entry.above(), entry)) {
                checkProtection(mod, pos);
                inspectClearance(mod, pos, mod.getWorld().getBlockState(pos), clearance, tools);
            }
        }
        clearance.addAll(foundationClearance);
        return new WarehouseSite(supports, clearance, tools);
    }

    private static void inspectSupport(PlayerEngineController mod, BlockPos pos,
                                       List<BlockPos> supports, List<BlockPos> clearance, Set<ConstructionTools> tools) throws IOException {
        var world = mod.getWorld();
        var column = GroundedFoundation.plan(pos, cell -> {
            if (!world.hasChunkAt(cell)) return GroundedFoundation.Cell.UNREADABLE;
            if (SafeZoneService.protectedAt(world, cell)) return GroundedFoundation.Cell.BLOCKED;
            BlockState state = world.getBlockState(cell);
            if (!world.getFluidState(cell).isEmpty()) return GroundedFoundation.Cell.BLOCKED;
            if (state.isAir()) return GroundedFoundation.Cell.DRY_AIR;
            if (solidSupport(mod, cell)) return GroundedFoundation.Cell.SOLID;
            if (canBreak(mod, cell, state)) {
                clearance.add(cell);
                requireTool(state, tools);
                return GroundedFoundation.Cell.DRY_AIR;
            }
            return GroundedFoundation.Cell.BLOCKED;
        });
        if (!column.ready()) {
            if (column.failure() == GroundedFoundation.Cell.DRY_AIR) {
                throw new IOException("No solid ground within " + GroundedFoundation.MAX_DEPTH + " blocks below foundation at " + pos);
            }
            BlockPos blocked = column.obstruction();
            checkProtection(mod, blocked);
            if (!world.getFluidState(blocked).isEmpty()) throw new IOException("Fluid in foundation at " + blocked.toShortString());
            BlockState state = world.getBlockState(blocked);
            throw new IOException("Unbreakable foundation obstruction " + state + " at " + blocked.toShortString());
        }
        supports.addAll(column.placements());
    }

    private static boolean solidSupport(PlayerEngineController mod, BlockPos pos) {
        var world = mod.getWorld();
        BlockState state = world.getBlockState(pos);
        return state.isSolidRender(world, pos) && world.getFluidState(pos).isEmpty() && world.getBlockEntity(pos) == null
            && !state.is(Blocks.MAGMA_BLOCK) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }

    private static void inspectClearance(PlayerEngineController mod, BlockPos pos, BlockState state,
                                         List<BlockPos> clearance, Set<ConstructionTools> tools) throws IOException {
        if (!mod.getWorld().getFluidState(pos).isEmpty()) throw new IOException("Fluid in warehouse footprint at " + pos.toShortString());
        if (state.isAir()) return;
        if (!canBreak(mod, pos, state)) throw new IOException("Unbreakable obstruction " + state + " at " + pos.toShortString());
        requireTool(state, tools);
        clearance.add(pos);
    }

    private static boolean canBreak(PlayerEngineController mod, BlockPos pos, BlockState state) {
        return !(state.getBlock() instanceof GameMasterBlock) && state.getDestroySpeed(mod.getWorld(), pos) >= 0;
    }

    private static void requireTool(BlockState state, Set<ConstructionTools> tools) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) tools.add(ConstructionTools.AXE);
        else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) tools.add(ConstructionTools.SHOVEL);
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) tools.add(ConstructionTools.PICKAXE);
    }

    private static void checkProtection(PlayerEngineController mod, BlockPos pos) throws IOException {
        if (!mod.getWorld().hasChunkAt(pos)) throw new IOException("Unloaded terrain at " + pos);
        if (SafeZoneService.protectedAt(mod.getWorld(), pos)) {
            throw new IOException("Safe Zone protection at " + pos.toShortString());
        }
    }

}
