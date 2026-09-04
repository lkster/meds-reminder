# Project guidance

This is a native Android medication-reminder application whose primary goal is reliable, difficult-to-miss alarm delivery with correct lockscreen behavior and minimal disruption after alarm resolution.

Use direct, readable Kotlin and explicit Android behavior. Prefer narrow changes and existing ownership boundaries over new architecture.

## Core invariants

* Room is authoritative for medications, reminders, occurrences, History, and whether an occurrence logically exists and may be delivered.
* AlarmManager is a rebuildable external projection of authoritative Room state. Do not introduce another durable scheduling source of truth.
* Unknown, stale, or logically ineligible occurrence UUID delivery is a harmless no-op.
* Medication IDs, reminder IDs, and occurrence UUIDs are distinct identities. Use stable persisted identities for mutations; never use list position or UI presentation context as mutation authority.
* Snooze creates a distinct temporary occurrence; do not collapse it into the regular reminder identity or infer durable Snooze lineage.
* Current authoritative RINGING state is distinct from future exact AlarmManager projection.
* Preserve established Room-commit versus alarm-side completion boundaries. A committed Room mutation is not rolled back or blindly repeated merely because subsequent AlarmManager/reconciliation work fails.

## Existing ownership boundaries

* `MedicationEditorViewModel` owns medication-editor state and its established Save lifecycle.
* Medication-list enable/disable and deletion remain focused stable-ID Room mutations and do not acquire editor ownership.
* Do not introduce a global medication mutation lock or global busy state.
* Preserve established lifecycle/process-death guarantees rather than silently strengthening them with new durable machinery.
* Do not add WorkManager, durable operation journals, application-wide custom coroutine scopes, repositories, or other persistence/operation infrastructure merely to cover theoretical process-death windows.

## Room Flow presentation state

* Medication-list loading and History loading are independent Activity-local presentation states backed by independent real Room Flows.
* For these established screens, `null` means that the current Activity has not yet received that Flow's first real Room emission; it must not be treated as authoritative empty data.
* Do not cache, seed, persist, or merge these loading states into a shared Room-ready state.
* Keep the existing M11 and M12 instrumentation hooks narrow and independent; do not generalize them into a Flow/test framework.

## UI and accessibility

* Preserve native Material3 action and state semantics when adding accessibility context.
* Display-order numbers used by accessibility semantics are presentation context only, never persistence or mutation identity.
* Do not normalize otherwise valid editor draft state merely to simplify accessibility output.

## Architecture discipline

Avoid introducing without a concrete requirement:

* DI frameworks;
* repository/use-case layers;
* generic scheduling, mutation, loading, async-operation, settings, onboarding, accessibility, or instrumentation frameworks;
* global mutation/busy locks;
* application-wide custom CoroutineScopes;
* multi-module architecture;
* Navigation Compose;
* design-system infrastructure;
* refactoring of validated code merely for stylistic consistency.

Two similar narrow implementations are not by themselves justification for a shared framework.

## Repository navigation

When an accepted Planner artifact or correction is provided, treat it as the primary implementation map. Start from its named production/test surfaces and consult project documentation only for specific unresolved contracts or dependencies.

Do not reread milestone history or broad project documentation merely to reconstruct contracts already supplied by the accepted artifact.

Canonical project documentation remains authoritative when the task actually requires verifying or changing one of those contracts.

## Scope

Preserve established alarm delivery, reconciliation, ringing, History, editor, readiness, loading, accessibility, and lifecycle behavior unless the accepted task explicitly changes the relevant contract.

Do not claim Direct Boot support or completed Samsung physical-device validation unless the task supplies actual evidence establishing it.
