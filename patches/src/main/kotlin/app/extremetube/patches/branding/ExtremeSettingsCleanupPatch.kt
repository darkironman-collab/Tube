package app.extremetube.patches.branding

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val MORPHE_ABOUT_KEY = "morphe_settings_screen_00_about"
private const val ROOT_TITLE = "@string/morphe_settings_title"
private const val SUBMENU_TITLE = "@string/morphe_settings_submenu_title"
private const val POWER_SETTING_TITLE = "YouTube Power Setting"

private val USER_VISIBLE_STRING_REPLACEMENTS = mapOf(
    "morphe_language_title" to "App language",
    "morphe_pref_import_export_summary" to "Import / Export app settings",
    "morphe_show_menu_icons_title" to "Show settings icons"
)

/**
 * Keeps the first Extreme Tube / YouTube UI while removing Morphe branding from the
 * user-facing settings surface. Required offline GPL notices/attribution remain bundled.
 */
@Suppress("unused")
val extremeSettingsCleanupPatch = resourcePatch(
    name = "Extreme settings cleanup",
    description = "Shows a plain YouTube Power Setting entry, removes the Morphe heading/icon and remaining Morphe labels from the settings UI, and hides the network-backed Morphe About entry while preserving required offline license notices.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    finalize {
        // Rename only the exact user-facing resource keys requested for the stock-looking UI.
        // Do not blanket-replace the word Morphe so required license/attribution text stays intact.
        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")
            for (index in 0 until strings.length) {
                val element = strings.item(index) as? Element ?: continue
                val name = element.getAttribute("name")

                when (name) {
                    "morphe_settings_title",
                    "morphe_settings_submenu_title" -> element.textContent = POWER_SETTING_TITLE
                    else -> USER_VISIBLE_STRING_REPLACEMENTS[name]?.let { replacement ->
                        element.textContent = replacement
                    }
                }
            }
        }

        fun stripMorpheIcon(element: Element) {
            element.removeAttribute("android:icon")
            element.removeAttribute("app:iconSpaceReserved")
            element.removeAttribute("android:layout")
        }

        // Cairo layout: move the clickable Power Setting row out of Morphe's titled
        // category, remove the category, and strip the M icon.
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

        // Legacy layout: remove Morphe-specific icon/layout chrome from the entry.
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
