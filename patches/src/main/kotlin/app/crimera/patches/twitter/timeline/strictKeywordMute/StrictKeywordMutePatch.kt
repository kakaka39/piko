/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.strictKeywordMute

import app.crimera.patches.twitter.ads.timelineEntryHook.timelineEntryHookPatch
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.timeline.tweetinfo.tweetInfoHook
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private object TimelineItemConversionFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;",
    returnType = "Lcom/twitter/model/timeline/urt/n2;",
    parameters = listOf(
        "Lcom/twitter/model/json/timeline/urt/JsonTimelineItem;",
        "Ljava/lang/String;", "Z", "Lcom/twitter/model/timeline/m0;",
    ),
)

@Suppress("unused")
val strictKeywordMutePatch =
    bytecodePatch(
        name = "Strict keyword mute",
        description = "Filters posts containing muted keywords using strict substring matching.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, tweetInfoHook, timelineEntryHookPatch)

        execute {
            enableSettings("strictKeywordMute")
            TimelineItemConversionFingerprint.method.apply {
                // JSON parsing has finished here, including later siblings/replies.
                // Both standalone entries and module items use this conversion.
                if ((implementation?.registerCount ?: 0) <= 5) {
                    throw PatchException("No scratch register for timeline mute check")
                }
                addInstructionsWithLabels(0, """
                    invoke-static {p2}, $PATCHES_DESCRIPTOR/StrictKeywordMute;->shouldRemoveEntry(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :piko_keep_timeline_item
                    const/4 v0, 0x0
                    return-object v0
                """.trimIndent(), ExternalLabel("piko_keep_timeline_item", getInstruction(0)))
            }
        }
    }
