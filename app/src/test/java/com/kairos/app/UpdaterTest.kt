package com.kairos.app

import com.kairos.app.system.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    private val base = "https://github.com/Kw4nzah/Kairos/releases/download/"

    @Test fun onlyPlainVersionsBecomeFileNames() {
        assertTrue(Updater.isSafeVersion("1.4.3"))
        for (bad in listOf("", "../../x", "1.4/3", "1.4.3-evil", "1..4", "99999999999")) {
            assertFalse(bad, Updater.isSafeVersion(bad))
        }
    }

    @Test fun onlyThisRepositorysReleasesAreDownloaded() {
        assertTrue(Updater.isReleaseUrl(base + "v1.4.3/Kairos-1.4.3.apk"))
        for (bad in listOf(
            "http://github.com/Kw4nzah/Kairos/releases/download/v1/a.apk",
            "https://github.com.evil.example/Kw4nzah/Kairos/releases/download/v1/a.apk",
            "https://github.com/someone/else/releases/download/v1/a.apk",
            base + "../../../../someone/else/a.apk",
            base + "%2e%2e/a.apk"
        )) assertFalse(bad, Updater.isReleaseUrl(bad))
    }

    @Test fun versionsCompareByNumberNotText() {
        assertTrue(Updater.isNewer("1.10.0", "1.9.9"))
        assertFalse(Updater.isNewer("1.4.3", "1.4.3"))
        assertFalse(Updater.isNewer("1.4.2", "1.4.3"))
    }
}
