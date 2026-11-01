package com.pomodoro.update

import android.os.Looper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class AppUpdaterTest {
    @Before
    fun initializeProviderForThisApplication() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.resolveContentProvider("${context.packageName}.updates", PackageManager.GET_META_DATA)!!
        Robolectric.buildContentProvider(UpdateFileProvider::class.java).create(info)
    }

    @Test
    @Suppress("DEPRECATION")
    fun downloadIsNotDuplicatedAndPermissionReturnStillRequiresExplicitInstall() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.packageManager
        val packageShadow = shadowOf(manager)
        fun signing() = SigningInfo().also { Shadow.extract<ShadowSigningInfo>(it).setSignatures(arrayOf(Signature("aa11"))) }
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA).apply {
            versionCode = 3
            versionName = "1.1.1-alpha"
            signingInfo = signing()
        }
        packageShadow.installPackage(installed)
        val archive = PackageInfo().apply {
            packageName = context.packageName
            versionCode = 4
            versionName = "1.1.2-alpha"
            signingInfo = signing()
        }
        val bytes = byteArrayOf(1, 2, 3)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        var downloads = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.host == "api.github.com") {
                """[{"tag_name":"v1.1.2-alpha","assets":[{"name":"app.apk","size":3,"browser_download_url":"https://github.com/Fhuu/StillPomodoro/releases/download/v1.1.2-alpha/app.apk","digest":"sha256:$digest"}]}]""".toResponseBody()
            } else {
                downloads++
                File(context.cacheDir, "updates").listFiles().orEmpty().filter { it.extension == "apk" }.forEach {
                    packageShadow.setPackageArchiveInfo(it.absolutePath, archive)
                }
                bytes.toResponseBody()
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val updater = AppUpdater(context, GitHubReleaseClient(http), Executor { it.run() })
        updater.check()
        shadowOf(Looper.getMainLooper()).idle()
        val release = (updater.state as AppUpdater.State.Available).release
        updater.download(release)
        updater.download(release)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, downloads)
        assertTrue(updater.state is AppUpdater.State.Ready)
        packageShadow.setCanRequestPackageInstalls(false)
        updater.install()
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, shadowOf(context).nextStartedActivity.action)
        packageShadow.setCanRequestPackageInstalls(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(null, shadowOf(context).nextStartedActivity)
        updater.install()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Installer state: ${updater.state}", updater.state is AppUpdater.State.Ready)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content", intent.data!!.scheme)
        assertEquals("application/vnd.android.package-archive", intent.type)
    }

    @Test
    fun repeatedCheckWhileBusyMakesOnlyOneRequestAndOffersNewerAlpha() {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""[{"tag_name":"v9.0.0-alpha","assets":[{"name":"app.apk","size":3,"browser_download_url":"https://github.com/Fhuu/StillPomodoro/releases/download/v9.0.0-alpha/app.apk"}]}]""".toResponseBody()).build()
        }.build()
        val updater = AppUpdater(RuntimeEnvironment.getApplication(), GitHubReleaseClient(http), Executor { it.run() })
        updater.check()
        updater.check()
        assertEquals(1, requests)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("v9.0.0-alpha", (updater.state as AppUpdater.State.Available).release.tag)
    }

    @Test
    fun networkFailureReturnsRetryableErrorWithoutOpeningInstaller() {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                .body("".toResponseBody()).build()
        }.build()
        val updater = AppUpdater(RuntimeEnvironment.getApplication(), GitHubReleaseClient(http), Executor { it.run() })
        updater.check()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue((updater.state as AppUpdater.State.Error).message.contains("limiting"))
        assertEquals(null, shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }
}
