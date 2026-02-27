#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets/medium-streaming-en"
MODEL_CACHE="$HOME/Library/Caches/moonshine_voice/download.moonshine.ai/model/medium-streaming-en/quantized"

echo "=== PixelWhisper Setup ==="
echo ""

# Step 1: Check Android SDK
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    # Try common locations
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "/usr/local/lib/android/sdk" ]; then
        export ANDROID_HOME="/usr/local/lib/android/sdk"
    else
        echo "ERROR: Android SDK not found."
        echo "Install Android Studio or set ANDROID_HOME."
        echo "  brew install --cask android-studio"
        echo "  # Then: Android Studio → SDK Manager → install API 35"
        exit 1
    fi
fi
echo "✓ Android SDK: ${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

# Step 2: Check Gradle wrapper
if [ ! -f "$PROJECT_DIR/gradlew" ]; then
    echo "Generating Gradle wrapper..."
    if command -v gradle &>/dev/null; then
        cd "$PROJECT_DIR" && gradle wrapper --gradle-version 8.11.1
    elif [ -f "/tmp/gradle-8.11.1/bin/gradle" ]; then
        cd "$PROJECT_DIR" && /tmp/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1
    else
        echo "ERROR: No gradle available. Run:"
        echo "  curl -sL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o /tmp/gradle.zip"
        echo "  unzip -qo /tmp/gradle.zip -d /tmp/"
        echo "  /tmp/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1"
        exit 1
    fi
    echo "✓ Gradle wrapper generated"
else
    echo "✓ Gradle wrapper exists"
fi

# Step 3: Check Moonshine model files
if [ ! -f "$ASSETS_DIR/tokenizer.bin" ]; then
    echo ""
    echo "Moonshine model files not found. Downloading..."
    VENV_DIR="/tmp/moonshine-env"
    if [ ! -d "$VENV_DIR" ]; then
        python3 -m venv "$VENV_DIR"
    fi
    source "$VENV_DIR/bin/activate"
    pip install -q moonshine-voice

    python3 -m moonshine_voice.download --language en

    if [ -d "$MODEL_CACHE" ]; then
        mkdir -p "$ASSETS_DIR"
        cp "$MODEL_CACHE"/* "$ASSETS_DIR/"
        echo "✓ Model files copied to $ASSETS_DIR"
    else
        echo "WARNING: Model cache not found at expected location."
        echo "  Check ~/Library/Caches/moonshine_voice/ for the download"
        echo "  and copy all files to: $ASSETS_DIR/"
    fi
    deactivate 2>/dev/null || true
else
    echo "✓ Moonshine model files present"
fi

# Step 4: Build
echo ""
echo "Building debug APK..."
cd "$PROJECT_DIR"
./gradlew assembleDebug

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: Build failed — APK not found"
    exit 1
fi
echo "✓ APK built: $APK_PATH"

# Step 5: Install on device
echo ""
if adb devices 2>/dev/null | grep -q "device$"; then
    echo "Installing on connected device..."
    adb install -r "$APK_PATH"
    echo "✓ APK installed"

    # Step 6: Enable accessibility service via ADB
    echo ""
    echo "Enabling accessibility service..."
    CURRENT=$(adb shell settings get secure enabled_accessibility_services 2>/dev/null || echo "")
    SERVICE="com.pixelwhisper/.TextInjectionService"
    if echo "$CURRENT" | grep -q "$SERVICE"; then
        echo "✓ Accessibility service already enabled"
    else
        if [ -n "$CURRENT" ] && [ "$CURRENT" != "null" ]; then
            adb shell settings put secure enabled_accessibility_services "$CURRENT:$SERVICE"
        else
            adb shell settings put secure enabled_accessibility_services "$SERVICE"
        fi
        echo "✓ Accessibility service enabled"
    fi

    echo ""
    echo "=== PixelWhisper is ready! ==="
    echo "Open the app on your device to grant overlay + mic permissions, then tap Launch."
else
    echo "No device connected. To install later:"
    echo "  adb install -r $APK_PATH"
    echo "  adb shell settings put secure enabled_accessibility_services com.pixelwhisper/.TextInjectionService"
fi
