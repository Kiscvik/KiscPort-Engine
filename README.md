# KiscPort Engine

**Motor 3D libre para teléfonos Java ME de los 2000.** Hecho por KISC-LABS para llevar juegos 3D a móviles como
el Sony Ericsson W995 o el W900i, y abierto para que cualquiera lo use en sus propios proyectos: juegos nuevos,
demos, homebrew o ports de juegos que tú tengas derecho a portar.

*English: a free (MIT) 3D engine for 2000s Java ME phones (CLDC 1.1, MIDP 2.0, JSR-184 M3G), with a reproducible
toolchain, an original demo and KiscPort Probe, a MIDlet that measures what a phone can do. Docs are in Spanish.*

## Qué incluye

| Parte | Qué es |
|---|---|
| **Motor** | Bucle con paso fijo de 20 ms (la lógica no depende del FPS), entrada sin teclas pegadas tras una pausa, carga de escenarios por tramos, tiras de triángulos optimizadas, textos UTF-16, audio MIDI con alternativa, medición de FPS y memoria, intro de arranque |
| **Demo** | Un cubo M3G que se gira con el joystick, con intro y FPS en pantalla. Es el punto de partida para tu juego |
| **[KiscPort Probe](docs/PROBE.md)** | Una aplicación que mide un teléfono: APIs, sonido, triángulos por cuadro, texturas, memoria. Guarda un informe y capturas PNG en el teléfono |
| **Herramientas** | Construcción reproducible (mismo JAR byte a byte), auditoría CLDC, pruebas y emulador FreeJ2ME-Plus, todo con versiones y hashes fijados |

Todo el contenido de este repositorio es original. No hay imágenes de disco, datos ni recursos de ningún juego
comercial.

## Teléfonos

Cualquier teléfono con **CLDC 1.1 + MIDP 2.0**. Para 3D hace falta **JSR-184 (M3G)**; sin él, la aplicación arranca
igual y lo indica. Probado durante el desarrollo en Sony Ericsson **W995** (teléfono base) y **W900i** (M3G 1.0).
Para saber qué da de sí otro teléfono, instala [KiscPort Probe](docs/PROBE.md) y envía su informe.

## Empezar

Necesitas Python 3.9 o superior. El resto lo descarga `bootstrap.py`: un JDK 17 que sólo se usa en el PC, el
compilador ECJ, ProGuard, las APIs de compilación y FreeJ2ME-Plus. Cada descarga se comprueba por SHA-256
(`tools/dependencies.lock.json`). El JDK fijado es para macOS arm64; en otro sistema, define `KISCPORT_JDK` con un
JDK 17 instalado.

```sh
python3 scripts/bootstrap.py            # una vez
python3 scripts/build.py                # dist/KiscPortDemo.jar y .jad
python3 scripts/emulator.py             # abrir la demo en FreeJ2ME-Plus
python3 scripts/test.py                 # pruebas de escritorio y auditoría CLDC
python3 scripts/smoke.py                # demo en el emulador, con M3G
python3 scripts/smoke.py absent         # demo en el emulador, sin M3G
python3 scripts/reproducibility.py      # dos construcciones idénticas
python3 scripts/build_probe.py          # dist/probe/KiscPortProbe.jar y .jad
python3 scripts/smoke_probe.py          # Probe en el emulador
python3 scripts/mascot.py check NIVEL   # beta: ¿cabe este nivel en Mascot Capsule v3?
```

Para instalar en el teléfono, copia el `.jar` y el `.jad` (por USB, Bluetooth o tarjeta) y ábrelos desde el
gestor de aplicaciones.

## Hacer tu propio juego

1. **Parte de la demo.** `src/kiscport/demo/DemoCanvas.java` tiene el bucle completo: entrada, pasos de 20 ms,
   dibujo, pausa. Cambia `step()` por la lógica de tu juego y `CubeScene` por tu escena.
2. **El 3D va en `kiscport.renderer`.** La auditoría exige que M3G sólo se use ahí, y que la escena se cargue por
   nombre (`Class.forName`), para que un teléfono sin M3G no se cierre.
3. **Recursos.** Los archivos dentro del JAR se leen con `getResourceAsStream`. Los escenarios por tramos usan el
   formato KPS (`AssetSection`, carpeta en `AssetSection.ROOT`) y los textos, KTT1 (`scripts/prepare_text.py`).
4. **Nombre y versión** de tu aplicación: en `scripts/build.py` (`MIDlet-Name`, `MIDlet-Version`, `MIDlet-Vendor`).
5. **Intro de arranque.** `python3 scripts/build.py --intro off` la quita. Para poner tu logo:
   `python3 scripts/prepare_intro.py tu-logo.png --output-dir mi-intro` (genera los tamaños 304, 208, 128 y 80 px
   con sus hashes) y luego `python3 scripts/build.py --intro-assets mi-intro`.
6. **Mide en el teléfono, no en el emulador.** FreeJ2ME prueba la lógica; el rendimiento real lo dice Probe.

Reglas del teléfono que el motor ya respeta y que tu código debe mantener: Java 1.3, nada de Java SE, preverificación
CLDC (la hace `build.py`) y APIs opcionales cargadas por nombre. `scripts/test.py` las comprueba.

## Documentación

- [docs/ENGINE.md](docs/ENGINE.md): cómo funciona el motor, sus formatos y qué falta por incorporar.
- [docs/PROBE.md](docs/PROBE.md): KiscPort Probe, cómo usarlo y cómo leer el informe.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): herramientas de terceros y sus licencias.

## Contribuir

Se agradecen sobre todo **informes de Probe de teléfonos nuevos** (modelo, firmware, `kiscport-probe.txt` y
capturas), correcciones y piezas del motor. Una regla sin excepciones: **no se aceptan imágenes de disco, datos ni
recursos de juegos comerciales**, ni en el código ni en los issues.

## Licencia

El código y la documentación están bajo licencia **MIT** ([LICENSE](LICENSE)): puedes usarlos, modificarlos y
distribuirlos, también en proyectos comerciales, conservando el aviso de copyright.

El nombre y el logotipo **KISC-LABS / KiscPort** (`assets/intro/`) identifican a sus autores. Puedes mantener la
intro «Powered by KiscPort Engine» en tu juego; si publicas algo hecho con el motor, no lo presentes como un
producto oficial de KISC-LABS.

Las herramientas que descarga `bootstrap.py` tienen sus propias licencias y no se incluyen en el JAR del teléfono.

## Estado

**0.8.0** (13 de noviembre de 2026): primera versión pública. Motor, demo y Probe 1.2.0 probados en PC y
FreeJ2ME-Plus; pendiente su prueba en teléfonos. Lo siguiente está en [docs/ENGINE.md](docs/ENGINE.md#qué-falta).

**0.9.0** (14 de noviembre de 2026): **Mascot Capsule v3 en alpha y beta, para estudio futuro;** no cambia el
renderer de producción. En la demo, `#` cambia el cubo entre M3G y Mascot dentro del bucle del motor: es la base para probar un renderer Mascot, no una medida (el cubo tiene 12
triángulos). Y en beta, para probar Mascot con el siguiente juego: `scripts/mascot.py` convierte páginas a BMP de
8 bits y dice si un nivel cabe en Mascot, y `MascotSection` dibuja tramos KPS3 con Mascot sin M3G. M3G sigue siendo
el renderer de producción. Probado en PC y FreeJ2ME-Plus (su Mascot es una reimplementación); pendiente en
teléfonos. Detalles y criterio para decidir en [docs/ENGINE.md](docs/ENGINE.md#mascot-capsule-v3-alpha).

**Probe 1.4.0** (14 de noviembre de 2026): añade Mascot Capsule v3 (prueba 7), una comparación con M3G que dice
cuál conviene en cada teléfono (prueba 8), y en las pruebas 3D sólo `*` las detiene: otra tecla mantiene la
pantalla encendida sin cortarlas. Probada en PC y
FreeJ2ME-Plus; pendiente en teléfonos.
