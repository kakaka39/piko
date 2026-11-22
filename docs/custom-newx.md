# Custom NewX patches

`custom/piko` in `kakaka39/piko` includes both the existing custom Twitter
patches and the three custom NewX patches transferred from the local
`custom/piko-newx` worktree. The bundle is updated to upstream `crimera/piko`
tag `v3.12.0-dev.4` (`03c0a79d`), including its NewX patches and Morphe Patcher
2.4.0 migration. The previous NewX base was `x-lite` commit
`25743f5205937a8f8892994958e02ec7eab83e00`. The custom NewX sources
originally came from `kakaka39/piko-newx` commit
`7c8c19b201f2660d516beb896f73b46f928b7e2e`.

The custom sources are maintained directly in `extensions/newx`,
`patches/src/main`, and `tests/newx-mute`. Building uses the normal upstream
Gradle tasks and includes the full upstream patch set. The old NewX-only
builder, source pruning, overlay application, and resource-check exclusions
are no longer needed.

## Additional patches

The following optional patches target X `12.43.0-prod.01` APKM files:

| Patch | Function |
| --- | --- |
| `NewX: Strict keyword mute with schedules` | Keyword matching with expiry, weekly schedules, and conversation filtering |
| `NewX: Account mute with schedules` | Account matching with independent schedules and grouped account rules |
| `NewX: Use Android document picker for composer` | Android file picker for posts and replies |

All three patches are unselected by default. Their compatibility remains
limited to the version checked during their original development; upstream
patches retain their own compatibility declarations.

Mute rules are configured in dedicated keyword and account screens under NewX
content settings. Both use the same list rows, switches, floating add button,
and editor dialog components as Piko's Post filtering screen. They remain
independent of Post filtering. The content menu contains only the two screen
entries; global mute and conversation switches are inside the screens, backed
by the same existing settings. Editors change text and periods while preserving
each rule's enabled state. Tap a row to edit it, use its switch to enable
or disable it, and long-press a row to select rules in the same full-page list.
Selection adds checkboxes to the left of the existing keyword/account labels
and schedule summaries. The mute switches are disabled during selection;
cancel, delete, and bulk schedule actions stay below the scrolling list.
Rules support indefinite
muting, an expiry time, and weekly periods in the device time zone. Refresh the
timeline after changing rules or crossing a period boundary. Conversation
matching relies on parent posts observed in fetched responses and cannot link
posts whose connecting parent has never been fetched.

Rule keys and formats are preserved from the previous custom repository.
NewX settings backup includes the custom rules. Keyword rules use format v1;
account rules use v2 and can also read v1. Existing upstream post-filter rules
remain separate.

The document picker replacement is scoped to the composer, preserves the
original result parser, and accepts at most four items.

## Build and verification

Use JDK 21 and an Android SDK configured through `local.properties` or the
standard SDK environment variables. GitHub Packages dependencies require
read access, using the upstream `gpr.user` / `gpr.key` Gradle properties or
`GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables.

```powershell
./gradlew.bat buildAndroid :patches:test :patches:lintNewxResolvers :patches:checkStringResources
./gradlew.bat :patches:checkExtensionDescriptors '--args=--extensions=build/resources/main/extensions --sources=src/main/kotlin/app/crimera/patches/newx'
./tests/newx-mute/run.ps1
./gradlew.bat :extensions:newx:testDebugUnitTest -PnewxMuteUiTests --tests app.morphe.extension.newx.mute.MuteRulesFragmentTest
./tests/account-mute/run.ps1
./tests/account-mute/run-patch-guards.ps1
```

On Linux, use `./gradlew` and run the test script with `pwsh -File`.
The `newxMuteUiTests` property selects an isolated Robolectric suite using the
real shared settings library; ordinary NewX unit tests retain their existing
lightweight settings stand-ins.
The standard output is `patches/build/libs/patches-<version>.mpp`, with the
version taken from `gradle.properties`. Both the custom Twitter patches and
the custom NewX patches are included in this bundle. Upstream release
configuration is retained. No NewX-only workflow is required.

Only the extension descriptor audit above is scoped to NewX. The upstream
scanner confuses same-named descriptor constants across app families when
scanning all sources together. The initial migration's full-source audit
reported 60 unresolved references; the same references were also reported
against the pristine upstream sources. Building, patch tests, and resource
checks continue to cover the normal full bundle.

After upstream updates, rerun these checks and inspect patch compatibility.
APKM application and on-device checks remain separate from unit tests. Check
keyword/account toggles, expiry and weekly boundaries, quotes and reposts,
settings backup/restore, large text and landscape dialogs, and composer media
selection (including cancellation and returning to the composer).
