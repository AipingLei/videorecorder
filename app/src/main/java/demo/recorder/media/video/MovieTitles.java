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

package demo.recorder.media.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;


/**
 * description: the tities of movie
 * create by: leiap
 * create date: 2017/5/19
 * update date: 2017/5/19
 * version: 1.0
*/
public class MovieTitles extends SurfaceMovie {
    private static final String TAG = "SurfaceMovie";

    protected int mNumFrames = 60;

    private float[] getIconXY(int aCanvasWidth, int aCanvasHeight){
        Bitmap bitmap = getBitmap();
        float[] xy = new float[2];
        xy[0] = (WIDTH-)/2;
    }

    @Override
    protected void generateFrame(int frameIndex) {
        Canvas canvas = mInputSurface.lockCanvas(null);
        try {
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            canvas.drawColor(Color.BLACK);
            mPaint.setAlpha(255-(frameIndex*5));
            canvas.drawBitmap(getBitmap(),(WIDTH-width)/2,height,mPaint);
        } finally {
            mInputSurface.unlockCanvasAndPost(canvas);
        }
    }

}


