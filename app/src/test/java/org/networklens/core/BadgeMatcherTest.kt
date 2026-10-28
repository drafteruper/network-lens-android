package org.networklens.core

import org.junit.Assert.*
import org.junit.Test

class BadgeMatcherTest {
    private val alex = Profile(1, "Alex Example", "Example Ventures", "Partner", "investor")
    @Test fun fullNameAndCompanyHaveExplicitEvidence() {
        val m = BadgeMatcher.match("Alex Example\nExample Ventures", listOf(alex)).single()
        assertEquals(95, m.score); assertTrue(m.strong); assertEquals(3, m.evidence.size)
    }
    @Test fun nameAloneStaysPossible() {
        val m = BadgeMatcher.match("Alex Example", listOf(alex)).single()
        assertEquals(70, m.score); assertFalse(m.strong)
    }
    @Test fun duplicateNamesWithoutCompanyAreAmbiguous() {
        val other = alex.copy(id = 2, company = "Other Company")
        val results = BadgeMatcher.match("Alex Example", listOf(alex, other))
        assertTrue(results.all { it.ambiguous && !it.strong })
    }
    @Test fun companyDisambiguatesSameName() {
        val results = BadgeMatcher.match("Alex Example Example Ventures", listOf(alex, alex.copy(id = 2, company = "Other")))
        assertTrue(results.first().strong); assertEquals(1L, results.first().profile.id)
    }
    @Test fun multipleNamesInBlockRemainAmbiguous() {
        val second = Profile(2, "Sam Sample", "Demo Labs", "Founder", "startup")
        assertTrue(BadgeMatcher.match("Alex Example Example Ventures Sam Sample Demo Labs", listOf(alex, second)).all { it.ambiguous })
    }
    @Test fun partialAndSimilarNamesDoNotMatch() {
        listOf("Alex", "Alex Examples", "Alex Examp1e", "Alex Other").forEach { assertTrue(BadgeMatcher.match(it, listOf(alex)).isEmpty()) }
    }
    @Test fun accentsAndPunctuationNormalize() {
        assertEquals("jose example", BadgeMatcher.normalize(" José — Example "))
    }
    @Test fun unknownAndEmptyRemainUnknown() {
        assertTrue(BadgeMatcher.match("", listOf(alex)).isEmpty())
        assertTrue(BadgeMatcher.match("Nobody Here", listOf(alex)).isEmpty())
        assertTrue(BadgeMatcher.match("Alex Example", emptyList()).isEmpty())
    }
    @Test fun singleNamesRequireManualSelection() {
        assertTrue(BadgeMatcher.match("Alex Example Ventures", listOf(alex.copy(name = "Alex"))).isEmpty())
    }
}
