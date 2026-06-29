package com.example.pavewatchapp;

/**
 * Modelo de datos: representa un evento de bache detectado.
 *
 * Principios POO aplicados:
 *  - Encapsulamiento: todos los campos son privados con getters públicos.
 *  - Inmutabilidad: los campos se asignan solo en el constructor.
 */
public class Bache {

    // ── Campos privados (Encapsulamiento) ──────────────────────────
    private final String tipo;
    private final String hora;
    private final String estado;
    private final double latitud;
    private final double longitud;

    // ── Constructor ────────────────────────────────────────────────
    /**
     * @param tipo      Origen del reporte ("Automático – Acelerómetro", "Manual", etc.)
     * @param hora      Timestamp en formato HH:mm:ss
     * @param estado    Estado del envío MQTT ("Enviado a MQTT", "En espera", "Error")
     * @param latitud   Latitud de la ubicación del evento
     * @param longitud  Longitud de la ubicación del evento
     */
    public Bache(String tipo, String hora, String estado, double latitud, double longitud) {
        this.tipo = tipo;
        this.hora = hora;
        this.estado = estado;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    // ── Getters públicos ───────────────────────────────────────────
    public String getTipo() {
        return tipo;
    }

    public String getHora() {
        return hora;
    }

    public String getEstado() {
        return estado;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    /**
     * Representación textual del objeto para depuración.
     */
    @Override
    public String toString() {
        return "Bache{" +
                "tipo='" + tipo + '\'' +
                ", hora='" + hora + '\'' +
                ", estado='" + estado + '\'' +
                ", latitud=" + latitud +
                ", longitud=" + longitud +
                '}';
    }
}
