package org.fossify.gallery

import org.fossify.commons.FossifyApp

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = false

    override fun onCreate() {
        super.onCreate()
    }
}
