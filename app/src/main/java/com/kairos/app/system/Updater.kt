package com.kairos.app.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer release found on GitHub. [sha256] is the asset digest GitHub publishes; installing requires it. */
data class UpdateInfo(val version: String, val url: String, val sha256: String, val size: Long)

/**
 * In-app updater. Talks only to the project's public GitHub releases; nothing about the user or the
 * device is sent. Android still shows its own install confirmation.
 */
object Updater {
    private const val API = "https://api.github.com/repos/Kw4nzah/Kairos/releases/latest"
    private const val DOWNLOADS = "https://github.com/Kw4nzah/Kairos/releases/download/"
    private const val MAX_APK_BYTES = 50L * 1024 * 1024
    private const val MAX_JSON_BYTES = 1024 * 1024
    private val VERSION = Regex("[0-9]{1,4}(\\.[0-9]{1,4}){0,3}")
    private val SHA256 = Regex("sha256:[0-9a-fA-F]{64}")

    /** null = already up to date. Throws on network or parse errors. */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(API)
        val json = try { JSONObject(conn.inputStream.use { readCapped(it) }) } finally { conn.disconnect() }
        val version = json.getString("tag_name").removePrefix("v")
        if (!isSafeVersion(version)) error("unexpected version")
        if (!isNewer(version, currentVersion)) return@withContext null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                val digest = a.optString("digest")
                if (!SHA256.matches(digest)) error("release has no SHA-256 digest")
                val url = a.getString("browser_download_url")
                if (!isReleaseUrl(url)) error("unexpected download address")
                return@withContext UpdateInfo(version, url, digest.removePrefix("sha256:"), a.optLong("size"))
            }
        }
        null
    }

    /** Downloads the APK into the app cache, reporting 0..1 progress, and verifies its digest, package and signer. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "Kairos-${info.version}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        val c = open(info.url)
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            if (total > MAX_APK_BYTES) error("download too large")
            c.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (done + n > MAX_APK_BYTES) error("download too large")
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { c.disconnect() }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(info.sha256, ignoreCase = true)) {
            out.delete()
            error("checksum mismatch")
        }
        // The digest comes from the same place as the file. This is what holds if that place is taken over:
        // without it an APK with another package name would be offered as "the Kairos update".
        if (!isOurUpdate(ctx, out, info.version)) {
            out.delete()
            error("not a Kairos update")
        }
        out
    }

    // GET_SIGNATURES reads archives on every API from 26; the lint warning is about Android 4.4 and older.
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun isOurUpdate(ctx: Context, apk: File, version: String): Boolean {
        val flags = PackageManager.GET_SIGNATURES
        val pm = ctx.packageManager
        val next = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        val mine = pm.getPackageInfo(ctx.packageName, flags)
        fun signers(p: PackageInfo) = p.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val ours = signers(mine)
        return next.packageName == mine.packageName &&
            next.versionName == version &&
            PackageInfoCompat.getLongVersionCode(next) > PackageInfoCompat.getLongVersionCode(mine) &&
            ours.isNotEmpty() && signers(next) == ours
    }

    private fun readCapped(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > MAX_JSON_BYTES) error("response too large")
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    /** The tag becomes a file name, so nothing but digits and dots. */
    internal fun isSafeVersion(v: String): Boolean = VERSION.matches(v)

    internal fun isReleaseUrl(url: String): Boolean =
        url.startsWith(DOWNLOADS) && ".." !in url && '%' !in url

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Opens the screen where the user allows Kairos to install apps. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Kairos-Android")
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c
    }

    internal fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
