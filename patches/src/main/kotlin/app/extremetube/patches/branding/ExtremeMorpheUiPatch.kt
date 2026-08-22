package app.extremetube.patches.branding

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

private const val EXTREME_ABOUT = "Lapp/extremetube/extension/ExtremeAboutHtml;"

private object AboutDialogHtmlFingerprint : Fingerprint(
    name = "createDialogHtml",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/util/List;", "Ljava/lang/String;"),
    filters = listOf(string("Morphe"))
)

private object FetchAboutLinksFingerprint : Fingerprint(
    name = "fetchAboutLinks",
    returnType = "Ljava/util/List;",
    filters = listOf(string("Fetching social links from:"))
)

private object LatestPatchesVersionFingerprint : Fingerprint(
    name = "getLatestPatchesVersion",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("Fetching latest patches version links from:"))
)

private object HasFetchedLinksFingerprint : Fingerprint(
    name = "hasFetchedLinks",
    returnType = "Z"
)

private object HasFetchedPatchersVersionFingerprint : Fingerprint(
    name = "hasFetchedPatchersVersion",
    returnType = "Z"
)

/**
 * Replaces the network-backed Morphe About experience with an Extreme-owned local page.
 * The original GPL/NOTICE files remain bundled; this only removes Morphe's online About UI calls.
 */
@Suppress("unused")
val extremeMorpheUiPatch = bytecodePatch(
    name = "Extreme Morphe UI cleanup",
    description = "Uses a local Dark Ironman About page and disables Morphe About/update network fetches.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)
    extendWith("extensions/extension.mpe")

    execute {
        AboutDialogHtmlFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTREME_ABOUT->build()Ljava/lang/String;
                move-result-object v0
                return-object v0
            """.trimIndent()
        )

        FetchAboutLinksFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
                move-result-object v0
                return-object v0
            """.trimIndent()
        )

        LatestPatchesVersionFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """.trimIndent()
        )

        HasFetchedLinksFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )

        HasFetchedPatchersVersionFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )
    }
}
