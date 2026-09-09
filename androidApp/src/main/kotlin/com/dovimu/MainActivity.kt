package com.dovimu

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.toColorInt
import com.dovimu.platform.AndroidApp
import com.dovimu.platform.CrashReporter
import com.dovimu.platform.toUserMessage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.context = applicationContext
        AndroidApp.activity = this
        CrashReporter.install(this)
        enableEdgeToEdge()
        try {
            setContent {
                App()
            }
        } catch (error: Throwable) {
            CrashReporter.recordFatal(this, error)
            Log.e("DoVimu", "fallo al componer la UI", error)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setContentView(
                TextView(this).apply {
                    text = getString(R.string.startup_failed, error.toUserMessage())
                    setTextColor(Color.WHITE)
                    setBackgroundColor("#0B0B0E".toColorInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(pad, pad, pad, pad)
                    gravity = Gravity.CENTER
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidApp.activity = this
    }

    override fun onPause() {
        if (AndroidApp.activity === this) AndroidApp.activity = null
        super.onPause()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
