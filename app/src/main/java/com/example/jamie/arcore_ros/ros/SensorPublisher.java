/*
 * Copyright (C) 2014 Jamie Cho.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.jamie.arcore_ros.ros;

import android.app.Activity;
import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.ros.concurrent.CancellableLoop;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.ros.node.topic.Publisher;


import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Publisher} {@link NodeMain}.
 */
// TODO : consider filtering barometric altitude and GPS altitude

public class SensorPublisher extends AbstractNodeMain implements
        SensorEventListener {

    // general multi-sensor manager
    private SensorManager mSensorManager;
    private List<Sensor> sensors;

    // manage gps information separately
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private FusedLocationProviderClient mFusedLocationClient;

    // ROS Publishers
    private IMUPublisher imuPublisher;
    private GPSPublisher gpsPublisher;
    private OdomPublisher odomPublisher;

    // IMU data
    private float[] mAcceleration; //linear acceleration
    private float[] mOrientation;
    private float[] mGyroscope; // angular velocity

    // GPS data
    private Location location;
    private final float mSeaPressure = 1020; // mBar @ Boston Logan Airport

    public SensorPublisher(Context mContext, NodeMainExecutor n) {
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        sensors = new ArrayList<>();

        sensors.add(mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)); //TODO: use vanilla Accelerometer data?
        sensors.add(mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
        sensors.add(mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        sensors.add(mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE));
        // hold off on other sensors

        mAcceleration = new float[3];
        mOrientation = new float[4]; //quaternion
        mGyroscope = new float[3];

        location = new Location(""); //probably ok

        // default parameters for Olin College of Engineering
        location.setLatitude(42.2932);
        location.setLongitude(-71.2637);
        location.setAltitude(88);
    }

    /* Sensor Callbacks Begin */
    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean orientationChanged = false;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                // accelerometer minus gravity
                mAcceleration = event.values;
                break;
            case Sensor.TYPE_GYROSCOPE:
                mGyroscope = event.values;
                break;
            case Sensor.TYPE_PRESSURE:
                location.setAltitude(SensorManager.getAltitude(mSeaPressure, event.values[0]));
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                GeomagneticField g = new GeomagneticField((float) location.getLatitude(),
                        (float) location.getLongitude(),
                        (float) location.getAltitude(),
                        System.currentTimeMillis());
                double decl = Math.toRadians(g.getDeclination());

                SensorManager.getQuaternionFromVector(mOrientation, event.values);

                Quaternion q = new Quaternion(mOrientation); //w,x,y,z

                // TODO : verify rectification for magnetic declination
                // Quaternion q1 = Quaternion.fromAxisAngle(new float[]{0,0,1}, (float)(0));
                // Correct for Magnetic Declination and x-y discrepancy
                //q = q.mul(q1);

                //q.normalize();
                mOrientation[0] = q.w;
                mOrientation[1] = q.x;
                mOrientation[2] = q.y;
                mOrientation[3] = q.z;


                //TODO : use accuracy : event.values[4], assumed variance?
                break;
        }
        if (imuPublisher != null) {
            //sometimes there's another application running to grab sensor data
            //before imuPublisher is instantiated
            imuPublisher.update(mAcceleration, mGyroscope, mOrientation);
        }
    }

    /* GPS Callback */
    public void onGPSChanged(Location location) {
        if (location.getAltitude() != 0) {
            this.location = location;
        } else {
            // essentially, use data from barometer
            this.location.setLatitude(location.getLatitude());
            this.location.setLongitude(location.getLongitude());
        }
        //this.location = location;

        if (gpsPublisher != null) {
            gpsPublisher.update(location);
            gpsPublisher.updateCovariance(location.getAccuracy());
        }
    }

    /* Odom Callback */
    public void onOdomChanged(float[] txn, float[] rxn) {
        odomPublisher.update(txn, rxn);
    }

    /* Sensor Accuracy Callback */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO: ADJUST COVARIANCE HERE??
    }
    /* Sensor Callbacks End */

    /* Callbacks Registration */
    public void registerListeners(Activity activity) {
        // register all listeners
        for (Sensor s : sensors) {
            mSensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);

        // create location request
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                location = locationResult.getLastLocation();
                onGPSChanged(location);
            }
        };
    }

    public void unregisterListeners() {
        mSensorManager.unregisterListener(this);
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    /* ROS Related Stuff */
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("android_sensors");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        imuPublisher = new IMUPublisher(connectedNode);
        gpsPublisher = new GPSPublisher(connectedNode);
        odomPublisher = new OdomPublisher(connectedNode);

        // This CancellableLoop will be canceled automatically when the node shuts
        // down.
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void setup() {

            }

            @Override
            protected void loop() throws InterruptedException {
                // basically, keep on publishing if data exists
                imuPublisher.publish();
                gpsPublisher.publish();
                odomPublisher.publish();
                //TODO : implement and check publication flags
            }
        });
    }
}