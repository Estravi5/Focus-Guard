package com.example.focusguard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    // Configuración del Bot de Telegram
    private val TELEGRAM_BOT_TOKEN = "7610200043:AAGBIL_xJNplRG1lreZr0I6cqKKpUWPbdEc"
    private val TELEGRAM_CHAT_ID = "754741051"
    private val SIMULACION_ACTIVA = false // ← Cambia a false para enviar alertas reales

    // UI Components
    private lateinit var btnIniciarSesion: Button
    private lateinit var btnDetenerSesion: Button
    private lateinit var tvFrecuenciaActual: TextView
    private lateinit var tvEstado: TextView
    private lateinit var tvResumen: TextView

    // Monitoreo de datos
    private var isMonitoring = false
    private var handler: Handler? = null
    private var monitoringRunnable: Runnable? = null

    // Estadísticas de la sesión
    private val frecuenciasRegistradas = mutableListOf<Int>()
    private var alertasEnviadas = 0
    private var ultimaFrecuencia = 75 // Valor base inicial

    // Configuración
    private val UMBRAL_DISTRACCION = 100 // bpm
    private val INTERVALO_MEDICION = 5000L // 5 segundos

    // Cliente HTTP para Telegram
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inicializarUI()
        configurarEventos()
    }

    private fun inicializarUI() {
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion)
        btnDetenerSesion = findViewById(R.id.btnDetenerSesion)
        tvFrecuenciaActual = findViewById(R.id.tvFrecuenciaActual)
        tvEstado = findViewById(R.id.tvEstado)
        tvResumen = findViewById(R.id.tvResumen)

        btnDetenerSesion.isEnabled = false
        tvFrecuenciaActual.text = "FC: -- bpm"
        tvEstado.text = "Sesión no iniciada"
        tvResumen.text = ""
    }

    private fun configurarEventos() {
        btnIniciarSesion.setOnClickListener {
            iniciarSesionConcentracion()
        }

        btnDetenerSesion.setOnClickListener {
            detenerSesionConcentracion()
        }
    }

    private fun iniciarSesionConcentracion() {
        if (isMonitoring) return

        isMonitoring = true
        btnIniciarSesion.isEnabled = false
        btnDetenerSesion.isEnabled = true

        frecuenciasRegistradas.clear()
        alertasEnviadas = 0
        ultimaFrecuencia = generarFrecuenciaBase()

        tvEstado.text = "🔍 Monitoreando concentración..."
        tvResumen.text = ""

        handler = Handler(Looper.getMainLooper())
        monitoringRunnable = object : Runnable {
            override fun run() {
                if (isMonitoring) {
                    procesarMedicion()
                    handler?.postDelayed(this, INTERVALO_MEDICION)
                }
            }
        }

        handler?.post(monitoringRunnable!!)

        Toast.makeText(this, "🎯 Sesión de concentración iniciada", Toast.LENGTH_SHORT).show()
    }

    private fun detenerSesionConcentracion() {
        if (!isMonitoring) return

        isMonitoring = false
        btnIniciarSesion.isEnabled = true
        btnDetenerSesion.isEnabled = false

        handler?.removeCallbacks(monitoringRunnable!!)
        tvEstado.text = "Sesión finalizada"
        mostrarResumenFinal()

        Toast.makeText(this, "⏹️ Sesión de concentración finalizada", Toast.LENGTH_SHORT).show()
    }

    private fun procesarMedicion() {
        val nuevaFrecuencia = generarFrecuenciaRealista()
        frecuenciasRegistradas.add(nuevaFrecuencia)

        tvFrecuenciaActual.text = "FC: $nuevaFrecuencia bpm"

        if (nuevaFrecuencia > UMBRAL_DISTRACCION) {
            detectarDistraccion(nuevaFrecuencia)
        } else {
            tvEstado.text = "🟢 Concentrado - FC: $nuevaFrecuencia bpm"
        }

        ultimaFrecuencia = nuevaFrecuencia
    }

    private fun generarFrecuenciaBase(): Int {
        return Random.nextInt(70, 86)
    }

    private fun generarFrecuenciaRealista(): Int {
        val variacion = Random.nextInt(-8, 9)
        val nuevaFrecuencia = ultimaFrecuencia + variacion
        val probabilidadPico = Random.nextFloat()

        return when {
            probabilidadPico < 0.1 -> Random.nextInt(105, 141)
            probabilidadPico < 0.15 -> Random.nextInt(60, 70)
            else -> nuevaFrecuencia.coerceIn(65, 100)
        }
    }

    private fun detectarDistraccion(frecuencia: Int) {
        tvEstado.text = "🔴 DISTRACCIÓN DETECTADA - FC: $frecuencia bpm"
        alertasEnviadas++
        enviarAlertaTelegram(frecuencia)
    }

    private fun enviarAlertaTelegram(frecuencia: Int) {
        if (SIMULACION_ACTIVA) {
            Toast.makeText(this, "⚠️ Alerta enviada (simulada): FC $frecuencia bpm", Toast.LENGTH_SHORT).show()
            return
        }

        val mensaje = "⚠️ FocusGuard - Posible distracción detectada\n" +
                "Frecuencia cardíaca: $frecuencia bpm\n" +
                "Hora: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage"

                val json = JSONObject().apply {
                    put("chat_id", TELEGRAM_CHAT_ID)
                    put("text", mensaje)
                    put("parse_mode", "HTML")
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Error al enviar alerta: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread {
                            if (response.isSuccessful) {
                                Toast.makeText(this@MainActivity, "✅ Alerta enviada por Telegram", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "❌ Error al enviar alerta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarResumenFinal() {
        if (frecuenciasRegistradas.isEmpty()) {
            tvResumen.text = "No hay datos registrados"
            return
        }

        val promedioFC = frecuenciasRegistradas.average().toInt()
        val tiempoTotal = frecuenciasRegistradas.size * (INTERVALO_MEDICION / 1000)
        val minutos = tiempoTotal / 60
        val segundos = tiempoTotal % 60

        val fcMaxima = frecuenciasRegistradas.maxOrNull() ?: 0
        val fcMinima = frecuenciasRegistradas.minOrNull() ?: 0

        val resumen = """
            📊 RESUMEN DE SESIÓN
            
            ⏱️ Duración: ${minutos}m ${segundos}s
            📈 FC Promedio: $promedioFC bpm
            📊 FC Máxima: $fcMaxima bpm
            📉 FC Mínima: $fcMinima bpm
            🚨 Alertas enviadas: $alertasEnviadas
            📋 Mediciones: ${frecuenciasRegistradas.size}
            
            ${if (alertasEnviadas == 0) "🎉 ¡Excelente concentración!" else "⚠️ Se detectaron distracciones"}
        """.trimIndent()

        tvResumen.text = resumen
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(monitoringRunnable!!)
        isMonitoring = false
    }
}
