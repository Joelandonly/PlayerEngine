# PlayerEngine: The AI Embodiment Framework for Minecraft

**Current direction: [Ellie for Minecraft 1.21.1](ellie/README.md).** The current
Player2 compact setup and crafting-recovery source, tests, and pinned binary
baseline live in [`ellie/`](ellie/). Start there for current development.

| Workflow | Target | Build | Status |
| --- | --- | --- | --- |
| **Current Ellie preview** | Minecraft 1.21.1 Fabric, Java 21, Python 3 | `cd ellie && bash build.sh` | Binary overlay; full matching mod source unavailable |
| Legacy root source | Minecraft 1.20.1, Java 17, Fabric dependencies | `bash gradlew --no-daemon --max-workers=2 build` | Source build; does not produce the Ellie preview |

## Start here

- [Current Ellie setup, architecture, and build](ellie/README.md)
- [Contribution guide and workflow selection](CONTRIBUTING.md)
- [Source provenance and the upstream 1.21.1 branch](docs/ELLIE_SOURCE_PROVENANCE.md)
- [Known bugs and stable backlog IDs](docs/ELLIE_BACKLOG.md)
- [Gameplay validation matrix](docs/ELLIE_VALIDATION.md)
- [Complete command inventory and test results](docs/ELLIE_COMMANDS.md)
- [Full-kit and starter-base setup contract](docs/ELLIE_SETUP.md)
- [Development history](DEVELOPMENT_LOG.md)

The imported bundle preserves the interpreter; the project-supervisor preview adds
reviewed task routing and lifecycle fixes. See [manager verification](docs/ELLIE_PROJECT_MANAGER.md).
It does not claim that reported crafting, navigation, or inference failures are
fixed. Local tests and reproducible packaging are separate from live gameplay.

## Historical framework overview (legacy source)

[![Player2 AI Game Jam](https://img.shields.io/badge/Player2-AI_Game_Jam-blueviolet)](https://itch.io/jam/ai-npc-jam)
[![Powered by Automatone](https://img.shields.io/badge/Powered%20by-Automatone-orange)](https://github.com/Ladysnake/Automatone/tree/1.20)
[![Based on ChatClef](https://img.shields.io/badge/Based%20on-ChatClef-9cf)](https://github.com/elefant-ai/chatclef/tree/main)

**PlayerEngine** is a server-side framework designed to fundamentally change how AI NPCs exist in Minecraft. Developed by **Goodbird**, this project moves beyond the limitations of client-side mods, offering a powerful toolkit to give **your own custom mobs** the full capabilities of a player.

This project was born from the desire to transcend the gimmick of chatbot NPCs and create truly embodied agents for the **Player2 AI Game Jam**. It's not about making vanilla pigs talk; it's about empowering developers to create entities that can mine, fight, manage an inventory, and interact with the world on a player's level.

## The Story: From Client-Side Hacks to a True Framework

The inspiration for PlayerEngine came from **ChatClef**, an innovative mod by Player2 that connected an AI to the player's client. While groundbreaking, it had a significant limitation: to have AI companions, one had to run multiple instances of the Minecraft client. This was cumbersome and not scalable.

PlayerEngine solves this problem by moving the logic to the server and, most importantly, decoupling player-like abilities from the `PlayerEntity` class itself. The result is a true framework that allows any modder to grant their custom `LivingEntity` the soul of a player.

## The Core Concept: The "Player" as an Interface

At its heart, PlayerEngine treats "being a player" not as a specific entity type, but as a set of capabilities that can be attached to any mob. By implementing a few simple interfaces, your custom mob gains access to:

*   A persistent, player-like inventory (`LivingEntityInventory`).
*   The ability to interact with the world, breaking blocks and using items (`LivingEntityInteractionManager`).
*   Advanced pathfinding and task execution via the powerful **Automatone** engine.

This makes PlayerEngine the ultimate "actuator" layer for an AI "brain" like the one provided by the **Player2 API**. Your LLM can decide *what* to do, and PlayerEngine gives your NPC the body to *do it*.

## Key Features for Developers

*   **🤖 Empower Your Mobs:** Designed for modders. Easily transform your own custom entities into player-like agents. Don't just reskin a vanilla mob—give your unique creations true agency.
*   **⛏️ True World Interaction:** NPCs can mine blocks, use tools, and interact with objects. *(Note: complex building is not yet supported).*
*   **🎒 Player-Like Inventories:** Each agent manages its own persistent inventory, allowing for complex resource gathering, crafting, and tool management.
*   **🧠 Seamless Player2 Integration:** PlayerEngine is the perfect physical counterpart to the Player2 API. Send high-level commands like `@get diamond 5` and watch your agent execute a complex chain of tasks to achieve the goal.
*   **🛠️ Built on a Solid Foundation:**
    *   **Navigation:** Powered by **Automatone**, a fork of the legendary Baritone pathfinding engine.
    *   **Task System:** Adapts the robust task and command system from Player2's **ChatClef**.
    *   **Modularity:** Uses **Cardinal Components** to cleanly attach capabilities, ensuring high compatibility and easy integration.

## Why PlayerEngine is "Beyond an AI Gimmick"

*   **Integration:** It's a framework for deep systemic integration. NPCs are no longer just quest-givers; they are active participants in the game's economy, ecology, and emergent stories.
*   **Guardrails:** PlayerEngine *is* the guardrail. It provides a deterministic, game-logic-based action layer that reliably executes the high-level goals from an LLM, complete with fallbacks and a robust understanding of the game world.
*   **Creativity:** It empowers *other creators*. We're not just showing one cool NPC; we're giving the entire community a tool to build their own intelligent companions, adversaries, and dynamic storytellers.
*   **Stability:** Built on the shoulders of giants—Baritone and Cardinal Components—PlayerEngine is a stable and performant foundation for ambitious AI projects.

## Acknowledgements

This project stands on the work and support of many.

### Player2
This framework was created for the **Player2 AI Game Jam** and is designed to integrate seamlessly with the Player2 API, realizing their vision for intelligent, interactive agents.
> *We are a team of researchers and engineers that are passionate about advancing the state of the art in AI. Our team members have worked at some of the world's leading tech companies and research institutions, and we are united by our shared vision of building intelligent agents that can interact with the world in a meaningful way.*

### Foundation & Inspiration
*   **Automatone / Baritone:** The powerful navigation of PlayerEngine is provided by Automatone, a fork of the legendary Baritone pathfinding engine.
*   **ChatClef:** The robust task and command system is adapted from the original ChatClef mod by Player2, which proved the potential of AI agents in Minecraft.

### Special Thanks
*   **Itsuka:** For his invaluable guidance with the Player2 API, brainstorming sessions, and rigorous testing that helped shape PlayerEngine into what it is today.

### Author
PlayerEngine is a solo project developed by **Goodbird**.
## Experimental Ellie / Minecraft 1.21.1 tracking

The current **Minecraft 1.21.1 Fabric / PlayerEngine 1.4.0 interpreter preview** is preserved in [ellie/](ellie/README.md), including bridge source and a pinned binary baseline. The root source still targets Minecraft 1.20.1; the complete matching 1.21.1 mod source remains unavailable. For open bugs, feature proposals, previews, live observations and test requirements, use:

- [Ellie backlog and issue IDs](docs/ELLIE_BACKLOG.md)
- [Chronological development log](DEVELOPMENT_LOG.md)
- [Live-survival validation matrix](docs/ELLIE_VALIDATION.md)

The register lives in Markdown because GitHub Issues were disabled for this repository when tracking was initialized. **A preview JAR is not a verified source implementation.**
