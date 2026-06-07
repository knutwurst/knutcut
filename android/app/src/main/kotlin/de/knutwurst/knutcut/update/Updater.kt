package de.knutwurst.knutcut.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The latest release as published in the self-update repo's latest.json. [sha256] is the lowercase
 *  hex digest of the APK; the download is rejected unless it matches, guarding against tampering. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apk: String, val sha256: String, val notes: String = "")

/**
 * Self-update from the knutcut-releases GitHub repo: read latest.json, compare versionCode, download
 * the APK into the cache and hand it to the package installer (the user confirms the install — a
 * normal app can't install silently). On launch, [cleanup] removes any leftover Knutcut APKs.
 */
object Updater {
    // Raw file base; upload Knutcut-v<ver>-release.apk + bump latest.json here.
    private const val BASE = "https://raw.githubusercontent.com/knutwurst/knutcut-releases/main/"
    private const val DIR = "updates"

    /** Read latest.json, or null on any network/parse error. */
    fun fetchLatest(): UpdateInfo? {
        // Cache-buster: raw.githubusercontent.com sits behind a CDN that caches latest.json for
        // minutes, so a fixed URL can return a stale version. A unique query param forces a fresh fetch.
        val txt = httpGet(BASE + "latest.json?ts=" + System.currentTimeMillis()) ?: return null
        return parseLatest(txt)
    }

    /** Parse a latest.json body into [UpdateInfo], or null if it's invalid or missing versionCode. */
    fun parseLatest(json: String): UpdateInfo? = try {
        val o = JSONObject(json)
        if (!o.has("versionCode")) null
        else UpdateInfo(o.getInt("versionCode"), o.optString("versionName"), o.optString("apk"), o.optString("sha256"), o.optString("notes"))
    } catch (e: Exception) { null }

    /** Download the update APK into the cache dir; returns the file or null on failure. */
    fun download(context: Context, info: UpdateInfo): File? { return try {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val out = File(dir, info.apk)
        (URL(BASE + info.apk).openConnection() as HttpURLConnection).run {
            connectTimeout = 10000; readTimeout = 30000
            if (responseCode != 200) return null
            inputStream.use { input -> out.outputStream().use { o -> input.copyTo(o) } }
        }
        // Integrity check: a tampered or truncated download (e.g. a MitM) must never be installed. A
        // missing/blank checksum is treated as a failure, so latest.json must always carry sha256.
        if (info.sha256.isBlank() || !sha256(out).equals(info.sha256.trim(), ignoreCase = true)) {
            out.delete(); return null
        }
        out
    } catch (e: Exception) { null } }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Launch the system package installer for [apk]; the user confirms. Returns false (and opens the
     * "install unknown apps" settings for this app) if the source isn't allowed yet, so the install
     * can't silently do nothing — the user grants it once and taps Update again.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    /** Delete leftover Knutcut update APKs (only ours), keeping [keep] (a pending installer). */
    fun cleanup(context: Context, keep: String? = null) {
        File(context.cacheDir, DIR).listFiles()?.forEach {
            if (it.isFile && it.name != keep && it.name.startsWith("Knutcut", ignoreCase = true) && it.name.endsWith(".apk", ignoreCase = true)) it.delete()
        }
    }

    private fun httpGet(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            if (responseCode == 200) inputStream.bufferedReader().use { it.readText() } else null
        }
    } catch (e: Exception) { null }
}
