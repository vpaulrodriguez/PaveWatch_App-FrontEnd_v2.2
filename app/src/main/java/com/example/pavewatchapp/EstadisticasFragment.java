package com.example.pavewatchapp;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import Network.ApiClient;
import Network.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EstadisticasFragment extends Fragment {
    private BarChart barChart;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_estadisticas, container, false);
        barChart = view.findViewById(R.id.barChartSeveridad);

        configurarAparienciaGrafica();
        obtenerDatosDelBackend();

        return view;
    }

    private void configurarAparienciaGrafica() {
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setTextColor(Color.WHITE);
        barChart.getAxisLeft().setTextSize(12f);
        barChart.getDescription().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setTextSize(10f); // Un poco más pequeño para que quepan palabras largas

        Legend legend = barChart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setTextSize(12f);
    }

    private void obtenerDatosDelBackend() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<Map<String, Long>> call = apiService.getEstadisticasSeveridad();

        call.enqueue(new Callback<Map<String, Long>>() {
            @Override
            public void onResponse(Call<Map<String, Long>> call, Response<Map<String, Long>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cargarDatosEnGrafica(response.body());
                } else {
                    Toast.makeText(getContext(), "Error leyendo datos del backend", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Long>> call, Throwable t) {
                Log.e("Estadisticas", "Error: " + t.getMessage());
                Toast.makeText(getContext(), "No se pudo conectar al servidor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarDatosEnGrafica(Map<String, Long> datosBackend) {
        List<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>(); // Aquí guardaremos los textos (ej: "SIN_ANALIZAR")

        int index = 0; // MPAndroidChart necesita un índice numérico para la posición en X
        for (Map.Entry<String, Long> entry : datosBackend.entrySet()) {
            String clasificacion = entry.getKey();
            float cantidad = entry.getValue().floatValue();

            labels.add(clasificacion);
            entries.add(new BarEntry((float) index, cantidad));
            index++;
        }

        // Le inyectamos los textos al Eje X usando el arreglo 'labels'
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());

        BarDataSet dataSet = new BarDataSet(entries, "Baches por Clasificación IA");
        dataSet.setColor(Color.CYAN);

        barChart.setData(new BarData(dataSet));
        barChart.invalidate(); // Refresca la gráfica
    }
}