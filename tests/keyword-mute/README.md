# Keyword mute regression tests

Run `./tests/keyword-mute/run.ps1` with Java 17 or newer on PATH.
The runner downloads a checksum-pinned, test-only `org.json` JAR from Maven
Central on its first run. Classes and dependencies stay in ignored `build/`.

The tests execute the production schedule, cache, migration and storage code.
Small Android/Settings stubs avoid initializing Android resources on the JVM.
These tests do not verify Android controls or X timeline behavior on a device.

Conversation regressions cover a clean root/sibling followed by a matching reply,
unrelated conversations, the conversation toggle, expiry, weekly recurrence and
bounded membership caches. After compiling the extension, also run
`./tests/account-mute/run-patch-guards.ps1` to exercise the production parse and
entry predicates in both parse orders. The strict keyword patch rechecks the
entry predicate in `JsonTimelineEntry.s`, after response parsing, for standalone
posts and module items. Already displayed rows from an earlier response still
require a timeline refresh; these tests do not verify live UI invalidation.

Device checks: migrate existing keywords, add/edit/delete each rule type,
long-press a keyword to select it, select several keywords or all, check the live
selection count and disabled actions for zero selections; edit the selected periods
and enabled states without changing keyword text, delete selected rules, and cancel
without changing storage;
cancel each picker, check duplicate/past/equal-boundary validation, cross a
schedule boundary with X open, refresh the timeline after expiry, and export
and restore settings. Weekly boundaries use device-local time; a DST gap moves
the selected boundary forward and an overlap uses the earlier offset.
