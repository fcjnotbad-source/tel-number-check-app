package com.fcj.telnumbercheck.data

import com.fcj.telnumbercheck.domain.BaiduSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the province/city relationship using the packaged dictionary, not fabricated city names. */
class AdministrativeDivisionPresentationTest {
    private val number = "10000000007"

    @Test
    fun `resolves actual cities under their own provinces with distinct identities`() {
        val shenzhen = AdministrativeDivisionIndex.resolveExact("广东深圳")
        val hangzhou = AdministrativeDivisionIndex.resolveExact("浙江杭州")

        assertNotNull(shenzhen)
        assertNotNull(hangzhou)
        assertNotEquals(shenzhen!!.key, hangzhou!!.key)
        assertTrue(shenzhen.displayName, compact(shenzhen.displayName).contains("深圳"))
        assertTrue(hangzhou.displayName, compact(hangzhou.displayName).contains("杭州"))
    }

    @Test
    fun `safe suffix spacing and punctuation variants share the same place identity`() {
        val expected = AdministrativeDivisionIndex.resolveExact("广东深圳")!!.key

        listOf("广东省深圳市", "广东 深圳", "广东省 深圳市", "广东，深圳", "广东、深圳市").forEach { text ->
            val actual = AdministrativeDivisionIndex.resolveExact(text)
            assertNotNull(text, actual)
            assertEquals(text, expected, actual!!.key)
        }
    }

    @Test
    fun `prefix match ends at the verified city without consuming caller facts`() {
        val text = "广东省 深圳市 中国联通 广告推销"
        val match = AdministrativeDivisionIndex.findPrefix(text)

        assertNotNull(match)
        assertEquals(AdministrativeDivisionIndex.resolveExact("广东深圳")!!.key, match!!.place.key)
        assertEquals("中国联通 广告推销", text.substring(match.endExclusive).trimStart())
        assertNotNull(AdministrativeDivisionIndex.resolveExact(text.substring(0, match.endExclusive)))
    }

    @Test
    fun `rejects real cities attached to the wrong province`() {
        listOf("广东杭州", "广东省杭州市", "浙江 深圳", "四川 武汉").forEach { text ->
            assertNull(text, AdministrativeDivisionIndex.resolveExact(text))
            assertNull(text, AdministrativeDivisionIndex.findPrefix("$text 中国移动"))
        }
    }

    @Test
    fun `rejects unknown invented cities instead of treating arbitrary Chinese words as geography`() {
        listOf("广东瞎编", "浙江不存在市", "云南任意城市").forEach { text ->
            assertNull(text, AdministrativeDivisionIndex.resolveExact(text))
            assertNull(text, AdministrativeDivisionIndex.findPrefix("$text 中国联通"))
        }
    }

    @Test
    fun `exact resolution never consumes a merchant name or an extended city like substring`() {
        listOf("广东深圳美团", "广东 深圳 网络收录数据", "广东 深圳湾", "广东深圳海风科技有限公司").forEach {
            assertNull(it, AdministrativeDivisionIndex.resolveExact(it))
        }
    }

    @Test
    fun `a missing province or a province alone is insufficient`() {
        listOf("深圳", "杭州市", "广东", "广东省").forEach {
            assertNull(it, AdministrativeDivisionIndex.resolveExact(it))
        }
        assertNull(AdministrativeDivisionIndex.findPrefix("深圳 中国联通"))
        assertNull(AdministrativeDivisionIndex.findPrefix("广东 中国联通"))
    }

    @Test
    fun `municipalities accept explicit repeated city names and full administrative names`() {
        listOf("北京", "上海", "天津", "重庆").forEach { city ->
            val short = AdministrativeDivisionIndex.resolveExact("$city$city")
            val full = AdministrativeDivisionIndex.resolveExact("${city}市 ${city}市")

            assertNotNull(city, short)
            assertNotNull(city, full)
            assertEquals(city, short!!.key, full!!.key)
            assertEquals(city, short.key, AdministrativeDivisionIndex.resolveExact(city)!!.key)
            assertEquals(city, short.key, AdministrativeDivisionIndex.resolveExact("${city}市")!!.key)
            assertEquals(city, city, short.displayName)
        }
    }

    @Test
    fun `administrative placeholders are not actual municipality city names`() {
        listOf("北京 市辖区", "上海市 市辖区", "天津 县", "重庆市 市辖区").forEach { text ->
            assertNull(text, AdministrativeDivisionIndex.resolveExact(text))
            // A prefix may be a valid municipality; the complete statistical placeholder is not.
            assertTrue(text, summarize("$text 中国移动").contains(text))
        }
    }

    @Test
    fun `autonomous prefectures keep their full name and reject ambiguous truncated aliases`() {
        val place = AdministrativeDivisionIndex.resolveExact("云南省大理白族自治州")

        assertNotNull(place)
        assertTrue(place!!.displayName, place.displayName.contains("大理白族自治州"))
        assertEquals(place.key, AdministrativeDivisionIndex.resolveExact("云南 大理白族自治州")!!.key)
        assertNull(AdministrativeDivisionIndex.resolveExact("云南大理"))
        assertNull(AdministrativeDivisionIndex.findPrefix("云南大理中国移动"))
    }

    @Test
    fun `the final shi in Mangshi is never removed to invent Mang`() {
        assertNull(AdministrativeDivisionIndex.resolveExact("云南芒"))
        assertNull(AdministrativeDivisionIndex.findPrefix("云南芒 中国移动"))
        // A prefecture-only dictionary may omit this county-level city. If present, preserve its name.
        AdministrativeDivisionIndex.resolveExact("云南芒市")?.let { place ->
            assertTrue(place.displayName, place.displayName.contains("芒市"))
            assertFalse(place.displayName, place.displayName.endsWith(" 芒"))
        }
    }

    @Test
    fun `dictionary validation preserves screenshot cleanup and old saved summaries`() {
        val expected = "广东 深圳 · 中国联通 · 美团"

        assertEquals(expected, summarize("广东 深圳 中国联通", "美团 网络收录数据 广东省 深圳市"))
        assertEquals(expected, SearchResultPresentation.summarizeText(
            number,
            "广东 深圳 中国联通\n美团 网络收录数据 广东省 深圳市",
        ))
    }

    @Test
    fun `invalid province city pair stays visible without inventing the correct province`() {
        val original = "广东杭州中国移动"
        val value = summarize("广东 深圳 中国联通", original)

        assertTrue(value, compact(value).contains(original))
        assertFalse(value, value.contains("浙江"))
        assertTrue(value, value.contains("中国联通"))
    }

    @Test
    fun `dictionary matches do not erase a company identity or full postal address`() {
        val company = "广东深圳海风科技有限公司"
        val address = "办公地址：广东省深圳市南山区科技路18号"
        val value = SearchResultPresentation.summarize(number, listOf(
            result("广东 深圳 中国联通"),
            BaiduSearchResult("$number $company", address, "https://example.invalid/company"),
        ))

        assertTrue(value, compact(value).contains(company))
        assertTrue(value, compact(value).contains(compact(address)))
    }

    @Test
    fun `dictionary cleanup preserves complete negative and uncertain location claims`() {
        listOf("并非广东深圳", "不在广东深圳", "可能并非广东深圳").forEach { claim ->
            val value = summarize("广东 深圳 中国联通", claim)
            assertTrue(value, compact(value).contains(claim))
            assertTrue(value, value.contains("中国联通"))
        }
    }

    @Test
    fun `autonomous prefecture metadata repeats once without abbreviating its official name`() {
        val value = summarize(
            "云南 大理白族自治州 中国移动",
            "美团 网络收录数据 云南省 大理白族自治州",
        )

        assertEquals(value, 1, Regex("大理白族自治州").findAll(value).count())
        assertTrue(value, value.contains("中国移动"))
        assertTrue(value, value.contains("美团"))
        assertFalse(value, value.contains("网络收录数据"))
    }

    private fun summarize(vararg snippets: String): String =
        SearchResultPresentation.summarize(number, snippets.map(::result))

    private fun result(snippet: String) = BaiduSearchResult("${number}号码查询", snippet, null)
    private fun compact(text: String): String = Regex("[\\p{P}\\p{Z}\\s]+").replace(text, "")
}
