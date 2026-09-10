# CGKA scenario vectors

Copied verbatim from the reference implementation:
`marmot-protocol/mdk`, `crates/cgka-conformance-simulator/vectors/`, at the
commit the shipping White Noise apps embed
(`2f44f6b65a19f8818644ccd7027618ba91450c33`, `marmotkit-v0.9.20`).

Their `manifest.v1.json` marks 31 artifacts `portable` — meaning they are
meant to be replayed by an implementation that is not theirs. Three are byte
fixtures we already consume from `quartz/src/commonTest/resources/marmot/
conformance/`. The rest are **scenario scripts**: a client roster, a step
list, and an expected trace.

These nine are the subset whose steps `MarmotScenarioRunner` implements —
`create_group`, `invite_members`, `send_app_message`, `deliver_all`, `tick`,
`acknowledge_outbound`, `observe`, `in_group`, `assert`, `clear_events`. The
other nineteen need fault injection (withhold/release, partition, duplicate,
reorder, restart) or group-data and admin-policy steps; the runner refuses
them by name rather than skipping quietly, so adding a step type is what
widens the set.

## Refreshing

```bash
cp <mdk>/crates/cgka-conformance-simulator/vectors/<name>.v1.json .
```

Refresh from the commit named in whitenoise-android's
`app/src/main/marmotkit/MARMOT_VERSION` (or whitenoise-ios's
`Packages/MarmotKit/MARMOT_VERSION`) — that is the engine users are running,
which is the thing worth being conformant with.
