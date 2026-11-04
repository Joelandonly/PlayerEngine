/* Recovered from the pinned Ellie baseline with CFR 0.152; final completion verification hardened. */
package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.tasks.crafting.CraftMacroResourceTask;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.tasks.setup.CoreAcquisitionProgress;
import com.player2.playerengine.tasks.setup.CoreGoalJournal;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class EllieSetupTask
extends Task
implements TransientlyResumableTask,
TrackedTaskOutcomeProvider {
    private static final Step[] PLAN = new Step[]{new Step("bootstrap", "wooden_pickaxe", 1, true, 1800), new Step("bootstrap", "stone_pickaxe", 2, true, 3600), new Step("bootstrap", "stone_shovel", 1, true, 2400), new Step("bootstrap", "stone_axe", 1, true, 2400), new Step("workshop", "crafting_table", 1, true, 2400), new Step("workshop", "furnace", 1, true, 2400), new Step("storage reserve", "chest", 1, true, 2400), new Step("supplies", "torch", 32, true, 1800), new Step("equipment upgrade", "shield", 1, true, 2400), new Step("equipment upgrade", "bucket", 2, true, 3200), new Step("equipment upgrade", "iron_pickaxe", 1, true, 3000), new Step("equipment upgrade", "iron_shovel", 1, true, 1800), new Step("equipment upgrade", "iron_axe", 1, true, 1800), new Step("equipment upgrade", "iron_sword", 1, true, 1800), new Step("equipment upgrade", "iron_chestplate", 1, true, 3000), new Step("equipment upgrade", "iron_leggings", 1, true, 3000), new Step("equipment upgrade", "iron_helmet", 1, true, 1800), new Step("equipment upgrade", "iron_boots", 1, true, 1800), new Step("supplies", "bread", 16, true, 1600), new Step("supplies", "bed", 1, true, 1600)};
    private int index;
    private int lastCount;
    private int stagnant;
    private int allTicks;
    private int lastLogged = -1;
    private String lastProgressSignature = "";
    private final CoreAcquisitionProgress progressLedger = new CoreAcquisitionProgress();
    private CoreGoalJournal journal;
    private int objectiveWorkTicks;
    private boolean inventoryPressureReported;
    private int stepTicks;
    private int sampledProgressAt;
    private boolean furnaceAssist;
    private boolean furnaceRetryUsed;
    private static final int PROGRESS_POLL_TICKS = 20;
    private static final int FURNACE_COBBLESTONE_REQUIRED = 8;
    private String lastPhase = "";
    private Task active;
    private boolean completed;
    private int finalVerificationPasses;
    private StarterBaseLayout.Pos baseOrigin;
    private String baseDimension;
    private StarterBaseTask baseTask;
    private boolean baseAccepted;
    private boolean failed;
    private int optionalSkipped;
    private boolean startedOnce;
    private boolean pendingGestureResume;
    private boolean abandoned;
    private TaskSuspensionCause pendingResumeCause;
    private int defenseResumeCount;
    private int navSampleTicks;
    private int navStillTicks;
    private boolean navStallReported;
    private double navLastX = Double.NaN;
    private double navLastY;
    private double navLastZ;
    private int gestureResumeCount;
    private String failureReason = "";

    public static Step[] plannedSteps() {
        return (Step[])PLAN.clone();
    }

    protected void onStart() {
        if (!this.startedOnce) {
            this.startedOnce = true;
            try {
                this.baseOrigin = StarterBaseWorld.origin(this.controller);
                this.baseDimension = StarterBaseWorld.dimension(this.controller);
                this.baseTask = new StarterBaseTask(this.baseOrigin, this.baseDimension);
                this.baseTask.controller = this.controller;
                this.baseTask.prepareExisting();
            } catch (Exception | LinkageError error) {
                String detail = error instanceof IllegalStateException && error.getMessage() != null ? ": " + error.getMessage() : "";
                this.stopStep(new Step("startup", "setup_context", 1, true, 12000), "Cannot restore setup context: " + error.getClass().getSimpleName() + detail, true);
                return;
            }
            this.journal = this.openGoalJournal();
            String string = this.journal.get("status", "none");
            EllieSetupTask.event("SETUP_CHECKPOINT_RECOVERED", "Previous journal status=" + string + ", step=" + this.journal.get("step", "0") + "; all inventory and world requirements will be reverified");
            this.journal.put("RUNNING", this.index, EllieSetupTask.PLAN[0].resource, "Starter inventory plan active; journal is evidence, not item ownership");
            if (this.journal.error() != null) {
                EllieSetupTask.event("SETUP_JOURNAL_WARNING", this.journal.error());
            }
            EllieSetupTask.event("SETUP_START", "Starter setup: full required kit with a compact three-block workstation");
        } else if (this.pendingGestureResume) {
            this.pendingGestureResume = false;
            TaskSuspensionCause taskSuspensionCause = this.pendingResumeCause;
            this.pendingResumeCause = null;
            this.checkpoint("RUNNING", this.index < PLAN.length ? EllieSetupTask.PLAN[this.index].resource : "setup", "Resuming after temporary " + String.valueOf(taskSuspensionCause));
            if (taskSuspensionCause == TaskSuspensionCause.HIGHER_PRIORITY_CHAIN) {
                EllieSetupTask.event("SETUP_DEFENSE_RESUMED", "Resuming step " + (this.index + 1) + "/" + PLAN.length + " after higher-priority safety chain (resume #" + this.defenseResumeCount + ")");
            } else {
                EllieSetupTask.event("SETUP_GESTURE_RESUMED", "Continuing step " + (this.index + 1) + "/" + PLAN.length + " after temporary body-language gesture (resume #" + this.gestureResumeCount + ")");
            }
        }
    }

    protected Task onTick() {
        if (this.completed || this.failed || this.abandoned) {
            return null;
        }
        // Establish the workshop/storage after bootstrap and before iron upgrades.
        // Preparing the remembered plan on start also credits already placed stations
        // on rerun, instead of needlessly gathering another chest item.
        if (this.index >= 7 && this.baseTask != null && !this.baseAccepted) {
            if (!this.baseTask.isTerminal()) return this.baseTask;
            if (!this.baseTask.isSuccessful()) {
                this.stopStep(new Step("starter base", "base", 1, true, 12000), this.baseTask.controlledReason(), true);
                return null;
            }
            this.baseAccepted = true;
            this.checkpoint("RUNNING", "starter_base", "Workstations and one chest verified; continuing required kit");
        }
        ++this.allTicks;
        if (this.index >= PLAN.length) {
            if (this.baseTask != null && !this.baseTask.isTerminal()) return this.baseTask;
            if (this.baseTask != null && !this.baseTask.isSuccessful()) {
                this.stopStep(new Step("starter base", "base", 1, true, 12000), this.baseTask.controlledReason(), true);
                return null;
            }
            // A completed subtask is historical evidence, not proof that its output is
            // still owned after later crafting, tool breakage or interruptions.
            try {
                for (int i = 0; i < PLAN.length; i++) {
                    Step required = PLAN[i];
                    if (!required.required || required.phase.equals("bootstrap")) continue;
                    boolean satisfied = this.verifiedCount(required.resource) >= required.amount
                        || required.resource.equals("wooden_pickaxe") && this.hasAnyPickaxeBeyondWood()
                        || required.resource.equals("crafting_table") && this.hasVerifiedNearbyCraftingTable();
                    if (satisfied) continue;
                    if (++this.finalVerificationPasses > 2) {
                        this.stopStep(required, "Final verification could not retain required supplies after two repair passes", true);
                        return null;
                    }
                    this.index = i;
                    this.active = null;
                    this.objectiveWorkTicks = 0;
                    this.inventoryPressureReported = false;
                    this.resetProgress();
                    this.checkpoint("RUNNING", required.resource, "Final verification found missing required supplies; repairing pass " + this.finalVerificationPasses);
                    EllieSetupTask.event("SETUP_FINAL_RECHECK", "Required " + required.resource + " no longer verified; reacquiring before completion");
                    return null;
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                this.stopStep(PLAN[0], "Final inventory verification unavailable: " + error.getClass().getSimpleName(), true);
                return null;
            }
            if (this.baseTask == null) {
                if (this.baseOrigin == null || this.baseDimension == null) {
                    this.stopStep(PLAN[0], "No verified setup starting site", true);
                    return null;
                }
                this.baseTask = new StarterBaseTask(this.baseOrigin, this.baseDimension);
                this.baseTask.controller = this.controller;
                this.checkpoint("RUNNING", "starter_base", "Full kit verified; placing one table, one furnace and one chest");
                return this.baseTask;
            }
            this.completed = true;
            this.checkpoint("COMPLETED", "setup", "All required inventory and workstation objectives verified");
            String string = "Starter setup complete: full kit verified, crafting table and furnace placed, one chest verified. No shelter or farm is implied.";
            EllieSetupTask.event("SETUP_SUPPLIES_READY", string);
            this.report(string);
            return null;
        }
        Step step = PLAN[this.index];
        if (this.finalVerificationPasses > 0 && !step.required) {
            // Optional objectives have already had their first attempt. Repairs must
            // not consume supplies again or inflate the optional-skipped counter.
            ++this.index;
            return null;
        }
        if (this.active != null) {
            ++this.objectiveWorkTicks;
        }
        if (this.objectiveWorkTicks > step.stagnantLimit * 4) {
            this.stopStep(step, "Cumulative active work budget exhausted for " + step.resource + " after " + this.objectiveWorkTicks + " ticks (suspensions excluded)", step.required);
            return null;
        }
        if (!step.phase.equals(this.lastPhase)) {
            this.lastPhase = step.phase;
            EllieSetupTask.event("SETUP_PHASE", step.phase + " (step " + (this.index + 1) + "/" + PLAN.length + ")");
        }
        try {
            String string;
            int n = this.verifiedCount(step.resource);
            if ("crafting_table".equals(step.resource) && this.hasVerifiedNearbyCraftingTable()) {
                EllieSetupTask.event("SETUP_WORKSTATION_FOUND", "Accessible crafting table in world; second inventory crafting table not required");
                this.advance(step, 1, "existing accessible world workstation");
                return null;
            }
            if (n >= step.amount || step.resource.equals("wooden_pickaxe") && this.hasAnyPickaxeBeyondWood()
                    || this.betterBootstrapToolOwned(step)) {
                this.advance(step, n, this.active == null ? "already owned" : "acquired or replenished");
                return null;
            }
            if (this.furnaceAssist && this.countInventory("cobblestone") >= 8) {
                EllieSetupTask.event("SETUP_MATERIAL_READY", "furnace: cobblestone verified; retrying furnace macro");
                this.furnaceAssist = false;
                this.active = null;
                this.resetProgress();
                return null;
            }
            if (this.active == null) {
                String string2;
                if (!step.required && !this.furnaceAssist && this.journal != null && this.journal.optionalCooldown(step.resource, string2 = this.optionalMaterialFingerprint(), System.currentTimeMillis())) {
                    EllieSetupTask.event("SETUP_OPTIONAL_COOLDOWN", step.resource + ": earlier unavailable materials unchanged; retry after a short cooldown");
                    this.checkpoint("OPTIONAL_SKIPPED", step.resource, "cooldown; materials unchanged");
                    ++this.optionalSkipped;
                    ++this.index;
                    this.objectiveWorkTicks = 0;
                    this.inventoryPressureReported = false;
                    this.resetProgress();
                    return null;
                }
                if (!this.inventoryPressureReported && this.noEmptyInventorySlots()) {
                    this.inventoryPressureReported = true;
                    EllieSetupTask.event("SETUP_INVENTORY_PRESSURE", "Inventory has no free main slot at " + step.resource + "; continuing only if existing stacks can accept materials");
                }
                this.checkpoint("RUNNING", step.resource, "Acquisition started, current=" + n + "/" + step.amount);
                Task resolved = this.furnaceAssist ? this.resolve(new Step("workshop", "cobblestone", 8, true, step.stagnantLimit)) : this.resolve(step);
                if (resolved == null) {
                    this.stopStep(step, "No supported gather/craft task", step.required);
                    return null;
                }
                resolved.controller = this.controller;
                this.active = resolved;
                this.resetProgress();
                this.lastCount = n;
                EllieSetupTask.event("SETUP_OBJECTIVE", step.phase + ": acquire " + (this.furnaceAssist ? "cobblestone for furnace" : step.resource) + " " + (this.furnaceAssist ? this.countInventory("cobblestone") + "/8" : n + "/" + step.amount) + (step.required ? " [required]" : " [optional]"));
                return this.active;
            }
            if (this.furnaceAssist && this.countInventory("cobblestone") >= 8) {
                EllieSetupTask.event("SETUP_MATERIAL_READY", "furnace: 8+ cobblestone verified in inventory; retrying furnace once");
                this.furnaceAssist = false;
                this.active = null;
                this.resetProgress();
                return null;
            }
            if (n > this.lastCount) {
                this.lastCount = n;
                this.stagnant = 0;
                EllieSetupTask.event("SETUP_PROGRESS", step.resource + " " + n + "/" + step.amount);
            }
            if ((string = EllieSetupTask.acquisitionFailure(this.active)) != null) {
                if (!this.furnaceAssist && this.attemptFurnaceMaterialRecovery(step, string)) {
                    return this.active;
                }
                this.stopStep(step, (this.furnaceAssist ? "Cobblestone pre-gather failed: " : "Acquisition failed: ") + string, step.required);
                return null;
            }
            if (this.active.isFinished()) {
                if (this.furnaceAssist) {
                    this.stopStep(step, "Cobblestone pre-gather ended without 8 verified cobblestone", step.required);
                } else if (n >= step.amount) {
                    this.advance(step, n, "acquired");
                } else {
                    this.stopStep(step, "Gather/craft subtask finished without sufficient items (" + n + "/" + step.amount + ")", step.required);
                }
                return null;
            }
            if (this.active.stopped()) {
                this.stopStep(step, "Acquisition task stopped without success (" + n + "/" + step.amount + ")", step.required);
                return null;
            }
            ++this.stepTicks;
            this.observeNavigationStall();
            if (this.stepTicks - this.sampledProgressAt >= 20) {
                String string3;
                String string4;
                String string5;
                this.sampledProgressAt = this.stepTicks;
                String string6 = this.active instanceof CraftMacroResourceTask ? String.valueOf(((CraftMacroResourceTask)this.active).getPhase()) : "";
                CoreAcquisitionProgress.Sample sample = this.progressLedger.observe(string6, string5 = (string4 = this.progressSignature(this.active, step)).contains("|") ? string4.substring(string4.indexOf(124) + 1) : string4, string3 = EllieSetupTask.craftingTableKey(this.active), this.nearCraftingStation(this.active));
                if (sample.materialProgress() || sample.phaseMilestone()) {
                    if (!this.lastProgressSignature.isEmpty()) {
                        EllieSetupTask.event("SETUP_MATERIAL_PROGRESS", step.resource + " real material/stage milestone: " + string4);
                    }
                    this.lastProgressSignature = string4;
                    this.stagnant = 0;
                }
                if (sample.stationLoop()) {
                    EllieSetupTask.event("SETUP_CRAFT_STATION_LOOP", step.resource + ": repeated " + sample.stationTransitions() + " table-state transitions without inventory progress; station=" + string3 + "; phase=" + string6);
                    this.stopStep(step, "Crafting-table movement/aim loop with no verified item progress; station=" + string3 + ", phase=" + string6, step.required);
                    return null;
                }
            }
            ++this.stagnant;
            if (this.stagnant > step.stagnantLimit) {
                this.stopStep(step, "No material or crafting-phase progress for " + this.stagnant + " ticks; output=" + n + "/" + step.amount, step.required);
                return null;
            }
            if (this.objectiveWorkTicks > step.stagnantLimit * 4) {
                this.stopStep(step, "Total work limit reached (" + this.objectiveWorkTicks + " active ticks, including replans)", step.required);
                return null;
            }
            return this.active;
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            this.stopStep(step, "Inventory or task lookup error: " + exception.getClass().getSimpleName() + " " + String.valueOf(exception.getMessage()), step.required);
            return null;
        }
    }

    private static String acquisitionFailure(Task task) {
        Object object;
        if (task instanceof CraftMacroResourceTask && (object = ((CraftMacroResourceTask)task).getFailureReason()) != null && !((String)object).isBlank()) {
            return (String)object;
        }
        if (task.stopped() && task instanceof ResourceTask && (object = ((ResourceTask)task).getAggregatedFailureReason()) != null && ((Optional)object).isPresent() && !((String)((Optional)object).get()).isBlank()) {
            return (String)((Optional)object).get();
        }
        return null;
    }

    private String progressSignature(Task task, Step step) throws ReflectiveOperationException {
        String[] stringArray;
        String[] stringArray2;
        StringBuilder stringBuilder = new StringBuilder();
        if (task instanceof DescribesProgress) {
            stringBuilder.append(((DescribesProgress)task).describeProgress());
        } else {
            stringBuilder.append(task.getClass().getName());
        }
        if ("furnace".equals(step.resource)) {
            String[] stringArray3 = new String[3];
            stringArray3[0] = "cobblestone";
            stringArray3[1] = "cobbled_deepslate";
            stringArray2 = stringArray3;
            stringArray3[2] = "blackstone";
        } else {
            String resource = step.resource;
            if (resource.startsWith("iron_") || resource.equals("bucket") || resource.equals("shield")) {
                stringArray2 = new String[]{"raw_iron", "iron_ingot", "iron_nugget", "coal", "charcoal"};
            } else if (resource.equals("bread")) {
                stringArray2 = new String[]{"wheat"};
            } else if (resource.equals("torch")) {
                stringArray2 = new String[]{"coal", "charcoal", "stick"};
            } else {
                stringArray2 = new String[]{"oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log", "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks", "stick", "cobblestone", "white_wool"};
            }
        }
        for (String string : stringArray = stringArray2) {
            try {
                stringBuilder.append('|').append(string).append('=').append(this.countInventory(string));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return stringBuilder.toString();
    }

    private boolean attemptFurnaceMaterialRecovery(Step step, String string) throws ReflectiveOperationException {
        if (!"furnace".equals(step.resource) || this.furnaceRetryUsed) {
            return false;
        }
        this.furnaceRetryUsed = true;
        int n = this.countInventory("cobblestone");
        String string2 = this.active instanceof CraftMacroResourceTask ? " phase=" + String.valueOf(((CraftMacroResourceTask)this.active).getPhase()) + " macro=" + ((CraftMacroResourceTask)this.active).describeProgress() + " creationNote=" + ((CraftMacroResourceTask)this.active).getCreationNote() : " source=" + (this.active == null ? "none" : this.active.getClass().getSimpleName());
        EllieSetupTask.event("SETUP_FURNACE_DIAG", "macro failed: " + string + "; cobblestone=" + n + "/8; cobbled_deepslate=" + this.safeDiagnosticCount("cobbled_deepslate") + "; blackstone=" + this.safeDiagnosticCount("blackstone") + ";" + string2 + "; recipe will be retried at most once");
        if (n >= 8) {
            this.active = null;
            this.resetProgress();
            EllieSetupTask.event("SETUP_FURNACE_RETRY", "8+ cobblestone already held; retrying furnace macro once");
            return true;
        }
        Step step2 = new Step("workshop", "cobblestone", 8, true, step.stagnantLimit);
        Task task = this.resolve(step2);
        if (task == null) {
            return false;
        }
        task.controller = this.controller;
        this.furnaceAssist = true;
        this.active = task;
        this.resetProgress();
        EllieSetupTask.event("SETUP_FURNACE_ASSIST", "Using normal get-item acquisition for 8 cobblestone (currently " + n + ")");
        return true;
    }

    private int safeDiagnosticCount(String string) {
        try {
            return this.countInventory(string);
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return -1;
        }
    }

    private void resetProgress() {
        this.stagnant = 0;
        this.stepTicks = 0;
        this.sampledProgressAt = 0;
        this.lastProgressSignature = "";
        this.progressLedger.reset();
        this.lastCount = 0;
        this.lastLogged = -1;
        this.resetNavigationWatch();
    }

    private Task resolve(Step step) throws ReflectiveOperationException {
        Object object;
        Class<?> clazz = Class.forName("com.player2.playerengine.TaskCatalogue");
        Object object2 = null;
        try {
            object2 = clazz.getMethod("getItemTarget", String.class, Integer.TYPE).invoke(null, step.resource, step.amount);
        }
        catch (NoSuchMethodException noSuchMethodException) {
            // empty catch block
        }
        if (object2 != null) {
            Object object3;
            try {
                Object object4 = Class.forName("com.player2.playerengine.patch.ToolRequestRouting").getMethod("select", Object.class).invoke(null, object2);
                if (object4 instanceof Task) {
                    return (Task)object4;
                }
            }
            catch (ClassNotFoundException | NoSuchMethodException reflectiveOperationException) {
                // empty catch block
            }
            try {
                for (Method method : Class.forName("com.player2.playerengine.tasks.crafting.CraftMacroTasks").getMethods()) {
                    Object object5;
                    if (!method.getName().equals("macroOutcome") || method.getParameterCount() != 2 || !method.getParameterTypes()[0].isInstance(this.controller) || !method.getParameterTypes()[1].isInstance(object2)) continue;
                    object3 = method.invoke(null, this.controller, object2);
                    if (object3 != null && "MACRO_CREATED".equals(String.valueOf(object3.getClass().getMethod("outcome", new Class[0]).invoke(object3, new Object[0]))) && (object5 = object3.getClass().getMethod("task", new Class[0]).invoke(object3, new Object[0])) instanceof Task) {
                        return (Task)object5;
                    }
                    break;
                }
            }
            catch (ClassNotFoundException | NoSuchMethodException reflectiveOperationException) {
                // empty catch block
            }
            for (Method method : clazz.getMethods()) {
                if (!method.getName().equals("getItemTask") || method.getParameterCount() != 1 || !method.getParameterTypes()[0].isInstance(object2)) continue;
                object3 = method.invoke(null, object2);
                if (!(object3 instanceof Task)) break;
                return (Task)object3;
            }
        }
        return (object = clazz.getMethod("getItemTask", String.class, Integer.TYPE).invoke(null, step.resource, step.amount)) instanceof Task ? (Task)object : null;
    }

    private int verifiedCount(String resource) throws ReflectiveOperationException {
        int count = this.countInventory(resource);
        if (resource.startsWith("iron_")) {
            String suffix = resource.substring(5);
            count += this.countInventory("diamond_" + suffix) + this.countInventory("netherite_" + suffix);
        }
        if (this.baseTask != null && (resource.equals("chest") || resource.equals("crafting_table") || resource.equals("furnace"))) {
            count += this.baseTask.verifiedPlacedCount(resource);
        }
        return count;
    }

    private int countInventory(String string) throws ReflectiveOperationException {
        Class<?> clazz = Class.forName("com.player2.playerengine.TaskCatalogue");
        Object object = clazz.getMethod("getItemMatches", String.class).invoke(null, string);
        if (object == null || !object.getClass().isArray() || Array.getLength(object) == 0) {
            throw new IllegalArgumentException("No item matches for " + string);
        }
        ItemStorageTracker itemStorageTracker = this.controller.getItemStorage();
        for (Method method : itemStorageTracker.getClass().getMethods()) {
            if (!method.getName().equals("getItemCountInventoryOnly") || method.getParameterCount() != 1 || !method.getParameterTypes()[0].isAssignableFrom(object.getClass())) continue;
            return ((Number)method.invoke((Object)itemStorageTracker, object)).intValue();
        }
        throw new NoSuchMethodException("getItemCountInventoryOnly");
    }

    private boolean hasVerifiedNearbyCraftingTable() {
        try {
            Class<?> clazz = Class.forName("com.player2.playerengine.tasks.crafting.CraftingTableLocator");
            Object object = clazz.getMethod("findReusableNearby", PlayerEngineController.class, Double.TYPE).invoke(null, this.controller, 12.0);
            return object instanceof Optional && ((Optional)object).isPresent();
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private boolean hasAnyPickaxeBeyondWood() throws ReflectiveOperationException {
        for (String string : new String[]{"stone_pickaxe", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe"}) {
            try {
                if (this.countInventory(string) <= 0) continue;
                return true;
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return false;
    }

    private boolean betterBootstrapToolOwned(Step step) throws ReflectiveOperationException {
        if (!step.phase.equals("bootstrap") || !step.resource.startsWith("stone_")) return false;
        String suffix = step.resource.substring(6);
        for (String tier : new String[]{"iron_", "diamond_", "netherite_"}) {
            if (this.countInventory(tier + suffix) > 0) return true;
        }
        return false;
    }

    private void advance(Step step, int n, String string) {
        EllieSetupTask.event("SETUP_STEP_DONE", step.phase + ": " + step.resource + " " + n + "/" + step.amount + " (" + string + ")");
        this.checkpoint("CHECKPOINT", step.resource, "Verified " + n + "/" + step.amount + " (" + string + ")");
        ++this.index;
        this.active = null;
        this.furnaceAssist = false;
        this.furnaceRetryUsed = false;
        this.objectiveWorkTicks = 0;
        this.inventoryPressureReported = false;
        this.resetProgress();
    }

    private void stopStep(Step step, String string, boolean bl) {
        EllieSetupTask.event(bl ? "SETUP_BLOCKED" : "SETUP_OPTIONAL_SKIP", step.resource + ": " + string);
        this.active = null;
        this.furnaceAssist = false;
        this.resetProgress();
        if (bl) {
            this.checkpoint("BLOCKED", step.resource, string);
        } else {
            if (this.journal != null) {
                this.journal.noteOptionalFailure(step.resource, this.optionalMaterialFingerprint());
            }
            this.checkpoint("OPTIONAL_SKIPPED", step.resource, string);
        }
        if (bl) {
            this.failed = true;
            this.failureReason = "Setup blocked at " + step.resource + ": " + string;
            this.report(this.failureReason);
            this.fail(this.failureReason);
        } else {
            ++this.optionalSkipped;
            ++this.index;
            this.objectiveWorkTicks = 0;
            this.inventoryPressureReported = false;
        }
    }

    public boolean prepareForTransientResume(TaskSuspensionCause taskSuspensionCause) {
        if (taskSuspensionCause != TaskSuspensionCause.GESTURE_OVERLAY && taskSuspensionCause != TaskSuspensionCause.HIGHER_PRIORITY_CHAIN || this.completed || this.failed || this.abandoned) {
            return false;
        }
        this.pendingGestureResume = true;
        this.pendingResumeCause = taskSuspensionCause;
        this.checkpoint("SUSPENDED", this.index < PLAN.length ? EllieSetupTask.PLAN[this.index].resource : "setup", "Temporarily suspended: " + String.valueOf(taskSuspensionCause));
        if (taskSuspensionCause == TaskSuspensionCause.HIGHER_PRIORITY_CHAIN) {
            ++this.defenseResumeCount;
            EllieSetupTask.event("SETUP_DEFENSE_PAUSED", "Preserving setup step " + (this.index + 1) + "/" + PLAN.length + " for higher-priority safety chain; no task completion callback");
        } else {
            ++this.gestureResumeCount;
            EllieSetupTask.event("SETUP_GESTURE_PAUSED", "Temporarily pausing setup step " + (this.index + 1) + "/" + PLAN.length + " for gesture; completion callback remains pending");
        }
        return true;
    }

    public void afterChildrenStopped() {
        this.active = null;
        this.resetProgress();
        this.resetNavigationWatch();
    }

    public void onTransientResumeAbandoned() {
        if (this.baseTask != null) this.baseTask.releaseProtection();
        if (this.completed || this.failed || this.abandoned) {
            return;
        }
        this.abandoned = true;
        this.pendingGestureResume = false;
        this.pendingResumeCause = null;
        this.checkpoint("INTERRUPTED", this.index < PLAN.length ? EllieSetupTask.PLAN[this.index].resource : "setup", "Transient resume abandoned");
        EllieSetupTask.event("SETUP_INTERRUPTED", "Setup abandoned at step " + (this.index + 1) + "/" + PLAN.length + "; not complete. Run setup again to inspect and recover actual inventory");
    }

    protected void onStop(Task task) {
        if (this.baseTask != null) this.baseTask.releaseProtection();
        if (!(this.completed || this.failed || this.abandoned)) {
            this.abandoned = true;
            this.pendingGestureResume = false;
            this.pendingResumeCause = null;
            this.checkpoint("INTERRUPTED", this.index < PLAN.length ? EllieSetupTask.PLAN[this.index].resource : "setup", "Task stopped by chain");
            EllieSetupTask.event("SETUP_INTERRUPTED", "Setup stopped at step " + (this.index + 1) + "/" + PLAN.length + "; not complete. Run setup again to recover");
        }
    }

    private void observeNavigationStall() {
        block10: {
            boolean bl;
            if (this.stepTicks - this.navSampleTicks < 20) {
                return;
            }
            this.navSampleTicks = this.stepTicks;
            String string = this.getTaskTree();
            String string2 = string.toLowerCase(Locale.ROOT);
            boolean bl2 = bl = string2.contains("getting within") || string2.contains("approach entity") || string2.contains("going to entity") || string2.contains("walking to") || string2.contains("pathing to") || string2.contains("travelling to");
            if (!bl) {
                this.resetNavigationWatch();
                return;
            }
            try {
                Object object;
                Object object2 = this.controller.getClass().getMethod("getPlayer", new Class[0]).invoke((Object)this.controller, new Object[0]);
                double d = EllieSetupTask.readCoord(object2, "method_23317", "getX");
                double d2 = EllieSetupTask.readCoord(object2, "method_23318", "getY");
                double d3 = EllieSetupTask.readCoord(object2, "method_23321", "getZ");
                if (!(Double.isFinite(d) && Double.isFinite(d2) && Double.isFinite(d3))) {
                    return;
                }
                if (Double.isNaN(this.navLastX)) {
                    this.navLastX = d;
                    this.navLastY = d2;
                    this.navLastZ = d3;
                    return;
                }
                double d4 = Math.sqrt((d - this.navLastX) * (d - this.navLastX) + (d2 - this.navLastY) * (d2 - this.navLastY) + (d3 - this.navLastZ) * (d3 - this.navLastZ));
                this.navLastX = d;
                this.navLastY = d2;
                this.navLastZ = d3;
                if (d4 >= 0.1) {
                    this.navStillTicks = 0;
                    this.navStallReported = false;
                    return;
                }
                this.navStillTicks += 20;
                if (this.navStillTicks < 120 || this.navStallReported) break block10;
                this.navStallReported = true;
                String string3 = "unknown";
                try {
                    object = object2.getClass().getMethod("method_24828", new Class[0]).invoke(object2, new Object[0]);
                    string3 = String.valueOf(object);
                }
                catch (ReflectiveOperationException reflectiveOperationException) {
                    // empty catch block
                }
                object = string.replace('\n', ' ').replace('\r', ' ');
                if (((String)object).length() > 240) {
                    object = ((String)object).substring(((String)object).length() - 240);
                }
                EllieSetupTask.event("SETUP_NAV_STALL", "No significant movement for " + this.navStillTicks + " ticks during navigation. pos=" + d + "," + d2 + "," + d3 + " fractions=" + EllieSetupTask.fract(d) + "," + EllieSetupTask.fract(d3) + " feetBlock=" + (int)Math.floor(d) + "," + (int)Math.floor(d2) + "," + (int)Math.floor(d3) + " belowFeet=" + (int)Math.floor(d) + "," + ((int)Math.floor(d2)-1) + "," + (int)Math.floor(d3) + " bounds=" + bounds(object2) + " onGround=" + string3 + " step=" + (this.index + 1) + "/" + PLAN.length + " task=" + (String)object);
            }
            catch (ReflectiveOperationException | RuntimeException exception) {
                // empty catch block
            }
        }
    }

    private static String bounds(Object entity) {
        try {
            String value = String.valueOf(entity.getClass().getMethod("method_5829").invoke(entity));
            return value.replace('\n', ' ').substring(0, Math.min(160, value.length()));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return "unavailable"; }
    }

    private static double readCoord(Object object, String string, String string2) throws ReflectiveOperationException {
        try {
            return ((Number)object.getClass().getMethod(string, new Class[0]).invoke(object, new Object[0])).doubleValue();
        }
        catch (NoSuchMethodException noSuchMethodException) {
            return ((Number)object.getClass().getMethod(string2, new Class[0]).invoke(object, new Object[0])).doubleValue();
        }
    }

    private static String fract(double d) {
        return String.format(Locale.ROOT, "%.3f", d - Math.floor(d));
    }

    private void resetNavigationWatch() {
        this.navSampleTicks = this.stepTicks;
        this.navStillTicks = 0;
        this.navStallReported = false;
        this.navLastX = Double.NaN;
    }

    public boolean isTerminal() {
        return this.completed || this.failed || this.abandoned;
    }

    public boolean isSuccessful() {
        return this.isSuccessfullyCompleted();
    }

    public String controlledReason() {
        if (this.failed) {
            return this.failureReason;
        }
        if (this.abandoned) {
            return "Setup interrupted before verifying all required items";
        }
        if (this.completed) {
            return "Full starter kit and placed workstation/storage base verified";
        }
        return "Setup still running";
    }

    public boolean isSuccessfullyCompleted() {
        return this.completed && !this.failed && !this.abandoned;
    }

    public boolean isBlocked() {
        return this.failed;
    }

    public String outcomeForCommand() {
        if (this.isSuccessfullyCompleted()) {
            return "Setup COMPLETED: full starter kit verified; crafting table, furnace and one chest physically placed and verified. No house or farm is implied.";
        }
        if (this.failed) {
            return "Setup BLOCKED, not complete: " + this.failureReason;
        }
        return "Setup INTERRUPTED, not complete at step " + (this.index + 1) + "/" + PLAN.length + ". Re-run setup to continue; do NOT claim fully equipped.";
    }

    protected boolean isEqual(Task task) {
        return task instanceof EllieSetupTask;
    }

    protected String toDebugString() {
        if (this.index >= 7 && this.baseTask != null && !this.baseAccepted && !this.failed) {
            return "Setup workstation/storage placement: " + this.baseTask;
        }
        if (this.failed) {
            return "Setup BLOCKED: " + this.failureReason;
        }
        if (this.completed) {
            return "Setup starter kit and workstation/storage base READY";
        }
        if (this.index >= PLAN.length) {
            return "Setup finalizing";
        }
        Step step = PLAN[this.index];
        return "Setup " + step.phase + " [" + (this.index + 1) + "/" + PLAN.length + "]: " + (this.furnaceAssist ? "furnace material assist (cobblestone)" : step.resource) + " x" + step.amount + " (optional skipped=" + this.optionalSkipped + ")";
    }

    public boolean isFinished() {
        return this.completed || this.failed || this.abandoned;
    }

    private void checkpoint(String string, String string2, String string3) {
        if (this.journal == null) {
            return;
        }
        this.journal.put(string, this.index + 1, string2, string3);
        EllieSetupTask.event("SETUP_GOAL_STATE", string + " step=" + (this.index + 1) + "/" + PLAN.length + " item=" + string2 + " detail=" + string3);
    }

    private CoreGoalJournal openGoalJournal() {
        try {
            Class<?> clazz = Class.forName("com.player2.playerengine.tasks.deferred.DeferredJobStore");
            Object object = clazz.getMethod("get", new Class[0]).invoke(null, new Object[0]);
            if (object == null) {
                return CoreGoalJournal.transientJournal();
            }
            Object object2 = clazz.getMethod("worldRoot", new Class[0]).invoke(object, new Object[0]);
            Object object3 = this.controller.getClass().getMethod("getAIPersistantData", new Class[0]).invoke((Object)this.controller, new Object[0]);
            if (object3 == null) {
                return CoreGoalJournal.transientJournal();
            }
            Object object4 = object3.getClass().getMethod("getCharacterId", new Class[0]).invoke(object3, new Object[0]);
            if (!(object2 instanceof Path) || !(object4 instanceof String)) {
                return CoreGoalJournal.transientJournal();
            }
            return CoreGoalJournal.open((Path)object2, (String)object4, "starter_setup");
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return CoreGoalJournal.transientJournal();
        }
    }

    private String optionalMaterialFingerprint() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : new String[]{"oak_log", "oak_planks", "spruce_log", "birch_log", "jungle_log", "cobblestone", "coal", "iron_ingot", "stick"}) {
            stringBuilder.append(string).append('=').append(this.safeDiagnosticCount(string)).append(';');
        }
        return stringBuilder.toString();
    }

    private boolean noEmptyInventorySlots() {
        try {
            ItemStorageTracker itemStorageTracker = this.controller.getItemStorage();
            Object object = itemStorageTracker.getClass().getMethod("hasEmptyInventorySlot", new Class[0]).invoke((Object)itemStorageTracker, new Object[0]);
            return Boolean.FALSE.equals(object);
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static String craftingTableKey(Task task) {
        if (!(task instanceof CraftMacroResourceTask)) {
            return "none";
        }
        try {
            Object object = task.getClass().getMethod("getCraftingTablePos", new Class[0]).invoke((Object)task, new Object[0]);
            return object == null ? "none" : object.toString();
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return "unknown";
        }
    }

    private boolean nearCraftingStation(Task task) {
        if (!(task instanceof CraftMacroResourceTask)) {
            return false;
        }
        try {
            double d;
            double d2;
            Object object = task.getClass().getMethod("getCraftingTablePos", new Class[0]).invoke((Object)task, new Object[0]);
            if (object == null) {
                return false;
            }
            double d3 = ((Number)object.getClass().getMethod("method_10263", new Class[0]).invoke(object, new Object[0])).doubleValue();
            double d4 = ((Number)object.getClass().getMethod("method_10264", new Class[0]).invoke(object, new Object[0])).doubleValue();
            double d5 = ((Number)object.getClass().getMethod("method_10260", new Class[0]).invoke(object, new Object[0])).doubleValue();
            Object object2 = this.controller.getClass().getMethod("getPlayer", new Class[0]).invoke((Object)this.controller, new Object[0]);
            double d6 = EllieSetupTask.readCoord(object2, "method_23317", "getX");
            return (d6 - d3) * (d6 - d3) + ((d2 = EllieSetupTask.readCoord(object2, "method_23318", "getY")) - d4) * (d2 - d4) + ((d = EllieSetupTask.readCoord(object2, "method_23321", "getZ")) - d5) * (d - d5) <= 36.0;
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private void report(String string) {
        try {
            this.controller.getClass().getMethod("reportAgenticProgress", String.class, Boolean.TYPE).invoke((Object)this.controller, string, Boolean.TRUE);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            this.controller.log(string);
        }
    }

    private static void event(String string, String string2) {
        System.out.println("[EllieSetup] " + string + ": " + string2);
        try {
            Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry").getMethod("event", String.class, String.class).invoke(null, string, string2);
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            // empty catch block
        }
    }

    public static final class Step {
        public final String phase;
        public final String resource;
        public final int amount;
        public final boolean required;
        public final int stagnantLimit;

        public Step(String string, String string2, int n, boolean bl, int n2) {
            if (n < 1 || n2 < 1) {
                throw new IllegalArgumentException();
            }
            this.phase = string;
            this.resource = string2;
            this.amount = n;
            this.required = bl;
            this.stagnantLimit = n2;
        }
    }
}
