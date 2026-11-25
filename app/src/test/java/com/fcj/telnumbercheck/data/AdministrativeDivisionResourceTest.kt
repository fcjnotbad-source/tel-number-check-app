package com.fcj.telnumbercheck.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdministrativeDivisionResourceTest {
    private fun data() = checkNotNull(javaClass.getResourceAsStream("/geo/province-cities.tsv"))
        .use { it.readBytes() }

    @Test
    fun `the packaged resource matches the pinned import`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(data())
            .joinToString("") { "%02x".format(it) }
        assertEquals("e0e6f43832f21c3bd13703926da933e6e514169ad38182eee81143a651d1ccbf", digest)
        assertNotNull(javaClass.getResource("/geo/LICENSE-uiw-province-city-china.txt"))
    }

    @Test
    fun `every imported full name pair resolves to its exact source identity`() {
        val rows = data().toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank)
            .map { it.split('\t') }.toList()
        assertEquals(370, rows.size)
        assertEquals(31, rows.map { it[0] }.toSet().size)
        assertEquals(370, rows.map { "${it[0]}:${it[2]}" }.toSet().size)
        rows.forEach { fields ->
            assertEquals(4, fields.size)
            assertTrue(fields[2].startsWith(fields[0].take(2)))
            assertFalse(fields[3] in setOf("市辖区", "县") || fields[3].contains("直辖县级行政区划"))
            val place = AdministrativeDivisionIndex.resolveExact(fields[1] + fields[3])
            assertNotNull(fields.toString(), place)
            assertEquals(fields.toString(), "${fields[0]}:${fields[2]}", place!!.key)
        }
    }
}
