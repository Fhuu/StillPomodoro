package com.pomodoro.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GitHubReleaseClientTest {
    private fun release(tag: String, draft: Boolean = false, assets: String = asset(tag)) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":true,"assets":[$assets]}"""

    private fun asset(tag: String, url: String = "https://github.com/Fhuu/StillPomodoro/releases/download/$tag/app.apk") =
        """{"name":"app.apk","size":3,"browser_download_url":"$url"}"""

    @Test
    fun checkReadsAShortGithubResponseAndIncludesPrereleases() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(GitHubReleaseClient.RELEASES_URL, chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("[${release("v1.1.2-alpha")}]".toResponseBody()).build()
        }.build()
        assertEquals("v1.1.2-alpha", GitHubReleaseClient(client).check("1.1.1-alpha")!!.tag)
    }

    @Test
    fun findsNewestPublishedAlphaApkAndIgnoresDraftsAndUnavailableAssets() {
        val json = "[${release("v1.1.2-alpha")},${release("v1.2.0-alpha", draft = true)},${release("v2.0.0", assets = "")},${release("v1.1.1-alpha")}]"
        val found = GitHubReleaseClient().select(json, "1.1.1-alpha")
        assertNotNull(found)
        assertEquals("v1.1.2-alpha", found!!.tag)
        assertNull(GitHubReleaseClient().select(json, "1.1.2-alpha"))
    }

    @Test
    fun rejectsForeignDownloadsAndAmbiguousApkChoices() {
        val foreign = asset("v1.1.2-alpha", "https://example.com/app.apk")
        assertNull(GitHubReleaseClient().select("[${release("v1.1.2-alpha", assets = foreign)}]", "1.1.1-alpha"))
        val ambiguous = asset("v1.1.2-alpha") + "," + asset("v1.1.2-alpha")
        assertNull(GitHubReleaseClient().select("[${release("v1.1.2-alpha", assets = ambiguous)}]", "1.1.1-alpha"))
    }

    @Test
    fun downloadsExactBytesAndRejectsWrongSizeOrDigest() {
        val bytes = byteArrayOf(1, 2, 3)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(bytes.toResponseBody()).build()
        }.build()
        val api = GitHubReleaseClient(client)
        val found = api.select("[${release("v1.1.2-alpha")}]", "1.1.1-alpha")!!
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val file = api.download(found.copy(sha256 = digest), directory) { }
            assertEquals(bytes.toList(), file.readBytes().toList())
            assertThrows(IOException::class.java) { api.download(found.copy(sha256 = "0".repeat(64)), directory) { } }
            assertThrows(IOException::class.java) { api.download(found.copy(size = 4), directory) { } }
            assertFalse(File(directory, "download.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun laterDownloadCannotReplaceAnApkAlreadyHandedToTheInstaller() {
        var body = byteArrayOf(1, 2, 3)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(body.toResponseBody()).build()
        }.build()
        val api = GitHubReleaseClient(client)
        val found = api.select("[${release("v1.1.2-alpha")}]", "1.1.1-alpha")!!
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val first = api.download(found, directory) { }
            body = byteArrayOf(4, 5, 6)
            val second = api.download(found, directory) { }
            assertTrue(first != second)
            assertEquals(listOf<Byte>(1, 2, 3), first.readBytes().toList())
            assertEquals(listOf<Byte>(4, 5, 6), second.readBytes().toList())
            val third = api.download(found, directory) { }
            assertTrue("Even identical downloads need independent installer files", third != second)
            assertEquals(listOf<Byte>(4, 5, 6), second.readBytes().toList())
        } finally {
            directory.deleteRecursively()
        }
    }
}
