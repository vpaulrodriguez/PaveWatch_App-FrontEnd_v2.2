package com.example.pavewatchapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MonitoreoFragment extends Fragment implements SensorEventListener, LocationListener {

    private BacheViewModel bacheViewModel;

    // --- SENSORES Y FILTROS BASE ---
    private SensorManager sensorManager;
    private Sensor acelerometro;
    private Sensor giroscopio;
    private static final float ALPHA = 0.8f;
    private float gravityX = 0f;
    private float gravityZ = 0f;
    private float netZ = 0f;
    private float giroY = 0f;

    private boolean primeraLectura = true; // <-- Para evitar el primer envio INNECESARIO

    // ================================================================
    // ALGORITMOS 1 Y 2: AUTOCALIBRACIÓN Y TROCHAS (Varianza)
    // ================================================================
    private static final int BUFFER_SIZE = 100; // ~2 segundos de datos a 50Hz
    private final LinkedList<Float> bufferZ = new LinkedList<>();
    private static final float UMBRAL_BACHE_BASE = 4.5f;
    private static final float TROCHA_VARIANCE_THRESHOLD = 8.0f; // Si la varianza supera esto, es trocha
    private boolean esPavimentado = true;
    private float ruidoMotorActual = 0f;

    // ================================================================
    // ALGORITMO 3: MÁQUINA DE ESTADOS DE VELOCIDAD (Efecto Esquina)
    // ================================================================
    private static final float VELOCIDAD_MINIMA_MPS = 4.16f; // Aprox 15 km/h
    private static final long TIEMPO_GRACIA_ESQUINA_MS = 20000L; // 20 segundos de tolerancia al frenar
    private int estadoConduccion = 0; // 0: Caminando/Parado, 1: Conduciendo, 2: Frenado en esquina (Grace Period)
    private long tiempoUltimaAltaVelocidad = 0L;
    private float velocidadActualMps = 0f;

    // ================================================================
    // ALGORITMO 4: MÁQUINA DE ESTADOS EVASIÓN (Curva en S)
    // ================================================================
    private static final float UMBRAL_EVASION = 3.5f; // MEJORAR URGENTE
    private static final long TIEMPO_MAX_RETORNO_MS = 3500L; // Máximo 3.5 seg para devolver el timón
    private int estadoEvasion = 0; // 0: Normal, 1: Volantazo inicial detectado
    private float direccionPrimerVolantazo = 0f;
    private long tiempoPrimerVolantazo = 0L;

    private static final long COOLDOWN_REDUNDANCIA_MS = 3000L;
    private long ultimoEventoDetectadoTiempo = 0L;

    // --- GPS ---
    private LocationManager locationManager;
    private double latitudActual = 0.0;
    private double longitudActual = 0.0;
    private static final int PERMISO_LOCATION_CODE = 100;

    // --- MQTT ---
    private static final String BROKER_URL = "ssl://118c4eb634124e6a914d87e18ad2ea02.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_USER = "admin";
    private static final String MQTT_PASS = "Admin123";
    private static final String MQTT_TOPIC = "pavewatch/alertas";

    // --- GRÁFICA Y UI ---
    private LineChart lineChart;
    private final List<Entry> entradasAcel = new ArrayList<>();
    private final List<Entry> entradasGiro = new ArrayList<>();
    private float indiceGrafica = 0f;
    private static final int MAX_PUNTOS = 60;

    private SwitchCompat switchAuto;
    private MaterialButton btnReportar;
    private TextView tvAcelZ, tvGiroY, tvContadorBaches, tvGpsEstado, tvCoordenadas, tvMqttEstado, tvBrokerUrl;
    private Chip chipSensorEstado;
    private FrameLayout layoutGrafica;
    private View layoutPlaceholder, viewMqttDot;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_monitoreo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bacheViewModel = new ViewModelProvider(requireActivity()).get(BacheViewModel.class);
        enlazarViews(view);
        inicializarGPS();
        inicializarSensores();
        configurarGrafica();
        configurarListeners();
        observarViewModel();
    }

    private void enlazarViews(View view) {
        switchAuto = view.findViewById(R.id.switch1);
        btnReportar = view.findViewById(R.id.btnReportar);
        tvAcelZ = view.findViewById(R.id.tvAcelZ);
        tvGiroY = view.findViewById(R.id.tvGiroY);
        tvContadorBaches = view.findViewById(R.id.tvContadorBaches);
        tvGpsEstado = view.findViewById(R.id.tvGpsEstado);
        tvCoordenadas = view.findViewById(R.id.tvCoordenadas);
        tvMqttEstado = view.findViewById(R.id.tvMqttEstado);
        tvBrokerUrl = view.findViewById(R.id.tvBrokerUrl);
        chipSensorEstado = view.findViewById(R.id.chipSensorEstado);
        layoutGrafica = view.findViewById(R.id.layoutGrafica);
        layoutPlaceholder = view.findViewById(R.id.layoutGraficaPlaceholder);
        viewMqttDot = view.findViewById(R.id.viewMqttDot);

        tvBrokerUrl.setText("hivemq.cloud:8883");
        tvMqttEstado.setText("SSL Configurado");
    }

    // --- GPS Y VELOCIDAD ---
    private void inicializarGPS() {
        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISO_LOCATION_CODE);
        } else {
            iniciarActualizacionesGPS();
        }
    }

    private void iniciarActualizacionesGPS() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            boolean gpsHabilitado = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean redHabilitada = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            // 1. Intentar con el GPS puro (Alta precisión, ideal para la calle)
            if (gpsHabilitado) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
            }

            // 2. Intentar con la Red (Baja precisión, funciona bajo techo para pruebas)
            if (redHabilitada) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 2f, this);
            }

            if (!gpsHabilitado && !redHabilitada) {
                tvGpsEstado.setText("GPS Apagado en celular");
            } else {
                tvGpsEstado.setText("Buscando señal...");
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitudActual = location.getLatitude();
        longitudActual = location.getLongitude();

        // Extraer velocidad si está disponible (en m/s)
        if (location.hasSpeed()) {
            velocidadActualMps = location.getSpeed();
        } else {
            // Cálculo rudimentario si el hardware no da la velocidad directa
            velocidadActualMps = 0f;
        }

        long tiempoActual = System.currentTimeMillis();

        // MÁQUINA DE ESTADOS: Efecto Esquina
        if (velocidadActualMps >= VELOCIDAD_MINIMA_MPS) {
            estadoConduccion = 1; // Manejando
            tiempoUltimaAltaVelocidad = tiempoActual;
        } else if (estadoConduccion == 1 || estadoConduccion == 2) {
            if (tiempoActual - tiempoUltimaAltaVelocidad <= TIEMPO_GRACIA_ESQUINA_MS) {
                estadoConduccion = 2; // Grace Period (posiblemente frenó por esquina o semáforo)
            } else {
                estadoConduccion = 0; // Pasaron 20s lento. Está caminando o estacionado.
            }
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                String velKmH = String.format(Locale.getDefault(), "%.1f km/h", velocidadActualMps * 3.6);
                tvGpsEstado.setText("Conectado | " + velKmH);
                tvCoordenadas.setText(String.format(Locale.getDefault(), "%.4f° %.4f°", latitudActual, longitudActual));
            });
        }
    }

    // --- ALGORITMOS DE SENSORES Y DSP ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long tiempoActual = System.currentTimeMillis();

            float rawX = event.values[0];
            float rawZ = event.values[2];

            // SOLUCIONANDO EL ERROR DE LA PRIMERA LECTURA
            if (primeraLectura) {
                gravityX = rawX;
                gravityZ = rawZ;
                primeraLectura = false;
            }

            // Filtro Paso Alto (Elimina Gravedad 9.8)
            gravityX = ALPHA * gravityX + (1 - ALPHA) * rawX;
            gravityZ = ALPHA * gravityZ + (1 - ALPHA) * rawZ;

            float netX = rawX - gravityX;
            netZ = rawZ - gravityZ;

            // ACTUALIZACIÓN DE BUFFER Y CÁLCULO DE VARIANZA
            bufferZ.add(netZ);
            if (bufferZ.size() > BUFFER_SIZE) {
                bufferZ.removeFirst();
            }

            if (bufferZ.size() == BUFFER_SIZE) {
                analizarTerrenoYVibracion();
            }

            actualizarGraficaUI();

            // BLOQUEO DE SEGURIDAD: Solo analizamos si estamos conduciendo y en vía pavimentada

            /* --- APAGADO PARA PROBAR EN CASA ---
            if (estadoConduccion == 0) {
                actualizarChipEstado("Baja Vel.", "#475569");
                return; // Ignorar baches caminando
            }
            if (!esPavimentado) {
                actualizarChipEstado("Zona Trocha", "#B45309"); // Ámbar oscuro
                return; // Ignorar porque todo vibrará
            }
            -------------------------------------- */

            actualizarChipEstado("Analizando", "#065F46");

            // 1. DETECCIÓN DINÁMICA DE BACHE
            // El umbral se ajusta a la vibración del vehículo para evitar falsos positivos
            float umbralDinamicoActual = UMBRAL_BACHE_BASE + (ruidoMotorActual * 1.5f);

            float fuerzaZ = Math.abs(netZ);
            if (fuerzaZ > umbralDinamicoActual && (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS)) {
                ultimoEventoDetectadoTiempo = tiempoActual;
                estadoEvasion = 0; // Resetear evasión
                registrarEvento(fuerzaZ, "Automático – Bache", "BACHE");
            }

            // 2. DETECCIÓN DE ESQUIVE (CURVA EN S)
            if (estadoEvasion == 1 && (tiempoActual - tiempoPrimerVolantazo > TIEMPO_MAX_RETORNO_MS)) {
                estadoEvasion = 0; // Se demoró mucho en volver, fue un simple cambio de carril o curva normal
            }

            if (Math.abs(netX) > UMBRAL_EVASION) {
                if (estadoEvasion == 0) {
                    // Fase 1: Volantazo hacia un lado
                    estadoEvasion = 1;
                    tiempoPrimerVolantazo = tiempoActual;
                    direccionPrimerVolantazo = Math.signum(netX);
                } else if (estadoEvasion == 1) {
                    // Fase 2: Contra-volante (el signo debe ser exactamente el opuesto)
                    if (Math.signum(netX) == -direccionPrimerVolantazo) {
                        // ¡Curva en S detectada!
                        if (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS) {
                            ultimoEventoDetectadoTiempo = tiempoActual;
                            float fuerzaEvasion = Math.abs(netX);
                            registrarEvento(fuerzaEvasion, "Automático – Esquive", "EVASION");
                        }
                        estadoEvasion = 0;
                    }
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            giroY = event.values[1];
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> tvGiroY.setText(String.format(Locale.getDefault(), "%.2f", giroY)));
            }
        }
    }

    /**
     * Calcula estadísticamente si el terreno es pavimentado y extrae la vibración base del motor.
     */
    private void analizarTerrenoYVibracion() {
        float sum = 0f;
        for (float val : bufferZ) sum += val;
        float mean = sum / BUFFER_SIZE;

        float sumSq = 0f;
        for (float val : bufferZ) sumSq += (val - mean) * (val - mean);
        float variance = sumSq / BUFFER_SIZE;

        ruidoMotorActual = (float) Math.sqrt(variance); // Desviación estándar

        // Si la varianza promedio de los últimos 2 segundos es colosal, estamos en trocha/afirmado
        esPavimentado = variance < TROCHA_VARIANCE_THRESHOLD;
    }

    private void registrarEvento(float fuerza, String tituloUI, String tipoMqtt) {
        String hora = obtenerHoraActual();
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), tituloUI + " detectado", Toast.LENGTH_SHORT).show();
                bacheViewModel.agregarBache(new Bache(tituloUI, hora, "Enviado a MQTT"));
            });
        }
        enviarMqtt(fuerza, "AUTOMATICO", tipoMqtt);
    }

    private void actualizarChipEstado(String texto, String colorHex) {
        if (getActivity() != null && switchAuto.isChecked()) {
            getActivity().runOnUiThread(() -> {
                chipSensorEstado.setText(texto);
                chipSensorEstado.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(colorHex)));
            });
        }
    }

    // --- MÉTODOS DE SOPORTE (UI, MQTT, ETC) ---
    private void actualizarGraficaUI() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvAcelZ.setText(String.format(Locale.getDefault(), "%.2f", netZ));
                entradasAcel.add(new Entry(indiceGrafica, netZ));
                entradasGiro.add(new Entry(indiceGrafica, giroY));
                indiceGrafica++;

                if (entradasAcel.size() > MAX_PUNTOS) {
                    entradasAcel.remove(0);
                    entradasGiro.remove(0);
                }

                LineDataSet dataSetAcel = new LineDataSet(entradasAcel, "Acel Z");
                dataSetAcel.setColor(Color.parseColor("#F59E0B"));
                dataSetAcel.setDrawCircles(false);
                dataSetAcel.setDrawValues(false);
                dataSetAcel.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                LineDataSet dataSetGiro = new LineDataSet(entradasGiro, "Giro Y");
                dataSetGiro.setColor(Color.parseColor("#22D3EE"));
                dataSetGiro.setDrawCircles(false);
                dataSetGiro.setDrawValues(false);
                dataSetGiro.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                lineChart.setData(new LineData(dataSetAcel, dataSetGiro));
                lineChart.notifyDataSetChanged();
                lineChart.invalidate();

                if (entradasAcel.size() >= MAX_PUNTOS) {
                    lineChart.setVisibleXRangeMaximum(MAX_PUNTOS);
                    lineChart.moveViewToX(lineChart.getData().getEntryCount());
                }
            });
        }
    }

    private void inicializarSensores() {
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    private void configurarGrafica() {
        lineChart = new LineChart(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        layoutGrafica.addView(lineChart, params);
        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.setDrawGridBackground(false);
        lineChart.setDrawBorders(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(false);
        lineChart.setNoDataText("Activa la detección");

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawLabels(false);

        lineChart.getAxisLeft().setAxisMinimum(-15f);
        lineChart.getAxisLeft().setAxisMaximum(15f);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void configurarListeners() {
        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                activarSensores();
                actualizarChipEstado("Activo", "#065F46");
                switchAuto.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B")));
            } else {
                desactivarSensores();
                chipSensorEstado.setText("En espera");
                chipSensorEstado.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#22263A")));
                switchAuto.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#475569")));
            }
        });

        btnReportar.setOnClickListener(v -> {
            String hora = obtenerHoraActual();
            bacheViewModel.agregarBache(new Bache("Manual – Botón de Pánico", hora, "En espera..."));
            Toast.makeText(requireContext(), "Enviando bache manual...", Toast.LENGTH_SHORT).show();
            enviarMqtt(9.5f, "MANUAL", "BACHE");
        });
    }

    private void observarViewModel() {
        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), listaBaches ->
                tvContadorBaches.setText(String.valueOf(listaBaches.size()))
        );
    }

    private void activarSensores() {
        if (acelerometro != null) sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME);
        if (giroscopio != null) sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_GAME);
        layoutPlaceholder.setVisibility(View.GONE);
    }

    private void desactivarSensores() {
        sensorManager.unregisterListener(this);
        layoutPlaceholder.setVisibility(View.VISIBLE);
        bufferZ.clear(); // Limpiar el buffer si se pausa
        primeraLectura = true;
    }

    private void enviarMqtt(float severidadReal, String origen, String tipoEvento) {
        new Thread(() -> {
            String clientId = MqttClient.generateClientId();
            try {
                MqttClient mqttClient = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(MQTT_USER);
                options.setPassword(MQTT_PASS.toCharArray());
                options.setCleanSession(true);
                mqttClient.connect(options);

                String payload = "{\"latitud\": " + latitudActual + ", \"longitud\": " + longitudActual +
                        ", \"severidad\": " + severidadReal + ", \"tipo_evento\": \"" + tipoEvento +
                        "\", \"dispositivo\": \"app_" + origen + "\"}";

                MqttMessage message = new MqttMessage(payload.getBytes());git
                message.setQos(1);
                mqttClient.publish(MQTT_TOPIC, message);
                mqttClient.disconnect();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvMqttEstado.setText("Enviado ✓");
                        viewMqttDot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34D399")));
                        new Handler(Looper.getMainLooper()).postDelayed(() -> tvMqttEstado.setText("SSL Configurado"), 3000);
                    });
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISO_LOCATION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarActualizacionesGPS();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (switchAuto.isChecked()) activarSensores();
    }

    @Override
    public void onPause() {
        super.onPause();
        desactivarSensores();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        locationManager.removeUpdates(this);
    }

    private String obtenerHoraActual() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    public static MonitoreoFragment newInstance() {
        return new MonitoreoFragment();
    }

}