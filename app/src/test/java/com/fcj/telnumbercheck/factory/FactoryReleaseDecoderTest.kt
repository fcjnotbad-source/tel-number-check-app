package com.fcj.telnumbercheck.factory

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FactoryReleaseDecoderTest {
    @Test
    fun `accepts an enabled Android release for this application`() {
        assertEquals(
            FactoryRelease("0.3.10", AppFactoryConfig.DOWNLOAD_PAGE_URL),
            FactoryReleaseDecoder.decode(payload()),
        )
    }

    @Test
    fun `unavailable malformed or HTML responses are ignored`() {
        listOf(
            "",
            "not-json",
            "<html><title>Service unavailable</title></html>",
            "{}",
            """{"ok":false}""",
            """{"ok":true,"app":null}""",
        ).forEach { assertNull(it, FactoryReleaseDecoder.decode(it)) }
    }

    @Test
    fun `disabled applications other platforms and other applications are ignored`() {
        listOf(
            payload(enabled = false),
            payload(platform = "ios"),
            payload(slug = "another-app"),
            payload(version = "latest"),
            payload(version = ""),
        ).forEach { assertNull(it, FactoryReleaseDecoder.decode(it)) }
    }

    @Test
    fun `release links must point to the exact HTTPS application page`() {
        listOf(
            "http://${AppFactoryConfig.HOST}/apps/${AppFactoryConfig.APP_SLUG}",
            "https://untrusted.example/apps/${AppFactoryConfig.APP_SLUG}",
            "https://${AppFactoryConfig.HOST}.untrusted.example/apps/${AppFactoryConfig.APP_SLUG}",
            "https://user@${AppFactoryConfig.HOST}/apps/${AppFactoryConfig.APP_SLUG}",
            "https://${AppFactoryConfig.HOST}:8443/apps/${AppFactoryConfig.APP_SLUG}",
            "https://${AppFactoryConfig.HOST}/apps/another-app",
            "https://${AppFactoryConfig.HOST}/download/${AppFactoryConfig.APP_SLUG}",
            "javascript:alert(1)",
            "/apps/${AppFactoryConfig.APP_SLUG}",
        ).forEach { url ->
            assertNull(url, FactoryReleaseDecoder.decode(payload(pageUrl = url)))
        }
    }

    private fun payload(
        version: String = "0.3.10",
        enabled: Boolean = true,
        platform: String = "android",
        slug: String = AppFactoryConfig.APP_SLUG,
        pageUrl: String = AppFactoryConfig.DOWNLOAD_PAGE_URL,
    ): String = JSONObject()
        .put("ok", true)
        .put(
            "app",
            JSONObject()
                .put("enabled", enabled)
                .put("platform", platform)
                .put("slug", slug)
                .put("version", version)
                .put("page_url", pageUrl),
        )
        .toString()
}
