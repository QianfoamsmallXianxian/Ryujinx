package org.kenjinx.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.system.Os
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// NEOV2-NCZ2: conversor NSP -> NSZ dentro do emulador.
// Gera "<jogo>.nsz.part" na mesma pasta, o C# comprime e verifica (SHA-256 de cada arquivo),
// e so entao renomeia para .nsz e exclui o NSP original. Em erro ou cancelamento, o original fica intacto.
object NszConverter {
    const val LEVEL_FAST = 8
    const val LEVEL_MAX = 18

    const val PHASE_COMPRESSING = 1
    const val PHASE_VERIFYING = 2

    val running = mutableStateOf(false)
    val permille = mutableIntStateOf(0)
    val phase = mutableIntStateOf(0)
    val gameName = mutableStateOf("")

    fun cancel() {
        if (running.value) {
            try {
                KenjinxNative.deviceCompressCancel()
            } catch (_: Throwable) {
            }
        }
    }

    fun start(context: Context, source: DocumentFile, level: Int, onFinished: (Boolean, String) -> Unit) {
        val main = Handler(Looper.getMainLooper())

        if (running.value) {
            main.post { onFinished(false, "Já existe uma conversão em andamento.") }
            return
        }

        running.value = true
        permille.intValue = 0
        phase.intValue = PHASE_COMPRESSING
        gameName.value = source.name ?: ""

        thread(name = "NszConverter") {
            var part: DocumentFile? = null
            var success = false
            var message: String

            try {
                val name = source.name ?: throw IllegalStateException("Nome de arquivo inválido.")

                if (!name.lowercase(Locale.ROOT).endsWith(".nsp")) {
                    throw IllegalStateException("Somente arquivos NSP podem ser comprimidos.")
                }

                val parent = source.parentFile
                    ?: throw IllegalStateException("Não foi possível acessar a pasta do jogo.")

                val baseName = name.substring(0, name.length - 4)
                val outName = "$baseName.nsz"
                val partName = "$outName.part"

                if (parent.findFile(outName) != null) {
                    throw IllegalStateException("Já existe \"$outName\" nessa pasta.")
                }

                parent.findFile(partName)?.delete()

                part = parent.createFile("application/octet-stream", partName)
                    ?: throw IllegalStateException("Não foi possível criar o arquivo temporário.")

                val sourceSize = source.length()
                val resolver = context.contentResolver
                var result = 1

                resolver.openFileDescriptor(source.uri, "rw").use { inPfd ->
                    resolver.openFileDescriptor(part!!.uri, "rw").use { outPfd ->
                        if (inPfd == null || outPfd == null) {
                            throw IllegalStateException("Não foi possível abrir os arquivos.")
                        }

                        val stat = Os.fstatvfs(outPfd.fileDescriptor)
                        val free = stat.f_bavail * stat.f_bsize

                        if (free < sourceSize) {
                            throw IllegalStateException(
                                "Espaço insuficiente: são necessários ${sourceSize / (1024 * 1024)} MB livres, há ${free / (1024 * 1024)} MB."
                            )
                        }

                        val polling = AtomicBoolean(true)

                        val poller = thread(name = "NszConverterProgress") {
                            while (polling.get()) {
                                try {
                                    permille.intValue = KenjinxNative.deviceCompressProgress()
                                    val p = KenjinxNative.deviceCompressPhase()
                                    if (p != 0) phase.intValue = p
                                    Thread.sleep(250)
                                } catch (_: Throwable) {
                                }
                            }
                        }

                        try {
                            result = KenjinxNative.deviceCompressNsp(inPfd.fd, outPfd.fd, level)
                        } finally {
                            polling.set(false)
                            poller.join(1000)
                        }
                    }
                }

                when (result) {
                    0 -> {
                        val candidateSize = part!!.length()
                        if (candidateSize <= 0L || candidateSize >= sourceSize) {
                            throw IllegalStateException("Não houve redução de tamanho. O NSP original foi mantido.")
                        }
                        if (!part!!.renameTo(outName)) {
                            throw IllegalStateException("Não foi possível renomear o arquivo comprimido. O original foi mantido.")
                        }

                        part = null

                        val newSize = parent.findFile(outName)?.length() ?: 0L
                        val sizes = "${sourceSize / (1024 * 1024)} MB → ${newSize / (1024 * 1024)} MB"

                        message = if (source.delete()) {
                            "Jogo comprimido ($sizes). O NSP original foi excluído."
                        } else {
                            "Jogo comprimido ($sizes), mas não foi possível excluir o NSP original."
                        }

                        success = true
                    }
                    2 -> message = "Conversão cancelada. O arquivo original foi mantido."
                    3 -> message = "Não houve redução de tamanho. O NSP original foi mantido."
                    else -> message = "Falha na conversão. O arquivo original foi mantido."
                }
            } catch (t: Throwable) {
                message = t.message ?: "Falha na conversão. O arquivo original foi mantido."
            } finally {
                if (!success) {
                    try {
                        part?.delete()
                    } catch (_: Throwable) {
                    }
                }

                running.value = false
                phase.intValue = 0
            }

            main.post { onFinished(success, message) }
        }
    }
}
