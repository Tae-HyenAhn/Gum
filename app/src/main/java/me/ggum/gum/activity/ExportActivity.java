package me.ggum.gum.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import me.ggum.gum.R;
import me.ggum.gum.decoder.ExportAudioDecoder;
import me.ggum.gum.encoder_export.ExportMuxerWrapper;
import me.ggum.gum.encoder_export.TextureMovieEncoder;
import me.ggum.gum.gles.Drawable2d;
import me.ggum.gum.gles.Model;
import me.ggum.gum.gles.ScaledDrawable2d;
import me.ggum.gum.gles.Shader;
import me.ggum.gum.gles.Sprite2d;
import me.ggum.gum.gles.Texture2dProgram;
import me.ggum.gum.string.S;
import me.ggum.gum.utils.FileUtil;

public class ExportActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener{

    private GLSurfaceView exportSurface;
    private ExportRenderer renderer;

    private MediaPlayer mp;
    private Surface surface;
    private ExportAudioDecoder audioDecoder;

    private long startTime, endTime;
    private long leftTime, rightTime;
    private double frameRate;

    private TextureMovieEncoder encoder;
    private boolean mRecordingEnabled;
    private ExportMuxerWrapper muxerWrapper;

    private int screenWidth;
    private int ratio;

    private int effectState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);
        init();

    }

    private void init(){
        getIntentData();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;

        try{
            muxerWrapper = new ExportMuxerWrapper(new MediaMuxer(FileUtil.generateOutputVideoPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4), 2);


        }catch(IOException e){
            e.printStackTrace();
        }

        encoder = new TextureMovieEncoder(this, muxerWrapper, effectState);


        exportSurface = (GLSurfaceView) findViewById(R.id.export_surface);
        exportSurface.setEGLContextClientVersion(2);
        renderer = new ExportRenderer(this, encoder, screenWidth, ratio, effectState, startTime, endTime, leftTime, rightTime);
        exportSurface.setRenderer(renderer);
        exportSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }

    private void getIntentData(){
        Intent intent = getIntent();

        frameRate = intent.getExtras().getDouble(S.KEY_EFFECT_FRAME_RATE);
        leftTime = intent.getExtras().getLong(S.KEY_EFFECT_LEFT_TIME);
        rightTime = intent.getExtras().getLong(S.KEY_EFFECT_RIGHT_TIME);
        effectState = intent.getExtras().getInt(S.KEY_EFFECT_STATE);

    }

    private void switchEncoding(){
        mRecordingEnabled = !mRecordingEnabled;
        exportSurface.queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.changeRecordingState(mRecordingEnabled);
            }
        });
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        exportSurface.requestRender();
    }

    private void startPlay(SurfaceTexture surfaceTexture){
        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);

        mp = new MediaPlayer();
        audioDecoder = new ExportAudioDecoder();
        audioDecoder.init(FileUtil.generateTempOutputPath(), startTime, endTime, muxerWrapper);
        mp.setOnPreparedListener(mpPreparedListener);
        try{
            mp.setDataSource(FileUtil.generateTempOutputPath());
            mp.setSurface(surface);
            mp.prepare();
            mp.seekTo((int)startTime);
            audioDecoder.start();
            mp.start();

        } catch (IOException e){
            e.printStackTrace();
        }

    }

    private MediaPlayer.OnPreparedListener mpPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            switchEncoding();
        }
    };


    public class ExportRenderer implements GLSurfaceView.Renderer{
        private static final String TAG = "ExportRenderer";

        private int screenWidth;

        /* for Record */
        private boolean mRecordingEnabled;

        private static final int RECORDING_OFF = 0;
        private static final int RECORDING_ON = 1;
        private static final int RECORDING_RESUMED = 2;
        private int mRecordingStatus;
        private TextureMovieEncoder mVideoEncoder;

        private SurfaceTexture mSurfaceTexture;
        private final float[] mDisplayProjectionMatrix = new float[16];
        private int mTextureId;

        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect= new Sprite2d(mRectDrawable);

        private final ScaledDrawable2d mRectDrawable1 =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect1= new Sprite2d(mRectDrawable1);

        private final ScaledDrawable2d mRectDrawable2 =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect2= new Sprite2d(mRectDrawable2);

        private float mPosX, mPosY;

        public final static int RATIO_SQUARE = 0;
        public final static int RATIO_CIRCLE = 1;
        private int ratioState = RATIO_SQUARE;

        public final static int EFFECT_ORIGINAL = -1;
        public final static int EFFECT_REPEAT = 0;
        public final static int EFFECT_ZOOM = 1;
        public final static int EFFECT_CROP = 2;
        public final static int EFFECT_LEFT_RIGHT = 3;
        public final static int EFFECT_ROLLING = 4;
        public final static int EFFECT_ZOOM_MOVE = 5;
        private int effectState = -1;


        /* for Circle stencil */
        private Context context;
        private Model model;
        private Shader shader;

        private long startTime, endTime, leftTime, rightTime;


        public ExportRenderer(Context context, TextureMovieEncoder encoder, int screenWidth, int ratio, int effectState
        , long startTime, long endTime, long leftTime, long rightTime) {
            this.context = context;
            this.screenWidth = screenWidth;
            this.ratioState = ratio;
            this.effectState = effectState;
            this.startTime = startTime;
            this.endTime = endTime;
            this.leftTime = leftTime;
            this.rightTime = rightTime;
            mVideoEncoder = encoder;
        }

        public void changeRecordingState(boolean isRecording){
            mRecordingEnabled = isRecording;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mRecordingEnabled = mVideoEncoder.isRecording();
            if (mRecordingEnabled) {
                mRecordingStatus = RECORDING_RESUMED;
            } else {
                mRecordingStatus = RECORDING_OFF;
            }


            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mTextureId = mTexProgram.createTextureObject();

            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mRect.setTexture(mTextureId);
            mRect1.setTexture(mTextureId);
            mRect2.setTexture(mTextureId);
            GLES20.glViewport(0, 0, exportSurface.getWidth(), exportSurface.getWidth());

            shader = new Shader(context);
            model = new Model(context, shader);

            mPosX = screenWidth/2;
            mPosY = screenWidth/2;

            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, screenWidth, 0, screenWidth, -1, 1);

            mRect.setScale(screenWidth, screenWidth);
            mRect.setPosition(mPosX, mPosY);

            mRect1.setScale(screenWidth*3/4, screenWidth*3/4);
            mRect1.setPosition(screenWidth/2, screenWidth/2);

            mRect2.setScale(screenWidth/2, screenWidth/2);
            mRect2.setPosition(screenWidth/2, screenWidth/2);

            if(mSurfaceTexture != null){
                startPlay(mSurfaceTexture);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }

        long frameCount;
        int zoomFactor;
        float angle;
        boolean turnZoom = false;
        boolean first = false;

        @Override
        public void onDrawFrame(GL10 gl) {


            if(mp.getCurrentPosition() >= (int)endTime){
                mp.pause();
                if(!first){
                    audioDecoder.close();
                    switchEncoding();
                    first = true;
                }
                mp.seekTo((int)startTime);
                mp.start();
            }


            mSurfaceTexture.updateTexImage();

            if (mRecordingEnabled) {
                switch (mRecordingStatus) {
                    case RECORDING_OFF:
                        Log.d(TAG, "START recording");
                        // start recording
                        mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(FileUtil.generateTempOutputPath(), 720, 720, 12000000,
                                EGL14.eglGetCurrentContext(), FileUtil.generateOutputVideoPath()));
                        mVideoEncoder.setScreenSize(screenWidth, screenWidth);
                        mVideoEncoder.setOrientation(0);
                        mVideoEncoder.setVideoSize(720, 720);
                        mVideoEncoder.setRatio(ratioState);
                        mRecordingStatus = RECORDING_ON;
                        Log.d("Start Recording", "SRARTTTT");
                        break;
                    case RECORDING_RESUMED:
                        Log.d(TAG, "RESUME recording");
                        mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                        mRecordingStatus = RECORDING_ON;
                        break;
                    case RECORDING_ON:
                        // yay
                        break;
                    default:
                        throw new RuntimeException("unknown status " + mRecordingStatus);
                }
            } else {
                switch (mRecordingStatus) {
                    case RECORDING_ON:
                    case RECORDING_RESUMED:
                        // stop recording
                        Log.d(TAG, "STOP recording");
                        mVideoEncoder.stopRecording();
                        mRecordingStatus = RECORDING_OFF;

                        Log.d("END Recording", "ENDDDDDD");
                        break;
                    case RECORDING_OFF:
                        // yay
                        break;
                    default:
                        throw new RuntimeException("unknown status " + mRecordingStatus);
                }
            }

            mVideoEncoder.setTextureId(mTextureId);
            mVideoEncoder.frameAvailable(mSurfaceTexture);

            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.5f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);

            if(mp.getCurrentPosition() >= (int)leftTime && mp.getCurrentPosition() <= (int)rightTime ){
                mVideoEncoder.sendSignalEffectArea(true);
                frameCount ++;
                if(effectState == EFFECT_ORIGINAL){
                    setEffectOriginal();
                }else if(effectState == EFFECT_LEFT_RIGHT){
                    setEffectLeftRight();
                }else if(effectState == EFFECT_CROP){
                    setEffectCrop();
                }else if(effectState == EFFECT_ZOOM){
                    setEffectZoom();
                }else if(effectState == EFFECT_ZOOM_MOVE){
                    setEffectZoomMove();
                }else if(effectState == EFFECT_ROLLING){
                    setEffectRolling();
                }
            }else{
                mVideoEncoder.sendSignalEffectArea(false);
                frameCount = 0;
                changeZoomValue(0);
                mRect.setScale(screenWidth, screenWidth);
                mRect.setRotation(0);
                angle = 0;
            }

            if(ratioState == RATIO_CIRCLE){
                shader.setShader();
                shader.setShaderParameters();
                model.draw();
            }
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

        private void setEffectZoomMove(){
            if(frameCount >= 10 ){
                mRect1.draw(mTexProgram, mDisplayProjectionMatrix);
            }

            if(frameCount >= 20){
                mRect2.draw(mTexProgram, mDisplayProjectionMatrix);
            }
        }

        private void setEffectRolling(){
            mRect.setRotation(angle);
            if(angle >= 360.0f){
                angle = 0.0f;
            }
            angle = angle+3.0f;
        }
    }
}
