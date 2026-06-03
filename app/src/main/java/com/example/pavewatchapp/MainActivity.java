package com.example.pavewatchapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    // --- SENSORES Y FILTROS ---
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float ALPHA = 0.8f;
    private float gravityX = 0;
    private float gravityZ = 0;

    // --- CALIBRACIÓN DE BACHES ---
    private static final float UMBRAL_BACHE = 5.5f;
    private static final long COOLDOWN_REDUNDANCIA_MS = 3000;
    private long ultimoEventoDetectadoTiempo = 0;

    // --- VARIABLES DE MANIOBRA EVASIVA (Eje X) ---
    private static final float UMBRAL_EVASION = 4.0f; // Aceleración lateral para considerarlo volantazo
    private int estadoEvasion = 0; // 0: Normal, 1: Primer volantazo detectado
    private float direccionPrimerVolantazo = 0; // +1 o -1
    private long tiempoPrimerVolantazo = 0;
    private static final long TIEMPO_MAX_REGRESO_MS = 2500; // Máximo 2.5 segs para regresar al carril

    // --- VARIABLES GPS ---
    private LocationManager locationManager;
    private double latitudActual = 0.0;
    private double longitudActual = 0.0;
    private static final int PERMISO_LOCATION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar GPS
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        pedirPermisosGPS();

        // Inicializar Sensores
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Botón Manual
        Button btnReportar = findViewById(R.id.btnReportar);
        btnReportar.setOnClickListener(v -> {
            Toast.makeText(this, "Enviando bache MANUAL...", Toast.LENGTH_SHORT).show();
            enviarMqtt(9.5f, "MANUAL", "BACHE");
        });
    }

    // --- GESTIÓN DE BATERÍA ---
    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    // --- LÓGICA (Baches + Evasión) ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long tiempoActual = System.currentTimeMillis();

            // Valores crudos
            float rawX = event.values[0]; // Lateral (Evasión)
            float rawZ = event.values[2]; // Vertical (Bache)

            // Filtros Paso Alto para quitar la gravedad
            gravityX = ALPHA * gravityX + (1 - ALPHA) * rawX;
            gravityZ = ALPHA * gravityZ + (1 - ALPHA) * rawZ;

            float netX = rawX - gravityX;
            float netZ = rawZ - gravityZ;

            // 1. DETECCIÓN DE BACHE VERTICAL
            float fuerzaZ = Math.abs(netZ);
            if (fuerzaZ > UMBRAL_BACHE && (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS)) {
                ultimoEventoDetectadoTiempo = tiempoActual;
                estadoEvasion = 0; // Reseteamos evasión por si acaso

                Toast.makeText(this, "¡BACHE! Fuerza: " + String.format("%.1f", fuerzaZ), Toast.LENGTH_SHORT).show();
                enviarMqtt(fuerzaZ, "AUTOMATICO", "BACHE");
            }

            // 2. DETECCIÓN DE MANIOBRA PELIGROSA O EVASIVA (Máquina de Estados)
            // Timeout de evasión: Si pasó mucho tiempo desde el primer volantazo, cancelamos.
            if (estadoEvasion == 1 && (tiempoActual - tiempoPrimerVolantazo > TIEMPO_MAX_REGRESO_MS)) {
                estadoEvasion = 0;
            }

            if (Math.abs(netX) > UMBRAL_EVASION) {
                if (estadoEvasion == 0) {
                    // Detectamos el primer volantazo (salida del carril)
                    estadoEvasion = 1;
                    tiempoPrimerVolantazo = tiempoActual;
                    direccionPrimerVolantazo = Math.signum(netX); // Guarda si fue izquierda o derecha
                }
                else if (estadoEvasion == 1) {
                    // Estamos esperando el regreso. Verificamos si la dirección es contraria.
                    if (Math.signum(netX) != direccionPrimerVolantazo) {
                        // Fue una maniobra peligrosa.
                        if (tiempoActual - ultimoEventoDetectadoTiempo > COOLDOWN_REDUNDANCIA_MS) {
                            ultimoEventoDetectadoTiempo = tiempoActual;

                            Toast.makeText(this, "¡Realizó una maniobra peligrosa! Tenga cuidado", Toast.LENGTH_LONG).show();
                            // Enviamos un evento especial a MQTT
                            enviarMqtt(Math.abs(netX), "AUTOMATICO", "EVASION");
                        }
                        estadoEvasion = 0; // Reiniciar ciclo
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // --- LÓGICA DEL GPS ---
    private void pedirPermisosGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISO_LOCATION_CODE);
        } else {
            // Actualiza la ubicación cada 2 segundos o cada 5 metros
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, this);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // Actualizamos las variables globales con tu ubicación en tiempo real
        latitudActual = location.getLatitude();
        longitudActual = location.getLongitude();
    }

    // --- ENVÍO MQTT AL BACKEND ---
    private void enviarMqtt(float severidadReal, String origen, String tipoEvento) {
        new Thread(() -> {
            String brokerUrl = "ssl://118c4eb634124e6a914d87e18ad2ea02.s1.eu.hivemq.cloud:8883";
            String clientId = MqttClient.generateClientId();

            try {
                MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName("admin");
                options.setPassword("Admin123".toCharArray());
                options.setCleanSession(true);

                mqttClient.connect(options);

                // Armamos el JSON inyectando la lat/lon reales del GPS y el tipo de evento
                String payload = "{\"latitud\": " + latitudActual + ", \"longitud\": " + longitudActual +
                        ", \"severidad\": " + severidadReal + ", \"tipo_evento\": \"" + tipoEvento +
                        "\", \"dispositivo\": \"app_" + origen + "\"}";

                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);

                mqttClient.publish("pavewatch/alertas", message);
                mqttClient.disconnect();

            } catch (MqttException e) {
                e.printStackTrace();
            }
        }).start();
    }
}