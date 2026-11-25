package com.fcj.telnumbercheck.factory

object AppFactoryConfig {
    const val APP_KEY = "laidian-kuaijian"
    const val APP_SLUG = "p-dfca67eab2a440b2ae3e"
    const val HOST = "vdb.aivest.art"
    const val DOWNLOAD_PAGE_URL = "https://$HOST/apps/$APP_SLUG"
    const val LATEST_VERSION_URL = "https://$HOST/api/v1/apps/latest?platform=android&app_key=$APP_KEY"
}
