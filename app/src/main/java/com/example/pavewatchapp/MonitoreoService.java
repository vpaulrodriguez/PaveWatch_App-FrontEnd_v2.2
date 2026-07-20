package com.example.pavewatchapp;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class MonitoreoService extends Service implements SensorEventListener, LocationListener {

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

    @Override
    public void onCreate() {
        super.onCreate();
        inicializarSensores();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Notificación persistente
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notificacion = new NotificationCompat.Builder(this, "CanalMonitoreo")
                .setContentTitle("PaveWatch Activo")
                .setContentText("Detectando baches en segundo plano...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notificacion);

        // 2. Activar Sensores y GPS
        activarSensoresYGPS();

        return START_STICKY;
    }

    private void inicializarSensores() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    private void activarSensoresYGPS() {
        if (acelerometro != null) sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_NORMAL);
        if (giroscopio != null) sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_NORMAL);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            boolean gpsHabilitado = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean redHabilitada = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            // Optimización: Cada 3 segundos en lugar de 1
            if (gpsHabilitado) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 2f, this);
            if (redHabilitada) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 2f, this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationManager != null) locationManager.removeUpdates(this);
        bufferZ.clear();
        primeraLectura = true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- LÓGICA DE SENSORES Y GPS ---

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

        // Transmitir datos de GPS a la UI (Si está abierta)
        Intent intent = new Intent("UPDATE_GPS");
        intent.putExtra("lat", latitudActual);
        intent.putExtra("lon", longitudActual);
        intent.putExtra("vel", velocidadActualMps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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

            transmitirSensoresUI();

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            giroY = event.values[1];
        }
    }

    private void transmitirSensoresUI() {
        // Transmitir datos para la gráfica (Si la UI está abierta)
        Intent intent = new Intent("UPDATE_SENSORES");
        intent.putExtra("netZ", netZ);
        intent.putExtra("giroY", giroY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        String payload = String.format(Locale.US,
                "{\"latitud\": %f, \"longitud\": %f, \"severidad\": %f, \"tipo_evento\": \"%s\", \"dispositivo\": \"app_AUTOMATICO\"}",
                latitudActual, longitudActual, fuerza, tipoMqtt);

        MqttManager.getInstance().publicarMensaje(this, payload, new MqttManager.MqttCallback() {
            @Override
            public void onExito() {
                LocalBroadcastManager.getInstance(MonitoreoService.this).sendBroadcast(new Intent("UPDATE_MQTT_EXITO"));
            }
            @Override
            public void onError(String mensaje) {}
        });

        // Transmitir evento a la UI para agregarlo al ViewModel si la app está visible
        Intent intent = new Intent("EVENTO_BACHE_DETECTADO");
        intent.putExtra("titulo", tituloUI);
        intent.putExtra("hora", hora);
        intent.putExtra("lat", latitudActual);
        intent.putExtra("lon", longitudActual);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}