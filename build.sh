#!/bin/bash
set -e

# Configuration
# Ensure you have android.jar at this path or update it
ANDROID_JAR="lib/platform-33/android13/android.jar"
BUILD_DIR="build"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
APK_UNSIGNED="$BUILD_DIR/app-unsigned.apk"
APK_SIGNED="$BUILD_DIR/app-signed.apk"

# Keystore settings (Debug key)
KEYSTORE="debug.keystore"
KEY_ALIAS="androiddebugkey"
KEY_PASS="android"
STORE_PASS="android"

# Clean previous build
echo "Cleaning build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$OBJ_DIR"

# 1. Compile Resources
echo "Compiling resources..."
RES_ARGS=""
if [ -d "res" ]; then
    aapt2 compile --dir res -o "$BUILD_DIR/resources.zip"
    RES_ARGS="$BUILD_DIR/resources.zip"
else
    echo "Warning: 'res' directory not found. Assuming resources are not needed or handled elsewhere."
fi

# 2. Link Resources
echo "Linking resources..."
# This generates R.java and the initial APK structure (with AndroidManifest.xml and resources)
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    --java "$GEN_DIR" \
    --auto-add-overlay \
    -o "$APK_UNSIGNED" \
    $RES_ARGS

# 3. Compile Java Sources
echo "Compiling Java sources..."
# Find all Java files (source files + generated R.java)
JAVA_FILES=$(find src "$GEN_DIR" -name "*.java")

if [ -z "$JAVA_FILES" ]; then
    echo "Error: No Java files found to compile."
    exit 1
fi

# Compile using ECJ (Eclipse Compiler for Java)
# Target Java 1.8 for Android
ecj \
    -cp "$ANDROID_JAR" \
    -d "$OBJ_DIR" \
    -source 1.8 \
    -target 1.8 \
    $JAVA_FILES

# 4. Dexing (Convert .class to classes.dex)
echo "Dexing classes..."
# Find all compiled .class files
CLASS_FILES=$(find "$OBJ_DIR" -name "*.class")

if [ -z "$CLASS_FILES" ]; then
    echo "Error: No compiled class files found."
    exit 1
fi

# Run d8 to generate classes.dex in the build directory
d8 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR" \
    $CLASS_FILES

# 5. Package APK
echo "Packaging APK..."
# Add classes.dex to the APK package
cd "$BUILD_DIR"
zip -u "app-unsigned.apk" "classes.dex"
cd ..

# 6. Sign APK
echo "Signing APK..."
# Check if keystore exists, generate one if not
if [ ! -f "$KEYSTORE" ]; then
    echo "Generating debug keystore..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$STORE_PASS" \
        -keypass "$KEY_PASS" \
        -dname "CN=Android Debug,O=Android,C=US"
fi

# Sign the APK
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$STORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$APK_SIGNED" \
    "$APK_UNSIGNED"

echo "Build Complete! Output: $APK_SIGNED"
