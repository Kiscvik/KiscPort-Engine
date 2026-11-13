# KiscPort Probe 1.2.0

KISC-LABS, 13 de noviembre de 2026. Un MIDlet pequeño y público que dice qué puede hacer un teléfono con
Java ME para un port de KiscPort: plataforma, APIs, audio, rendimiento M3G y memoria. Guarda un informe y
capturas PNG en el propio teléfono. No contiene nada de ningún juego: los sonidos son sintéticos
(`scripts/make_probe_sounds.py`) y las mallas y texturas se generan por código en el teléfono. Incluye el
antiguo diagnóstico MMAPI 1.0.0 como su parte de sonido.

## Construir

```sh
python3 scripts/make_probe_sounds.py   # sólo si cambian los sonidos de prueba (salida determinista)
python3 scripts/build_probe.py         # dist/probe/KiscPortProbe.jar / .jad (~74 KB)
python3 scripts/smoke_probe.py         # FreeJ2ME-Plus: prueba la lógica, no el teléfono
```

Se instala junto a cualquier juego (otro `MIDlet-Name`, no reemplaza nada). Mismas reglas que el motor:
CLDC 1.1, MIDP 2.0, Java 1.3, bytecode 45.3 y preverificación StackMap. M3G, MIDIControl, OpenGL ES y
FileConnection se cargan por nombre: si el teléfono no los tiene, sólo falla esa parte y se dice por qué.

## Usarlo

| Tecla | Acción |
|---|---|
| Joystick arriba/abajo | Mover la selección o desplazar el texto |
| OK (centro del joystick) | Elegir |
| Izquierda o «Atrás» | Volver al menú |
| Números | Atajo: el número de cada entrada |
| `#` (o «Captura») | Captura PNG de la pantalla actual |
| `*` (o «Parar sonido») | Para todo el sonido |

El pie de cada pantalla recuerda sus teclas y muestra los avisos (captura guardada, informe guardado…).

| Menú | Qué hace |
|---|---|
| 1 Prueba rápida (recomendada) | Información + gráficos 1–5 + informe guardado, sin tocar nada, unos 2 minutos. Termina en el informe |
| 2 Información del teléfono | Propiedades, APIs presentes, M3G, OpenGL ES, controles y formatos de audio, en una sola lista |
| 3 Gráficos 3D (M3G) | Las pruebas de rendimiento, de una en una o todas; bajo cada una, su resultado |
| 4 Sonido | Formato WAV (entrada 0) y las 9 pruebas MMAPI. Al terminar una, aparece la pregunta «¿Qué oyó?» con sus respuestas |
| 5 Memoria | Explica el riesgo y empieza con OK |
| 6 Informe y capturas | El informe completo; OK lo guarda como `kiscport-probe.txt` |
| 7 Registro | Todo lo ocurrido, con tiempos |
| 8 Ayuda | Teclas y cómo enviar los resultados |

Orden recomendado para un teléfono nuevo: **1** → **4** (responder lo que se oye) → **5** si se acepta que
puede cerrar la aplicación → **6** para guardar el informe final.

## Capturas

`#` guarda la pantalla actual como `kiscport-001.png`, `kiscport-002.png`… En la pantalla 3D del benchmark,
`0` guarda el siguiente cuadro como `kiscport-3d-001.png`… (cualquier otra tecla detiene el benchmark).
Los archivos van a la primera raíz de archivos que los acepte (tarjeta o memoria del teléfono); el teléfono
puede pedir permiso, porque el MIDlet no está firmado.

- **Sólo de Probe.** MIDP 2.0 no permite leer la pantalla de otras aplicaciones. Para capturar el juego hay
  que añadir el mismo código al juego (`PngWriter` + dibujar el cuadro en una `Image`).
- **Cómo se hace.** La pantalla se vuelve a dibujar en una `Image` mutable del mismo tamaño; `PngWriter` la lee
  fila a fila con `getRGB` y escribe un PNG RGB sin compresión (~230 KB para 240×320). Además de la imagen,
  sólo ocupa una fila en memoria.
- **El cuadro 3D capturado no se mide.** Dibujar en una `Image` puede seguir otro camino en el driver que
  dibujar en pantalla, así que se hace aparte y la ventana de medida se desplaza lo que tardó.
- La imagen mutable cuesta unos 150–300 KB mientras dura la captura; en un teléfono con poca memoria la
  captura puede fallar, y entonces se dice en el pie y en el Registro.

## Pruebas de rendimiento

Cada paso dibuja durante 2 s tras 0,5 s de calentamiento. Un cuadro es `bindTarget` + borrar + dibujar +
`releaseTarget` + `flushGraphics`, medido con el reloj de milisegundos. Se informa FPS, ms medios por cuadro,
la parte de render (sin el volcado a pantalla) y el cuadro más lento. Una serie se detiene si un paso supera
500 ms por cuadro, y ningún paso dura más de 20 s. La pantalla 3D muestra la prueba y «Paso N de M».

| Nº | Prueba | Qué responde |
|---|---|---|
| 1 | Mallas de 12, 96, 192, 384, 768, 1.536 y 3.072 triángulos, misma superficie en pantalla, envío QUAD | Cuántos triángulos por cuadro aguanta el teléfono |
| 2 | Los mismos 768 triángulos enviados de tres formas: una tira por triángulo, por cuadrilátero (QUAD, la del motor) y por fila | Cuánto cuesta cada tira al driver |
| 3 | Texturas de cuadros de 16 a 512 px (hasta `maxTextureDimension`) sobre 384 triángulos | Tiempo de creación, memoria usada y coste al dibujar; dónde falla |
| 4 | Un cuadrado quieto, más grande que la pantalla, dibujado 1, 2, 4 y 8 veces sin prueba de profundidad | El coste de los píxeles solos (sobredibujado) |
| 5 | 425 y 1.617 vértices reescritos en cada cuadro frente a la misma malla quieta | El coste del skinning en CPU de un personaje animado |
| 9 | 1 a 5 seguidas | Pasada completa (la de la Prueba rápida) |
| Memoria | Bloques de 64 KB reservados hasta 8 MB o hasta `OutOfMemoryError` | Cuánta memoria Java puede usar de verdad un port |

La prueba de memoria puede cerrar la aplicación en algunos teléfonos; por eso tiene su propia pantalla con
aviso y no entra en la Prueba rápida. Si se cierra, el último valor del Registro es el límite aproximado. En
estos teléfonos el heap Java crece bajo demanda: `freeMemory` al inicio no dice cuánto cabe, el total
reservado sí.

## Informe

Una línea por dato: identidad de Probe, plataforma y propiedades conocidas, memoria en ese momento,
propiedades de M3G, APIs presentes, resultados de sonido (API y lo oído, por separado), resultados de
rendimiento y capturas guardadas. Si se usaron fases rápidas (sólo el emulador) lo avisa.

Para enviar resultados: `kiscport-probe.txt` y las capturas, en un issue de GitHub con el modelo y el
firmware. Los datos alimentan el perfil de dispositivo ([ENGINE](ENGINE.md)): memoria
utilizable, triángulos por cuadro, textura segura, coste de tiras, relleno y skinning, formatos de audio y
mezcla. Cada dato es una **medida en ese teléfono**; Probe mide el driver aislado, no la carga de tramos, la
animación ni el HUD de un juego.

## Código

| Archivo | Papel |
|---|---|
| `probe/src/kiscport/probe/KiscPortProbe.java` | MIDlet: ciclo de vida, versión |
| `ProbeCanvas.java` | Menú y pantallas, teclas, hilo de trabajo, sonido, memoria, informe y capturas 2D |
| `M3GBench.java` | Benchmark M3G (`GameCanvas` propio mientras mide), progreso y captura 3D |
| `PngWriter.java` | PNG RGB fila a fila, CLDC puro (CRC-32, Adler-32, bloques deflate sin compresión) |
| `Bench.java`, `Info.java`, `Saver.java`, `MidiFx.java` | Interfaces de las partes opcionales, cargadas por nombre |
| `M3GInfo.java`, `GLInfo.java`, `MidiImpl.java`, `ReportFile.java` | M3G, OpenGL ES, MIDIControl y archivos (JSR-75) |
| `probe/stubs/` | Firmas para compilar MIDIControl, JSR-239 y JSR-75; nunca se empaquetan |
| `probe/res/` | Sonidos sintéticos con sus hashes en `sounds.json` |
| `tests/ProbeSmoke.java` | Prueba en FreeJ2ME de toda la navegación, sonido, rendimiento rápido, memoria, informe y PNG |

## Validación 1.2.0

PC y FreeJ2ME-Plus, 13 de noviembre de 2026: el paquete pasa la auditoría (bytecode 45.3, StackMap, sólo
`kiscport/probe/` y `snd/`) y `smoke_probe.py` pasa 94 comprobaciones:
- navegación por joystick, OK, atajos y vuelta al menú;
- las 9 pruebas de sonido con su pantalla de pregunta;
- las 5 de rendimiento en modo rápido, con captura 3D pedida;
- la memoria;
- la Prueba rápida, que termina en el informe;
- el PNG de dos pantallas reales, leído de vuelta (240×320 y con contenido).

FreeJ2ME convierte la raíz de archivos en una ruta desde `/` del disco, así que ahí no se puede guardar:
la prueba comprueba que Probe lo dice, y valida el codificador PNG aparte. **Sin prueba todavía en el W995
ni en el W900i**, donde se comprobará el guardado real (permiso, tarjeta o memoria del teléfono).

