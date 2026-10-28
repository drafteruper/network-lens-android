# Releasing

1. Run the build, JVM tests and lint. Run device tests when available and record device/API level and limitations in VALIDATION.md.
2. Review staged content for attendee data, credentials, local paths and signing files. Never upload caches, local.properties or keystores.
3. Update versionName/versionCode and CHANGELOG.md. Commit the actual tested source.
4. Create a prerelease tag for a debug build. Attach the verified APK and SHA-256 checksum, and state clearly that it is development-signed.
5. Publish a production release only after establishing a persistent secure signing key, secure CI configuration and a production verification process. Do not commit that key. This project does not configure production signing.

Manual GitHub publishing if not already published:

```sh
gh auth login
git init -b main
git add .
git commit -m "Build badge-first Network Lens Android MVP"
gh repo create network-lens-android --public --source=. --remote=origin --push
```

Do not rerun `repo create` if a repository already exists. CI artifacts use the runner's debug signing identity, which may differ from locally built debug APKs. Updates across differing certificates require uninstalling first and erase local data.
