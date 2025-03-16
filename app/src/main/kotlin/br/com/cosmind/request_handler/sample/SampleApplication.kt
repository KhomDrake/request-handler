package br.com.cosmind.request_handler.sample

import android.app.Application
import br.com.khomdrake.request_handler.RequestHandler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RequestHandler.init(this, cleanCacheTimeout = false)
        Timber.plant(Timber.DebugTree())
    }

}