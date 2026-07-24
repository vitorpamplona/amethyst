# geode

A standalone [Nostr](https://github.com/nostr-protocol/nips) relay for the JVM,
built on Quartz's relay-server code (Ktor CIO). It speaks the core relay
protocol plus NIP-11 (info doc), NIP-42 (AUTH), NIP-45 (COUNT), NIP-50
(full-text search), NIP-77 (Negentropy sync), and NIP-86 (relay management),
stores events in SQLite (or a filesystem backend), and can mirror upstream
relays strfry-router style.

geode depends only on `:quartz` — no Android, no Compose. `amy serve` (the
Amethyst CLI) embeds the same engine in-process.

## Install

Pick the channel that matches how you deploy. For a real relay, the **Docker
image** or the **`.deb`/`.rpm` + systemd** are the two production paths;
Homebrew and the portable tarball are handy for local testing.

### Docker (recommended for servers)

Images are published to GHCR on every release:

```bash
# Ephemeral — in-memory store, events vanish on restart:
docker run --rm -p 7447:7447 ghcr.io/vitorpamplona/geode:latest

# Persistent + configured:
docker run -d --name geode -p 7447:7447 \
  -v $PWD/geode.toml:/etc/geode/geode.toml:ro \
  -v geode-data:/var/lib/geode \
  ghcr.io/vitorpamplona/geode:latest --config /etc/geode/geode.toml
```

with `in_memory = false` and `file = "/var/lib/geode/events.db"` in your
`geode.toml`. Pin a version (`:1.12.6`) instead of `:latest` for reproducible
deploys. To build the image yourself, from the repo root:

```bash
docker build -f geode/Dockerfile -t geode:local .
```

### Debian/Ubuntu (`.deb`) and Fedora/RHEL (`.rpm`)

Download `geode-<version>-linux-x64.deb` (or `.rpm`) from the
[release page](https://github.com/vitorpamplona/amethyst/releases) and install
it — a minimal JRE is bundled, so no system Java is required. It installs to
`/opt/geode/` with the launcher at `/opt/geode/bin/geode`.

```bash
sudo dpkg -i geode-1.12.6-linux-x64.deb    # or: sudo rpm -i geode-1.12.6-linux-x64.rpm
```

To run it as a managed service, wire up the shipped systemd unit
(`/opt/geode/share/geode/geode.service`) — the unit file's header comment has
the copy-paste steps (create the `geode` user, drop your config in
`/etc/geode/geode.toml`, `systemctl enable --now geode`).

### Homebrew

```bash
brew install vitorpamplona/tap/geode    # from the tap, once published
```

The formula depends on `openjdk` and installs the no-JRE jar bundle. Reference
formula: [`packaging/homebrew/geode.rb`](packaging/homebrew/geode.rb).

### Portable tarball (any Linux/macOS, no system Java)

Download `geode-<version>-<os>-<arch>.tar.gz`, unpack, and run:

```bash
tar xzf geode-1.12.6-linux-x64.tar.gz
./geode/bin/geode --config geode/share/geode/config.example.toml
```

The tarball bundles a jlink'd JRE plus `share/geode/config.example.toml` and
`share/geode/geode.service`.

### From source

```bash
./gradlew :geode:run --args="--config /etc/geode.toml"
```

## Configure

geode runs with zero config (binds `0.0.0.0:7447`, in-memory SQLite). For
anything real, copy [`config.example.toml`](config.example.toml) and edit it —
the sections mirror nostr-rs-relay's `config.toml` so existing configs port
almost verbatim. Precedence is **CLI flags > TOML > built-in defaults**.

```bash
geode --config /etc/geode/geode.toml
geode --help          # full flag list
geode --version
```

Key sections: `[info]` (NIP-11 doc), `[network]` (bind + thread pools),
`[database]` (SQLite path/tuning), `[options]` (AUTH / verify / search),
`[authorization]` (allow/deny lists), `[[mirror]]` (upstream mirroring), and
`[admin]` (NIP-86 management). See the example file for every knob.

## Verbs

```
geode [relay] [flags]        serve the relay (default)
geode import [flags] [FILE…] bulk-load NDJSON events into the store
geode export [flags]         dump the store as NDJSON to stdout
```

`import`/`export` are geode's `strfry import` / `strfry export` — one JSON event
per line, for seeding, migrating, or backing up a relay. Both stream, so a
multi-million-event corpus round-trips in roughly constant memory.

## Release process

geode ships on the same tag-driven pipeline as the rest of Amethyst: pushing a
`v*` tag runs `.github/workflows/create-release.yml`, whose `build-geode` job
produces the tarball + `.deb`/`.rpm` + Homebrew jvm bundle, `docker-geode`
builds and pushes the GHCR image, and `bump-homebrew-geode-formula.yml` syncs
the reference formula. Design notes:
[`plans/2026-07-24-geode-release.md`](plans/2026-07-24-geode-release.md).
