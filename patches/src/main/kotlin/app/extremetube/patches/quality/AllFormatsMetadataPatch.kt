/*
 * Extreme Tube adaptation.
 * Fingerprint structure derived from MorpheApp/morphe-patches (GPLv3 + NOTICE).
 * https://github.com/MorpheApp/morphe-patches
 */
package app.extremetube.patches.quality

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.newInstance
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/extremetube/extension/AllFormatsData;"

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
 * Read-only foundation for the future Extreme Tube All Formats selector.
 *
 * This patch does not reorder, remove, replace or synthesize any YouTube stream. It only passes
 * the adaptive-format list already in memory to the Extreme Tube extension for metadata parsing.
 */
@Suppress("unused")
val allFormatsMetadataPatch = bytecodePatch(
    name = "All Formats metadata",
    description = "Captures actual adaptive-format codec, resolution, FPS and bitrate metadata without changing playback.",
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

                addInstruction(
                    adaptiveFormatsIndex + 1,
                    "invoke-static { v$adaptiveFormatsRegister }, $EXTENSION_CLASS->capture(Ljava/util/List;)V"
                )
            }
        }
    }
}
