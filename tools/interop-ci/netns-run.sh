#!/usr/bin/env bash
#
# netns-run.sh — run a command in a fresh network namespace that has only
# loopback, so nothing it starts can reach any other host: no public relay, no
# Blossom server, no DNS. Relays, blob stores and counterpart daemons the
# command boots on 127.0.0.0/8 (or on an --addr) still talk to each other.
#
#   tools/interop-ci/netns-run.sh [--addr CIDR]... -- CMD [ARG...]
#
#   --addr CIDR   also put CIDR on the namespace's lo, e.g. 192.0.2.2/32. amy drops
#                 loopback and RFC 1918 relays from other accounts' relay lists, so
#                 a test that resolves someone's kind:10050 / NIP-65 list needs its
#                 relay on an address outside those ranges. In here that address
#                 is just another name for loopback.
#
# Before CMD runs, the script checks the namespace really is cut off (a TCP
# connect to 1.1.1.1:443 must fail) and refuses to run CMD otherwise. CMD runs
# as the calling user, with the proxy variables dropped (there is nothing to
# proxy to), and NETNS_ISOLATED=1 in its environment. Every process left in the
# namespace is killed when CMD exits, and the namespace is deleted.
#
# Needs iproute2 and root, or passwordless sudo (GitHub's Ubuntu runners have both).
set -euo pipefail

ADDRS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --addr) ADDRS+=("$2"); shift 2 ;;
    --) shift; break ;;
    -h|--help) sed -n '3,22p' "$0" | sed 's/^# \?//'; exit 0 ;;
    *) break ;;
  esac
done
[[ $# -gt 0 ]] || { echo "usage: $0 [--addr CIDR]... -- CMD [ARG...]" >&2; exit 2; }

command -v ip >/dev/null || { echo "netns-run: iproute2 (ip) is required" >&2; exit 2; }
SUDO=()
if [[ "$(id -u)" -ne 0 ]]; then
  sudo -n true 2>/dev/null || { echo "netns-run: needs root or passwordless sudo" >&2; exit 2; }
  SUDO=(sudo -n)
fi

NS="interop-$$"
# shellcheck disable=SC2317  # invoked by the EXIT trap
cleanup() {
  local pids
  pids=$("${SUDO[@]}" ip netns pids "$NS" 2>/dev/null || true)
  if [[ -n "$pids" ]]; then
    # shellcheck disable=SC2086  # one pid per word
    "${SUDO[@]}" kill -TERM $pids 2>/dev/null || true
    sleep 2
    pids=$("${SUDO[@]}" ip netns pids "$NS" 2>/dev/null || true)
    # shellcheck disable=SC2086
    if [[ -n "$pids" ]]; then "${SUDO[@]}" kill -KILL $pids 2>/dev/null || true; fi
  fi
  "${SUDO[@]}" ip netns del "$NS" 2>/dev/null || true
}
trap cleanup EXIT

"${SUDO[@]}" ip netns add "$NS"
"${SUDO[@]}" ip -n "$NS" link set lo up
for a in "${ADDRS[@]}"; do
  "${SUDO[@]}" ip -n "$NS" addr add "$a" dev lo
done

# Prove the cut before trusting it.
if ! "${SUDO[@]}" ip netns exec "$NS" python3 -I -c '
import socket, sys
for host, port in (("1.1.1.1", 443), ("8.8.8.8", 53)):
    try:
        socket.create_connection((host, port), timeout=3).close()
    except OSError as e:
        print(f"netns-run: {host}:{port} unreachable ({e.strerror})")
        continue
    print(f"netns-run: {host}:{port} is REACHABLE from the namespace", file=sys.stderr)
    sys.exit(1)
'; then
  echo "netns-run: the namespace is not isolated; refusing to run" >&2
  exit 3
fi
echo "netns-run: namespace $NS, lo${ADDRS[*]:+ + ${ADDRS[*]}}; external network unreachable"

# Proxy settings would only point the JVM at a host it cannot reach.
JTO="$(printf '%s' "${JAVA_TOOL_OPTIONS:-}" | sed -E 's/-D(https?|socks)\.(proxyHost|proxyPort|nonProxyHosts)=[^ ]*//g')"

# CMD gets the caller's environment as explicit NAME=VALUE arguments. sudo resets the
# environment (the outer `sudo ip netns exec` already drops it), and --preserve-env
# cannot bring back what that dropped; the sudo-rs on current runners ignores it
# outright. Lost, the harness never saw INTEROP_RESULTS_DIR, so every suite recorded
# 0 results and read as aborted while its scenarios passed.
ENV_ARGS=()
while IFS= read -r name; do
  case "$name" in
    HTTPS_PROXY|HTTP_PROXY|https_proxy|http_proxy|ALL_PROXY|all_proxy|JAVA_TOOL_OPTIONS) ;;
    *) ENV_ARGS+=("$name=${!name}") ;;
  esac
done < <(compgen -e)

AS_USER=()
if [[ ${#SUDO[@]} -gt 0 ]]; then
  AS_USER=(sudo -n -u "$(id -un)")
fi

set +e
"${SUDO[@]}" ip netns exec "$NS" "${AS_USER[@]}" env -i "${ENV_ARGS[@]}" \
  JAVA_TOOL_OPTIONS="$JTO" NETNS_ISOLATED=1 AMY_LOCAL_ONLY=1 \
  "$@"
rc=$?
set -e
exit "$rc"
