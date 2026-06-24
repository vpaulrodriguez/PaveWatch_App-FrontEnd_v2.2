package com.example.pavewatchapp;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador del RecyclerView para el historial de baches.
 *
 * Principios POO aplicados:
 *  - Herencia: extiende RecyclerView.Adapter.
 *  - Clase interna estática (ViewHolder): encapsula las referencias
 *    a los Views de cada ítem para evitar llamadas repetidas a findViewById.
 *  - Encapsulamiento: la lista interna se modifica solo mediante métodos públicos.
 */
public class BacheAdapter extends RecyclerView.Adapter<BacheAdapter.BacheViewHolder> {

    // Lista interna de baches a mostrar
    private List<Bache> listaBaches;

    // ── Colores semánticos para el chip de estado ──────────────────
    private static final String COLOR_ENVIADO  = "#065F46"; // Verde oscuro
    private static final String COLOR_ESPERA   = "#1E3A5F"; // Azul oscuro
    private static final String COLOR_ERROR    = "#7F1D1D"; // Rojo oscuro

    private static final String COLOR_TEXT_ENVIADO = "#34D399"; // Verde claro
    private static final String COLOR_TEXT_ESPERA  = "#93C5FD"; // Azul claro
    private static final String COLOR_TEXT_ERROR   = "#FCA5A5"; // Rojo claro

    // ── Constructor ────────────────────────────────────────────────
    public BacheAdapter() {
        this.listaBaches = new ArrayList<>();
    }

    /**
     * Actualiza la lista completa y notifica al RecyclerView.
     * Se llama desde HistorialFragment cuando el LiveData cambia.
     *
     * @param nuevaLista La lista actualizada de baches
     */
    public void actualizarLista(List<Bache> nuevaLista) {
        this.listaBaches = new ArrayList<>(nuevaLista);
        notifyDataSetChanged();
    }

    // ── RecyclerView.Adapter — métodos obligatorios ────────────────

    @NonNull
    @Override
    public BacheViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Infla el layout de cada fila (item_bache.xml)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bache, parent, false);
        return new BacheViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BacheViewHolder holder, int position) {
        Bache bache = listaBaches.get(position);

        // Mapear datos del modelo a los Views
        holder.tvTipo.setText(bache.getTipo());
        holder.tvHora.setText(bache.getHora());
        holder.chipEstado.setText(bache.getEstado());

        // Aplicar color semántico al chip según el estado
        aplicarColorChip(holder.chipEstado, bache.getEstado());
    }

    @Override
    public int getItemCount() {
        return listaBaches.size();
    }

    /**
     * Aplica color de fondo y texto al chip según el estado del envío.
     * BUG FIX #3: colores diferenciados para cada estado.
     *
     * @param chip   El Chip de Material Design a colorear
     * @param estado El texto del estado ("Enviado", "En espera", "Error")
     */
    private void aplicarColorChip(Chip chip, String estado) {
        String colorFondo;
        String colorTexto;

        if (estado.toLowerCase().contains("enviado")) {
            colorFondo = COLOR_ENVIADO;
            colorTexto = COLOR_TEXT_ENVIADO;
        } else if (estado.toLowerCase().contains("espera") ||
                   estado.toLowerCase().contains("pending")) {
            colorFondo = COLOR_ESPERA;
            colorTexto = COLOR_TEXT_ESPERA;
        } else if (estado.toLowerCase().contains("error") ||
                   estado.toLowerCase().contains("fallo")) {
            colorFondo = COLOR_ERROR;
            colorTexto = COLOR_TEXT_ERROR;
        } else {
            colorFondo = COLOR_ESPERA;
            colorTexto = COLOR_TEXT_ESPERA;
        }

        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(colorFondo)));
        chip.setTextColor(Color.parseColor(colorTexto));
    }

    // ── ViewHolder — clase interna estática ────────────────────────

    /**
     * Patrón ViewHolder: cachea las referencias a los Views de cada ítem
     * para evitar llamadas costosas a findViewById en cada scroll.
     */
    public static class BacheViewHolder extends RecyclerView.ViewHolder {

        final TextView tvTipo;
        final TextView tvHora;
        final Chip     chipEstado;

        public BacheViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTipo    = itemView.findViewById(R.id.tvTipo);
            tvHora    = itemView.findViewById(R.id.tvHora);
            chipEstado = itemView.findViewById(R.id.chipEstado);
        }
    }
}
