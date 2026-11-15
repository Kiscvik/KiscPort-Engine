# KiscPort Probe 1.4.0

KISC-LABS, 14 de noviembre de 2026. Un MIDlet pequeño y público que dice qué puede hacer un teléfono con
Java ME para un port de KiscPort: plataforma, APIs, audio, rendimiento M3G (y Mascot Capsule v3 si lo tiene) y memoria. Guarda un informe y
capturas PNG en el propio teléfono. No contiene nada de ningún juego: los sonidos son sintéticos
(`scripts/make_probe_sounds.py`) y las mallas y texturas se generan por código en el teléfono. Incluye el
antiguo diagnóstico MMAPI 1.0.0 como su parte de sonido.

## Construir

```sh
python3 scripts/make_probe_sounds.py   # sólo si cambian los sonidos de prueba (salida determinista)
python3 scripts/build_probe.py         # dist/probe/KiscPortProbe.jar / .jad (~84 KB)
python3 scripts/smoke_probe.py         # FreeJ2ME-Plus: prueba la lógica, no el teléfono
```

Se instala junto a cualquier juego (otro `MIDlet-Name`, no reemplaza nada). Mismas reglas que el motor:
CLDC 1.1, MIDP 2.0, Java 1.3, bytecode 45.3 y preverificación StackMap. M3G, Mascot Capsule v3, MIDIControl,
OpenGL ES y FileConnection se cargan por nombre: si el teléfono no los tiene, sólo falla esa parte y se dice por qué.

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
| 3 Gráficos 3D (M3G, Mascot) | Las pruebas de rendimiento, de una en una o todas; bajo cada una, su resultado. La 8 compara M3G con Mascot y da un veredicto; la 7 es Mascot Capsule v3 completa |
| 4 Sonido | Formato WAV (entrada 0) y las 9 pruebas MMAPI. Al terminar una, aparece la pregunta «¿Qué oyó?» con sus respuestas |
| 5 Memoria | Explica el riesgo y empieza con OK |
| 6 Informe y capturas | El informe completo; OK lo guarda como `kiscport-probe.txt` |
| 7 Registro | Todo lo ocurrido, con tiempos |
| 8 Ayuda | Teclas y cómo enviar los resultados |

Orden recomendado para un teléfono nuevo: **1** → **4** (responder lo que se oye) → **5** si se acepta que
puede cerrar la aplicación → **6** para guardar el informe final.

## Capturas

`#` guarda la pantalla actual como `kiscport-001.png`, `kiscport-002.png`… En la pantalla 3D del benchmark,
`0` guarda el siguiente cuadro como `kiscport-3d-001.png`… (`kiscport-mc3-001.png`… en la prueba Mascot) y
`*` (asterisco) detiene la prueba. **Las demás teclas no hacen nada:** sirven para que la pantalla no se apague
durante una prueba larga, sin cortarla. (Hasta 1.3.0 cualquier tecla la detenía, también la respuesta a la
pregunta de permiso al guardar una captura en el W900i.)
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
| 7 | Mascot Capsule v3: las mallas de la 1 como QUADS, los 768 triángulos como TRIANGLES y como QUADS, texturas de 16 a 256 px sobre 384 triángulos y el relleno de la 4. Sólo si el teléfono tiene `com.mascotcapsule.micro3d.v3`; si no, lo dice | Si la API propietaria de HI Corporation rinde más que M3G en ese teléfono |
| 8 | Las mismas cuatro cargas con M3G y con Mascot: 768 y 3.072 triángulos, textura de 128 px sobre 384 triángulos y la pantalla rellenada 4 veces | **Qué API conviene en este teléfono**: termina con un veredicto |
| 9 | 1 a 5 seguidas | Pasada completa (la de la Prueba rápida) |
| Memoria | Bloques de 64 KB reservados hasta 8 MB o hasta `OutOfMemoryError` | Cuánta memoria Java puede usar de verdad un port |

### Mascot Capsule v3 frente a M3G

La prueba 7 repite las de M3G con la misma malla, la misma superficie en pantalla, el mismo giro y la misma
medida, para leer ambas lado a lado (sus líneas empiezan por `MC3`). Un cuadro es borrar con `fillRect` + `bind`
+ `renderPrimitives` + `flush` + `release` + `flushGraphics`. Diferencias que van dentro de los números:

- `renderPrimitives` recibe los vértices sin compartir y como mucho 255 primitivas por llamada (la malla se
  parte en lotes, preparados antes de medir);
- un color por cara (en M3G, por vértice y suavizado), sin luz;
- texturas BMP de 8 bits con paleta, generadas en el teléfono (M3G: RGB), hasta 256 px;
- sin búfer de profundidad: la API ordena los polígonos;
- no hay prueba de subida de vértices: en Mascot los vértices viajan en cada llamada, así que su coste ya está
  en las demás.

El ángulo de vista se calcula para que la malla ocupe los mismos píxeles que en M3G (60° en vertical), y la
primera línea lo informa. Si una implementación aplica el ángulo de otra forma, la malla saldrá de otro tamaño:
la captura con `0` lo muestra. Sólo se usan las llamadas básicas de la API, para que un nombre que falte en
un teléfono no haga fallar la prueba entera; si un paso falla, dice `NO` y el motivo, y la serie sigue o se
detiene. No entra en la Prueba rápida.

### Veredicto: M3G o Mascot (prueba 8)

Cada carga se mide con las dos APIs, por **tiempo de cuadro completo** (no sólo «render»: en el W995, M3G deja
parte del trabajo para `flushGraphics`). Una API gana una carga si es al menos un 15 % más rápida. El
veredicto suma los tiempos de las cuatro cargas, como hacerlas una vez cada una:

```
CMP mallas 768 tri: M3G 36.2 ms, Mascot 20.4 ms -> Mascot 1.7x mas rapido
VEREDICTO para este telefono: Mascot Capsule v3, 1.9x mas rapido en total (gana 4 de 4)
```

Con menos de un 15 % de diferencia el veredicto es «empate: M3G, el estándar». Si al teléfono le falta una de
las dos APIs, gana la otra y se dice por qué. El veredicto aparece arriba de la lista de Gráficos, en el aviso
del pie y como `VEREDICTO 3D` al principio del Informe. Son mallas sintéticas: dicen qué API dibuja más rápido
en ese teléfono, no cuánto rinde un juego concreto.

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
| `MascotBench.java` | Las mismas pruebas con Mascot Capsule v3 (BMP de 8 bits generado en código) |
| `PngWriter.java` | PNG RGB fila a fila, CLDC puro (CRC-32, Adler-32, bloques deflate sin compresión) |
| `Bench.java`, `Info.java`, `Saver.java`, `MidiFx.java` | Interfaces de las partes opcionales, cargadas por nombre |
| `M3GInfo.java`, `GLInfo.java`, `MidiImpl.java`, `ReportFile.java` | M3G, OpenGL ES, MIDIControl y archivos (JSR-75) |
| `probe/stubs/` | Firmas para compilar MIDIControl, JSR-239, JSR-75 y las llamadas básicas de Mascot Capsule v3; nunca se empaquetan |
| `probe/res/` | Sonidos sintéticos con sus hashes en `sounds.json` |
| `tests/ProbeSmoke.java` | Prueba en FreeJ2ME de toda la navegación, sonido, rendimiento rápido, memoria, informe y PNG |

## Validación 1.4.0

PC y FreeJ2ME-Plus, 14 de noviembre de 2026: `build_probe.py` pasa la auditoría y `smoke_probe.py` pasa 104
comprobaciones: las de 1.3.0, la prueba 8 hasta su veredicto,
que también aparece en el Informe, y que en la pantalla 3D `*` detiene la prueba y otras teclas no. En el emulador los tiempos son de PC y el veredicto se marca como no válido.
**Sin prueba todavía en un teléfono.**

## Validación 1.3.0

PC y FreeJ2ME-Plus, 14 de noviembre de 2026: el paquete pasa la auditoría (además, sólo `MascotBench` hace
referencia a `com/mascotcapsule`) y `smoke_probe.py` pasa 98 comprobaciones: las de 1.2.0 y la prueba 7 en modo
rápido, con sus cuatro series y una captura pedida. En las capturas del emulador la malla Mascot ocupa lo mismo
que la de M3G, y la textura y el relleno se ven dibujados. Sin la API, la prueba lo dice antes de tomar la pantalla.
FreeJ2ME trae su propia reimplementación de Mascot en Java y no puede arrancar sin ella, así que el caso «sin
Mascot» se comprueba por construcción (carga por nombre, sólo una clase la usa), no ejecutándolo. **Sin prueba
todavía en un teléfono:** falta saber si el W995 y el W900i tienen Mascot y qué rinde. Los tiempos del emulador
no dicen nada del teléfono.

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

