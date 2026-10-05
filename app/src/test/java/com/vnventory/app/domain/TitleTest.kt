package com.vnventory.app.domain

import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.remote.vndb.VndbReleaseDto
import com.vnventory.app.data.remote.vndb.VndbTitleDto
import com.vnventory.app.data.remote.vndb.VndbVnDto
import com.vnventory.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TitleTest {
    @Test fun `VN retains both titles including explicit latin field`() {
        val vn = VndbVnDto("v1", "Site title", titles = listOf(
            VndbTitleDto("サクラノ詩", "ja", main = true, official = true, latin = "Sakura no Uta"),
        )).toDomain()
        assertEquals("サクラノ詩", vn.originalTitle)
        assertEquals("Sakura no Uta", vn.romanizedTitle)
        assertEquals("サクラノ詩", vn.displayTitle(TitleDisplayMode.ORIGINAL))
        assertEquals("Sakura no Uta", vn.displayTitle(TitleDisplayMode.ROMANIZED))
        assertEquals("サクラノ詩", vn.secondaryTitle(TitleDisplayMode.ROMANIZED))
    }

    @Test fun `ASCII original and identical titles are not discarded`() {
        val vn = VndbVnDto("v1", "AIR", titles = listOf(VndbTitleDto(" AIR ", "ja", main = true))).toDomain()
        assertEquals("AIR", vn.originalTitle)
        assertEquals("AIR", vn.romanizedTitle)
        assertNull(vn.secondaryTitle)
    }

    @Test fun `Release original and romanized have independent semantics`() {
        val release = VndbReleaseDto("r1", "Limited Edition", " 初回限定版 ").toDomain("v1")
        assertEquals("初回限定版", release.originalTitle)
        assertEquals("Limited Edition", release.romanizedTitle)
        assertEquals("初回限定版", release.displayTitle(TitleDisplayMode.ORIGINAL))
        assertEquals("Limited Edition", release.displayTitle(TitleDisplayMode.ROMANIZED))
    }

    @Test fun `missing titles are null and never transliterated`() {
        val vn = VndbVnDto("v1", " ", titles = listOf(VndbTitleDto("日本語", "ja", main = true))).toDomain()
        assertEquals("日本語", vn.originalTitle)
        assertNull(vn.romanizedTitle)
        assertEquals("日本語", vn.displayTitle(TitleDisplayMode.ROMANIZED))
        val release = VndbReleaseDto("r1", " ", " ").toDomain("v1")
        assertNull(release.originalTitle)
        assertNull(release.romanizedTitle)
        assertEquals("r1", release.displayTitle(TitleDisplayMode.ORIGINAL))
    }

    @Test fun `unified fallback trims ignores blanks and retains legacy`() {
        for (mode in TitleDisplayMode.entries) {
            assertEquals("Legacy", displayTitle(mode, " ", null, " Legacy ", "v1"))
            assertEquals("Original", displayTitle(mode, " Original ", null, "Legacy", "v1"))
            assertEquals("Romanized", displayTitle(mode, null, " Romanized ", "Legacy", "v1"))
            assertEquals("v1", displayTitle(mode, null, " ", "", " v1 "))
            assertEquals("—", displayTitle(mode, null, null, null, ""))
        }
    }
}
