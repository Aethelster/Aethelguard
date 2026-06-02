#!/usr/bin/env sh
set -e

MAVEN_VERSION="3.9.10"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
DIST_DIR="$WRAPPER_DIR/dists/apache-maven-$MAVEN_VERSION"
MAVEN_HOME="$DIST_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_CMD="$MAVEN_HOME/bin/mvn"
DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
DIST_TGZ="$DIST_DIR/apache-maven-$MAVEN_VERSION-bin.tar.gz"

if [ ! -x "$MAVEN_CMD" ]; then
  mkdir -p "$DIST_DIR"
  echo "Downloading Apache Maven $MAVEN_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$DIST_TGZ"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DIST_URL" -O "$DIST_TGZ"
  else
    echo "curl or wget is required to download Apache Maven." >&2
    exit 1
  fi
  tar -xzf "$DIST_TGZ" -C "$DIST_DIR"
fi

exec "$MAVEN_CMD" "$@"
