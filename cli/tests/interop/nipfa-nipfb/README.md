# NIP-FA / NIP-FB interop fixture

Two signed events for checking that another client (Brainstorm, first) reads AI agent keys and
scheduled tasks the way Amethyst writes them:

- **kind 12000**, NIP-FA AI agent keys: two `key` tags, a hosted provider first (the default)
  and a local runner with an empty key.
- **kind 34817**, NIP-FB scheduled task `d` = `observer`: `task`, `cron` in `America/New_York`
  with its set-at time, a finished `claim`, `ran`, and an option tag (`length`) a reader must
  keep when it rewrites the task. Only `d` is public.

Both contents are NIP-44 encrypted by the author to themselves, as the drafts specify.

## Files

- `make-fixture.sh` builds the events with amy (`encrypt`, `event`), checks each one with
  `verify` and decrypts it back, and writes `fixture.json`. Offline: no relay, no provider.
  Needs `jq` and a built amy (`./gradlew :cli:installDist`).
- `fixture.json` is one run of it: `events` (the two signed events), `decrypted` (the tag
  array each `content` decrypts to) and `expect` (what a reader should report). The ciphertext
  and signatures change from run to run (NIP-44 and Schnorr use fresh randomness); everything
  else is fixed.

## The key is a test key

`test_nsec` (`npub1dqmd2r6awzvdv7uzdctrkr53wec7u8csmrad0vk6jxl7p6mz6fmqnn3r8f`) is public in this
repository. It exists only to sign this fixture: never log in with it anywhere real, never send
it funds, never save a real provider key under it. The provider key inside is fake.

## Checking a reader

Log a client in with `test_nsec` (or give it the events and the key), then check that it:

1. decrypts the kind 12000 to `decrypted["12000"]`, uses the OpenRouter key as the default and
   lists the local runner (no key) second;
2. decrypts the kind 34817 to `decrypted["34817"]` and shows the task as `expect["34817"]`
   says: 07:00 every day in New York, not paused, last run by "Desktop (Linux)", finished;
3. on a rewrite of either, keeps every tag it doesn't change (the `length` option, the second
   key) and writes `created_at` = max(now, 1794355200 + 1).

Publishing the events to a relay a client reads (a throwaway one, never a public relay) lets it
pick them up as its own, for example:

```bash
jq -c '.events[]' fixture.json | amy publish --file - --relay ws://127.0.0.1:PORT
```

Specs: the NIP-FA and NIP-FB drafts, `FA.md` on the `claude/nip-fa-ai-agent-keys` branch and
`FB.md` on the `claude/nip-fb-scheduled-tasks` branch of the nips repository.
