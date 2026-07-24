# Reference Homebrew formula for `geode`, the standalone Nostr relay.
#
# Submit this to Homebrew/homebrew-core (new-formula PR) or drop it into a
# personal tap (`Formula/geode.rb`) for an instant `brew install <tap>/geode`.
#
# The url + sha256 below are kept in sync automatically on every stable release
# by .github/workflows/bump-homebrew-geode-formula.yml (it downloads the
# published `geode-<version>-jvm.tar.gz`, recomputes the sha256, and opens a
# PR). To refresh by hand instead:
#   curl -fsSL -o geode-jvm.tar.gz \
#     https://github.com/vitorpamplona/amethyst/releases/download/vX.Y.Z/geode-X.Y.Z-jvm.tar.gz
#   shasum -a 256 geode-jvm.tar.gz
#
# Why a pre-built jar bundle instead of building from source:
#   homebrew-core builds inside a network sandbox, so a Gradle build cannot
#   resolve its Maven dependencies there. The accepted pattern for JVM tools is
#   to download a pre-built, no-JRE jar bundle and depend on the system openjdk.
#   We publish exactly that as `geode-<version>-jvm.tar.gz` (bin/geode +
#   lib/*.jar, no bundled runtime) from .github/workflows/create-release.yml.
#
# Note: geode is a long-running relay daemon. Homebrew is a convenient way to
# INSTALL it on a workstation for local testing; for a production deployment
# prefer the Docker image or the .deb/.rpm + systemd unit (see geode/README.md).
class Geode < Formula
  desc "Standalone Nostr relay from the Amethyst project"
  homepage "https://github.com/vitorpamplona/amethyst"
  url "https://github.com/vitorpamplona/amethyst/releases/download/v1.12.6/geode-1.12.6-jvm.tar.gz"
  sha256 "0000000000000000000000000000000000000000000000000000000000000000"
  license "MIT"

  # Lets homebrew-core's BrewTestBot auto-open version-bump PRs when a new
  # stable GitHub release appears.
  livecheck do
    url :stable
    strategy :github_latest
  end

  depends_on "openjdk"

  def install
    # Tarball top level is bin/ and lib/ (the Gradle installDist layout).
    libexec.install Dir["*"]
    # Wrapper on PATH that pins JAVA_HOME to Homebrew's openjdk so geode runs
    # regardless of the user's own Java setup.
    (bin/"geode").write_env_script libexec/"bin/geode", JAVA_HOME: Formula["openjdk"].opt_prefix
  end

  test do
    assert_match "geode", shell_output("#{bin}/geode --version 2>&1")
  end
end
