# KiscPort Engine

Repositorio público de KISC-LABS. El nombre es **KiscPort** (no "Kisport").

El artefacto del teléfono usa CLDC 1.1, MIDP 2.0 y JSR-184; lenguaje Java 1.3 y bytecode 45.3. El JDK moderno es
exclusivamente una herramienta del PC. No agregar bibliotecas Java SE ni implementaciones javax/java al JAR del
teléfono. Mantener la preverificación CLDC StackMap. Las APIs opcionales (M3G, MIDIControl, JSR-75, JSR-239,
Mascot Capsule v3) se cargan por nombre para que su ausencia sólo afecte a esa parte.

Construcción: `python3 scripts/build.py`. Pruebas: `python3 scripts/test.py`, `python3 scripts/smoke.py` y
`python3 scripts/smoke.py absent`. Probe: `python3 scripts/build_probe.py` y `python3 scripts/smoke_probe.py`.
Reproducibilidad: `python3 scripts/reproducibility.py`. No ejecutar a la vez dos construcciones que escriban en `dist/`.

Nunca versionar imágenes de disco, datos ni recursos de juegos comerciales, ni nombres o textos de juegos de
terceros. Los juegos concretos viven en sus propios repositorios, fuera de este. Mantener versiones y hashes de
herramientas. Distinguir siempre PC, emulador y teléfono: un resultado de FreeJ2ME no es una medida de hardware.
W995 es el teléfono base; W900i (M3G 1.0) se mantiene compatible.
