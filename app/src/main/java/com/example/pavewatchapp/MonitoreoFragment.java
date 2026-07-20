package com.example.pavewatchapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import java.util.List;
import java.util.Locale;

public class MonitoreoFragment extends Fragment {

    private BacheViewModel bacheViewModel;

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

    private static final int PERMISO_LOCATION_CODE = 100;
    private double latitudActual = 0.0;
    private double longitudActual = 0.0;

    // --- RECEIVERS PARA ESCUCHAR AL SERVICIO ---
    private final BroadcastReceiver datosReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("UPDATE_SENSORES".equals(intent.getAction())) {
                float netZ = intent.getFloatExtra("netZ", 0f);
                float giroY = intent.getFloatExtra("giroY", 0f);
                actualizarGraficaUI(netZ, giroY);
            } else if ("UPDATE_GPS".equals(intent.getAction())) {
                latitudActual = intent.getDoubleExtra("lat", 0.0);
                longitudActual = intent.getDoubleExtra("lon", 0.0);
                float vel = intent.getFloatExtra("vel", 0f);

                String velKmH = String.format(Locale.getDefault(), "%.1f km/h", vel * 3.6);
                tvGpsEstado.setText("Conectado | " + velKmH);
                tvCoordenadas.setText(String.format(Locale.getDefault(), "%.4f° %.4f°", latitudActual, longitudActual));
            } else if ("UPDATE_MQTT_EXITO".equals(intent.getAction())) {
                tvMqttEstado.setText("Enviado ✓");
                viewMqttDot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34D399")));
                new Handler(Looper.getMainLooper()).postDelayed(() -> tvMqttEstado.setText("SSL Configurado"), 3000);
            }
        }
    };

    private final BroadcastReceiver eventoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String titulo = intent.getStringExtra("titulo");
            String hora = intent.getStringExtra("hora");
            double lat = intent.getDoubleExtra("lat", 0.0);
            double lon = intent.getDoubleExtra("lon", 0.0);

            Toast.makeText(requireContext(), titulo + " detectado", Toast.LENGTH_SHORT).show();
            bacheViewModel.agregarBache(new Bache(titulo, hora, "Enviado a MQTT", lat, lon));
        }
    };

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

                // ENCENDEMOS EL SERVICIO EN SEGUNDO PLANO
                Intent serviceIntent = new Intent(requireContext(), MonitoreoService.class);
                ContextCompat.startForegroundService(requireContext(), serviceIntent);

                actualizarChipEstado("Activo", "#065F46");
                switchAuto.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B")));
                layoutPlaceholder.setVisibility(View.GONE);
            } else {

                // APAGAMOS EL SERVICIO EN SEGUNDO PLANO
                Intent serviceIntent = new Intent(requireContext(), MonitoreoService.class);
                requireContext().stopService(serviceIntent);

                chipSensorEstado.setText("En espera");
                chipSensorEstado.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#22263A")));
                switchAuto.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#475569")));
                layoutPlaceholder.setVisibility(View.VISIBLE);
            }
        });

        btnReportar.setOnClickListener(v -> {
            String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            bacheViewModel.agregarBache(new Bache("Manual – Botón de Pánico", hora, "En espera...", latitudActual, longitudActual));
            Toast.makeText(requireContext(), "Enviando bache manual...", Toast.LENGTH_SHORT).show();

            String payload = String.format(Locale.US,
                    "{\"latitud\": %f, \"longitud\": %f, \"severidad\": %f, \"tipo_evento\": \"%s\", \"dispositivo\": \"app_MANUAL\"}",
                    latitudActual, longitudActual, 9.5f, "BACHE");

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
                public void onError(String mensaje) {}
            });
        });
    }

    private void observarViewModel() {
        bacheViewModel.getListaBaches().observe(getViewLifecycleOwner(), listaBaches ->
                tvContadorBaches.setText(String.valueOf(listaBaches.size()))
        );
    }

    private void actualizarChipEstado(String texto, String colorHex) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                chipSensorEstado.setText(texto);
                chipSensorEstado.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(colorHex)));
            });
        }
    }

    private void actualizarGraficaUI(float netZ, float giroY) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvAcelZ.setText(String.format(Locale.getDefault(), "%.2f", netZ));
                tvGiroY.setText(String.format(Locale.getDefault(), "%.2f", giroY));

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

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter datosFilter = new IntentFilter();
        datosFilter.addAction("UPDATE_SENSORES");
        datosFilter.addAction("UPDATE_GPS");
        datosFilter.addAction("UPDATE_MQTT_EXITO");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(datosReceiver, datosFilter);

        IntentFilter eventoFilter = new IntentFilter("EVENTO_BACHE_DETECTADO");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(eventoReceiver, eventoFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(datosReceiver);
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(eventoReceiver);
    }

    public static MonitoreoFragment newInstance() {
        return new MonitoreoFragment();
    }
}