package com.example.pavewatchapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MqttManager {

    private static MqttManager instance;
    private final ExecutorService executor;

    // Credenciales de HiveMQ
    private static final String BROKER_URL = "ssl://118c4eb634124e6a914d87e18ad2ea02.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_USER = "admin";
    private static final String MQTT_PASS = "Admin123";
    private static final String MQTT_TOPIC = "pavewatch/alertas";

    // Interfaz para avisar a los Fragmentos si el envío fue exitoso
    public interface MqttCallback {
        void onExito();
        void onError(String mensaje);
    }

    private MqttManager() {
        // Un solo hilo dedicado para no saturar la memoria
        executor = Executors.newSingleThreadExecutor();
    }

    // Patrón Singleton
    public static synchronized MqttManager getInstance() {
        if (instance == null) {
            instance = new MqttManager();
        }
        return instance;
    }

    public void publicarMensaje(Context context, String jsonPayload, MqttCallback callback) {
        executor.execute(() -> {
            try {
                String clientId = MqttClient.generateClientId();
                MqttClient mqttClient = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());

                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(MQTT_USER);
                options.setPassword(MQTT_PASS.toCharArray());
                options.setCleanSession(true);

                mqttClient.connect(options);

                MqttMessage message = new MqttMessage(jsonPayload.getBytes());
                message.setQos(1);

                mqttClient.publish(MQTT_TOPIC, message);
                mqttClient.disconnect();

                // Avisar éxito en el hilo principal
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onExito();
                });

            } catch (MqttException e) {
                e.printStackTrace();
                // Avisar error en el hilo principal
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                    if (context != null) {
                        Toast.makeText(context, "Fallo MQTT: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}