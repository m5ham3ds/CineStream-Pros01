package com.example.extension.managed

import com.example.extension.managed.registry.ScraperRegistry
import com.example.extension.managed.scraper.EgyDeadScraper
import com.example.extension.managed.scraper.QfilmScraper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperRegistryTest {

    private val registry = ScraperRegistry.INSTANCE

    @Test
    fun knownScraperKey_resolvesStatically() {
        val scraper = registry.getScraper("egydead")
        assertNotNull("egydead key must resolve to an in-app scraper", scraper)
        assertTrue("Scraper must be an instance of EgyDeadScraper", scraper is EgyDeadScraper)
        assertEquals("egydead", scraper?.scraperKey)
        assertEquals(1, scraper?.implementationVersion)

        val qfilmScraper = registry.getScraper("qfilm")
        assertNotNull("qfilm key must resolve to an in-app scraper", qfilmScraper)
        assertTrue("Scraper must be an instance of QfilmScraper", qfilmScraper is QfilmScraper)
        assertEquals("qfilm", qfilmScraper?.scraperKey)
        assertEquals(1, qfilmScraper?.implementationVersion)
    }

    @Test
    fun caseInsensitiveLookup_succeeds() {
        val scraper = registry.getScraper("EgyDead")
        assertNotNull(scraper)
        assertEquals("egydead", scraper?.scraperKey)

        val qfilm = registry.getScraper("QFilm")
        assertNotNull(qfilm)
        assertEquals("qfilm", qfilm?.scraperKey)
    }

    @Test
    fun unknownScraperKey_returnsNull() {
        val scraper = registry.getScraper("non_existent_site")
        assertNull(scraper)
        assertFalse(registry.hasScraper("non_existent_site"))
    }

    @Test
    fun allScrapers_containsOnlyBundledScrapers() {
        val all = registry.allScrapers()
        assertEquals(2, all.size)
        val keys = all.map { it.scraperKey }.toSet()
        assertTrue(keys.contains("egydead"))
        assertTrue(keys.contains("qfilm"))
    }
}
