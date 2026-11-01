package com.pomodoro.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

internal object ApkUpdateInstaller {
    @Suppress("DEPRECATION")
    fun validate(context: Context, file: File, release: ReleaseApk) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = manager.getPackageInfo(context.packageName, flags)
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("The downloaded file isn't a valid APK.")
        validatePackages(installed, archive, release)
    }

    @Suppress("DEPRECATION")
    fun validatePackages(installed: PackageInfo, archive: PackageInfo, release: ReleaseApk) {
        if (archive.packageName != installed.packageName) throw IOException("This APK is for a different app.")
        val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        val updateCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (updateCode <= currentCode) throw IOException("The release APK isn't newer than this installation.")
        if (ReleaseVersion.parse(archive.versionName.orEmpty()) != release.version) {
            throw IOException("The APK version doesn't match the GitHub release.")
        }
        val currentKeys = currentSigners(installed)
        val updateKeys = currentSigners(archive)
        if (currentKeys.isEmpty() || currentKeys != updateKeys) throw IOException("This update has a different signing key.")
    }

    @Suppress("DEPRECATION")
    private fun currentSigners(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
