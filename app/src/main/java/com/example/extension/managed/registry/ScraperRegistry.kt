package com.example.extension.managed.registry

import com.example.extension.managed.contract.BaseSiteScraper
import com.example.extension.managed.scraper.EgyDeadScraper
import com.example.extension.managed.scraper.QfilmScraper

/**
 * Static In-App Registry mapping remote scraper keys to trusted bundled implementations.
 * Strictly avoids reflection, dynamic class loaders, or runtime package scanning.
 */
class ScraperRegistry(
    private val staticScrapers: Map<String, BaseSiteScraper> = defaultRegistry()
) {
    companion object {
        private fun defaultRegistry(): Map<String, BaseSiteScraper> {
            return mapOf(
                "egydead" to EgyDeadScraper(),
                "qfilm" to QfilmScraper()
            )
        }

        val INSTANCE: ScraperRegistry by lazy { ScraperRegistry() }
    }

    fun getScraper(scraperKey: String): BaseSiteScraper? {
        return staticScrapers[scraperKey.lowercase().trim()]
    }

    fun hasScraper(scraperKey: String): Boolean {
        return staticScrapers.containsKey(scraperKey.lowercase().trim())
    }

    fun allScrapers(): List<BaseSiteScraper> {
        return staticScrapers.values.toList()
    }
}
