from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'baseline/playerengine-acquisition-safety.jar'
out=ROOT/'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-NATIVE-COMMANDS.jar'
changes={p.relative_to(ROOT/'build/classes').as_posix():p.read_bytes() for p in (ROOT/'build/classes').rglob('*.class')}
changes.update({p.relative_to(ROOT/'build/patches').as_posix():p.read_bytes() for p in (ROOT/'build/patches').rglob('*.class')})
expected_existing={
    'com/player2/playerengine/supervisor/CoreGoalSupervisor.class',
    'com/player2/playerengine/supervisor/CoreGoalSupervisor$Slot.class',
    'com/player2/playerengine/supervisor/GoalLifecycle.class',
    'com/player2/playerengine/supervisor/GoalLifecycle$Snapshot.class',
    'com/player2/playerengine/supervisor/GoalLifecycle$Status.class',
}
expected_existing.update({
    'com/player2/playerengine/commands/EllieSetupCommand.class',
    'com/player2/playerengine/tasks/setup/EllieSetupTask.class',
    'com/player2/playerengine/tasks/setup/EllieSetupTask$Step.class',
    'com/player2/playerengine/tasks/setup/CoreAcquisitionProgress.class',
    'com/player2/playerengine/tasks/setup/CoreAcquisitionProgress$Sample.class',
    'com/player2/playerengine/tasks/crafting/CraftMacroResourceTask.class',
    'com/player2/playerengine/tasks/container/CraftInTableTask.class',
    'com/player2/playerengine/tasks/crafting/CraftingTableLocator.class',
})
expected_existing.update('com/player2/playerengine/commands/'+name+'.class' for name in (ROOT/'tools/supervised-commands.txt').read_text().splitlines())
expected_existing.update('com/player2/playerengine/'+name+'.class' for name in [
    'automaton/api/entity/LivingEntityInteractionManager',
    'agentic/AgenticPlannerService','agentic/elliegps/WaypointIngestionService',
    'commands/BuildStructureCommand','commands/AgenticCommand','commands/random/ScanCommand','commands/base/CommandExecutor',
    'player2api/SetupDirectRoute','tasks/agentic/LabelChestTask',
    'tasks/construction/build_structure/BuildStructureTask'])
expected_new={'com/player2/playerengine/supervisor/ProjectTaskGateway.class',
              'com/player2/playerengine/tasks/crafting/CraftTableApproachRecovery.class',
              'com/player2/playerengine/tasks/crafting/CraftTableApproachRecovery$Progress.class'}
expected_new.update('com/player2/playerengine/tasks/setup/'+name+'.class' for name in [
    'StarterBaseLayout','StarterBaseLayout$Pos','StarterBaseLayout$Placement','StarterBaseLayout$Probe',
    'StarterBaseTask','StarterBaseTask$Environment','StarterBaseWorld',
    'SetupBuildZone','SetupBuildZone$Shape','SetupBuildZone$Origin'])
expected_new.update('com/player2/playerengine/nativecommands/'+name+'.class' for name in [
    'NativeAccess','NativeBuildSupport','NativeFood','NativeCommandText'])
expected_new.update('com/player2/playerengine/reliability/'+name+'.class' for name in ['NpcItemUse','ProductionHandoff'])
removed={'com/player2/playerengine/commands/random/DummyTaskCommand.class',
         'com/player2/playerengine/commands/random/DummyTaskCommand$DummyTask.class'}
assert set(changes)==expected_existing|expected_new,(set(changes)-(expected_existing|expected_new),(expected_existing|expected_new)-set(changes))
with ZipFile(base) as z: orig={k:z.read(k) for k in z.namelist() if not k.endswith('/')}
assert set(changes)-set(orig)==expected_new
assert set(changes)&set(orig)==expected_existing
out.parent.mkdir(parents=True,exist_ok=True)
with ZipFile(out,'w',compression=ZIP_DEFLATED,compresslevel=6) as z:
    for key in sorted((set(orig)|set(changes))-removed):
        info=ZipInfo(key,date_time=(2026,11,3,0,0,0));info.compress_type=ZIP_DEFLATED;info.external_attr=0o644<<16
        z.writestr(info,changes.get(key,orig.get(key)),compress_type=ZIP_DEFLATED,compresslevel=6)
with ZipFile(out) as z:
    assert z.testzip() is None
    assert len(z.namelist())==len(set(z.namelist()))
    for key,value in orig.items():
        if key not in changes and key not in removed:assert z.read(key)==value,key
print('Generated',out,'sha256',sha256(out.read_bytes()).hexdigest())
