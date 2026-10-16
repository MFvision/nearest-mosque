#!/usr/bin/env bash
# Packs the Compose for Desktop app image (`gradlew createDistributable` on Linux) into one .AppImage file.
# Compose's own AppImage target stops at the folder; this adds AppRun, the menu entry and the icon, and runs
# appimagetool with a pinned runtime (both downloaded and checked by .github/workflows/desktop-release.yml).
#
#   build-appimage.sh <version> <appimagetool> <runtime-x86_64>
#
# Writes build/appimage/near-mosque-<version>-x86_64.AppImage. Needs no FUSE (appimagetool is extracted and run).
set -euo pipefail
version="$1"; tool="$(readlink -f "$2")"; runtime="$(readlink -f "$3")"
here="$(cd "$(dirname "$0")/.." && pwd)"
app="$here/build/compose/binaries/main/app/Near Mosque"
out="$here/build/appimage"
appdir="$out/NearMosque.AppDir"
[ -x "$app/bin/Near Mosque" ] || { echo "No app image at $app: run ./gradlew createDistributable first" >&2; exit 1; }

rm -rf "$appdir"
mkdir -p "$appdir"
# Hard links where the file system allows (the app image is only read), else a copy.
cp -al "$app/." "$appdir/" 2>/dev/null || { rm -rf "$appdir"; mkdir -p "$appdir"; cp -a "$app/." "$appdir/"; }
cp "$here/src/main/icon/app.png" "$appdir/near-mosque.png"
ln -s near-mosque.png "$appdir/.DirIcon"

cat > "$appdir/AppRun" <<'RUN'
#!/bin/sh
# The AppImage's entry point: the jpackage launcher inside it, with the arguments given (e.g. --minimized).
here="$(dirname "$(readlink -f "$0")")"
exec "$here/bin/Near Mosque" "$@"
RUN
chmod +x "$appdir/AppRun"

cat > "$appdir/near-mosque.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Near Mosque
Comment=Prayer times, Adhan, Qibla, nearby mosques, Ask and classical books
Exec=near-mosque
Icon=near-mosque
Terminal=false
Categories=Utility;
StartupWMClass=sa-zood-nearmosque-desktop-MainKt
X-AppImage-Version=$version
DESKTOP

target="$out/near-mosque-$version-x86_64.AppImage"
rm -f "$target"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --no-appstream --runtime-file "$runtime" "$appdir" "$target"
chmod +x "$target"
rm -rf "$appdir"
echo "AppImage: $target"
