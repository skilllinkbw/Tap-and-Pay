package com.getauthepay.app.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Currency support (directive section 40).
 *
 * The app launches in Botswana but must not hardcode BWP. These tests pin
 * the multi-currency contract so a future market can be enabled by adding
 * a code to the catalogue rather than editing screens.
 */
class CurrencyCatalogTest {

    @Test
    fun `BWP is the default launch currency`() {
        assertEquals("BWP", CurrencyCatalog.defaultCurrency)
    }

    @Test
    fun `required launch-market currencies are supported`() {
        listOf("BWP", "ZAR", "USD", "ZMW", "KES", "NGN").forEach {
            assertTrue("$it must be supported", CurrencyCatalog.isSupported(it))
        }
    }

    @Test
    fun `currency lookup is case insensitive`() {
        assertTrue(CurrencyCatalog.isSupported("bwp"))
        assertTrue(CurrencyCatalog.isSupported("Zar"))
    }

    @Test
    fun `unsupported currency is rejected`() {
        assertFalse(CurrencyCatalog.isSupported("XYZ"))
        assertFalse(CurrencyCatalog.isSupported(""))
    }

    @Test
    fun `every catalogued currency is a real ISO 4217 currency`() {
        val codes = CurrencyCatalog.allSupported()
        assertTrue("catalogue must not be empty", codes.isNotEmpty())
        codes.forEach {
            assertEquals(3, it.currencyCode.length)
        }
    }

    @Test
    fun `catalogue has no duplicates`() {
        val codes = CurrencyCatalog.allSupported().map { it.currencyCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `display name falls back to the code for unknown input`() {
        assertEquals("XYZ", CurrencyCatalog.displayName("XYZ"))
    }

    @Test
    fun `symbol never throws for unknown input`() {
        assertEquals("XYZ", CurrencyCatalog.symbol("XYZ"))
    }

    @Test
    fun `supported currencies cover SADC and major corridors`() {
        val codes = CurrencyCatalog.allSupported().map { it.currencyCode }.toSet()
        // SADC / Africa
        assertTrue(codes.contains("BWP"))
        assertTrue(codes.contains("ZAR"))
        assertTrue(codes.contains("ZMW"))
        assertTrue(codes.contains("NAD") || codes.contains("MUR") || codes.contains("TZS"))
        // International card corridors
        assertTrue(codes.contains("USD"))
        assertTrue(codes.contains("EUR") || codes.contains("GBP"))
    }
}
