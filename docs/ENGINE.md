# KiscPort Engine 0.9.0

KISC-LABS, 14 de noviembre de 2026. Cómo funciona el motor, qué contiene este repositorio y qué falta.

## Plataforma

- CLDC 1.1, MIDP 2.0 y JSR-184 (M3G); fuente Java 1.3, bytecode 45.3, preverificación CLDC StackMap.
- El JDK 17 sólo ejecuta herramientas del PC. `scripts/test.py` rechaza a propósito una compilación que use Java SE.
- Sin APIs propietarias obligatorias. Mascot Capsule v3 entra sólo como renderer **alpha** opcional de la demo
  (ver «Mascot Capsule v3 (alpha)»), cargado por nombre. Las diferencias entre teléfonos se detectan por capacidad (`microedition.m3g.version`,
  clases presentes, respuesta de MMAPI), nunca por el nombre del modelo.
- Teléfono base: Sony Ericsson W995. W900i (M3G 1.0) se mantiene compatible.

## Código

| Clase | Papel |
|---|---|
| `platform/KiscPortMIDlet` | Ciclo de vida: `startApp`, `pauseApp`, `destroyApp`; luz de pantalla sólo para cancelar |
| `platform/LoopPacer` | Descanso mínimo de 1 ms por cuadro para que el teléfono atienda teclas y ciclo de vida |
| `platform/BacklightControl`, `BacklightTarget` | Política de luz (desactivada: en el W900i parpadeaba) |
| `input/InputState` | Teclas mantenidas y pulsaciones acumuladas entre cuadros; `clear()` al pausar u ocultar |
| `diagnostics/FrameStats`, `Hud` | FPS, tiempo de trabajo y memoria, sin reservar memoria al dibujar |
| `resources/TextTable` | Lector KTT1 de textos (hasta 32 × 256 caracteres UTF-16) |
| `ui/StartupIntro` | Intro KISC-LABS escalada en el PC, una sola imagen decodificada |
| `audio/MidiOut`, `MidiControlOut` | Notas MIDI por `MIDIControl`, cargado por nombre; si falta, `playTone` |
| `renderer/Strips` | Convierte listas de triángulos en tiras: una por cuadrilátero (QUAD), una por malla o ninguna |
| `renderer/AssetSection` | Lee tramos de escenario KPS1/KPS3 y sus páginas de textura desde `AssetSection.ROOT` |
| `renderer/CubeScene` | Escena M3G de la demo |
| `renderer/MascotCubeScene` | **Alpha**: el mismo cubo con Mascot Capsule v3 (`#` en la demo) |
| `renderer/MascotSection` | **Beta**: un tramo KPS3 dibujado con Mascot, sin M3G |
| `demo/DemoCanvas`, `DemoScene` | La demo: bucle, intro, entrada, pausa, textos, FPS y cambio de renderer |

Reglas que comprueba `scripts/audit.py`: M3G sólo en `kiscport/renderer/`, MMAPI sólo en `kiscport/audio/`,
`MIDIControl` sólo en `MidiControlOut` y Mascot sólo en `MascotCubeScene` y `MascotSection`, que no pueden usar M3G. Así un teléfono sin esas APIs sigue
arrancando.

## Mascot Capsule v3 (alpha)

Con Probe 1.3.0 (pruebas 7 y 9, mallas sintéticas), el propietario de los teléfonos informó, con fotos del
Informe, que la API más rápida depende del teléfono: Mascot fue unas 2 veces más rápida que M3G en el W995 y unas
2 veces más lenta en el W900i. Falta confirmar qué teléfono es cada uno con la línea `platform=` y la prueba 8.
Antes de decidir si el motor debe tener un renderer Mascot, la demo trae una escena alpha. Es la base: el cambio
de API dentro del bucle del motor, la construcción y la auditoría. El cubo tiene sólo 12 triángulos, como la fila
GEO 12 de Probe, así que no decide nada por sí solo: los datos para decidir vendrán de pasar un tramo de
escenario a Mascot (ver abajo).

- **Cómo se prueba.** En la demo, `#` cambia el cubo entre M3G y Mascot. Arriba a la derecha se ve
  `M3G  # Mascot` o `Mascot (alpha)  # M3G`. Al cambiar, las estadísticas vuelven a empezar.
- **Qué mirar.** El bucle se limita a 30 FPS, así que el FPS casi no distingue las APIs: la cifra que compara es
  `ms` (trabajo por cuadro) y `max ms`. Anotar las dos con cada API en cada teléfono, y confirmar que el cubo
  tiene el mismo tamaño, colores y giro que con M3G.
- **Qué hace.** El mismo cubo que `CubeScene`: seis caras de un color, enviadas como seis `QUADS` en una sola
  llamada `renderPrimitives`, con borrado por `fillRect`, `bind`, `flush` y `release`, las llamadas que Probe ya
  usó en los dos teléfonos. Mascot no tiene búfer de profundidad: ordena los polígonos. Su cámara mira hacia +z
  con y hacia abajo; la escena gira la vista 180° sobre x para que el cubo salga igual que en M3G, sin reflejo.
- **Si falta Mascot**, la demo dice «Mascot no disponible» y sigue con M3G. Si falta M3G, `#` sigue mostrando el
  cubo con Mascot (comprobado en el emulador sin M3G).
- **Evidencia.** Probado en PC y en FreeJ2ME-Plus, cuya Mascot es una reimplementación en Java: eso no dice nada
  del rendimiento ni del aspecto en un teléfono. Sin prueba en teléfono todavía. Que la clase no se enlace si
  falta la API se comprueba con la auditoría estática, no en el emulador, que no arranca sin Mascot.

**Cuándo valdría la pena moverse.** Un renderer Mascot completo sólo tiene sentido si, en el teléfono base
(W995), con escenas del motor y no sólo el cubo, gana con claridad en `ms` y el contenido acepta sus límites: un
color por cara o su propia iluminación, texturas BMP de 8 bits (256 colores) de hasta 256 px, mezclas al 50 %,
suma o resta, y orden de polígonos en lugar de profundidad por píxel. M3G sigue siendo el renderer de producción y
la ruta compatible con el W900i. Siguiente paso si el cubo confirma la ventaja: pasar un tramo de escenario
(`AssetSection`) a Mascot y medirlo con Probe en los dos teléfonos.

### Herramientas beta para el siguiente juego

Tres piezas para probar Mascot con contenido real, sin formatos propietarios: usan los mismos tramos KPS3 que
`AssetSection` y nada de HI Corporation. No hay exportadores a `.mbac` ni `.mtra`, ni dependencia de su SDK.

1. **Comprobador:** `python3 scripts/mascot.py check carpeta_del_nivel`. Lee `chunkN.kps` y `stage_N.png` y da un
   veredicto (`CABE`, `CABE CON PERDIDAS` o `NO CABE`). Separa los límites de la API de lo que va más allá de lo
   medido en los teléfonos. Por tramo cuenta:
   - triángulos y llamadas a `renderPrimitives` (255 como máximo por llamada);
   - memoria expandida (64 bytes por triángulo);
   - triángulos cuyo color de vértice se pierde: Mascot no multiplica la textura por el color, así que se pierden
     la modulación y el Gouraud;
   - coordenadas de textura fuera de la página.

   Por página: colores, alfa parcial y tamaño.
2. **Conversor de texturas:** `python3 scripts/mascot.py texture pagina.png pagina.bmp`. Genera un BMP de 8 bits
   con el formato que Probe ya usó en los dos teléfonos. Si la página tiene 256 colores o menos, la paleta es
   exacta; si tiene más, una reducción determinista avisa del error RMS.
   - El alfa menor que 128 (el umbral de M3G) pasa a un *colour key*: la entrada 0 de la paleta es magenta FF00FF
     y sólo existe en las páginas que lo necesitan.
   - El alfa parcial se avisa como pérdida.
   - Más de 256 px no cabe.
3. **Renderer de tramos:** `kiscport.renderer.MascotSection`. Dibuja un tramo KPS3 con páginas `stage_N.bmp`.
   - No usa `AssetSection` ni M3G (lo comprueba la auditoría), así que funciona en un teléfono sin JSR-184. Se
     carga por nombre y sus métodos públicos sólo usan enteros y `Graphics`.
   - Uso: `loadPages()` por nivel; `new MascotSection(n)` por tramo; en cada cuadro `view(...)`, `begin(g)`,
     `draw()` de todos los tramos visibles y `end(g)`. Mascot ordena los polígonos dentro de un mismo
     `begin`/`end`, así que todos los tramos de un cuadro van juntos.
   - Todavía no tiene intervalos de visibilidad ni carga por pasos: el constructor lee el tramo entero.

**Qué está comprobado.** En PC, el conversor y el comprobador. En FreeJ2ME-Plus, cuya Mascot es una
reimplementación, con un nivel sintético:
- `MascotSection` lee los mismos triángulos que `AssetSection`;
- la textura no sale volteada;
- el colour key deja ver el fondo;
- un vértice rojo no tiñe la textura (la pérdida esperada).

Con los tramos del primer experimento (no incluidos aquí), el comprobador marcó dos cosas que hay que mirar en el
teléfono:
- coordenadas de hasta ±12284, cuando lo medido llega a ±2600;
- entre 86 y 432 triángulos por tramo con un color de vértice que Mascot no aplica.

**Qué hay que comprobar en el teléfono (W995 y W900i) con el siguiente juego:**
- que el colour key (`PATTR_COLORKEY` = 0x10, valor tomado de FreeJ2ME) recorte de verdad;
- si `renderPrimitives` dibuja las dos caras (FreeJ2ME no descarta caras traseras);
- el recorte de polígonos que cruzan el plano cercano;
- la precisión con coordenadas grandes;
- `ms` con cargas por encima de 768 y 3072 triángulos;
- páginas que no son cuadradas;
- el tiempo de carga del constructor y la memoria real frente a la estimada.

## Cómo corre un cuadro

```
KiscPortMIDlet.startApp → DemoCanvas.run
  ┌─ entrada: InputState.consume() (mantenidas + pulsaciones desde el último cuadro)
  ├─ simulación: el tiempo transcurrido en pasos enteros de 20 ms (máx. 250 ms por cuadro)
  ├─ dibujo: escena M3G + capa 2D (FPS, textos)
  ├─ flushGraphics
  └─ LoopPacer (≥ 1 ms de descanso)
```

La simulación avanza en pasos fijos, así que el FPS no cambia el resultado: la demo gira igual a 10 que a 30 FPS,
y las pruebas pueden contar pasos. Al pausar u ocultar la aplicación se limpia la entrada, para que ninguna tecla
quede pegada al volver.

## Formatos

Todos son big-endian, empiezan por un magic de 4 bytes y una versión, y el lector rechaza variantes desconocidas.

| Formato | Contenido | Lector en este repositorio |
|---|---|---|
| KPS1 / KPS3 | Malla con UV y colores; KPS3 = tramo de escenario por partes con páginas de textura compartidas | `AssetSection` |
| KTT1 | Textos UTF-16 | `TextTable` (codificador: `scripts/prepare_text.py`) |
| KPV1 | Por triángulo, en qué intervalos de cámara puede verse | Pendiente de incorporar |
| KPW2 | Mundo: recorrido, colisiones, objetos y móviles | Pendiente de incorporar |
| KPO1, KPC1, KPM1, KPU1 | Catálogo de objetos, personaje con huesos, modelo iluminado, piezas 2D | Pendientes de incorporar |

Los formatos pendientes existen y están probados en el primer experimento, pero sus lectores todavía mezclan reglas
de ese juego. Entrarán aquí cuando estén separados (ver «Qué falta»).

## Memoria

- Geometría en tiras QUAD (`Strips`): muchas menos llamadas al driver que una tira por triángulo.
- Tramos de escenario que se leen por pasos (`AssetSection`), repartiendo la carga en varios cuadros.
- **Pendientes de incorporar** (existen en el primer experimento, mezclados con su juego): el gestor que mantiene
  dos tramos residentes y prepara el siguiente, y la visibilidad precalculada en el PC (KPV1).
- No se fuerza el recolector de basura por cuadro. Antes de cambiar una política de memoria, medir en el
  teléfono con Probe.
- **Lección del W900i (M3G 1.0), noviembre de 2026.** En el primer experimento, al cambiar un conjunto de objetos
  por otro, la construcción del siguiente lanzaba `OutOfMemoryError` y la sesión terminaba. La corrección se
  aplicará al gestor cuando entre aquí:
  1. Soltar las referencias del conjunto anterior antes de construir el siguiente (ruta normal, sin cambios).
  2. Sólo si esa construcción falla: abandonar la precarga pendiente y las vistas por intervalo opcionales de los
     tramos residentes (`AssetSection.views`; sin ellas el tramo se dibuja entero), pedir una recolección y
     **reintentar una vez**. Si vuelve a fallar, terminar con un mensaje que diga la fase (conjunto, tramo, dibujo).

  Es una ruta de recuperación que se activa por el fallo, no por el modelo de teléfono, y no cambia la regla de no
  recolectar por cuadro. Puede causar una pausa breve al cambiar de conjunto. Alcance de la evidencia: el
  propietario del teléfono superó en un W900i el punto que antes fallaba. No hubo sesión larga ni medida de FPS,
  ningún contador confirma que la recuperación se activara, FreeJ2ME no reproduce la memoria M3G del W900i y ese
  binario no se volvió a probar en el W995.

## Construcción, pruebas y evidencia

| Comando | Qué hace | Valida en |
|---|---|---|
| `scripts/bootstrap.py` | Descarga herramientas fijadas por SHA-256 y prepara el emulador | PC |
| `scripts/prepare_intro.py`, `make_intro_sound.py` | Receta de los recursos de marca (`assets/intro/`); no se volvió a ejecutar para 0.8.0, sus salidas están versionadas con hash | PC |
| `scripts/build.py` | JAR de la demo; verifica metadatos, StackMap y dependencias | PC |
| `scripts/test.py` | FrameStats, luz, tiras, textos, auditoría CLDC, compilación negativa, herramientas Mascot | PC |
| `scripts/smoke.py` (`absent`) | La demo con y sin M3G, el cubo Mascot alpha (`#`) y, sin `absent`, `MascotSection` | Emulador |
| `scripts/mascot.py` (`texture`, `check`) | **Beta**: páginas a BMP de 8 bits y si un nivel cabe en Mascot | PC |
| `scripts/reproducibility.py` | Dos construcciones idénticas | PC |
| `scripts/build_probe.py`, `smoke_probe.py` | KiscPort Probe | PC / emulador |
| Probe en el teléfono | Capacidades y rendimiento reales | **Teléfono** |

## Qué falta

1. **Separar e incorporar** el resto del motor del primer experimento: lector de mundo KPW2, renderer de
   escenario con visibilidad, personajes con huesos (KPC), catálogo de objetos por zonas y gestión de audio. Cada
   pieza entra aquí sólo sin reglas ni nombres de ningún juego. El gestor de tramos y del catálogo de objetos
   debe llegar con la recuperación ante `OutOfMemoryError` y los mensajes por fase descritos en «Memoria».
2. **Perfiles de dispositivo** construidos con los informes de Probe: memoria utilizable, triángulos por cuadro,
   textura segura y coste de tiras, relleno y skinning. Probe mide hoy el montón Java; falta medir la memoria M3G
   creando y soltando mallas y texturas como al cambiar de tramo o de conjunto, que es lo que falló en el W900i.
3. **Comprobador de viabilidad**: cruzar lo que necesita un contenido con un perfil y decir si cabe y con qué límites.
4. **Importadores de formatos abiertos**: glTF a KPC/KPM/KPO, PNG a atlas, nivel JSON a KPW2 y KPV1.
