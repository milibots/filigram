#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.13
GRADLE_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/vicovpn-dist"
ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
HOME_DIR="$CACHE_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl --fail --location --retry 3 --output "$ZIP.tmp" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    mv "$ZIP.tmp" "$ZIP"
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s  %s\n' "$GRADLE_SHA256" "$ZIP" | sha256sum -c -
  else
    ACTUAL=$(shasum -a 256 "$ZIP" | awk '{print $1}')
    [ "$ACTUAL" = "$GRADLE_SHA256" ] || { echo 'Gradle checksum mismatch' >&2; exit 1; }
  fi
  rm -rf "$HOME_DIR"
  unzip -q "$ZIP" -d "$CACHE_DIR"
fi
exec "$HOME_DIR/bin/gradle" -p "$ROOT_DIR" "$@"
