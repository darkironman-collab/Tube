package app.extremetube.patches.branding

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Keeps the first-version YouTube settings layout while presenting the injected surface as Extreme.
 * Morphe's required offline GPL/NOTICE attribution remains bundled separately.
 */
@Suppress("unused")
val extremeSettingsCleanupPatch = resourcePatch(
    name = "Extreme settings cleanup",
    description = "Renames Morphe-facing settings UI to Extreme/Dark Ironman and removes the Morphe M root icon.",
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
                    "morphe_settings_screen_00_about_title" -> element.textContent = "Dark Ironman"
                }
            }
        }

        // The stock Morphe settings entry uses a runtime Drawable that renders the M icon.
        // Point that drawable resource at our 1px transparent implementation so only "Extreme"
        // remains visible in the YouTube settings list.
        document("res/drawable/morphe_settings_icon_dynamic.xml").use { document ->
            val root = document.documentElement
            root.setAttribute("class", "app.extremetube.extension.TransparentDrawable")
        }
    }
}
