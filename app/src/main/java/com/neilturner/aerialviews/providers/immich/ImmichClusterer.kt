package com.neilturner.aerialviews.providers.immich

import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ImmichClusterer {
    private val parsers =
        listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        )

    data class Result(
        val representatives: List<Asset>,
        /**
         * Per-cluster alternate members, keyed by representative asset id.
         * Callers can use this to present variety across playlist loops —
         * pick a random alternate each time the slot is rendered instead of
         * freezing the choice at fetch time.
         */
        val alternatesByPrimaryId: Map<String, List<Asset>> = emptyMap(),
    )

    fun cluster(
        assets: List<Asset>,
        gapMinutes: Int,
        exemptPattern: Regex? = null,
        exemptKeepFraction: Double = 1.0,
    ): Result {
        if (assets.isEmpty() || gapMinutes <= 0) return Result(assets)

        val exemptAll = mutableListOf<Asset>()
        val dated = mutableListOf<Pair<Asset, Long>>()
        val undated = mutableListOf<Asset>()

        for (asset in assets) {
            if (exemptPattern != null && exemptPattern.containsMatchIn(asset.albumName.orEmpty())) {
                exemptAll += asset
                continue
            }
            val epoch = asset.localDateTime?.let(::parseEpochSeconds)
            if (epoch != null) dated += asset to epoch else undated += asset
        }

        val exemptKept =
            when {
                exemptKeepFraction >= 1.0 -> exemptAll
                exemptKeepFraction <= 0.0 -> emptyList()
                else -> {
                    val n = (exemptAll.size * exemptKeepFraction).toInt()
                    if (n >= exemptAll.size) exemptAll else exemptAll.shuffled().take(n)
                }
            }

        dated.sortBy { it.second }

        val gapSeconds = gapMinutes.toLong() * 60
        val representatives = mutableListOf<Asset>()
        val alternates = mutableMapOf<String, List<Asset>>()
        var clusterStart = 0
        for (i in 1..dated.size) {
            val breakHere = i == dated.size || (dated[i].second - dated[i - 1].second) > gapSeconds
            if (breakHere) {
                val clusterAssets = dated.subList(clusterStart, i).map { it.first }
                val rep = clusterAssets.random()
                representatives += rep
                if (clusterAssets.size > 1) {
                    alternates[rep.id] = clusterAssets.filter { it.id != rep.id }
                }
                clusterStart = i
            }
        }

        representatives += undated
        representatives += exemptKept

        Timber.i(
            "ImmichClusterer: %d in → %d clusters (with %d alternates-carrying) + %d undated + %d/%d exempt (gap=%d min, keep=%.2f)",
            assets.size,
            representatives.size - undated.size - exemptKept.size,
            alternates.size,
            undated.size,
            exemptKept.size,
            exemptAll.size,
            gapMinutes,
            exemptKeepFraction,
        )

        return Result(representatives, alternates)
    }

    private fun parseEpochSeconds(raw: String): Long? {
        val normalized = raw.trim().replace(Regex("(?i)(?:z|[+-]\\d{2}:?\\d{2})$"), "")
        for (parser in parsers) {
            try {
                return LocalDateTime.parse(normalized, parser).toEpochSecond(java.time.ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
            }
        }
        Timber.w("ImmichClusterer: failed to parse localDateTime '%s'", raw)
        return null
    }
}
