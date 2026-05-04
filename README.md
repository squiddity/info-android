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
# Use JDK 17 for Gradle/AGP builds
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleRelease
```

## SDK + emulator setup (automation-friendly)

```bash
# shell env (persist this in ~/.bashrc)
source /etc/profile.d/android-sdk-cmdline-tools-latest.sh
source /etc/profile.d/android-emulator.sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

# tooling installs
yes | sdkmanager --sdk_root="$ANDROID_HOME" \
  "cmdline-tools;latest" \
  "platform-tools" \
  "emulator" \
  "platforms;android-34" \
  "system-images;android-34;android-tv;x86"

# create & run TV AVD
echo no | avdmanager create avd -n infoAndroidTv \
  -k "system-images;android-34;android-tv;x86" \
  -d tv_1080p --force
emulator -avd infoAndroidTv
```

## TV-first UI

The app uses a D-pad friendly list UI with focusable cards and grouped sections:
- Current active mode
- Resolution to refresh-rate combinations
- All supported `Display.Mode` rows
- HDR metadata (when available)

## GitHub Actions: APK build + Releases

The workflow now requires signing secrets and will fail if a signed APK is not produced.

Required GitHub repository secrets:
- `SIGNING_KEYSTORE_B64` (base64-encoded `.jks` keystore)
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Generate `SIGNING_KEYSTORE_B64` locally:

```bash
base64 -w0 my-release-key.jks
```

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
