# Reference Homebrew Cask for the Amethyst desktop app.
#
# This file is NOT consumed by any build in this repo. Submit it to
# Homebrew/homebrew-cask (as `Casks/a/amethyst-nostr.rb`) or drop it into a
# personal tap (`Casks/amethyst-nostr.rb`) for an instant
# `brew install --cask <tap>/amethyst-nostr`.
#
# The release matrix (.github/workflows/create-release.yml) builds an
# Apple-Silicon DMG only (no Intel DMG), so this cask is arm64-only.
#
# version + sha256 below track the published
# `amethyst-desktop-<version>-macos-arm64.dmg`. Once the cask exists upstream,
# bump-homebrew.yml keeps the live copy current on each stable release. To
# refresh this reference by hand:
#   curl -fsSL -o amethyst.dmg \
#     https://github.com/vitorpamplona/amethyst/releases/download/vX.Y.Z/amethyst-desktop-X.Y.Z-macos-arm64.dmg
#   shasum -a 256 amethyst.dmg
cask "amethyst-nostr" do
  version "1.14.0"
  sha256 "84a1bdaf3577ed7375ab65c48358efd3c609fa47b504de61f4e8fc436f6b3436"

  url "https://github.com/vitorpamplona/amethyst/releases/download/v#{version}/amethyst-desktop-#{version}-macos-arm64.dmg"
  name "Amethyst"
  desc "Nostr client"
  homepage "https://github.com/vitorpamplona/amethyst"

  livecheck do
    url :url
    strategy :github_latest
  end

  # The unrelated tiling window manager (cask `amethyst`, ianyh/Amethyst) also
  # installs `Amethyst.app`, so the two cannot coexist in /Applications.
  conflicts_with cask: "amethyst"
  depends_on arch: :arm64
  depends_on :macos

  app "Amethyst.app"

  # Verified against the source, not the docs:
  #   ~/.amethyst                            DesktopAccountStorage (accounts + keys)
  #   ~/Library/Application Support/Amethyst DesktopTorManager (tor/)
  #   ~/Library/Caches/AmethystDesktop       Coil image cache
  #
  # Deliberately NOT zapped: ~/Library/Preferences/com.apple.java.util.prefs.plist.
  # The app uses the Java Preferences API, which writes to that single SHARED
  # plist — deleting it would wipe every other Java app's preferences too.
  zap trash: [
    "~/.amethyst",
    "~/Library/Application Support/Amethyst",
    "~/Library/Caches/AmethystDesktop",
  ]
end
