package me.ggum.gum.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import me.ggum.gum.R;
import me.ggum.gum.decoder.EffectAudioDecoder;
import me.ggum.gum.decoder.EffectDecoder;
import me.ggum.gum.encoder_effect.TextureMovieEncoder;
import me.ggum.gum.gles.Drawable2d;
import me.ggum.gum.gles.Model;
import me.ggum.gum.gles.ScaledDrawable2d;
import me.ggum.gum.gles.Shader;
import me.ggum.gum.gles.Sprite2d;
import me.ggum.gum.gles.Texture2dProgram;
import me.ggum.gum.string.S;

import me.ggum.gum.view.EffectControlView;
import me.ggum.gum.view.HoverImageButton;
import wseemann.media.FFmpegMediaMetadataRetriever;

public class EffectActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener, View.OnClickListener{
    private static final String TAG = "EffectActivity";

    private HoverImageButton backBtn, soundSwitch, completeBtn;
    private EffectControlView effectControlView;

    /* temp */
    private ImageButton effect00Button;
    private TextView effect00Text;
    private ImageButton effect01Button;
    private TextView effect01Text;
    private ImageButton effect02Button;
    private TextView effect02Text;
    private ImageButton effect03Button;
    private TextView effect03Text;
    private ImageButton effect04Button;
    private ImageButton effect05Button;


    private GLSurfaceView effectSurface;
    private EffectGLRenderer renderer;


    private EffectDecoder decoder;
    private EffectAudioDecoder audioDecoder;
    private Surface surface;

    private long duration;

    private int screenWidth;
    private double frameRate;

    private float leftFPer, rightFPer, midFPer;
    private long leftTime, rightTime, midDifferTime;

    private static final boolean FROM_CAMERA = true;
    private boolean fromWhat;

    private int ratioState;

    private String vPath;

    @Override
    public void onClick(View v) {
        int id = v.getId();

        switch (id){
            case R.id.effect_effect_00:
                //original
                effect00Button.setAlpha(1.0f);
                effect01Button.setAlpha(0.5f);
                effect02Button.setAlpha(0.5f);
                effect03Button.setAlpha(0.5f);
                effect04Button.setAlpha(0.5f);
                effect05Button.setAlpha(0.5f);
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setEffectState(EffectGLRenderer.EFFECT_ORIGINAL);
                    }
                });
                break;
            case R.id.effect_effect_01:
                //left right
                effect00Button.setAlpha(0.5f);
                effect01Button.setAlpha(1.0f);
                effect02Button.setAlpha(0.5f);
                effect03Button.setAlpha(0.5f);
                effect04Button.setAlpha(0.5f);
                effect05Button.setAlpha(0.5f);
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setEffectState(EffectGLRenderer.EFFECT_LEFT_RIGHT);
                    }
                });
                break;
            case R.id.effect_effect_02:
                //crop in out
                effect00Button.setAlpha(0.5f);
                effect01Button.setAlpha(0.5f);
                effect02Button.setAlpha(1.0f);
                effect03Button.setAlpha(0.5f);
                effect04Button.setAlpha(0.5f);
                effect05Button.setAlpha(0.5f);
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setEffectState(EffectGLRenderer.EFFECT_CROP);
                    }
                });
                break;
            case R.id.effect_effect_03:
                //zoom in out
                effect00Button.setAlpha(0.5f);
                effect01Button.setAlpha(0.5f);
                effect02Button.setAlpha(0.5f);
                effect03Button.setAlpha(1.0f);
                effect04Button.setAlpha(0.5f);
                effect05Button.setAlpha(0.5f);
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setEffectState(EffectGLRenderer.EFFECT_ZOOM);
                    }
                });
                break;
            case R.id.effect_effect_04:
                //zoom in move out
                effect00Button.setAlpha(0.5f);
                effect01Button.setAlpha(0.5f);
                effect02Button.setAlpha(0.5f);
                effect03Button.setAlpha(0.5f);
                effect04Button.setAlpha(1.0f);
                effect05Button.setAlpha(0.5f);
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setEffectState(EffectGLRenderer.EFFECT_ZOOM_MOVE);
                    }
                });
                break;
            case R.id.effect_effect_05:
                //rolling
                effect00Button.setAlpha(0.5f);
                effect01Button.setAlpha(0.5f);
                effect02Button.setAlpha(0.5f);
                effect03Button.setAlpha(0.5f);
                effect04Button.setAlpha(0.5f);
                effect05Button.setAlpha(1.0f);
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setEffectState(EffectGLRenderer.EFFECT_ROLLING);
                    }
                });
                break;
        }
    }

    private View.OnClickListener completeBtnListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            goIntentExport();
            finish();
        }
    };

    private EffectDecoder.OnEffectDecoderListener effectDecoderListener = new EffectDecoder.OnEffectDecoderListener() {

        @Override
        public void onDecoderStart() {

        }

        @Override
        public void onPlayTime(final long time) {
            float timePer = culTimeToPer(time);
            float nowPoint = screenWidth*timePer;
            effectControlView.setPlayPoint(nowPoint);

            if(!decoder.getSeekMode()){
                effectSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        if(time > leftTime && time < rightTime) {
                            renderer.setIsEffectArea(true);
                        }else{
                            renderer.setIsEffectArea(false);
                        }
                    }
                });
            }
        }

        @Override
        public void decoderRestart() {
            effectSurface.queueEvent(new Runnable() {
                @Override
                public void run() {

                }
            });
        }

        @Override
        public void onDecoderStop() {

        }
    };

    private EffectControlView.OnEffectVideoTimeChangeListener videoTimeChangeListener = new EffectControlView.OnEffectVideoTimeChangeListener() {
        @Override
        public void onStartTimeChange(float pre) {

        }

        @Override
        public void onVideoTimeChange(float left, float right, float midDifference, int touchState) {
            Log.d(TAG, "CHANGE: "+left+", "+right+", "+midDifference);

            leftFPer = culXtoPer(left);
            rightFPer = culXtoPer(right);
            midFPer = culXtoPer(midDifference);

            decoder.setSeekMode(true);

            leftTime = (long)(duration*leftFPer);
            rightTime = (long)(duration*rightFPer);
            midDifferTime = (long)(duration*midFPer);

            if(touchState == EffectControlView.LEFT_TOUCHED){
                decoder.setTouchTime(leftTime);
            }else if(touchState == EffectControlView.RIGHT_TOUCHED){
                decoder.setTouchTime(rightTime);
            }else if(touchState == EffectControlView.MID_RECT_TOUCHED){
                decoder.setTouchTime(leftTime+midDifferTime);
            }



            Log.d(TAG, "CHANGE: "+leftFPer+", "+rightFPer+", "+midFPer);

        }

        @Override
        public void onFinishTimeChange() {
            decoder.setSeekMode(false);

            decoder.setLeftTime(leftTime);
            decoder.setRightTime(rightTime);
            decoder.setEffectDuration(rightTime-leftTime);
            Log.d(TAG, "Onfinish!");
        }
    };

    private float culXtoPer(float x){
        return (float) (x/screenWidth);
    }

    private float culTimeToPer(long time){
        return (((float)(time))/(float)duration);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_effect);
        init();
    }

    @Override
    protected void onPause() {
        super.onPause();
        effectSurface.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        effectSurface.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        decoder.close();
        audioDecoder.close();
    }

    private void init(){
        DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;

        getIntentData();

        effectSurface = (GLSurfaceView) findViewById(R.id.effect_glsurface);
        effectSurface.setEGLContextClientVersion(2);
        renderer = new EffectGLRenderer(this, screenWidth, ratioState);
        effectSurface.setZOrderOnTop(false);
        effectSurface.setRenderer(renderer);
        effectSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        effectControlView = (EffectControlView) findViewById(R.id.effect_time_control_view);
        effectControlView.setOnEffectVideoTimeChangeListener(videoTimeChangeListener);

        backBtn = (HoverImageButton) findViewById(R.id.effect_back);
        soundSwitch = (HoverImageButton) findViewById(R.id.effect_sound_switch);
        completeBtn = (HoverImageButton) findViewById(R.id.effect_complete);
        completeBtn.setOnClickListener(completeBtnListener);

        setDefaultArea();
        /*temp*/
        effect00Button = (ImageButton) findViewById(R.id.effect_effect_00);
        effect00Text = (TextView) findViewById(R.id.effect_effect_text_00);
        effect01Button = (ImageButton) findViewById(R.id.effect_effect_01);
        effect01Text = (TextView) findViewById(R.id.effect_effect_text_01);
        effect02Button = (ImageButton) findViewById(R.id.effect_effect_02);
        effect02Text = (TextView) findViewById(R.id.effect_effect_text_02);
        effect03Button = (ImageButton) findViewById(R.id.effect_effect_03);
        effect03Text = (TextView) findViewById(R.id.effect_effect_text_03);
        effect04Button = (ImageButton) findViewById(R.id.effect_effect_04);
        effect05Button = (ImageButton) findViewById(R.id.effect_effect_05);
        effect00Button.setOnClickListener(this);
        effect01Button.setOnClickListener(this);
        effect02Button.setOnClickListener(this);
        effect03Button.setOnClickListener(this);
        effect04Button.setOnClickListener(this);
        effect05Button.setOnClickListener(this);

    }

    private void setDefaultArea() {
        //duration
        if(duration > 3000){
            leftTime = duration/2-1500;
            rightTime = duration/2+1500;

            float leftTimePer = culTimeToPer(leftTime);
            float rightTimePer = culTimeToPer(rightTime);

            effectControlView.setStart(screenWidth*leftTimePer);
            effectControlView.setEnd(screenWidth*rightTimePer);

        }
    }

    private void getIntentData(){
        Intent intent = getIntent();
        if(intent != null){
            duration = intent.getExtras().getLong(S.KEY_EFFECT_DURATION);
            frameRate = intent.getExtras().getLong(S.KEY_EFFECT_FRAME_RATE);
            fromWhat = intent.getExtras().getBoolean(S.KEY_EFFECT_FROM_WHAT);
            ratioState = intent.getExtras().getInt(S.KEY_EFFECT_RATIO);
            vPath = intent.getExtras().getString(S.KEY_INPUT_PATH);


            Toast.makeText(this, "FRATE: "+frameRate, Toast.LENGTH_SHORT).show();
        }
    }


    private void goIntentExport(){
        Intent intent = new Intent(getApplicationContext(), ExportActivity.class);
        Log.d(TAG, "GO");
        intent.putExtra(S.KEY_EFFECT_FRAME_RATE, frameRate);
        intent.putExtra(S.KEY_EFFECT_LEFT_TIME, leftTime);
        intent.putExtra(S.KEY_EFFECT_RIGHT_TIME, rightTime);
        intent.putExtra(S.KEY_EFFECT_STATE, renderer.getEffectState());
        startActivity(intent);
    }

    private void startDecoding(SurfaceTexture surfaceTexture){
        decoder = new EffectDecoder();
        audioDecoder = new EffectAudioDecoder();

        decoder.setLeftTime(duration/2 - duration/6);
        decoder.setRightTime(duration/2 + duration/6);

        decoder.setOnEffectDecoderListener(effectDecoderListener);

        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);

        if(decoder.init(surface, vPath, frameRate)){
            Log.d(TAG, "vPath: "+vPath);
            audioDecoder.init(vPath);
            decoder.start();
            audioDecoder.start();
        }else{
            surface.release();
            decoder.close();
            audioDecoder.close();
        }
    }


    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        effectSurface.requestRender();
    }

    public class EffectGLRenderer implements GLSurfaceView.Renderer{

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

        private int screenWidth;

        public final static int EFFECT_ORIGINAL = -1;
        public final static int EFFECT_REPEAT = 0;
        public final static int EFFECT_ZOOM = 1;
        public final static int EFFECT_CROP = 2;
        public final static int EFFECT_LEFT_RIGHT = 3;
        public final static int EFFECT_ROLLING = 4;
        public final static int EFFECT_ZOOM_MOVE = 5;
        private int effectState = -1;
        private boolean isEffectArea;

        public final static int RATIO_SQUARE = 0;
        public final static int RATIO_CIRCLE = 1;
        private int ratioState = RATIO_SQUARE;




        /* for Circle stencil */
        private Context context;
        private Model model;
        private Shader shader;

        public EffectGLRenderer(Context context, int screenWidth, int ratioState) {
            this.context = context;

            this.screenWidth = screenWidth;
            this.isEffectArea = false;
            this.ratioState = ratioState;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {


            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mTextureId = mTexProgram.createTextureObject();
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mRect.setTexture(mTextureId);
            mRect1.setTexture(mTextureId);
            mRect2.setTexture(mTextureId);

            GLES20.glViewport(0, 0, effectSurface.getWidth(), effectSurface.getWidth());

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

            if(mSurfaceTexture != null)
                startDecoding(mSurfaceTexture);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }


        long frameCount;
        int zoomFactor;
        float angle;
        boolean turnZoom = false;
        @Override
        public void onDrawFrame(GL10 gl) {
            mSurfaceTexture.updateTexImage();


            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.5f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
            if(isEffectArea){
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

        public void setEffectState(int state){
            if(state == EFFECT_ORIGINAL){
                this.effectState = EFFECT_ORIGINAL;
                decoder.setPlayFirst();

                decoder.setEffectState(this.effectState);
            }else if(state == EFFECT_LEFT_RIGHT){
                this.effectState = EFFECT_LEFT_RIGHT;

                decoder.setPlayFirst();
            }else if(state == EFFECT_CROP){
                this.effectState = EFFECT_CROP;
                decoder.setPlayFirst();

                decoder.setEffectState(this.effectState);
            }else if(state == EFFECT_ZOOM){
                this.effectState = EFFECT_ZOOM;
                decoder.setPlayFirst();

                decoder.setEffectState(this.effectState);
            }else if(state == EFFECT_ZOOM_MOVE){
                this.effectState = EFFECT_ZOOM_MOVE;
                decoder.setPlayFirst();

                decoder.setEffectState(this.effectState);
            }else if(state == EFFECT_ROLLING){
                this.effectState = EFFECT_ROLLING;
                decoder.setPlayFirst();

                decoder.setEffectState(this.effectState);
            }
        }

        public int getEffectState(){
            return effectState;
        }

        public void setIsEffectArea(boolean isEffectArea){
            this.isEffectArea = isEffectArea;
        }

    }

}
