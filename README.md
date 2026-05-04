# info-android

Android TV-focused system info app.

## First release scope

Show display capabilities for Android TV devices:
- Current display metadata
- Supported `Display.Mode` entries (resolution + refresh rate)
- Grouped resolution/frame-rate combinations to answer: **"What resolutions and frame rate combos does this Android TV support?"**

## Build

Open in Android Studio (Giraffe+ / AGP 8.x) and run on an Android TV device or emulator.

Or build from CLI with the checked-in Gradle wrapper:

```bash
./gradlew :app:assembleRelease
```

## TV-first UI

The app uses a D-pad friendly list UI with focusable cards and grouped sections:
- Current active mode
- Resolution to refresh-rate combinations
- All supported `Display.Mode` rows
- HDR metadata (when available)

## GitHub Actions: APK build + Releases

Workflow: `.github/workflows/android-release.yml`

- `workflow_dispatch`: manual build (uploads APK artifact)
- `push` tag `v*`: builds APK and publishes it to GitHub Releases

### One-time repo setup

```bash
cd ~/ai-projects/info-android
git init
git add .
git commit -m "Initial Android TV display info app"
# create empty repo on GitHub first, then:
git remote add origin git@github.com:<your-user>/info-android.git
git branch -M main
git push -u origin main
```

### Create a release APK

```bash
git tag v0.1.0
git push origin v0.1.0
```

That tag push triggers the workflow and uploads `app-release.apk` to the GitHub Release.
