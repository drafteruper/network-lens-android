package org.networklens.core

import java.text.Normalizer
import java.util.Locale

data class Profile(val id: Long, val name: String, val company: String, val role: String, val category: String)
data class Match(val profile: Profile, val score: Int, val evidence: List<String>, val ambiguous: Boolean) {
    val strong: Boolean get() = score >= 95 && !ambiguous
}

/** Scores are explicit evidence weights, NOT probabilities of identity. Never uses faces. */
object BadgeMatcher {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun match(text: String, profiles: List<Profile>): List<Match> {
        return BadgeIndex(profiles).match(text)
    }
}

/** Normalize the event directory once, rather than for every camera frame. */
class BadgeIndex(profiles: List<Profile>) {
    private data class Entry(val profile: Profile, val name: String, val company: String)
    private val entries = profiles.map { Entry(it, BadgeMatcher.normalize(it.name), BadgeMatcher.normalize(it.company)) }
    fun match(text: String): List<Match> {
        fun normalize(value: String) = BadgeMatcher.normalize(value)
        val badge = " ${normalize(text)} "
        val candidates = entries.mapNotNull { entry ->
            val p = entry.profile
            val name = entry.name
            // Single names and fuzzy fragments cannot establish a candidate automatically.
            if (name.split(" ").size < 2 || !badge.contains(" $name ")) return@mapNotNull null
            val company = entry.company
            val companyMatch = company.isNotBlank() && badge.contains(" $company ")
            Match(p, if (companyMatch) 95 else 70,
                listOf("Full name found in this text block", "On selected event's imported list") +
                    if (companyMatch) listOf("Company found in the same text block") else emptyList(), false)
        }.sortedByDescending { it.score }
        // Multiple names in one region are not assigned to a person. Equal top scores stay ambiguous.
        val multiNames = candidates.map { normalize(it.profile.name) }.distinct().size > 1
        val tied = candidates.size > 1 && candidates[0].score == candidates[1].score
        return candidates.map { it.copy(ambiguous = multiNames || tied) }
    }
}

/** Vendor-independent frame result contract for future external cameras/displays. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class BadgeObservation(val bounds: Bounds, val text: String, val candidates: List<Match>)
data class FrameObservations(val timestampMs: Long, val badges: List<BadgeObservation>, val anonymousFaces: List<Bounds>)
fun interface ObservationSink { fun onFrame(frame: FrameObservations) }
