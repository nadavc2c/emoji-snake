# 🐍 Emoji Snake

Classic Snake — except every "pixel" on the board is a little color **emoji tile**. Built on
**Java 25 + JavaFX 25**, with a runtime chiptune soundtrack and no third-party dependencies.

Eat the food, grow longer, don't crash into the walls or yourself. You know the drill. 🍎

## Run it

You'll need **Java 25**. Everything else is fetched into this folder on first run — nothing is
installed on your machine.

**Windows:**
```powershell
.\run.ps1
```
(or just double-click `run.cmd`)

**macOS / Linux / any shell:**
```
./gradlew -g .gradle-home run
```

## Controls

| Key | Action |
|-----|--------|
| Arrow keys / **W A S D** | steer (press a direction to start) |
| **Space** / **P** | pause / resume |
| **M** | mute / unmute |
| **R** / **Enter** | restart |
| **Esc** | quit |

Hit a wall, an obstacle, or your own tail and you lose a life (you start with ♥♥♥). Your best score
is saved. Give it a few rounds. 🙂

## Send it to a friend

`.\package.ps1` builds two ready-to-run bundles into `dist\`:

- **`emoji-snake-windows.zip`** — Windows, **no Java needed.** Unzip and double-click
  `Emoji Snake\Emoji Snake.exe`.
- **`emoji-snake-crossplatform.zip`** — **Windows, macOS *and* Linux** (needs **Java 25** installed).
  Unzip, then run `bin/emoji-snake` (macOS/Linux) or `bin\emoji-snake.bat` (Windows).

## Your machine stays clean

This project is a self-contained sandbox, like a Python `venv` but for Java:

- **Nothing installs globally** — it uses your existing Java 25; no JDK/Gradle install, no
  `PATH`/registry changes.
- **All downloads stay in the repo.** `GRADLE_USER_HOME` is redirected to a repo-local
  `.gradle-home/` (the run scripts do this for you), so Gradle, JavaFX, and JUnit cache *inside this
  folder*.
- Progress saves to a single `save.dat` **here**. Delete that one file to start fresh.
- **Deleting this folder removes 100% of the footprint.**

## Why emoji *images* instead of font emoji?

Java's desktop text rendering often draws emoji as black-and-white outlines. To guarantee colorful
tiles on any machine, the board is drawn from bundled **OpenMoji** color PNGs (CC BY-SA 4.0 — see
`src/main/resources/ATTRIBUTION.md`).

## License

Code is **MIT** (see [`LICENSE`](LICENSE)). Bundled assets keep their own licenses: emoji tiles are
**OpenMoji** (CC BY-SA 4.0); other art is generated locally with
[FLUX.2](https://github.com/nadavc2c/SlopMachine) (see `src/main/resources/vn/CREDITS.md`).
