package me.ggum.gum.encoder;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.opengl.EGLContext;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;


import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import me.ggum.gum.activity.CameraActivity;
import me.ggum.gum.gles.Drawable2d;
import me.ggum.gum.gles.EglCore;
import me.ggum.gum.gles.FullFrameRect;
import me.ggum.gum.gles.Model;
import me.ggum.gum.gles.ScaledDrawable2d;
import me.ggum.gum.gles.Shader;
import me.ggum.gum.gles.Sprite2d;
import me.ggum.gum.gles.Texture2dProgram;
import me.ggum.gum.gles.WindowSurface;

/**
 * Created by sb on 2017. 1. 4..
 */

public class TextureMovieEncoder implements Runnable {

    private static final String TAG = "TextureMovieEncoder";

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID =3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_QUIT = 5;
    private static final int MSG_CANCEL = 6;

    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;

    private FullFrameRect mFullScreen;
    private Texture2dProgram mTexProgram;
    private final ScaledDrawable2d mRectDrawable =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final ScaledDrawable2d mRectDrawable2 =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final ScaledDrawable2d mRectDrawable3 =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final ScaledDrawable2d mRectDrawable4 =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final Sprite2d mRect = new Sprite2d(mRectDrawable);
    private final Sprite2d mRect2 = new Sprite2d(mRectDrawable2);
    private final Sprite2d mRect3 = new Sprite2d(mRectDrawable3);
    private final Sprite2d mRect4 = new Sprite2d(mRectDrawable4);
    private float[] mDisplayProjectionMatrix = new float[16];


    private int mTextureId;

    private VideoEncoderCore mVideoEncoder;


    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();
    private boolean mReady;
    private boolean mRunning;

    private Context context;

    private int cameraFacingState;
    private int screenWidth;
    private int screenHeight;

    private OnEncoderListener listener;


    public TextureMovieEncoder(Context context) {
        this.context = context;
    }

    public void setOnEncoderListener(OnEncoderListener listener){
        this.listener = listener;
    }

    public static class EncoderConfig {
        final String mOutputPath;
        final int mWidth;
        final int mHeight;
        final int mBitRate;
        final EGLContext mEglContext;

        public EncoderConfig(String outputPath, int width, int height, int bitrate, EGLContext sharedEglContext){
            mOutputPath = outputPath;
            mWidth = width;
            mHeight = height;
            mBitRate = bitrate;
            mEglContext = sharedEglContext;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mWidth + "x" + mHeight + " @" + mBitRate +
                    " to '" + mOutputPath + "' ctxt=" + mEglContext;
        }
    }



    public void startRecording(EncoderConfig config){
        synchronized (mReadyFence){
            if(mRunning){
                return;
            }
            mRunning = true;
            new Thread(this, "TextureMovieEncoder").start();
            while(!mReady) {
                try{
                    mReadyFence.wait();
                } catch (InterruptedException ie){

                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
    }

    public void cancelRecording(){
        mHandler.sendMessage(mHandler.obtainMessage(MSG_CANCEL));
    }

    public void stopRecording(){
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
    }

    public boolean isRecording(){
        synchronized (mReadyFence){
            return mRunning;
        }
    }

    public void updateSharedContext(EGLContext sharedContext){
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }

    public void frameAvailable(SurfaceTexture st){
        synchronized (mReadyFence){
            if(!mReady){
                return;
            }
        }

        float[] transform = new float[16];
        st.getTransformMatrix(transform);
        long timestamp = st.getTimestamp();
        //long timestamp = System.nanoTime();
        if (timestamp == 0){
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE, (int) (timestamp >> 32), (int) timestamp, transform));
    }

    public void setTextureId(int id){
        synchronized (mReadyFence){
            if(!mReady){
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }

    @Override
    public void run() {

        Looper.prepare();
        synchronized (mReadyFence){
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        synchronized (mReadyFence){
            mReady = mRunning = false;
            mHandler = null;
        }
    }

    private static class EncoderHandler extends Handler{
        private WeakReference<TextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(TextureMovieEncoder encoder){
            mWeakEncoder = new WeakReference<TextureMovieEncoder>(encoder);
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMovieEncoder encoder = mWeakEncoder.get();
            if(encoder == null){
                return;
            }

            switch(what){
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);

                    //long timestamp = (long) inputMessage.arg2;
                    encoder.handleFrameAvailable((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    encoder.handleSetTexture(inputMessage.arg1);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;
                case MSG_CANCEL:
                    encoder.handleCancelRecording();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what="+what);
            }
        }
    }

    private void handleStartRecording(EncoderConfig config){
        prepareEncoder(config.mEglContext, config.mWidth, config.mHeight, config.mBitRate, config.mOutputPath);
    }

    float angle;
    long PTS;
    boolean firstFrame = false;
    private AudioThread audio;

    private void handleFrameAvailable(float[] transform, long timestampNanos){

        if(!firstFrame){
            audio.start();
            firstFrame = true;
        }

        long timestamp = mVideoEncoder.getPTSUs();



        Log.d(TAG, "timestampNanos : "+timestampNanos);
        mVideoEncoder.drainEncoder(false);


        mRect.draw(mTexProgram,mDisplayProjectionMatrix);
        mRect2.draw(mTexProgram, mDisplayProjectionMatrix);
        mRect3.draw(mTexProgram, mDisplayProjectionMatrix);
        mRect4.draw(mTexProgram, mDisplayProjectionMatrix);

        mInputWindowSurface.swapBuffers();
    }

    private void handleCancelRecording(){
        audio.stopRecording();
        mVideoEncoder.drainEncoder(true);

        releaseEncoderWidthoutMuxing();

    }

    private void handleStopRecording(){
        PTS = mVideoEncoder.getPTSUs();

        mVideoEncoder.drainEncoder(true);
        audio.stopRecording();

        Log.d(TAG, "HANDLE END");
        releaseEncoder();

    }

    private void handleSetTexture(int id){
        mTextureId = id;
        mRect.setTexture(mTextureId);
        mRect2.setTexture(mTextureId);
        mRect3.setTexture(mTextureId);
        mRect4.setTexture(mTextureId);
    }

    private void handleUpdateSharedContext(EGLContext newSharedContext){

        mInputWindowSurface.releaseEglSurface();
        mFullScreen.release(false);
        mEglCore.release();

        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        prepareRect();
    }

    public void setCameraFacing(int facing){
        cameraFacingState = facing;
    }

    public void setScreenSize(int width, int height){
        screenWidth = width;
        screenHeight = height;
    }

    private void prepareEncoder(EGLContext sharedContext, int width, int height, int bitrate, String outputPath){
        try{
            mVideoEncoder = new VideoEncoderCore(width, height, bitrate, outputPath);
        } catch(IOException ioe){
            throw new RuntimeException(ioe);
        }


        audio = new AudioThread();


        mEglCore = new EglCore(sharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();

        prepareRect();

    }

    private void prepareRect(){

        mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
        //mFullScreen = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, screenWidth, 0, screenHeight, -1, 1);

        if(cameraFacingState == CameraActivity.FRONT_CAMERA){
            //mRect.setScale(1280*screenWidth/720, -screenWidth);
            mRect.setRotation(90);
            mRect2.setRotation(90);
            mRect3.setRotation(90);
            mRect4.setRotation(90);
            mRect.setScale(-(1280*screenWidth/720)/2, -screenWidth/2);
            mRect2.setScale(-(1280*screenWidth/720)/2, screenWidth/2);
            mRect3.setScale((1280*screenWidth/720)/2, -screenWidth/2);
            mRect4.setScale((1280*screenWidth/720)/2, screenWidth/2);
        }else if(cameraFacingState == CameraActivity.BACK_CAMERA){
            //mRect.setScale(1280*screenWidth/720, screenWidth);
            mRect.setRotation(-90);
            mRect2.setRotation(-90);
            mRect3.setRotation(-90);
            mRect4.setRotation(-90);
            mRect.setScale(-(1280*screenWidth/720)/2, screenWidth/2);
            mRect2.setScale(-(1280*screenWidth/720)/2, -screenWidth/2);
            mRect3.setScale((1280*screenWidth/720)/2, screenWidth/2);
            mRect4.setScale((1280*screenWidth/720)/2, -screenWidth/2);
        }

        //mRect.setPosition(screenWidth/2, screenHeight/2);
        mRect.setPosition(screenWidth/4, screenHeight/4-((7*screenHeight)/36));
        mRect2.setPosition(screenWidth*3/4, screenHeight/4-((7*screenHeight)/36));
        mRect3.setPosition(screenWidth/4, screenHeight*3/4+((7*screenHeight)/36));
        mRect4.setPosition(screenWidth*3/4, screenHeight*3/4+((7*screenHeight)/36));
    }

    private void releaseEncoderWidthoutMuxing(){
        mVideoEncoder.cancel();

        if(mInputWindowSurface != null){
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }

        if (mFullScreen != null){
            mFullScreen.release(false);
            mFullScreen = null;
        }
        if(mEglCore != null){
            mEglCore.release();
            mEglCore = null;
        }
        Log.d(TAG, "stoped");
        if(listener != null){
            listener.onEncoderStop();
        }

    }

    private void releaseEncoder() {

        mVideoEncoder.release();

        if(mInputWindowSurface != null){
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }

        if (mFullScreen != null){
            mFullScreen.release(false);
            mFullScreen = null;
        }
        if(mEglCore != null){
            mEglCore.release();
            mEglCore = null;
        }
        Log.d(TAG, "stoped");
        if(listener != null){
            listener.onEncoderStop();
        }

    }

    public interface OnEncoderListener{
        void onEncoderStop();
    }



}
