package app.extremetube.patches.branding

import app.extremetube.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val extremeTubeBrandingPatch = resourcePatch(
    name = "Extreme Tube branding",
    description = "Changes the Android application label to Extreme Tube without adding SDKs, trackers, network code or permissions.",
    default = true
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    finalize {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            application.setAttribute("android:label", "Extreme Tube")
        }
    }
}
