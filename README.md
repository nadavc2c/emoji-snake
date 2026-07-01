# 🐍 Emoji Snake - eye-bleed edition

Classic Snake - but every "pixel" is a **color emoji tile**, wrapped in a trippy, hostile,
self-aware roguelite. Built on the latest Java stack (**Java 25 + JavaFX 25**), rendered on a
stacked JavaFX `Canvas` with a runtime chiptune synth - **no third-party dependencies**.

It starts gentle. Then, after a couple of games, it *wakes up*.

| Tile | Meaning |
|------|---------|
| 🐍 | snake head |
| 🟢 | snake body |
| 🍎🍕🍔🌮🍩🍖 | food (+1, grows you, speeds you up) |
| 🤑 | bonus (+5, appears briefly every 5 foods) |
| 🧱🪦💀 | obstacles (deadly) |
| 🔥🧊👻🪙🧲 | power-ups - burn / slow / ghost / gold×2 / magnet (3-of-a-kind = synergy) |
| 🌀 | portals (paired teleport) |
| 👹 | the boss (genre-shift dodge fight every few levels) |
| 📖 | a rare book - drops you into a "slop AI" visual novel (one of 5) |
| 🏪 | the company store - spend score on power-ups *and* upgrades that last |
| 🎰 | a slot-machine food - gamble it for length |
| 🍖→🐔 / 🥚 | basilisk: roast chicken can turn your head into one; eat the egg to cure it |
| 🪱🐭🐤 | living food - the only kind that flees when you crowd it |
| ✂️ | the barber - a permanent "haircut" so your own tail stops strangling you |

## Controls

| Key | Action |
|-----|--------|
| Arrow keys / **W A S D** | steer (press to start; quick tap-taps buffer) |
| **Space** / **P** | pause / resume |
| **M** | mute / unmute |
| **C** | calm mode (tone down the eye-bleed) |
| **R** / **Enter** | restart after game over |
| **Esc** | quit |

Hit a wall, an obstacle, or yourself and you lose a life (you start with ♥♥♥). Out of lives and
it's game over - usually. Your best score is saved. There may also be a secret. (↑↑↓↓←→←→ B A)

## What makes it crazy

- **Adaptive chiptune** - a runtime square/triangle/noise synth whose tempo, layering, and
  distortion ramp with your speed and the rising *corruption*.
- **Juice** - screen shake, hitstop, particle bursts, neon bloom, motion trails, hue-cycling,
  screen warp, chromatic aberration, CRT scanlines - all escalating, calibrated medium-high.
- **Roguelite** - power-up elements, eat-3-of-a-kind synergies, portals, and a boss that turns
  the game into a bullet-hell.
- **A slop AI visual novel** - eat a 📖 and the game becomes a short branching dating-sim parody
  set at a predatory all-snake law firm, with AI-generated art and a recurring cast. The clichés
  play straight (senpai, rival-to-lover, the wholesome intern) - but the morality is *inverted*:
  the kind, honest choices are the lethal bad ends. Five novels; a wrong choice costs a life.
- **It actually goes somewhere** - spend score in the on-board 🏪 on power-ups *and* permanent
  upgrades that stick across every run (Dead-Cells style): vested extra lives and **the barber**, a
  ✂️ "haircut" that shears your tail as you grow so late-game length stops being an instant death -
  the thing that makes the deep floors survivable. Buy your way down the back-room floors, finish all
  five novels, and a real **ending** unlocks. (No spoilers on what you become.)
- **Per-context music** - the runtime synth swaps to a fitting public-domain **classical** piece for
  each takeover - doom for the boss, Für Elise in the shop, Pachelbel for the novel, Ode to Joy for
  the finale - all played as note data through the same chip synth. Still zero audio files.
- **Rare gags** - once in a long while your whole body just… falls apart. It freezes into a row of
  tombstones and your head pops off on its own. Crawl back and bite your still-living neck (the 🤑,
  ringed in green) to pull yourself back together.
- **The slow reveal** - the first couple of runs play it straight. Then the game starts *talking*
  (dark, cruel, depressive-funny), glitching, and faking your death. All illusions - it never
  touches a single real file beyond its own save file.
- **Forgiving on purpose** - press-to-start, 3 lives, gentle/decaying speed, a 1-in-3 "merciful
  run", early walls that wrap, and fake deaths that hand you back. The derangement *is* the mercy.

## Run it

You have Java 25 installed; everything else is fetched into this folder on first run.

**Windows (PowerShell):**
```powershell
.\run.ps1
```
**Windows (cmd) / double-click:**
```
run.cmd
```
**Or directly via the Gradle wrapper** (any shell):
```
./gradlew -g .gradle-home run        # macOS/Linux/Git Bash
gradlew.bat run                      # (set GRADLE_USER_HOME=.gradle-home first)
```

Run the tests:
```
./gradlew -g .gradle-home test
```

## Share it (build a standalone .exe)

To make a copy a friend can run **without installing Java**:
```powershell
.\package.ps1
```
This uses the JDK's own `jpackage` (no extra tools) to build a self-contained app-image and zips it to
**`dist\emoji-snake-windows.zip`**. They unzip it and double-click `Emoji Snake\Emoji Snake.exe` - a
bundled, trimmed Java runtime + the snake icon travel with it. Nothing is installed on their machine
either; deleting the folder removes it completely.

## Self-contained - your machine stays clean

This project is a sandbox, like a Python `venv` but for Java:

- **Nothing is installed globally.** It uses your existing Java 25; no JDK/Gradle install,
  no `PATH`/registry/global-settings changes.
- **All downloads stay in the repo.** `GRADLE_USER_HOME` is redirected to a repo-local
  `.gradle-home/` (the run scripts do this for you), so Gradle itself, JavaFX, and JUnit
  are cached *inside this folder* - never in your user profile.
- All progress saves to a single `save.dat` **here**, not in your home directory (obfuscated +
  tamper-evident). Delete that one file to start completely fresh.
- **Deleting this folder removes 100% of the footprint.** (The only out-of-repo touch is
  transient OS-temp extraction of JavaFX native libraries, which the OS cleans up.)

Ignored build dirs: `.gradle-home/`, `.gradle/`, `build/`.

## Why image tiles instead of font emoji?

Java's desktop text rendering frequently draws emoji as black-and-white outlines. To
guarantee colorful tiles on any machine, the board is drawn from bundled **OpenMoji**
color PNGs (CC BY-SA 4.0 - see `src/main/resources/ATTRIBUTION.md`).

## Layout

```
build.gradle.kts / settings.gradle.kts   # Java 25 toolchain + JavaFX 25 plugin
run.cmd / run.ps1                          # self-contained launchers
src/main/java/com/emojisnake/              # game source
src/main/resources/emoji/                  # OpenMoji color PNGs
src/main/resources/vn/                      # AI-generated visual-novel + ending art
src/test/java/com/emojisnake/              # JUnit 5 logic tests
```

## License

Code is **MIT** (see [`LICENSE`](LICENSE)). Bundled assets keep their own licenses: the emoji board
tiles are **OpenMoji** (CC BY-SA 4.0, see `src/main/resources/ATTRIBUTION.md`) and the visual-novel /
icon art is **AI-generated** locally with [FLUX.2](https://github.com/nadavc2c/SlopMachine)
(see `src/main/resources/vn/CREDITS.md`).
