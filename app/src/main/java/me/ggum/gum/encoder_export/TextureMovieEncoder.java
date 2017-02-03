package me.ggum.gum.encoder_export;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;

import me.ggum.gum.activity.ExportActivity;
import me.ggum.gum.activity.PreRenderActivity;
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
 * Created by sb on 2017. 1. 25..
 */

public class TextureMovieEncoder implements Runnable{
    private static final String TAG = "TextureMovieEncoder";
    private static final boolean VERBOSE = false;

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_QUIT = 5;
    private static final int MSG_SEND_EFFECT_AREA = 6;

    // ----- accessed exclusively by encoder thread -----
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;
    private FullFrameRect mFullScreen;

    private Texture2dProgram mTexProgram;
    private final ScaledDrawable2d mRectDrawable =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final Sprite2d mRect = new Sprite2d(mRectDrawable);

    private final ScaledDrawable2d mRectDrawable1 =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final Sprite2d mRect1= new Sprite2d(mRectDrawable1);

    private final ScaledDrawable2d mRectDrawable2 =
            new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
    private final Sprite2d mRect2= new Sprite2d(mRectDrawable2);

    private float[] mDisplayProjectionMatrix = new float[16];

    private int mTextureId;
    private int mFrameNum;
    private VideoEncoderCore mVideoEncoder;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    private Context context;
    private Model model;
    private Shader shader;
    private int ratioState;

    private int screenWidth;
    private int screenHeight;

    private int orientation;

    private int videoWidth, videoHeight;

    private ExportMuxerWrapper muxerWrapper;

    private boolean isEffectArea;

    private int effectState = -1;

    public TextureMovieEncoder(Context context, ExportMuxerWrapper muxerWrapper, int effectState) {
        this.context = context;
        this.muxerWrapper = muxerWrapper;
        isEffectArea = false;
        this.effectState = effectState;

    }

    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     *       with reasonable defaults for those and bit rate.
     */
    public static class EncoderConfig {
        final String mOutputPath;
        final int mWidth;
        final int mHeight;
        final int mBitRate;
        final EGLContext mEglContext;
        final String mFilePath;

        public EncoderConfig(String outputPath, int width, int height, int bitRate,
                             EGLContext sharedEglContext, String filePath) {
            mOutputPath = outputPath;
            mWidth = width;
            mHeight = height;
            mBitRate = bitRate;
            mEglContext = sharedEglContext;
            mFilePath = filePath;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mWidth + "x" + mHeight + " @" + mBitRate +
                    " to '" + mOutputPath + "' ctxt=" + mEglContext;
        }
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    public void startRecording(TextureMovieEncoder.EncoderConfig config) {
        Log.d(TAG, "Encoder: startRecording()");
        synchronized (mReadyFence) {
            if (mRunning) {
                Log.w(TAG, "Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(this, "TextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public void stopRecording() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    public void sendSignalEffectArea(boolean isEffectArea){
        this.isEffectArea = isEffectArea;
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    public void updateSharedContext(EGLContext sharedContext) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * TODO: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    public void frameAvailable(SurfaceTexture st) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }

        float[] transform = new float[16];      // TODO - avoid alloc every frame
        st.getTransformMatrix(transform);
        long timestamp = st.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, transform));
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }



    @Override
    public void run() {
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new TextureMovieEncoder.EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<TextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(TextureMovieEncoder encoder) {
            mWeakEncoder = new WeakReference<TextureMovieEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
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
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    /**
     * Starts recording.
     */
    private void handleStartRecording(EncoderConfig config) {
        Log.d(TAG, "handleStartRecording " + config);
        mFrameNum = 0;
        prepareEncoder(config.mEglContext, config.mWidth, config.mHeight, config.mBitRate,
                config.mOutputPath, config.mFilePath);
    }


    long PTS;
    boolean firstFrame = false;
    long frameCount;
    int zoomFactor;
    float angle;
    boolean turnZoom = false;
    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     * @param transform The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(float[] transform, long timestampNanos) {

        if(!firstFrame){

            firstFrame = true;
        }

        mInputWindowSurface.setPresentationTime(timestampNanos);

        mVideoEncoder.drainEncoder(false);


        mRect.draw(mTexProgram, mDisplayProjectionMatrix);
        if(ratioState == PreRenderActivity.PreRenderer.RATIO_CIRCLE){
            shader.setShader();
            shader.setShaderParameters();
            model.draw();

            angle = angle + 2.0f;
        }

        if(isEffectArea){
            frameCount ++;
            if(effectState == ExportActivity.ExportRenderer.EFFECT_ORIGINAL){
                setEffectOriginal();
            }else if(effectState == ExportActivity.ExportRenderer.EFFECT_LEFT_RIGHT){
                setEffectLeftRight();
            }else if(effectState == ExportActivity.ExportRenderer.EFFECT_CROP){
                setEffectCrop();
            }else if(effectState == ExportActivity.ExportRenderer.EFFECT_ZOOM){
                setEffectZoom();
            }else if(effectState == ExportActivity.ExportRenderer.EFFECT_ZOOM_MOVE){
                setEffectZoomMove();
            }else if(effectState == ExportActivity.ExportRenderer.EFFECT_ROLLING){
                setEffectRolling();
            }
        }else{
            frameCount = 0;
            changeZoomValue(0);
            mRect.setScale(screenWidth, screenWidth);
            mRect.setRotation(0);
            angle = 0;
        }

        mInputWindowSurface.swapBuffers();
    }

    public void changeZoomValue(int mZoomPercent){
        float zoomFactor = 1.0f - (mZoomPercent / 100.0f);
        mRectDrawable.setScale(zoomFactor);
    }

    private void setEffectOriginal(){
        mRect.setScale(screenWidth, screenWidth);
        mRect.setRotation(0.0f);
        changeZoomValue(0);

    }

    private void setEffectLeftRight(){
        if(frameCount%4 == 0){
            mRect.setScale(-screenWidth, screenWidth);
        }else if(frameCount%4 == 1){
            mRect.setScale(-screenWidth, screenWidth);
        }else if(frameCount%4 == 2){
            mRect.setScale(screenWidth, screenWidth);
        }else if(frameCount%4 == 3){
            mRect.setScale(screenWidth, screenWidth);
        }
    }

    private void setEffectCrop(){
        if(frameCount%4 == 0){
            changeZoomValue(55);
        }else if(frameCount%4 == 1){
            changeZoomValue(55);
        }else if(frameCount%4 == 2){
            changeZoomValue(0);
        }else if(frameCount%4 == 3){
            changeZoomValue(0);
        }
    }

    private void setEffectZoomMove(){
        if(frameCount >= 10 ){
            mRect1.draw(mTexProgram, mDisplayProjectionMatrix);
        }

        if(frameCount >= 20){
            mRect2.draw(mTexProgram, mDisplayProjectionMatrix);
        }
    }

    private void setEffectZoom(){
        if(frameCount % 20 == 0){
            turnZoom = !turnZoom;
        }
        if(!turnZoom){

            if(zoomFactor <= 80){
                zoomFactor = zoomFactor+4;
                if(zoomFactor < 100)
                    changeZoomValue(zoomFactor);
            }
        }else if(turnZoom){
            if(zoomFactor > 0){
                zoomFactor = zoomFactor -4;
                if(zoomFactor > 0)
                    changeZoomValue(zoomFactor);
            }
        }

        Log.d(TAG, "ZOOMFACTOR: "+zoomFactor);

    }


    private void setEffectRolling(){
        mRect.setRotation(angle);
        if(angle >= 360.0f){
            angle = 0.0f;
        }
        angle = angle+3.0f;
    }

    private void setIsEffectArea(){
        this.isEffectArea = !isEffectArea;
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        Log.d(TAG, "handleStopRecording");
        mVideoEncoder.drainEncoder(true);

        releaseEncoder();
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int id) {
        //Log.d(TAG, "handleSetTexture " + id);
        mTextureId = id;
        mRect.setTexture(mTextureId);
        mRect1.setTexture(mTextureId);
        mRect2.setTexture(mTextureId);
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     * <p>
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        Log.d(TAG, "handleUpdatedSharedContext " + newSharedContext);

        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        mFullScreen.release(false);
        mEglCore.release();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new context.


        prepareRect();
    }

    public void setRatio(int ratio){    ratioState = ratio; }

    public void setScreenSize(int width, int height){
        screenWidth = width;
        screenHeight = height;
    }

    public void setOrientation(int orientation){
        this.orientation = orientation;
    }

    public void setVideoSize(int width, int height){
        videoWidth = width;
        videoHeight = height;
    }


    private void prepareEncoder(EGLContext sharedContext, int width, int height, int bitRate,
                                String outputPath, String filePath) {
        try {
            mVideoEncoder = new VideoEncoderCore(width, height, bitRate, outputPath, muxerWrapper);

        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEglCore = new EglCore(sharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();

        prepareRect();
    }

    private void prepareRect(){
        shader = new Shader(context);
        model = new Model(context, shader);


        mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
        Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, screenWidth, 0, screenWidth, -1, 1);

        mRect.setScale(screenWidth, screenWidth);

        mRect.setPosition(screenWidth/2, screenWidth/2);

        mRect1.setScale(screenWidth*3/4, screenWidth*3/4);
        mRect1.setPosition(screenWidth/2, screenWidth/2);

        mRect2.setScale(screenWidth/2, screenWidth/2);
        mRect2.setPosition(screenWidth/2, screenWidth/2);
    }

    private void releaseEncoder() {
        mVideoEncoder.release();
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);
            mFullScreen = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }

    }
}
