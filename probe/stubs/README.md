# Compile stubs (PC only)

Public signatures of JSR-135 `MIDIControl`, a subset of JSR-239 (EGL 1.0 / GL10), JSR-75 (`FileConnection`, `FileSystemRegistry`) and the basic calls of Mascot Capsule Micro3D v3 (`com.mascotcapsule.micro3d.v3`: `Graphics3D`, `FigureLayout`, `AffineTrans`, `Effect3D`, `Texture`), written by hand so that ECJ can compile KiscPort Probe. **They are not packaged**: `scripts/build_probe.py` compiles them to `build/probe/stubs.jar`, uses them only as boot classpath/`-libraryjars`, and checks that the JAR holds no classes outside `kiscport/probe/`. On the phone the real classes are linked; if they do not exist, Probe catches `NoClassDefFoundError`/`NoSuchMethodError` and shows it.

`scripts/build.py` also uses the `MIDIControl` and Mascot stubs for the engine demo (`build/api-stubs.jar`), with the same rules: they are not packaged, and `scripts/audit.py` limits Mascot to `kiscport/renderer/MascotCubeScene` and `MascotSection`.

They are not implementations. The Mascot constants are copied into the code at compile time, so they must keep the API's documented values (`PATTR_COLORKEY` = 0x10 comes from FreeJ2ME-Plus and has not been run on a phone yet).
