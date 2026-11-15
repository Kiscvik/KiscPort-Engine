# Herramientas y procedencia

El MIDlet de `dist/` contiene sólo clases `kiscport.*`, su manifiesto, los PNG y el sonido de marca KISC-LABS (`assets/intro/`, hashes en `assets/intro/conversion.json`) y la tabla de textos de la demo, generada en cada construcción desde `assets/demo/text.json`. No incorpora implementaciones de FreeJ2ME-Plus, MicroEmulator, Sony Ericsson, Oracle ni Nvidia. Este repositorio no contiene imágenes de disco, datos ni recursos de ningún juego comercial.

Las dependencias descargadas de desarrollo están en `tools/`, excluidas de Git. `tools/dependencies.lock.json` (URL, versión y SHA256 de cada descarga) y `tools/emulator.commit` sí se versionan. El bootstrap no cambia la instalación global de Java.

| Componente | Uso | Procedencia/licencia |
| --- | --- | --- |
| Eclipse Temurin 17.0.21+9, macOS ARM64 | Ejecutar compilador, preverificador y emulador | [Adoptium](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21%2B9), OpenJDK GPLv2 con Classpath Exception y avisos incluidos en la distribución |
| ECJ 3.26.0 | Compilar lenguaje 1.3 y destino 1.1 | [Eclipse JDT](https://www.eclipse.org/jdt/), EPL-2.0; jar de Maven Central |
| ProGuard base 6.2.2 | Preverification `-microedition` | [Guardsquare](https://github.com/Guardsquare/proguard/tree/v6.2.2), GPLv2; jar de Maven Central |
| MicroEmulator cldcapi11/midpapi20 2.0.4 | Boot classpath de compilación; no runtime del MIDlet | [MicroEmulator](https://github.com/barteo/microemu), licencias/avisos de los artefactos originales, LGPL; jars de Maven Central |
| FreeJ2ME-Plus, commit 677d41b60e2cee4d7084055bc5b4152b2bc60ecb | Emulación real y biblioteca de símbolos M3G de compilación | [Código fuente fijado](https://github.com/TASEmulators/freej2me-plus/tree/677d41b60e2cee4d7084055bc5b4152b2bc60ecb), GPLv3 y avisos por archivo; licencia en tools/emulator/LICENSE |

El código de KiscPort invoca las APIs del dispositivo; no implementa esas APIs.

Para compartir las herramientas con terceros, conservar sus licencias y el código fuente correspondiente; no empaquetarlas como parte del JAR del teléfono. El bootstrap permite obtenerlas de sus fuentes. `M3G_API_JAR` admite sustituir la biblioteca de compilación por un jsr184.jar de un SDK legítimamente instalado; sus condiciones de distribución siguen siendo las del proveedor.

KiscPort Probe (`probe/`, antes diagnóstico MMAPI) compila contra stubs escritos para este proyecto con las firmas públicas de JSR-135 `MIDIControl`, de un subconjunto de JSR-239, de JSR-75 `FileConnection` y de las llamadas básicas de Mascot Capsule Micro3D v3 (`com.mascotcapsule.micro3d.v3`); sólo se usan como boot classpath y no se empaquetan. La demo usa los de `MIDIControl` y Mascot del mismo modo. No contienen código de HI Corporation ni de Sony Ericsson; en el teléfono se enlazan las clases reales del dispositivo. Sus sonidos son sintéticos, generados por `scripts/make_probe_sounds.py`.
