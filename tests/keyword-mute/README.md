# Keyword mute regression tests

Run `./tests/keyword-mute/run.ps1` with Java 17 or newer on PATH.
The runner downloads a checksum-pinned, test-only `org.json` JAR from Maven
Central on its first run. Classes and dependencies stay in ignored `build/`.

The tests execute the production schedule, cache, migration and storage code.
Small Android/Settings stubs avoid initializing Android resources on the JVM.
These tests do not verify Android controls or X timeline behavior on a device.

Device checks: migrate existing keywords, add/edit/delete each rule type,
cancel each picker, check duplicate/past/equal-boundary validation, cross a
schedule boundary with X open, refresh the timeline after expiry, and export
and restore settings. Weekly boundaries use device-local time; a DST gap moves
the selected boundary forward and an overlap uses the earlier offset.
