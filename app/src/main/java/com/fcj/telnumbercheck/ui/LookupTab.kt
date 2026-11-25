package com.fcj.telnumbercheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fcj.telnumbercheck.domain.CallerInfo

@Composable
internal fun LookupTab(
    number: String,
    loading: Boolean,
    result: CallerInfo?,
    roleEnabled: Boolean,
    onNumberChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onPreview: () -> Unit,
    onToggleLabel: () -> Unit,
    onOpenCalls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canSearch = number.any(Char::isDigit) && !loading
    val search = {
        if (canSearch) {
            focusManager.clearFocus()
            keyboard?.hide()
            onSearch()
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            onClick = onOpenCalls,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    if (roleEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.PhoneInTalk,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (roleEnabled) "来电自动检测已启用" else "开启来电自动检测",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("查一个号码", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "仅查询号码服务信息，结果保存在本机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = onNumberChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("电话号码") },
                    placeholder = { Text("手机、固话或服务热线") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                Button(onClick = search, enabled = canSearch, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在查询…", modifier = Modifier.padding(start = 9.dp))
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                        Text("查询号码", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (result == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Text("输入号码，即可查询", style = MaterialTheme.typography.titleMedium)
                Text("无需登录，仅查询搜索引擎的号码服务信息。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("查询结果", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRefresh, enabled = !loading) { Text("重新查询") }
            }
            if (loading && result.baiduSnippet == null) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(result.displayNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("正在查询号码服务信息…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                CallerInfoCard(result)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenSearch, modifier = Modifier.weight(1f)) {
                    Text(if (result.requiresVerification) "完成安全验证" else "打开搜索页")
                }
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("预览来电卡片") }
            }
            TextButton(onClick = onToggleLabel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (result.isLocallyMarked) "清除本机标记" else "标记为疑似骚扰", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
