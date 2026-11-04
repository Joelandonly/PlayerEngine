# Ellie ChatGPT Interpreter — optional local Sign in with ChatGPT preview

**Platform:** Minecraft 1.21.1 Fabric / PlayerEngine 1.4.0, Java 21, Windows + Node.js 22+.
**Baseline:** Acquisition Safety Preview, preserving Goal Supervisor Phase 2 and prior fixes.

## Quick install (Windows / Shadow PC)

1. Back up your existing PlayerEngine JAR. Close Minecraft.
2. Copy the one `PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-CHATGPT-INTERPRETER-PREVIEW.jar` from the archive root into `.minecraft/mods/`, replacing the existing PlayerEngine JAR. **Do not keep two copies**. Leave **Player2NPC** installed because it supplies the NPC/character integration. This patch does not remove it.
3. Install **Node.js 22+** if necessary (check `node --version` in PowerShell).
4. Keep the `sidecar/` folder next to `Start-Ellie-ChatGPT.ps1`, then from that extracted directory run:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\Start-Ellie-ChatGPT.ps1"
   ```

   Or override your Minecraft path: `-MinecraftDirectory "C:\Users\Shadow\AppData\Roaming\.minecraft"`.

5. At the bridge terminal, type `login`. The browser will open OpenAI's official consent page, or print its login link if automatic opening fails. **Only approve ChatGPT plan usage if you wish**. After successful authorization the bridge tries to retrieve your authorized model list and enables itself. Otherwise Player2 remains selected. If your Windows profile cannot use DPAPI, sign-in now continues as a **memory-only session** rather than failing; you must authorize again after restarting the bridge.
6. Launch Minecraft. Test **`Ellie, setup`**: it should start instantly and never call the language model. Then send a conversational request like `Ellie, how are you?`. Its response should come through the local bridge, and `latest.log` / Observatory should show `CHATGPT_INTERPRETER_REQUEST` and `CHATGPT_INTERPRETER_RESPONSE`.
7. At the terminal use `status` to check `credential_storage=encrypted` or `credential_storage=memory-only`, then `models`, `model MODEL_ID`, `use-player2`, `use-chatgpt`, or `quit` as needed. To revert immediately, type `use-player2`; Player2 stays installed and is not removed.

**Never send screenshots or support files containing `.minecraft/config/ellie-chatgpt.properties` or the `ellie-chatgpt-private/` directory.** The config has a local transport secret; the private directory has encrypted OAuth credentials. The Java mod only knows a local loopback secret and **never sees ChatGPT access or refresh tokens**.

## Architecture and preservation

```
Ellie direct command, e.g. "Ellie, setup"
   -> existing exact command router -> Goal Supervisor -> setup/get/build executors

Ellie natural-language conversation
   -> existing NPC AICommandBridge -> Player2APIService
      -> (provider=player2) original Player2 completion request
      -> (provider=chatgpt) localhost:38457 (authenticated)
             -> local OAuth ChatGPT account -> OpenAI Responses API
             -> validated Player2-style JSON choice -> existing command dispatcher
```

Only the private `Player2APIService.sendChatCompletionRequest` method is wrapped; its previous body remains as `sendChatCompletionRequestViaPlayer2`. All other existing class entries are byte-identical to the Acquisition Safety baseline. We do **not** change the Player2NPC mod, source/tool catalogue, command list, task supervisor, prompt routing, crafting, navigation, or TTS.

## Security and usage

- Uses OpenAI's published open-source **Sign in with ChatGPT** protocol: dynamic local client, browser consent, PKCE, state, nonce, public OIDC JWKS signature verification, and explicit `chatgpt.tokens.use.direct` scope.
- Requests use authorized account token to `POST https://api.openai.com/v1/responses`, with `stream: true`, `store: false`, and no API key; they use the user's **shared eligible plan allowance**, not infinite or separate quota. If permission isn't granted, the bridge does not enable itself.
- On Windows, the bridge **attempts** to store tokens encrypted with current-user DPAPI in `.minecraft/config/ellie-chatgpt-private/connection.dpapi`. If encryption is unavailable, it keeps the authorized session **in memory only** and prints `credential_storage=memory-only`. You must sign in again after the bridge closes. No plaintext token file is written. Non-Windows is likewise RAM-only. The host ID is nonsecret and stable.
- Java/Node traffic binds only to loopback and requires a random 256-bit local shared secret. The secret is created on first sidecar run. Never publish port 38457 or share the secret file. Treat the runtime as trusted local software, not a hardened multi-user server.
- Inference errors, permission revocation, expired tokens, unreachable sidecar or usage limits do **not** silently fall back to Player2, which may have a separate payment mechanism. To return to Player2 explicitly, type `use-player2` or set `provider=player2` in config. Your direct tasks continue without a model call.
- ChatGPT plan use does not grant access to this ChatGPT conversation or memory, and inference model availability depends on the signed-in account's model catalog.
- The interpreter emits exactly the existing Player2 JSON command shape. It is not literally the same ChatGPT conversation or hidden context.
- OAuth credentials should be revoked in ChatGPT Settings if compromised or no longer needed. Terminal `quit` closes the sidecar but doesn't revoke your OpenAI account connection.

Docs:
- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://learn.chatgpt.com/docs/sign-in-with-chatgpt

## Known limits and next validation

- **Preview, not in-game validated.** The bridge's local protocol and static bytecode verification were tested; actual browser OAuth, a supported user/model, OpenAI live inference, and Minecraft end-to-end need testing on your machine. OpenAI sign-in/inference currently has external service limitations and may require re-sign-in after idle periods.
- Player2NPC is still installed and manages the physical character and TTS. This replaces the **chat-completion interpreter**, not Player2's entire NPC infrastructure or unrelated agentic services. In the current patch all `Player2APIService` chat-completion task classes use the ChatGPT backend when enabled; other native non-LLM Player2 endpoints remain unchanged.
- **Does not fix** the vertical crafting table `MOVE_TO_TABLE` stall at `(241,99,343)`, or the furnace / iron-ore source failures. The latest log demonstrates these bugs and they remain in the resource-acquisition workstream. Importantly, this AI change does not mask or pretend to fix them.
- A full Fabric source port is still outstanding. This release is a deterministic Java/ASM binary-overlay build against a pinned JAR, preserving earlier experimental additions.

## Development and tests

Inside the source bundle, run `./build.sh` with Java 21, Python 3 and Node 22+. It executes Node protocol and local HTTP tests, ASM verification, reproducible JAR packaging, and checks every untouched original archive entry. No external service/API request is made by the test suite.

## Authentication hotfix (Nov 3, 2026)

The browser callback can succeed even if Windows PowerShell/DPAPI fails to encrypt the tokens. Earlier releases incorrectly treated this as total authorization failure (`Windows credential encryption unavailable (seal)`). This release keeps the authorized tokens in RAM for the current session and warns that the login will not persist across bridge restarts. The current-user encrypted store remains preferred; no plaintext fallback is introduced.

If you saw the original failure: stop the old bridge, replace **both** `sidecar/bridge.mjs` and `sidecar/secure-session.mjs` from this archive, restart `Start-Ellie-ChatGPT.ps1`, and type `login` again. The previous failed authorization was not retained. Run `status` afterward; `credential_storage=memory-only` is acceptable for a temporary test. The Minecraft JAR is unchanged.

## Model catalog hotfix (Nov 3, 2026)

If the browser reports Ellie authorization received but the terminal reports `ChatGPT returned an empty model catalog`, the previous bridge parsed the wrong JSON format. Sign in with ChatGPT returns `models[]` with account-visible `slug` entries, whereas the previous bridge assumed the API-key `data[]`/`id` format. This release accepts both, selecting only entries advertised as visible under `visibility: "list"` for ChatGPT-plan sign-in.

**Only the bridge changes.** Keep your already-installed PlayerEngine JAR. Stop the old bridge, extract the updated `sidecar/bridge.mjs` and `sidecar/protocol.mjs` into the existing extracted folder (keeping `sidecar/secure-session.mjs` from this bundle), and relaunch `Start-Ellie-ChatGPT.ps1`. Run `login`, then `status` and `models`. If you were in `credential_storage=memory-only` mode, signing in again after restart is expected. A real unavailable model catalog, rejected HTTP response or permission issue is still reported as an error; this release doesn't invent model names or silently bill Player2.

The live OAuth/model catalog and an end-to-end Minecraft session cannot be tested in this build environment. The JAR is byte-identical to the previous interpreter preview.

## Stream diagnostics hotfix (Nov 4, 2026)

Minecraft successfully passed chat-completion requests to the local bridge, but it returned HTTP 503 after an OpenAI streaming failure. The old bridge threw the opaque message `OpenAI stream error` for every `response.failed` or `response.incomplete` event and did not reveal the upstream reason. **This release does not claim the upstream failure was fixed.** It reports the safe machine-readable error code, parameter/type, HTTP status where applicable, and OpenAI request ID. It does not print prompts, tokens, raw response bodies or personal account data.

New terminal command: **`probe`**. Run `status`, then `probe` before launching Minecraft. This tests a minimal public Responses API inference using the connected ChatGPT account and selected model, requiring the actual `response.completed` event. `status` now includes `inference=untested|ok|failed`. A successful sign-in and model catalog are not sufficient proof of inference. If `probe` succeeds but in-game decisions fail, the full Minecraft prompt or request shape needs inspection; the bridge will now report the precise upstream error metadata.

To install this bridge-only hotfix, close the bridge terminal, extract **`sidecar/bridge.mjs`**, **`sidecar/protocol.mjs`** and the **new `sidecar/inference.mjs`** from this ZIP over the previously extracted folder, preserving the directory structure. Restart `Start-Ellie-ChatGPT.ps1`. Do not copy the JAR again; its SHA256 is unchanged (`9464cebb201edf6ac720828bb445293195389c9123007a3319c12510857be2c8`). A memory-only credential session requires reauthorization after restarting the terminal, while successful DPAPI persistence can survive restarts. If an OpenAI error indicates plan usage is unavailable, unsupported or exhausted, there is no automatic Player2 billing fallback.

**Important:** Local mocked tests can verify failure reporting and streaming logic but cannot verify a live inference on the user's authorized account. Do not paste tokens, authorization headers, or `.minecraft/config/ellie-chatgpt*` files into chat.
