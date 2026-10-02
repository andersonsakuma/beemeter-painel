#!/usr/bin/env bash
# Gera o APK do Colmeia Viva sem Gradle: aapt2 + javac + d8 + apksigner.
# Uso:  JAVA_HOME=<jdk 11+> bash android/build.sh
# Requer: Android SDK (build-tools + platforms/android-34) e um JDK com javac.
# O keystore NÃO fica no repositório: KEYSTORE e KSPASS vêm do ambiente.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)}"
AJAR="$SDK/platforms/android-34/android.jar"
JB="$JAVA_HOME/bin"
: "${KEYSTORE:?defina KEYSTORE}"; : "${KSPASS:?defina KSPASS}"
OUT=build; rm -rf $OUT; mkdir -p $OUT/compiled $OUT/classes $OUT/assets

cp ../index.html $OUT/assets/index.html          # única fonte do painel
"$BT/aapt2.exe" compile --dir res -o $OUT/compiled
"$BT/aapt2.exe" link -I "$AJAR" --manifest AndroidManifest.xml -A $OUT/assets \
  --min-sdk-version 24 --target-sdk-version 34 --version-code "${VERSION_CODE:-1}" --version-name "${VERSION_NAME:-1.0}" \
  $OUT/compiled/*.flat -o $OUT/base.apk
"$JB/javac.exe" --release 8 -Xlint:-options -cp "$AJAR" -d $OUT/classes $(find src -name '*.java')
(cd $OUT/classes && "$JB/jar.exe" cf ../classes.jar .)
JAVA_HOME="$JAVA_HOME" "$BT/d8.bat" --release --lib "$AJAR" --output $OUT $OUT/classes.jar
python - <<PY
import zipfile
z=zipfile.ZipFile("$OUT/base.apk","a"); z.write("$OUT/classes.dex","classes.dex"); z.close()
PY
"$BT/zipalign.exe" -f -p 4 $OUT/base.apk $OUT/aligned.apk
JAVA_HOME="$JAVA_HOME" "$BT/apksigner.bat" sign --ks "$KEYSTORE" --ks-pass env:KSPASS \
  --out "${APK_OUT:-$OUT/colmeia-viva.apk}" $OUT/aligned.apk
JAVA_HOME="$JAVA_HOME" "$BT/apksigner.bat" verify --print-certs "${APK_OUT:-$OUT/colmeia-viva.apk}" | head -3
