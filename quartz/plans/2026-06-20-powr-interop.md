# POWR interop: rendering kind-1301 strength workouts

Research date: 2026-06-20. Source: POWR app (`DocNR/POWR`), spec at
`docs/technical/nostr/exercise_nip.md` (the NIP-101e "Workout Events" draft:
kinds 33401 exercise template, 33402 workout template, 1301 workout record).
Sample event provided by a POWR user (`client: POWR`, `relay.powr.build`).

## The problem

POWR and RUNSTR both publish **kind 1301**, but the tag schemas are
structurally incompatible. Amethyst already supported the RUNSTR dialect
(`quartz/.../experimental/fitness/workout/`), so a POWR event rendered badly:
the activity label showed the raw coordinate `33401:…:back-squat-bb`, there was
no duration, and none of the set data appeared.

| Concern | RUNSTR dialect (existing) | POWR / NIP-101e dialect (new) |
|---|---|---|
| Activity type | `["exercise", "running"]` (verb) | `["type", "strength"]` (`strength`/`circuit`/`emom`/`amrap`) |
| `exercise` tag | the activity verb | one **logged set**, referencing a 33401 template: `["exercise", "33401:pubkey:d-tag", relay, weightKg, reps, rpe, set_type, set_number]` |
| Duration | `["duration", "HH:MM:SS"]` or raw seconds | none — derive from `["start", unix]` / `["end", unix]` |
| Weight unit | lbs | **kg** (empty = bodyweight, negative = assisted) |
| Misc | `source`, `t` hashtags | `completed`, `template`, `client` |

## What was implemented (read/render only)

Parsing + tests in Quartz, rendering in Amethyst. Amethyst still **publishes**
the RUNSTR-canonical form (no POWR writer) — this is rendering interop.

### Quartz (`experimental/fitness/workout/`)
- `tags/WorkoutTypeTag.kt` — POWR `type`.
- `tags/WorkoutSessionTimeTags.kt` — `start` / `end` / `completed`.
- `tags/ExerciseSetTag.kt` — the per-set coordinate form, with `isCoordinate()`
  to distinguish it from a RUNSTR verb (`kind:64-hex-pubkey:d-tag`).
- `ExerciseGroup.kt` — groups sets by template reference (first-seen order,
  sorted by set number); `displayName()` prettifies the d-tag slug
  (`back-squat-bb` → "Back Squat Bb"); `totalVolumeKg()` / `topWeightKg()`.
- `ExerciseTag.parse()` now returns null for the coordinate form, so the verb
  path never surfaces `33401:…` as an activity.
- `ExerciseType` gains `CIRCUIT` / `EMOM` / `AMRAP` (icon + label reuse).
- `WorkoutRecordEvent`: `activityType()` (prefers `type`, falls back to the
  verb), `effectiveDurationSeconds()` (duration tag, else `end - start`),
  `exerciseGroups()`, `client()`, `workoutCompleted()`.

### Amethyst (`WorkoutDisplay.kt`)
- Activity label/icon come from `activityType()`; coordinate never leaks.
- Hero = derived duration; secondary stats add Exercises / Sets / Volume.
- New `ExerciseBreakdown` lists each exercise with its sets, e.g.
  `Back Squat Bb → 3 × 8 × 84 kg`, in the viewer's preferred unit (kg↔lbs).

## Not done (possible follow-ups)
- Fetch 33401 templates to show real exercise titles / equipment / media
  instead of the slug, and 33402 to show the planned-vs-done template.
- A POWR-dialect writer (would need exercise-template authoring — large).
- Surface `rpe` / `set_type` (warmup/drop/failure) per set in the breakdown.
