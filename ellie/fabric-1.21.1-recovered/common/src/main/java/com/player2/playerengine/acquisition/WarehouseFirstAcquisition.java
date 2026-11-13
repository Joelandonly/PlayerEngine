package com.player2.playerengine.acquisition;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.warehouse.WarehouseWithdrawal;
import java.util.List;
import java.util.function.Supplier;

/** Verifies the original inventory target after drawing from storage and any native fallback. */
final class WarehouseFirstAcquisition extends Task implements TrackedTaskOutcomeProvider {
    private final ItemTarget target;
    private final Supplier<NativeAcquisitionPlanner.Decision> fallback;
    private final WarehouseWithdrawal warehouse = new WarehouseWithdrawal();
    private Task gathering;
    private String failure;

    WarehouseFirstAcquisition(ItemTarget target, Supplier<NativeAcquisitionPlanner.Decision> fallback) {
        this.target = target;
        this.fallback = fallback;
    }

    @Override protected void onStart() {}

    @Override
    protected Task onTick() {
        if (this.isTerminal()) return null;
        if (this.gathering == null) {
            Task stored = this.warehouse.next(this.controller, List.of(this.target));
            if (stored != null) {
                this.setDebugState("Withdrawing from Ellie's warehouse");
                return stored;
            }
            var decision = this.fallback.get();
            this.gathering = decision.task();
            if (this.gathering == null) this.failure = decision.reason();
        } else if (this.gathering.stopped() || this.gathering.isFinished()) {
            this.failure = this.gathering instanceof ResourceTask resource
                ? resource.getAggregatedFailureReason().orElse("Native acquisition ended before reaching the inventory target")
                : "Native acquisition ended before reaching the inventory target";
            return null;
        }
        return this.gathering;
    }

    @Override
    protected void onStop(Task next) {
        this.warehouse.close();
    }

    @Override public boolean isFinished() { return this.isTerminal(); }
    @Override public boolean isTerminal() { return this.failure != null || this.isSuccessful(); }
    @Override public boolean isSuccessful() {
        return this.controller != null
            && this.controller.getItemStorage().getItemCountInventoryOnly(this.target.getMatches()) >= this.target.getTargetCount();
    }
    @Override public String controlledReason() { return this.failure == null ? "Inventory target verified" : this.failure; }
    @Override protected boolean isEqual(Task other) { return this == other; }
    @Override protected String toDebugString() { return "Acquire " + this.target + " from warehouse or native sources"; }
}
