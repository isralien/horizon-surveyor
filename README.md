# Horizon Surveyor

An Android app that surveys your local horizon — how high nearby obstructions
(trees, roofs, hills) reach at every compass direction — and exports a
horizon profile that Stellarium (and similar tools) can use to show which
parts of the sky are actually blocked from your observing spot.

## How it works

1. **Capture.** Point the camera at the horizon and tap **Start Capture**,
   then turn slowly on the spot. A progress ring at the top shows a fixed
   dot for where you started and a moving dot for your current heading —
   panning brings them back together. As you turn, the app builds a
   panorama as a "pushbroom" scan: it crops a thin vertical strip from the
   live preview every few degrees and lays the strips side by side, each
   tagged with the exact azimuth/altitude the phone was reading at that
   instant. Placement comes from the orientation sensor, not image
   matching, so there's no seam-blending or feature-matching involved —
   the x-axis is an exact azimuth scale by construction. Capture finishes
   automatically once the two dots meet (a full turn).
2. **Review.** The finished panorama opens full-width and scrollable. Tap
   anywhere on the horizon line in the photo to read off its azimuth/altitude
   and drop a marker — repeat around the full image. This replaced an
   earlier live-tap-while-panning design: tapping a big, still photo is far
   more accurate than trying to line up a live reticle while turning.
3. **Finish.** Once you've marked enough points, export writes the
   Stellarium files and opens the share sheet.

This deliberately avoids full computer-vision panorama stitching (feature
matching, blending) — that's a much harder, error-prone problem to get right
without hardware to test against. Sensor-placed strips are simple and, more
importantly, give exact coordinates for free.

## Project layout

- `core/` — pure Kotlin/JVM module, no Android dependency:
  - `HorizonPoint` / `HorizonProfile` — sorting, dedup, polygon closing.
  - `StellariumHorizonExporter` — the Stellarium/CSV file formats.
  - `PanoramaGeometry` — the panorama math: unwrapped-rotation tracking
    across the 0/360 wrap, strip placement, and tap → azimuth/altitude
    lookup (interpolating the phone's recorded pitch between capture
    checkpoints, corrected for how far the tap landed from vertical center
    using the camera's field of view).
  
  All of the above has a real unit test suite (`./gradlew :core:test`,
  21 tests) — notably including the wraparound-accumulation and
  tap-interpolation math, which is exactly the kind of thing that's easy to
  get subtly backwards.
- `app/` — the Android app: CameraX preview, sensor fusion
  (`OrientationTracker`), the panorama capture glue (`PanoramaBuilder`,
  wrapping `PanoramaGeometry` with actual Bitmap/Canvas work), the progress
  ring and marker overlays, and export/share.

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
so this has been written carefully but **not run on hardware**. Things
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
3. **Vertical field of view.** Tapping the panorama converts pixel offset
   from vertical center into a degree offset using the camera's vertical
   FOV, computed from `CameraCharacteristics` (sensor physical size +
   focal length). This assumes the preview crop is proportional to the full
   sensor and a rectilinear (non-fisheye) lens — a reasonable approximation
   for a typical phone main camera, not an exact one. Sanity check: tap
   dead center of a capture strip and confirm the reported altitude roughly
   matches the readout you saw live while capturing that part of the pan;
   if it's off by more than a couple degrees, the `DEFAULT_VERTICAL_FOV_DEG`
   fallback in `MainActivity.kt` (55°) may need adjusting for your device,
   or `computeVerticalFovDeg()` may be failing to read characteristics and
   silently falling back to it.
4. **Preview snapshot cost.** Panorama strips come from
   `PreviewView.getBitmap()`, called every few degrees of rotation
   (throttled, not every frame) rather than a raw camera analysis stream —
   simpler and correctness-safe (no manual sensor-buffer rotation math to
   get wrong), but it's documented as a relatively expensive call. If
   panning feels janky, increase `captureIntervalDeg` in
   `PanoramaBuilder`'s constructor to call it less often.

Location permission is optional and used only to correct magnetic azimuth to
true-north azimuth via `GeomagneticField`; it's never stored or exported.

## Building

Open the project root in Android Studio (Iguana+), let it sync, run on a
device. Or from the command line with the Android SDK installed:

```
./gradlew :app:assembleDebug
```

`core`'s tests don't need the Android SDK and run standalone:

```
./gradlew :core:test
```
