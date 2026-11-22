package com.kairos.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer release found on GitHub. [sha256] is the asset digest published by GitHub, when present. */
data class UpdateInfo(val version: String, val url: String, val sha256: String?, val size: Long)

/**
 * In-app updater. Talks only to the project's public GitHub releases; nothing about the user or the
 * device is sent. Android still shows its own install confirmation.
 */
object Updater {
    private const val API = "https://api.github.com/repos/Kw4nzah/Kairos/releases/latest"

    /** null = already up to date. Throws on network or parse errors. */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(API)
        val json = try { JSONObject(conn.inputStream.bufferedReader().readText()) } finally { conn.disconnect() }
        val version = json.getString("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion)) return@withContext null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                return@withContext UpdateInfo(
                    version, a.getString("browser_download_url"),
                    a.optString("digest").removePrefix("sha256:").ifBlank { null }, a.optLong("size")
                )
            }
        }
        null
    }

    /** Downloads the APK into the app cache, reporting 0..1 progress, and verifies the digest if known. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "Kairos-${info.version}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        val c = open(info.url)
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            c.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { c.disconnect() }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && !hex.equals(info.sha256, ignoreCase = true)) {
            out.delete()
            error("checksum mismatch")
        }
        out
    }

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
