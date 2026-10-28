# Validation

Verified locally on 2026-10-27. Application version **0.1.0 (1), debug**, package `org.networklens`, minimum API 26, target API 35. JDK 17, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.10.

| Check | Actual result |
|---|---|
| `assembleDebug` | Passed; installable debug APK produced |
| `testDebugUnitTest` | 20 passed, 0 failed (9 badge matcher, 11 CSV parser) |
| `lintDebug` | Passed: 0 errors, 25 warnings |
| `connectedDebugAndroidTest` | 5 passed, 0 failed, Android 15 / API 35 x86_64 emulator |
| APK signature | `apksigner verify --verbose` passed, v2 signature, one development signer |
| Install and launch | Passed on emulator with `adb install` and explicit activity launch |
| Camera permission absent | Camera-access explanation and manual-directory fallback visible |
| Camera permission granted | Live rear-camera emulator test pattern displayed; no crash observed |
| Navigate away / restart | Returned to People, force-stopped, reopened; saved event still present |
| APK permissions | CAMERA plus AndroidX's app-scoped signature receiver permission; no INTERNET, microphone, location, or storage permission |
| Physical Galaxy Z Flip 6 | Not tested; no physical device connected |

Device tests exercise Room event isolation, repeat encounter updates, retained status, cascading deletion, individual deletion, retention expiry, bundled OCR reading a synthetic fictional badge, blank-frame face detection returning no faces, and the Compose create-event/add-attendee/confirm/history/privacy workflow.

Lint warnings are newer-dependency notices, draw-time allocation suggestions, a programmatically-created View constructor suggestion, and Kotlin extension-style suggestions. They are not suppressed. There are no lint errors. Dependencies are pinned for reproducibility; production adoption should review current updates and security advisories.

Actual emulator screenshots are in `docs/screenshots/`. The scanner image shows an emulator camera test pattern, not an event or a successful person identification. No private data was used.

## Not yet validated

- Real badge OCR accuracy at a crowded event, partial badges, poor lighting, motion blur, and multiple real faces.
- Physical-camera overlay alignment and latency on a Galaxy Z Flip 6; unfolded/rotation/fold transitions and battery/thermal behavior on that hardware.
- Statistical score calibration (the displayed weights are estimates, not probabilities).
- Long-duration scanning, very large directories, accessibility audit, or all Android API levels from 26 onward.
- Automated permission-denial/revocation tests, document-picker CSV import on physical hardware, and full process-death recreation during imports.

No claim of reliable facial identification or crowd-level unique-person counting is made. Existing tests do not establish those capabilities. Face recognition is not implemented.

## Reproduction

Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`, then `./gradlew connectedDebugAndroidTest` with an emulator attached. Windows locally exhibited intermittent Gradle immutable-cache move failures; retrying after the build stopped and using `--no-watch-fs --max-workers=1` allowed completion. This is a host build-tool issue, not an Android app crash.

GitHub Actions build **passed** on application-source commit `ea3bd47`: https://github.com/drafteruper/network-lens-android/actions/runs/40618081595 . It compiled the APK and instrumentation APK, ran the unit tests and lint, and uploaded artifacts. Device tests were run locally, not in CI. The earlier run failed during SDK setup because an action default requested the retired `tools` package; the workflow now requests specific supported SDK packages.
