# Reference Homebrew formula for `amy`, the Amethyst CLI.
#
# This file is NOT consumed by any build in this repo. It is the artifact you
# submit to Homebrew/homebrew-core (`brew bump-formula-pr` / a new-formula PR).
# Once accepted, homebrew-core's copy is the source of truth; keep this in sync
# for reference and to make version bumps a copy-paste.
#
# Why a pre-built jar bundle instead of building from source:
#   homebrew-core builds inside a network sandbox, so a Gradle build cannot
#   resolve its Maven dependencies there. The accepted pattern for JVM tools is
#   to download a pre-built, no-JRE jar bundle and depend on the system openjdk.
#   We publish exactly that as `amy-<version>-jvm.tar.gz` (bin/amy + lib/*.jar,
#   no bundled runtime) from .github/workflows/create-release.yml.
#
# Before submitting: replace the version in the url and the sha256 with the
# values for the actual published release asset:
#   curl -fsSL -o amy-jvm.tar.gz \
#     https://github.com/vitorpamplona/amethyst/releases/download/vX.Y.Z/amy-X.Y.Z-jvm.tar.gz
#   shasum -a 256 amy-jvm.tar.gz
class Amy < Formula
  desc "Command-line Nostr client from the Amethyst project"
  homepage "https://github.com/vitorpamplona/amethyst"
  url "https://github.com/vitorpamplona/amethyst/releases/download/v1.12.1/amy-1.12.1-jvm.tar.gz"
  sha256 "REPLACE_WITH_RELEASE_ASSET_SHA256"
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
