package me.ggum.gum.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import me.ggum.gum.R;
import me.ggum.gum.data.VideoInputData;
import me.ggum.gum.decoder.PreAudioDecoder;
import me.ggum.gum.decoder.PreDecoder;
import me.ggum.gum.gles.Drawable2d;
import me.ggum.gum.gles.Model;
import me.ggum.gum.gles.ScaledDrawable2d;
import me.ggum.gum.gles.Shader;
import me.ggum.gum.gles.Sprite2d;
import me.ggum.gum.gles.Texture2dProgram;
import me.ggum.gum.string.S;
import me.ggum.gum.view.HoverImageButton;
import me.ggum.gum.view.TrimControlView;
import me.ggum.gum.view.TrimPlayBarView;
import wseemann.media.FFmpegMediaMetadataRetriever;

public class PreviewActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener{
    private static final String TAG = "PreviewActivity";

    private ImageView[] thumbImg;
    private int[] thumbImgRes = { R.id.preview_thumbimg_01, R.id.preview_thumbimg_02, R.id.preview_thumbimg_03, R.id.preview_thumbimg_04, R.id.preview_thumbimg_05 };

    private HoverImageButton ratioSwitch;
    private HoverImageButton backBtn;
    private HoverImageButton completeBtn;

    private GLSurfaceView previewSurface;
    private PreviewRenderer renderer;
    private TrimControlView trimControlView;
    private TrimPlayBarView playBarView;
    private TextView timeText;
    private float timeControlViewWidth;

    private VideoInputData data;

    private int screenWidth;

    private MediaPlayer mp;

    private Surface surface;

    private float startPer, endPer, midDifferPer;
    private long startTime, endTime, midTime;





    private TrimControlView.OnVideoTimeChangeListener videoTimeChangeListener = new TrimControlView.OnVideoTimeChangeListener() {
        @Override
        public void onViewSizeOK(float start, float end, float midDffer, float width) {
            Log.d(TAG, "onViewSizeOK"+start+", "+end+", "+midDffer);

            timeControlViewWidth = width;


        }

        @Override
        public void onStartVideoTimeChange() {
            Log.d(TAG, "onStartVideoTimeChange");
        }

        @Override
        public void onVideoTimeChange(float start, float end, float midDiffer, int touchState) {
            Log.d(TAG, "onVideoTimeChange"+start+", "+end+", "+midDiffer);
            startPer = culXtoPer(start);
            endPer = culXtoPer(end);
            midDifferPer = culXtoPer(midDiffer);

            startTime = (long) (data.getDuration()*startPer);
            endTime = (long) (data.getDuration()*endPer);
            midTime = (long) (data.getDuration()*midDifferPer);



            if(touchState == TrimControlView.LEFT_TOUCHED){

                playBarView.setPlayPoint((float)startTime/(float)data.getDuration());

                timeText.setText(getTimeText(endTime - startTime));

                mp.seekTo((int)startTime);


            }else if(touchState == TrimControlView.RIGHT_TOUCHED){

                playBarView.setPlayPoint((float)endTime/(float)data.getDuration());

                timeText.setText(getTimeText(endTime - startTime));

                mp.seekTo((int)endTime);


            }else if(touchState == TrimControlView.MID_TOUCHED){

                playBarView.setPlayPoint((float)(startTime+midTime)/(float)data.getDuration());

                timeText.setText(getTimeText(endTime - startTime));

                mp.seekTo((int)startTime);

            }


            Log.d(TAG, "onVideoTimeChange "+startTime+", "+endTime+", "+midTime);

        }

        @Override
        public void onFinishVideoTimeChange() {
            Log.d(TAG, "onFinishVideoTimeChange");
            mp.seekTo((int)startTime);

        }
    };

    private View.OnClickListener ratioSwitchListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(renderer.getRatioState() == PreviewRenderer.RATIO_SQUARE){
                previewSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setRatioState(PreviewRenderer.RATIO_CIRCLE);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ratioSwitch.setImageResource(R.drawable.trim_ratio_circle);
                            }
                        });

                    }
                });
            }else if(renderer.getRatioState() == PreviewRenderer.RATIO_CIRCLE){
                previewSurface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        renderer.setRatioState(PreviewRenderer.RATIO_SQUARE);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ratioSwitch.setImageResource(R.drawable.trim_ratio_square);
                            }
                        });
                    }
                });
            }
        }
    };

    private View.OnClickListener backListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    private View.OnClickListener completeListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), PreRenderActivity.class);
            intent.putExtra(S.KEY_PRERENDER_START_TIME, startTime);
            intent.putExtra(S.KEY_PRERENDER_END_TIME, endTime);
            intent.putExtra(S.KEY_PRERENDER_OBJECT, data);
            intent.putExtra(S.KEY_PRERENDER_RATIO, renderer.getRatioState());
            startActivity(intent);
            finish();
        }
    };

    private float culXtoPer(float x){
        return x/(timeControlViewWidth);
    }

    private float culTimeToPer(long time){
        return (float)time/(float)data.getDuration();
    }

    private String getTimeText(long time){
        if(time/1000 >= 10){
            return "0:"+Long.toString(time/1000);
        }else{
            return "0:0"+Long.toString(time/1000);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        init();
    }

    @Override
    protected void onPause() {
        if(mp != null){
            mp.pause();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if(mp != null){
            mp.stop();
            mp.release();
            mp = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        if(mp != null){
            mp.start();
        }
        super.onResume();
    }

    private void init(){
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;

        data = getIntentVideoData();

        startTime = data.getDuration()/2-1500;
        endTime = data.getDuration()/2+1500;

        thumbImg = new ImageView[5];

        for(int i=0; i< thumbImg.length; i++){
            thumbImg[i] = (ImageView) findViewById(thumbImgRes[i]);
        }
        updateThumbImg(data.getPath(), data.getDuration(), data.getOrientation());

        trimControlView = (TrimControlView) findViewById(R.id.preview_trim_control);
        trimControlView.setOnVideoTimeChangeListener(videoTimeChangeListener);
        playBarView = (TrimPlayBarView) findViewById(R.id.preview_playbar_view);
        previewSurface = (GLSurfaceView) findViewById(R.id.preview_surf);
        previewSurface.setEGLContextClientVersion(2);
        renderer = new PreviewRenderer(data, this, screenWidth);
        previewSurface.setRenderer(renderer);
        previewSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        timeText = (TextView) findViewById(R.id.preview_time_text);
        ratioSwitch = (HoverImageButton) findViewById(R.id.preview_ratio_switch);
        ratioSwitch.setOnClickListener(ratioSwitchListener);
        backBtn = (HoverImageButton) findViewById(R.id.preview_back);
        backBtn.setOnClickListener(backListener);
        completeBtn = (HoverImageButton) findViewById(R.id.preview_complete);
        completeBtn.setOnClickListener(completeListener);
    }

    private void updateThumbImg(String path, long duration, final int orientation){
        final long[] thumbTime = new long[5];
        for(int i=0; i<thumbTime.length; i++){
            thumbTime[i] = ((duration/thumbTime.length)*i + 100)*1000;
        }

        new AsyncTask<String, Void, Bitmap>(){
            @Override
            protected Bitmap doInBackground(String... params) {
                FFmpegMediaMetadataRetriever thumbRetriever = new FFmpegMediaMetadataRetriever();
                thumbRetriever.setDataSource(params[0]);
                Bitmap bm = thumbRetriever.getFrameAtTime(thumbTime[0], FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                thumbRetriever.release();

                if(orientation == 90){
                    Matrix m = new Matrix();
                    m.postRotate(90);

                    Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    return resizedBitmap;
                }

                return bm;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
                    Drawable thumbDraw = new BitmapDrawable(bitmap);
                    thumbImg[0].setBackground(thumbDraw);
                }
            }
        }.execute(path);
        new AsyncTask<String, Void, Bitmap>(){
            @Override
            protected Bitmap doInBackground(String... params) {
                FFmpegMediaMetadataRetriever thumbRetriever = new FFmpegMediaMetadataRetriever();
                thumbRetriever.setDataSource(params[0]);
                Bitmap bm = thumbRetriever.getFrameAtTime(thumbTime[1], FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                thumbRetriever.release();
                Log.d(TAG, "OK");
                if(orientation == 90){
                    Matrix m = new Matrix();
                    m.postRotate(90);

                    Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    return resizedBitmap;
                }

                return bm;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
                    Drawable thumbDraw = new BitmapDrawable(bitmap);
                    thumbImg[1].setBackground(thumbDraw);
                }
            }
        }.execute(path);
        new AsyncTask<String, Void, Bitmap>(){
            @Override
            protected Bitmap doInBackground(String... params) {
                FFmpegMediaMetadataRetriever thumbRetriever = new FFmpegMediaMetadataRetriever();
                thumbRetriever.setDataSource(params[0]);
                Bitmap bm = thumbRetriever.getFrameAtTime(thumbTime[2], FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                thumbRetriever.release();

                if(orientation == 90){
                    Matrix m = new Matrix();
                    m.postRotate(90);

                    Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    return resizedBitmap;
                }

                return bm;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
                    Drawable thumbDraw = new BitmapDrawable(bitmap);
                    thumbImg[2].setBackground(thumbDraw);
                }
            }
        }.execute(path);
        new AsyncTask<String, Void, Bitmap>(){
            @Override
            protected Bitmap doInBackground(String... params) {
                FFmpegMediaMetadataRetriever thumbRetriever = new FFmpegMediaMetadataRetriever();
                thumbRetriever.setDataSource(params[0]);
                Bitmap bm = thumbRetriever.getFrameAtTime(thumbTime[3], FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                thumbRetriever.release();

                if(orientation == 90){
                    Matrix m = new Matrix();
                    m.postRotate(90);

                    Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    return resizedBitmap;
                }

                return bm;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
                    Drawable thumbDraw = new BitmapDrawable(bitmap);
                    thumbImg[3].setBackground(thumbDraw);
                }
            }
        }.execute(path);
        new AsyncTask<String, Void, Bitmap>(){
            @Override
            protected Bitmap doInBackground(String... params) {
                FFmpegMediaMetadataRetriever thumbRetriever = new FFmpegMediaMetadataRetriever();
                thumbRetriever.setDataSource(params[0]);
                Bitmap bm = thumbRetriever.getFrameAtTime(thumbTime[4], FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                thumbRetriever.release();

                if(orientation == 90){
                    Matrix m = new Matrix();
                    m.postRotate(90);

                    Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    return resizedBitmap;
                }

                return bm;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
                    Drawable thumbDraw = new BitmapDrawable(bitmap);
                    thumbImg[4].setBackground(thumbDraw);
                }
            }
        }.execute(path);

    }

    private VideoInputData getIntentVideoData(){
        Intent intent = getIntent();
        return new VideoInputData(intent.getExtras().getString(S.KEY_INPUT_PATH),
                intent.getExtras().getLong(S.KEY_INPUT_DURATION),
                intent.getExtras().getInt(S.KEY_INPUT_WIDTH),
                intent.getExtras().getInt(S.KEY_INPUT_HEIGHT),
                intent.getExtras().getDouble(S.KEY_INPUT_FRAME_RATE),
                intent.getExtras().getInt(S.KEY_INPUT_ORIENTATION));
    }

    private void startDecoding(SurfaceTexture surfaceTexture){

        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);

        mp = new MediaPlayer();

        try{
            mp.setDataSource(data.getPath());
            mp.setSurface(surface);
            mp.prepare();

            mp.seekTo((int)startTime);
            mp.start();


        }catch(IOException e){
            e.printStackTrace();
        }

    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        previewSurface.requestRender();
    }

    class PreviewRenderer implements GLSurfaceView.Renderer{

        private VideoInputData data;
        private int screenWidth;

        private SurfaceTexture mSurfaceTexture;
        private final float[] mDisplayProjectionMatrix = new float[16];
        private int mTextureId;

        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect= new Sprite2d(mRectDrawable);

        private float mPosX, mPosY;

        public final static int RATIO_SQUARE = 0;
        public final static int RATIO_CIRCLE = 1;
        private int ratioState = RATIO_SQUARE;

        /* for Circle stencil */
        private Context context;
        private Model model;
        private Shader shader;


        public PreviewRenderer(VideoInputData data, Context context, int screenWidth) {
            this.data = data;
            this.context = context;
            this.screenWidth = screenWidth;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mTextureId = mTexProgram.createTextureObject();

            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mRect.setTexture(mTextureId);

            GLES20.glViewport(0, 0, previewSurface.getWidth(), previewSurface.getWidth());

            shader = new Shader(context);
            model = new Model(context, shader);

            mPosX = screenWidth/2;
            mPosY = screenWidth/2;

            android.opengl.Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, screenWidth, 0, screenWidth, -1, 1);
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
                mp.pause();
                mp.seekTo((int)startTime);
                mp.start();
            }

            playBarView.setPlayPoint((float)mp.getCurrentPosition()/(float)data.getDuration());

            mSurfaceTexture.updateTexImage();

            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.5f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);

            if(ratioState == RATIO_CIRCLE){
                shader.setShader();
                shader.setShaderParameters();
                model.draw();
            }
        }

        public void setRatioState(int state){
            ratioState = state;
        }

        public int getRatioState(){
            return ratioState;
        }
    }
}
