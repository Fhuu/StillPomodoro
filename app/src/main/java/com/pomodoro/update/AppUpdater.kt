package com.pomodoro.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Process-local work/state survives Settings dismissal and rotation; only user actions start work. */
internal class AppUpdater(
    context: Context,
    private val client: GitHubReleaseClient = GitHubReleaseClient(),
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object NoUpdate : State
        data class Available(val release: ReleaseApk) : State
        data class Downloading(val release: ReleaseApk, val percent: Int) : State
        data class Ready(val release: ReleaseApk, val file: File) : State
        data object OpeningInstaller : State
        data class Error(val message: String) : State
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    var state by mutableStateOf<State>(State.Idle)
        private set

    val installedVersion: String get() = ApkUpdateInstaller.installedVersion(appContext)

    fun check() {
        if (isBusy()) return
        state = State.Checking
        executor.execute {
            try {
                val release = client.check(installedVersion)
                main.post { state = if (release == null) State.NoUpdate else State.Available(release) }
            } catch (error: Exception) {
                fail(error, "Couldn't check for updates. Check your connection and try again.")
            }
        }
    }

    fun download(release: ReleaseApk) {
        if ((state as? State.Available)?.release != release) return
        state = State.Downloading(release, 0)
        executor.execute {
            var file: File? = null
            try {
                file = client.download(release, File(appContext.cacheDir, "updates")) { percent ->
                    main.post { state = State.Downloading(release, percent) }
                }
                ApkUpdateInstaller.validate(appContext, file, release)
                val ready = State.Ready(release, file)
                main.post { state = ready }
            } catch (error: Exception) {
                file?.delete()
                fail(error, "Couldn't download the update. Check your connection and try again.")
            }
        }
    }

    fun install() {
        val ready = state as? State.Ready ?: return
        if (!ApkUpdateInstaller.canInstall(appContext)) {
            try {
                appContext.startActivity(ApkUpdateInstaller.permissionIntent(appContext))
            } catch (error: Exception) {
                state = State.Error("Couldn't open Android's app-update permission settings.")
            }
            return
        }
        state = State.OpeningInstaller
        executor.execute {
            try {
                ApkUpdateInstaller.validate(appContext, ready.file, ready.release)
                val intent = ApkUpdateInstaller.installIntent(appContext, ready.file)
                main.post {
                    try {
                        appContext.startActivity(intent)
                        state = ready
                    } catch (error: Exception) {
                        state = State.Error("Couldn't open Android's APK installer.")
                    }
                }
            } catch (error: Exception) {
                fail(error, "The downloaded update is no longer available. Download it again.")
            }
        }
    }

    private fun isBusy() = state is State.Checking || state is State.Downloading || state is State.OpeningInstaller

    private fun fail(error: Exception, fallback: String) {
        val message = if (error is IOException) error.message ?: fallback else fallback
        main.post { state = State.Error(message) }
    }

    companion object {
        @Volatile private var instance: AppUpdater? = null

        fun get(context: Context): AppUpdater = instance ?: synchronized(this) {
            instance ?: AppUpdater(context.applicationContext).also { instance = it }
        }
    }
}
