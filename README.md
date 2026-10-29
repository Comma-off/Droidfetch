# Droidfetch

An Android app that shows what your phone is made of: Android version, build, kernel, chipset,
GPU, memory, display, battery, cameras, sensors and more. Built with
[miuix](https://github.com/compose-miuix-ui/miuix), themed with Monet, with an optional floating
Liquid Glass bottom bar powered by [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass).

## Building

Requires the Android SDK (API 37). Gradle provisions JDK 21 automatically.

```sh
./gradlew assembleDebug
```

Release builds are signed with the key described in `keystore.properties`; copy
`keystore.properties.example` and fill it in. Without it, release builds use the debug key.

## License

Copyright (C) 2026 Comma

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
[GNU General Public License](LICENSE) for more details.

Third-party libraries (miuix, Kyant Backdrop, AndroidX, Kotlin) are licensed under the Apache
License 2.0.

The Android robot is reproduced or modified from work created and shared by Google and used
according to terms described in the Creative Commons 3.0 Attribution License.
