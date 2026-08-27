# Reference Homebrew formula for `amy`, the Amethyst CLI.
#
# Submit this to Homebrew/homebrew-core (new-formula PR) or drop it into a
# personal tap (`Formula/amy.rb`) for an instant `brew install <tap>/amy`.
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
#
# Submission status (2026-08-24): NOT yet submitted. The `amethyst-nostr` cask
# is live in Homebrew/homebrew-cask, and Homebrew allows a non-maintainer only
# ONE open AI-assisted pull request at a time, so that had to merge first — it
# has. Two things to settle before opening this one:
#   * The bundle is ~70 MB because `:commons` drags Compose/Skiko onto the CLI
#     classpath. homebrew-core reviewers do question payload size; a
#     commons core/ui split would shrink it and smooth review.
#   * `brew audit --new --formula` has not been run end to end (it needs the
#     published tarball). `brew style` is clean as of this commit.
#
# Do NOT "simplify" this formula to match `desktopApp/packaging/homebrew/
# amethyst-nostr.rb`. That cask had its `livecheck` block and inline comments
# stripped on review, but homebrew-CASK and homebrew-CORE differ here: in a
# sample of 300 core formulae with GitHub-release URLs, 127 declare `livecheck`
# (62 with `:github_latest`), and 109 of 200 carry indented inline comments.
# The `livecheck` below is load-bearing — it is what lets BrewTestBot open the
# version-bump PRs.
class Amy < Formula
  desc "Nostr client from the Amethyst project"
  homepage "https://github.com/vitorpamplona/amethyst"
  url "https://github.com/vitorpamplona/amethyst/releases/download/v1.14.0/amy-1.14.0-jvm.tar.gz"
  sha256 "284a8dbbead65db09d27b94b3575daa3d645826a037546fda91297561160fb5b"
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
    (bin/"amy").write_env_script libexec/"bin/amy", JAVA_HOME: formula_opt_prefix("openjdk")
  end

  test do
    assert_match "Amethyst command-line interface", shell_output("#{bin}/amy --help 2>&1")
  end
end
