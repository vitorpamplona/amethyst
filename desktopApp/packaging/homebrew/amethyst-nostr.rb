# Reference Homebrew Cask for the Amethyst desktop app.
#
# THIS IS A MIRROR. The live cask is Homebrew/homebrew-cask
# `Casks/a/amethyst-nostr.rb` (merged 2026-08-24). Keep this file byte-identical
# to it below the header, so a diff against upstream is meaningful. Note that
# `scripts/bump-homebrew-cask.sh` bumps upstream via `brew bump-cask-pr`, which
# edits the upstream cask in place — it only reads version + sha256 from here,
# so this file is documentation, not the thing that ships.
#
# The release matrix (.github/workflows/create-release.yml) builds an
# Apple-Silicon DMG only (no Intel DMG), so this cask is arm64-only.
#
# version + sha256 track the published
# `amethyst-desktop-<version>-macos-arm64.dmg`; bump-homebrew.yml keeps them
# current on each stable release. To refresh by hand:
#   curl -fsSL -o amethyst.dmg \
#     https://github.com/vitorpamplona/amethyst/releases/download/vX.Y.Z/amethyst-desktop-X.Y.Z-macos-arm64.dmg
#   shasum -a 256 amethyst.dmg
#
# ---------------------------------------------------------------------------
# Why the cask body is this bare (review feedback on homebrew-cask#282745)
# ---------------------------------------------------------------------------
# The submitted version carried a `livecheck` block and two explanatory
# comments. A maintainer removed all three. Do not add them back:
#
#   * `livecheck do url :url; strategy :github_latest end` — redundant.
#     Homebrew infers the strategy from a GitHub release URL. Verified that
#     `brew audit --new --cask` and `brew style` both still pass without it.
#   * Inline comments — homebrew-cask house style is terse. The rationale
#     lives here instead, where it is useful to us and invisible upstream.
#
# `conflicts_with cask: "amethyst"` is NOT optional: the unrelated tiling
# window manager (cask `amethyst`, ianyh/Amethyst) installs its own
# `Amethyst.app`, so the two cannot coexist in /Applications.
#
# The `zap` paths were verified against application source, not docs:
#   ~/.amethyst                            AccountManager.kt -> File(homeDir, ".amethyst")
#                                          (accounts + KEYS — destructive)
#   ~/Library/Application Support/Amethyst DesktopTorManager.kt -> ".../Amethyst/tor"
#   ~/Library/Caches/AmethystDesktop       DesktopImageLoaderSetup.kt -> cacheDir()
#                                          (its macOS branch is ~/Library/Caches)
#
# Deliberately NOT zapped: ~/Library/Preferences/com.apple.java.util.prefs.plist.
# The app uses the Java Preferences API (auth approvals, Namecoin settings),
# which writes into that single SHARED plist — deleting it would wipe every
# other Java application's preferences too.
cask "amethyst-nostr" do
  version "1.14.0"
  sha256 "84a1bdaf3577ed7375ab65c48358efd3c609fa47b504de61f4e8fc436f6b3436"

  url "https://github.com/vitorpamplona/amethyst/releases/download/v#{version}/amethyst-desktop-#{version}-macos-arm64.dmg"
  name "Amethyst"
  desc "Nostr client"
  homepage "https://github.com/vitorpamplona/amethyst"

  conflicts_with cask: "amethyst"
  depends_on arch: :arm64
  depends_on :macos

  app "Amethyst.app"

  zap trash: [
    "~/.amethyst",
    "~/Library/Application Support/Amethyst",
    "~/Library/Caches/AmethystDesktop",
  ]
end
