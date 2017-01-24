package com.saharw.phoneleech;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.saharw.phoneleech.config.Config;
import com.wowza.gocoder.sdk.api.WowzaGoCoder;
import com.wowza.gocoder.sdk.api.broadcast.WZBroadcast;
import com.wowza.gocoder.sdk.api.broadcast.WZBroadcastConfig;
import com.wowza.gocoder.sdk.api.devices.WZCamera;
import com.wowza.gocoder.sdk.api.devices.WZCameraView;
import com.wowza.gocoder.sdk.api.errors.WZError;
import com.wowza.gocoder.sdk.api.errors.WZStreamingError;
import com.wowza.gocoder.sdk.api.geometry.WZSize;
import com.wowza.gocoder.sdk.api.status.WZState;
import com.wowza.gocoder.sdk.api.status.WZStatus;
import com.wowza.gocoder.sdk.api.status.WZStatusCallback;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.rtsp.RtspClient;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.saharw.phoneleech.config.Config.PUBLISHER_PASSWORD;
import static com.saharw.phoneleech.config.Config.PUBLISHER_USERNAME;
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity implements View.OnClickListener, WZStatusCallback, SurfaceHolder.Callback, Session.Callback, RtspClient.Callback {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1000;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private MediaRecorder mMediaRecorder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSIONS = 10;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final int VIDEO_ENCODING_BIT_RATE = 512 * 1000;
    private final int VIDEO_RECORDING_FRAME_RATE = 30;
    private final String OUTPUT_FILE_PATH = Environment.
            getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
            "/screen_capture.mp4";


    // The top level GoCoder API interface
    private WowzaGoCoder mGoCoder;

    // The GoCoder SDK camera view
    private WZCameraView mGoCoderCameraView;

    // The GoCoder SDK broadcaster
    WZBroadcast goCoderBroadcaster;

    // The broadcast configuration settings
    WZBroadcastConfig mBroadcastConfig;
    private final String LICENSE_KEY = "GOSK-3943-0103-EC1B-6920-0D9E";
    private final String SERVER_ADDRESS = "rtsp://10.0.0.14";
    private final String STREAM_NAME = "livestream";
    private WZCameraView.PreviewStatusListener mPreviewStatusListener;
    private final int SERVER_PORT_NUM = 8086;
    private final String SERVER_APP_NAME = "phoneleech";
    private final String WOWZA_USER_NAME = "ssddWowza";
    private final String WOWZA_PASSWORD = "ssddWowza3000";
    private final int AUDIO_SAMPLE_RATE = 41000;
    private final int AUDIO_BIT_RATE = 64000;
    private final int VIDEO_FRAME_HEIGHT = 640;
    private final int VIDEO_FRAME_WIDTH  = 480;
    private final int VIDEO_BIT_RATE  = 15000;
    private final int VIDEO_FRAME_RATE  = 30;
    private final int VIDEO_FRAME_INTERVAL  = 30;
    private net.majorkernelpanic.streaming.gl.SurfaceView mSurfaceView;
    private Session mSession;
    private RtspClient mClient;
    private final String PATTERN = "http://(.+):(\\d+)(.+)";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);


        mScreenDensity = metrics.densityDpi;

        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService
                (Context.MEDIA_PROJECTION_SERVICE);

        mToggleButton = (ToggleButton) findViewById(R.id.toggle);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat
                        .checkSelfPermission(MainActivity.this,
                                Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale
                            (MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                            ActivityCompat.shouldShowRequestPermissionRationale
                                    (MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
                        mToggleButton.setChecked(false);
                        Snackbar.make(findViewById(android.R.id.content), R.string.label_permissions,
                                Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        ActivityCompat.requestPermissions(MainActivity.this,
                                                new String[]{Manifest.permission
                                                        .WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                                                REQUEST_PERMISSIONS);
                                    }
                                }).show();
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                                REQUEST_PERMISSIONS);
                    }
                } else {
                    onToggleScreenShare(v);
                }
            }
        });
        mPreviewStatusListener = new WZCameraView.PreviewStatusListener() {
            @Override
            public void onWZCameraPreviewStarted(WZCamera wzCamera, WZSize wzSize, int i) {
                Log.d(TAG, "onWZCameraPreviewStarted: camera = " + wzCamera + ", size = " + wzSize);
            }

            @Override
            public void onWZCameraPreviewStopped(int i) {
                Log.d(TAG, "onWZCameraPreviewStopped: " + i);
            }

            @Override
            public void onWZCameraPreviewError(WZCamera wzCamera, WZError wzError) {
                Log.e(TAG, "onWZCameraPreviewError: camera = " + wzCamera + ", error = " + wzError);
            }
        };
//        initGoCoderSDK(LICENSE_KEY);
        initUIComponents();
        initRtspClient();
    }

    private void initRtspClient() {
        // configure the SessionBuilder
        mSession = SessionBuilder.getInstance().
                setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setAudioQuality(new AudioQuality(8000, 16000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setSurfaceView(mSurfaceView).setPreviewOrientation(90)
                .setCallback(this).build();

        // configure the rtsp client
        mClient = new RtspClient();
        mClient.setSession(mSession);
        mClient.setCallback(this);
        mSurfaceView.setAspectRatioMode(net.majorkernelpanic.streaming.gl.SurfaceView.ASPECT_RATIO_PREVIEW);

        String ip, port, path;

        // parse the URI
        Pattern uri = Pattern.compile(PATTERN);
        Matcher mathcer = uri.matcher(Config.STREAM_URL);
        mathcer.find();
        ip = mathcer.group(1);
        port = mathcer.group(2);
        path = mathcer.group(3);

        mClient.setCredentials(PUBLISHER_USERNAME, PUBLISHER_PASSWORD);
        mClient.setServerAddress(ip, Integer.parseInt(port));
        mClient.setStreamPath("/" + path);
    }

    private void initUIComponents() {
        final Button broadcastButton = (Button) findViewById(R.id.broadcast_button);
        broadcastButton.setOnClickListener(this);
        mSurfaceView = (net.majorkernelpanic.streaming.gl.SurfaceView)findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

//        if (mGoCoderCameraView != null) {
//            if (mGoCoderCameraView.isPreviewPaused())
//                mGoCoderCameraView.onResume();
//            else
//                mGoCoderCameraView.startPreview();
//        }
        toggleStreaming();
    }

    private void toggleStreaming() {
        if (!mClient.isStreaming()) {
            mSession.startPreview();

            // start stream
            mClient.startStream();
        }else {
            mSession.stopPreview();
            mClient.stopStream();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            return;
        }
        mMediaProjectionCallback = new MediaProjectionCallback();
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            initRecorder();
            shareScreen();
        } else {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            Log.v(TAG, "Stopping Recording");
            stopScreenSharing();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        } else {
            mVirtualDisplay = createVirtualDisplay();
            mMediaRecorder.start();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    private void initRecorder() {
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaRecorder.setOutputFile(OUTPUT_FILE_PATH);
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(VIDEO_ENCODING_BIT_RATE);
            mMediaRecorder.setVideoFrameRate(VIDEO_RECORDING_FRAME_RATE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        // Ensure the minimum set of configuration settings have been specified necessary to
        // initiate a broadcast streaming session
//        WZStreamingError configValidationError = mBroadcastConfig.validateForBroadcast();
//
//        if (configValidationError != null) {
//            Toast.makeText(this, configValidationError.getErrorDescription(), Toast.LENGTH_LONG).show();
//        } else if (goCoderBroadcaster.getStatus().isRunning()) {
//            // Stop the broadcast that is currently running
//            goCoderBroadcaster.endBroadcast(this);
//        } else {
//            // Start streaming
//            mGoCoder.startStreaming(mBroadcastConfig, this);
//        }
    }

    @Override
    public void onWZStatus(WZStatus wzStatus) {

        // A successful status transition has been reported by the GoCoder SDK
        final StringBuffer statusMessage = new StringBuffer("Broadcast status: ");

        switch (wzStatus.getState()) {
            case WZState.STARTING:
                statusMessage.append("Broadcast initialization");
                break;

            case WZState.READY:
                statusMessage.append("Ready to begin streaming");
                break;

            case WZState.RUNNING:
                statusMessage.append("Streaming is active");
                break;

            case WZState.STOPPING:
                statusMessage.append("Broadcast shutting down");
                break;

            case WZState.IDLE:
                statusMessage.append("The broadcast is stopped");
                break;

            default:
                return;
        }

        // Display the status message using the U/I thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, statusMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onWZError(final WZStatus wzStatus) {
        // If an error is reported by the GoCoder SDK, display a message
        // containing the error details using the U/I thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        "Streaming error: " + wzStatus.getLastError().getErrorDescription(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onBitrateUpdate(long l) {

    }

    @Override
    public void onSessionError(int i, int i1, Exception e) {

    }

    @Override
    public void onPreviewStarted() {

    }

    @Override
    public void onSessionConfigured() {

    }

    @Override
    public void onSessionStarted() {

    }

    @Override
    public void onSessionStopped() {

    }

    @Override
    public void onRtspUpdate(int i, Exception e) {

    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v(TAG, "Recording Stopped");
            }
            mMediaProjection = null;
            stopScreenSharing();
        }
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        destroyMediaProjection();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyMediaProjection();
        mClient.release();
        mSession.release();
        mSurfaceView.getHolder().removeCallback(this);
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS: {
                if ((grantResults.length > 0) && (grantResults[0] +
                        grantResults[1]) == PackageManager.PERMISSION_GRANTED) {
                    onToggleScreenShare(mToggleButton);
                } else {
                    mToggleButton.setChecked(false);
                    Snackbar.make(findViewById(android.R.id.content), R.string.label_permissions,
                            Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                                    startActivity(intent);
                                }
                            }).show();
                }
                return;
            }
        }
    }

    private void initGoCoderSDK(String licenseKey) {
//        mGoCoderCameraView = (WZCameraView) findViewById(R.id.camera_preview);
        mGoCoderCameraView.setPreviewReadyListener(mPreviewStatusListener);
        mGoCoder = WowzaGoCoder.init(getApplicationContext(), licenseKey);
        mGoCoder.setCameraView(mGoCoderCameraView);

        if (mGoCoder == null) {
            // If initialization failed, retrieve the last error and display it
            WZError goCoderInitError = WowzaGoCoder.getLastError();
            Toast.makeText(this,
                    "GoCoder SDK error: " + goCoderInitError.getErrorDescription(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Create a broadcaster instance
        goCoderBroadcaster = new WZBroadcast();

        // Create a configuration instance for the broadcaster
        mBroadcastConfig = new WZBroadcastConfig(mGoCoder.getDefaultBroadcastConfig());

        // Set the address for the Wowza Streaming Engine server or Wowza Cloud
        mBroadcastConfig.setHostAddress(SERVER_ADDRESS);
        mBroadcastConfig.setPortNumber(SERVER_PORT_NUM);
        mBroadcastConfig.setApplicationName(SERVER_APP_NAME);

        // Set the name of the stream
        mBroadcastConfig.setStreamName(STREAM_NAME);
        mBroadcastConfig.setUsername(WOWZA_USER_NAME);
        mBroadcastConfig.setPassword(WOWZA_PASSWORD);

        mBroadcastConfig.setVideoBroadcaster(mGoCoderCameraView.getBroadcaster());
        mBroadcastConfig.setAudioBroadcaster(mGoCoder.getDefaultAudioDevice());
        mBroadcastConfig.setAudioSampleRate(AUDIO_SAMPLE_RATE);
        mBroadcastConfig.setAudioBroadcaster(mGoCoder.getDefaultAudioDevice());
        mBroadcastConfig.setAudioSampleRate(AUDIO_SAMPLE_RATE);
        mBroadcastConfig.setAudioBitRate(AUDIO_BIT_RATE);
        mBroadcastConfig.setVideoFrameHeight(VIDEO_FRAME_HEIGHT);
        mBroadcastConfig.setVideoFrameWidth(VIDEO_FRAME_WIDTH);
        mBroadcastConfig.setVideoBitRate(VIDEO_BIT_RATE);
        mBroadcastConfig.setVideoFramerate(VIDEO_FRAME_RATE);
        mBroadcastConfig.setVideoKeyFrameInterval(VIDEO_FRAME_INTERVAL);


    }
}