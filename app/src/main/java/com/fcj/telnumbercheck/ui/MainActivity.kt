package com.fcj.telnumbercheck.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fcj.telnumbercheck.TelNumberCheckApp
import com.fcj.telnumbercheck.ui.theme.TelNumberCheckTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as TelNumberCheckApp).callerPopupVisibility.setVisible(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TelNumberCheckApp
        setContent {
            val popupVisible by app.callerPopupVisibility.visible.collectAsState()
            TelNumberCheckTheme {
                MainScreen(app.phoneLookupRepository, popupVisible)
            }
        }
    }
}
