# Services, research and dependency terms

Researched 2026-10-27. The inspiration URL https://x.com/N01ennn/status/2115068439052030202?s=20 could not be fetched with the available web tool; implementation follows the written brief.

| Component | API / access | Cost / credentials | MVP use |
|---|---|---|---|
| Android CameraX | Official AndroidX camera APIs | No service account or per-use fee | Rear preview and view-coordinate analysis |
| ML Kit text recognition | Official bundled Android SDK 16.0.1 | No cloud key; local model | Latin-script badge OCR |
| ML Kit face detection | Official bundled Android SDK 16.1.7 | No cloud key; local model | Anonymous rectangles only |
| Room | AndroidX database | No service or credentials | Local events and encounters |
| Luma | Official authenticated event/guest API | Organizer API key/access; current plan eligibility and quotas must be checked with Luma | Not integrated; URL reference and CSV fallback |
| Professional profiles | User-provided HTTPS links | Browser/site authentication may apply | Opens supplied links only |
| Reverse-image / facial search | No service integrated | Not applicable | Not implemented |
| GitHub | Git/CLI and Actions | Maintainer authentication; account/Actions limits apply | Public source, CI artifacts and optional prereleases |

Sources:
- https://developer.android.com/media/camera/camerax/analyze
- https://developer.android.com/media/camera/camerax/mlkitanalyzer
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developers.google.com/ml-kit/vision/face-detection/android
- https://developers.google.com/ml-kit/terms
- https://developer.android.com/jetpack/androidx/releases/room
- https://docs.luma.com/reference/getting-started-with-your-api
- https://help.luma.com/p/luma-api
- https://www.samsungmobilepress.com/articles/samsung-galaxy-z-fold6-and-z-flip6-elevate-galaxy-ai-to-new-heights

## Licensing

Original application source is Apache-2.0 (full LICENSE included). AndroidX and Kotlin components use their respective upstream licenses, generally Apache-2.0. Gradle wrapper is from the official Gradle distribution. Google ML Kit libraries/models and Google Play services dependencies remain governed by Google's SDK terms; they are retrieved from Google's Maven repository and are not relicensed as this project's source. No proprietary source or model files are copied into the repository. Build-tool downloads, license acceptances, debug signing keys and caches are outside the source project. Fictional names and example.org links are test fixtures, not real attendee data.

## Deferred services

No cloud lookup, legal biometric identification workflow or paid facial-search service is enabled. A future authorized event API adapter must obtain organizer access, document current terms/quotas, avoid embedding service secrets in the APK, and never bypass guest-list restrictions. This MVP does not claim that an arbitrary public event URL exposes its attendees.
