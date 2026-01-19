#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "cleaning..."
PLATFORM_JAR="$HOME/.android/android.jar"

# Build classpath for external libraries
LIBS_CLASSPATH=""
if [ -d "libs" ]; then
    for jar in libs/*.jar; do
        [ -f "$jar" ] && LIBS_CLASSPATH="$LIBS_CLASSPATH:$jar"
    done
fi

rm -rf "build"
mkdir -p "build/res_compiled" "build/classes" "build/dex"

echo "compiling res..."
aapt2 compile --dir "res" -o "build/res_compiled"

echo "linking..."
aapt2 link -o "build/app-unsigned.apk"     --manifest "AndroidManifest.xml"     -I "$PLATFORM_JAR"     --java "build/classes"     -R build/res_compiled/*.flat

echo "ecj..."
ecj -d "build/classes"     -classpath "$PLATFORM_JAR$LIBS_CLASSPATH"     $(find src -name "*.java")     "build/classes/com/example/deviceownerapp/R.java"

echo "dexing..."
d8 --lib "$PLATFORM_JAR"    --output "build/dex"    $(find build/classes -name "*.class")    $(find libs -name "*.jar" 2>/dev/null)

echo "zipping..."
zip -uj "build/app-unsigned.apk" "build/dex/classes.dex"

echo "signing..."
apksigner sign --ks "$HOME/.android/debug.keystore" --ks-pass "pass:android" --key-pass "pass:android" --out "build/DeviceAdminApp.apk" "build/app-unsigned.apk"
rm build/*.idsig build/app-unsigned.apk
echo "done! build/DeviceAdminApp.apk"
