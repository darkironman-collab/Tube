package app.extremetube.patches.branding

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Runs as a second-pass resource patch after Morphe has fully generated its settings XML.
 * The required offline GPL/NOTICE attribution remains bundled separately.
 */
@Suppress("unused")
val extremeSettingsCleanupPatch = resourcePatch(
    name = "Extreme settings cleanup",
    description = "Shows Extreme/Dark Ironman branding, removes the Morphe M root icon, and replaces the network About preference with a static local row.",
    default = true
) {
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

        // Remove the Morphe M icon beside the outer Extreme settings entry.
        document("res/drawable/morphe_settings_icon_dynamic.xml").use { document ->
            document.documentElement.setAttribute(
                "class",
                "app.extremetube.extension.TransparentDrawable"
            )
        }

        // Reuse Morphe's existing About icon resource id, but draw sunglasses instead.
        // This avoids adding any new resource IDs to the already-patched APK.
        document("res/drawable/morphe_settings_screen_00_about.xml").use { document ->
            val root = document.documentElement
            while (root.hasChildNodes()) root.removeChild(root.firstChild)
            val path = document.createElement("path")
            path.setAttribute("android:fillColor", "?android:attr/textColorPrimary")
            path.setAttribute(
                "android:pathData",
                "M2,9L4,17C4.3,18.2 5.3,19 6.6,19H8.4C9.8,19 10.9,18.1 11.2,16.8L11.8,14H12.2L12.8,16.8C13.1,18.1 14.2,19 15.6,19H17.4C18.7,19 19.7,18.2 20,17L22,9H2ZM5,11H10L9,16C8.9,16.6 8.4,17 7.8,17H6.8C6.2,17 5.7,16.6 5.6,16L5,11ZM14,11H19L18.4,16C18.3,16.6 17.8,17 17.2,17H16.2C15.6,17 15.1,16.6 15,16L14,11ZM10.5,12H13.5V14H10.5V12Z"
            )
            root.appendChild(path)
        }

        // Morphe originally serializes this row as MorpheAboutPreference, which performs
        // network About/social/update requests. Replace it with a plain non-interactive
        // Preference while keeping the same key/title/icon resource IDs.
        document("res/xml/morphe_prefs.xml").use { document ->
            val nodes = document.getElementsByTagName("*")
            var about: Element? = null
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.getAttribute("android:key") == "morphe_settings_screen_00_about") {
                    about = element
                    break
                }
            }

            about?.let { old ->
                val replacement = document.createElement("Preference")
                replacement.setAttribute("android:key", "morphe_settings_screen_00_about")
                replacement.setAttribute("android:title", "@string/morphe_settings_screen_00_about_title")
                replacement.setAttribute("android:icon", "@drawable/morphe_settings_screen_00_about")
                replacement.setAttribute("app:iconSpaceReserved", "true")
                replacement.setAttribute("android:selectable", "false")
                old.parentNode.replaceChild(replacement, old)
            }
        }
    }
}
