# Horizon Surveyor

An Android app that surveys your local horizon — how high nearby obstructions
(trees, roofs, hills) reach at every compass direction — and exports a
horizon profile that Stellarium (and similar tools) can use to show which
parts of the sky are actually blocked from your observing spot.

## How it works

1. **Capture.** Point the camera at the horizon and tap **Start Capture**,
   then turn slowly on the spot, keeping the on-screen crosshair on the
   horizon line. A progress ring at the top shows a fixed dot for where you
   started and a moving dot for your current heading — panning brings them
   back together. As you turn, the app builds a panorama as a "pushbroom"
   scan: it crops a thin vertical strip from the live preview every few
   degrees and lays the strips side by side, positioned horizontally by
   exactly how far the phone has rotated (an exact azimuth scale, no
   feature-matching involved). Vertically, each strip is *shifted* by how
   far the phone's pitch deviated from the pitch reading when capture
   started, registering every strip against that one shared reference —
   so the photo reads as a single coherent panorama instead of a jump-cut
   between frames, even though your hand naturally drifts up/down while
   tracking an uneven horizon. A horizon point is recorded automatically for
   every strip at the row matching its own pitch reading, so the line
   through them traces the real horizon shape immediately, without needing
   any taps. (Above/below the horizon itself, expect a jagged "staircase" at
   the sky/ground edges of the image where consecutive strips' registration
   shift differs — that's an expected side effect of aligning the horizon
   rather than the frame edges, not a bug.) Auto-exposure/white-balance lock
   during capture to reduce banding between strips. Capture finishes
   automatically once the two dots meet (a full turn).
2. **Review.** The finished panorama opens pinch-zoomable and pannable, with
   a line connecting all the recorded points drawn over it. If a point looks
   wrong (e.g. the crosshair briefly caught an obstacle, or drifted off the
   true horizon), drag it up or down to correct it — that adjusts only its
   altitude, never which azimuth it belongs to.
3. **Finish.** Export writes the Stellarium files and opens the share sheet.

This deliberately avoids full computer-vision panorama stitching (feature
matching, blending) — that's a much harder, error-prone problem to get right
without hardware to test against. Sensor-placed strips are simple and, more
importantly, give exact coordinates for free.

## Project layout

- `core/` — pure Kotlin/JVM module, no Android dependency:
  - `HorizonPoint` / `HorizonProfile` — sorting, dedup, polygon closing.
  - `StellariumHorizonExporter` — the Stellarium/CSV file formats.
  - `PanoramaGeometry` — unwrapped-rotation tracking across the 0/360 wrap,
    strip placement (horizontal and the vertical registration shift), and
    the altitude <-> canvas-row math used both to draw the horizon line and
    to convert a dragged point back to an altitude.
  - `PanoramaViewTransform` — the zoom/pan math for the review screen: fit
    scale, clamping so the image never pans past its own edges, and
    view <-> content coordinate conversion.

  All of the above has a real unit test suite (`./gradlew :core:test`,
  28 tests) — notably including the wraparound-accumulation and
  coordinate-conversion math, which is exactly the kind of thing that's easy
  to get subtly backwards (one already caught a bug in a test's own
  arithmetic, not the implementation, while this was being built).
- `app/` — the Android app: CameraX preview, sensor fusion
  (`OrientationTracker`), the panorama capture glue (`PanoramaBuilder`,
  wrapping `PanoramaGeometry` with actual Bitmap/Canvas work), the progress
  ring, the zoomable review view with drag-to-correct
  (`PanoramaReviewView`), and export/share.

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
3. **Vertical field of view.** Both the strip registration shift and
   dragging a point convert a pixel offset into a degree offset using the
   camera's vertical FOV, computed from `CameraCharacteristics` (sensor
   physical size + focal length). This assumes the preview crop is
   proportional to the full sensor and a rectilinear (non-fisheye) lens —
   a reasonable approximation for a typical phone main camera, not an exact
   one. Sanity check: right after capture, before dragging anything, the
   line should already roughly trace the real horizon in the photo; if the
   fallback `DEFAULT_VERTICAL_FOV_DEG` (55°) is being used instead of a real
   per-device reading, the registration/drag scale will be off (horizon
   shape looks stretched or compressed vertically vs. what you saw live).
4. **Preview snapshot cost.** Panorama strips come from
   `PreviewView.getBitmap()`, a documented-expensive, main-thread call.
   It's throttled two ways: an angle threshold (`captureIntervalDeg`, so
   slow/careful panning gets fine resolution) and a hard wall-clock floor
   (`minCaptureIntervalMs`, default 150ms) that caps the absolute call rate
   no matter how fast someone pans. The time floor is the one that actually
   matters for not hanging the UI — an earlier version only had the angle
   throttle, so panning fast enough (or the call itself being slower on some
   device than expected) could queue this expensive call up faster than the
   main thread could drain it, which read as the whole screen freezing.
   If capture still feels janky or unresponsive on real hardware, raising
   `minCaptureIntervalMs` is the first lever to pull, before touching
   `captureIntervalDeg`.
5. **Registration seams.** Early versions pasted each strip as one rigid
   block at its registered vertical offset, so any jump in the pitch
   reading between two consecutive captures showed as a hard step in the
   photo — and since `minCaptureIntervalMs` (above) bounds how *often*
   strips can be captured, strips can end up fairly wide under normal
   panning speed, which made those steps read as a chunky, visibly offset
   "fan" rather than a fine stagger. Tuning capture frequency against the
   hang-prevention throttle turned out to be a losing trade, so
   `addStrip()` now draws each strip in narrow (`SUB_COLUMN_WIDTH_PX`, 3px)
   sub-columns whose vertical offset ramps linearly from where the previous
   strip left off to this strip's own offset, instead of one constant
   offset for the whole width — turning a hard step into a smooth ramp
   regardless of strip width. `OrientationTracker`'s altitude smoothing
   (`ALTITUDE_SMOOTHING_FACTOR`) still helps by damping the sensor jitter
   that drives those jumps in the first place. The ramping math
   (`PanoramaGeometry.interpolatedSubColumns`) is unit-tested; the
   Bitmap/Canvas drawing loop that uses it is not (no Android runtime
   here), so this is the next thing to check if the photo still looks
   stepped on real hardware.
6. **AE/AWB lock.** Capture locks auto-exposure and auto-white-balance
   (`CONTROL_AE_LOCK` / `CONTROL_AWB_LOCK` via Camera2 interop) to stop
   consecutive strips from visibly banding as the camera's exposure drifts
   mid-pan. This should be broadly supported, but isn't verified on real
   hardware; if strips still look inconsistently exposed, that's the first
   place to check.

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
