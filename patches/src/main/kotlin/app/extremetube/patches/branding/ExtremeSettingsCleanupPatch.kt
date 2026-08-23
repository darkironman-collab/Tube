package app.extremetube.patches.branding

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val MORPHE_ABOUT_KEY = "morphe_settings_screen_00_about"
private const val ROOT_TITLE = "@string/morphe_settings_title"
private const val SUBMENU_TITLE = "@string/morphe_settings_submenu_title"
private const val POWER_SETTING_TITLE = "YouTube Power Setting"

/**
 * Keeps the first Extreme Tube / YouTube UI, removes the online Morphe About entry,
 * and presents the injected settings entry as a stock-looking YouTube Power Setting row.
 *
 * The required Morphe GPL NOTICE remains bundled offline. This patch only changes the
 * visible settings entry: the separate section heading and Morphe icon are removed.
 */
@Suppress("unused")
val extremeSettingsCleanupPatch = resourcePatch(
    name = "Extreme settings cleanup",
    description = "Shows a plain YouTube Power Setting entry, hides the Morphe section heading/icon, and removes the network-backed Morphe About entry while preserving required offline license notices.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    finalize {
        // Use the requested label for both old and Cairo settings layouts.
        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            for (index in 0 until strings.length) {
                val element = strings.item(index) as? Element ?: continue
                when (element.getAttribute("name")) {
                    "morphe_settings_title",
                    "morphe_settings_submenu_title" -> element.textContent = POWER_SETTING_TITLE
                }
            }
        }

        fun stripMorpheIcon(element: Element) {
            element.removeAttribute("android:icon")
            element.removeAttribute("app:iconSpaceReserved")
            element.removeAttribute("android:layout")
        }

        // Cairo layout: Morphe normally creates a titled PreferenceCategory containing
        // the clickable row. Move that row out of the category, then remove the category.
        // Result: no separate "Extreme" section heading and no Morphe M icon.
        val cairoPath = "res/xml/settings_fragment_cairo.xml"
        if (get(cairoPath).exists()) {
            document(cairoPath).use { document ->
                val nodes = document.getElementsByTagName("*")
                var powerEntry: Element? = null

                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("android:title") == SUBMENU_TITLE) {
                        powerEntry = element
                        break
                    }
                }

                powerEntry?.let { entry ->
                    stripMorpheIcon(entry)
                    val category = entry.parentNode as? Element
                    if (category != null && category.getAttribute("android:title") == ROOT_TITLE) {
                        val parent = category.parentNode
                        parent?.insertBefore(entry, category)
                        parent?.removeChild(category)
                    }
                }
            }
        }

        // Legacy layout: there is only one root preference, so just remove any custom
        // Morphe icon/layout chrome and keep the new YouTube Power Setting label.
        val legacyPath = "res/xml/settings_fragment.xml"
        if (get(legacyPath).exists()) {
            document(legacyPath).use { document ->
                val nodes = document.getElementsByTagName("*")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("android:title") == ROOT_TITLE) {
                        stripMorpheIcon(element)
                    }
                }
            }
        }

        // Keep Morphe About hidden in every generated Morphe preference variant.
        listOf(
            "res/xml/morphe_prefs.xml",
            "res/xml/morphe_prefs_icons.xml",
            "res/xml/morphe_prefs_icons_bold.xml"
        ).forEach { path ->
            if (!get(path).exists()) return@forEach

            document(path).use { document ->
                val nodes = document.getElementsByTagName("*")
                val toRemove = mutableListOf<Node>()

                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("android:key") == MORPHE_ABOUT_KEY) {
                        toRemove += element
                    }
                }

                toRemove.forEach { node ->
                    node.parentNode?.removeChild(node)
                }
            }
        }
    }
}
