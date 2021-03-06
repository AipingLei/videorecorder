/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package demo.recorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;


import demo.recorder.media.MediaRecordService;
import demo.recorder.ui.CameraPreview;

public class CameraCaptureActivity extends Activity {

    public static final String TAG = "CameraCaptureActivity";
    private static final int HANDLER_PRGRESS_RECORDING = 0 ;
    MediaRecordHandler mMediaRecordHandler;
    ImageView mRecordButton;
    ProgressBar mRecordProcess;
    View mPreview;
    private static final  long MAX_RECORD_TIME = 8000;
    private static final float PROCESS_UPDATE_VUALE = 100.f/MAX_RECORD_TIME;
    private Handler mMessageHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_recorder);
        CameraPreview cameraPreview = (CameraPreview) findViewById(R.id.record_preview);
        verifyStoragePermissions(this);
        mRecordButton = (ImageView) findViewById(R.id.record_button);
        mPreview = findViewById(R.id.record_camera_led);
        mRecordButton.setOnTouchListener(mOnVideoControllerTouchListener);
        mPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayMovieActivity.startActivity(CameraCaptureActivity.this);
            }
        });
        mRecordProcess = (ProgressBar)findViewById(R.id.record_process);
        mMediaRecordHandler = new MediaRecordHandler(this,cameraPreview);
        mMessageHandler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                switch (msg.what) {
                    case HANDLER_PRGRESS_RECORDING:
                        int sTargetProgress = (int)(mMediaRecordHandler.getRecordTime()*PROCESS_UPDATE_VUALE+0.05f);
                        if (sTargetProgress != mRecordProcess.getProgress()){
                            mRecordProcess.setProgress(sTargetProgress);
                            mRecordProcess.invalidate();
                        }
                        if (mMediaRecordHandler.isRecording()){
                            sendEmptyMessageDelayed(HANDLER_PRGRESS_RECORDING, 0);
                        }
                        break;
                }
                super.dispatchMessage(msg);
            }
        };
        Log.d(TAG, "onCreate complete: " + this);
    }

    private boolean isAlive = false;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume -- acquiring camera");
        super.onResume();
        mMediaRecordHandler.resume();
        isAlive = true;
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause -- releasing camera");
        super.onPause();
        mMediaRecordHandler.pause();
        isAlive = false;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        mMediaRecordHandler.destroy();
    }

    private View.OnTouchListener mOnVideoControllerTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mMediaRecordHandler == null || mMediaRecordHandler.isStopped()) {
                return false;
            }

            long sRecordTime = mMediaRecordHandler.getRecordTime();
            Log.d(TAG, "sRecordTime"+sRecordTime);
            if (sRecordTime> MAX_RECORD_TIME){
                mMediaRecordHandler.handleStopEvent();
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mMediaRecordHandler.handleRecordEvent();
                    break;
                case MotionEvent.ACTION_UP:
                    mMediaRecordHandler.handlePauseEvent();
                    break;
            }
            return true;
        }

    };

    class MediaRecordHandler {

        private int mVideoRecordState;

        MediaRecordService mediaRecordService;

        protected static final int RECORDING_IDEL = 0;
        protected static final int RECORDING_STARTED = 1;
        protected static final int RECORDING_RESUMED = 2;
        protected static final int RECORDING_PAUSED = 3;
        protected static final int RECORDING_STOPPED = 4;

        public MediaRecordHandler(Activity aActivity, CameraPreview cameraPreview) {
            mediaRecordService = new MediaRecordService(aActivity, cameraPreview);
        }

        public long getRecordTime() {
            return mediaRecordService.getRecordTime();
        }



        public void handleRecordEvent() {
            switch (mVideoRecordState) {
                case RECORDING_IDEL:
                    mVideoRecordState = RECORDING_STARTED;
                    mediaRecordService.startRecord();
                    mMessageHandler.sendEmptyMessage(HANDLER_PRGRESS_RECORDING);
                    break;
                case RECORDING_PAUSED:
                    mVideoRecordState = RECORDING_RESUMED;
                    mediaRecordService.resumeRecord();
                    mMessageHandler.sendEmptyMessage(HANDLER_PRGRESS_RECORDING);
                    break;
                default:
                    break;
            }
        }

        public void handlePauseEvent() {
            if (isRecording()){
                mVideoRecordState = RECORDING_PAUSED;
                mediaRecordService.pauseRecord();
            }
        }

        public void handleStopEvent() {
            if (isRecording()){
                mVideoRecordState = RECORDING_STOPPED;
                mediaRecordService.stopRecord();
                mRecordProcess.setProgress(0);
                mRecordProcess.setVisibility(View.GONE);
            }
        }

        public boolean isRecording(){
            return  mVideoRecordState == RECORDING_STARTED || mVideoRecordState == RECORDING_RESUMED;
        }

        public boolean isStopped(){
            return  mVideoRecordState == RECORDING_STOPPED;
        }


        public void resume() {
            mediaRecordService.resume();
        }

        public void pause() {
            mediaRecordService.pause();
        }

        public void destroy() {
            mediaRecordService.destroy();
        }
    }
}


