# Security Policy

Amethyst is a Nostr client that handles user private keys, signed events, and
end-to-end encrypted direct messages. We take security reports seriously and
appreciate responsible disclosure.

## Supported Versions

Only the latest release receives security fixes. We do not backport patches to
older versions. Fixes also land on the `main` branch ahead of the next release.

| Version        | Supported |
| -------------- | --------- |
| Latest release | ✅        |
| `main`         | ✅        |
| Older          | ❌        |

This covers all artifacts built from this repository: the Android app
(`amethyst/`), the desktop app (`desktopApp/`), the `amy` CLI (`cli/`), and
the `quartz` / `commons` libraries.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Use GitHub's private vulnerability reporting instead:

👉 [Report a vulnerability](https://github.com/vitorpamplona/amethyst/security/advisories/new)

This keeps the details private until a fix is ready and coordinates disclosure
between you and the maintainers.

### What to include

To help us triage quickly, please provide:

- A clear description of the vulnerability and its impact (what an attacker
  could achieve — e.g. key material exposure, DM confidentiality, integrity,
  availability).
- Affected module(s): `quartz`, `commons`, `amethyst` (Android), `desktopApp`,
  or `cli` / `amy`.
- Affected version(s), commit SHA, platform, and OS.
- Steps to reproduce, a proof of concept, or a failing test.
- Any suggested remediation.

### What to expect

- **Acknowledgement within 48 hours** of your report.
- We will investigate and keep you informed of progress.
- We will coordinate a release and disclosure timeline with you. We aim to
  ship a fix within 90 days for high and critical issues, faster when key
  material or DM confidentiality is at risk.
- Credit will be given to reporters in the security advisory and release
  notes (unless you prefer to remain anonymous).

## Scope

In scope:

- Source code in this repository across all modules.
- Released binaries (APK, DMG, MSI, DEB, RPM, AppImage, tarball) built from
  this repository.
- Cryptographic handling: signing, NIP-04 / NIP-17 / NIP-44 encryption, key
  storage (Android Keystore, desktop keychain), NIP-46 bunker flows, NIP-55
  external signer integration.
- Relay client behavior that could leak private data or bypass authorization.

Out of scope:

- Vulnerabilities in third-party relays, bridges, media servers, or Nostr
  clients not built from this repository.
- Issues that require a rooted / jailbroken device, a compromised host, or
  physical access with the device unlocked.
- Weaknesses inherent to the Nostr protocol itself — please report these
  upstream at <https://github.com/nostr-protocol/nips>.
- Denial-of-service from a malicious relay the user has explicitly connected
  to.
- Social-engineering and phishing that does not exploit an app-level flaw.

## Disclosure Policy

We follow a coordinated disclosure model. We ask that you:

- Give us reasonable time to investigate and release a fix before any public
  disclosure.
- Avoid accessing or modifying other users' data during research.
- Only interact with accounts and data you own or have explicit permission to
  test.
- Act in good faith.

We will not pursue or support legal action against researchers who follow
this policy. We commit to responding promptly and treating all reports
seriously.

Thank you for helping keep Amethyst and its users safe.
