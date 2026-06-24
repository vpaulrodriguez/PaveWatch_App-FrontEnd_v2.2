package com.example.pavewatchapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.text.SimpleDateFormat
import java.util.*

/**
 * MonitoreoFragment — Consola de control en tiempo real.
 *
 * Integra:
 *  - Acelerómetro con filtro paso alto (igual que el código original)
 *  - Detección de baches por eje Z con umbral y cooldown
 *  - Detección de maniobras evasivas por eje X (máquina de estados)
 *  - GPS en tiempo real para incluir coordenadas en el payload
 *  - Envío MQTT SSL a HiveMQ con el mismo payload del backend
 *  - Gráfica MPAndroidChart en tiempo real
 *  - ViewModel compartido con HistorialFragment
 */
class MonitoreoFragment : Fragment(), SensorEventListener, LocationListener {

    // ----------------------------------------------------------------
    // ViewModel compartido con HistorialFragment
    // ----------------------------------------------------------------
    private lateinit var bacheViewModel: BacheViewModel

    // ================================================================
    // SENSORES Y FILTRO PASO ALTO
    // Mismo filtro ALPHA del código original para eliminar la gravedad
    // ================================================================
    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var giroscopio: Sensor?   = null

    // Filtro paso alto — igual que el original
    private val ALPHA = 0.8f
    private var gravityX = 0f
    private var gravityZ = 0f

    // Últimos valores filtrados (para la gráfica y los TextViews)
    private var netZ = 0f
    private var giroY = 0f

    // ================================================================
    // CALIBRACIÓN DE BACHES — mismos valores del código original
    // ================================================================
    private val UMBRAL_BACHE = 5.5f          // m/s² en eje Z
    private val COOLDOWN_REDUNDANCIA_MS = 3000L
    private var ultimoEventoDetectadoTiempo = 0L

    // ================================================================
    // MÁQUINA DE ESTADOS — MANIOBRA EVASIVA (Eje X)
    // Lógica exacta del código original
    // ================================================================
    private val UMBRAL_EVASION = 4.0f
    private var estadoEvasion = 0             // 0=Normal, 1=Primer volantazo
    private var direccionPrimerVolantazo = 0f // +1 o -1
    private var tiempoPrimerVolantazo = 0L
    private val TIEMPO_MAX_REGRESO_MS = 2500L

    // ================================================================
    // GPS — mismas variables y lógica del código original
    // ================================================================
    private lateinit var locationManager: LocationManager
    private var latitudActual  = 0.0
    private var longitudActual = 0.0
    private val PERMISO_LOCATION_CODE = 100

    // ================================================================
    // MQTT — mismas credenciales y broker del código original
    // ================================================================
    private val BROKER_URL = "ssl://118c4eb634124e6a914d87e18ad2ea02.s1.eu.hivemq.cloud:8883"
    private val MQTT_USER  = "admin"
    private val MQTT_PASS  = "Admin123"
    private val MQTT_TOPIC = "pavewatch/alertas"

    // ================================================================
    // GRÁFICA
    // ================================================================
    private lateinit var lineChart: LineChart
    private val entradasAcel  = ArrayList<Entry>()
    private val entradasGiro  = ArrayList<Entry>()
    private var indiceGrafica = 0f
    private val MAX_PUNTOS    = 60

    // ================================================================
    // VIEWS
    // ================================================================
    private lateinit var switchAuto:        SwitchCompat
    private lateinit var btnReportar:       MaterialButton
    private lateinit var tvAcelZ:           TextView
    private lateinit var tvGiroY:           TextView
    private lateinit var tvContadorBaches:  TextView
    private lateinit var tvGpsEstado:       TextView
    private lateinit var tvCoordenadas:     TextView
    private lateinit var tvMqttEstado:      TextView
    private lateinit var tvBrokerUrl:       TextView
    private lateinit var chipSensorEstado:  Chip
    private lateinit var layoutGrafica:     ViewGroup
    private lateinit var layoutPlaceholder: View
    private lateinit var viewMqttDot:       View

    // ================================================================
    // CICLO DE VIDA
    // ================================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_monitoreo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bacheViewModel = ViewModelProvider(requireActivity())[BacheViewModel::class.java]

        enlazarViews(view)
        inicializarGPS()
        inicializarSensores()
        configurarGrafica()
        configurarListeners()
        observarViewModel()
    }

    // ================================================================
    // INICIALIZACIÓN
    // ================================================================

    private fun enlazarViews(view: View) {
        switchAuto        = view.findViewById(R.id.switch1)
        btnReportar       = view.findViewById(R.id.btnReportar)
        tvAcelZ           = view.findViewById(R.id.tvAcelZ)
        tvGiroY           = view.findViewById(R.id.tvGiroY)
        tvContadorBaches  = view.findViewById(R.id.tvContadorBaches)
        tvGpsEstado       = view.findViewById(R.id.tvGpsEstado)
        tvCoordenadas     = view.findViewById(R.id.tvCoordenadas)
        tvMqttEstado      = view.findViewById(R.id.tvMqttEstado)
        tvBrokerUrl       = view.findViewById(R.id.tvBrokerUrl)
        chipSensorEstado  = view.findViewById(R.id.chipSensorEstado)
        layoutGrafica     = view.findViewById(R.id.layoutGrafica)
        layoutPlaceholder = view.findViewById(R.id.layoutGraficaPlaceholder)
        viewMqttDot       = view.findViewById(R.id.viewMqttDot)

        // Valores iniciales de UI
        tvBrokerUrl.text  = "hivemq.cloud:8883"
        tvMqttEstado.text = "SSL Configurado"
    }

    /**
     * Inicializa el GPS y pide permisos si no los tiene.
     * Misma lógica que el código original pero adaptada a Fragment.
     */
    private fun inicializarGPS() {
        locationManager = requireContext()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Verificar permiso de ubicación
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Pedir permiso desde el Fragment
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISO_LOCATION_CODE
            )
        } else {
            iniciarActualizacionesGPS()
        }
    }

    /**
     * Registra el listener de GPS.
     * Actualiza cada 2 segundos o cada 5 metros — igual que el original.
     */
    private fun iniciarActualizacionesGPS() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,  // 2 segundos
                5f,     // 5 metros
                this
            )
            tvGpsEstado.text = "Buscando señal..."
        }
    }

    private fun inicializarSensores() {
        sensorManager = requireContext()
            .getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        giroscopio   = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    // ================================================================
    // GRÁFICA — MPAndroidChart
    // ================================================================

    private fun configurarGrafica() {
        lineChart = LineChart(requireContext())
        layoutGrafica.addView(
            lineChart,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        lineChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            setDrawBorders(false)
            description.isEnabled    = false
            legend.isEnabled         = false
            isDoubleTapToZoomEnabled = false
            setTouchEnabled(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            setNoDataText("Activa la detección para ver datos")
            setNoDataTextColor(Color.parseColor("#475569"))
        }

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor     = Color.parseColor("#1E2333")
            axisLineColor = Color.parseColor("#1E2333")
            textColor     = Color.parseColor("#475569")
            setDrawLabels(false)
            setDrawAxisLine(false)
        }

        lineChart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor     = Color.parseColor("#1E2333")
            axisLineColor = Color.parseColor("#1E2333")
            textColor     = Color.parseColor("#475569")
            textSize      = 9f
            axisMinimum   = -15f
            axisMaximum   = 15f
        }

        lineChart.axisRight.isEnabled = false
    }

    private fun actualizarGrafica(valorAcel: Float, valorGiro: Float) {
        entradasAcel.add(Entry(indiceGrafica, valorAcel))
        entradasGiro.add(Entry(indiceGrafica, valorGiro))
        indiceGrafica++

        if (entradasAcel.size > MAX_PUNTOS) {
            entradasAcel.removeAt(0)
            entradasGiro.removeAt(0)
        }

        val dataSetAcel = LineDataSet(entradasAcel, "Acel Z").apply {
            color     = Color.parseColor("#F59E0B") // ámbar
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode           = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }

        val dataSetGiro = LineDataSet(entradasGiro, "Giro Y").apply {
            color     = Color.parseColor("#22D3EE") // cian
            lineWidth = 1f
            setDrawCircles(false)
            setDrawValues(false)
            mode           = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }

        lineChart.data = LineData(dataSetAcel, dataSetGiro)
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()

        if (entradasAcel.size >= MAX_PUNTOS) {
            lineChart.setVisibleXRangeMaximum(MAX_PUNTOS.toFloat())
            lineChart.moveViewToX(lineChart.data.entryCount.toFloat())
        }
    }

    // ================================================================
    // LISTENERS DE CONTROLES
    // ================================================================

    private fun configurarListeners() {

        // Switch: activa/desactiva sensores
        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activarSensores()
                chipSensorEstado.text = "Activo"
                Toast.makeText(requireContext(),
                    "Detección automática: ACTIVADA", Toast.LENGTH_SHORT).show()
            } else {
                desactivarSensores()
                chipSensorEstado.text = "En espera"
                Toast.makeText(requireContext(),
                    "Detección automática en pausa", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón manual — mismo comportamiento que el original:
        // envía con severidad 9.5, origen MANUAL, tipo BACHE
        btnReportar.setOnClickListener {
            Toast.makeText(requireContext(),
                "Enviando bache MANUAL...", Toast.LENGTH_SHORT).show()
            enviarMqtt(9.5f, "MANUAL", "BACHE")
        }
    }

    private fun observarViewModel() {
        bacheViewModel.listaBaches.observe(viewLifecycleOwner) { lista ->
            tvContadorBaches.text = lista.size.toString()
        }
    }

    // ================================================================
    // SENSORES — registro y desregistro
    // ================================================================

    private fun activarSensores() {
        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        giroscopio?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        layoutPlaceholder.visibility = View.GONE
    }

    private fun desactivarSensores() {
        sensorManager.unregisterListener(this)
        layoutPlaceholder.visibility = View.VISIBLE
    }

    // ================================================================
    // SensorEventListener
    // Lógica EXACTA del código original:
    //   - Filtro paso alto para quitar gravedad
    //   - Detección de bache en eje Z
    //   - Máquina de estados para maniobra evasiva en eje X
    // ================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {

            Sensor.TYPE_ACCELEROMETER -> {
                val tiempoActual = System.currentTimeMillis()

                // Valores crudos
                val rawX = event.values[0] // Lateral (evasión)
                val rawZ = event.values[2] // Vertical (bache)

                // Filtro paso alto — quita la gravedad (igual que original)
                gravityX = ALPHA * gravityX + (1 - ALPHA) * rawX
                gravityZ = ALPHA * gravityZ + (1 - ALPHA) * rawZ

                val filtradoX = rawX - gravityX
                netZ          = rawZ - gravityZ

                // Actualizar UI en tiempo real
                tvAcelZ.text = String.format("%.2f", netZ)
                actualizarGrafica(netZ, giroY)

                // ── 1. DETECCIÓN DE BACHE VERTICAL ──────────────────────
                val fuerzaZ = Math.abs(netZ)
                if (fuerzaZ > UMBRAL_BACHE &&
                    (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS)
                ) {
                    ultimoEventoDetectadoTiempo = tiempoActual
                    estadoEvasion = 0 // Resetear evasión

                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(),
                            "¡BACHE! Fuerza: ${String.format("%.1f", fuerzaZ)}",
                            Toast.LENGTH_SHORT).show()
                    }

                    // Registrar en ViewModel para el Historial
                    val hora = obtenerHoraActual()
                    bacheViewModel.agregarBache(
                        Bache(
                            tipo   = "Automático – Acelerómetro",
                            hora   = hora,
                            estado = "Enviado a MQTT"
                        )
                    )

                    // Enviar al broker con el payload exacto del backend
                    enviarMqtt(fuerzaZ, "AUTOMATICO", "BACHE")
                }

                // ── 2. MÁQUINA DE ESTADOS — MANIOBRA EVASIVA ────────────
                // Timeout: si pasó demasiado tiempo desde el primer volantazo, cancelar
                if (estadoEvasion == 1 &&
                    (tiempoActual - tiempoPrimerVolantazo > TIEMPO_MAX_REGRESO_MS)
                ) {
                    estadoEvasion = 0
                }

                if (Math.abs(filtradoX) > UMBRAL_EVASION) {
                    if (estadoEvasion == 0) {
                        // Primer volantazo detectado (salida del carril)
                        estadoEvasion              = 1
                        tiempoPrimerVolantazo       = tiempoActual
                        direccionPrimerVolantazo    = Math.signum(filtradoX)

                    } else if (estadoEvasion == 1) {
                        // Esperando regreso con dirección contraria
                        if (Math.signum(filtradoX) != direccionPrimerVolantazo) {
                            if (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS) {
                                ultimoEventoDetectadoTiempo = tiempoActual

                                activity?.runOnUiThread {
                                    Toast.makeText(requireContext(),
                                        "¡Maniobra peligrosa detectada! Tenga cuidado",
                                        Toast.LENGTH_LONG).show()
                                }

                                // Registrar en ViewModel como evasión
                                bacheViewModel.agregarBache(
                                    Bache(
                                        tipo   = "Automático – Maniobra Evasiva",
                                        hora   = obtenerHoraActual(),
                                        estado = "Enviado a MQTT"
                                    )
                                )

                                // Enviar al broker con tipo EVASION
                                enviarMqtt(Math.abs(filtradoX), "AUTOMATICO", "EVASION")
                            }
                            estadoEvasion = 0
                        }
                    }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                giroY = event.values[1]
                tvGiroY.text = String.format("%.2f", giroY)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción
    }

    // ================================================================
    // LocationListener — GPS en tiempo real
    // ================================================================

    /**
     * Llamado cada vez que el GPS tiene una nueva ubicación.
     * Actualiza las variables globales usadas en el payload MQTT.
     */
    override fun onLocationChanged(location: Location) {
        latitudActual  = location.latitude
        longitudActual = location.longitude

        // Actualizar UI de la tarjeta GPS
        activity?.runOnUiThread {
            tvGpsEstado.text   = "Conectado"
            tvCoordenadas.text = "${String.format("%.4f", latitudActual)}° " +
                    "${String.format("%.4f", longitudActual)}°"
        }
    }

    // ================================================================
    // MQTT — Envío con SSL a HiveMQ
    // Payload EXACTO que lee el backend:
    // {"latitud": X, "longitud": Y, "severidad": Z,
    //  "tipo_evento": "BACHE|EVASION", "dispositivo": "app_MANUAL|app_AUTOMATICO"}
    // ================================================================

    /**
     * Envía el evento al broker HiveMQ en un hilo secundario.
     * NO bloquea el hilo principal (igual que el original con new Thread()).
     *
     * @param severidadReal Magnitud del evento detectado
     * @param origen        "MANUAL" o "AUTOMATICO"
     * @param tipoEvento    "BACHE" o "EVASION"
     */
    private fun enviarMqtt(severidadReal: Float, origen: String, tipoEvento: String) {
        Thread {
            val clientId = MqttClient.generateClientId()
            try {
                val mqttClient = MqttClient(BROKER_URL, clientId, MemoryPersistence())

                // Opciones de conexión — mismas credenciales del original
                val options = MqttConnectOptions().apply {
                    userName     = MQTT_USER
                    password     = MQTT_PASS.toCharArray()
                    isCleanSession = true
                }

                mqttClient.connect(options)

                // Payload exacto que espera el backend
                val payload = """{"latitud": $latitudActual, "longitud": $longitudActual, "severidad": $severidadReal, "tipo_evento": "$tipoEvento", "dispositivo": "app_$origen"}"""

                val message = MqttMessage(payload.toByteArray()).apply {
                    qos = 1
                }

                mqttClient.publish(MQTT_TOPIC, message)
                mqttClient.disconnect()

                // Actualizar UI de MQTT en el hilo principal
                activity?.runOnUiThread {
                    tvMqttEstado.text = "Enviado ✓"
                    viewMqttDot.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#34D399")
                        )
                    // Volver al estado normal después de 3 segundos
                    Handler(Looper.getMainLooper()).postDelayed({
                        tvMqttEstado.text = "SSL Configurado"
                    }, 3000)
                }

            } catch (e: MqttException) {
                e.printStackTrace()
                // Mostrar error en UI
                activity?.runOnUiThread {
                    tvMqttEstado.text = "Error conexión"
                    viewMqttDot.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#EF4444")
                        )
                }
            }
        }.start()
    }

    // ================================================================
    // PERMISOS — respuesta del usuario
    // ================================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISO_LOCATION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarActualizacionesGPS()
        } else {
            tvGpsEstado.text   = "Sin permiso GPS"
            tvCoordenadas.text = "Coordenadas no disponibles"
        }
    }

    // ================================================================
    // CICLO DE VIDA — gestión de recursos
    // ================================================================

    override fun onResume() {
        super.onResume()
        if (switchAuto.isChecked) activarSensores()
    }

    override fun onPause() {
        super.onPause()
        // Siempre desregistrar sensores al pausar para ahorrar batería
        desactivarSensores()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Detener actualizaciones GPS al destruir la vista
        locationManager.removeUpdates(this)
    }

    // ================================================================
    // UTILIDADES
    // ================================================================

    private fun obtenerHoraActual(): String {
        val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formato.format(Date())
    }

    companion object {
        fun newInstance(): MonitoreoFragment = MonitoreoFragment()
    }
}