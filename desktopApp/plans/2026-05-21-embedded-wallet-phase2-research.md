# Phase 2: Embedded Self-Custodial Wallet — Research Summary

**Date:** 2026-05-21
**Status:** Research complete, parked. Return after NWC parity (Phase 1) ships.

## Context

No desktop Nostr client has an embedded wallet. All use NWC. This would be a first.

## Top Candidates (High Sovereignty)

| | Breez SDK Spark | ldk-node-jvm | lightning-kmp |
|---|---|---|---|
| Sovereignty | Full | Full | Full |
| Architecture | No channels | Channels + LSPS2 | Single channel + splicing |
| KMP/JVM | KMP artifact (`breez-sdk-spark-kmp:0.7.10`) | JVM JAR (`ldk-node-jvm:0.7.0`) | KMP (`lightning-kmp:1.8.4`) |
| LSP lock-in | None | Any LSPS2 | ACINQ only |
| Embedding docs | Breez docs | Good | None |
| License | MIT | MIT/Apache-2.0 | Apache-2.0 |

### Recommended path

1. **Spike Breez SDK Spark** — verify KMP artifact works on JVM desktop (not just Android)
2. **Fallback: ldk-node-jvm** — proven JVM, any LSP, well-documented
3. **Skip: lightning-kmp** — ACINQ LSP lock-in, no embedding docs

### Eliminated

- phoenixd: subprocess, no Windows native
- Breez SDK Liquid: Android-only bindings
- Greenlight: weak JVM support
- Cashu: not self-custodial (mint trust)

## Open Questions

1. Does `breez-sdk-spark-kmp` include JVM desktop native libs?
2. Spark fee economics for small zaps (10-100 sats)?
3. Does `ldk-node-jvm` bundle macOS arm64/x64 + Linux x64 natives?
4. Which LSPS2 LSPs are publicly available?
5. Would ACINQ accept third-party lightning-kmp clients?

## Nostr App Landscape

| App | Wallet | Type |
|-----|--------|------|
| Primal | Strike (custodial), maybe migrating to Spark | Built-in |
| 0xchat | cashu-dart | Cashu ecash |
| YakiHonne | Cashu + NWC | Dual |
| Amethyst Android | NWC + Cashu token parsing | External |
| All desktop clients | NWC only | External |
