package com.github.tomasbjerre.keepsheet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.tomasbjerre.keepsheet.ui.KeepSheetApp
import com.github.tomasbjerre.keepsheet.ui.theme.KeepSheetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeepSheetTheme {
                KeepSheetApp(
                    repository = (application as KeepSheetApplication).repository,
                    filesDir = filesDir,
                )
            }
        }
    }
}
