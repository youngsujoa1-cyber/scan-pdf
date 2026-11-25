package com.youngsu.scanpdf

import android.app.Application
import com.youngsu.scanpdf.ads.AdManager

class ScanPdfApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
    }
}
