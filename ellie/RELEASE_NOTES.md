# Ellie ChatGPT Interpreter Preview

## Latest log reviewed

- At Minecraft local time **22:22:17** you wrote: `she just standin there with iron in the furnace after i tried to make her go agane cuz she walked all da way to this village for the last block`.
- At **22:21:01** the setup's `iron_helmet` task chose crafting table `(241,99,343)`. At **22:21:10** the navigation stall position was `(242.0244,96,343.5063)`, meaning the table was three blocks higher than Ellie's feet while she was unable to make progress.
- Bucket was skipped at **22:20:06** after smelting without acquiring the requested output, and iron chestplate was skipped at **22:21:00**. Cobblestone material counts rose substantially without corresponding required iron acquisition. This is a repeat executor-level fault, not evidence that Player2 selected the wrong command.
- Your final chat comment was **not delivered to Ellie** because Player2's `call_by_name_no_mention` filter rejected it, as expected for a diagnostic note without `Ellie` addressed in it. No new task command was dispatched.
- At **22:22:18** the game was exiting; setup was saved as `INTERRUPTED` and the last goal was cancelled, with no claim of full success. All dimensions were saved by 22:22:20 in this log.

## Changes

- Added an optional, explicit **Sign in with ChatGPT** conversational interpreter bridge using a local Node.js process and the documented OpenAI OAuth + plan-use protocol.
- `Player2APIService` is the **only modified existing class**; its original chat-completion code is preserved verbatim as a renamed private method and used whenever `provider=player2`.
- Added `LocalChatGPTBridge` new class, reading a local config and making authenticated HTTP requests only to 127.0.0.1.
- Existing exact setup routing, Phase 2 Goal Supervisor, Acquisition Safety travel-progress + wool-bed changes, Player2NPC and TTS are preserved.
- Added locally verified JSON output shape, bounded requests, bounded timeouts, strict SSE completion, OIDC ID-token checks, encrypted Windows DPAPI credential persistence, and explicit no-silent-fallback policy.

## Validation boundaries

Local unit tests and JVM structural verification are not proof that Windows OAuth or Minecraft runtime integration will succeed. The external credentials and network requests cannot be tested from this environment, so the preview remains opt-in. The crafting-table/iron source failures in the logs are **not fixed by this interpreter patch**.

## Auth hotfix: `Windows credential encryption unavailable (seal)`

- Authorization can complete even when DPAPI current-user encryption is unavailable on a Windows/Shadow profile. Tokens remain in the running Node process's RAM only, never in plaintext on disk.
- `status` shows `credential_storage=encrypted`, `memory-only`, or `none`.
- Added explicit System.Security assembly loading for PowerShell DPAPI and a nonsecret diagnostic exit code.
- Corrected the browser callback wording: receiving an authorization code is not yet the same as completing sign-in.
- JAR and Minecraft gameplay code are **byte-for-byte unchanged**. Five new unit tests for credential fallback.

## Model catalog response parser hotfix

- Fixed an incorrect assumption that the ChatGPT plan catalog uses the standard OpenAI API `data[].id` schema. The documented Sign in with ChatGPT schema is `models[].slug` with `visibility: "list"`.
- Added compatibility for both schema variants and explicit errors for unavailable or unrecognized catalogs. No hard-coded model fallback.
- Only `sidecar/bridge.mjs`, `sidecar/protocol.mjs`, and associated tests/docs changed. No Minecraft class or JAR changes.
- Ran 17 passing Node tests, including 3 new model-catalog regression tests. No live account authorization or model inference test.

## Nov 4, 2026: ChatGPT Responses stream diagnostics

- Reviewed `latest(20261103-232114).log` and Observatory: two `CHATGPT_INTERPRETER_REQUEST task=DECISION` attempts followed by bridge HTTP 503. No upstream OpenAI error code is present in Minecraft's log.
- Distinguishes `response.failed`, `response.incomplete`, and `error` SSE events. Preserves sanitized error codes/parameters and x-request-id, including JSON admission failures (`{error:...}` or `{detail:...}`). Strict `response.completed` requirement retained.
- Handles SSE CRLF delimiters split across network chunks. Adds `sidecar/inference.mjs` to share one tested Responses request path between Minecraft and a new terminal `probe` test. `status` now exposes inference status.
- Keeps localhost auth, OAuth consent, no plaintext credentials, no silent Player2 fallback, and `store:false`/`stream:true` exactly as before.
- Node mocked tests: 24 pass. No live authorization/inference or Minecraft test was performed. No JAR change.
