package com.example.extension.managed.model

import com.example.utils.M3U8Parser

/**
 * Standard utility mapping M3U8Parser output to normalized MediaVariant instances.
 * Guarantees zero artificial resolution defaults ("Auto" or exact parsed height only).
 */
object MediaVariantParser {

    fun fromQualityInfo(
        info: M3U8Parser.QualityInfo,
        protocol: StreamProtocol = StreamProtocol.HLS,
        headers: Map<String, String> = emptyMap()
    ): MediaVariant {
        val height = info.name.replace(Regex("[^0-9]"), "").toIntOrNull()
        return MediaVariant(
            url = info.url,
            height = height,
            label = info.name,
            mimeType = if (protocol == StreamProtocol.HLS) "application/x-mpegURL" else "video/mp4",
            protocol = protocol,
            headers = headers,
            isDefault = info.name.equals("Auto", ignoreCase = true)
        )
    }

    fun fromQualityInfoList(
        infos: List<M3U8Parser.QualityInfo>,
        protocol: StreamProtocol = StreamProtocol.HLS,
        headers: Map<String, String> = emptyMap()
    ): List<MediaVariant> {
        return infos.map { fromQualityInfo(it, protocol, headers) }
    }
}
