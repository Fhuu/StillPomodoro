package com.pomodoro.update

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class ApkUpdateInstallerTest {
    @Before
    fun initializeProviderForThisApplication() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.resolveContentProvider("${context.packageName}.updates", PackageManager.GET_META_DATA)!!
        Robolectric.buildContentProvider(UpdateFileProvider::class.java).create(info)
    }

    private val release = ReleaseApk("v1.1.2-alpha", ReleaseVersion.parse("1.1.2-alpha")!!, "", 1, null)

    private fun info(code: Int, version: String = "1.1.2-alpha", key: String = "aa11", name: String = "com.pomodoro.timer") =
        PackageInfo().apply {
            packageName = name
            versionCode = code
            versionName = version
            signatures = arrayOf(Signature(key))
            signingInfo = SigningInfo().also { Shadow.extract<ShadowSigningInfo>(it).setSignatures(arrayOf(Signature(key))) }
        }

    @Test
    fun acceptsOnlyTheSameAppKeyAndANewerBuildMatchingTheRelease() {
        val current = info(3, "1.1.1-alpha")
        ApkUpdateInstaller.validatePackages(current, info(4), release)
        for (archive in listOf(
            info(3), info(2), info(4, key = "bb22"), info(4, name = "other.app"), info(4, version = "1.1.1-alpha"),
            info(4).apply { signatures = emptyArray(); signingInfo = null },
        )) {
            assertThrows(IOException::class.java) { ApkUpdateInstaller.validatePackages(current, archive, release) }
        }
    }

    @Test
    fun comparesCurrentSigningKeysInsteadOfTheSharedHistoricalKey() {
        val current = info(3, "1.1.1-alpha", key = "bb22").apply { signatures = arrayOf(Signature("aa11")) }
        val archive = info(4, key = "cc33").apply { signatures = arrayOf(Signature("aa11")) }
        assertThrows(IOException::class.java) { ApkUpdateInstaller.validatePackages(current, archive, release) }
    }

    @Test
    @Config(sdk = [27])
    fun supportsLegacySignatureChecksBeforeAndroidNine() {
        val installed = PackageInfo().apply {
            packageName = "com.pomodoro.timer"
            versionCode = 3
            versionName = "1.1.1-alpha"
            signatures = arrayOf(Signature("aa11"))
        }
        val archive = PackageInfo().apply {
            packageName = installed.packageName
            versionCode = 4
            versionName = "1.1.2-alpha"
            signatures = arrayOf(Signature("aa11"))
        }
        ApkUpdateInstaller.validatePackages(installed, archive, release)
        archive.signatures = arrayOf(Signature("bb22"))
        assertThrows(IOException::class.java) { ApkUpdateInstaller.validatePackages(installed, archive, release) }
    }

    @Test
    fun sharesOnlyUpdateCacheThroughAReadOnlyInstallerIntent() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "updates/update.apk").apply { parentFile!!.mkdirs(); writeText("apk") }
        val intent = ApkUpdateInstaller.installIntent(context, file)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("content", intent.data!!.scheme)
        assertEquals("${context.packageName}.updates", intent.data!!.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals("apk", context.contentResolver.openInputStream(intent.data!!)!!.bufferedReader().use { it.readText() })
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", File(context.cacheDir, "other.txt"))
        }
    }
}
