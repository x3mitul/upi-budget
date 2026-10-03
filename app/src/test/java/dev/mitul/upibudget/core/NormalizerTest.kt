package dev.mitul.upibudget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizerTest {
    @Test fun upperCasesCollapsesSpacesAndStripsPunctuation() {
        assertEquals("SUNIL SURNAME", Normalizer.name("Sunil  Surname"))
        assertEquals("DOMINO S PIZZA", Normalizer.name("Domino's Pizza"))
        assertEquals("H M", Normalizer.name("H&M"))
        assertEquals("SWIGGY LTD", Normalizer.name("  swiggy.ltd "))
    }

    @Test fun keepsNonAsciiLetters() {
        assertEquals("रमेश किराना", Normalizer.name("रमेश  किराना"))
    }

    @Test fun emptyInputGivesEmpty() {
        assertEquals("", Normalizer.name("  ...  "))
    }

    @Test fun wholeWordMatchOnly() {
        assertTrue(Normalizer.hasWords("OLA CABS", "OLA"))
        assertFalse(Normalizer.hasWords("COCA COLA", "OLA"))
        assertTrue(Normalizer.hasWords("SWIGGY INSTAMART", "SWIGGY INSTAMART"))
        assertTrue(Normalizer.hasWords("THE SWIGGY INSTAMART STORE", "SWIGGY INSTAMART"))
        assertFalse(Normalizer.hasWords("SWIGGYX", "SWIGGY"))
    }
}
