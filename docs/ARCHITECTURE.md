# Architecture

- `MainActivity`: Compose event/directory/history/privacy/profile screens, permission and document-picker entry points.
- `LensViewModel`: UI state, event selection, bounded imports, retention, persistence and user confirmation.
- `data/Database`: Room entities and transactions. Event deletion cascades to attendees and encounters. Attendee IDs are distinct across events, even when names match.
- `core/CsvImport`: pure Kotlin parser and validation. Exact duplicate rows are removed; existing identical records are skipped on import.
- `core/Matching`: immutable event index, deterministic matching rules, and vendor-independent observation contracts.
- `camera/CameraScanner`: lifecycle-bound CameraX + ML Kit analyzer; bundled Latin OCR and anonymous face detection. Detection/matching callback runs on a single background executor. CameraX handles latest-frame analysis; results are posted to the UI thread.

CameraX `MlKitAnalyzer` transforms OCR and face coordinates to PreviewView coordinates. Overlay boxes and hit testing use those same pixel coordinates. Labels expire after 1.5 seconds without a new result. Switching away from Scan, opening a candidate/profile, or leaving the activity releases/suspends camera use through Compose disposal and the lifecycle. Results are never used to infer which face belongs to which badge.

`FrameObservations`, `BadgeObservation`, `Bounds`, `Profile` and `ObservationSink` have no Android dependencies. Future glasses adapters must map coordinates and timestamps explicitly, and can reuse the badge matcher. No transport or glasses SDK has been implemented.

## Confirmation and memory

The directory is observed from Room and indexed in memory. Saved encounters are keyed by event-local attendee ID. A new scan still needs badge evidence; confirming it updates lastSeen and retains networking status. There is no automatic identification from prior face sightings. Corrections remove the old encounter association and route back to directory search. Attendee-field editing is not yet implemented; delete/reimport the event to replace an incorrect directory.

Heuristic score thresholds are constants, not trained/calibrated metrics. Keep probability language out of the UI until independently validated on a suitable consented evaluation set. No aggregate OCR accuracy or end-to-end latency claim has been established.

## Privacy boundaries

No audio permission, location permission, facial embeddings, analytics, remote image calls or persistent images. Profile details derive only from user-provided event data. Face detector classifications and landmarks are disabled. The app is intended for transparent, permitted badge use. Public visibility of a guest list is not assumed to authorize its reuse.

## Known MVP tradeoffs

Single activity with separated data/core/camera components; no dependency injection framework or mandatory backend. Portrait and landscape layouts scroll where appropriate; unfolded phone UI is the target. ML Kit may split related badge lines into different blocks, conservatively reducing matches. Large and crowded frames may clutter labels (capped at 16 text blocks). No text-to-face attribution, automatic unknown-person counts, or biometric retention.
