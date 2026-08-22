package app.extremetube.patches.quality

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val ADVANCED_EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/playback/quality/AdvancedVideoQualityMenuPatch;"

/**
 * Morphe's first-pass patch already routes YouTube's Advanced-item creation through
 * forceAdvancedVideoQualityMenuCreation(boolean). In an upgraded installation the old
 * shared preference can still be false, hiding Advanced even though the new default is true.
 *
 * This second-pass patch makes that one helper unconditionally return true. It does not
 * fabricate video formats; it only guarantees that YouTube creates the Advanced entry.
 */
private object ForceAdvancedMenuCreationFingerprint : Fingerprint(
    name = "forceAdvancedVideoQualityMenuCreation",
    returnType = "Z",
    parameters = listOf("Z"),
    custom = { _, classDef -> classDef.type == ADVANCED_EXTENSION_CLASS }
)

@Suppress("unused")
val forceAdvancedQualityEntryPatch = bytecodePatch(
    name = "Force advanced quality entry",
    description = "Always exposes the Advanced video quality item so Ytube presets and actual formats can be opened.",
    default = false
) {
    execute {
        ForceAdvancedMenuCreationFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x1
                return p0
            """.trimIndent()
        )
    }
}
