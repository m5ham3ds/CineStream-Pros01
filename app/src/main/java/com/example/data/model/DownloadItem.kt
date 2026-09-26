package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey val id: String = "",
    val mediaId: String = "",
    val title: String = "",
    val posterUrl: String = "",
    val isMovie: Boolean = true,
    val quality: String = "",
    val progress: Float = 0f,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val fileSizeBytes: Long = 0L
) {
    val cleanQuality: String
        get() = cleanQualityName(quality)

    companion object {
        fun cleanQualityName(rawQuality: String?): String {
            if (rawQuality.isNullOrBlank()) return "HD"
            val name = if (rawQuality.contains("||")) {
                rawQuality.substringBefore("||").trim()
            } else {
                rawQuality.trim()
            }
            return if (name.startsWith("http://") || name.startsWith("https://") || name.contains("/") || name.contains("://") || name.length > 15) {
                "HD"
            } else if (name.isNotBlank()) {
                name
            } else {
                "HD"
            }
        }
    }
}