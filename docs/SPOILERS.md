# Emoji Snake — what's actually going on (⚠️ SPOILERS)

> **Stop.** This page spoils the whole point of the game. If you haven't played a handful of rounds
> yet, close this and go do that first. It starts as plain, honest Snake on purpose.

---

It starts gentle. Then, after a couple of games, it *wakes up* — into a trippy, hostile, self-aware
roguelite that talks back, glitches, fakes your death, and sends you down a rabbit hole.

## The full tile legend

| Tile | Meaning |
|------|---------|
| 🐍 | snake head |
| 🟢 | snake body |
| 🍎🍕🍔🌮🍩🍖 | food (+1, grows you, speeds you up) |
| 🤑 | bonus (+5, appears briefly every 5 foods) |
| 🧱🪦💀 | obstacles (deadly) |
| 🔥🧊👻🪙🧲 | power-ups — burn / slow / ghost / gold×2 / magnet (3-of-a-kind = synergy) |
| 🌀 | portals (paired teleport) |
| 👹 | the boss (genre-shift dodge fight every few levels) |
| 📖 | a rare book — drops you into a "slop AI" visual novel (one of 5) |
| 🏪 | the company store — spend score on power-ups *and* upgrades that last |
| 🎰 | a slot-machine food — gamble it for length |
| 📈 | a stock — eat it to compound your score (a per-run "rally"; the market can crash) |
| 🚪 | the store's "back room" — spend score to descend a floor |
| 🍖→🐔 / 🥚 | basilisk: roast chicken can turn your head into one; eat the egg to cure it |
| 🪱🐭🐤 | living food — the only kind that flees when you crowd it |
| ✂️ | the barber — a permanent "haircut" so your own tail stops strangling you |
| 🪦 / 🗿 | during the shed gag: your frozen body / the head you bite to reattach |

## What makes it crazy

- **Adaptive chiptune** — a runtime square/triangle/noise synth whose tempo, layering, and
  distortion ramp with your speed and the rising *corruption*. Each takeover swaps to a fitting
  public-domain **classical** piece (boss doom, Für Elise in the shop, Pachelbel for the novel, Ode
  to Joy for the finale) — all played as note data through the same chip synth. Still zero audio files.
- **Juice** — screen shake, hitstop, particle bursts, neon bloom, motion trails, hue-cycling, screen
  warp, chromatic aberration, CRT scanlines — all escalating, calibrated medium-high.
- **Roguelite** — power-up elements, eat-3-of-a-kind synergies, portals, and a boss that turns the
  game into a bullet-hell.
- **A slop AI visual novel** — eat a 📖 and the game becomes a short branching dating-sim parody set
  at a predatory all-snake law firm, with AI-generated art and a recurring cast. The clichés play
  straight (senpai, rival-to-lover, the wholesome intern) — but the morality is *inverted*: the kind,
  honest choices are the lethal bad ends. Five novels; a wrong (kind) choice costs a life.
- **It actually goes somewhere** — spend score in the on-board 🏪 on power-ups *and* permanent
  upgrades that stick across every run (Dead-Cells style): vested extra lives and **the barber**, a
  ✂️ "haircut" that shears your tail as you grow so late-game length stays comfortable. Buy your way
  down the back-room floors, finish all five novels, and a real **ending** unlocks — reachable in
  roughly 20 minutes of total play. (No spoilers on what you become — earn it.)
- **📈 Stocks** — a cookie-clicker rally hidden in Snake: eat a 📈 to buy a "share," and every future
  bite this run scores more (a compounding multiplier). Ride it and a good run *snowballs* — the thing
  that makes the deep floors affordable. But this is a predatory firm: the market can **crash** and
  gut your portfolio (a MARGIN CALL). Greed, taxed.
- **Rare gags** — once in a long while your whole body just… falls apart. It freezes into a row of
  tombstones and your head pops off on its own. Crawl back and bite your still-living head (the 🗿,
  ringed in green) to pull yourself back together.
- **The slow reveal** — the first couple of runs play it straight (even the window title stays plain).
  Then the game starts *talking* (dark, cruel, depressive-funny), glitching, and faking your death.
  All illusions — it never touches a single real file beyond its own `save.dat`.
- **Forgiving on purpose** — press-to-start, 3 lives, gentle/decaying speed, a 1-in-3 "merciful run",
  early walls that wrap, and fake deaths that hand you back. The derangement *is* the mercy.

## A secret

There may also be a secret. ↑ ↑ ↓ ↓ ← → ← → B A

---

For the code architecture and build/test/packaging details, see [`../CLAUDE.md`](../CLAUDE.md).
