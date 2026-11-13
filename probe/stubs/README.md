# Stubs de compilación (sólo PC)

Firmas públicas de JSR-135 `MIDIControl`, de un subconjunto de JSR-239 (EGL 1.0 / GL10) y de JSR-75 (`FileConnection`, `FileSystemRegistry`), escritas a mano para que ECJ compile KiscPort Probe. **No se empaquetan**: `scripts/build_probe.py` los compila a `build/probe/stubs.jar`, los usa sólo como boot classpath/`-libraryjars` y comprueba que el JAR no contenga clases fuera de `kiscport/probe/`. En el teléfono se enlazan las clases reales; si no existen, el diagnóstico captura `NoClassDefFoundError`/`NoSuchMethodError` y lo muestra. No son implementaciones.
