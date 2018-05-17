/*
 * Copyright 2016-present Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.daliborjelnek.touchlessBrowser;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;



import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlib.ImageUtils;

import junit.framework.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    //324, 648, 972, 1296, 224, 448, 672, 976, 1344
    private static final int INPUT_SIZE = 976;
    private static final String TAG = "OnGetImageListener";

    private int mScreenRotation = 90;

    private List<VisionDetRet> results;
    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mResizedBitmap = null;
    private Bitmap mInversedBitmap = null;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;
    private Activity mActivity;

    private Context mContext;
    private FaceDet mFaceDet;

    private Paint mFaceLandmardkPaint;
    private Paint infoPaint;

    private int mframeNum = 0;

    public void initialize(
            final Context context,
            AssetManager assets, final Handler handler,Activity activity) {
        this.mContext = context;
        this.mActivity = activity;
        this.mInferenceHandler = new Handler(context.getMainLooper());
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mFaceLandmardkPaint = new Paint();
        infoPaint = new Paint();
        infoPaint.setTextSize(50f);
        infoPaint.setColor(Color.BLACK);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);
        mFaceLandmardkPaint.setStrokeWidth(1);
        mFaceLandmardkPaint.setColor(Color.GREEN);

    }



    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        int screen_width = point.x;

        int screen_height = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
        if (screen_width < screen_height) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
            mScreenRotation = -90;
        } else {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
            mScreenRotation = 0;
        }

        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    public Bitmap imageSideInversion(Bitmap src){
        Matrix sideInversion = new Matrix();
        sideInversion.setScale(-1, 1);
        Bitmap inversedImage = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), sideInversion, false);
        return inversedImage;
    }


    @Override
    public void onImageAvailable(final ImageReader reader) {


        if(!((MainActivity)mActivity).getReady()){
            ((MainActivity)mActivity).setReady(true);
        }

        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();

                //Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWdith,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            //Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        mInversedBitmap = imageSideInversion(mCroppedBitmap);
        mResizedBitmap = Bitmap.createScaledBitmap(mInversedBitmap, (int)(INPUT_SIZE/4.5), (int)(INPUT_SIZE/4.5), true);

        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
            // mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
        }

        if(mframeNum % 3 == 0){

            synchronized (OnGetImageListener.this) {

                results = mFaceDet.detect(mResizedBitmap);


            }
        }


        // mInversedBitmap.eraseColor(Color.BLACK);
        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {


                        // Draw on bitmap
                        if (results.size() != 0) {
                            for (final VisionDetRet ret : results) {
                                float resizeRatio = 4.5f;
                                Canvas canvas = new Canvas(mInversedBitmap);
                                Rect bounds = new Rect();
                                bounds.left = (int) (ret.getLeft() * resizeRatio);
                                bounds.top = (int) (ret.getTop() * resizeRatio);
                                bounds.right = (int) (ret.getRight() * resizeRatio);
                                bounds.bottom = (int) (ret.getBottom() * resizeRatio);
                              //  canvas.drawRect(bounds, mFaceLandmardkPaint);
                                // Draw landmark
                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                                int i = 0;

                                for (Point point : landmarks) {

                                    int pointX = (int) (point.x * resizeRatio);
                                    int pointY = (int) (point.y * resizeRatio);
                                    //canvas.drawCircle(pointX, pointY, 4, mFaceLandmardkPaint);
                                    canvas.drawText(Integer.toString(i),pointX,pointY,mFaceLandmardkPaint);
                                    i++;

                                }
                                int horizontal;
                                int vertical;

                                int smile = (landmarks.get(66).y - landmarks.get(62).y) * (landmarks.get(54).x - landmarks.get(49).x);
                                vertical = (landmarks.get(30).y - landmarks.get(27).y)*2 - (landmarks.get(8).y - landmarks.get(30).y);
                                horizontal = (landmarks.get(30).x -landmarks.get(2).x) - (landmarks.get(14).x - landmarks.get(30).x);


                                canvas.drawText("s: "+ Integer.toString(smile),100,100,infoPaint);
                                canvas.drawText("v: "+ Integer.toString(vertical),100,200,infoPaint);
                                canvas.drawText("h: "+ Integer.toString(horizontal),100,300,infoPaint);

                               // canvas.drawText("t: "+ Integer.toString((landmarks.get(30).y - landmarks.get(27).y)),250,200,infoPaint);
                                //canvas.drawText("d: "+ Integer.toString((landmarks.get(8).y - landmarks.get(30).y)),250,300,infoPaint);



                            }
                            ((MainActivity)mActivity).onLandmarksAvailable(results.get(0).getFaceLandmarks(), mInversedBitmap);
                        }

                        mframeNum++;


                        mIsComputing = false;
                    }

                });

        Trace.endSection();
    }
}
