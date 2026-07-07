# Reference Homebrew formula for `amy`, the Amethyst CLI.
#
# Reference Homebrew formula for `amy`, the Amethyst CLI. Submit this to
# Homebrew/homebrew-core (new-formula PR) or drop it into a personal tap
# (`Formula/amy.rb`) for an instant `brew install <tap>/amy`.
#
# The url + sha256 below are kept in sync automatically on every stable release
# by .github/workflows/bump-homebrew-formula.yml (it downloads the published
# `amy-<version>-jvm.tar.gz`, recomputes the sha256, and opens a PR). To refresh
# by hand instead:
#   curl -fsSL -o amy-jvm.tar.gz \
#     https://github.com/vitorpamplona/amethyst/releases/download/vX.Y.Z/amy-X.Y.Z-jvm.tar.gz
#   shasum -a 256 amy-jvm.tar.gz
#
# Why a pre-built jar bundle instead of building from source:
#   homebrew-core builds inside a network sandbox, so a Gradle build cannot
#   resolve its Maven dependencies there. The accepted pattern for JVM tools is
#   to download a pre-built, no-JRE jar bundle and depend on the system openjdk.
#   We publish exactly that as `amy-<version>-jvm.tar.gz` (bin/amy + lib/*.jar,
#   no bundled runtime) from .github/workflows/create-release.yml.
class Amy < Formula
  desc "Command-line Nostr client from the Amethyst project"
  homepage "https://github.com/vitorpamplona/amethyst"
  url "https://github.com/vitorpamplona/amethyst/releases/download/v1.12.6/amy-1.12.6-jvm.tar.gz"
  sha256 "209316d704a4622ddef1fd86b958b7619e9d049c20f3543dff60348ec73affd6"
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
    # Wrapper on PATH that pins JAVA_HOME to Homebrew's openjdk so amy runs
    # regardless of the user's own Java setup.
    (bin/"amy").write_env_script libexec/"bin/amy", JAVA_HOME: Formula["openjdk"].opt_prefix
  end

  test do
    assert_match "Amethyst command-line interface", shell_output("#{bin}/amy --help 2>&1")
  end
end
