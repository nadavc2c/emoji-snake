# Visual-novel art - provenance

The images under `vn/characters/` and `vn/bg/` are **AI-generated** ("slop" - which is the joke) for
the in-game visual-novel interlude, produced locally with the project author's own **SlopMachine**
(`slop`) CLI - no third-party stock art or external services.

- **Character sprites** - FLUX.2-klein (`black-forest-labs/FLUX.2-klein-4B`, Apache-2.0) via the
  `vn-corpo` style preset, fixed seed per character for a consistent recurring cast; backgrounds
  removed to transparent RGBA with **BiRefNet** (`ZhengPeng7/BiRefNet`, MIT) via `slop matte`. SFW.
- **Backgrounds** - FLUX.2-klein (`black-forest-labs/FLUX.2-klein-4B`, Apache-2.0).
- **`bg/ending.png`** - the CEO-snake super-partner finale (the "become the firm" true ending),
  FLUX.2-klein (`black-forest-labs/FLUX.2-klein-4B`, Apache-2.0).
- **`characters/founder_neutral.png` + `bg/archive.png`** - the hidden "chicken" novel (Employee One /
  The Founding File), opened by eating a 📖 as a 🐔 basilisk. Character: FLUX.2-klein via `vn-corpo`,
  matted to transparent RGBA with **BiRefNet** (`ZhengPeng7/BiRefNet`, MIT) via `slop matte`. Archive
  background: FLUX.2-klein (no style preset, so it stays people-free). Generated 2026-07-04.

Generated 2026-06-30 (and 2026-07-04) on current-best models. Distinct from the board tiles, which
are OpenMoji (see `../ATTRIBUTION.md`).
