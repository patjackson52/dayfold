#!/usr/bin/env bash
# Install the `dayfold` CLI into a headless / cloud environment (scheduled-task
# setup). Idempotent and SELF-CONTAINED: works inside the dayfold repo OR any
# other repo's cloud env — if the CLI source isn't present locally it shallow-
# clones the public repo and builds. Auth is NOT configured here; the task
# supplies the legacy household token via env (DAYFOLD_API/FAMILY_ID/
# HOUSEHOLD_SECRET). See processes/scheduled-authoring-loop.md.
#
# Usage from any repo's setup (the dayfold repo is public, no auth needed):
#   curl -fsSL https://raw.githubusercontent.com/SloopWorks/dayfold/main/scripts/install-dayfold-cli.sh | bash
# or, if a release has been cut (JRE-only, no JDK/Gradle, fast):
#   DAYFOLD_CLI_VERSION=0.1.0 bash install-dayfold-cli.sh
#
# Env knobs:
#   DAYFOLD_CLI_VERSION  if set, download that release tarball instead of building
#   DAYFOLD_CLI_REF      git ref to build from when cloning (default: main)
#   DAYFOLD_PREFIX       install dir (default: /opt/dayfold)
#   DAYFOLD_BIN_LINK     PATH symlink (default: /usr/local/bin/dayfold)
#   JAVA17_HOME          JDK 17 home (default: /usr/lib/jvm/java-17-openjdk-amd64)
set -euo pipefail

REPO_SLUG="SloopWorks/dayfold"
PREFIX="${DAYFOLD_PREFIX:-/opt/dayfold}"
BIN_LINK="${DAYFOLD_BIN_LINK:-/usr/local/bin/dayfold}"
REF="${DAYFOLD_CLI_REF:-main}"

if command -v dayfold >/dev/null 2>&1; then
  echo "dayfold already on PATH: $(command -v dayfold)"; exit 0
fi

install_tree() {  # $1 = a built install/dayfold tree (bin/ + lib/)
  rm -rf "$PREFIX"; mkdir -p "$(dirname "$PREFIX")" "$(dirname "$BIN_LINK")"
  cp -r "$1" "$PREFIX"; ln -sf "$PREFIX/bin/dayfold" "$BIN_LINK"
  echo "Installed: $("$BIN_LINK" --version)"
}

# ── Fast path: prebuilt release tarball (needs only a JRE >=17 at runtime) ──
if [ -n "${DAYFOLD_CLI_VERSION:-}" ]; then
  V="$DAYFOLD_CLI_VERSION"
  URL="https://github.com/$REPO_SLUG/releases/download/cli-v$V/dayfold-$V.tar"
  echo "Downloading release $V from $URL…"
  TMP="$(mktemp -d)"; curl -fsSL "$URL" -o "$TMP/dayfold.tar"
  tar -xf "$TMP/dayfold.tar" -C "$TMP"        # yields $TMP/dayfold-$V/{bin,lib}
  install_tree "$TMP/dayfold-$V"; rm -rf "$TMP"; exit 0
fi

# ── Build-from-source path (no release published yet) ──
# Locate the CLI source: local checkout if we're in the dayfold repo, else clone.
if [ -f "apps/cli/build.gradle.kts" ]; then
  CLI_DIR="$(pwd)/apps/cli"
elif [ -f "$(dirname "${BASH_SOURCE[0]}")/../apps/cli/build.gradle.kts" ]; then
  CLI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/apps/cli"
else
  echo "CLI source not local — shallow-cloning $REPO_SLUG@$REF (public)…"
  SRC="$(mktemp -d)"
  git clone --depth 1 --branch "$REF" "https://github.com/$REPO_SLUG.git" "$SRC/dayfold"
  CLI_DIR="$SRC/dayfold/apps/cli"
fi

# The build pins jvmToolchain(17); Gradle won't substitute another major and
# toolchain auto-download isn't configured. Ensure a JDK 17 and point Gradle at it.
JDK17="${JAVA17_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
if [ ! -x "$JDK17/bin/javac" ]; then
  echo "Installing OpenJDK 17 (build pins jvmToolchain 17)…"
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -y && apt-get install -y openjdk-17-jdk-headless
  else
    echo "ERROR: need JDK 17 but no apt-get. Install OpenJDK 17 and set JAVA17_HOME." >&2; exit 1
  fi
fi

echo "Building the CLI (installDist)…"
( cd "$CLI_DIR" && ./gradlew --no-daemon installDist -q \
    -Porg.gradle.java.installations.paths="$JDK17" )
install_tree "$CLI_DIR/build/install/dayfold"
