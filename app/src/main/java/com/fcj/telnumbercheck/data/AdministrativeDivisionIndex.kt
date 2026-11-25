package com.fcj.telnumbercheck.data

internal data class AdministrativePlace(
    val provinceCode: String,
    val provinceName: String,
    val cityCode: String,
    val cityName: String,
) {
    val key: String = "$provinceCode:$cityCode"
    private val provinceShort = provinceName
        .removeSuffix("壮族自治区").removeSuffix("维吾尔自治区").removeSuffix("回族自治区")
        .removeSuffix("自治区").removeSuffix("特别行政区").removeSuffix("省").removeSuffix("市")
    // In 芒市, the 市 is part of its two-character name, not a suffix to discard.
    private val cityShort = if (cityName.length > 2) cityName.removeSuffix("市") else cityName
    private val selfRegion = provinceName == cityName && provinceName in setOf(
        "北京市", "天津市", "上海市", "重庆市", "香港特别行政区", "澳门特别行政区",
    )
    val displayName: String = if (selfRegion) provinceShort else "$provinceShort $cityShort"
    private val provincePattern = alternatives(provinceName, provinceShort)
    private val cityPattern = alternatives(cityName, cityShort)
    private val pair = "$provincePattern${AdministrativeDivisionIndex.SEPARATORS}$cityPattern"
    internal val pattern = Regex(if (selfRegion) "(?:$pair|$provincePattern)" else pair)

    private fun alternatives(vararg names: String): String = names.distinct()
        .sortedByDescending(String::length).joinToString("|", "(?:", ")", transform = Regex::escape)
}

internal data class AdministrativePlaceMatch(val place: AdministrativePlace, val endExclusive: Int)

/** Validates names and parentage in the bundled snapshot; it contains no telephone-number data. */
internal object AdministrativeDivisionIndex {
    internal const val SEPARATORS = "[\\p{P}\\p{Z}\\s]*"
    private val places: List<AdministrativePlace> by lazy {
        runCatching {
            AdministrativeDivisionIndex::class.java.getResourceAsStream("/geo/province-cities.tsv")
                ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                        val fields = line.split('\t')
                        require(fields.size == 4 && fields.all(String::isNotBlank))
                        require(fields[0].all(Char::isDigit) && fields[2].all(Char::isDigit))
                        AdministrativePlace(fields[0], fields[1], fields[2], fields[3])
                    }.distinctBy(AdministrativePlace::key).toList()
                }.orEmpty()
        }.getOrDefault(emptyList()) // Missing/malformed resources must never block call detection.
    }

    fun findPrefix(text: String): AdministrativePlaceMatch? = places.mapNotNull { place ->
        place.pattern.find(text)?.takeIf { it.range.first == 0 }?.let { match ->
            AdministrativePlaceMatch(place, match.range.last + 1)
        }
    }.maxByOrNull(AdministrativePlaceMatch::endExclusive)

    fun resolveExact(text: String): AdministrativePlace? {
        val value = text.trim()
        val match = findPrefix(value) ?: return null
        return match.place.takeIf { match.endExclusive == value.length }
    }
}
