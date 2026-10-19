package org.kenjinx.android

import android.os.Bundle
import androidx.activity.ComponentActivity

abstract class BaseActivity : ComponentActivity() {
    companion object {
        val crashHandler = CrashHandler()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Reaplica "Seguir o sistema" caso o idioma do Android tenha mudado desde a ultima vez.
        I18n.refresh()
        super.onCreate(savedInstanceState)
    }
}
