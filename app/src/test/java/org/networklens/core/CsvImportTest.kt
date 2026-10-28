package org.networklens.core

import org.junit.Assert.*
import org.junit.Test

class CsvImportTest {
    @Test fun parsesBomQuotedCommasAndCrLf() {
        val result = CsvImport.parse("\uFEFFname,company,role,category,link\r\nAlex Example,\"Example, Inc.\",Founder,startup,https://example.org\r\n")
        assertEquals("Example, Inc.", result.single().company)
    }
    @Test fun multilineAndEscapedQuotes() {
        val result = CsvImport.parse("name,company\n\"Alex\nExample\",\"The \"\"Example\"\" Co\"")
        assertEquals("The \"Example\" Co", result.single().company)
    }
    @Test fun minimalHeadersDefaultToUnknown() {
        assertEquals("other", CsvImport.parse("name\nAlex Example").single().category)
    }
    @Test fun exactDuplicatesRemoved() { assertEquals(1, CsvImport.parse("name\nAlex Example\nAlex Example").size) }
    @Test(expected = IllegalArgumentException::class) fun rejectsMalformedQuote() { CsvImport.parse("name\n\"Alex") }
    @Test(expected = IllegalArgumentException::class) fun rejectsColumnMismatch() { CsvImport.parse("name,company\nAlex Example") }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedCategory() { CsvImport.parse("name,category\nAlex Example,wealthy") }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsafeLink() { CsvImport.parse("name,link\nAlex Example,javascript:alert(1)") }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateHeaders() { CsvImport.parse("name,name\nAlex,Example") }
    @Test(expected = IllegalArgumentException::class) fun requiresName() { CsvImport.parse("company\nExample") }
    @Test(expected = IllegalArgumentException::class) fun limitsInputSize() { CsvImport.parse("a".repeat(2_000_001)) }
}
