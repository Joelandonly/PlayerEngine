# Developing PlayerEngine

## Choose the workflow

The current direction is **Ellie for Minecraft 1.21.1** in [`ellie/`](ellie/README.md).
Use a Java 21 JDK, Node 22+, and Python 3:

```sh
cd ellie
bash build.sh
python3 make_release.py
```

This runs Node tests, JVM configuration checks, ASM verification and JAR packaging.
The module retains a checksum-pinned binary baseline because the matching full mod
source is unavailable. Preserve baseline behavior and provenance; do not replace it
with the legacy root JAR. CI runs Ellie and legacy builds as separate jobs.

The following sections describe the **legacy root build** only. It remains available
for reference and maintenance; a successful root build does not validate Ellie.

## Legacy supported source

| Component | This branch |
| --- | --- |
| Minecraft | 1.20.1 |
| Java JDK | 17 |
| Build tool | Gradle 8.10, through the checked-in wrapper |
| Loom | 1.6.12 |
| Loader dependency | Fabric Loader 0.15.6 |
| Fabric API | 0.92.1+1.20.1 |

The Ellie 1.21.1 / PlayerEngine 1.4.0 preview JARs are distributed separately.
They do not establish that this source contains the preview implementation.
Some historical documentation refers to Quilt; the build currently declares Fabric.

## Build

Install a Java 17 **JDK** and point `JAVA_HOME` to it. From the repository root:

```sh
java -version
bash gradlew --version
bash gradlew --no-daemon --max-workers=2 build
```

On Windows, use `gradlew.bat` in place of `bash gradlew`. A global Gradle
installation is unnecessary. The first build downloads Minecraft, mappings,
plugins, and dependencies; allow time and memory for Loom's processing. The
project reserves up to 3 GiB for Gradle. Use the wrapper rather than upgrading
Gradle or Loom independently.

JAR outputs belong in `build/libs/`. The intended distributable is
`PlayerEngine-1.0.0.jar`; `-dev` and sources JARs are development artifacts.
GitHub Actions runs the same build command on pushes to main and pull requests,
and retains JARs only after a successful build. It also retains available reports
on failure.

There are currently no automated test sources. A successful `build` verifies
compilation and packaging, not NPC behavior. Do not report `test NO-SOURCE` as
passing gameplay tests.

## Runtime validation

PlayerEngine is a framework for integrating custom entities, not a standalone
Ellie companion distribution. Before installing an artifact on a real world,
validate it in a disposable Minecraft 1.20.1 Fabric environment with the matching
Fabric API and the consuming entity mod. Do not use the separate 1.21.1 preview
as a substitute for testing this branch.

Record the exact commit, Minecraft/loader/API versions, consuming mod, command,
expected result, actual result, and relevant logs. Check mod initialization,
entity integration, a simple task, task cancellation, and save/reload before
testing long-running navigation. A supported spawn command and complete demo
entity setup still need to be documented and verified; do not assume that an
Ellie spawn command exists in this source.

See [the Ellie validation matrix](docs/ELLIE_VALIDATION.md) for preview-specific
acceptance checks, and [the backlog](docs/ELLIE_BACKLOG.md) for existing issue IDs.

## Contributions and bug reports

Keep changes focused and include the build result in your pull request. For a
behavior fix, include a reproduction and the relevant positive and negative
checks. Distinguish source verification, automated tests, and live gameplay.
Do not commit credentials, personal game directories, generated JARs, or worlds.

When reporting a bug, include the versions and exact artifact or commit, steps
to reproduce, expected and actual behavior, and a minimal log with credentials
removed. Reference an existing backlog ID when applicable. If repository Issues
are disabled, record the report in the Markdown backlog.
