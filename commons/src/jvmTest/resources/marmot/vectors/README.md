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

These sixteen are the subset whose steps `MarmotScenarioRunner` implements:
`create_group`, `invite_members`, `remove_members`, `send_app_message`,
`update_group_data`, `deliver_all`, `tick`, `acknowledge_outbound`, `observe`,
`in_group`, `assert`, `clear_events`, and the queue faults `omit_message`,
`duplicate_message`, `reorder_messages`, `withhold_message`,
`release_withheld`. The rest need `restart_client`, `set_partition`, `leave`,
admin-policy steps, or the `convergence_decision` outcome; the runner refuses
them by name rather than skipping quietly, so adding a step type is what
widens the set.

## Two expectation shapes

A vector states what must be true in one of two ways, and BOTH have to be
read:

- `expected_trace.observations` — the older shape (`publish-fail`,
  `three-client-message-exchange`).
- `expected_outcomes` — a list of typed entries: `client_state`,
  `clients_converged`, `group_profile`, `pending_resolution`,
  `no_pending_work`, and `convergence_decision`. Everything else here uses
  this one.

Reading only the first is not a partial check, it is no check: a vector whose
expectations all live in the other shape replays its steps and reports green
having compared nothing. `MarmotScenarioVectorTest.everyVectorStatesSomethingToCheck`
exists to make that failure loud rather than invisible.

An outcome type the runner cannot evaluate raises `UnsupportedScenarioOutcome`
and the vector is refused — `convergence-committer-selected` is refused today
for exactly that reason.

## Refreshing

```bash
cp <mdk>/crates/cgka-conformance-simulator/vectors/<name>.v1.json .
```

Refresh from the commit named in whitenoise-android's
`app/src/main/marmotkit/MARMOT_VERSION` (or whitenoise-ios's
`Packages/MarmotKit/MARMOT_VERSION`) — that is the engine users are running,
which is the thing worth being conformant with.
