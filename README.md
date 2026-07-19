# Indoor Wi-Fi Navigation

[![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Validation](https://img.shields.io/github/actions/workflow/status/LinkisLethe/BNBU_IndoorNavigation/validate.yml?branch=master&style=flat-square&label=validation)](https://github.com/LinkisLethe/BNBU_IndoorNavigation/actions/workflows/validate.yml)
[![License](https://img.shields.io/badge/license-MIT-2ea44f?style=flat-square)](LICENSE)

[中文说明](README.zh-CN.md)

An Android indoor positioning and navigation prototype for a mapped
multi-building environment. It combines Wi-Fi fingerprint matching for room-
level positioning with pedestrian dead reckoning (PDR), graph-based routing,
floor switching, and route snapping.

## Core functions

- Admin mode collects six Wi-Fi scans per reference point, drops weak signals
  below `-85 dBm`, removes the largest RSSI deviation, and persists fingerprints
  as JSON.
- User mode performs four scans and ranks fingerprints with a `K=3` weighted
  nearest-neighbor score that combines Euclidean distance and cosine similarity.
- `MapData` stores room, door, corridor, and stair nodes across three floors;
  `PathFinder` uses Dijkstra's algorithm with a cross-floor penalty.
- `PdrManager` uses Android's step detector and rotation-vector sensor. The
  navigation activity applies a `0.79 m` step length, map scale conversion,
  path snapping, Wi-Fi drift correction, stair triggers, and arrival detection.
- The event module retrieves current event listings and maps recognized venue
  labels to the campus overview.

The bundled maps, graph coordinates, and fingerprint database are calibrated
for one physical deployment. They demonstrate the complete pipeline but are
not a general indoor-positioning benchmark.

## Build and run

The project uses Java 11 source compatibility, Android Gradle Plugin `8.13.1`,
Gradle `8.13`, `minSdk 24`, `compileSdk 36`, and `targetSdk 36`. Use JDK 17 to
run Gradle.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Open the repository root in Android Studio, let Gradle sync, and deploy to a
physical Android device with Wi-Fi, a step detector, and a rotation-vector
sensor. Grant location and activity-recognition permissions when prompted.
Frequent Wi-Fi scans may also require disabling scan throttling in Android
Developer Options.

The main code is under
`app/src/main/java/com/example/fingerprintlocation/`. The local unit tests
cover same-floor routing, cross-floor routing, and invalid-node handling.

## Limits and licensing

Positioning accuracy depends on the device, Wi-Fi environment, fingerprint
coverage, map calibration, and phone orientation. The PDR constants are fixed
for the reviewed deployment, and the event parser depends on the current HTML
structure of its source page.

The project is available under the [MIT License](LICENSE). GitHub preserves the
original upstream history and fork relationship.
