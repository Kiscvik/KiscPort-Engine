# KiscPort Engine 0.9.0

KISC-LABS, 14 November 2026. How the engine works, what this repository contains and what is still to come.

## Platform

- CLDC 1.1, MIDP 2.0 and JSR-184 (M3G); Java 1.3 source, bytecode 45.3, CLDC StackMap preverification.
- JDK 17 only runs PC tools. `scripts/test.py` deliberately rejects a build that uses Java SE.
- No mandatory proprietary APIs. Mascot Capsule v3 is only an optional **alpha/beta** renderer (see "Mascot
  Capsule v3 (alpha)"), loaded by name. Differences between phones are detected by capability
  (`microedition.m3g.version`, classes present, MMAPI answers), never by model name.
- Base phone: Sony Ericsson W995. The W900i (M3G 1.0) stays compatible.

## Code

| Class | Role |
|---|---|
| `platform/KiscPortMIDlet` | Lifecycle: `startApp`, `pauseApp`, `destroyApp`; backlight only to cancel |
| `platform/LoopPacer` | At least 1 ms of rest per frame so the phone can handle keys and lifecycle |
| `platform/BacklightControl`, `BacklightTarget` | Backlight policy (disabled: it flickered on the W900i) |
| `input/InputState` | Held keys and presses accumulated between frames; `clear()` on pause or hide |
| `diagnostics/FrameStats`, `Hud` | FPS, work time and memory, with no allocation while drawing |
| `resources/TextTable` | KTT1 text reader (up to 32 × 256 UTF-16 characters) |
| `ui/StartupIntro` | KISC-LABS intro scaled on the PC, a single decoded image |
| `audio/MidiOut`, `MidiControlOut` | MIDI notes through `MIDIControl`, loaded by name; `playTone` if it is missing |
| `renderer/Strips` | Turns triangle lists into strips: one per quad (QUAD), one per mesh, or none |
| `renderer/AssetSection` | Reads KPS1/KPS3 stage sections and their texture pages from `AssetSection.ROOT` |
| `renderer/CubeScene` | The demo's M3G scene |
| `renderer/MascotCubeScene` | **Alpha**: the same cube with Mascot Capsule v3 (`#` in the demo) |
| `renderer/MascotSection` | **Beta**: a KPS3 section drawn with Mascot, without M3G |
| `demo/DemoCanvas`, `DemoScene` | The demo: loop, intro, input, pause, text, FPS and renderer switch |

Rules checked by `scripts/audit.py`: M3G only in `kiscport/renderer/`, MMAPI only in `kiscport/audio/`,
`MIDIControl` only in `MidiControlOut`, and Mascot only in `MascotCubeScene` and `MascotSection`, which may not use
M3G. That way a phone without those APIs still starts.

## Mascot Capsule v3 (alpha)

> **Mascot beta for future study.** Nothing in this section changes the production renderer (M3G) or current
> games: it is material for evaluating Mascot in a future game.

### Why M3G, and why Mascot is being studied

Many Sony Ericsson phones ship two 3D APIs: the standard **M3G** (JSR-184) and **Mascot Capsule Micro3D v3**, HI
Corporation's proprietary API. Mascot is often said to be faster and closer to the PlayStation look (paletted
textures, sorted polygons, the "wobbly" polygons). Instead of answering from memory, KiscPort Probe 1.3.0 drew the
same synthetic scenes with both APIs (tests 7 and 9: same mesh, same screen area, same turn, same measuring
window) on the project's two phones. Frames per second of the whole frame, reported by the phones' owner from
photos of the Probe report:

| Load | W995 M3G | W995 Mascot | W900i M3G | W900i Mascot |
| --- | ---: | ---: | ---: | ---: |
| 768 triangles | 27.6 | 49.0 | 52.9 | 29.4 |
| 3,072 triangles | 10.7 | 21.4 | 22.4 | 10.9 |
| 384 triangles, 16 px texture | 30.6 | 54.6 | 62.8 | 33.6 |
| 384 triangles, 256 px texture | 25.9 | 50.0 | 62.5 | not measured |
| Screen filled 8 times | 7.7 | 21.5 | 51.0 | not measured |

**The faster API depends on the phone.** Mascot was about 2× faster than M3G on the W995 and about 2× slower on
the W900i. A likely reason, suggested by a member of the community and not verified by us: the W900i has an
NVIDIA GoForce 4800 GPU that accelerates M3G, which Mascot does not use. It fits the data: on the W900i, M3G fills
a full-screen layer in about 1 ms, against about 14 ms on the W995. Which phone is which still has to be
confirmed with Probe's `platform=` line and test 8, which measures both APIs and gives a verdict on each phone.

**Why production stays on M3G.** Speed is not the only requirement. The first experiment demands pixel-exact
fidelity with its M3G build, and its converted assets use four things Mascot v3 does not offer:

| The content uses | Mascot Micro3D v3 offers |
| --- | --- |
| Texture multiplied by each vertex's colour (Gouraud-shaded textured polygons) | One colour per face, or its own lighting |
| Partial alpha (for example 64 and 102 out of 255) | Opaque, 50 %, add or subtract, per polygon |
| Texture pages with more than 256 colours (up to 1,108) | 8-bit indexed BMP: 256 colours per texture |
| Per-pixel depth | Polygon sorting, no depth buffer |

For a new game whose art accepts those limits, Mascot is a valid choice, and on phones like the W995 it may be the
faster one. That is why the engine keeps M3G for production and adds Mascot as an alpha and beta to study.

### The demo alpha

Before deciding whether the engine should have a Mascot renderer, the demo has an alpha scene. It is the
groundwork: switching API inside the engine loop, the build and the audit. The cube has only 12 triangles, like
Probe's GEO 12 row, so it decides nothing by itself; the data for deciding will come from drawing a stage section
with Mascot (see below).

- **How to test it.** In the demo, `#` switches the cube between M3G and Mascot. The top right shows
  `M3G  # Mascot` or `Mascot (alpha)  # M3G`. Statistics restart on every switch.
- **What to look at.** The loop is capped at 30 FPS, so FPS barely tells the APIs apart: compare `ms` (work per
  frame) and `max ms`. Note both with each API on each phone, and check that the cube has the same size, colours
  and turn as with M3G.
- **What it does.** The same cube as `CubeScene`: six one-colour faces sent as six `QUADS` in a single
  `renderPrimitives` call, with a `fillRect` clear, `bind`, `flush` and `release`: the calls Probe already ran on
  both phones. Mascot has no depth buffer: it sorts polygons. Its camera looks down +z with y pointing down the
  screen; the scene turns the view 180° about x so the cube looks the same as in M3G, without mirroring.
- **If Mascot is missing**, the demo says "Mascot no disponible" and keeps M3G. If M3G is missing, `#` still shows
  the cube with Mascot (checked in the emulator without M3G).
- **Evidence.** Tested on PC and in FreeJ2ME-Plus, whose Mascot is a Java reimplementation: that says nothing about
  speed or looks on a phone. No phone run yet. That the class is not linked when the API is missing is checked by
  the static audit, not in the emulator, which cannot start without Mascot.

**When moving would pay off.** A full Mascot renderer only makes sense if, on the base phone (W995), with engine
scenes and not just the cube, it clearly wins in `ms` and the content accepts its limits: one colour per face or
its own lighting, 8-bit BMP textures (256 colours) up to 256 px, 50 %, add or subtract blending, and polygon
sorting instead of per-pixel depth. M3G remains the production renderer and the compatible path for the W900i.
Next step if the cube confirms the advantage: move a stage section (`AssetSection`) to Mascot and measure it with
Probe on both phones.

### Beta tools for the next game

Three pieces to try Mascot with real content, without proprietary formats: they use the same KPS3 sections as
`AssetSection` and nothing from HI Corporation. There are no `.mbac` or `.mtra` exporters and no dependency on its
SDK.

1. **Checker:** `python3 scripts/mascot.py check level_folder`. Reads `chunkN.kps` and `stage_N.png` and gives a
   verdict (`CABE` = fits, `CABE CON PERDIDAS` = fits with losses, or `NO CABE` = does not fit). It separates the
   API's limits from what goes beyond what was measured on the phones. Per section it counts:
   - triangles and `renderPrimitives` calls (at most 255 per call);
   - expanded memory (64 bytes per triangle);
   - triangles whose vertex colour is lost: Mascot does not multiply the texture by the colour, so modulation and
     Gouraud shading are lost;
   - texture coordinates outside the page.

   Per page: colours, partial alpha and size.
2. **Texture converter:** `python3 scripts/mascot.py texture page.png page.bmp`. Writes an 8-bit BMP in the layout
   Probe already used on both phones. With 256 colours or fewer the palette is exact; with more, a deterministic
   reduction reports the RMS error.
   - Alpha below 128 (M3G's threshold) becomes a *colour key*: palette entry 0 is magenta FF00FF, and only pages
     that need it have one.
   - Partial alpha is reported as a loss.
   - More than 256 px does not fit.
3. **Section renderer:** `kiscport.renderer.MascotSection`. Draws a KPS3 section with `stage_N.bmp` pages.
   - It does not use `AssetSection` or M3G (the audit checks this), so it works on a phone without JSR-184. It is
     loaded by name and its public methods use only integers and `Graphics`.
   - Use: `loadPages()` per level; `new MascotSection(n)` per section; every frame `view(...)`, `begin(g)`,
     `draw()` for every visible section and `end(g)`. Mascot sorts polygons within one `begin`/`end`, so all
     sections of a frame go together.
   - No visibility intervals or step-by-step loading yet: the constructor reads the whole section.

**What is verified.** On PC, the converter and the checker. In FreeJ2ME-Plus, whose Mascot is a
reimplementation, with a synthetic level:
- `MascotSection` reads the same triangles as `AssetSection`;
- the texture is not flipped;
- the colour key shows the background;
- a red vertex does not tint the texture (the expected loss).

On the first experiment's sections (not included here), the checker flagged two things to look at on the phone:
- coordinates up to ±12284, while the measured range reaches ±2600;
- between 86 and 432 triangles per section with a vertex colour that Mascot does not apply.

**What to check on the phone (W995 and W900i) with the next game:**
- that the colour key (`PATTR_COLORKEY` = 0x10, value taken from FreeJ2ME) really cuts out;
- whether `renderPrimitives` draws both faces (FreeJ2ME does not cull back faces);
- clipping of polygons that cross the near plane;
- precision with large coordinates;
- `ms` with loads above 768 and 3072 triangles;
- pages that are not square;
- the constructor's loading time and real memory against the estimate.

## How a frame runs

```
KiscPortMIDlet.startApp → DemoCanvas.run
  ┌─ input: InputState.consume() (held + presses since the last frame)
  ├─ simulation: elapsed time in whole 20 ms steps (at most 250 ms per frame)
  ├─ drawing: M3G scene + 2D layer (FPS, text)
  ├─ flushGraphics
  └─ LoopPacer (≥ 1 ms of rest)
```

The simulation advances in fixed steps, so FPS does not change the result: the demo turns the same at 10 as at
30 FPS, and tests can count steps. Pausing or hiding the app clears the input, so no key stays stuck on return.

## Formats

All are big-endian, start with a 4-byte magic and a version, and the reader rejects unknown variants.

| Format | Content | Reader in this repository |
|---|---|---|
| KPS1 / KPS3 | Mesh with UVs and colours; KPS3 = stage section in parts with shared texture pages | `AssetSection` (M3G), `MascotSection` (Mascot, beta) |
| KTT1 | UTF-16 text | `TextTable` (encoder: `scripts/prepare_text.py`) |
| KPV1 | Per triangle, in which camera intervals it can be seen | To be added |
| KPW2 | World: path, collisions, objects and movers | To be added |
| KPO1, KPC1, KPM1, KPU1 | Object catalogue, skinned character, lit model, 2D pieces | To be added |

The pending formats exist and are tested in the first experiment, but their readers still mix in that game's
rules. They will come here once they are separated (see "What is missing").

## Memory

- Geometry in QUAD strips (`Strips`): far fewer driver calls than one strip per triangle.
- Stage sections read in steps (`AssetSection`), spreading the load over several frames.
- **To be added** (they exist in the first experiment, mixed with its game): the manager that keeps two sections
  resident and prepares the next one, and visibility precomputed on the PC (KPV1).
- The garbage collector is not forced every frame. Before changing a memory policy, measure on the phone with
  Probe.
- **W900i lesson (M3G 1.0), November 2026.** In the first experiment, when one object set was replaced by
  another, building the next one threw `OutOfMemoryError` and the session ended. The fix will be applied to the
  manager when it comes here:
  1. Drop the references to the previous set before building the next one (normal path, unchanged).
  2. Only if that build fails: abandon the pending preload and the optional per-interval views of the resident
     sections (`AssetSection.views`; without them the section is drawn whole), request a collection and **retry
     once**. If it fails again, stop with a message that names the phase (set, section, drawing).

  It is a recovery path triggered by the failure, not by the phone model, and it does not change the rule of not
  collecting every frame. It can cause a short pause when switching sets. Scope of the evidence: the phone's owner
  got past the point that used to fail on a W900i. There was no long session or FPS measurement, no counter
  confirms that the recovery ran, FreeJ2ME does not reproduce the W900i's M3G memory, and that build was not
  retested on the W995.

## Build, tests and evidence

| Command | What it does | Validates on |
|---|---|---|
| `scripts/bootstrap.py` | Downloads tools pinned by SHA-256 and prepares the emulator | PC |
| `scripts/prepare_intro.py`, `make_intro_sound.py` | Recipe for the brand assets (`assets/intro/`); not rerun for 0.8.0, its outputs are versioned with hashes | PC |
| `scripts/build.py` | Demo JAR; checks metadata, StackMap and dependencies | PC |
| `scripts/test.py` | FrameStats, backlight, strips, text, CLDC audit, negative compile, Mascot tools | PC |
| `scripts/smoke.py` (`absent`) | The demo with and without M3G, the Mascot alpha cube (`#`) and, without `absent`, `MascotSection` | Emulator |
| `scripts/mascot.py` (`texture`, `check`) | **Beta**: pages to 8-bit BMP, and whether a level fits Mascot | PC |
| `scripts/reproducibility.py` | Two identical builds | PC |
| `scripts/build_probe.py`, `smoke_probe.py` | KiscPort Probe | PC / emulator |
| Probe on the phone | Real capabilities and performance | **Phone** |

## What is missing

1. **Separate and bring in** the rest of the first experiment's engine: KPW2 world reader, stage renderer with
   visibility, skinned characters (KPC), object catalogue by zones, and audio management. Each piece comes here
   only without any game's rules or names. The section and object-catalogue manager must come with the
   `OutOfMemoryError` recovery and the per-phase messages described in "Memory".
2. **Device profiles** built from Probe reports: usable memory, triangles per frame, safe texture size, and the
   cost of strips, fill and skinning. Probe measures the Java heap today; M3G memory still has to be measured by
   creating and dropping meshes and textures as when switching sections or sets, which is what failed on the
   W900i.
3. **Feasibility checker**: match what a piece of content needs against a profile and say whether it fits and
   within which limits. (`scripts/mascot.py check` is a first, Mascot-only beta of this idea.)
4. **Importers for open formats**: glTF to KPC/KPM/KPO, PNG to atlas, JSON level to KPW2 and KPV1.
