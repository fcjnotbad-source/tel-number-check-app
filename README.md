<p align="center"><img src="assets/app-icon.png" width="80" height="80" alt="来电快检应用图标"></p>

# 来电快检

来电快检是一款适用于 **Android 10 及以上**手机的陌生来电识别与手动查号工具。陌生号码来电时，手机照常响铃。来电快检在后台查询号码，把能查到的商家名称、归属地、运营商或公众标记整理成一张提示卡，供你决定是否接听。

[访问官网](https://tel.pdos.work/) · [下载 Android 版](https://vdb.aivest.art/apps/p-dfca67eab2a440b2ae3e) · [提交使用反馈](https://github.com/fcjnotbad-source/tel-number-check-app/issues/new?template=feedback.yml)

此软件来自AI系统独立开发者：常见

本仓库开放完整 Android 客户端源码。你可以查看[来电识别](app/src/main/java/com/fcj/telnumbercheck/call/)、[联网查号](app/src/main/java/com/fcj/telnumbercheck/search/)和[本机存储](app/src/main/java/com/fcj/telnumbercheck/data/)的实现，也可以自行构建安装包。

## 第一次使用

1. 从上方 Android 下载入口进入应用工厂，在手机上下载安装包。
2. 打开来电快检，在「来电」页点「启用来电识别」，按系统提示选择来电快检作为「来电显示和骚扰电话应用」。
3. 想查未接来电时，打开「查号」页输入号码。已有缓存的号码可点「重新查询」，获取当前的号码服务卡信息。

如果只在锁屏时看不到提醒，可在「设置」页查看通知和全屏提醒的权限状态。不同手机的后台运行设置可能影响提示卡显示。

## 号码信息从哪里来？

来电快检通过手机访问搜索引擎，只收录号码查询服务卡里的字段。多个来源出现相同信息时，提示卡会合并显示。有些号码没有公开服务卡，此时可以打开搜索页面继续查阅。卡片上的标记仅供接听时参考，由你决定是否接听。

## 哪些数据保存在手机上？

无需注册或登录。命中有效缓存时，手机直接读取本地结果。未命中缓存或主动重新查询时，手机将待查号码直接发送给百度、360 搜索和搜狗搜索。号码整理在本机完成，开发者服务器不经手查号请求。

有效结果在应用内缓存 180 天。个人标记可以手动清除，卸载可删除本机应用数据。Android 系统备份或换机迁移可能包含这些数据，取决于设备和系统设置。[完整隐私说明](PRIVACY.md)

## 反馈问题或建议

请写明手机型号、Android 版本、来电快检版本，以及遇到问题时看到的情况。反馈记录公开可见，如附截图，请遮住完整电话号码、联系人姓名等个人信息。[提交反馈](https://github.com/fcjnotbad-source/tel-number-check-app/issues/new?template=feedback.yml) · [查看已有反馈](https://github.com/fcjnotbad-source/tel-number-check-app/issues)


## 从源码构建

使用 JDK 17 和 Android SDK Platform 35，在仓库根目录运行：

```sh
./gradlew testDebugUnitTest assembleDebug
```

Android Studio 可直接打开本仓库。命令行构建可通过 `ANDROID_HOME` 指定 SDK 目录。生成的调试包位于 `app/build/outputs/apk/debug/app-debug.apk`，由本机调试密钥签名。

用 Node.js 和 Playwright 运行离线网页样本测试：

```sh
npm install --no-save --package-lock=false playwright
npx playwright install chromium
node scripts/test-search-extractor.cjs
```

## 开源许可

原创客户端与官网代码采用 [MIT 许可证](LICENSE)，允许使用、修改和再分发。Gradle Wrapper 使用 [Apache 2.0 许可证](gradle/wrapper/LICENSE)，省市数据沿用[上游 MIT 许可证](data/administrative-divisions/LICENSE)。第三方库按各自的许可分发。
