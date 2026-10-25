package com.kanhaji.singularity.feature.docs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kanhaji.core.theme.composable.AppTheme

class TextPreviewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REMOTE_PATH = "extra_remote_path"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FORMATTED_SIZE = "extra_formatted_size"

        fun createIntent(
            context: Context,
            remotePath: String,
            title: String,
            formattedSize: String = ""
        ): Intent {
            return Intent(context, TextPreviewActivity::class.java).apply {
                putExtra(EXTRA_REMOTE_PATH, remotePath)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMATTED_SIZE, formattedSize)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val remotePath = intent.getStringExtra(EXTRA_REMOTE_PATH).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val formattedSize = intent.getStringExtra(EXTRA_FORMATTED_SIZE).orEmpty()

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TextPreviewScreen(
                        remotePath = remotePath,
                        title = title,
                        formattedSize = formattedSize,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
