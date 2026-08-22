package app.extremetube.patches.privacy

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val MORPHE_ABOUT_KEY = "morphe_settings_screen_00_about"

@Suppress("unused")
val hideMorpheAboutPatch = resourcePatch(
    name = "Hide Morphe About",
    description = "Removes only the Morphe About entry from the Morphe settings screen without changing playback, quality, package identity, permissions or other settings.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    finalize {
        listOf(
            "res/xml/morphe_prefs.xml",
            "res/xml/morphe_prefs_icons.xml",
            "res/xml/morphe_prefs_icons_bold.xml"
        ).forEach { path ->
            if (!get(path).exists()) return@forEach

            document(path).use { document ->
                val nodes = document.getElementsByTagName("*")
                val matches = mutableListOf<Node>()

                for (index in 0 until nodes.length) {
                    val node = nodes.item(index)
                    if (node is Element && node.getAttribute("android:key") == MORPHE_ABOUT_KEY) {
                        matches += node
                    }
                }

                matches.forEach { node ->
                    node.parentNode?.removeChild(node)
                }
            }
        }
    }
}
