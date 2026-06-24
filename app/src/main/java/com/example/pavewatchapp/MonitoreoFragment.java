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
import java.util.List;
import java.util.Locale;

/**
 * MonitoreoFragment — Consola de control en tiempo real.
 *
 * Principios POO aplicados:
 *  - Herencia: extiende Fragment, implementa SensorEventListener y LocationListener.
 *  - Interfaces: SensorEventListener y LocationListener como contratos de comportamiento.
 *  - Encapsulamiento: todos los campos son privados.
 *  - Responsabilidad única: delega el envío MQTT a un método separado.
 *
 * Bugs corregidos:
 *  #1 — La gráfica ya no se superpone al layout: se añade al FrameLayout correctamente.
 *  #2 — Switch con colores y mensajes coherentes (misma capitalización).
 *  #3 — Botón manual escribe en el ViewModel → HistorialFragment se actualiza.
 */
public class MonitoreoFragment extends Fragment
        implements SensorEventListener, LocationListener {

    // ── ViewModel compartido ───────────────────────────────────────
    private BacheViewModel bacheViewModel;

    // ================================================================
    // SENSORES Y FILTRO PASO ALTO
    // ================================================================
    private SensorManager sensorManager;
    private Sensor        acelerometro;
    private Sensor        giroscopio;

    // Filtro paso alto — elimina la componente de gravedad
    private static final float ALPHA     = 0.8f;
    private float gravityX = 0f;
    private float gravityZ = 0f;

    // Últimos valores filtrados (para la gráfica)
    private float netZ  = 0f;
    private float giroY = 0f;

    // ================================================================
    // CALIBRACIÓN — mismos valores del código original
    // ================================================================
    private static final float UMBRAL_BACHE            = 5.5f;
    private static final long  COOLDOWN_REDUNDANCIA_MS = 3000L;
    private long ultimoEventoDetectadoTiempo            = 0L;

    // ================================================================
    // MÁQUINA DE ESTADOS — MANIOBRA EVASIVA
    // ================================================================
    private static final float UMBRAL_EVASION          = 4.0f;
    private static final long  TIEMPO_MAX_REGRESO_MS   = 2500L;
    private int   estadoEvasion              = 0;
    private float direccionPrimerVolantazo   = 0f;
    private long  tiempoPrimerVolantazo      = 0L;

    // ================================================================
    // GPS
    // ================================================================
    private LocationManager locationManager;
    private double latitudActual   = 0.0;
    private double longitudActual  = 0.0;
    private static final int PERMISO_LOCATION_CODE = 100;

    // ================================================================
    // MQTT
    // ================================================================
    private static final String BROKER_URL  = "ssl://118c4eb634124e6a914d87e18ad2ea02.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_USER   = "admin";
    private static final String MQTT_PASS   = "Admin123";
    private static final String MQTT_TOPIC  = "pavewatch/alertas";

    // ================================================================
    // GRÁFICA
    // ================================================================
    private LineChart           lineChart;
    private final List<Entry>   entradasAcel = new ArrayList<>();
    private final List<Entry>   entradasGiro = new ArrayList<>();
    private float               indiceGrafica = 0f;
    private static final int    MAX_PUNTOS    = 60;

    // ================================================================
    // VIEWS
    // ================================================================
    private SwitchCompat   switchAuto;
    private MaterialButton btnReportar;
    private TextView       tvAcelZ;
    private TextView       tvGiroY;
    private TextView       tvContadorBaches;
    private TextView       tvGpsEstado;
    private TextView       tvCoordenadas;
    private TextView       tvMqttEstado;
    private TextView       tvBrokerUrl;
    private Chip           chipSensorEstado;
    private FrameLayout    layoutGrafica;
    private View           layoutPlaceholder;
    private View           viewMqttDot;

    // ================================================================
    // CICLO DE VIDA
    // ================================================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_monitoreo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bacheViewModel = new ViewModelProvider(requireActivity())
                .get(BacheViewModel.class);

        enlazarViews(view);
        inicializarGPS();
        inicializarSensores();
        configurarGrafica();      // BUG FIX #1: gráfica correctamente inicializada
        configurarListeners();    // BUG FIX #2 y #3
        observarViewModel();
    }

    // ================================================================
    // INICIALIZACIÓN
    // ================================================================

    private void enlazarViews(View view) {
        switchAuto        = view.findViewById(R.id.switch1);
        btnReportar       = view.findViewById(R.id.btnReportar);
        tvAcelZ           = view.findViewById(R.id.tvAcelZ);
        tvGiroY           = view.findViewById(R.id.tvGiroY);
        tvContadorBaches  = view.findViewById(R.id.tvContadorBaches);
        tvGpsEstado       = view.findViewById(R.id.tvGpsEstado);
        tvCoordenadas     = view.findViewById(R.id.tvCoordenadas);
        tvMqttEstado      = view.findViewById(R.id.tvMqttEstado);
        tvBrokerUrl       = view.findViewById(R.id.tvBrokerUrl);
        chipSensorEstado  = view.findViewById(R.id.chipSensorEstado);
        layoutGrafica     = view.findViewById(R.id.layoutGrafica);
        layoutPlaceholder = view.findViewById(R.id.layoutGraficaPlaceholder);
        viewMqttDot       = view.findViewById(R.id.viewMqttDot);

        tvBrokerUrl.setText("hivemq.cloud:8883");
        tvMqttEstado.setText("SSL Configurado");
    }

    private void inicializarGPS() {
        locationManager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISO_LOCATION_CODE
            );
        } else {
            iniciarActualizacionesGPS();
        }
    }

    private void iniciarActualizacionesGPS() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L, 5f, this
            );
            tvGpsEstado.setText("Buscando señal...");
        }
    }

    private void inicializarSensores() {
        sensorManager = (SensorManager) requireContext()
                .getSystemService(Context.SENSOR_SERVICE);
        acelerometro  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        giroscopio    = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    // ================================================================
    // GRÁFICA — MPAndroidChart
    // BUG FIX #1: se agrega al FrameLayout sin superponerse al Switch
    // ================================================================

    private void configurarGrafica() {
        lineChart = new LineChart(requireContext());

        // Añadir la gráfica al FrameLayout contenedor del layout XML
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        layoutGrafica.addView(lineChart, params);

        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.setDrawGridBackground(false);
        lineChart.setDrawBorders(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setDoubleTapToZoomEnabled(false);
        lineChart.setTouchEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setScaleEnabled(false);
        lineChart.setNoDataText("Activa la detección para ver datos");
        lineChart.setNoDataTextColor(Color.parseColor("#475569"));

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#1E2333"));
        xAxis.setAxisLineColor(Color.parseColor("#1E2333"));
        xAxis.setDrawLabels(false);
        xAxis.setDrawAxisLine(false);

        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#1E2333"));
        lineChart.getAxisLeft().setAxisLineColor(Color.parseColor("#1E2333"));
        lineChart.getAxisLeft().setTextColor(Color.parseColor("#475569"));
        lineChart.getAxisLeft().setTextSize(9f);
        lineChart.getAxisLeft().setAxisMinimum(-15f);
        lineChart.getAxisLeft().setAxisMaximum(15f);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void actualizarGrafica(float valorAcel, float valorGiro) {
        entradasAcel.add(new Entry(indiceGrafica, valorAcel));
        entradasGiro.add(new Entry(indiceGrafica, valorGiro));
        indiceGrafica++;

        if (entradasAcel.size() > MAX_PUNTOS) {
            entradasAcel.remove(0);
            entradasGiro.remove(0);
        }

        LineDataSet dataSetAcel = new LineDataSet(entradasAcel, "Acel Z");
        dataSetAcel.setColor(Color.parseColor("#F59E0B"));
        dataSetAcel.setLineWidth(1.5f);
        dataSetAcel.setDrawCircles(false);
        dataSetAcel.setDrawValues(false);
        dataSetAcel.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSetAcel.setCubicIntensity(0.2f);

        LineDataSet dataSetGiro = new LineDataSet(entradasGiro, "Giro Y");
        dataSetGiro.setColor(Color.parseColor("#22D3EE"));
        dataSetGiro.setLineWidth(1f);
        dataSetGiro.setDrawCircles(false);
        dataSetGiro.setDrawValues(false);
        dataSetGiro.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSetGiro.setCubicIntensity(0.2f);

        lineChart.setData(new LineData(dataSetAcel, dataSetGiro));
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

        if (entradasAcel.size() >= MAX_PUNTOS) {
            lineChart.setVisibleXRangeMaximum(MAX_PUNTOS);
            lineChart.moveViewToX(lineChart.getData().getEntryCount());
        }
    }

    // ================================================================
    // LISTENERS
    // BUG FIX #2: Switch con colores coherentes y mensajes uniformes
    // BUG FIX #3: Botón manual escribe en el ViewModel
    // ================================================================

    private void configurarListeners() {

        // ── Switch de detección automática ────────────────────────
        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                activarSensores();

                // BUG FIX #2a: chip verde cuando está activo
                chipSensorEstado.setText("Activo");
                chipSensorEstado.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#065F46")));
                chipSensorEstado.setTextColor(Color.parseColor("#34D399"));

                // BUG FIX #2b: switch con color ámbar cuando está ON
                switchAuto.setThumbTintList(
                        ColorStateList.valueOf(Color.parseColor("#F59E0B")));
                switchAuto.setTrackTintList(
                        ColorStateList.valueOf(Color.parseColor("#251F0B")));

                // BUG FIX #2c: mensaje uniforme con la misma capitalización
                Toast.makeText(requireContext(),
                        "Detección automática: Activada", Toast.LENGTH_SHORT).show();

            } else {
                desactivarSensores();

                // Chip gris cuando está en espera
                chipSensorEstado.setText("En espera");
                chipSensorEstado.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#22263A")));
                chipSensorEstado.setTextColor(Color.parseColor("#94A3B8"));

                // Switch con color apagado cuando está OFF
                switchAuto.setThumbTintList(
                        ColorStateList.valueOf(Color.parseColor("#475569")));
                switchAuto.setTrackTintList(
                        ColorStateList.valueOf(Color.parseColor("#1E2333")));

                // BUG FIX #2c: mensaje uniforme
                Toast.makeText(requireContext(),
                        "Detección automática: Pausada", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Botón de reporte manual ────────────────────────────────
        btnReportar.setOnClickListener(v -> {
            String hora = obtenerHoraActual();

            // BUG FIX #3: primero registrar como "En espera" en el ViewModel
            Bache bachePendiente = new Bache(
                    "Manual – Botón de Pánico",
                    hora,
                    "En espera..."
            );
            bacheViewModel.agregarBache(bachePendiente);

            Toast.makeText(requireContext(),
                    "Enviando bache manual...", Toast.LENGTH_SHORT).show();

            // Enviar por MQTT (en hilo secundario)
            enviarMqtt(9.5f, "MANUAL", "BACHE");
        });
    }

    /**
     * Observa el LiveData para mantener el contador de baches actualizado.
     * BUG FIX #3: el contador ahora refleja correctamente los reportes manuales.
     */
    private void observarViewModel() {
        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), listaBaches -> {
            tvContadorBaches.setText(String.valueOf(listaBaches.size()));
        });
    }

    // ================================================================
    // SENSORES
    // ================================================================

    private void activarSensores() {
        if (acelerometro != null) {
            sensorManager.registerListener(this, acelerometro,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        if (giroscopio != null) {
            sensorManager.registerListener(this, giroscopio,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        layoutPlaceholder.setVisibility(View.GONE);
    }

    private void desactivarSensores() {
        sensorManager.unregisterListener(this);
        layoutPlaceholder.setVisibility(View.VISIBLE);
    }

    // ================================================================
    // SensorEventListener
    // Lógica exacta del código original con filtro paso alto
    // ================================================================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long tiempoActual = System.currentTimeMillis();

            float rawX = event.values[0];
            float rawZ = event.values[2];

            // Filtro paso alto — quita la gravedad
            gravityX = ALPHA * gravityX + (1 - ALPHA) * rawX;
            gravityZ = ALPHA * gravityZ + (1 - ALPHA) * rawZ;

            float filtradoX = rawX - gravityX;
            netZ            = rawZ - gravityZ;

            // Actualizar UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    tvAcelZ.setText(String.format(Locale.getDefault(), "%.2f", netZ));
                    actualizarGrafica(netZ, giroY);
                });
            }

            // ── 1. DETECCIÓN DE BACHE VERTICAL ──────────────────────
            float fuerzaZ = Math.abs(netZ);
            if (fuerzaZ > UMBRAL_BACHE &&
                (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS)) {

                ultimoEventoDetectadoTiempo = tiempoActual;
                estadoEvasion = 0;

                String hora = obtenerHoraActual();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "¡Bache detectado! Fuerza: " +
                                String.format(Locale.getDefault(), "%.1f", fuerzaZ),
                                Toast.LENGTH_SHORT).show();

                        // Registrar en ViewModel → actualiza Historial automáticamente
                        bacheViewModel.agregarBache(new Bache(
                                "Automático – Acelerómetro",
                                hora,
                                "Enviado a MQTT"
                        ));
                    });
                }
                enviarMqtt(fuerzaZ, "AUTOMATICO", "BACHE");
            }

            // ── 2. MÁQUINA DE ESTADOS — MANIOBRA EVASIVA ────────────
            if (estadoEvasion == 1 &&
                (tiempoActual - tiempoPrimerVolantazo > TIEMPO_MAX_REGRESO_MS)) {
                estadoEvasion = 0;
            }

            if (Math.abs(filtradoX) > UMBRAL_EVASION) {
                if (estadoEvasion == 0) {
                    estadoEvasion            = 1;
                    tiempoPrimerVolantazo    = tiempoActual;
                    direccionPrimerVolantazo = Math.signum(filtradoX);

                } else if (estadoEvasion == 1 &&
                           Math.signum(filtradoX) != direccionPrimerVolantazo) {

                    if (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS) {
                        ultimoEventoDetectadoTiempo = tiempoActual;
                        String hora = obtenerHoraActual();
                        float fuerzaX = Math.abs(filtradoX);

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                        "¡Maniobra peligrosa detectada! Tenga cuidado",
                                        Toast.LENGTH_LONG).show();

                                bacheViewModel.agregarBache(new Bache(
                                        "Automático – Maniobra Evasiva",
                                        hora,
                                        "Enviado a MQTT"
                                ));
                            });
                        }
                        enviarMqtt(fuerzaX, "AUTOMATICO", "EVASION");
                    }
                    estadoEvasion = 0;
                }
            }

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            giroY = event.values[1];
            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        tvGiroY.setText(String.format(Locale.getDefault(), "%.2f", giroY))
                );
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No se requiere acción
    }

    // ================================================================
    // LocationListener — GPS en tiempo real
    // ================================================================

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitudActual  = location.getLatitude();
        longitudActual = location.getLongitude();

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvGpsEstado.setText("Conectado");
                tvCoordenadas.setText(
                        String.format(Locale.getDefault(),
                                "%.4f° %.4f°", latitudActual, longitudActual)
                );
            });
        }
    }

    // ================================================================
    // MQTT — payload exacto del backend
    // ================================================================

    /**
     * Envía el evento al broker HiveMQ en un hilo secundario.
     * Payload: {"latitud":X,"longitud":Y,"severidad":Z,"tipo_evento":"...","dispositivo":"..."}
     */
    private void enviarMqtt(float severidadReal, String origen, String tipoEvento) {
        new Thread(() -> {
            String clientId = MqttClient.generateClientId();
            try {
                MqttClient mqttClient = new MqttClient(
                        BROKER_URL, clientId, new MemoryPersistence());

                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(MQTT_USER);
                options.setPassword(MQTT_PASS.toCharArray());
                options.setCleanSession(true);

                mqttClient.connect(options);

                // Payload exacto que lee el backend
                String payload = "{\"latitud\": " + latitudActual +
                        ", \"longitud\": " + longitudActual +
                        ", \"severidad\": " + severidadReal +
                        ", \"tipo_evento\": \"" + tipoEvento +
                        "\", \"dispositivo\": \"app_" + origen + "\"}";

                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);

                mqttClient.publish(MQTT_TOPIC, message);
                mqttClient.disconnect();

                // Actualizar UI: MQTT enviado exitosamente
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvMqttEstado.setText("Enviado ✓");
                        viewMqttDot.setBackgroundTintList(
                                ColorStateList.valueOf(Color.parseColor("#34D399")));

                        new Handler(Looper.getMainLooper()).postDelayed(() ->
                                tvMqttEstado.setText("SSL Configurado"), 3000);
                    });
                }

            } catch (MqttException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvMqttEstado.setText("Error conexión");
                        viewMqttDot.setBackgroundTintList(
                                ColorStateList.valueOf(Color.parseColor("#EF4444")));
                    });
                }
            }
        }).start();
    }

    // ================================================================
    // PERMISOS
    // ================================================================

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISO_LOCATION_CODE &&
            grantResults.length > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarActualizacionesGPS();
        } else {
            tvGpsEstado.setText("Sin permiso GPS");
            tvCoordenadas.setText("No disponible");
        }
    }

    // ================================================================
    // CICLO DE VIDA
    // ================================================================

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

    // ================================================================
    // UTILIDADES
    // ================================================================

    private String obtenerHoraActual() {
        SimpleDateFormat formato = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return formato.format(new Date());
    }

    public static MonitoreoFragment newInstance() {
        return new MonitoreoFragment();
    }
}
