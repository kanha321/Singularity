package com.kanhaji.singularity.feature.image

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kanhaji.core.theme.composable.AppTheme

class ImageViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CURRENT_PATH = "extra_current_path"
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_TITLE = "extra_title"

        fun createIntent(
            context: Context,
            currentPath: String,
            folderPath: String,
            title: String
        ): Intent {
            return Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_CURRENT_PATH, currentPath)
                putExtra(EXTRA_FOLDER_PATH, folderPath)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentPath = intent.getStringExtra(EXTRA_CURRENT_PATH).orEmpty()
        val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    ImageViewerScreen(
                        initialPath = currentPath,
                        folderPath = folderPath,
                        initialTitle = title,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
