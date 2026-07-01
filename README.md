# 🐍 Emoji Snake

Classic Snake — but every dot on the board is a tiny color **emoji**. Eat the food 🍎, grow longer,
and don't crash into the walls or your own tail. Simple, colorful, and a little bit silly.

## Play it

**Windows — nothing to install:**

1. Download **`emoji-snake-windows.zip`** from the [latest release](https://github.com/nadavc2c/emoji-snake/releases/latest).
2. Unzip it anywhere.
3. Open the folder and double-click **`Emoji Snake.exe`**.

That's it — no Java, no setup, no installer.

**Mac / Linux** (or Windows, if you already have **Java 25**): grab `emoji-snake-crossplatform.zip`
from the same release, unzip, and run `bin/emoji-snake` (or `bin\emoji-snake.bat` on Windows).

## Controls

| Key | What it does |
|-----|--------------|
| Arrow keys / **W A S D** | steer — press any direction to start |
| **Space** / **P** | pause |
| **M** | mute |
| **R** / **Enter** | restart |
| **Esc** | quit |

You start with three lives (♥♥♥), and your best score is remembered. Give it a few rounds. 🙂

## It won't mess with your computer

Nothing gets installed and no system settings are touched. The whole game lives in its own folder —
**delete the folder and it's gone without a trace.** Your progress is a single `save.dat` file;
delete that to start over.

## Build it yourself

Want to poke at the code? Install **Java 25**, then:

- **Windows:** `.\run.ps1`
- **Mac / Linux:** `./gradlew -g .gradle-home run`

Nothing installs globally — it all downloads into this folder. Full build / test / package commands
are in [`CLAUDE.md`](CLAUDE.md).

## License

Code is **MIT** ([`LICENSE`](LICENSE)). The emoji tiles are **OpenMoji** (CC BY-SA 4.0); some other
art is generated locally — details in `src/main/resources/ATTRIBUTION.md`.
