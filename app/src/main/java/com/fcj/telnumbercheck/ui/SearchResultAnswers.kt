package com.fcj.telnumbercheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcj.telnumbercheck.domain.BaiduSearchResult
import com.fcj.telnumbercheck.data.SearchResultPresentation

@Composable
internal fun SearchResultAnswers(results: List<BaiduSearchResult>, phoneNumber: String) {
    SearchResultSummary(SearchResultPresentation.summarize(phoneNumber, results))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchResultSummary(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.lineSequence().filter(String::isNotBlank).forEach { paragraph ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                paragraph.split(" · ").filter(String::isNotBlank).forEach { fact ->
                    Text(
                        text = fact,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 25.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
