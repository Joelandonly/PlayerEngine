# Ellie: 1.21.1 project supervisor preview

This is the project's current development direction, based on
`PlayerEngine-Ellie-ChatGPT-Interpreter-STREAM-HOTFIX-FLAT.zip` supplied by the
maintainer. The project supervisor now has readable recovered source, regression
coverage and reviewed tool routing. It retains the older Minecraft 1.20.1 code
in the repository root as legacy reference.

**Target:** Minecraft 1.21.1 Fabric, PlayerEngine 1.4.0, Java **21 JDK**, Node.js
**22+**, Python **3**. Runtime installation currently targets Windows / Shadow PC
and still requires the separately installed Player2NPC integration.

## What is here

| Path | Purpose |
| --- | --- |
| `sidecar/` | Local ChatGPT sign-in, inference, stream handling, encrypted-session support |
| `src/` | Java bridge, recovered supervisor/lifecycle source, project task gateway |
| `tests/` | Node tests, JVM bridge test, supervisor harness and routing verification |
| `tools/` | Exact-method ASM patching, verification, deterministic JAR packaging |
| `baseline/` | Required Acquisition Safety baseline binary, pinned by SHA-256 |
| `Start-Ellie-ChatGPT.ps1` | Windows bridge launcher |
| `RELEASE_MANIFEST.json` | Original upload's provenance and artifact hashes |
| `UPSTREAM_README.md`, `RELEASE_NOTES.md` | Original bundle documentation and known limitations |
| `build/`, `dist/` | Generated outputs, ignored by Git |

**This is a binary-overlay build, not the complete 1.21.1 mod source.** The baseline
contains earlier navigation, acquisition, supervisor, and NPC integration work
whose matching source has not been recovered. Retain it until a source port can
be verified. Do not substitute the legacy root Gradle JAR for this baseline.

A public upstream 1.21.1 mod baseline has since been identified on Goodbird's
`1.21.1-arch` branch (version 1.1.11). It is not yet established as the matching
source for this Ellie 1.4.0 preview. See [source provenance](../docs/ELLIE_SOURCE_PROVENANCE.md)
for the pinned commit and recovery path.

## Build and test

From this directory, with the Java 21 JDK tools on `PATH`:

```sh
bash build.sh
python3 make_release.py
```

The build verifies the baseline checksum, starts from clean generated class
directories, compiles the bridge and supervisor, patches the interpreter and reviewed command
submission points, runs ASM verification, JVM and Node tests, and packages the
preview JAR. The generated `build/reports/task-routing.tsv` inventories static
submission routes; it does not prove runtime execution.
Packaging verifies that every unrelated baseline archive entry is unchanged.
It never requires account credentials or a live OpenAI request.

The release command writes a portable ZIP to `dist/` containing the JAR,
launcher, sidecar, tests, build tools, baseline, documentation, and a generated
manifest for that build. It does not overwrite the original provenance manifest.
GitHub Actions tests this module on Java 21 / Node 22 and retains the release ZIP
and JAR as build artifacts. These are previews, not automatically published releases.

## Install and operate

Download/extract the **Ellie** artifact, not the legacy Gradle artifact. Follow
the [original Windows install guide](UPSTREAM_README.md#quick-install-windows--shadow-pc).
Replace the existing PlayerEngine JAR rather than installing a duplicate, retain
Player2NPC, and keep the sidecar beside the PowerShell launcher. Use a backed-up,
disposable world first.
For this build, install the `ELLIE-PROJECT-SUPERVISOR-PREVIEW.jar` from `dist/`;
the interpreter filename in the historical guide refers to the original upload.

In the bridge terminal, use `login`, `status`, and `probe` before testing chat in
Minecraft. `probe` performs a real account/model inference; it is not a local test.
Use `use-player2` only when intentionally selecting Player2. A failed ChatGPT
request must not silently switch billing providers.

Never commit or share the generated `ellie-chatgpt.properties` file or
`ellie-chatgpt-private/` directory. Credentials stay outside this repository.

## Validation and next work

The original upload reports local tests and ASM verification, but no live OAuth,
inference, or Minecraft validation. The maintainer reports partial runtime success;
this import does not turn that into a complete gameplay pass. See the
[validation matrix](../docs/ELLIE_VALIDATION.md) and
[backlog](../docs/ELLIE_BACKLOG.md).

See [project manager verification and remaining gates](../docs/ELLIE_PROJECT_MANAGER.md).
The hardened JAR has a distinct `ELLIE-PROJECT-SUPERVISOR-PREVIEW` filename and is
not byte-identical to the original interpreter upload. ChatGPT bridge behavior is
unchanged; the supervisor and reviewed command routes intentionally change.
Navigation, crafting and furnace bugs are not claimed fixed.
