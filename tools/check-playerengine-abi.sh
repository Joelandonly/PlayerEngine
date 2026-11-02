#!/usr/bin/env bash
# Static gate for PlayerEngine 1.4.0 / Minecraft 1.21.1 Fabric candidate jars.
# This is NOT an in-game GameTest.
set -euo pipefail
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 path/to/playerengine-fabric-1.21.1-1.4.0.jar" >&2
  exit 2
fi
JAR="$1"
for tool in python3 javap; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 2; }
done
[[ -f "$JAR" ]] || { echo "Not found: $JAR" >&2; exit 2; }
python3 - "$JAR" <<'PY'
import json,sys,zipfile
j=sys.argv[1]
with zipfile.ZipFile(j) as z:
    corrupt=z.testzip()
    if corrupt:
        raise SystemExit(f"FAIL: corrupt JAR entry {corrupt}")
    names=set(z.namelist())
    for name in (
        'fabric.mod.json',
        'com/player2/playerengine/commands/BuildStructureCommand.class',
        'com/player2/playerengine/tasks/construction/build_structure/BuildStructureTask.class',
        'com/player2/playerengine/tasks/construction/build_structure/BuildStructureTask$BuildFromCode.class',
    ):
        if name not in names:
            raise SystemExit(f"FAIL: missing JAR entry: {name}")
    m=json.loads(z.read('fabric.mod.json'))
    if m.get('id') != 'playerengine':
        raise SystemExit(f"FAIL: wrong Fabric mod ID: {m.get('id')}")
    print(f"Archive OK; mod ID={m['id']}; mod version={m.get('version','?')}")
PY
CLS='com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask$BuildFromCode'
JAVAP="$(javap -p -classpath "$JAR" "$CLS")" || { echo 'FAIL: javap could not read BuildFromCode' >&2; exit 1; }
if ! grep -Fq 'BuildFromCode(com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask, java.lang.String)' <<<"$JAVAP"; then
  echo 'FAIL: expected original BuildFromCode(BuildStructureTask, String) constructor is missing' >&2
  exit 1
fi
for class in \
  'com.player2.playerengine.commands.BuildStructureCommand' \
  'com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask' \
  "$CLS"; do
  MAJOR="$(javap -verbose -classpath "$JAR" "$class" | sed -n 's/^[[:space:]]*major version: //p' | head -n 1)" || true
  [[ "$MAJOR" == "65" ]] || { echo "FAIL: $class has class version '$MAJOR', expected Java 21 / 65" >&2; exit 1; }
done
printf 'PASS: original construction constructor present, Java 21 classes and ZIP CRC verified.\n'
printf 'WARNING: static ABI checks cannot prove world placement or task completion.\n'
