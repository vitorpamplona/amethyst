#!/bin/bash
# Session start hook: Configure proxy auth, SSL trust, and Android SDK for Claude Code on the web

# Only run in remote (web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# --- Fix no_proxy: remove *.google.com and *.googleapis.com so traffic routes through proxy ---
# The default no_proxy in Claude Code web includes *.google.com and *.googleapis.com,
# which causes JVM/curl to bypass the proxy for Google Maven (dl.google.com).
# These domains MUST go through the proxy for authenticated egress to work.
fix_no_proxy() {
  local var="$1"
  local val="${!var:-}"
  if [ -n "$val" ]; then
    # Remove *.google.com, *.googleapis.com entries from the comma-separated list
    local fixed
    fixed=$(echo "$val" | tr ',' '\n' | grep -v -E '^\*\.google\.com$|^\*\.googleapis\.com$' | tr '\n' ',' | sed 's/,$//')
    export "$var=$fixed"
  fi
}

fix_no_proxy no_proxy
fix_no_proxy NO_PROXY

# --- Fix JAVA_TOOL_OPTIONS: remove nonProxyHosts that bypass Google domains ---
# JAVA_TOOL_OPTIONS may contain -Dhttp.nonProxyHosts=...|*.google.com|*.googleapis.com
# which prevents the JVM from routing Google Maven traffic through the proxy.
if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
  # Remove the nonProxyHosts entries for google.com and googleapis.com from the JVM options
  # Strategy: rewrite the -Dhttp.nonProxyHosts value to exclude google patterns
  JAVA_TOOL_OPTIONS=$(echo "$JAVA_TOOL_OPTIONS" | sed -E '
    s/-Dhttp\.nonProxyHosts=[^ ]*/\n&\n/g
  ' | while IFS= read -r line; do
    if [[ "$line" == -Dhttp.nonProxyHosts=* ]]; then
      # Extract the value, remove google patterns, reconstruct
      val="${line#-Dhttp.nonProxyHosts=}"
      fixed=$(echo "$val" | tr '|' '\n' | grep -v -E '^\*\.google\.com$|^\*\.googleapis\.com$' | tr '\n' '|' | sed 's/|$//')
      echo "-Dhttp.nonProxyHosts=$fixed"
    else
      echo "$line"
    fi
  done | tr '\n' ' ' | sed 's/  */ /g; s/^ //; s/ $//')
  export JAVA_TOOL_OPTIONS
fi

# Export fixed env vars for the session
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export no_proxy='${no_proxy:-}'" >> "$CLAUDE_ENV_FILE"
  echo "export NO_PROXY='${NO_PROXY:-}'" >> "$CLAUDE_ENV_FILE"
  echo "export JAVA_TOOL_OPTIONS='$JAVA_TOOL_OPTIONS'" >> "$CLAUDE_ENV_FILE"
fi

echo "Fixed proxy bypass settings (no_proxy, JAVA_TOOL_OPTIONS)" >&2

# --- Proxy credentials: configure Maven/Gradle if authenticated proxy is set ---
proxy="${https_proxy:-${HTTPS_PROXY:-}}"
if [ -n "$proxy" ] && echo "$proxy" | grep -q '@'; then
  rest="${proxy#*://}"
  userpass="${rest%@*}"
  hostport="${rest##*@}"
  user="${userpass%%:*}"
  pass="${userpass#*:}"
  host="${hostport%%:*}"
  port="${hostport##*:}"
  port="${port%/}"

  mkdir -p ~/.m2
  cat > ~/.m2/settings.xml << EOF
<settings>
  <proxies>
    <proxy>
      <id>ccw</id><active>true</active><protocol>https</protocol>
      <host>$host</host><port>$port</port>
      <username>$user</username>
      <password><![CDATA[$pass]]></password>
    </proxy>
  </proxies>
</settings>
EOF

  # Force wagon transport for Maven 3.9+ proxy auth compatibility
  cat > ~/.mavenrc << 'MAVENRC'
MAVEN_OPTS="$MAVEN_OPTS -Dmaven.resolver.transport=wagon"
MAVENRC

  mkdir -p ~/.gradle
  cat > ~/.gradle/gradle.properties << EOF
systemProp.https.proxyHost=$host
systemProp.https.proxyPort=$port
systemProp.https.proxyUser=$user
systemProp.https.proxyPassword=$pass
systemProp.http.proxyHost=$host
systemProp.http.proxyPort=$port
systemProp.http.proxyUser=$user
systemProp.http.proxyPassword=$pass
# Override nonProxyHosts: route all external traffic (incl. *.google.com) through proxy
systemProp.http.nonProxyHosts=localhost|127.0.0.1
systemProp.https.nonProxyHosts=localhost|127.0.0.1
# Use Ubuntu's Java trust store (includes Anthropic TLS inspection CA) for all Gradle JVMs.
# This is needed because Gradle may download a custom JDK (e.g. JetBrains) whose bundled
# trust store doesn't include the Anthropic CA, causing TLS inspection failures.
systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
systemProp.javax.net.ssl.trustStoreType=JKS
systemProp.javax.net.ssl.trustStorePassword=changeit
systemProp.jdk.http.auth.tunneling.disabledSchemes=
systemProp.jdk.http.auth.proxying.disabledSchemes=
EOF

  echo "Configured Maven/Gradle proxy from HTTPS_PROXY" >&2
fi

# --- SSL trust: import Anthropic TLS inspection CA into JVM trust stores ---
ANTHROPIC_CA_PEM=$(python3 -c "
import re, ssl, sys
try:
    with open('/etc/ssl/certs/ca-certificates.crt') as f:
        certs = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', f.read(), re.DOTALL)
    for cert in certs:
        der = ssl.PEM_cert_to_DER_cert(cert)
        if b'Anthropic' in der and b'sandbox-egress-production' in der:
            print(cert)
            break
except Exception as e:
    sys.stderr.write(f'CA extraction failed: {e}\n')
" 2>/dev/null)

if [ -n "$ANTHROPIC_CA_PEM" ]; then
  TMPCA=$(mktemp /tmp/anthropic-ca.XXXXXX.pem)
  echo "$ANTHROPIC_CA_PEM" > "$TMPCA"
  for cacerts in \
    /usr/lib/jvm/java-21-openjdk-amd64/lib/security/cacerts \
    /root/.gradle/jdks/*/lib/security/cacerts; do
    [ -f "$cacerts" ] || continue
    keytool -list -keystore "$cacerts" -storepass changeit \
      -alias anthropic-egress-production-ca >/dev/null 2>&1 && continue
    keytool -import \
      -alias anthropic-egress-production-ca \
      -file "$TMPCA" \
      -keystore "$cacerts" \
      -storepass changeit \
      -noprompt >/dev/null 2>&1 && \
      echo "Imported Anthropic CA into $cacerts" >&2
  done
  rm -f "$TMPCA"
fi

# --- Project setup: local.properties and env vars (before SDK download so these always run) ---
ANDROID_SDK_DIR="/root/android-sdk"
REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || echo "${CLAUDE_PROJECT_DIR:-/home/user/amethyst}")"

# Create local.properties if missing
LOCAL_PROPS="$REPO_ROOT/local.properties"
if [ ! -f "$LOCAL_PROPS" ]; then
  echo "sdk.dir=$ANDROID_SDK_DIR" > "$LOCAL_PROPS"
  echo "Created local.properties with sdk.dir=$ANDROID_SDK_DIR"
fi

# Export ANDROID_HOME for the session
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$ANDROID_SDK_DIR" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=$ANDROID_SDK_DIR" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\$PATH:$ANDROID_SDK_DIR/platform-tools" >> "$CLAUDE_ENV_FILE"
fi

# --- Install standalone ktlint (fallback for spotlessApply when Gradle can't resolve AGP) ---
# The egress proxy may block dl.google.com (Google Maven), preventing Gradle from resolving
# the Android Gradle Plugin. Since spotlessApply depends on Gradle's plugin resolution,
# we install ktlint standalone as a reliable alternative.
KTLINT_VERSION="1.5.0"
KTLINT_BIN="/usr/local/bin/ktlint"
if [ ! -x "$KTLINT_BIN" ]; then
  echo "Installing ktlint $KTLINT_VERSION..." >&2
  if curl -fsSL "https://github.com/pinterest/ktlint/releases/download/${KTLINT_VERSION}/ktlint" -o "$KTLINT_BIN" 2>/dev/null; then
    chmod +x "$KTLINT_BIN"
    echo "Installed ktlint $KTLINT_VERSION to $KTLINT_BIN" >&2
  else
    echo "WARNING: Failed to install ktlint (GitHub may be unreachable)" >&2
  fi
fi

# Create a spotless-apply wrapper that mimics the Gradle spotless configuration
cat > /usr/local/bin/spotless-apply << 'SPOTLESS_SCRIPT'
#!/bin/bash
# Standalone spotless-apply: runs ktlint with the same settings as the Gradle spotless plugin.
# Use this when ./gradlew spotlessApply fails due to proxy/network issues.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

if ! command -v ktlint &>/dev/null; then
  echo "ERROR: ktlint not found. Install it first." >&2
  exit 1
fi

echo "Running ktlint format on Kotlin sources..."
# Find all Kotlin source files matching the spotless target pattern (src/**/*.kt)
# Exclude build directories
ktlint --format "**/*.kt" 2>&1 | grep -v "^$" || true

echo "Done. Files formatted with ktlint."
SPOTLESS_SCRIPT
chmod +x /usr/local/bin/spotless-apply

# --- Android SDK download (non-fatal: warns if dl.google.com is blocked) ---
SDK_REPO_BASE="https://dl.google.com/android/repository"

install_sdk_package() {
  local zip_url="$1"
  local dest_dir="$2"
  local inner_dir="$3"  # top-level dir inside the zip

  if [ -d "$dest_dir" ]; then
    return 0
  fi

  echo "Downloading $zip_url..." >&2
  local TMP_ZIP
  TMP_ZIP=$(mktemp /tmp/sdk-pkg.XXXXXX.zip)
  if ! curl -fsSL "$zip_url" -o "$TMP_ZIP" 2>/dev/null; then
    rm -f "$TMP_ZIP"
    echo "WARNING: Failed to download $zip_url (dl.google.com may be blocked by proxy)" >&2
    echo "  -> Add dl.google.com to the egress proxy allowlist to fix this" >&2
    return 1
  fi

  local TMP_DIR
  TMP_DIR=$(mktemp -d)
  unzip -q "$TMP_ZIP" -d "$TMP_DIR"
  rm -f "$TMP_ZIP"

  mkdir -p "$(dirname "$dest_dir")"
  mv "$TMP_DIR/$inner_dir" "$dest_dir"
  rm -rf "$TMP_DIR"
  echo "Installed to $dest_dir" >&2
}

sdk_ok=true

# Install Android platform 36
if ! install_sdk_package \
  "$SDK_REPO_BASE/platform-36_r02.zip" \
  "$ANDROID_SDK_DIR/platforms/android-36" \
  "android-36"; then
  sdk_ok=false
fi

# Install build-tools 36.0.0 (zip uses "android-16" as inner dir name)
if ! install_sdk_package \
  "$SDK_REPO_BASE/build-tools_r36_linux.zip" \
  "$ANDROID_SDK_DIR/build-tools/36.0.0" \
  "android-16"; then
  sdk_ok=false
fi

# Install platform-tools
if ! install_sdk_package \
  "$SDK_REPO_BASE/platform-tools_r37.0.0-linux.zip" \
  "$ANDROID_SDK_DIR/platform-tools" \
  "platform-tools"; then
  sdk_ok=false
fi

if [ "$sdk_ok" = true ]; then
  # Accept SDK licenses (create license files manually)
  echo "Writing SDK license files..." >&2
  mkdir -p "$ANDROID_SDK_DIR/licenses"
  echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_SDK_DIR/licenses/android-sdk-license"
  echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" >> "$ANDROID_SDK_DIR/licenses/android-sdk-license"
  echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" > "$ANDROID_SDK_DIR/licenses/android-sdk-preview-license"
  echo -e "\n504667f4c0de7af1a06de9f4b1727b84351f2910" >> "$ANDROID_SDK_DIR/licenses/android-sdk-preview-license"
  echo -e "\nd975f751698a77b662f1254ddbeed3901e976f5a" > "$ANDROID_SDK_DIR/licenses/intel-android-extra-license"
  echo "Android SDK setup complete." >&2
else
  echo "WARNING: Android SDK download failed. dl.google.com is likely blocked by the egress proxy." >&2
  echo "  -> Gradle builds requiring AGP will fail until dl.google.com is added to the proxy allowlist." >&2
  echo "  -> Use 'spotless-apply' (standalone ktlint) instead of './gradlew spotlessApply'." >&2
fi

# Warm up Gradle wrapper (non-fatal)
cd "$REPO_ROOT"
./gradlew --version > /dev/null 2>&1 || true

echo "Session start hook complete." >&2
