package com.fcj.telnumbercheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PrivacyInformation(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查询方式与隐私") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PrivacyParagraph("纯净查询", "无需注册或登录任何账号，没有广告、统计 SDK，查询号码不发送给开发者。")
                PrivacyParagraph("仅查询当前号码", "向搜索引擎查询当前号码，类似直接打开搜索页面输入来电号码。无需登录账号，也不读取或附带本机（被叫方）的电话号码。")
                PrivacyParagraph("号码来自系统", "不申请联系人、通话记录、短信、定位等无关敏感权限；来电号码由 Android 系统的“来电显示和骚扰电话应用”接口提供。")
                PrivacyParagraph("本机缓存", "查询成功后会保存在本机，缓存有效期为 180 天；再次遇到相同号码时直接显示，减少重复联网查询，也可随时手动刷新。")
                PrivacyParagraph("后台查询与提醒", "后台悬浮查询权限只用于把透明查询窗口挂载到系统窗口，确保来电时页面能正常加载；窗口不展示内容，也不接收触摸操作。")
                PrivacyParagraph("独立的版本检查", "版本检查仅向应用工厂获取公开版本信息，不携带电话号码或查询结果；服务不可用时不影响来电识别和查号。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun PrivacyParagraph(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
