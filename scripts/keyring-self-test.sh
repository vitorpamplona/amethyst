#!/usr/bin/env bash
# Runs a packaged Amethyst's `--keyring-self-test` and fails unless it reports PASS.
#
# The self-test writes, reads back and deletes a probe secret in the OS keyring through the same
# code a login uses (see commons/plans/2026-10-12-desktop-key-storage.md). It is run on the
# packaged app because that is where the release-only breakages live: classes ProGuard stripped,
# a jar whose signature ProGuard invalidated, a sandbox permission the Flatpak lacks.
#
#   Linux:   runs inside a fresh D-Bus session with an unlocked GNOME Keyring, as a desktop login
#            provides. Needs the gnome-keyring and dbus packages.
#   macOS:   uses the runner's keychain.
#   Windows: uses Credential Manager. The app is a GUI-subsystem executable with no console, so the
#            result also comes back through AMETHYST_SELF_TEST_REPORT.
#
# Usage: keyring-self-test.sh <command that starts the app> [args...]
#   e.g. keyring-self-test.sh /opt/amethyst/bin/Amethyst
#        keyring-self-test.sh flatpak run com.vitorpamplona.amethyst.Amethyst
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <app command> [args...]" >&2
  exit 2
fi

TIMEOUT_SECS=180
REPORT="$(mktemp)"
LOG="$(mktemp)"
export AMETHYST_SELF_TEST_REPORT="$REPORT"

rc=0
case "$(uname -s)" in
  Linux)
    export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-$(mktemp -d)}"
    dbus-run-session -- bash -c '
      set -euo pipefail
      echo -n ci-keyring | gnome-keyring-daemon --unlock --components=secrets >/dev/null
      eval "$(gnome-keyring-daemon --start --components=secrets)"
      timeout "$0" "$@" --keyring-self-test
    ' "$TIMEOUT_SECS" "$@" 2>&1 | tee "$LOG" || rc=$?
    ;;
  *)
    # No `timeout` on macOS or Git Bash: a watchdog stops a run that hangs on an unanswered
    # keychain dialog instead of holding the job until its own timeout.
    "$@" --keyring-self-test > "$LOG" 2>&1 &
    pid=$!
    ( sleep "$TIMEOUT_SECS"; kill "$pid" 2>/dev/null ) &
    watchdog=$!
    wait "$pid" || rc=$?
    kill "$watchdog" 2>/dev/null || true
    cat "$LOG"
    ;;
esac

echo "self-test report: $(cat "$REPORT" 2>/dev/null || true)"
if [[ $rc -ne 0 ]] || ! { grep -q '^PASS' "$REPORT" 2>/dev/null || grep -q 'keyring-self-test\] PASS' "$LOG"; }; then
  echo "::error::The packaged app could not use the OS keyring (exit $rc). See the output above."
  exit 1
fi
echo "Keyring self-test passed."
