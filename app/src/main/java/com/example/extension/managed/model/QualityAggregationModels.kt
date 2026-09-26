package com.example.extension.managed.model

import com.example.utils.M3U8Parser

/**
 * Quality evidence classification enforcing strict evidence-based quality representation.
 */
enum class QualityEvidence {
    RESOLUTION,
    EXPLICIT_LABEL,
    OTHER_VERIFIED
}

/**
 * Internal representation of a candidate stream quality discovered from a specific server.
 * Preserves all source provenance (serverId, serverName, sourceUrl, headers, evidence)
 * to support robust multi-server fallback without exposing duplicates to the user.
 */
data class QualityCandidate(
    val qualityKey: String,
    val label: String,
    val width: Int? = null,
    val height: Int? = null,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val serverId: String? = null,
    val serverName: String? = null,
    val sourceUrl: String? = null,
    val evidence: QualityEvidence = QualityEvidence.RESOLUTION
)

/**
 * Aggregated representation of a canonical quality level.
 * Contains the chosen primary candidate for playback and all alternate fallback candidates
 * from other discovered servers offering the same resolution.
 */
data class AggregatedQuality(
    val qualityKey: String,
    val label: String,
    val primaryCandidate: QualityCandidate,
    val fallbackCandidates: List<QualityCandidate> = emptyList(),
    val width: Int? = primaryCandidate.width,
    val height: Int? = primaryCandidate.height
) {
    val allCandidates: List<QualityCandidate>
        get() = listOf(primaryCandidate) + fallbackCandidates
}

/**
 * Central Quality Aggregator.
 * Responsible for deduplicating, grouping, and ordering discovered stream qualities
 * across all participating servers into a canonical descending hierarchy.
 */
object QualityAggregator {

    private val STANDARD_ORDER = listOf(
        "4320", "2160", "1440", "1080", "720", "576", "480", "360", "240", "144"
    )

    fun aggregate(
        candidates: List<QualityCandidate>
    ): List<AggregatedQuality> {
        val valid = candidates.filter { it.qualityKey != "Auto" && it.qualityKey != "Unknown" }
        val grouped = valid.groupBy { it.qualityKey }

        val aggregatedList = mutableListOf<AggregatedQuality>()
        for ((key, list) in grouped) {
            val primary = list.first()
            val fallbacks = list.drop(1)
            aggregatedList.add(
                AggregatedQuality(
                    qualityKey = key,
                    label = primary.label,
                    primaryCandidate = primary,
                    fallbackCandidates = fallbacks,
                    width = primary.width,
                    height = primary.height
                )
            )
        }

        // Sort descending: 4320p -> 2160p -> 1440p -> 1080p -> 720p -> 480p -> 360p ...
        return aggregatedList.sortedWith(Comparator { a, b ->
            val idxA = STANDARD_ORDER.indexOf(a.qualityKey).let { if (it == -1) 999 else it }
            val idxB = STANDARD_ORDER.indexOf(b.qualityKey).let { if (it == -1) 999 else it }
            idxA.compareTo(idxB)
        })
    }

    fun toQualityInfoList(
        aggregated: List<AggregatedQuality>,
        defaultStreamUrl: String? = null,
        defaultHeaders: Map<String, String> = emptyMap()
    ): List<M3U8Parser.QualityInfo> {
        val result = mutableListOf<M3U8Parser.QualityInfo>()
        for (item in aggregated) {
            result.add(
                M3U8Parser.QualityInfo(
                    name = item.label,
                    url = item.primaryCandidate.streamUrl,
                    width = item.width,
                    height = item.height,
                    headers = item.primaryCandidate.headers,
                    serverId = item.primaryCandidate.serverId,
                    serverName = item.primaryCandidate.serverName
                )
            )
        }

        // Auto is appended at the end if explicit qualities exist, or as sole entry if none
        val autoUrl = defaultStreamUrl ?: aggregated.firstOrNull()?.primaryCandidate?.streamUrl.orEmpty()
        result.add(
            M3U8Parser.QualityInfo(
                name = "Auto",
                url = autoUrl,
                headers = defaultHeaders
            )
        )
        return result
    }
}

fun normalizeQualityKey(rawName: String): String? {
    val trimmed = rawName.trim()
    val lower = trimmed.lowercase()

    // 1. Explicitly reject arbitrary/non-evidence descriptors
    if (lower in listOf("high", "best", "medium", "hd1", "quality-1", "quality-2", "auto", "unknown") ||
        lower.startsWith("server") ||
        lower.contains("quality-") ||
        lower.matches(Regex("""hd\d+""")) ||
        lower == "custom" ||
        lower.endsWith("-custom")
    ) {
        return null
    }

    // 2. Resolution attribute matching (e.g. RESOLUTION=1920x1080)
    val resMatch = Regex("""(?i)resolution\s*=\s*\d+x(\d+)""").find(rawName)
    if (resMatch != null) {
        val h = resMatch.groupValues[1].toIntOrNull()
        if (h != null) {
            return when {
                h >= 2160 -> "2160"
                h in 1440..2159 -> "1440"
                h in 1080..1439 -> "1080"
                h in 720..1079 -> "720"
                h in 576..719 -> "576"
                h in 480..575 -> "480"
                h in 360..479 -> "360"
                h in 240..359 -> "240"
                else -> "144"
            }
        }
    }

    // 3. Exact standard resolution numeric and evidence tokens
    return when {
        lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> "2160"
        lower.contains("1440") || lower.contains("2k") -> "1440"
        lower.contains("1080") || lower.contains("fhd") || lower.contains("1920") -> "1080"
        lower.contains("720") || lower.contains("1280") -> "720"
        lower.contains("576") -> "576"
        lower.contains("480") || lower.contains("854") || Regex("""\bsd\b""").containsMatchIn(lower) -> "480"
        lower.contains("360") || lower.contains("640") -> "360"
        lower.contains("240") || lower.contains("426") -> "240"
        lower.contains("144") || lower.contains("256") -> "144"
        Regex("""\bhd\b""").containsMatchIn(lower) && !lower.contains("server") -> "720"
        else -> null
    }
}

fun normalizeQualityLabel(rawName: String): String? {
    val key = normalizeQualityKey(rawName) ?: return null
    return "${key}p"
}

