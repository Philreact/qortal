# AT Optimization Handoff

This document explains the AT/block-processing optimization work in this branch: what problem it was solving, how the old path behaved, how the new path behaves, which tables are canonical vs derived, and what a reviewer should verify before production rollout.

## Scope

The work targeted two related problems:

- AT processing was dominating block validation and processing time during sync.
- Background QDN/maintenance threads could compete with sync for repository, disk, and CPU resources.

The highest-cost AT areas observed in logs and analyzer reports were:

- Repeated `findNextTransaction()` polling from `willExecute()`.
- Latest AT state reads before running each AT.
- Fetching all executable/unfinished ATs as the active AT set grows.
- Rewriting wide `ATs` rows for mutable runtime metadata.
- Rewriting large serialized AT state blobs.
- Background cleanup/storage/trimming/pruning work running during sync.

The optimization direction was:

- Keep consensus grounded in canonical chain/state rows.
- Move repeated lookup work into rebuildable derived tables.
- Batch reads/writes in the block hot path.
- Avoid rewriting large or wide rows when only small metadata changes.
- Defer non-consensus background work while sync is requested or active.

## Non-Negotiable Safety Rules

The important design rule is that acceleration tables must not become consensus truth.

Canonical consensus data remains:

- `Transactions` plus subtype tables such as `PaymentTransactions`, `MessageTransactions`, and `ATTransactions`.
- `BlockTransactions` / transaction block height and sequence.
- `ATs` deployment/code data.
- `ATRuntime` mutable runtime data once backfilled.
- `ATStates` per-height AT execution metadata.
- `ATStates.state_hash`, fees, initial flag, and sleep timestamp used for validation.
- `ATStatesData` during rollout as legacy state-byte storage.
- `ATStateBlobs` as verified content-addressed state-byte storage.

Derived/rebuildable acceleration data:

- `ATIncomingTransactions`
- `ATNextIncoming`
- `ATCurrentState`
- `ATExecutionQueue`

If derived data is missing, stale, or mismatched, the code should rebuild or repair it from canonical data. If repair cannot make derived data agree with canonical data, block validation must fail loudly rather than continue with an unsafe wake set or state pointer.

## Schema Changes

The optimization adds migrations in `HSQLDBDatabaseUpdates`.

### Case 52

Compatibility placeholder.

Some local databases had already run an earlier experimental case 52 and advanced the schema version. The real AT inbox migration was moved to case 53 so those databases still receive the correct tables.

### Case 53: AT Inbox / Next Incoming

Drops the failed experimental table:

```sql
DROP TABLE IF EXISTS ATRecipientTransactions
```

Adds:

```sql
ATIncomingTransactions(
  AT_address,
  block_height,
  block_sequence,
  signature,
  PRIMARY KEY (AT_address, block_height, block_sequence, signature)
)
```

Adds:

```sql
ATNextIncoming(
  AT_address PRIMARY KEY,
  sleep_until_message_timestamp,
  block_height,
  block_sequence,
  signature
)
```

Why:

- Old `willExecute()` repeatedly asked: “search all transaction subtype tables for the next transaction for this sleeping AT.”
- New path maintains a derived inbox of confirmed AT-recipient transactions.
- `ATNextIncoming` stores the earliest message wake candidate for the AT’s current sleep timestamp.

Safety:

- `ATIncomingTransactions` is rebuilt from canonical transaction tables and existing `ATs`.
- `ATNextIncoming` is rebuilt from `ATRuntime`/`ATs` sleep state plus the inbox.
- `sleep_until_message_timestamp` is stored in `ATNextIncoming` so stale cursor rows are rejected if the AT sleep state changes.

### Case 54: ATCurrentState

Adds:

```sql
ATCurrentState(
  AT_address PRIMARY KEY,
  height
)
```

Adds an index on `ATStatesData(AT_address, height)`.

Why:

- Old execution fetched latest state by recalculating “max height for this AT” repeatedly.
- `ATCurrentState` gives a direct pointer to the current state height.

Safety:

- It is rebuildable from canonical `ATStates` plus available state bytes.
- It is not reused from `LatestATStates`, because `LatestATStates` has trim/prune semantics and height cutoffs.

### Case 55: ATStateBlobs and ATStates.previous_height

Adds `ATStates.previous_height`.

Adds:

```sql
ATStateBlobs(
  state_hash PRIMARY KEY,
  state_data,
  created_height,
  state_data_length
)
```

Why:

- `ATStates` should remain the canonical per-height execution metadata.
- Large serialized machine state bytes are moved to content-addressed storage.
- Multiple state rows can reference one blob if the state hash repeats.
- `previous_height` lets orphan rollback move the current-state pointer back directly instead of scanning for the previous max height.

Safety:

- During rollout, reads verify blob bytes hash to `ATStates.state_hash`.
- If legacy bytes are also present, blob bytes must equal legacy `ATStatesData.state_data`.
- Old `ATStatesData` is still available during rollout/fallback.
- Blob rows are not deleted in hot orphan paths.

### Case 56: ATs.current_state_height

Adds a current-state pointer column to `ATs`.

Why:

- `ATCurrentState` is useful as a rebuildable table, but the execution hot path benefits from the latest pointer being close to AT metadata.
- This also provides an additional pointer source for rollback/rebuild checks.

Safety:

- Pointer copies are treated as acceleration/fallback data.
- Canonical history is still in `ATStates`.

### Case 57: ATRuntime

Adds:

```sql
ATRuntime(
  AT_address PRIMARY KEY,
  is_sleeping,
  sleep_until_height,
  is_finished,
  had_fatal_error,
  is_frozen,
  frozen_balance,
  sleep_until_message_timestamp,
  current_state_height
)
```

Why:

- Before this, every AT runtime update rewrote the wide `ATs` row, which includes immutable deployment/code fields.
- Runtime fields change frequently. Code/deployment fields do not.
- Moving mutable fields into `ATRuntime` reduces write amplification and avoids touching large/wide rows during block processing.

Compatibility:

- Repository reads use `runtimeColumn(...)`, which falls back to `ATs` if `ATRuntime` is missing.
- `ensureRuntimeRowsExist()` repairs missing runtime rows from `ATs`.

### Case 58

Adds indexes for the new executable-AT hot path:

- `ATRuntimeFinishedAddressIndex`
- `ATCreatedOrderIndex`

Why:

- `getExecutableATs(height)` joins the queue/runtime rows with `ATs` and still has to preserve the existing AT execution order by creation time and address.

### Case 59: ATExecutionQueue

Adds:

```sql
ATExecutionQueue(
  AT_address PRIMARY KEY,
  next_height
)
```

Why:

- Long-term, there may be many ATs that are not finished but are sleeping.
- Scanning every unfinished AT every block does not scale.
- `ATExecutionQueue` stores the earliest block height at which the AT can execute:
  - `0` for ATs that are not sleeping on message.
  - `sleep_until_height` for height wakes.
  - `incoming_block_height + 1` for message wakes.
  - the earlier of height/message wake when both exist.

Safety:

- The queue is derived from `ATRuntime` and `ATNextIncoming`.
- `getExecutableATs(height)` calls verification before trusting the queue.
- If due queue rows do not match canonical due rows, it repairs affected rows, then rebuilds if needed, then fails if still mismatched.

### Case 60

Adds:

- `ATIncomingTransactionsBlockHeightIndex`

Why:

- Orphan/delete and per-block inbox maintenance need to delete/fetch inbox rows by block height.

### Case 61

Adds verifier/helper indexes:

- `ATRuntimeFinishedMessageAddressIndex`
- `ATRuntimeFinishedHeightAddressIndex`
- `ATNextIncomingHeightTimestampAddressIndex`

Why:

- Keeps `ATExecutionQueue` verification indexed without changing consensus state.

## Old AT Execution Flow

Before these changes, block validation/execution roughly did:

1. Fetch all unfinished/executable ATs.
2. For each AT:
   - Check whether it should wake.
   - If sleeping until message, call `findNextTransaction()`.
   - Fetch latest state for that one AT.
   - Execute the VM.
   - Emit AT state and AT transactions.
3. During processing:
   - Write AT state metadata.
   - Write full serialized AT state bytes.
   - Save the wide `ATs` row.
   - Modify fee balances one path at a time.

Scaling problem:

- Sleeping ATs still caused DB reads.
- “Next transaction” checks scaled with number of sleeping ATs.
- Latest-state reads were per AT.
- Runtime writes touched wide rows.
- State byte writes could rewrite large blobs.

## New AT Execution Flow

`Block.executeATs()` is now split into two phases.

### Phase 1: Cheap Wake Selection

1. Fetch executable ATs using:

```java
repository.getATRepository().getExecutableATs(blockHeight)
```

This uses `ATExecutionQueue`, with repository-side verification/repair.

2. Batch-load message wake cursors using:

```java
getNextIncomingForATs(executableATs, blockHeight)
```

This only looks at ATs that:

- have `sleep_until_message_timestamp != null`
- are not already height-wakeable

Important detail:

- `Map.containsKey(atAddress)` matters.
- A present key with `null` means “cursor was checked, and no message exists.”
- A missing key means “not precomputed; use normal lookup if needed.”

3. For each candidate AT:

- Run `AT.willExecute(...)`.
- Preserve pre-execution sleep/finished fields in maps.
- Add only ATs that will execute to `atsToRun`.

### Phase 2: Batched State Load and VM Run

1. Batch-load current state rows for ATs that passed `willExecute()`:

```java
getCurrentATStates(atAddressesToRun)
```

2. Run each AT using:

```java
runWithLatestState(...)
```

This avoids per-AT latest-state queries.

3. Set `ATStateData.previousHeight` from the loaded previous state.

Why the two-phase approach matters:

- Wake checks do not require loading state bytes.
- State bytes are only fetched for ATs that actually run.
- Latest-state reads are batched.
- AT API object from the wake check is reused when available so behavior stays aligned with the original single-call path.

## `willExecute()` Cursor Semantics

`QortalATAPI.willExecute(...)` now accepts:

```java
NextTransactionInfo precomputedWake
boolean precomputedWakeAvailable
```

The boolean is necessary because `null` has two meanings:

- `precomputedWakeAvailable == true && precomputedWake == null`
  - The cursor was checked and there is no wake message.
- `precomputedWakeAvailable == false`
  - The caller did not pre-load a cursor, so the old `findNextTransaction()` lookup should be used.

This preserves compatibility for non-hot-path callers and VM calls.

## `findNextTransaction()` After the Inbox Change

`findNextTransaction()` still exists because CIYAM machine execution can ask for subsequent transactions, not only the initial wake decision.

The important change:

- It now reads from `ATIncomingTransactions`, not the broad transaction-subtype union.

That keeps VM message iteration fast while preserving the old ordering rules:

- same block: strictly greater `block_sequence`
- future blocks: lowest `block_height`, then lowest `block_sequence`

`findNextTransactionCanonical(...)` still exists internally for rebuilding cursors from canonical subtype tables.

## AT Processing / Persistence Flow

`Block.processAtFeesAndStates()` now batches work.

For each emitted AT state:

1. Build fee balance deltas.
2. Reuse `ATData` captured during execution when available.
3. Parse flags from state bytes.
4. Compare pre-execution runtime fields to post-execution runtime fields.
5. Split ATs into:
   - full runtime metadata updates
   - current-height-only updates
6. Track which ATs need next-incoming cursor recompute.
7. Track which ATs need execution-queue recompute.

Then it performs:

1. Batch QORT fee balance reduction.
2. Batch save canonical AT state rows and blobs.
3. Batch update runtime metadata.
4. Recompute cursors only for ATs whose message sleep timestamp changed.
5. Recompute execution queue only for ATs whose scheduling inputs changed.

Why:

- Avoid per-AT repository round trips where possible.
- Avoid recomputing derived rows for ATs whose wake inputs did not change.
- Avoid wide `ATs` updates when only runtime data changes.

## AT State Storage Model

The new model is:

```sql
ATStates(
  AT_address,
  height,
  state_hash,
  fees,
  is_initial,
  sleep_until_message_timestamp,
  previous_height
)
```

plus:

```sql
ATStateBlobs(
  state_hash PRIMARY KEY,
  state_data,
  created_height,
  state_data_length
)
```

During rollout, legacy `ATStatesData` remains.

Write path:

1. Insert/update `ATStates` metadata.
2. Deduplicate state bytes by `state_hash` in memory for the block.
3. Query which hashes already exist in `ATStateBlobs`.
4. Insert only missing blobs.
5. Optionally write legacy `ATStatesData`.
6. Update current-state pointers if requested.

Read path:

1. Load `ATStates.state_hash`.
2. Prefer `ATStateBlobs.state_data`.
3. Join legacy `ATStatesData.state_data` for rollout verification/fallback.
4. Verify blob hash equals `ATStates.state_hash`.
5. If both blob and legacy bytes exist, require them to be identical.

Why this is safe:

- Consensus comparison is still based on AT state hash/fees.
- Any byte source must hash to the canonical `ATStates.state_hash`.
- Blob dedup affects storage cost, not VM semantics.

## Current-State Pointers

There are now several current-height pointer locations:

- `ATCurrentState.height`
- `ATs.current_state_height`
- `ATRuntime.current_state_height`

Why multiple copies exist:

- `ATCurrentState` is rebuildable and explicit.
- `ATs.current_state_height` helps compatibility and fallback paths.
- `ATRuntime.current_state_height` keeps the pointer close to the hot runtime row.

Safety:

- Rebuilds derive from `ATStates` plus available state bytes.
- Delete/orphan logic checks pointer state before moving it backward.
- If `previous_height` is available, rollback uses it directly.
- If not, fallback scans canonical `ATStates`.

Important cleanup note:

- Future cleanup should make one pointer source the primary hot-path source and keep the others only as compatibility/rebuild aids.
- Before removing any pointer copy, verify bootstrap/import, orphan, trim/prune, and API paths.

## Incoming Transaction Maintenance

`Block.linkTransactionsToBlock()` now builds candidate AT-recipient rows while assigning canonical block height and sequence.

The repository then:

1. Filters candidates to existing AT addresses.
2. Deletes existing inbox rows for the block height.
3. Batch inserts rows into `ATIncomingTransactions`.
4. Returns affected AT addresses.
5. Recomputes `ATNextIncoming` and `ATExecutionQueue` only for affected ATs.

Why:

- Empty blocks avoid broad subtype scans.
- Non-AT recipient transactions are ignored cheaply.
- Work scales with incoming AT transactions, not total sleeping ATs.

Orphan path:

- Delete inbox rows for the orphaned height.
- Recompute affected AT cursors.
- Recompute affected execution-queue rows.

## Execution Queue Maintenance

`ATExecutionQueue` answers:

“Which ATs can possibly execute at or before this block height?”

It is updated when:

- AT runtime scheduling fields change.
- A block links transactions addressed to ATs.
- A block is orphaned and inbox rows are removed.
- Rebuild/repair paths run.

Queue row calculation:

- finished AT: no row
- no `sleep_until_message_timestamp`: `next_height = 0`
- height wake only: `next_height = sleep_until_height`
- message wake only: `next_height = incoming_block_height + 1`
- both height and message wake: earlier height wins

Why `incoming_block_height + 1`:

- Existing behavior should only wake from confirmed prior-chain transactions according to block processing order.

Verifier:

`getExecutableATs(height)` calls:

- `ensureExecutionQueueVerified()`
- `verifyExecutionQueueForHeight(height)`

The verifier compares:

- canonical due ATs from `ATRuntime` and `ATNextIncoming`
- queued due ATs from `ATExecutionQueue`

Mismatch handling:

1. Recompute affected queue rows.
2. If still mismatched, rebuild the entire queue.
3. If still mismatched, throw `DataException`.

This is the fork-safety guard for the derived queue.

## Runtime Metadata Split

Before:

- AT runtime updates saved the `ATs` row.
- `ATs` also includes immutable deployment/code fields.

After:

- Mutable runtime fields are updated in `ATRuntime`.
- Reads use `ATRuntime` when present.
- Fallback to `ATs` exists for older/incomplete DBs.
- `ensureRuntimeRowsExist()` repairs missing rows.

Why:

- Avoid rewriting wide rows.
- Reduce write amplification.
- Keep immutable deployment/code data stable.

## Background Work Defer

The following background managers now defer around sync:

- `ArbitraryDataManager`
- `ArbitraryDataCleanupManager`
- `ArbitraryDataStorageManager`
- `AtStatesPruner`
- `AtStatesTrimmer`

They check:

```java
synchronizer.isSyncRequested()
synchronizer.isSyncRequestPending()
synchronizer.isSynchronizing()
```

They also use a startup grace window.

Why:

- `isSynchronizing()` can still be false while a sync is requested or pending.
- Background work could otherwise start expensive repository/disk work just before sync begins.
- QDN directory-size scans, cleanup, metadata fetches, AT trim, and AT prune are not consensus-critical in the moment they run.

Important behavior:

- The storage/cleanup work is deferred before starting expensive scans.
- It does not attempt to abort a directory scan midway.

## Directory Size Scan Change

`ArbitraryDataStorageManager` no longer uses `FileUtils.sizeOfDirectory(...)`.

It now uses:

```java
Files.walkFileTree(...)
```

Why:

- Uses `BasicFileAttributes` supplied during traversal.
- Avoids extra `File` object/stat churn.
- Works across Linux, Windows, and macOS through Java NIO.
- Ignores individual file visit failures and continues scanning.

## Thread CPU Monitor

`ThreadCpuMonitor` was added as a diagnostic sampler.

It:

- samples JVM thread CPU deltas using `ThreadMXBean`
- logs top threads and grouped thread names
- helps identify CPU contention from background managers during sync

It does not:

- change consensus behavior
- change scheduling decisions
- touch repository state

The CPU monitor logs were intentionally kept when other temporary AT/block timing logs were removed.

## Logging Cleanup

Temporary detailed timing logs were removed in a later commit.

Removed categories included:

- `[AT.exec]`
- `[AT.apply.fees]`
- `[AT.apply.txn]`
- `[AT.areAtsValid]`
- `[Block.process.summary]`
- `[Block.process.linkTx]`
- sync batch/block timing summaries
- QDN storage scan timing summaries
- one-off startup/backfill progress logs added during testing

Kept:

- CPU monitor logs.
- Existing normal application logs.
- Exceptions and thrown `DataException` messages.

## Bootstrap / Rebuild Expectations

Bootstrapping users need the new canonical/required tables included in the DB:

- `ATStates`
- `ATStatesData` during rollout
- `ATStateBlobs`
- `ATs`
- `ATRuntime`
- transaction subtype tables
- transaction height/sequence data

Derived caches can be rebuilt after import:

- `ATIncomingTransactions`
- `ATNextIncoming`
- `ATCurrentState`
- `ATExecutionQueue`

Consistency checks should ensure:

- every retained/latest AT state can resolve bytes from `ATStateBlobs` or legacy `ATStatesData`
- resolved bytes hash to `ATStates.state_hash`
- no derived queue/cursor mismatch survives repair/rebuild

If a bootstrap excludes required state bytes or canonical transaction data needed for rebuilding, it should fail loudly rather than create partial derived state.

## Orphan Safety

The orphan-sensitive areas are:

- `ATStates.previous_height`
- current-state pointer rollback
- `ATIncomingTransactions` delete by block height
- `ATNextIncoming` recompute for affected ATs
- `ATExecutionQueue` recompute for affected ATs
- `ATRuntime` revert/update

Expected behavior:

- Delete AT state rows for the orphaned height.
- Delete legacy `ATStatesData` rows for that height.
- Do not delete `ATStateBlobs` in the hot orphan path.
- If deleted state was current, move pointer to `previous_height` when available.
- If `previous_height` is missing, recompute from canonical `ATStates`.
- If deleted state was not current, leave current pointer alone.
- Recompute affected inbox cursors and queue rows.

## Tests Run During Cleanup

After removing temporary logs and after adding comments, these passed:

```bash
mvn -q -DskipTests compile
mvn -q -Dtest=AtRepositoryTests,SleepUntilMessageTests,SleepUntilMessageOrHeightTests,GetNextTransactionTests,BlockTests test
git diff --check
```

These are focused tests, not a replacement for full CI.

## Reviewer Checklist

Before handing this to production, a reviewer should verify:

- `ATExecutionQueue` verifier cannot allow a stale queue row to change wake decisions.
- `ATNextIncoming.sleep_until_message_timestamp` mismatch always causes recompute/rejection.
- `findNextTransaction()` ordering matches the original canonical query.
- Same-block transaction sequence ordering is strictly greater than the stored timestamp sequence.
- Message wake uses `incoming_block_height + 1`, preserving prior-chain semantics.
- Orphaning a block with AT-recipient transactions removes/recomputes affected cursor/queue rows.
- Orphaning a block with AT state rows restores current-state pointers correctly.
- `ATStateBlobs` reads always verify against `ATStates.state_hash`.
- Legacy `ATStatesData` and blob bytes agree when both exist.
- Bootstrap/rebuild can recreate all derived tables deterministically.
- Trim/prune cannot remove bytes needed by retained/latest AT states.
- Background managers defer when sync is requested/pending/active.

## Known Handoff Concern To Verify

Review `Block.processAtFeesAndStates()` around `currentHeightOnlyRuntimeUpdateATs`.

The code separates ATs into:

- `fullRuntimeUpdateATs`
- `currentHeightOnlyRuntimeUpdateATs`

The intent appears to be:

- full runtime updates write all mutable runtime fields plus current height
- current-height-only updates advance only current-state pointers

Make sure the current-height-only list is actually persisted through `updateCurrentATStates(...)` or another equivalent path. If it is only counted for instrumentation and not written, an AT whose runtime flags did not change could fail to advance its current-state pointer. That would not necessarily show up in narrow sleep/message tests, so it should be explicitly reviewed before production rollout.

## Future Cleanup Plan

Once the branch has enough test/live confidence:

1. Remove or reduce rollout-only legacy `ATStatesData` writes if blob verification has proven stable.
2. Decide which current-state pointer is primary and simplify duplicated pointer maintenance.
3. Add full rebuild commands/tests for:
   - `ATIncomingTransactions`
   - `ATNextIncoming`
   - `ATCurrentState`
   - `ATExecutionQueue`
   - `ATStateBlobs`
4. Add bootstrap validation for blob/state hash resolution.
5. Consider blob garbage collection only as background maintenance, never in block/orphan hot paths.
6. Revisit delta/checkpoint storage only after measuring duplicate blob rate and changed-byte rate.

## Main Files To Review

- `src/main/java/org/qortal/block/Block.java`
- `src/main/java/org/qortal/at/AT.java`
- `src/main/java/org/qortal/at/QortalATAPI.java`
- `src/main/java/org/qortal/repository/ATRepository.java`
- `src/main/java/org/qortal/repository/hsqldb/HSQLDBATRepository.java`
- `src/main/java/org/qortal/repository/hsqldb/HSQLDBDatabaseUpdates.java`
- `src/main/java/org/qortal/data/at/ATStateData.java`
- `src/main/java/org/qortal/controller/arbitrary/ArbitraryDataManager.java`
- `src/main/java/org/qortal/controller/arbitrary/ArbitraryDataCleanupManager.java`
- `src/main/java/org/qortal/controller/arbitrary/ArbitraryDataStorageManager.java`
- `src/main/java/org/qortal/controller/repository/AtStatesPruner.java`
- `src/main/java/org/qortal/controller/repository/AtStatesTrimmer.java`
- `src/main/java/org/qortal/controller/ThreadCpuMonitor.java`

## Short Version

The branch changes AT processing from repeated polling/scanning to maintained derived knowledge:

- derived inbox says which ATs received transactions
- derived cursor says the next message wake for each sleeping AT
- derived queue says which ATs are due at a block height
- current-state pointers avoid repeated latest-state searches
- content-addressed blobs reduce repeated large state-byte writes
- runtime metadata moves out of the wide `ATs` row
- QDN and maintenance work defer around sync

The intended safety model is:

- canonical chain/state data remains authoritative
- derived tables are rebuildable
- stale derived data is rejected or repaired
- unrepaired mismatch fails loudly
- no config flag changes consensus behavior
