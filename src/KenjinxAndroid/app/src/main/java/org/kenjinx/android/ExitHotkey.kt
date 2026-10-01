package org.kenjinx.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.preference.PreferenceManager

// NEOV2-HOTKEY2: atalho definido pelo proprio usuario (um botao ou uma combinacao de ate 4 botoes)
// que fecha o emulador inteiro. Configurado em Controles > "Atalho para fechar o emulador".
object ExitHotkey {
    private const val PREF_KEY = "exitHotkeyKeys"
    const val MAX_KEYS = 4

    // Enquanto a tela de configuracao esta capturando o atalho, todos os botoes vao para ela.
    @Volatile
    var captureListener: ((KeyEvent) -> Boolean)? = null

    private val pressed = HashSet<Int>()
    private var cached: List<Int>? = null
    private var lastTrigger = 0L

    @Volatile
    private var exiting = false

    fun isCapturable(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH -> false
        else -> true
    }

    fun load(context: Context): List<Int> {
        cached?.let { return it }
        val raw = try {
            PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, "") ?: ""
        } catch (_: Throwable) {
            ""
        }
        val keys = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().take(MAX_KEYS)
        cached = keys
        return keys
    }

    fun save(context: Context, keys: List<Int>) {
        val clean = keys.distinct().take(MAX_KEYS)
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY, clean.joinToString(","))
                .apply()
        } catch (_: Throwable) {
        }
        cached = clean
        synchronized(pressed) { pressed.clear() }
    }

    fun clear(context: Context) = save(context, emptyList())

    fun describe(keys: List<Int>): String =
        if (keys.isEmpty()) "Nenhum" else keys.joinToString(" + ") { keyName(it) }

    fun keyName(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        KeyEvent.KEYCODE_BUTTON_MODE -> "Home/Guia"
        KeyEvent.KEYCODE_BACK -> "Voltar"
        KeyEvent.KEYCODE_MENU -> "Menu"
        KeyEvent.KEYCODE_DPAD_UP -> "Cima"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Baixo"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Esquerda"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Direita"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }

    // Chamado no inicio de MainActivity.dispatchKeyEvent. Retorna true quando o evento deve ser consumido.
    fun onKeyEvent(activity: MainActivity, event: KeyEvent): Boolean {
        captureListener?.let { return it(event) }

        val keyCode = event.keyCode
        if (!isCapturable(keyCode)) return false

        val keys = load(activity)

        synchronized(pressed) {
            when (event.action) {
                KeyEvent.ACTION_UP -> {
                    pressed.remove(keyCode)
                    return false
                }
                KeyEvent.ACTION_DOWN -> pressed.add(keyCode)
                else -> return false
            }

            if (keys.isEmpty() || event.repeatCount != 0 || !keys.contains(keyCode) || !pressed.containsAll(keys)) {
                return false
            }

            pressed.clear()
        }

        val now = SystemClock.uptimeMillis()
        if (!exiting && now - lastTrigger > 1000) {
            lastTrigger = now
            exitEmulator(activity)
        }

        return true
    }

    // Fecha o jogo (se houver), espera a emulacao parar e encerra o aplicativo.
    private fun exitEmulator(activity: MainActivity) {
        exiting = true

        activity.runOnUiThread {
            val viewModel = MainActivity.mainViewModel

            try {
                if (activity.isGameRunning) {
                    viewModel?.closeGame()
                }
            } catch (_: Throwable) {
            }

            Thread {
                val deadline = SystemClock.uptimeMillis() + 5000
                while (viewModel?.emulationRunning == true && SystemClock.uptimeMillis() < deadline) {
                    try {
                        Thread.sleep(50)
                    } catch (_: Throwable) {
                    }
                }

                activity.runOnUiThread {
                    try {
                        activity.finishAndRemoveTask()
                    } catch (_: Throwable) {
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }, 400)
                }
            }.start()
        }
    }
}
