package com.daliborjelnek.touchlessBrowser;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSION = 2;

    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };


    private ArrayList<Point> landmarks;



    private CustomDialogClass cdd;

    private boolean clickTriggered = false;
    private HeadTilt headTilted = HeadTilt.none;
    private boolean ready = false;
    private boolean listening = false;
    private int width;
    private int height;


    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    private String mCameraId = null;

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    private CursorImageView mCursor;
    private HandFreeWebView mWebView;

    private final OnGetImageListener mOnGetPreviewListener = new OnGetImageListener();
    private CaptureRequest.Builder mCaptureRequestBuilder;


    private ImageReader mImageReader;

    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        boolean permissions;
        //tvPermission = findViewById(R.id.tvPermissions);
        FloatingActionButton button = findViewById(R.id.btnSettings);
        mCursor = findViewById(R.id.ivCursor);
        mCursor.moveTo(500,500);

        mWebView = findViewById(R.id.webView);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.loadUrl("http://google.com/");



        mWebView.post(new Runnable() {
            @Override
            public void run() {
                height = mWebView.getHeight(); //height is ready
                width = mWebView.getWidth();

            }
        });



        cdd = new CustomDialogClass(this);
        //cdd.show();



        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cdd.show();

            }
        });


        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentApiVersion >= Build.VERSION_CODES.M) {
            permissions = verifyPermissions(this);
           // tvPermission.setText(permissions ? "Permission grnated" : "Permissions not granted");
        }

        setupCamera(600,600);


    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundThread();
        closeCamera();


    }

    @Override
    protected void onResume() {
        super.onResume();
        setReady(false);
        startBackgroundThread();
        connectCamera();


    }





    private boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE); //todo: is necessary?
        int camera_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);
        int record_audio_permission = ActivityCompat.checkSelfPermission(activity,Manifest.permission.RECORD_AUDIO);


        if (write_permission != PackageManager.PERMISSION_GRANTED ||
                read_permission != PackageManager.PERMISSION_GRANTED ||
                camera_permission != PackageManager.PERMISSION_GRANTED ||
                record_audio_permission != PackageManager.PERMISSION_DENIED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    private void setupCamera(int width, int heignt) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    mCameraId = cameraId;
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("CameraImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
      //  SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
      //  surfaceTexture.setDefaultBufferSize(300,300);
       // Surface previewSurface = new Surface(surfaceTexture);

        try {
            mImageReader = ImageReader.newInstance(600,600, ImageFormat.YUV_420_888,2);
            mImageReader.setOnImageAvailableListener(mOnGetPreviewListener,mBackgroundHandler);
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW
            );
           // mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        cameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),mCaptureCallback,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mOnGetPreviewListener.initialize(getBaseContext(),getAssets(), mBackgroundHandler,MainActivity.this);
    }


    public void setNewLandmarks(ArrayList<Point> landmarks, Bitmap bmp,int[] max) {

        int SENSITIVITY_THRESHOLD = 10;
        int H_SCROLL_THRESHOLD = 20;
        int V_SCROLL_THRESHOLD = 20;
        int NOSE_COMPENSATION = 2;
        double H_SPEED = 1.5;
        double V_SPEED = 2;


        boolean scrollMode = false;



        this.landmarks = landmarks;
       int vpositive, hpositive;

        int deltaH = (landmarks.get(30).x - landmarks.get(2).x) - (landmarks.get(14).x - landmarks.get(30).x);
        int deltaV = (landmarks.get(30).y - landmarks.get(27).y)*NOSE_COMPENSATION - (landmarks.get(8).y - landmarks.get(30).y);
        hpositive = deltaH > 0 ? 1 : -1;
        vpositive = deltaV > 0 ? 1 : -1;


        if(Math.abs(deltaH) > SENSITIVITY_THRESHOLD ||  Math.abs(deltaV) > SENSITIVITY_THRESHOLD ){

            deltaH = (int)(deltaH*H_SPEED) - SENSITIVITY_THRESHOLD*hpositive;
            deltaV = (int) (deltaV*V_SPEED) -SENSITIVITY_THRESHOLD*vpositive;

            int newHorizontalCoord = (int)(mCursor.getX() + deltaH);
            int newVerticalCoord = (int)(mCursor.getY() + deltaV);

            int horizontalScroll = mWebView.getScrollX();
            int verticalScroll = mWebView.getScrollY();


            int horizontalScrollMax = mWebView.computeHorizontalScrollRange() - width;
            int verticalScrollMax = mWebView.computeVerticalScrollRange()-height;


            // VERTICAL BOTTOM
            if(newVerticalCoord >= (height-V_SCROLL_THRESHOLD)){ //new coord would be in scrollzone
                if(mWebView.getScrollY()< verticalScrollMax ){ //scroll down is possible
                    scrollMode = true;
                    if((verticalScroll+deltaV)<= verticalScrollMax ){ // scroll range will not be crossed
                        verticalScroll+= deltaV;
                    }
                    else{ //scroll range would be crossed so scroll to one half of leftover.
                        verticalScroll += (verticalScrollMax-verticalScroll)/2;
                        if(verticalScroll>verticalScrollMax-5){
                            verticalScroll = verticalScrollMax;
                        }
                    }
                }
                else {
                    if(newVerticalCoord < height){
                        //do nothing
                    }
                    else{
                        newVerticalCoord = (int)(mCursor.getY()+(((height - mCursor.getY())/2)));

                        if(newVerticalCoord>verticalScrollMax-5){
                            newVerticalCoord = height;
                        }
                    }
                }



            }
            // VERTICAL TOP
            if(newVerticalCoord <= V_SCROLL_THRESHOLD){
                if(mWebView.getScrollY()> 0 ){ //scroll up is possible
                    scrollMode = true;
                    if((verticalScroll+deltaV)<= 0 ){ // scroll range will not be crossed
                        verticalScroll+= deltaV;
                    }
                    else{
                        verticalScroll = verticalScroll/2;
                        if(verticalScroll<5){
                            verticalScroll = 0;
                        }
                    }
                }
                else {
                    if(newVerticalCoord > 0){
                        //do nothing
                    }
                    else{
                        newVerticalCoord = (int)(mCursor.getY()/2);
                        if(newVerticalCoord<5){
                            newVerticalCoord = 0;
                        }
                    }
                }
            }

            //HORIZONTAL RIGHT
            if(newHorizontalCoord >= (width-H_SCROLL_THRESHOLD)){
                if(mWebView.getScrollX()< horizontalScrollMax ){ //scroll right is possible
                    scrollMode = true;
                    if((horizontalScroll+deltaH)<= horizontalScroll ){ // scroll range will not be crossed
                        horizontalScroll+= deltaH;
                    }
                    else{ //scroll range would be crossed so scroll to one half of leftover.
                        horizontalScroll += (horizontalScrollMax-horizontalScroll)/2;
                        if(horizontalScroll>horizontalScrollMax-5){
                            horizontalScroll = horizontalScrollMax;
                        }
                    }
                }
                else {
                    if(newHorizontalCoord < width){
                        //do nothing
                    }
                    else{
                        newHorizontalCoord = (int)(mCursor.getX()+(((width - mCursor.getX())/2)));

                        if(newHorizontalCoord > verticalScrollMax-5){
                            newHorizontalCoord = width;
                        }
                    }
                }
            }

            //HORIZONTAL LEFT
            if(newHorizontalCoord <= H_SCROLL_THRESHOLD){
                if(mWebView.getScrollX()> 0 ){ //scroll left is possible
                    scrollMode = true;
                    if((horizontalScroll+deltaH)<= 0 ){ // scroll range will not be crossed
                        horizontalScroll+= deltaH;
                    }
                    else{
                        horizontalScroll = horizontalScroll/2;
                        if(horizontalScroll<5){
                            horizontalScroll = 0;
                        }
                    }
                }
                else {
                    if(newHorizontalCoord > 0){
                        //do nothing
                    }
                    else{
                        newHorizontalCoord = (int)(mCursor.getX()/2);
                        if(newHorizontalCoord<5){
                            newHorizontalCoord = 0;
                        }
                    }
                }
            }



            if(scrollMode){

                mWebView.scrollTo(horizontalScroll,verticalScroll);
            }
            else {
                mCursor.moveTo(newHorizontalCoord,newVerticalCoord);
            }


        }



        /* GESTURES */
        int smile = (landmarks.get(66).y - landmarks.get(62).y) * (landmarks.get(54).x - landmarks.get(49).x);
        int headtilt =  landmarks.get(27).x - landmarks.get(8).x;

        if(headtilt < -30){
            if(headTilted == HeadTilt.none ){
                headTilted = HeadTilt.left;
                mCursor.setColorFilter(Color.GREEN);
                mWebView.goBack();
            }

        }


        if(headtilt > -30) {
            if(headTilted== HeadTilt.left){
                headTilted = HeadTilt.none;
                mCursor.setColorFilter(null);
            }
        }

        if(headtilt > 30){
            if(headTilted == HeadTilt.none ){
                headTilted = HeadTilt.right;
                mCursor.setColorFilter(Color.YELLOW);
                mWebView.startListenVoice();

            }
        }

        if(headtilt < 30){
            if(headTilted == HeadTilt.right ){
                headTilted = HeadTilt.none;
                mCursor.setColorFilter(null);

            }
        }



        if(smile > 150 ){
            if (!clickTriggered && !listening){
                clickTriggered = true;

                mCursor.setColorFilter(Color.RED);
                float x = mCursor.getX();
                float y = mCursor.getY();
                mWebView.simulateClick(x,y);
            }

        }


        if(smile < 150 && clickTriggered == true) {
            clickTriggered = false;
            mCursor.setColorFilter(null);
        }




        if(cdd != null && cdd.isShowing()){

            cdd.setPreviewBitmap(bmp);



        }

    }

    public void setListening(boolean l) {
        this.listening = l;
    }

    public void setReady(final boolean r) {
        ready = r;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {


                if(r == true){
                    if(cdd.isShowing()){
                        cdd.showPreview();
                        cdd.hide();
                    }
                }
                else {
                    cdd.show();
                    cdd.showLoading();
                }

            }
        });


    }

    public boolean getReady(){
        return ready;
    }






 }
