# KiscPort Engine 0.8.0

KISC-LABS, 13 de noviembre de 2026. Cómo funciona el motor, qué contiene este repositorio y qué falta.

## Plataforma

- CLDC 1.1, MIDP 2.0 y JSR-184 (M3G); fuente Java 1.3, bytecode 45.3, preverificación CLDC StackMap.
- El JDK 17 sólo ejecuta herramientas del PC. `scripts/test.py` rechaza a propósito una compilación que use Java SE.
- Sin APIs propietarias. Las diferencias entre teléfonos se detectan por capacidad (`microedition.m3g.version`,
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
| `demo/DemoCanvas`, `DemoScene` | La demo: bucle, intro, entrada, pausa, textos y FPS |

Reglas que comprueba `scripts/audit.py`: M3G sólo en `kiscport/renderer/`, MMAPI sólo en `kiscport/audio/` y
`MIDIControl` sólo en `MidiControlOut`. Así un teléfono sin esas APIs sigue arrancando.

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
| `scripts/test.py` | FrameStats, luz, tiras, textos, auditoría CLDC, compilación negativa | PC |
| `scripts/smoke.py` (`absent`) | La demo con y sin M3G | Emulador |
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
