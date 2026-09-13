package com.filigram.cinema

data class MovieItem(
    val id: Int,
    val title: String,
    val image: String,
    val type: Int, // 0 = movie, 1 = series
    val year: String? = null,
    val hasSub: Boolean = false,
    val hasDub: Boolean = false,
    val slug: String? = null
)

data class VitrinSection(
    val id: Int,
    val title: String,
    val items: List<MovieItem>
)

data class SeasonItem(
    val season: Int,
    val title: String
)

data class EpisodeItem(
    val episode: Int,
    val title: String,
    val qualities: List<QualityItem> = emptyList()
)

data class QualityItem(
    val id: Int,
    val type: String,
    val title: String,
    val size: String,
    val directUrl: String? = null
)

data class MovieDetail(
    val id: Int,
    val title: String,
    val image: String,
    val banner: String,
    val type: Int, // 0 = movie, 1 = series
    val imdbRate: String?,
    val duration: String?,
    val year: String?,
    val description: String?,
    val descriptionAi: String?,
    val seasons: List<SeasonItem> = emptyList(),
    val directQualities: List<QualityItem> = emptyList(),
    val slug: String? = null
)
