# Ellie source and artifact provenance

## Current preserved version

The maintainer supplied `PlayerEngine-Ellie-ChatGPT-Interpreter-STREAM-HOTFIX-FLAT.zip`.
Its interpreter source, sidecar, tests and binary build inputs are preserved in
[`ellie/`](../ellie/README.md). The rebuilt output matches the uploaded preview JAR
byte for byte:

- Preview JAR SHA-256: `9464cebb201edf6ac720828bb445293195389c9123007a3319c12510857be2c8`
- Acquisition Safety baseline SHA-256: `8ae4c3da375b45ad60f94ddbd4d4c31328400cce0c4a720b1002dd8dee143d27`

This preserves the current version while the exact source for earlier custom
Ellie additions is recovered. Do not treat the binary baseline as newly authored source.

## Upstream mod source discovered on 2026-11-04

[Goodbird-git/PlayerEngine, `1.21.1-arch`](https://github.com/Goodbird-git/PlayerEngine/tree/aa4ad2d5ec2a834107c76cdb58af732acb192ecc)
is a public Minecraft **1.21.1** mod source baseline:

- Inspected commit: `aa4ad2d5ec2a834107c76cdb58af732acb192ecc`
- Advertised mod version: `1.21.1-1.1.11`
- Architectury modules: `common`, `fabric`, `neoforge`
- Contains `common/src/main/java/com/player2/playerengine/player2api/Player2APIService.java`
- Declares Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1 and Architectury API 13.0.8

The upstream default `main` still declares Minecraft 1.20.1; selecting the correct
branch matters. The 1.21.1 branch is a candidate for restoring a complete source
build, not proof that the custom Ellie 1.4.0 changes are included. It has not been
built or compared class by class with the supplied baseline during this organization
pass. Preserve the existing binary until equivalent source behavior is verified.

Next source work: build the pinned upstream branch, inventory differences against
the supplied baseline, recover/port the custom supervisor, acquisition, navigation
and observatory additions, then add the interpreter in readable source and validate
runtime compatibility with Player2NPC. Track behavioral acceptance in the existing
backlog and validation matrix rather than declaring equivalence from version labels.

## Vanilla reference

[Soumeh/1.21.1-Deobfuscated](https://github.com/Soumeh/1.21.1-Deobfuscated) describes
decompiled vanilla Minecraft with Yarn mappings. It can help understand Minecraft
APIs; it does not contain PlayerEngine's custom mod source or recover Ellie's changes.
