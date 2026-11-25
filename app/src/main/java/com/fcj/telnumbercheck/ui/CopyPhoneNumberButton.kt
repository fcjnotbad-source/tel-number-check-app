package com.fcj.telnumbercheck.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fcj.telnumbercheck.data.PhoneNumberNormalizer

@Composable
internal fun CopyPhoneNumberButton(
    phoneNumber: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val normalizedNumber = PhoneNumberNormalizer.normalize(phoneNumber)
    IconButton(
        onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                ClipData.newPlainText("电话号码", normalizedNumber),
            )
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(context, "号码已复制", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
        enabled = normalizedNumber.isNotBlank(),
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "复制电话号码",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
