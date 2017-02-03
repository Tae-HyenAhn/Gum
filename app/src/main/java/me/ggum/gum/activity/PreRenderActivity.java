package me.ggum.gum.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
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
import me.ggum.gum.data.VideoInputData;
import me.ggum.gum.decoder.PreAudioDecoder;
import me.ggum.gum.decoder.PreDecoder;
import me.ggum.gum.encoder_trim.TextureMovieEncoder;
import me.ggum.gum.encoder_trim.TrimMuxerWrapper;
import me.ggum.gum.gles.Drawable2d;
import me.ggum.gum.gles.Model;
import me.ggum.gum.gles.ScaledDrawable2d;
import me.ggum.gum.gles.Shader;
import me.ggum.gum.gles.Sprite2d;
import me.ggum.gum.gles.Texture2dProgram;
import me.ggum.gum.string.S;
import me.ggum.gum.utils.FileUtil;
import wseemann.media.FFmpegMediaMetadataRetriever;

public class PreRenderActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener{
    private static final String TAG = "PreRenderActivity";

    private GLSurfaceView preRenderSurface;
    private PreRenderer renderer;

    private VideoInputData data;

    private int screenWidth;

    private Surface surface;

    private TextureMovieEncoder encoder;

    private int ratioState;

    private MediaPlayer mp;
    private PreAudioDecoder audioDecoder;

    private TrimMuxerWrapper muxerWrapper;

    private long startTime, endTime;

    private TrimMuxerWrapper.OnMuxerListener muxerListener = new TrimMuxerWrapper.OnMuxerListener() {
        @Override
        public void onMuxerStop() {
            Log.d(TAG, "muxer_stop_go_next");
            FFmpegMediaMetadataRetriever fmmr = new FFmpegMediaMetadataRetriever();
            fmmr.setDataSource(FileUtil.generateTempOutputPath());
            long duration = Long.parseLong(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION));

            goEffectPage(duration, (long)data.getFrameRate(), false);
            finish();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pre_render);
        init();
    }

    @Override
    public void onBackPressed() {
        //
    }

    @Override
    protected void onPause() {
        /*
        if(mp != null){
            mp.stop();
            mp.release();
            mp = null;
        }

        if(audioDecoder != null){
            audioDecoder.close();
            encoder.stopRecording();
        }
*/

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void init(){
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        try {
            muxerWrapper = new TrimMuxerWrapper(new MediaMuxer(FileUtil.generateTempOutputPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4), 2);
            muxerWrapper.setOnMuxerListener(muxerListener);
        } catch (IOException e) {
            e.printStackTrace();
        }
        encoder = new TextureMovieEncoder(this, muxerWrapper);


        data = getIntentVideoData();
        preRenderSurface = (GLSurfaceView) findViewById(R.id.pre_render_surface);
        preRenderSurface.setEGLContextClientVersion(2);
        preRenderSurface.setZOrderOnTop(false);
        renderer = new PreRenderer(this, data, screenWidth, encoder, ratioState);
        preRenderSurface.setRenderer(renderer);
        preRenderSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);


    }

    private void goEffectPage(long duration, long frameRate, boolean fromWhat){
        Intent intent = new Intent(getApplicationContext(), EffectActivity.class);
        intent.putExtra(S.KEY_EFFECT_DURATION, duration);
        intent.putExtra(S.KEY_EFFECT_FRAME_RATE, frameRate);
        intent.putExtra(S.KEY_EFFECT_FROM_WHAT, fromWhat);
        intent.putExtra(S.KEY_INPUT_PATH, FileUtil.generateTempOutputPath());
        startActivity(intent);
    }

    private VideoInputData getIntentVideoData(){
        Intent intent = getIntent();

        ratioState = intent.getExtras().getInt(S.KEY_PRERENDER_RATIO);
        startTime = intent.getExtras().getLong(S.KEY_PRERENDER_START_TIME);
        endTime = intent.getExtras().getLong(S.KEY_PRERENDER_END_TIME);
        return (VideoInputData) intent.getExtras().get(S.KEY_PRERENDER_OBJECT);
    }

    private void startDecoding(SurfaceTexture surfaceTexture){

        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);

        mp = new MediaPlayer();
        audioDecoder = new PreAudioDecoder();
        mp.setOnPreparedListener(mpPreparedListener);
        audioDecoder.setOnPreAudioDecoderListener(audioListener);
        try{
            mp.setDataSource(data.getPath());
            audioDecoder.init(data.getPath(), startTime, endTime, muxerWrapper);

            mp.setSurface(surface);
            mp.prepare();

            mp.seekTo((int)startTime);

            audioDecoder.start();


        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private PreAudioDecoder.OnPreAudioDecoderListener audioListener = new PreAudioDecoder.OnPreAudioDecoderListener() {
        @Override
        public void onStartAudioDecode() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mp.start();
                }
            });

        }
    };



    private MediaPlayer.OnPreparedListener mpPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            preRenderSurface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    renderer.startEncoding();
                }
            });
        }
    };

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        preRenderSurface.requestRender();
    }

    public class PreRenderer implements GLSurfaceView.Renderer{

        private static final String TAG = "PreRenderer";

        private VideoInputData data;


        private TextureMovieEncoder mVideoEncoder;

        private String tempPath;

        private SurfaceTexture mSurfaceTexture;
        private final float[] mDisplayProjectionMatrix = new float[16];
        private int mTextureId;

        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect= new Sprite2d(mRectDrawable);

        private float mPosX, mPosY;

        private int screenWidth;

        public final static int RATIO_SQUARE = 0;
        public final static int RATIO_CIRCLE = 1;
        private int ratioState = RATIO_SQUARE;

        /* for Circle stencil */
        private Context context;
        private Model model;
        private Shader shader;

        private boolean first;

        public PreRenderer(Context context, VideoInputData data, int screenWidth, TextureMovieEncoder encoder, int state) {
            this.context = context;
            this.data = data;
            this.screenWidth = screenWidth;
            this.mVideoEncoder = encoder;
            this.tempPath = FileUtil.generateTempOutputPath();
            this.ratioState = state;
            first = true;

        }


        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mTextureId = mTexProgram.createTextureObject();

            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mRect.setTexture(mTextureId);

            GLES20.glViewport(0, 0, preRenderSurface.getWidth(), preRenderSurface.getWidth());

            shader = new Shader(context);
            model = new Model(context, shader);

            mPosX = screenWidth/2;
            mPosY = screenWidth/2;

            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, screenWidth, 0, screenWidth, -1, 1);
            if(data.getOrientation() == 90){
                mRect.setRotation(-90);
            }else{
                mRect.setRotation(0);
            }

            mRect.setScale(data.getWidth()*screenWidth/data.getHeight(), screenWidth);
            mRect.setPosition(mPosX, mPosY);

            if(mSurfaceTexture != null){
                startDecoding(mSurfaceTexture);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }

        @Override
        public void onDrawFrame(GL10 gl) {

            if(mp.getCurrentPosition() >= (int)endTime){
                if(first){
                    stopEncoding();
                    audioDecoder.close();
                    mp.pause();
                    first = false;
                }

            }

            mSurfaceTexture.updateTexImage();

            mVideoEncoder.setTextureId(mTextureId);
            mVideoEncoder.frameAvailable(mSurfaceTexture);

            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.5f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);

            if(ratioState == RATIO_CIRCLE){
                shader.setShader();
                shader.setShaderParameters();
                model.draw();
            }
        }

        public void startEncoding(){
            mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(FileUtil.generateTempOutputPath(),
                    720, 720, 12000000, EGL14.eglGetCurrentContext(), data.getPath()));
            mVideoEncoder.setScreenSize(screenWidth, screenWidth);
            mVideoEncoder.setOrientation(data.getOrientation());
            mVideoEncoder.setVideoSize(data.getWidth(), data.getHeight());
        }

        public void stopEncoding(){
            mVideoEncoder.stopRecording();
        }


    }
}
