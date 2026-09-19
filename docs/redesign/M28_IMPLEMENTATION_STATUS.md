# M28 implementation status

## Implemented in M28

- History browse-surface redesign with scheduled-occurrence local-day grouping.
- Presentation-only All, Taken, Skipped, and No response filters.
- Scheduled-time and existing `resolvedAtEpochMillis` presentation: Taken at, Skipped at, and
  Timed out at. Cross-midnight resolution shows its date without changing scheduled-day identity.
- After snooze context, stable loading shell, global empty state without a replacement CTA, and
  filter-empty Show all action.
- Safe-drawing handling and responsive/accessibility semantics for History.

## Partial / transitional

- History remains reached from Medication list and uses the secondary top-bar Back affordance.
- The final normal-app IA remains incomplete. History details, read-error/retry presentation, and
  the final global-empty to Today CTA remain deferred.

## Deferred Category A/B

- Medication list, editor, details, delete/unsaved-change redesign, final top-level shell,
  History details/read-error presentation, AlarmActivity/fixed alarm theme/split Snooze, shared
  components, and broader copy/localization.

## Deferred Category C

- Today day-plan/calendar, Today Taken/Skip, one-off rescheduling, medication appearance
  persistence, richer medication/schedule model, dose snapshots, multiple richer schedules,
  editable History, and grouped same-time alarms.
