package com.fcj.telnumbercheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fcj.telnumbercheck.data.CallEventSnapshot
import com.fcj.telnumbercheck.data.CallLookupStatus
import com.fcj.telnumbercheck.data.SearchResultPresentation

@Composable
internal fun RecentCallCard(
    event: CallEventSnapshot,
    retryInProgress: Boolean,
    onRetry: () -> Unit,
    onLookup: () -> Unit,
) {
    val success = event.lookupStatus == CallLookupStatus.SUCCESS
    val canRetry = event.number.any(Char::isDigit) && !retryInProgress
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = when (event.lookupStatus) {
                    CallLookupStatus.SUCCESS -> if (event.resultFromCache) "本机缓存 · 号码信息" else "号码信息"
                    CallLookupStatus.LOADING -> if (retryInProgress) "正在重新检测" else "正在检测来电"
                    CallLookupStatus.FAILURE -> "此次检测未完成"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            when (event.lookupStatus) {
                CallLookupStatus.SUCCESS -> {
                    val summary = if (event.searchResults.isNotEmpty()) {
                        SearchResultPresentation.summarize(event.number, event.searchResults)
                    } else {
                        SearchResultPresentation.summarizeText(event.number, event.resultSnippet.orEmpty())
                    }
                    if (summary.isNotBlank()) {
                        SearchResultSummary(summary)
                    } else {
                        Text(
                            "暂未识别到有效的号码服务信息，可重新检测。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CallLookupStatus.LOADING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "正在查询号码服务信息，切换页面不会中断查询。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CallLookupStatus.FAILURE -> {
                    Text(
                        event.errorMessage ?: "暂未查到号码服务信息，可重试或手动查询。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${event.number}号码查询",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CopyPhoneNumberButton(event.number)
            }
            if (success) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onRetry, enabled = canRetry, modifier = Modifier.weight(1f)) {
                        Text(if (retryInProgress) "正在检测…" else "重新检测")
                    }
                    OutlinedButton(
                        onClick = onLookup,
                        enabled = event.number.any(Char::isDigit),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("手动查号")
                    }
                }
            } else {
                Button(onClick = onRetry, enabled = canRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(if (retryInProgress) "正在重新检测…" else "重新检测此号码")
                }
                OutlinedButton(
                    onClick = onLookup,
                    enabled = event.number.any(Char::isDigit),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("到查号页")
                }
            }
        }
    }
}
