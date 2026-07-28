# Horizon Surveyor

An Android app that surveys your local horizon — how high nearby obstructions
(trees, roofs, hills) reach at every compass direction — and exports a
horizon profile that Stellarium (and similar tools) can use to show which
parts of the sky are actually blocked from your observing spot.

## How it works

This is the "manual-tap" approach, not automatic image analysis: as you turn
360° on the spot, you visually line up the on-screen horizon reticle with the
real horizon and tap **Mark** every 10-20°. The phone's fused orientation
sensor (`TYPE_ROTATION_VECTOR`) records the compass azimuth and tilt angle
(altitude) at the moment you tap. This is far more reliable than automated
sky/ground image segmentation — no confusion from clouds, glare, or a hazy
tree line — at the cost of being manual instead of instant.

Automatic detection (analyzing the live camera feed for the sky/ground
boundary) is a natural v2, and it's why the point-recording logic lives in a
plain-Kotlin `core` module decoupled from the UI — a future CV pipeline can
feed it `HorizonPoint`s the same way manual taps do today.

## Project layout

- `core/` — pure Kotlin/JVM module, no Android dependency. `HorizonPoint`,
  `HorizonProfile` (sorting, dedup, polygon closing) and
  `StellariumHorizonExporter`. Has a real unit test suite
  (`./gradlew :core:test`) covering azimuth normalization, wraparound
  closing, duplicate-azimuth handling, and the exact exported file format.
- `app/` — the Android app: CameraX preview, sensor fusion, the tap-to-mark
  UI, and export/share.

## Output files

Finishing a survey writes three files (named after whatever you call the
survey) and opens the share sheet so you can send them to Stellarium, a
cloud drive, email, etc.:

- `<name>_horizon.txt` — Stellarium's polygonal horizon format: one
  `azimuth altitude` pair per line, in degrees, closed into a full loop.
- `landscape.ini` — the companion config Stellarium needs to load that file
  as a custom landscape (`type = polygonal`,
  `polygonal_horizon_list_mode = azDeg_altDeg`).
- `<name>_horizon.csv` — the same az/alt data as a plain two-column CSV, for
  any other tool that just wants a horizon profile.

### Installing into Stellarium (desktop)

1. Create a folder named after your survey inside Stellarium's user
   `landscapes/` directory (Configuration → Landscape shows the path, or see
   the [Stellarium user guide](https://stellarium.org/doc/)).
2. Copy `landscape.ini` and `<name>_horizon.txt` into that folder.
3. Restart Stellarium (or reload landscapes) — your surveyed horizon will
   appear in the landscape list.

Stellarium Mobile has more limited custom-landscape support than desktop;
the CSV export is the fallback for mobile planetarium apps that accept a
generic az/alt horizon list.

## Known limitations / things to verify on a real device

I don't have an Android device or SDK in the environment this was built in,
so this has been written carefully but **not run on hardware**. Two things
worth checking on first use:

1. **Altitude sign.** `OrientationTracker.kt` derives the axis remap for
   "phone held vertically like a camera" from first principles (see the
   comment on the class), which is the part that's easy to get subtly
   wrong. If tilting the camera up makes the on-screen altitude reading go
   *down*, flip `INVERT_ALTITUDE` to `true` in that file — it's a one-line,
   easily observable fix.
2. **Compass accuracy.** Phone magnetometers drift and are sensitive to
   nearby metal/magnets. Wave the phone in a figure-8 if Android prompts for
   compass calibration before surveying, and expect a few degrees of
   real-world error — fine for "is that tree blocking Saturn," not
   survey-grade.

Location permission is optional and used only to correct magnetic azimuth to
true-north azimuth via `GeomagneticField`; it's never stored or exported.

## Building

Open `android/` in Android Studio (Iguana+), let it sync, run on a device.
Or from the command line with the Android SDK installed:

```
./gradlew :app:assembleDebug
```

`core`'s tests don't need the Android SDK and run standalone:

```
./gradlew :core:test
```
