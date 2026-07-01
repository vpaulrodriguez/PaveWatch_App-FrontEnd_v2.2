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

    private boolean primeraLectura = true;

    // --- ALGORITMOS ---
    private static final int BUFFER_SIZE = 100;
    private final LinkedList<Float> bufferZ = new LinkedList<>();
    private static final float UMBRAL_BACHE_BASE = 4.5f;
    private static final float TROCHA_VARIANCE_THRESHOLD = 8.0f;
    private boolean esPavimentado = true;
    private float ruidoMotorActual = 0f;

    private long lastUiUpdateTime = 0L;
    private static final long UI_UPDATE_INTERVAL_MS = 100L;

    private static final float VELOCIDAD_MINIMA_MPS = 4.16f;
    private static final long TIEMPO_GRACIA_ESQUINA_MS = 20000L;
    private int estadoConduccion = 0;
    private long tiempoUltimaAltaVelocidad = 0L;
    private float velocidadActualMps = 0f;

    private static final float UMBRAL_EVASION = 3.5f;
    private static final long TIEMPO_MAX_RETORNO_MS = 3500L;
    private int estadoEvasion = 0;
    private float direccionPrimerVolantazo = 0f;
    private long tiempoPrimerVolantazo = 0L;

    private static final long COOLDOWN_REDUNDANCIA_MS = 3000L;
    private long ultimoEventoDetectadoTiempo = 0L;

    // --- GPS ---
    private LocationManager locationManager;
    private double latitudActual = 0.0;
    private double longitudActual = 0.0;
    private static final int PERMISO_LOCATION_CODE = 100;

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

            if (gpsHabilitado) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
            if (redHabilitada) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 2f, this);

            if (!gpsHabilitado && !redHabilitada) tvGpsEstado.setText("GPS Apagado en celular");
            else tvGpsEstado.setText("Buscando señal...");
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitudActual = location.getLatitude();
        longitudActual = location.getLongitude();
        velocidadActualMps = location.hasSpeed() ? location.getSpeed() : 0f;
        long tiempoActual = System.currentTimeMillis();

        if (velocidadActualMps >= VELOCIDAD_MINIMA_MPS) {
            estadoConduccion = 1;
            tiempoUltimaAltaVelocidad = tiempoActual;
        } else if (estadoConduccion == 1 || estadoConduccion == 2) {
            if (tiempoActual - tiempoUltimaAltaVelocidad <= TIEMPO_GRACIA_ESQUINA_MS) estadoConduccion = 2;
            else estadoConduccion = 0;
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                String velKmH = String.format(Locale.getDefault(), "%.1f km/h", velocidadActualMps * 3.6);
                tvGpsEstado.setText("Conectado | " + velKmH);
                tvCoordenadas.setText(String.format(Locale.getDefault(), "%.4f° %.4f°", latitudActual, longitudActual));
            });
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long tiempoActual = System.currentTimeMillis();
            float rawX = event.values[0];
            float rawZ = event.values[2];

            if (primeraLectura) {
                gravityX = rawX;
                gravityZ = rawZ;
                primeraLectura = false;
            }

            gravityX = ALPHA * gravityX + (1 - ALPHA) * rawX;
            gravityZ = ALPHA * gravityZ + (1 - ALPHA) * rawZ;

            float netX = rawX - gravityX;
            netZ = rawZ - gravityZ;

            bufferZ.add(netZ);
            if (bufferZ.size() > BUFFER_SIZE) bufferZ.removeFirst();
            if (bufferZ.size() == BUFFER_SIZE) analizarTerrenoYVibracion();

            if (tiempoActual - lastUiUpdateTime > UI_UPDATE_INTERVAL_MS) {
                actualizarGraficaUI();
                lastUiUpdateTime = tiempoActual;
            }

            actualizarChipEstado("Analizando", "#065F46");

            float umbralDinamicoActual = UMBRAL_BACHE_BASE + (ruidoMotorActual * 1.5f);
            float fuerzaZ = Math.abs(netZ);

            if (fuerzaZ > umbralDinamicoActual && (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS)) {
                ultimoEventoDetectadoTiempo = tiempoActual;
                estadoEvasion = 0;
                registrarEvento(fuerzaZ, "Automático – Bache", "BACHE");
            }

            if (estadoEvasion == 1 && (tiempoActual - tiempoPrimerVolantazo > TIEMPO_MAX_RETORNO_MS)) {
                estadoEvasion = 0;
            }

            if (Math.abs(netX) > UMBRAL_EVASION) {
                if (estadoEvasion == 0) {
                    estadoEvasion = 1;
                    tiempoPrimerVolantazo = tiempoActual;
                    direccionPrimerVolantazo = Math.signum(netX);
                } else if (estadoEvasion == 1) {
                    if (Math.signum(netX) == -direccionPrimerVolantazo) {
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

    private void analizarTerrenoYVibracion() {
        float sum = 0f;
        for (float val : bufferZ) sum += val;
        float mean = sum / BUFFER_SIZE;

        float sumSq = 0f;
        for (float val : bufferZ) sumSq += (val - mean) * (val - mean);
        float variance = sumSq / BUFFER_SIZE;

        ruidoMotorActual = (float) Math.sqrt(variance);
        esPavimentado = variance < TROCHA_VARIANCE_THRESHOLD;
    }

    private void registrarEvento(float fuerza, String tituloUI, String tipoMqtt) {
        String hora = obtenerHoraActual();
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), tituloUI + " detectado", Toast.LENGTH_SHORT).show();
                bacheViewModel.agregarBache(new Bache(tituloUI, hora, "Enviado a MQTT", latitudActual, longitudActual));
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
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Se requiere permiso de ubicación", Toast.LENGTH_LONG).show();
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISO_LOCATION_CODE);
                    switchAuto.setChecked(false);
                    return;
                }
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
            bacheViewModel.agregarBache(new Bache("Manual – Botón de Pánico", hora, "En espera...", latitudActual, longitudActual));
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
        if (acelerometro != null) sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_NORMAL);
        if (giroscopio != null) sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_NORMAL);
        layoutPlaceholder.setVisibility(View.GONE);
    }

    private void desactivarSensores() {
        sensorManager.unregisterListener(this);
        layoutPlaceholder.setVisibility(View.VISIBLE);
        bufferZ.clear();
        primeraLectura = true;
    }

    private void enviarMqtt(float severidadReal, String origen, String tipoEvento) {
        String payload = String.format(Locale.US,
                "{\"latitud\": %f, \"longitud\": %f, \"severidad\": %f, \"tipo_evento\": \"%s\", \"dispositivo\": \"app_%s\"}",
                latitudActual, longitudActual, severidadReal, tipoEvento, origen);

        // USAMOS LA NUEVA CLASE MANAGER
        MqttManager.getInstance().publicarMensaje(requireContext(), payload, new MqttManager.MqttCallback() {
            @Override
            public void onExito() {
                if (getActivity() != null) {
                    tvMqttEstado.setText("Enviado ✓");
                    viewMqttDot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34D399")));
                    new Handler(Looper.getMainLooper()).postDelayed(() -> tvMqttEstado.setText("SSL Configurado"), 3000);
                }
            }

            @Override
            public void onError(String mensaje) {
                // Errores ya manejados por el Toast genérico del Manager
            }
        });
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