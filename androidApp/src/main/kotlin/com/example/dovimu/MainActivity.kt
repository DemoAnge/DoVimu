package com.example.dovimu

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
import com.example.dovimu.platform.AndroidApp
import com.example.dovimu.platform.CrashReporter
import com.example.dovimu.platform.toUserMessage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.context = applicationContext
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
                    text = "DoVimu no pudo arrancar:\n${error.toUserMessage()}"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#0B0B0E"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(pad, pad, pad, pad)
                    gravity = Gravity.CENTER
                },
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
