#!/usr/bin/env python3
"""Read-only command/backlog parity gate for the PR29 1.21.1 line.

Checks source/manifest/documentation and stable issue IDs. Does NOT claim
Minecraft gameplay coverage, decompile the native JAR, rewrite any files, or
equate the old 1.20.1 Gradle source with the custom 1.21.1 package.
"""
from __future__ import annotations

import csv
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs"
ELLIE = ROOT / "ellie"


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise AssertionError(reason)


def read(path: Path) -> str:
    require(path.is_file(), f"Missing tracked file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def audit_commands() -> None:
    tsv = DOC / "ELLIE_COMMANDS.tsv"
    with tsv.open(newline="", encoding="utf-8") as stream:
        records = list(csv.DictReader(stream, delimiter="\t"))
    require(set(records[0]) == {
        "registration", "name", "class", "description", "argument_literals"
    }, "Unexpected command TSV columns")
    registered = {r["name"]: r for r in records if r["registration"] == "registered"}
    disabled = {r["name"] for r in records if r["registration"] == "unregistered"}
    require(len(records) == 57 and len(registered) == 49 and len(disabled) == 8,
            "49 registered + 8 unregistered entries expected for this PR29 snapshot")
    require(len({r["name"] for r in records}) == 57,
            "Duplicate command ID")
    require(disabled == {"cycle", "gamer", "inventory", "list", "pause",
                         "stash", "status", "unpause"},
            "Unregistered command classes changed without fresh audit")
    require(all(r["class"].startswith("com/player2/playerengine/commands/")
                for r in records), "Command class path not reviewed")

    native_text = read(ELLIE / "src/com/player2/playerengine/nativecommands/NativeCommandText.java")
    ids = re.search(r"IDS\s*=\s*Set\.of\s*\((.*?)\)\s*;", native_text, re.DOTALL)
    require(ids is not None, "Cannot audit native exact-command registry")
    native_ids = set(re.findall(r'"([^"]+)"', ids.group(1)))
    require(len(native_ids) == 48, "Native source expected 46 legacy + 2 schematic IDs")
    require(native_ids | {"excavate"} == set(registered),
            "Native exact parser/TSV command ID mismatch; update source AND docs")
    require({"schematic_prepare", "schematic_build"} <= native_ids,
            "Missing direct schematic owner routes")

    for name, relative in {
        "schematic_prepare": "src/com/player2/playerengine/commands/PrepareSchematicCommand.java",
        "schematic_build": "src/com/player2/playerengine/commands/BuildSchematicCommand.java",
        "excavate": "tools/excavate-src/com/player2/playerengine/commands/ExcavateAreaCommand.java",
    }.items():
        src = read(ELLIE / relative)
        require(f'super("{name}"' in src, f"Missing actual constructor ID for {name}")
        require(registered[name]["class"].split("/")[-1] + ".java" ==
                Path(relative).name,
                f"TSV class does not match the implemented command for {name}")
    require("ExcavationCommandRoute.exact" in read(
        ELLIE / "tools/PatchExcavationCommand.java") or
        'PARSER,"exact"' in read(ELLIE / "tools/PatchExcavationCommand.java"),
        "Excavate native command route not patched")

    setup = read(ELLIE / "src/com/player2/playerengine/commands/EllieSetupCommand.java")
    plan = read(ELLIE / "src/com/player2/playerengine/tasks/setup/BasicSetupTask.java")
    require("new BasicSetupTask()" in setup,
            "Current setup dispatch differs; update the command contract")
    stages = re.findall(r'new Stage\("([^"]+)",\s*(null|"[^"]+")\s*,\s*(\d+),\s*(\d+)\)',
                        plan)
    require(len(stages) == 14, "Current setup no longer has 14 reviewed stages")
    bill = {resource.strip('"'): int(amount) for _, resource, amount, _ in stages
            if resource != "null"}
    require(bill.get("log") == 12 and bill.get("cobblestone") == 20 and
            bill.get("coal") == 16 and bill.get("torch") == 64 and
            bill.get("chest") == 1 and bill.get("furnace") == 1,
            "Setup resource bills changed, review documentation before release")
    require("14-stage" in registered["setup"]["description"] and
            "64 torches" in registered["setup"]["description"] and
            "20 cobblestone" in registered["setup"]["description"],
            "TSV setup description is not the actual basic HOME contract")
    require("iron-or-better tools and armor" not in registered["setup"]["description"],
            "Obsolete survival-kit setup reintroduced")
    doc = read(DOC / "ELLIE_COMMANDS.md")
    require("49 registered NPC commands" in doc or
            "**49 registered NPC commands**" in doc,
            "Command report count is stale")
    for name in registered:
        require(f"`{name}`" in doc or f"`{name} " in doc or
                f"`{name} <" in doc or f"`{name} [" in doc,
                f"Missing registered command in docs/ELLIE_COMMANDS.md: {name}")
    require("14-stage" in doc and "64 torches" in doc,
            "Owner setup description drifted")
    require("historical" in read(DOC / "ELLIE_SURVIVAL_SETUP.md").lower(),
            "Superseded survival instructions need historical banner")
    print("DOC PARITY PASS: 49 registered IDs, 8 unregistered, exact source parser, basic HOME setup contract")


def audit_backlogs() -> None:
    old = read(DOC / "ELLIE_BACKLOG.md")
    now = read(DOC / "ELLIE_BACKLOG_CURRENT_20261109.md")
    historic_ids = re.findall(r"^\| ELLIE-(\d{3}) \|", old, re.MULTILINE)
    current_ids = re.findall(r"^\| ELLIE-(\d{3}) \|", now, re.MULTILINE)
    require(historic_ids == [f"{i:03}" for i in range(1, 30)],
            "Legacy issue register lost, duplicated or renumbered ELLIE-001–029")
    require(current_ids == [f"{i:03}" for i in range(1, 38)],
            "Current issue register lost, duplicated or renumbered ELLIE-001–037")
    require("2026-11-06 source audit follow-up" in old and
            "ELLIE-024" in old and "ELLIE-029" in old,
            "Main-branch-only source audit items were not carried forward")
    require("ELLIE-032" in read(DOC / "ELLIE_INVENTORY_OVERFLOW_BACKLOG_20261109.md"),
            "Overflow detailed backlog must refer to current issue ID")
    for name in ["ELLIE_SETUP.md", "ELLIE_SURVIVAL_SETUP.md",
                 "ELLIE_NATIVE_COMMANDS.md", "ELLIE_SOURCE_PROVENANCE.md"]:
        body = read(DOC / name)
        require("historical" in body.lower() and
                "ELLIE_BACKLOG_CURRENT_20261109.md" in body,
                f"Historical {name} lacks current-source warning")
    print("BACKLOG PARITY PASS: all 29 historical IDs and 37 current IDs preserved; legacy docs dated")


if __name__ == "__main__":
    audit_commands()
    audit_backlogs()
    print("DOC AUDIT ONLY: no live Minecraft gameplay was executed")
