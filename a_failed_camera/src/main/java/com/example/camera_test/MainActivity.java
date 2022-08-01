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
    static boolean i = true; // 控制摄像头翻转

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

        // 请求相机权限
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, Configuration.REQUIRED_PERMISSIONS,
                    Configuration.REQUEST_CODE_PERMISSIONS);
        }

        // 设置拍照按钮监听
        Button camera_capture_button = findViewById(R.id.camera_capture_button);
        camera_capture_button.setOnClickListener(v -> takePhoto());

        // 设置转换摄像头按钮监听
        Button change_camera = findViewById(R.id.change_camera);
        change_camera.setOnClickListener(v -> changecamera());


        // 设置录频按钮监听
        Button video_button = findViewById(R.id.video_button);
        video_button.setOnClickListener(v -> takeVideo());

        // 设置照片等保存的位置
        outputDirectory = getOutputDirectory();

        cameraExecutor = Executors.newSingleThreadExecutor();

    }



    private void takePhoto() {
        // 确保imageCapture 已经被实例化, 否则程序将可能崩溃
        if (imageCapture != null) {
            // 创建带时间戳的输出文件以保存图片，带时间戳是为了保证文件名唯一
            File photoFile = new File(outputDirectory,
                    new SimpleDateFormat(Configuration.FILENAME_FORMAT,
                            Locale.SIMPLIFIED_CHINESE).format(System.currentTimeMillis())
                            + ".jpg");

            // 创建 output option 对象，用以指定照片的输出方式
            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions
                    .Builder(photoFile)
                    .build();

            // 执行takePicture（拍照）方法
            imageCapture.takePicture(outputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {// 保存照片时的回调
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Uri savedUri = Uri.fromFile(photoFile);
                            String msg = "照片捕获成功! " + savedUri;
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
        // 确保videoCapture 已经被实例化，否则程序可能崩溃
        if (videoCapture != null) {
            viewBinding.videoButton.setEnabled(false);

            Recording curRecording = recording;
            if (curRecording != null) {
                // 停止当前的录制会话
                curRecording.stop();
                recording = null;
                return;
            }

            // 创建一个新的 recording session
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
            // 申请音频权限
            /*
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        Configuration.REQUEST_CODE_PERMISSIONS);
            }

            recording = VideoCapture.getOutput.prepareRecording(this, mediaStoreOutputOptions)
                    .withAudioEnabled() // 开启音频录制
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

    //摄像头转换控制
    private void changecamera(){
        i = !i;
        startCamera();
    }


    @SuppressLint("RestrictedApi")
    private void startCamera() {
        // 将Camera的生命周期和Activity绑定在一起（设定生命周期所有者），这样就不用手动控制相机的启动和关闭。
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // 将你的相机和当前生命周期的所有者绑定所需的对象
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();

                // 创建一个Preview 实例，并设置该实例的 surface 提供者（provider）。
                PreviewView viewFinder = (PreviewView)findViewById(R.id.viewFinder);
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
/*
                //预览页面手势
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
                // 选择摄像头
                CameraSelector cameraSelector =  i ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;

                // 创建拍照所需的实例
                imageCapture = new ImageCapture.Builder().build();
                // 创建视频所需的实例
                videoCapture = new Builder().build();
/*
                // 设置预览帧分析
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new MyAnalyzer());
*/
                // 重新绑定用例前先解绑
                processCameraProvider.unbindAll();

                // 绑定用例至相机
                Camera mCamera = processCameraProvider.bindToLifecycle(MainActivity.this, cameraSelector,
                        preview,
                        imageCapture,
                        videoCapture     //绑定失败——一次只能绑定三个
                        /*imageAnalysis*/);


            } catch (Exception e) {
                Log.e(Configuration.TAG, "用例绑定失败！" + e);
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
            if (allPermissionsGranted()) {// 申请权限通过
                startCamera();
            } else {// 申请权限失败
                Toast.makeText(this, "用户拒绝授予权限！", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}


