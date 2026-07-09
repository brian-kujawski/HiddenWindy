package com.hiddenwindy

import android.app.Application
import org.maplibre.android.MapLibre

/**
 * Initializes the MapLibre SDK once for the whole process before any [org.maplibre.android.maps.MapView]
 * is created.
 */
class HiddenWindyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
