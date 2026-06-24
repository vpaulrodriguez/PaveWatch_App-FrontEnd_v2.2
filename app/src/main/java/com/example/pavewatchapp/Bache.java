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

    // ── Constructor ────────────────────────────────────────────────
    /**
     * @param tipo   Origen del reporte ("Automático – Acelerómetro", "Manual", etc.)
     * @param hora   Timestamp en formato HH:mm:ss
     * @param estado Estado del envío MQTT ("Enviado a MQTT", "En espera", "Error")
     */
    public Bache(String tipo, String hora, String estado) {
        this.tipo   = tipo;
        this.hora   = hora;
        this.estado = estado;
    }

    // ── Getters públicos ───────────────────────────────────────────
    public String getTipo()   { return tipo;   }
    public String getHora()   { return hora;   }
    public String getEstado() { return estado; }

    /**
     * Representación textual del objeto para depuración.
     */
    @Override
    public String toString() {
        return "Bache{tipo='" + tipo + "', hora='" + hora + "', estado='" + estado + "'}";
    }
}
