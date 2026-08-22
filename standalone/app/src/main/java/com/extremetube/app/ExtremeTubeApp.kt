package com.extremetube.app

import android.app.Application
import com.extremetube.app.network.SafeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class ExtremeTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(
            SafeDownloader(),
            Localization("en", "IN"),
            ContentCountry("IN")
        )
    }
}
