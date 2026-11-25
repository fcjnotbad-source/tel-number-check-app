package com.fcj.telnumbercheck.factory

import java.net.URI
import org.json.JSONObject

data class FactoryRelease(val version: String, val pageUrl: String)

object FactoryReleaseDecoder {
    fun decode(payload: String): FactoryRelease? = runCatching {
        val json = JSONObject(payload)
        if (!json.optBoolean("ok")) return null
        val app = json.getJSONObject("app")
        if (!app.optBoolean("enabled") || app.optString("platform") != "android") return null
        if (app.optString("slug") != AppFactoryConfig.APP_SLUG) return null
        val version = app.getString("version").trim()
        if (!AppVersion.isSupported(version)) return null
        val pageUrl = app.getString("page_url").trim()
        val uri = URI(pageUrl)
        if (
            uri.scheme != "https" || uri.host != AppFactoryConfig.HOST ||
            uri.userInfo != null || uri.port !in setOf(-1, 443) ||
            uri.path != "/apps/${AppFactoryConfig.APP_SLUG}"
        ) return null
        FactoryRelease(version, pageUrl)
    }.getOrNull()
}
