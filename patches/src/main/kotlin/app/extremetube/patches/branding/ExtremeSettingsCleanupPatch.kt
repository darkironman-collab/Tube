package app.extremetube.patches.branding

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Second-pass cleanup after Morphe has generated its settings resources.
 * Visible Morphe branding is removed from the settings entry and the network-backed
 * Morphe About/details page is removed entirely. Required offline GPL/NOTICE files remain bundled.
 */
@Suppress("unused")
val extremeSettingsCleanupPatch = resourcePatch(
    name = "Extreme settings cleanup",
    description = "Renames the injected settings entry to YouTube Extra Setting and removes the Morphe About/details page.",
    default = false
) {
    finalize {
        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            for (index in 0 until strings.length) {
                val element = strings.item(index) as? Element ?: continue
                when (element.getAttribute("name")) {
                    "morphe_settings_title",
                    "morphe_settings_submenu_title" -> element.textContent = "YouTube Extra Setting"
                }
            }
        }

        // Remove the Morphe M icon beside the injected settings entry.
        document("res/drawable/morphe_settings_icon_dynamic.xml").use { document ->
            document.documentElement.setAttribute(
                "class",
                "app.extremetube.extension.TransparentDrawable"
            )
        }

        // Remove Morphe's About/details preference completely. This prevents the Morphe
        // logo/version/update/Donate/Website/GitHub/Reddit/Translations/Credits page from
        // being reachable from the visible settings UI.
        document("res/xml/morphe_prefs.xml").use { document ->
            val nodes = document.getElementsByTagName("*")
            val toRemove = mutableListOf<Node>()
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.getAttribute("android:key") == "morphe_settings_screen_00_about") {
                    toRemove += element
                }
            }
            toRemove.forEach { node -> node.parentNode?.removeChild(node) }
        }
    }
}
