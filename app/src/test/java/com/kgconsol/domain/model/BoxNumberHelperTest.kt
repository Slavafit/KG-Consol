package com.kgconsol.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxNumberHelperTest {

    @Test
    fun `automatic number has four digits and is not already used`() {
        val existing = setOf("1000", "1111", "6376", "9999")

        repeat(100) {
            val generated = BoxNumberHelper.nextAutoNumber(existing)
            assertTrue(generated.matches(Regex("""\d{4}""")))
            assertFalse(generated in existing)
        }
    }

    @Test
    fun `manual number requires exactly four digits`() {
        assertTrue(BoxNumberHelper.isValidManual("6376"))
        assertFalse(BoxNumberHelper.isValidManual("11111"))
        assertFalse(BoxNumberHelper.isValidManual("123"))
        assertFalse(BoxNumberHelper.isValidManual("12A4"))
    }

    @Test
    fun `fallback returns the only free four digit number`() {
        val existing = (1000..9998).map(Int::toString).toSet()

        assertEquals("9999", BoxNumberHelper.nextAutoNumber(existing))
    }
}
