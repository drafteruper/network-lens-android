# Contributing

Use JDK 17 and the Gradle wrapper. Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` before a pull request. With an emulator/device available, also run `./gradlew connectedDebugAndroidTest`.

Keep camera input, pure text matching, storage and UI separate. Use only fictional or explicitly consented test data, and do not commit images of attendees, biographies, contact lists, databases, tokens or signing keys. New matcher behavior must include ambiguity and false-positive tests. Label uncalibrated scores honestly. Describe device/API level and reproducible steps in issues; redact all personal information.

Contributions are under Apache-2.0. Production signing, external integrations and new permissions require explicit documentation and tests. Report security problems using SECURITY.md rather than public issues containing exploit details or personal data.
