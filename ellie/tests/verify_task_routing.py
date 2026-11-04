"""Verify actual packaged command bytecode reaches the reviewed project gateway."""
from pathlib import Path
from collections import Counter
import csv
import zipfile

root = Path(__file__).resolve().parents[1]
commands = (root / 'tools/supervised-commands.txt').read_text().splitlines()
with (root / 'build/reports/task-routing.tsv').open() as f:
    rows = list(csv.DictReader(f, delimiter='\t'))
counts = Counter(row['caller'].split('#')[0] for row in rows if row['route'] == 'supervised-tool')
expected = {'com/player2/playerengine/commands/' + name: 2 if name in ('EquipCommand', 'LocateStructureCommand') else 1
            for name in commands}
assert counts == expected, (counts, expected)
assert not any(row['route'] == 'legacy-user-task' and row['caller'].split('#')[0] in expected for row in rows)
# These paths must retain their baseline behavior; they are not project work.
with zipfile.ZipFile(root / 'baseline/playerengine-acquisition-safety.jar') as baseline, \
     zipfile.ZipFile(root / 'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar') as built:
    setup_changes = {
        'com/player2/playerengine/tasks/setup/EllieSetupTask.class',
        'com/player2/playerengine/tasks/setup/EllieSetupTask$Step.class',
        'com/player2/playerengine/tasks/setup/CoreAcquisitionProgress.class',
        'com/player2/playerengine/tasks/setup/CoreAcquisitionProgress$Sample.class',
        'com/player2/playerengine/tasks/crafting/CraftMacroResourceTask.class',
        'com/player2/playerengine/tasks/container/CraftInTableTask.class',
        'com/player2/playerengine/tasks/construction/build_structure/BuildStructureTask.class',
        'com/player2/playerengine/tasks/agentic/LabelChestTask.class',
        'com/player2/playerengine/tasks/base/Task.class',
        'com/player2/playerengine/tasks/container/SmeltInFurnaceTask.class', 'com/player2/playerengine/tasks/construction/PlaceBlockNearbyTask.class',
        'com/player2/playerengine/tasks/crafting/CraftingTableLocator.class',
    }
    for entry in baseline.namelist():
        if entry in setup_changes: continue
        if entry.startswith(('com/player2/playerengine/chains/', 'com/player2/playerengine/tasks/')) or entry in (
            'com/player2/playerengine/commands/BodyLanguageCommand.class',
            'com/player2/playerengine/commands/UnPauseCommand.class',
            'com/player2/playerengine/PlayerEngineController.class',
            'com/player2/playerengine/executor/TaskStepExecutorAdapter.class'):
            assert baseline.read(entry) == built.read(entry), entry
print(f'Task routing verified: {len(commands)} command classes, {sum(counts.values())} supervised call sites; reviewed recovery/production hooks allowed; remaining native chains/tasks preserved')
