package net.kravos.vesti

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import net.kravos.vesti.data.Http
import net.kravos.vesti.data.Prefs
import net.kravos.vesti.data.Repo
import net.kravos.vesti.data.Saved

class App : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var saved: Saved
        private set
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        prefs = Prefs(this)
        saved = Saved(this)
        repo = Repo(this, prefs, saved)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Http.client() }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
            .respectCacheHeaders(false)
            .crossfade(150)
            .build()
}
