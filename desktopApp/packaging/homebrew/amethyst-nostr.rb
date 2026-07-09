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
  version "1.12.6"
  sha256 "69882e83ebcec6723e1ad5655ec2c9d1fa151b9d1a8ae51b869a9d62feabf093"

  url "https://github.com/vitorpamplona/amethyst/releases/download/v#{version}/amethyst-desktop-#{version}-macos-arm64.dmg",
      verified: "github.com/vitorpamplona/amethyst/"
  name "Amethyst"
  desc "Nostr client for desktop"
  homepage "https://github.com/vitorpamplona/amethyst"

  livecheck do
    url :url
    strategy :github_latest
  end

  depends_on arch: :arm64

  app "Amethyst.app"

  zap trash: "~/.amethyst"
end
