package com.example.ar_core_map;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.location.Location;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    SupportMapFragment mapFragment;
    GLSurfaceView mSurfaceView;
    MainRenderer mainRenderer;
    ArrayList<MyPlace> myPlaces = new ArrayList<>();
    // AR Core ??? ?????? -- ????????? ????????? ????????? ????????????.
    float[] mePos = new float[3];
    Session session;
    Config mConfig;
    float bearing = 0f;

    DisplayListener displayListener = new DisplayListener();
    CallBack callBack = new CallBack();
    LocCallback locCallBack = new LocCallback();

    float mCurrentX, mCurrentY;

    boolean userRequestedInstall = true, firstCheck = true, makeCube = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requstCameraPermission();
        noTitleBar();
        setContentView(R.layout.activity_main);
        myPlaces.add(new MyPlace("?????????",37.49548409999986, 127.02917309999997, Color.YELLOW));
        myPlaces.add(new MyPlace("?????????",37.502498600000095, 127.02056580000001, Color.BLUE));
        myPlaces.add(new MyPlace("????????????",37.50079200000018, 127.0233722999999, Color.RED));

        // ?????? : ?????? : 37.49548409999986, ?????? : 127.02917309999997 ????????? ????????????
        // ?????? : ?????? : 37.502498600000095, ?????? : 127.02056580000001 ?????????1
        // ?????? : ?????? : 37.50079200000018, ?????? : 127.0233722999999    ?????????2
        mapFragment = (SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null)
            displayManager.registerDisplayListener(displayListener, null);

        mapFragment.getMapAsync(this);

        mainRenderer = new MainRenderer(callBack, this);
        settingSurfaceView();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        session.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (session == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                    case INSTALLED:
                        session = new Session(this);
                        Log.d("??????", "ARCroe Session get?????????");
                        break;
                    default:
                        Log.d("??????", "AR Core??? ????????? ?????????");
                        userRequestedInstall = false;
                        break;
                }
            }
        } catch (Exception e) {
        }


        mConfig = new Config(session);
        session.configure(mConfig);
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }



    class CallBack implements MainRenderer.RenderCallBack {

        @Override
        public void preRender() {
            if (mainRenderer.mViewport) {
                Display display = getWindowManager().getDefaultDisplay();
                int displayRotation = display.getRotation();
                mainRenderer.updateSession(session, displayRotation); // ?????????????????? ?????? ?????????
            }

            session.setCameraTextureName(mainRenderer.getTextureId());

            Frame frame = null;

            try {
                frame = session.update();
            } catch (CameraNotAvailableException e) {
                e.printStackTrace();
            }
            if (frame.hasDisplayGeometryChanged())
                mainRenderer.mCamera.transformDisplayGeometry(frame);

            Camera camera = frame.getCamera();
            float[] projMatrix = new float[16];
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f);
            float[] viewMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);

            mainRenderer.setPorojectMatrix(projMatrix);
            mainRenderer.updateViewMatrix(viewMatrix);


            mePos = calculateInitialMePoint(mainRenderer.mViewportWidth,
                    mainRenderer.mViewportHeight,
                    projMatrix,
                    viewMatrix);

            // ????????? x, y, z ?????? ????????????.
            mainRenderer.mObj.setViewMatrix(viewMatrix);

            float[] modelMatrix = new float[16];
            // ?????? ?????????
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, mePos[0], mePos[1], mePos[2]);
            Matrix.scaleM(modelMatrix, 0, 0.01f, 0.02f, 0.01f);
            mainRenderer.mObj.setModelMatrix(modelMatrix);

            //?????? ?????????
            if(makeCube && currentLocation!=null){ // ???????????? ??? ?????? ????????? ?????? ????????????
                makeCube = false;
                for(MyPlace place : myPlaces){
                    place.setArPosition(currentLocation, mePos);
                    mainRenderer.addCube(place);
                }
           }
        }
    }

    // ??? ?????? ?????? ??????
    GoogleMap mMap;
    // ??? ????????? ?????? ?????? ??????
    FusedLocationProviderClient fusedLocClient;

    Location currentLocation;
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        googleMap.setMyLocationEnabled(true);
        fusedLocClient = LocationServices.getFusedLocationProviderClient(this);

        //ArCore??? ?????? ?????? -- ?????? ?????? ????????? ??????


        // ???????????? ?????? ?????? ??????
        LocationRequest locRequest = LocationRequest.create();
        locRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); //?????????????????????
        locRequest.setInterval(1000); // 1????????? ??????

        LocationCallback locCallback = locCallBack;

        // ?????? ????????? ?????? ?????? ??????
        fusedLocClient.requestLocationUpdates(locRequest, locCallback, Looper.myLooper());
    }
    class LocCallback extends LocationCallback{
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if(locationResult != null){
                for(Location loc : locationResult.getLocations()){
                    currentLocation = loc;
                    setCameraLocation(loc);
                }
            }
        }
    }
    private void setCameraLocation(Location loc){
        if(firstCheck == true){
            bearing += 5f;
            firstCheck = false;
            LatLng latlng = new LatLng(loc.getLatitude(), loc.getLongitude());
            CameraPosition camPos = new CameraPosition.Builder().target(latlng)
                    .tilt(45f)
                    .bearing(bearing)
                    .zoom(15f)
                    .build();
            places();
            // ??? ??? ????????? ?????? 15:1??? ????????? !
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(camPos));
        }
    }
    // ?????? : ?????? : 37.49548409999986, ?????? : 127.02917309999997 ????????? ????????????
    // ?????? : ?????? : 37.502498600000095, ?????? : 127.02056580000001 ?????????1
    // ?????? : ?????? : 37.50079200000018, ?????? : 127.0233722999999    ?????????2
    private void places(){
        for(MyPlace place : myPlaces)
            mMap.addMarker(new MarkerOptions().position(place.latLng).title(place.title));
    }


    class DisplayListener implements DisplayManager.DisplayListener {
        @Override
        public void onDisplayAdded(int i) {
        }

        @Override
        public void onDisplayRemoved(int i) {
        }

        @Override
        public void onDisplayChanged(int i) {
            synchronized (this) {
                mainRenderer.mViewport = true;
            }
        }
    }
    //?????? ????????? ????????? ????????????
    float[] calculateInitialMePoint(int width, int height,
                                    float[] projMat, float[] viewMat) {
        return getScreenPoint(width / 2, height - 50.0f, width, height, projMat, viewMat);
    }
    //?????????
    public float[] getScreenPoint(float x, float y, float w, float h,
                                  float[] projMat, float[] viewMat) {
        float[] position = new float[3];
        float[] direction = new float[3];

        x = x * 2 / w - 1.0f;
        y = (h - y) * 2 / h - 1.0f;

        float[] viewProjMat = new float[16];
        Matrix.multiplyMM(viewProjMat, 0, projMat, 0, viewMat, 0);

        float[] invertedMat = new float[16];
        Matrix.setIdentityM(invertedMat, 0);
        Matrix.invertM(invertedMat, 0, viewProjMat, 0);

        float[] farScreenPoint = new float[]{x, y, 1.0F, 1.0F};
        float[] nearScreenPoint = new float[]{x, y, -1.0F, 1.0F};
        float[] nearPlanePoint = new float[4];
        float[] farPlanePoint = new float[4];

        Matrix.multiplyMV(nearPlanePoint, 0, invertedMat, 0, nearScreenPoint, 0);
        Matrix.multiplyMV(farPlanePoint, 0, invertedMat, 0, farScreenPoint, 0);

        position[0] = nearPlanePoint[0] / nearPlanePoint[3];
        position[1] = nearPlanePoint[1] / nearPlanePoint[3];
        position[2] = nearPlanePoint[2] / nearPlanePoint[3];

        direction[0] = farPlanePoint[0] / farPlanePoint[3] - position[0];
        direction[1] = farPlanePoint[1] / farPlanePoint[3] - position[1];
        direction[2] = farPlanePoint[2] / farPlanePoint[3] - position[2];

        normalize(direction);

        position[0] += (direction[0] * 0.1f);
        position[1] += (direction[1] * 0.1f);
        position[2] += (direction[2] * 0.1f);

        return position;
    }

    // ?????????
    private void normalize(float[] v) {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    private void settingSurfaceView() {
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mSurfaceView.setRenderer(mainRenderer);
    }

    private void noTitleBar() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void requstCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 0);
    }
}