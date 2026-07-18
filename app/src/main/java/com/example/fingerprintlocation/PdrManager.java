package com.example.fingerprintlocation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * PDR Manager - Xiaomi 13 Pro flat-hold dedicated version
 * * Features:
 * 1. Uses ROTATION_VECTOR (gyroscope + magnetometer fusion), turns extremely fast, no delay.
 * 2. Removed coordinate remapping, designed for "screen up" flat-hold posture.
 */
public class PdrManager implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepDetector;
    private Sensor rotationVectorSensor;
    // Add a new variable to store the old angle
    private float lastAzimuth = 0f;
    // Filtering coefficient (0.0 ~ 1.0), the smaller the smoother, the larger the more sensitive. 0.1~0.2 is an empirical value
    private static final float ALPHA = 0.5f;
    // Data cache
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    // Current phone orientation (radians)
    private volatile float currentAzimuth = 0f;

    // Fixed step length 0.795m
    private static final float STEP_LENGTH_METERS = 0.79f;

    private PdrListener listener;

    public interface PdrListener {
        void onStep(float stepLength, float azimuth);
    }

    public PdrManager(Context context, PdrListener listener) {
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // 1. Pedometer
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        // 2. Rotation vector sensor (fusion sensor)
        // Xiaomi 13 Pro has very good support for this sensor
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (stepDetector == null) Log.e("PdrManager", "No Step Detector found!");
        if (rotationVectorSensor == null) Log.e("PdrManager", "No Rotation Vector Sensor found!");
    }

    public void start() {
        if (sensorManager != null) {
            if (stepDetector != null) {
                sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (rotationVectorSensor != null) {
                // SENSOR_DELAY_GAME (20ms) ensures smoothness
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (listener != null) {
                listener.onStep(STEP_LENGTH_METERS, currentAzimuth);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            float rawAzimuth = orientationAngles[0];

            // Handle the mirror jump logic from -PI to PI
            float diff = rawAzimuth - lastAzimuth;
            if (diff > Math.PI) rawAzimuth -= 2 * Math.PI;
            if (diff < -Math.PI) rawAzimuth += 2 * Math.PI;

            // Smooth filtering update
            currentAzimuth = lastAzimuth + ALPHA * (rawAzimuth - lastAzimuth);

            // Normalize the radian range
            if (currentAzimuth > Math.PI) currentAzimuth -= 2 * Math.PI;
            if (currentAzimuth < -Math.PI) currentAzimuth += 2 * Math.PI;

            lastAzimuth = currentAzimuth;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Xiaomi phones sometimes callback accuracy changes here, usually no need to handle
    }
}