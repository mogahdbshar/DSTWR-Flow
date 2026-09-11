#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_DIR="$ROOT_DIR/native/tun2socks"
JNI_DIR="$ROOT_DIR/app/src/main/jniLibs"
NDK_DIR="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [[ -z "$NDK_DIR" || ! -d "$NDK_DIR" ]]; then
  echo "ANDROID_NDK_HOME/ANDROID_NDK_ROOT is required" >&2
  exit 1
fi

if ! command -v go >/dev/null 2>&1; then
  echo "Go is required to build the native forwarding engine" >&2
  exit 1
fi

mkdir -p "$JNI_DIR/arm64-v8a" "$JNI_DIR/armeabi-v7a"

cd "$NATIVE_DIR"
go mod download

env CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  CC="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang" \
  go build -trimpath -buildmode=c-shared -ldflags="-s -w" \
  -o "$JNI_DIR/arm64-v8a/libdstwr_tun2socks.so" .

env CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 \
  CC="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi24-clang" \
  go build -trimpath -buildmode=c-shared -ldflags="-s -w" \
  -o "$JNI_DIR/armeabi-v7a/libdstwr_tun2socks.so" .

rm -f "$JNI_DIR/arm64-v8a/libdstwr_tun2socks.h" "$JNI_DIR/armeabi-v7a/libdstwr_tun2socks.h"

echo "Native tun2socks engine built for arm64-v8a and armeabi-v7a."
