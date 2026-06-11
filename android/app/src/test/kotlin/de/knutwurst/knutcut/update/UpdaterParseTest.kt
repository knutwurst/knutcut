package de.knutwurst.knutcut.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parsing of the self-update manifest (latest.json). Robolectric because it uses org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdaterParseTest {

    @Test
    fun parsesAFullManifest() {
        val info = Updater.parseLatest(
            """{"versionCode":109,"versionName":"0.50.2","apk":"Knutcut-v0.50.2-release.apk","sha256":"abc123","notes":"Bugfixes"}"""
        )
        requireNotNull(info)
        assertEquals(109, info.versionCode)
        assertEquals("0.50.2", info.versionName)
        assertEquals("Knutcut-v0.50.2-release.apk", info.apk)
        assertEquals("abc123", info.sha256)
        assertEquals("Bugfixes", info.notes)
    }

    @Test
    fun defaultsOptionalFieldsWhenAbsent() {
        val info = Updater.parseLatest("""{"versionCode":5}""")
        requireNotNull(info)
        assertEquals(5, info.versionCode)
        assertEquals("", info.sha256)
        assertEquals("", info.notes)
        assertEquals("an absent apkUrl defaults to empty (raw fallback is used)", "", info.apkUrl)
    }

    @Test
    fun parsesApkUrlWhenPresent() {
        val info = Updater.parseLatest(
            """{"versionCode":127,"versionName":"0.55.0","apk":"Knutcut-v0.55.0-release.apk","sha256":"abc","apkUrl":"https://github.com/knutwurst/knutcut-releases/releases/download/v0.55.0/Knutcut-v0.55.0-release.apk"}"""
        )
        requireNotNull(info)
        assertEquals("https://github.com/knutwurst/knutcut-releases/releases/download/v0.55.0/Knutcut-v0.55.0-release.apk", info.apkUrl)
    }

    @Test
    fun rejectsManifestWithoutVersionCode() {
        assertNull(Updater.parseLatest("""{"versionName":"1.0","apk":"x.apk"}"""))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(Updater.parseLatest("not json at all"))
        assertNull(Updater.parseLatest(""))
    }

    @Test
    fun downloadFileNameFavoursApkThenUrlThenFallback() {
        fun info(apk: String, apkUrl: String = "") = UpdateInfo(1, "1.0", apk, "sha", apkUrl = apkUrl)
        assertEquals("Knutcut-v1.0.apk", Updater.downloadFileName(info("Knutcut-v1.0.apk")))
        // blank apk → last path component of the URL, query stripped
        assertEquals("Knutcut-v1.0.apk", Updater.downloadFileName(info("", "https://x/releases/Knutcut-v1.0.apk?ts=9")))
        // URL component without .apk → extension appended
        assertEquals("asset123.apk", Updater.downloadFileName(info("", "https://x/download/asset123")))
        // nothing usable → fixed fallback, never an empty/directory target
        assertEquals("update.apk", Updater.downloadFileName(info("", "")))
    }
}
