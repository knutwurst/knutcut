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
}
