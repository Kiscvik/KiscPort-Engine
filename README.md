# KiscPort Engine

**English** · [Español](README.es.md)

**A free 3D engine for 2000s Java ME phones.** Made by KISC-LABS to bring 3D games to phones such as the Sony
Ericsson W995 and W900i, and open so anyone can use it in their own projects: new games, demos, homebrew, or ports
of games you have the right to port.

## What is included

| Part | What it is |
|---|---|
| **Engine** | Fixed 20 ms step loop (game logic does not depend on FPS), input with no stuck keys after a pause, stage loading in sections, optimised triangle strips, UTF-16 text, MIDI audio with a fallback, FPS and memory measurement, startup intro |
| **Demo** | An M3G cube you turn with the joystick, with intro and on-screen FPS. The starting point for your game |
| **[KiscPort Probe](docs/PROBE.md)** | An app that measures a phone: APIs, sound, triangles per frame, textures, memory. It saves a report and PNG screenshots on the phone |
| **Tools** | Reproducible build (byte-identical JAR), CLDC audit, tests and the FreeJ2ME-Plus emulator, all with pinned versions and hashes |

Everything in this repository is original. There are no disc images, data or assets from any commercial game.

## Phones

Any phone with **CLDC 1.1 + MIDP 2.0**. 3D needs **JSR-184 (M3G)**; without it the app still starts and says so.
Tested during development on the Sony Ericsson **W995** (base phone) and **W900i** (M3G 1.0). To find out what
another phone can do, install [KiscPort Probe](docs/PROBE.md) and send its report.

## Getting started

You need Python 3.9 or later. `bootstrap.py` downloads the rest: a JDK 17 used only on the PC, the ECJ compiler,
ProGuard, the compile APIs and FreeJ2ME-Plus. Every download is checked by SHA-256
(`tools/dependencies.lock.json`). The pinned JDK is for macOS arm64; on another system, set `KISCPORT_JDK` to an
installed JDK 17.

```sh
python3 scripts/bootstrap.py            # once
python3 scripts/build.py                # dist/KiscPortDemo.jar and .jad
python3 scripts/emulator.py             # open the demo in FreeJ2ME-Plus
python3 scripts/test.py                 # desktop tests and CLDC audit
python3 scripts/smoke.py                # demo in the emulator, with M3G
python3 scripts/smoke.py absent         # demo in the emulator, without M3G
python3 scripts/reproducibility.py      # two identical builds
python3 scripts/build_probe.py          # dist/probe/KiscPortProbe.jar and .jad
python3 scripts/smoke_probe.py          # Probe in the emulator
python3 scripts/mascot.py check LEVEL   # beta: does this level fit Mascot Capsule v3?
```

To install on the phone, copy the `.jar` and `.jad` (USB, Bluetooth or memory card) and open them from the
application manager.

## Making your own game

1. **Start from the demo.** `src/kiscport/demo/DemoCanvas.java` has the whole loop: input, 20 ms steps, drawing,
   pause. Replace `step()` with your game logic and `CubeScene` with your scene.
2. **3D goes in `kiscport.renderer`.** The audit requires M3G to be used only there, and the scene to be loaded by
   name (`Class.forName`), so a phone without M3G does not crash.
3. **Resources.** Files inside the JAR are read with `getResourceAsStream`. Stages in sections use the KPS format
   (`AssetSection`, folder in `AssetSection.ROOT`) and text uses KTT1 (`scripts/prepare_text.py`).
4. **Name and version** of your app: in `scripts/build.py` (`MIDlet-Name`, `MIDlet-Version`, `MIDlet-Vendor`).
5. **Startup intro.** `python3 scripts/build.py --intro off` removes it. To use your logo:
   `python3 scripts/prepare_intro.py your-logo.png --output-dir my-intro` (makes the 304, 208, 128 and 80 px sizes
   with their hashes), then `python3 scripts/build.py --intro-assets my-intro`.
6. **Measure on the phone, not in the emulator.** FreeJ2ME tests the logic; Probe tells you the real performance.

Phone rules the engine already follows and your code must keep: Java 1.3, no Java SE, CLDC preverification (done
by `build.py`) and optional APIs loaded by name. `scripts/test.py` checks them.

## Documentation

- [docs/ENGINE.md](docs/ENGINE.md): how the engine works, its formats, why M3G and Mascot, and what is still to
  come.
- [docs/PROBE.md](docs/PROBE.md): KiscPort Probe, how to use it and how to read the report.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): third-party tools and their licences.

## Contributing

**Probe reports from new phones** are especially welcome (model, firmware, `kiscport-probe.txt` and screenshots),
as are fixes and engine pieces. One rule with no exceptions: **no disc images, data or assets from commercial
games**, neither in the code nor in issues.

## Licence

Code and documentation are under the **MIT** licence ([LICENSE](LICENSE)): you can use, modify and distribute
them, also in commercial projects, as long as you keep the copyright notice.

The **KISC-LABS / KiscPort** name and logo (`assets/intro/`) identify their authors. You may keep the "Powered by
KiscPort Engine" intro in your game; if you publish something made with the engine, do not present it as an
official KISC-LABS product.

The tools that `bootstrap.py` downloads have their own licences and are not included in the phone JAR.

## Status

**0.8.0** (13 November 2026): first public release. Engine, demo and Probe 1.2.0 tested on PC and FreeJ2ME-Plus;
phone testing pending. What comes next is in [docs/ENGINE.md](docs/ENGINE.md#what-is-missing).

**0.9.0** (14 November 2026): **Mascot Capsule v3 in alpha and beta, for future study;** it does not change the
production renderer. In the demo, `#` switches the cube between M3G and Mascot inside the engine loop: it is the
base for trying a Mascot renderer, not a measurement (the cube has 12 triangles). In beta, to try Mascot with the
next game: `scripts/mascot.py` converts pages to 8-bit BMP and says whether a level fits Mascot, and
`MascotSection` draws KPS3 sections with Mascot, without M3G. M3G remains the production renderer. Tested on PC
and FreeJ2ME-Plus (whose Mascot is a reimplementation); phone testing pending. Why we study both APIs, and the
criteria for deciding, are in [docs/ENGINE.md](docs/ENGINE.md#mascot-capsule-v3-alpha).

**Probe 1.4.0** (14 November 2026): adds Mascot Capsule v3 (test 7) and a comparison with M3G that says which one
suits each phone (test 8). In the 3D tests only `*` stops a run: any other key keeps the screen on without ending
it. Tested on PC and FreeJ2ME-Plus; phone testing pending.
