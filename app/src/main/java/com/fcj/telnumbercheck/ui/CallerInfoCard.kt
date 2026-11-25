package com.fcj.telnumbercheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcj.telnumbercheck.domain.CallerInfo
import com.fcj.telnumbercheck.data.SearchResultPresentation

@Composable
fun CallerInfoCard(
    info: CallerInfo,
    modifier: Modifier = Modifier,
) {
    val phoneColor = Color(0xFF172321)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (info.isFromCache) "本机缓存 · 号码信息" else "号码信息",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (info.baiduSnippet != null) {
                if (info.searchResults.isNotEmpty()) {
                    SearchResultAnswers(info.searchResults, info.normalizedNumber)
                } else {
                    SearchResultSummary(SearchResultPresentation.summarizeText(info.normalizedNumber, info.detail))
                }
                if (info.isRefreshing) {
                    Text(
                        text = "已显示查询结果，正在补充信息…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${info.normalizedNumber}号码查询",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = phoneColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CopyPhoneNumberButton(phoneNumber = info.normalizedNumber)
                }
            } else {
                Text(
                    text = AnnotatedString(info.headline),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = AnnotatedString(info.detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF394946),
                )
            }
            if (info.isLocallyMarked) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Report,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = info.localLabel.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
