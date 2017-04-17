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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;


import demo.recorder.media.MediaRecordService;
import demo.recorder.ui.CameraPreview;

/**
 * Shows the camera preview on screen while simultaneously recording it to a .mp4 file.
 * <p>
 * Every time we receive a frame from the camera, we need to:
 * <ul>
 * <li>Render the frame to the SurfaceView, on GLSurfaceView's renderer thread.
 * <li>Render the frame to the mediacodec's input surface, on the encoder thread, if
 *     recording is enabled.
 * </ul>
 * <p>
 * At any given time there are four things in motion:
 * <ol>
 * <li>The UI thread, embodied by this Activity.  We must respect -- or work around -- the
 *     app lifecycle changes.  In particular, we need to release and reacquire the Camera
 *     so that, if the user switches away from us, we're not preventing another app from
 *     using the camera.
 * <li>The Camera, which will busily generate preview frames once we hand it a
 *     SurfaceTexture.  We'll get notifications on the main UI thread unless we define a
 *     Looper on the thread where the SurfaceTexture is created (the GLSurfaceView renderer
 *     thread).
 * <li>The video encoder thread, embodied by TextureMovieEncoder.  This needs to share
 *     the Camera preview external texture with the GLSurfaceView renderer, which means the
 *     EGLContext in this thread must be created with a reference to the renderer thread's
 *     context in hand.
 * <li>The GLSurfaceView renderer thread, embodied by CameraSurfaceRenderer.  The thread
 *     is created for us by GLSurfaceView.  We don't get callbacks for pause/resume or
 *     thread startup/shutdown, though we could generate messages from the Activity for most
 *     of these things.  The EGLContext created on this thread must be shared with the
 *     video encoder, and must be used to create a SurfaceTexture that is used by the
 *     Camera.  As the creator of the SurfaceTexture, it must also be the one to call
 *     updateTexImage().  The renderer thread is thus at the center of a multi-thread nexus,
 *     which is a bit awkward since it's the thread we have the least control over.
 * </ol>
 * <p>
 * GLSurfaceView is fairly painful here.  Ideally we'd create the video encoder, create
 * an EGLContext for it, and pass that into GLSurfaceView to share.  The API doesn't allow
 * this, so we have to do it the other way around.  When GLSurfaceView gets torn down
 * (say, because we rotated the device), the EGLContext gets tossed, which means that when
 * it comes back we have to re-create the EGLContext used by the video encoder.  (And, no,
 * the "preserve EGLContext on pause" feature doesn't help.)
 * <p>
 * We could simplify this quite a bit by using TextureView instead of GLSurfaceView, but that
 * comes with a performance hit.  We could also have the renderer thread drive the video
 * encoder directly, allowing them to work from a single EGLContext, but it's useful to
 * decouple the operations, and it's generally unwise to perform disk I/O on the thread that
 * renders your UI.
 * <p>
 * We want to access Camera from the UI thread (setup, teardown) and the renderer thread
 * (configure SurfaceTexture, start preview), but the API says you can only access the object
 * from a single thread.  So we need to pick one thread to own it, and the other thread has to
 * access it remotely.  Some things are simpler if we let the renderer thread manage it,
 * but we'd really like to be sure that Camera is released before we leave onPause(), which
 * means we need to make a synchronous call from the UI thread into the renderer thread, which
 * we don't really have full control over.  It's less scary to have the UI thread own Camera
 * and have the renderer call back into the UI thread through the standard Handler mechanism.
 * <p>
 * (The <a href="http://developer.android.com/training/camera/cameradirect.html#TaskOpenCamera">
 * camera docs</a> recommend accessing the camera from a non-UI thread to avoid bogging the
 * UI thread down.  Since the GLSurfaceView-managed renderer thread isn't a great choice,
 * we might want to create a dedicated camera thread.  Not doing that here.)
 * <p>
 * With three threads working simultaneously (plus Camera causing periodic events as frames
 * arrive) we have to be very careful when communicating state changes.  In general we want
 * to send a message to the thread, rather than directly accessing state in the object.
 * <p>
 * &nbsp;
 * <p>
 * To exercise the API a bit, the video encoder is required to survive Activity restarts.  In the
 * current implementation it stops recording but doesn't stop time from advancing, so you'll
 * see a pause in the video.  (We could adjust the timer to make it seamless, or output a
 * "paused" message and hold on that in the recording, or leave the Camera running so it
 * continues to generate preview frames while the Activity is paused.)  The video encoder object
 * is managed as a static property of the Activity.
 */
public class CameraCaptureActivity extends Activity {
    public static final String TAG = "CameraCaptureActivity";
    private static final boolean VERBOSE = false;
    MediaRecordService mediaRecordService;
    ImageView mRecordButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_recorder);
        CameraPreview sGLView = (CameraPreview) findViewById(R.id.record_preview);
        mRecordButton = (ImageView) findViewById(R.id.record_button);
        mRecordButton.setOnTouchListener(mOnVideoControllerTouchListener);
        mediaRecordService = new MediaRecordService(this,sGLView);
        Log.d(TAG, "onCreate complete: " + this);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume -- acquiring camera");
        super.onResume();
        mediaRecordService.resume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause -- releasing camera");
        super.onPause();
        mediaRecordService.pause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        mediaRecordService.destroy();
    }

    private boolean isRecord = false;

    private View.OnTouchListener mOnVideoControllerTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mediaRecordService == null) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;

                case MotionEvent.ACTION_UP:
                    boolean sRecord = !isRecord;
                    if (sRecord){
                        mediaRecordService.startRecord();
                        mRecordButton.setBackgroundResource(R.drawable.squarecamera__camera_snap_unselected);
                        Toast.makeText(CameraCaptureActivity.this,"start recording!",Toast.LENGTH_LONG).show();
                    }else {
                        mRecordButton.setBackgroundResource(R.drawable.squarecamera__camera_snap_selected);
                        mediaRecordService.stopRecord();
                        Toast.makeText(CameraCaptureActivity.this,"stop recording!",Toast.LENGTH_LONG).show();
                    }
                    isRecord = sRecord;
                    break;
            }
            return true;
        }

    };

}


