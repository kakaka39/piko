package app.crimera.patches.newx.timeline.postfilter

import app.morphe.patcher.util.p0Register
import app.morphe.util.cloneMutable
import app.crimera.patches.common.requireExactlyOne
import app.crimera.bytecode.Target
import app.crimera.patches.newx.models.*
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.CUSTOM_NEW_X_COMPATIBILITY
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.timeline.newXTimelineFilterPatch
import app.crimera.patches.settings.customScreen
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.toggle
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.bytecodeContext
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val OBJECT = "Ljava/lang/Object;"
private const val BRIDGES = "Lapp/morphe/extension/newx/mute/MuteModel;"

internal val scheduledMuteModelPatch = bytecodePatch(default = false) {
    dependsOn(newXTimelineTextModelAdapterPatch, newXPostModelResolutionPatch)
    execute {
        fun formatter(type: String) = requireExactlyOne(
            "NewX scheduled mute model formatter $type",
            bytecodeContext.classDefBy(type).methods.filter { it.name == "toString" },
        )
        val posts = resolvedNewXPostModels()
        val timeline = resolvedNewXTimelineModels()
        val bridge = bytecodeContext.mutableClassDefBy(BRIDGES)
        fun helper(name: String) = bridge.requireBridge(name, OBJECT, OBJECT).let { original ->
            original.cloneMutable(additionalRegisters = 2).also { replacement ->
                bridge.methods.remove(original)
                bridge.methods.add(replacement)
            }
        }
        fun field(name: String, reference: FieldReference) {
            bytecodeContext.mutableClassDefBy(reference.definingClass).requirePublicFields(listOf(reference))
            val method = helper(name)
            method.patchBridge {
                val receiver = method.p0Register
                instanceOf(0, receiver, reference.definingClass)
                ifEqz(0, Target.Local("missing"))
                checkCast(receiver, reference.definingClass)
                iget(0, receiver, reference)
                returnObject(0)
                label("missing")
                constInt(0, 0)
                returnObject(0)
            }
        }
        field("postResult", timeline.postResultField)
        field("contextualCanonical", posts.contextualCanonicalPostField)
        field("repostedCanonical", posts.repostedCanonicalPostField)
        field("reposted", posts.contextualRepostedPostField)
        val contextual = formatter(posts.contextualPostDescriptor)
        field("contextualQuoted", contextual.fieldForToStringLabel(", quotedPost="))
        val reposted = formatter(posts.contextualRepostedPostField.type)
        field("repostedQuoted", reposted.fieldForToStringLabel(", quotedPost="))
        val canonical = formatter(posts.canonicalPostDescriptor)
        field("id", canonical.fieldForToStringLabel("CanonicalPost(id="))
        field("text", canonical.fieldForToStringLabel(", text="))
        field("parentId", canonical.fieldForToStringLabel(", repliedPostId="))
        val note = canonical.fieldForToStringLabel(", notePost=")
        field("note", note)
        val noteModel = formatter(note.type)
        field("noteResult", noteModel.fieldForToStringLabel(", noteTweetResult="))
        val loadedNote = Fingerprint(
            definingClass = "Lcom/x/models/notes/", name = "toString",
            filters = listOf(string("NotePost(postEntityList=")),
        ).requireSingle("loaded note post")
        field("noteText", loadedNote.originalMethod.fieldForToStringLabelThroughAppendHelper(", text=") {
            bytecodeContext.classDefByOrNull(it)
        } ?: loadedNote.fieldForToStringLabel(", text="))
        val author = canonical.fieldForToStringLabel(", author=")
        field("author", author)
        val getter = resolveAuthorFieldGetter(
            authorDescriptor = author.type,
            minimalUserMatch = MinimalUserModelFingerprint.requireSingle("mute author model"),
            fieldLabel = ", screenName=",
            expectedType = "Ljava/lang/String;",
            semanticName = "mute author screen name",
        )
        val nameBridge = helper("screenName")
        nameBridge.patchBridge {
            val receiver = nameBridge.p0Register
            instanceOf(0, receiver, author.type)
            ifEqz(0, Target.Local("missing"))
            checkCast(receiver, author.type)
            invokeInterface(getter, receiver)
            moveResult(0, OBJECT)
            returnObject(0)
            label("missing")
            constInt(0, 0)
            returnObject(0)
        }
    }
}

@Suppress("unused")
val scheduledKeywordMutePatch = bytecodePatch(
    name = "NewX: Strict keyword mute with schedules",
    description = "Strict keyword matching with expiry, weekly schedules and conversation filtering.",
    default = false,
) {
    unsupportedOnMount(default = false)
    compatibleWith(CUSTOM_NEW_X_COMPATIBILITY)
    dependsOn(scheduledMuteModelPatch, newXTimelineFilterPatch)
    newXSettings {
        category(Categories.CONTENT) {
            toggle(id = "newx.custom.keyword_mute.enabled", strings = settingStrings("piko_newx_custom_keyword_mute"), order = 410, defaultValue = true)
            toggle(id = "newx.custom.keyword_mute.conversations", strings = settingStrings("piko_newx_custom_mute_conversations"), order = 411, defaultValue = false)
            customScreen(id = "newx.custom.keyword_mute.rules", strings = settingStrings("piko_newx_custom_keyword_rules"), order = 412,
                fragmentClassDescriptor = "Lapp/morphe/extension/newx/mute/KeywordMuteFragment;")
        }
    }
}

@Suppress("unused")
val scheduledAccountMutePatch = bytecodePatch(
    name = "NewX: Account mute with schedules",
    description = "Exact account matching with independent schedules and grouped account rules.",
    default = false,
) {
    unsupportedOnMount(default = false)
    compatibleWith(CUSTOM_NEW_X_COMPATIBILITY)
    dependsOn(scheduledMuteModelPatch, newXTimelineFilterPatch)
    newXSettings {
        category(Categories.CONTENT) {
            toggle(id = "newx.custom.account_mute.enabled", strings = settingStrings("piko_newx_custom_account_mute"), order = 420, defaultValue = true)
            customScreen(id = "newx.custom.account_mute.rules", strings = settingStrings("piko_newx_custom_account_rules"), order = 421,
                fragmentClassDescriptor = "Lapp/morphe/extension/newx/mute/AccountMuteFragment;")
        }
    }
}
