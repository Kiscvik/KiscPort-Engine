# KiscPort Engine

Public KISC-LABS repository. The name is **KiscPort** (not "Kisport").

The phone artefact uses CLDC 1.1, MIDP 2.0 and JSR-184; Java 1.3 language and bytecode 45.3. The modern JDK is only
a PC tool. Do not add Java SE libraries or javax/java implementations to the phone JAR. Keep CLDC StackMap
preverification. Optional APIs (M3G, MIDIControl, JSR-75, JSR-239, Mascot Capsule v3) are loaded by name so that
their absence only affects that part.

Build: `python3 scripts/build.py`. Tests: `python3 scripts/test.py`, `python3 scripts/smoke.py` and
`python3 scripts/smoke.py absent`. Probe: `python3 scripts/build_probe.py` and `python3 scripts/smoke_probe.py`.
Reproducibility: `python3 scripts/reproducibility.py`. Never run two builds that write to `dist/` at the same time.

Never version disc images, data or assets from commercial games, nor names or texts of third-party games. Specific
games live in their own repositories, outside this one. Keep tool versions and hashes. Always distinguish PC,
emulator and phone: a FreeJ2ME result is not a hardware measurement. The W995 is the base phone; the W900i (M3G
1.0) stays compatible.

Documentation is in English; only the README exists in both languages (`README.md` in English, `README.es.md` in
Spanish), and the two must say the same thing. Probe's and the demo's on-screen text stay in Spanish.
