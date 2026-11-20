package app.crimera.patches.newx.misc

import app.morphe.patcher.patch.mutableClassDefBy
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.CUSTOM_NEW_X_COMPATIBILITY
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val CONTRACT = "Landroidx/activity/result/contract/ActivityResultContracts\$PickMultipleVisualMedia;"
private const val REPLACEMENT = "Lapp/morphe/extension/newx/mute/NativeMediaPicker;"

@Suppress("unused")
val newXNativeMediaPickerPatch = patch(
    name = "NewX: Use Android document picker for composer",
    description = "Uses the Android file picker with gallery app support for composer media selection.",
    default = false,
) {
    compatibleWith(CUSTOM_NEW_X_COMPATIBILITY)
    category("NewX patches")
    dependsOn(newXExtensionPatch)
    execute {
        val matches = Fingerprint(
            definingClass = "Lcom/x/composer/",
            filters = listOf(methodCall(smali = "$CONTRACT-><init>(I)V")),
        ).scopedMatchAll()
        if (matches.isEmpty()) throw PatchException("NewX composer media contracts were not found")
        // AndroidX publishes this contract as final in some builds. The replacement overrides
        // createIntent and inherits the original parser and synchronous-result behavior.
        mutableClassDefBy(CONTRACT).apply {
            accessFlags = accessFlags and AccessFlags.FINAL.value.inv()
            methods.filter { it.name == "createIntent" || it.name == "parseResult" }.forEach {
                it.accessFlags = it.accessFlags and AccessFlags.FINAL.value.inv()
            }
        }
        matches.forEach { match ->
            val method = match.method
            method.implementation!!.instructions.toList().forEachIndexed { index, instruction ->
                if (instruction.opcode == Opcode.NEW_INSTANCE && instruction.getReference<TypeReference>()?.type == CONTRACT) {
                    val register = (instruction as OneRegisterInstruction).registerA
                    method.replaceInstruction(index, "new-instance v$register, $REPLACEMENT")
                }
                val reference = instruction.getReference<MethodReference>()
                if (reference?.definingClass == CONTRACT && reference.name == "<init>") {
                    val call = instruction as? FiveRegisterInstruction
                        ?: throw PatchException("Unexpected NewX composer media constructor encoding")
                    method.replaceInstruction(index, "invoke-direct {v${call.registerC}, v${call.registerD}}, $REPLACEMENT-><init>(I)V")
                }
            }
        }
    }
}
