# CraftBro AI

CraftBro AI is a Minecraft Java mod powered by local AI. It currently gives advice based on your location, health, and inventory. The goal is to let you point at something in the world and ask for repetitive work to be done—chopping a tree, clearing an area, or replacing blocks—using your tools and materials under normal survival rules.

Built for **Minecraft Java Edition 1.21.1** with **Fabric** and **Ollama**. AI inference runs on your own machine, without a cloud subscription or API key.

**Status: early prototype. Contextual advice and telemetry work today; world-changing actions are planned.**

## First draft: what works today

- `/askmod <question>` answers questions using a snapshot of your position, biome, health, hunger, inventory, and daytime status.
- `/askmod` asks for next-step advice; `/aitip` requests one useful tip.
- Answers appear in a compact, unblurred panel that stays open until you press **Close** or **Esc**. Long replies have pages. `/aipanel` reopens the latest reply in the current connection.
- Optional Aspire telemetry shows AI prompts, context, responses, tokens, timings, errors, and sampled player movement.
- macOS launchers run the development game, local AI, and telemetry dashboard.

**The AI currently gives advice only. It cannot break, place, or change blocks.** Small models can give incorrect advice; each request is independent and has no conversation memory.

## Planned: point, ask, and act

The next stage is assistance with repetitive survival tasks:

| Request | Intended behavior |
| --- | --- |
| Point at a tree: “Chop this tree.” | Preview the selected tree, then break its logs progressively using an appropriate tool and consuming durability. |
| Select an area: “Clear this patch.” | Show the affected blocks before clearing within the confirmed boundaries and collecting normal drops. |
| Point at a surface: “Replace this with stone.” | Preview the replacement and required materials, then use blocks from your inventory. |

The player chooses the task and approves its scope. AI interprets the instruction; game code will validate and execute the permitted action. Planned safeguards include cancellation, checks for sufficient materials and usable tools, and stopping when an action can no longer proceed safely.

These are design goals, not features in the current release. The first planned action is tree chopping. The aim is **less grinding while keeping the resource costs and progression of survival gameplay**.

## Requirements

- Java 21 or newer (development tested with Java 25).
- Minecraft Java 1.21.1, Fabric Loader 0.19.5+, and matching Fabric API for an installed-game setup.
- Ollama with `qwen3.5:4b` for AI. No cloud account or API key is required.
- macOS for the included `.command` launchers. Other platforms can build with Gradle and run Ollama separately.

## Quick start on macOS

1. Clone this repository and enter its directory.
2. Double-click **Launch Minecraft with Telemetry.command**.
3. Wait for Aspire, Ollama, and the development game to start. First launch downloads dependencies and the model (about 3.4 GB).
4. Enter a world, open chat with **T**, and try `/askmod How do I make a crafting table?`.

The telemetry launcher rebuilds the mod before starting. Reusing it closes this project's existing development game normally before relaunching; it refuses to force-kill a stuck game. Services already running are reused. Service startup logs are in `.tools/aspire-launch.log` and `.tools/ollama-launch.log`.

For a game without telemetry, start **Launch Local AI.command**, then **Launch Minecraft.command**. `/hellomod` remains a basic diagnostic command from the prototype. The internal mod ID remains `hellomod` for compatibility.

## Build and install

```sh
./gradlew --gradle-user-home .gradle-user-home build
```

Windows: use `gradlew.bat`. The mod JAR is `build/libs/craftbro-ai-1.0.0.jar`; copy it into your Fabric instance's `mods` directory alongside Fabric API. Start Ollama on the Minecraft server's machine (your Mac for single-player):

```sh
ollama pull qwen3.5:4b
ollama serve
```

If Ollama is already running, do not start another server. The macOS launcher installs its own runtime under `.tools/ollama/`; that installation is not added to your terminal's PATH. To check it, run `./.tools/ollama/ollama ps` from this directory.

The reply panel requires the mod on the client; clients without its payload support receive chat replies. The panel pauses ordinary single-player play, while multiplayer continues running.

## Aspire telemetry

Start **Launch Aspire Dashboard.command** separately if desired. Use the dashboard login link printed in its output. The default dashboard is at `http://localhost:18888` and OTLP HTTP at `http://127.0.0.1:4318`. Docker and a .NET backend are not required.

Start Minecraft with these environment variables to enable telemetry:

```sh
HELLOMOD_TELEMETRY_ENABLED=true OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318 ./gradlew --gradle-user-home .gradle-user-home runClient
```

Select service **minecraft-hello-mod** (the prototype's telemetry name):

- **`ai.askmod` → GenAI details:** exact system prompt, question, game snapshot, full response, token counts, model settings, HTTP status, errors, and Ollama timing details when available. The cleaned answer shown in-game is recorded separately.
- **`player.move`:** previous/current coordinates and dimension, emitted after at least one block of displacement, sampled every 20 server ticks. Standing still or looking around emits nothing. Dimension changes emit a trace without a cross-dimension distance.
- **Logs and metrics:** joins and `/hellomod` executions.

**Content recording:** AI questions, inventory snapshots, coordinates, and answers are sent to the configured telemetry destination. Ordinary chat, account UUIDs, and player names are not explicitly collected. Keep the destination local unless you intend to share this data. Aspire being offline does not prevent gameplay or AI requests.

## Optional browser chat

The browser UI is separate from the mod and does not automatically receive game context. Install Open WebUI into a Python 3.11 or 3.12 environment:

```sh
python3.12 -m venv .tools/webui
.tools/webui/bin/python -m pip install open-webui==0.11.3
```

Then run **Launch Browser Chat.command** and open `http://localhost:3000`. It uses local-only, single-user mode with no login. History and settings are in `.tools/webui-data`.

Fresh-install defaults disable thinking, built-in tools, and automatic title/tag/follow-up generation; replies are capped at 256 tokens with a 4096-token context. Existing saved settings override launcher defaults. Browser chat and Minecraft share the model, so concurrent requests may queue.

## Developer checks

```sh
# Build plus local HTTP integration checks (no model required)
./gradlew --gradle-user-home .gradle-user-home build

# Real Ollama request and a synthetic trace sent to local Aspire
./gradlew --gradle-user-home .gradle-user-home aiLiveTest
```

AI calls run asynchronously with a 120-second timeout, a 4096-token context, a 220-token response limit, and one active mod request at a time. Minecraft state is captured and replies delivered on the server thread. Responses for disconnected or respawned players are discarded.

World saves, model weights, downloaded runtimes, chat databases, credentials, build outputs, and machine-specific app bundles are excluded from this repository.

## Attribution

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

Built using [Fabric](https://fabricmc.net/), [Ollama](https://ollama.com/), [OpenTelemetry](https://opentelemetry.io/), and the [Aspire dashboard](https://aspire.dev/dashboard/). Optional browser chat uses [Open WebUI](https://docs.openwebui.com/). Third-party components retain their respective licenses.
