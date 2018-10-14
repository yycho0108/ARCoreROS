package com.example.jamie.arcore_ros;

/* OpenGL Imports */
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/* Android Imports */
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/* ARCore Imports */
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

/* Helper Imports */
import com.example.jamie.arcore_ros.ros.GPSPermissionHelper;
import com.example.jamie.arcore_ros.arcore.BackgroundRenderer;
import com.example.jamie.arcore_ros.arcore.CameraPermissionHelper;
import com.example.jamie.arcore_ros.arcore.DisplayRotationHelper;

/* ROS Imports */
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import com.example.jamie.arcore_ros.ros.SensorPublisher;

import java.io.IOException;

public class MainActivity extends RosActivity implements GLSurfaceView.Renderer {

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private DisplayRotationHelper displayRotationHelper;

    private boolean mUserRequestedInstall = true;
    private Session mSession = null;
    SensorPublisher mPublisher = null;

    /* UI Elements */
    private GLSurfaceView surfaceView = null;
    Button mArButton = null;
    TextView mPoseView = null;

    Pose deviceToPhysical = null;

    public MainActivity() {
        super("Odomobile", "Odomobile");
    }

    @Override
    public void init(NodeMainExecutor n){
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(getRosHostname());
        nodeConfiguration.setMasterUri(getMasterUri());

        mPublisher = new SensorPublisher(this, n);

        //register listeners - camera and other sensors
        mPublisher.registerListeners(this);

        n.execute(mPublisher, nodeConfiguration);
    }

    void initARCore(){
        surfaceView = findViewById(R.id.surfaceView);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        mArButton = findViewById(R.id.arbutton);
        mPoseView = findViewById(R.id.poseView);

        // set up surfaceView
        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        maybeEnableArButton();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initARCore();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);

        } catch (IOException e) {
            Log.e("Surf", "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(mSession);

        try {
            mSession.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = mSession.update();
            Camera camera = frame.getCamera();

            // get tracking pose + show
            if(camera.getTrackingState() == TrackingState.TRACKING){
                Pose pose = camera.getPose();
                Log.i("pose", pose.toString());

                if(deviceToPhysical == null) {
                    // Can be done once after camera permission is granted.
//                    CameraManager cm = getSystemService(CameraManager.class);
//                    int sensorOrientation = Arrays.stream(cm.getCameraIdList()).map((id) -> {
//                        try {
//                            if (cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
//                                    CameraMetadata.LENS_FACING_BACK) {
//                                return cm.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION);
//                            }
//                        } catch (CameraAccessException e) {
//                            throw new RuntimeException(e);
//                        }
//                        return -1;
//                    }).filter((orientation) -> orientation != -1).findFirst().orElse(0);
//                    deviceToPhysical = Pose.makeInterpolated(
//                            Pose.IDENTITY,
//                            Pose.makeRotation(0, 0, -(float) Math.sqrt(0.5), (float) Math.sqrt(0.5)),
//                            sensorOrientation / 90);
                    deviceToPhysical = pose.inverse();
                }

                if(deviceToPhysical != null){
                    //Pose cameraPose = camera.getPose().compose(deviceToPhysical);
                    Pose cameraPose = deviceToPhysical.compose(pose);
                    // Per frame.
                    this.mPublisher.onOdomChanged(
                            cameraPose.getTranslation(),
                            cameraPose.getRotationQuaternion()
                    );
                }

            }

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e("draw", "Exception on the OpenGL thread", t);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();

        // stop ARCore?
        if(mSession != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            mSession.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permission to operate.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }

        // GPS Permission is technically not required?
        if (!GPSPermissionHelper.hasPermissions(this)) {
            GPSPermissionHelper.requestPermissions(this);
            return;
        }

        // Make sure ARCore is installed and up to date.
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        // Success, create the AR session.
                        mSession = new Session(this);
                        break;
                    case INSTALL_REQUESTED:
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        mUserRequestedInstall = false;
                        return;
                }
            }
        }catch (
                UnavailableUserDeclinedInstallationException |
                UnavailableSdkTooOldException |
                UnavailableArcoreNotInstalledException |
                UnavailableApkTooOldException |
                UnavailableDeviceNotCompatibleException e) {
            // Current catch statements.
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "TODO: handle exception " + e, Toast.LENGTH_LONG)
                    .show();
            return;  // mSession is still null.
        }

        if(mSession != null){
            try {
                if(CameraPermissionHelper.hasCameraPermission(this)) {
                    mSession.setCameraTextureName(backgroundRenderer.getTextureId());
                    mSession.resume();

                    if(surfaceView != null) {
                        surfaceView.onResume();
                    }
                    displayRotationHelper.onResume();
                }
            }catch(CameraNotAvailableException e){
                Toast.makeText(this, "TODO: handle exception " + e, Toast.LENGTH_LONG)
                        .show();
                return;
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }

        if (!GPSPermissionHelper.hasPermissions(this)){
            Toast.makeText(this, "GPS has been disabled!", Toast.LENGTH_LONG)
                    .show();
            if (!GPSPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                GPSPermissionHelper.launchPermissionSettings(this);
            }
        }

    }

    void maybeEnableArButton() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Re-query at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeEnableArButton();
                }
            }, 200);
        }
        if (availability.isSupported()) {
            mArButton.setVisibility(View.VISIBLE);
            mArButton.setEnabled(true);
            // indicator on the button.
        } else { // Unsupported or unknown.
            mArButton.setVisibility(View.INVISIBLE);
            mArButton.setEnabled(false);
        }
    }


}
