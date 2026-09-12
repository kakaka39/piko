/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.nativeMediaPicker

import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.NATIVE_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val PICK_MULTIPLE_CONTRACT =
    "Landroidx/activity/result/contract/ActivityResultContracts\$PickMultipleVisualMedia;"
private const val ACTIVITY_RESULT_CONTRACT = "Landroidx/activity/result/contract/a;"
private const val ACTIVITY_RESULT_CALLBACK = "Landroidx/activity/result/b;"
private const val ACTIVITY_RESULT_LAUNCHER = "Landroidx/activity/result/d;"
private const val COMPOSE_ACTIVITY_RESULT_LAUNCHER = "Landroidx/activity/compose/l;"
private const val PICK_VISUAL_MEDIA_TYPE =
    "Landroidx/activity/result/contract/ActivityResultContracts\$PickVisualMedia\$f;"
private const val IMAGE_ONLY_VISUAL_MEDIA_TYPE =
    "Landroidx/activity/result/contract/ActivityResultContracts\$PickVisualMedia\$c;"
private const val PICK_VISUAL_MEDIA_REQUEST = "Landroidx/activity/result/m;"
private const val NATIVE_MEDIA_PICKER_CONTRACT =
    "$NATIVE_DESCRIPTOR/mediaPicker/NativeMediaPickerContract;"
private const val INLINE_COMPOSER = "Lcom/twitter/inlinecomposer/k0;"
private const val INLINE_COMPOSER_PRESENTER = "Lcom/twitter/inlinecomposer/t;"
private const val INLINE_COMPOSER_GALLERY_CLICK = "Lcom/twitter/inlinecomposer/y;"
private const val INLINE_COMPOSER_MEDIA_RESULT_CALLBACK = "Lcom/twitter/inlinecomposer/o;"
private const val GALLERY_GRID_CONTENT_VIEW_ARGS =
    "Lcom/twitter/gallerygrid/api/GalleryGridContentViewArgs;"
private const val ACTIVITY_RESULT_EVENT_ROUTER = "Lcom/twitter/util/rx/q;"
private const val CONCURRENT_CALLBACK = "Lcom/twitter/util/concurrent/c;"
private const val MEDIA_ATTACHMENT_MANAGER = "Lcom/twitter/media/attachment/j;"

private object ComposerMediaContractRegistrationFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_DIRECT,
                definingClass = PICK_MULTIPLE_CONTRACT,
                name = "<init>",
                parameters = listOf("I"),
                returnType = "V",
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = "Landroidx/activity/ComponentActivity;",
                name = "registerForActivityResult",
                parameters = listOf(ACTIVITY_RESULT_CONTRACT, ACTIVITY_RESULT_CALLBACK),
                returnType = ACTIVITY_RESULT_LAUNCHER,
            ),
        ),
)

private object ComposerGalleryClickFingerprint : Fingerprint(
    classFingerprint = ComposerMediaContractRegistrationFingerprint,
    returnType = "V",
    parameters = listOf(),
    strings = listOf("android_photo_picker_enabled", "gallery", "composition", "photo_picker", "impression"),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Landroidx/activity/result/n;",
                name = "a",
                parameters = listOf(PICK_VISUAL_MEDIA_TYPE),
                returnType = PICK_VISUAL_MEDIA_REQUEST,
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = ACTIVITY_RESULT_LAUNCHER,
                name = "b",
                parameters = listOf("Ljava/lang/Object;", "Landroidx/core/app/d;"),
                returnType = "V",
            ),
        ),
)

private object MinimalComposerMediaContractRegistrationFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_DIRECT,
                definingClass = PICK_MULTIPLE_CONTRACT,
                name = "<init>",
                parameters = listOf("I"),
                returnType = "V",
            ),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Landroidx/activity/compose/d;",
                name = "a",
                parameters =
                    listOf(
                        ACTIVITY_RESULT_CONTRACT,
                        "Lkotlin/jvm/functions/Function1;",
                        "Landroidx/compose/runtime/Composer;",
                        "I",
                    ),
                returnType = COMPOSE_ACTIVITY_RESULT_LAUNCHER,
            ),
        ),
    custom = { methodDef, classDef ->
        classDef.type.startsWith("Lcom/x/composer/minimal/") &&
            methodDef.implementation?.instructions?.count { instruction ->
                val reference = instruction.getReference<MethodReference>()
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    reference?.definingClass == PICK_MULTIPLE_CONTRACT &&
                    reference.name == "<init>" &&
                    reference.parameterTypes == listOf("I") &&
                    reference.returnType == "V"
            } == 2
    },
)

private object ComposerToolbarMediaContractRegistrationFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("onAttachMedia"),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_DIRECT,
                definingClass = PICK_MULTIPLE_CONTRACT,
                name = "<init>",
                parameters = listOf("I"),
                returnType = "V",
            ),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Landroidx/activity/compose/d;",
                name = "a",
                parameters =
                    listOf(
                        ACTIVITY_RESULT_CONTRACT,
                        "Lkotlin/jvm/functions/Function1;",
                        "Landroidx/compose/runtime/Composer;",
                        "I",
                    ),
                returnType = COMPOSE_ACTIVITY_RESULT_LAUNCHER,
            ),
        ),
    custom = { methodDef, classDef ->
        classDef.type.startsWith("Lcom/x/composer/ui/") &&
            methodDef.implementation?.instructions?.count { instruction ->
                val reference = instruction.getReference<MethodReference>()
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    reference?.definingClass == PICK_MULTIPLE_CONTRACT &&
                    reference.name == "<init>" &&
                    reference.parameterTypes == listOf("I") &&
                    reference.returnType == "V"
            } == 1
    },
)

private object InlineComposerGalleryClickFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    strings = listOf("reply_composition", "composition", "add_photo"),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Lcom/twitter/gallerygrid/api/c;",
                name = "a",
                parameters = emptyList(),
                returnType = "Z",
            ),
            methodCall(
                opcode = Opcode.INVOKE_DIRECT,
                definingClass = GALLERY_GRID_CONTENT_VIEW_ARGS,
                name = "<init>",
                parameters = listOf("Ljava/lang/String;", "Lcom/twitter/media/util/i1;", "I"),
                returnType = "V",
            ),
        ),
    custom = { _, classDef -> classDef.type == INLINE_COMPOSER_GALLERY_CLICK },
)

private object InlineComposerExternalPickerFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("image/*", "android.intent.action.GET_CONTENT", "android.intent.category.OPENABLE"),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Lcom/twitter/media/util/d;",
                name = "v0",
                parameters = listOf("I", "Landroid/content/Intent;"),
                returnType = "V",
            ),
        ),
    custom = { _, classDef -> classDef.type == INLINE_COMPOSER },
)

private object InlineComposerResultRegistrationFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Lcom/twitter/app/common/i;",
                name = "a",
                parameters = listOf(ACTIVITY_RESULT_EVENT_ROUTER, "I", CONCURRENT_CALLBACK),
                returnType = "V",
            ),
        ),
    custom = { methodDef, classDef ->
        classDef.type == INLINE_COMPOSER_PRESENTER && methodDef.name == "<init>"
    },
)

private object InlineComposerMediaResultCallbackFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL_RANGE,
                definingClass = MEDIA_ATTACHMENT_MANAGER,
                name = "e",
                parameters =
                    listOf(
                        "I",
                        "I",
                        "Landroid/content/Intent;",
                        "Lcom/twitter/media/attachment/k;",
                        "Lcom/twitter/media/util/i1;",
                    ),
                returnType = "V",
            ),
        ),
    custom = { methodDef, classDef ->
        classDef.type == INLINE_COMPOSER_MEDIA_RESULT_CALLBACK && methodDef.name == "a"
    },
)

@Suppress("unused")
val nativeMediaPickerPatch =
    bytecodePatch(
        name = "Use Android document picker for composer",
        description = "Opens Android's file picker directly, including gallery apps, when adding images to new posts and replies.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(sharedExtensionPatch)

        execute {
            val registrationMethod = ComposerMediaContractRegistrationFingerprint.method
            val constructorIndex =
                ComposerMediaContractRegistrationFingerprint.instructionMatches[0].index
            val constructorInstruction = registrationMethod.getInstruction(constructorIndex)
            val constructorReference = constructorInstruction.getReference<MethodReference>()
                ?: throw PatchException("Composer media contract constructor has no method reference")
            val constructorRegisters = constructorInstruction.registersUsed
            if (
                constructorReference.definingClass != PICK_MULTIPLE_CONTRACT ||
                constructorRegisters.size != 2
            ) {
                throw PatchException("Unexpected composer media contract constructor")
            }

            val newInstanceIndex = constructorIndex - 1
            val newInstanceInstruction =
                registrationMethod.getInstruction<OneRegisterInstruction>(newInstanceIndex)
            val newInstanceType =
                registrationMethod.getInstruction(newInstanceIndex).getReference<TypeReference>()
                    ?: throw PatchException("Composer media contract allocation has no type reference")
            if (
                registrationMethod.getInstruction(newInstanceIndex).opcode != Opcode.NEW_INSTANCE ||
                newInstanceType.type != PICK_MULTIPLE_CONTRACT ||
                newInstanceInstruction.registerA != constructorRegisters[0]
            ) {
                throw PatchException("Unexpected composer media contract allocation")
            }

            registrationMethod.replaceInstruction(
                newInstanceIndex,
                "new-instance v${constructorRegisters[0]}, $NATIVE_MEDIA_PICKER_CONTRACT",
            )
            registrationMethod.replaceInstruction(
                constructorIndex,
                "invoke-direct {v${constructorRegisters[0]}, v${constructorRegisters[1]}}, " +
                    "$NATIVE_MEDIA_PICKER_CONTRACT-><init>(I)V",
            )

            val registerForResultIndex =
                ComposerMediaContractRegistrationFingerprint.instructionMatches[1].index
            val moveResultInstruction = registrationMethod.getInstruction<OneRegisterInstruction>(registerForResultIndex + 1)
            val launcherFieldInstruction = registrationMethod.getInstruction(registerForResultIndex + 2)
            val launcherField = launcherFieldInstruction.getReference<FieldReference>()
                ?: throw PatchException("Composer media launcher assignment has no field reference")
            val launcherFieldRegisters = launcherFieldInstruction.registersUsed
            if (
                moveResultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT ||
                launcherFieldInstruction.opcode != Opcode.IPUT_OBJECT ||
                launcherField.type != ACTIVITY_RESULT_LAUNCHER ||
                launcherFieldRegisters.size != 2 ||
                launcherFieldRegisters[0] != moveResultInstruction.registerA
            ) {
                throw PatchException("Unexpected composer media launcher assignment")
            }

            ComposerGalleryClickFingerprint.method.apply {
                val requestFactoryIndex = ComposerGalleryClickFingerprint.instructionMatches[0].index
                val requestTypeIndex = requestFactoryIndex - 1
                val requestTypeInstruction = getInstruction(requestTypeIndex)
                val requestTypeReference = requestTypeInstruction.getReference<FieldReference>()
                    ?: throw PatchException("Composer media request type has no field reference")
                if (
                    requestTypeInstruction.opcode != Opcode.SGET_OBJECT ||
                    requestTypeReference.definingClass != IMAGE_ONLY_VISUAL_MEDIA_TYPE ||
                    requestTypeReference.type != IMAGE_ONLY_VISUAL_MEDIA_TYPE
                ) {
                    throw PatchException("Unexpected composer media request construction")
                }

                val launchIndex = ComposerGalleryClickFingerprint.instructionMatches[1].index
                val launcherReadInstruction = getInstruction(launchIndex - 1)
                val launcherReadField = launcherReadInstruction.getReference<FieldReference>()
                    ?: throw PatchException("Composer media launcher read has no field reference")
                if (
                    launcherReadInstruction.opcode != Opcode.IGET_OBJECT ||
                    launcherReadField != launcherField
                ) {
                    throw PatchException("Composer gallery click does not use the registered media launcher")
                }

                val analyticsEmptyStringIndex =
                    implementation!!.instructions.withIndex().filter { (index, instruction) ->
                        index < requestTypeIndex &&
                            instruction.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
                            instruction.getReference<StringReference>()?.string == ""
                    }.map { it.index }.singleOrNull()
                        ?: throw PatchException("Could not identify composer picker analytics initialization")
                val analyticsEmptyStringRegister =
                    getInstruction<OneRegisterInstruction>(analyticsEmptyStringIndex).registerA

                addInstructionsWithLabels(
                    0,
                    """
                        const-string v$analyticsEmptyStringRegister, ""
                        goto :piko_native_media_picker
                    """,
                    ExternalLabel("piko_native_media_picker", getInstruction(requestTypeIndex)),
                )
            }

            MinimalComposerMediaContractRegistrationFingerprint.method.apply {
                val constructorIndexes =
                    implementation!!.instructions.withIndex().filter { (_, instruction) ->
                        val reference = instruction.getReference<MethodReference>()
                        instruction.opcode == Opcode.INVOKE_DIRECT &&
                            reference?.definingClass == PICK_MULTIPLE_CONTRACT &&
                            reference.name == "<init>" &&
                            reference.parameterTypes == listOf("I") &&
                            reference.returnType == "V"
                    }.map { it.index }
                if (constructorIndexes.size != 2) {
                    throw PatchException("Unexpected minimal composer media contract count")
                }

                constructorIndexes.forEach { constructorIndex ->
                    val constructorInstruction = getInstruction(constructorIndex)
                    val constructorRegisters = constructorInstruction.registersUsed
                    if (constructorRegisters.size != 2) {
                        throw PatchException("Unexpected minimal composer media contract constructor")
                    }

                    val newInstanceIndex =
                        (constructorIndex - 1 downTo maxOf(0, constructorIndex - 4)).firstOrNull { index ->
                            val instruction = getInstruction(index)
                            val type = instruction.getReference<TypeReference>()
                            instruction.opcode == Opcode.NEW_INSTANCE &&
                                type?.type == PICK_MULTIPLE_CONTRACT &&
                                (instruction as OneRegisterInstruction).registerA == constructorRegisters[0]
                        } ?: throw PatchException("Could not locate minimal composer media contract allocation")
                    val newInstanceInstruction =
                        getInstruction<OneRegisterInstruction>(newInstanceIndex)
                    val newInstanceType = getInstruction(newInstanceIndex).getReference<TypeReference>()
                        ?: throw PatchException("Minimal composer media contract allocation has no type reference")
                    if (
                        getInstruction(newInstanceIndex).opcode != Opcode.NEW_INSTANCE ||
                        newInstanceType.type != PICK_MULTIPLE_CONTRACT ||
                        newInstanceInstruction.registerA != constructorRegisters[0]
                    ) {
                        throw PatchException("Unexpected minimal composer media contract allocation")
                    }

                    replaceInstruction(
                        newInstanceIndex,
                        "new-instance v${constructorRegisters[0]}, $NATIVE_MEDIA_PICKER_CONTRACT",
                    )
                    replaceInstruction(
                        constructorIndex,
                        "invoke-direct {v${constructorRegisters[0]}, v${constructorRegisters[1]}}, " +
                            "$NATIVE_MEDIA_PICKER_CONTRACT-><init>(I)V",
                    )
                }
            }

            ComposerToolbarMediaContractRegistrationFingerprint.method.apply {
                val constructorIndex =
                    ComposerToolbarMediaContractRegistrationFingerprint.instructionMatches[0].index
                val constructorInstruction = getInstruction(constructorIndex)
                val constructorRegisters = constructorInstruction.registersUsed
                if (constructorRegisters.size != 2) {
                    throw PatchException("Unexpected composer toolbar media contract constructor")
                }

                val newInstanceIndex =
                    (constructorIndex - 1 downTo maxOf(0, constructorIndex - 4)).firstOrNull { index ->
                        val instruction = getInstruction(index)
                        val type = instruction.getReference<TypeReference>()
                        instruction.opcode == Opcode.NEW_INSTANCE &&
                            type?.type == PICK_MULTIPLE_CONTRACT &&
                            (instruction as OneRegisterInstruction).registerA == constructorRegisters[0]
                    } ?: throw PatchException("Could not locate composer toolbar media contract allocation")

                replaceInstruction(
                    newInstanceIndex,
                    "new-instance v${constructorRegisters[0]}, $NATIVE_MEDIA_PICKER_CONTRACT",
                )
                replaceInstruction(
                    constructorIndex,
                    "invoke-direct {v${constructorRegisters[0]}, v${constructorRegisters[1]}}, " +
                        "$NATIVE_MEDIA_PICKER_CONTRACT-><init>(I)V",
                )
            }

            InlineComposerExternalPickerFingerprint.method.apply {
                // Direct GET_CONTENT to DocumentsUI while keeping gallery apps available.
                val categoryIndex =
                    implementation!!.instructions.withIndex().singleOrNull { (_, instruction) ->
                        instruction.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
                            instruction.getReference<StringReference>()?.string == "android.intent.category.OPENABLE"
                    }?.index ?: throw PatchException("Could not identify inline composer picker category")
                val categoryMoveResult = getInstruction<OneRegisterInstruction>(categoryIndex + 2)
                if (categoryMoveResult.opcode != Opcode.MOVE_RESULT_OBJECT) {
                    throw PatchException("Unexpected inline composer picker category result")
                }
                val intentRegister = categoryMoveResult.registerA
                addInstructions(
                    categoryIndex + 3,
                    """
                        invoke-static {v$intentRegister}, $NATIVE_MEDIA_PICKER_CONTRACT->prepareIntent(Landroid/content/Intent;)Landroid/content/Intent;
                        move-result-object v$intentRegister
                    """,
                )
            }

            InlineComposerResultRegistrationFingerprint.method.apply {
                val registrationMatches =
                    implementation!!.instructions.withIndex().filter { (_, instruction) ->
                        val reference = instruction.getReference<MethodReference>()
                        instruction.opcode == Opcode.INVOKE_STATIC &&
                            reference?.definingClass == "Lcom/twitter/app/common/i;" &&
                            reference.name == "a" &&
                            reference.parameterTypes ==
                                listOf(ACTIVITY_RESULT_EVENT_ROUTER, "I", CONCURRENT_CALLBACK) &&
                            reference.returnType == "V"
                    }.filter { (index, instruction) ->
                        val registers = instruction.registersUsed
                        val requestCodeInstruction =
                            getInstruction(index - 1) as? NarrowLiteralInstruction
                        registers.size == 3 &&
                            requestCodeInstruction?.narrowLiteral == 0x102 &&
                            (requestCodeInstruction as? OneRegisterInstruction)?.registerA == registers[1]
                    }
                val cameraRegistration = registrationMatches.singleOrNull()
                    ?: throw PatchException("Could not identify inline composer media result registration")
                val registrationIndex = cameraRegistration.index
                val registrationRegisters = cameraRegistration.value.registersUsed
                val callbackConstructor = getInstruction(registrationIndex - 2)
                val callbackConstructorReference = callbackConstructor.getReference<MethodReference>()
                    ?: throw PatchException("Inline composer result callback has no constructor reference")
                val callbackConstructorRegisters = callbackConstructor.registersUsed
                val callbackAllocation = getInstruction(registrationIndex - 3)
                val callbackAllocationReference = callbackAllocation.getReference<TypeReference>()
                    ?: throw PatchException("Inline composer result callback has no allocation type")
                if (
                    callbackConstructor.opcode != Opcode.INVOKE_DIRECT ||
                    callbackConstructorReference.definingClass != INLINE_COMPOSER_MEDIA_RESULT_CALLBACK ||
                    callbackConstructorReference.name != "<init>" ||
                    callbackConstructorReference.parameterTypes != listOf(INLINE_COMPOSER_PRESENTER) ||
                    callbackConstructorRegisters.size != 2 ||
                    callbackAllocation.opcode != Opcode.NEW_INSTANCE ||
                    callbackAllocationReference.type != INLINE_COMPOSER_MEDIA_RESULT_CALLBACK ||
                    (callbackAllocation as OneRegisterInstruction).registerA != callbackConstructorRegisters[0] ||
                    callbackConstructorRegisters[0] != registrationRegisters[2]
                ) {
                    throw PatchException("Unexpected inline composer media result callback registration")
                }

                val routerRegister = registrationRegisters[0]
                val requestCodeRegister = registrationRegisters[1]
                val callbackRegister = registrationRegisters[2]
                val presenterRegister = callbackConstructorRegisters[1]
                addInstructions(
                    registrationIndex + 1,
                    """
                        new-instance v$callbackRegister, $INLINE_COMPOSER_MEDIA_RESULT_CALLBACK
                        invoke-direct {v$callbackRegister, v$presenterRegister}, $INLINE_COMPOSER_MEDIA_RESULT_CALLBACK-><init>($INLINE_COMPOSER_PRESENTER)V
                        const/16 v$requestCodeRegister, 0x101
                        invoke-static {v$routerRegister, v$requestCodeRegister, v$callbackRegister}, Lcom/twitter/app/common/i;->a(${ACTIVITY_RESULT_EVENT_ROUTER}I$CONCURRENT_CALLBACK)V
                    """,
                )
            }

            InlineComposerMediaResultCallbackFingerprint.method.apply {
                val dispatchIndex =
                    InlineComposerMediaResultCallbackFingerprint.instructionMatches.single().index
                val dispatchInstruction = getInstruction(dispatchIndex)
                val dispatchRegisters = dispatchInstruction.registersUsed
                if (dispatchRegisters.size != 6) {
                    throw PatchException("Unexpected inline composer media result dispatch")
                }
                val requestCodeRegister = dispatchRegisters[1]
                val intentRegister = dispatchRegisters[3]
                val originalRequestCodeIndex =
                    (dispatchIndex - 1 downTo maxOf(0, dispatchIndex - 12)).firstOrNull { index ->
                        val instruction = getInstruction(index)
                        instruction is NarrowLiteralInstruction &&
                            instruction is OneRegisterInstruction &&
                            instruction.registerA == requestCodeRegister &&
                            instruction.narrowLiteral == 0x102
                    } ?: throw PatchException("Could not identify inline composer media result request code")
                replaceInstruction(
                    originalRequestCodeIndex,
                    "const/16 v$requestCodeRegister, 0x101",
                )
                addInstructionsWithLabels(
                    dispatchIndex,
                    """
                        if-eqz v$intentRegister, :piko_inline_media_dispatch
                        const-string v$requestCodeRegister, "editable_media"
                        invoke-virtual {v$intentRegister, v$requestCodeRegister}, Landroid/content/Intent;->hasExtra(Ljava/lang/String;)Z
                        move-result v$requestCodeRegister
                        if-eqz v$requestCodeRegister, :piko_inline_document_result
                        const/16 v$requestCodeRegister, 0x102
                        goto :piko_inline_media_dispatch
                        :piko_inline_document_result
                        const/16 v$requestCodeRegister, 0x101
                    """,
                    ExternalLabel("piko_inline_media_dispatch", getInstruction(dispatchIndex)),
                )
            }

            InlineComposerGalleryClickFingerprint.method.apply {
                val nativePickerBranchIndex =
                    InlineComposerGalleryClickFingerprint.instructionMatches[0].index
                val composerReadIndex =
                    implementation!!.instructions.withIndex().firstOrNull { (index, instruction) ->
                        val reference = instruction.getReference<FieldReference>()
                        index < nativePickerBranchIndex &&
                            instruction.opcode == Opcode.IGET_OBJECT &&
                            reference?.definingClass == INLINE_COMPOSER_GALLERY_CLICK &&
                            reference.type == INLINE_COMPOSER
                    }?.index ?: throw PatchException("Could not identify inline composer instance")
                val composerRegisters = getInstruction(composerReadIndex).registersUsed
                if (composerRegisters.size != 2) {
                    throw PatchException("Unexpected inline composer instance read")
                }
                val composerRegister = composerRegisters[0]
                addInstructions(
                    nativePickerBranchIndex,
                    """
                        invoke-virtual {v$composerRegister}, $INLINE_COMPOSER->P1()V
                        return-void
                    """,
                )
            }
        }
    }
