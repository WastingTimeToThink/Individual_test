package com.example.camera_test;

import static androidx.camera.core.VideoCapture.*;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.OnVideoSavedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.camera_test.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private Recording recording;
    private ActivityMainBinding viewBinding;
    private File outputDirectory;
    private ExecutorService cameraExecutor;
    static boolean i = true; // ?????????????????????

    private static class MyAnalyzer implements ImageAnalysis.Analyzer{
        @SuppressLint("UnsafeOptInUsageError")
        @Override
        public void analyze(@NonNull ImageProxy image) {
            Log.d(Configuration.TAG, "Image's stamp is " + Objects.requireNonNull(image.getImage()).getTimestamp());
            image.close();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ??????????????????
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, Configuration.REQUIRED_PERMISSIONS,
                    Configuration.REQUEST_CODE_PERMISSIONS);
        }

        // ????????????????????????
        Button camera_capture_button = findViewById(R.id.camera_capture_button);
        camera_capture_button.setOnClickListener(v -> takePhoto());

        // ?????????????????????????????????
        Button change_camera = findViewById(R.id.change_camera);
        change_camera.setOnClickListener(v -> changecamera());


        // ????????????????????????
        Button video_button = findViewById(R.id.video_button);
        video_button.setOnClickListener(v -> takeVideo());

        // ??????????????????????????????
        outputDirectory = getOutputDirectory();

        cameraExecutor = Executors.newSingleThreadExecutor();

    }



    private void takePhoto() {
        // ??????imageCapture ??????????????????, ???????????????????????????
        if (imageCapture != null) {
            // ?????????????????????????????????????????????????????????????????????????????????????????????
            File photoFile = new File(outputDirectory,
                    new SimpleDateFormat(Configuration.FILENAME_FORMAT,
                            Locale.SIMPLIFIED_CHINESE).format(System.currentTimeMillis())
                            + ".jpg");

            // ?????? output option ??????????????????????????????????????????
            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions
                    .Builder(photoFile)
                    .build();

            // ??????takePicture??????????????????
            imageCapture.takePicture(outputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {// ????????????????????????
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Uri savedUri = Uri.fromFile(photoFile);
                            String msg = "??????????????????! " + savedUri;
                            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                            Log.d(Configuration.TAG, msg);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(Configuration.TAG, "Photo capture failed: " + exception.getMessage());
                        }
                    });
        }
    }

    private void takeVideo(){
        // ??????videoCapture ?????????????????????????????????????????????
        if (videoCapture != null) {
            viewBinding.videoButton.setEnabled(false);

            Recording curRecording = recording;
            if (curRecording != null) {
                // ???????????????????????????
                curRecording.stop();
                recording = null;
                return;
            }

            // ?????????????????? recording session
            String name = new SimpleDateFormat(Configuration.FILENAME_FORMAT, Locale.SIMPLIFIED_CHINESE)
                    .format(System.currentTimeMillis());
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video");
            }

            MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions
                    .Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(contentValues)
                    .build();
            // ??????????????????
            /*
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        Configuration.REQUEST_CODE_PERMISSIONS);
            }

            recording = VideoCapture.getOutput.prepareRecording(this, mediaStoreOutputOptions)
                    .withAudioEnabled() // ??????????????????
                    .start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            viewBinding.videoButton.setText(getString(R.string.stop_capture));
                            viewBinding.videoButton.setEnabled(true);
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                String msg = "Video capture succeeded: " +
                                        ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults()
                                                .getOutputUri();
                                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                                Log.d(Configuration.TAG, msg);
                            } else {
                                if (recording != null) {
                                    recording.close();
                                    recording = null;
                                    Log.e(Configuration.TAG, "Video capture end with error: " +
                                            ((VideoRecordEvent.Finalize) videoRecordEvent).getError());
                                }
                            }
                            viewBinding.videoButton.setText(getString(R.string.start_capture);
                            viewBinding.videoButton.setEnabled(true);
                        }
                    });

             */
        }
    }

    //?????????????????????
    private void changecamera(){
        i = !i;
        startCamera();
    }


    @SuppressLint("RestrictedApi")
    private void startCamera() {
        // ???Camera??????????????????Activity?????????????????????????????????????????????????????????????????????????????????????????????????????????
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // ?????????????????????????????????????????????????????????????????????
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();

                // ????????????Preview ?????????????????????????????? surface ????????????provider??????
                PreviewView viewFinder = (PreviewView)findViewById(R.id.viewFinder);
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
/*
                //??????????????????
                viewFinder.setOnTouchListener(new PreviewView.OnTouchListener(){
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        switch (motionEvent.getAction()) {
                            case MotionEvent.ACTION_DOWN:

                                break;
                            case MotionEvent.ACTION_UP:

                                break;
                        }
                        return true;
                    }
                });
*/
                // ???????????????
                CameraSelector cameraSelector =  i ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;

                // ???????????????????????????
                imageCapture = new ImageCapture.Builder().build();
                // ???????????????????????????
                videoCapture = new Builder().build();
/*
                // ?????????????????????
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new MyAnalyzer());
*/
                // ??????????????????????????????
                processCameraProvider.unbindAll();

                // ?????????????????????
                Camera mCamera = processCameraProvider.bindToLifecycle(MainActivity.this, cameraSelector,
                        preview,
                        imageCapture,
                        videoCapture     //??????????????????????????????????????????
                        /*imageAnalysis*/);


            } catch (Exception e) {
                Log.e(Configuration.TAG, "?????????????????????" + e);
            }
        }, ContextCompat.getMainExecutor(this));

    }


    private boolean allPermissionsGranted() {
        for (String permission : Configuration.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private File getOutputDirectory() {
        File mediaDir = new File(getExternalMediaDirs()[0], getString(R.string.app_name));
        boolean isExist = mediaDir.exists() || mediaDir.mkdir();
        return isExist ? mediaDir : null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    static class Configuration {
        public static final String TAG = "CameraxBasic";
        public static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
        public static final int REQUEST_CODE_PERMISSIONS = 10;
        public static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Configuration.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {// ??????????????????
                startCamera();
            } else {// ??????????????????
                Toast.makeText(this, "???????????????????????????", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}


