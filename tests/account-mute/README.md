# Account mute regression tests

Run `./tests/account-mute/run.ps1`. This includes all existing keyword schedule
and storage tests, then tests account input, exact matching, batch updates,
isolated backup storage, model extraction and expiry. Output stays in `build/`.

After `buildAndroid`, run `./tests/account-mute/run-patch-guards.ps1` (optionally
with `-AndroidSdk PATH`). It executes the compiled production mute entry points
with cached evidence and checks that omitted patches and disabled switches do
not hide posts. It does not execute Android UI or validate behavior on a device.

The extraction fixtures represent X 12.19.1 layouts verified from the target
DEX: `JsonApiTweet.W` and GraphQL `R` identify the outer author, `X` identifies
the repost source, `b.b` identifies the model author, `b.f.I1.c` identifies the
reposter, and `b.d` points to the quote result. `k1.i` is the username.

Device checks: open Piko settings → Mute; test account-only and keyword-only
patch configurations; create/edit/delete a single account; paste mixed-case,
duplicate and malformed multi-line batches; verify Save persists immediately
without a review dialog and Cancel leaves storage untouched; long-press an account
schedule to open selection with that schedule checked; verify the live selection
count, select-all, zero-selection disabled actions, bulk edit, bulk delete and cancel;
ensure unselected schedules for the same account survive; cross an end minute while the app remains open;
check authors, replies, repost actors, repost sources and nested quotes; restore
old and new backups. Handles are exact identifiers; update rules after a rename.

An account can have multiple independent schedules (account schema v2, with v1
read compatibility). Adding appends; editing or deleting affects selected rule
IDs only. Active schedules are combined with OR semantics. Tests cover duplicate
handles, overlapping expiries, independent weekly windows and backups.

New multi-account additions retain a group ID in account schema v2. The list and
selection dialog display one row per batch, with each handle on its own line.
Tapping a row edits its full membership and period; long-press selection and
deletion operate on whole rows. Separate additions remain separate even with
identical periods. Old v1/v2 data without group IDs retains individual rows,
because its original input batches cannot be reconstructed. Backup tests cover
group persistence, membership edits, deletion and independent duplicate handles.
