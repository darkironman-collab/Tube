package app.extremetube.patches.branding

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Keeps the first Extreme Tube / YouTube UI, but removes the online Morphe About entry
 * and presents the injected settings surface as Extreme.
 *
 * The required Morphe GPL NOTICE remains bundled offline. This patch only removes the
 * clickable About preference that can fetch Morphe website/social/update information.
 */
@Suppress("unused")
val extremeSettingsCleanupPatch = resourcePatch(
    name = "Extreme settings cleanup",
    description = "Renames the Morphe settings entry to Extreme and removes the network-backed Morphe About/social-links entry while preserving required offline license notices.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    finalize {
        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            for (index in 0 until strings.length) {
                val element = strings.item(index) as? Element ?: continue
                when (element.getAttribute("name")) {
                    "morphe_settings_title",
                    "morphe_settings_submenu_title" -> element.textContent = "Extreme"
                }
            }
        }

        document("res/xml/morphe_prefs.xml").use { document ->
            val nodes = document.getElementsByTagName("*")
            val toRemove = mutableListOf<Node>()

            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.getAttribute("android:key") == "morphe_settings_screen_00_about") {
                    toRemove += element
                }
            }

            toRemove.forEach { node ->
                node.parentNode?.removeChild(node)
            }
        }
    }
}
