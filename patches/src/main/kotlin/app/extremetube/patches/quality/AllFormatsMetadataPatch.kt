/*
 * Extreme Tube adaptation.
 * Fingerprint structure derived from MorpheApp/morphe-patches (GPLv3 + NOTICE).
 * https://github.com/MorpheApp/morphe-patches
 */
package app.extremetube.patches.quality

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.newInstance
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val DATA_EXTENSION_CLASS = "Lapp/extremetube/extension/AllFormatsData;"
private const val MENU_EXTENSION_CLASS = "Lapp/extremetube/extension/AllFormatsMenu;"

private object VideoStreamingDataToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("VideoStreamingData(itags=")
    )
)

private object VideoStreamingDataConstructorFingerprint : Fingerprint(
    classFingerprint = VideoStreamingDataToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass\$StreamingData;"
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        newInstance("Ljava/util/ArrayList;"),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "Lcom/google/protos/youtube/api/innertube/StreamingDataOuterClass\$StreamingData;"
        )
    )
)

/**
 * Matches YouTube's legacy/advanced quality ListView used after opening Advanced quality.
 * The opcode structure mirrors the currently supported Morphe YouTube versions; the custom
 * ListView check keeps the match narrow without importing Morphe's private resource helpers.
 */
private object VideoQualityMenuViewInflateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L", "L", "L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_SUPER,
        Opcode.CONST,
        Opcode.CONST_4,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST_16,
        Opcode.INVOKE_VIRTUAL,
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CHECK_CAST,
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.CHECK_CAST &&
                (instruction as? ReferenceInstruction)?.reference?.toString() == "Landroid/widget/ListView;"
        } == true
    }
)

/**
 * Extreme Tube All Formats selector.
 *
 * The patch never synthesizes fake qualities. It exposes the actual video adaptive formats that
 * YouTube already returned and allows exact-itag selection while keeping all audio streams.
 */
@Suppress("unused")
val allFormatsMetadataPatch = bytecodePatch(
    name = "All Formats selector",
    description = "Shows each actual YouTube resolution/codec/FPS/bitrate variant separately and allows exact format selection.",
    default = false
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)
    extendWith("extensions/extension.mpe")

    execute {
        VideoStreamingDataConstructorFingerprint.let { fingerprint ->
            fingerprint.method.apply {
                val adaptiveFormatsIndex = fingerprint.instructionMatches.last().index
                val adaptiveFormatsRegister =
                    getInstruction<TwoRegisterInstruction>(adaptiveFormatsIndex).registerA

                // Replace the register with an identical mutable wrapper retained only in memory.
                // Later codec selections can narrow this exact list without touching URLs or audio.
                addInstructions(
                    adaptiveFormatsIndex + 1,
                    """
                        invoke-static { v$adaptiveFormatsRegister }, $DATA_EXTENSION_CLASS->captureAndWrap(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$adaptiveFormatsRegister
                    """.trimIndent()
                )
            }
        }

        VideoQualityMenuViewInflateFingerprint.let { fingerprint ->
            fingerprint.method.apply {
                val listViewIndex = fingerprint.instructionMatches.last().index
                val listViewRegister =
                    getInstruction<OneRegisterInstruction>(listViewIndex).registerA

                addInstruction(
                    listViewIndex + 1,
                    "invoke-static { v$listViewRegister }, $MENU_EXTENSION_CLASS->install(Landroid/widget/ListView;)V"
                )
            }
        }
    }
}
