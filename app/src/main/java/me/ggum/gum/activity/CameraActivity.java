package me.ggum.gum.activity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.ThumbnailUtils;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.BreakIterator;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import me.ggum.gum.R;
import me.ggum.gum.encoder.TextureMovieEncoder;
import me.ggum.gum.gles.Drawable2d;
import me.ggum.gum.gles.Model;
import me.ggum.gum.gles.ScaledDrawable2d;
import me.ggum.gum.gles.Shader;
import me.ggum.gum.gles.Sprite2d;
import me.ggum.gum.gles.Texture2dProgram;
import me.ggum.gum.string.S;
import me.ggum.gum.utils.CameraUtil;
import me.ggum.gum.utils.FileUtil;
import me.ggum.gum.view.HoverImageButton;
import wseemann.media.FFmpegMediaMetadataRetriever;

public class CameraActivity extends AppCompatActivity implements SurfaceTexture.OnFrameAvailableListener{

    private static final String TAG = "CameraActivity";

    public static final int FRONT_CAMERA = 0;
    public static final int BACK_CAMERA = 1;
    private static final int FLASH_ON = 0;
    private static final int FLASH_OFF = 1;

    private int cameraFacingState;
    private int cameraFlashState;

    private static final int CAMERA_PREVIEW_WIDTH = 1280;
    private static final int CAMERA_PREVIEW_HEIGHT = 720;

    private static final boolean CAMERA_RECORD_STATE_ON = true;
    private static final boolean CAMERA_RECORD_STATE_OFF = false;


    private boolean recordState = false;

    private GLSurfaceView cameraSurface;
    private CameraSurfaceRenderer renderer;
    private Camera camera;
    private CameraHandler handler;

    private int screenWidth, screenHeight;

    private HoverImageButton flashSwitchBtn, ratioSwitchBtn;
    private HoverImageButton pickerBtn, cameraTurnBtn, recordBtn;
    private TextView timeText;

    private boolean mRecordingEnabled;
    private TextureMovieEncoder encoder;


    private CountDownTimer timer;
    private int seconds;

    private ContentResolver contentResolver;

    private boolean isCanceled;

    /*  Listeners   */

    private View.OnClickListener flashSwitchListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cameraFlashState = cameraFlashSwitch(cameraFlashState);
            updateFlashUI();
        }
    };
    private View.OnClickListener ratioSwitchListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cameraSurface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    if(renderer.ratioState == CameraSurfaceRenderer.RATIO_SQUARE){
                        renderer.ratioState = renderer.setRatioState(CameraSurfaceRenderer.RATIO_CIRCLE);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ratioSwitchBtn.setImageResource(R.drawable.camera_ratio_circle);
                            }
                        });

                    }else if(renderer.ratioState == CameraSurfaceRenderer.RATIO_CIRCLE){
                        renderer.ratioState = renderer.setRatioState(CameraSurfaceRenderer.RATIO_SQUARE);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ratioSwitchBtn.setImageResource(R.drawable.camera_ratio_square);
                            }
                        });

                    }
                }
            });
        }
    };


    private View.OnClickListener pickerListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), PickerActivity.class);
            startActivity(intent);
            finish();
        }
    };
    private View.OnClickListener cameraTurnListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            releaseCamera();
            cameraSurface.onPause();

            if(cameraFacingState == FRONT_CAMERA){
                cameraFacingState = openCamera(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT, BACK_CAMERA);

            }else if(cameraFacingState == BACK_CAMERA){
                cameraFacingState = openCamera(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT, FRONT_CAMERA);
            }

            cameraSurface.onResume();
            updateFlashUI();
        }
    };

    private View.OnClickListener recordListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(recordState == CAMERA_RECORD_STATE_ON){
                updateUIWhenRecord(false);
                switchEncoding();
                recordState = CAMERA_RECORD_STATE_OFF;
                timer.cancel();
                seconds = 0;


            }else if(recordState == CAMERA_RECORD_STATE_OFF){
                updateUIWhenRecord(true);
                switchEncoding();
                isCanceled = false;
                recordState = CAMERA_RECORD_STATE_ON;
                timer = new CountDownTimer(1000*21, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        seconds++;
                        if(seconds < 10){
                            timeText.setText("0:0"+String.valueOf(seconds));
                        }else{
                            timeText.setText("0:"+String.valueOf(seconds));
                        }
                    }

                    @Override
                    public void onFinish() {
                        Toast.makeText(getApplicationContext(), "FINISH", Toast.LENGTH_SHORT).show();
                        seconds = 0;
                        timeText.setText("0:00");
                        switchEncoding();
                        recordState = CAMERA_RECORD_STATE_OFF;
                        updateRecordButton();
                    }
                }.start();
            }
            updateRecordButton();
        }
    };

    private TextureMovieEncoder.OnEncoderListener encoderListener = new TextureMovieEncoder.OnEncoderListener() {
        @Override
        public void onEncoderStop() {
            if(!isCanceled){
                FFmpegMediaMetadataRetriever fmmr = new FFmpegMediaMetadataRetriever();
                fmmr.setDataSource(FileUtil.generateTempOutputPath());
                double frameRate = Double.parseDouble(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE));
                long duration = Long.parseLong(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION));
                fmmr.release();
                goEffectPage(duration, frameRate, true);
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        Log.d(TAG, "START");
        init();
    }

    @Override
    public void onBackPressed() {
        if(recordState == CAMERA_RECORD_STATE_ON){

        }else if(recordState == CAMERA_RECORD_STATE_OFF){
            super.onBackPressed();
        }
    }



    @Override
    protected void onPause() {

        if(recordState == CAMERA_RECORD_STATE_ON){
            updateUIWhenRecord(false);
            isCanceled = true;
            cameraSurface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    renderer.cancelRecord();
                }
            });
            recordState = CAMERA_RECORD_STATE_OFF;
            timer.cancel();
            seconds = 0;

        }

        Log.d(TAG, "PAUSE!!");
        releaseCamera();

        cameraSurface.onPause();

        finish();
        super.onPause();
    }

    private void goEffectPage(long duration, double frameRate, boolean fromWhat){
        Intent intent = new Intent(getApplicationContext(), EffectActivity.class);
        intent.putExtra(S.KEY_EFFECT_DURATION, duration);
        intent.putExtra(S.KEY_EFFECT_FRAME_RATE, frameRate);
        intent.putExtra(S.KEY_EFFECT_FROM_WHAT, fromWhat);
        intent.putExtra(S.KEY_INPUT_PATH, FileUtil.generateTempOutputPath());
        intent.putExtra(S.KEY_EFFECT_RATIO, renderer.ratioState);
        startActivity(intent);
    }

    private void updateUIWhenRecord(boolean recordState){
        if(recordState){
            ratioSwitchBtn.setClickable(false);
            ratioSwitchBtn.setAlpha(0.5f);
            cameraTurnBtn.setClickable(false);
            cameraTurnBtn.setAlpha(0.5f);
            pickerBtn.setClickable(false);
            pickerBtn.setAlpha(0.5f);
        }else{
            ratioSwitchBtn.setClickable(true);
            ratioSwitchBtn.setAlpha(1.0f);
            cameraTurnBtn.setClickable(true);
            cameraTurnBtn.setAlpha(1.0f);
            pickerBtn.setClickable(true);
            pickerBtn.setAlpha(1.0f);
        }
    }

    private void init(){

        getScreenSize();
        cameraFacingState = BACK_CAMERA;
        cameraFlashState = FLASH_OFF;

        isCanceled = false;

        handler = new CameraHandler(this);
        encoder = new TextureMovieEncoder(this);
        encoder.setOnEncoderListener(encoderListener);
        flashSwitchBtn = (HoverImageButton) findViewById(R.id.camera_flash_switch);
        ratioSwitchBtn = (HoverImageButton) findViewById(R.id.camera_ratio_switch);
        pickerBtn = (HoverImageButton) findViewById(R.id.camera_picker_btn);
        recordBtn = (HoverImageButton) findViewById(R.id.camera_record_btn);
        cameraTurnBtn = (HoverImageButton) findViewById(R.id.camera_turn_btn);
        cameraSurface = (GLSurfaceView) findViewById(R.id.camera_glsurfaceview);
        cameraSurface.setEGLContextClientVersion(2);
        renderer = new CameraSurfaceRenderer(handler, this, getTempPathFromIntent(), encoder);
        cameraSurface.setRenderer(renderer);
        cameraSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        timeText = (TextView) findViewById(R.id.camera_time_text);

        pickerBtn.setOnClickListener(pickerListener);
        flashSwitchBtn.setOnClickListener(flashSwitchListener);
        ratioSwitchBtn.setOnClickListener(ratioSwitchListener);
        cameraTurnBtn.setOnClickListener(cameraTurnListener);
        recordBtn.setOnClickListener(recordListener);


        updatePickerThumbImg(getCameraPickerThumbnail());

    }

    private Bitmap getCameraPickerThumbnail(){
        contentResolver = getContentResolver();

        String[] projection = { MediaStore.Video.VideoColumns.DATA };
        String sortOrderDESC = MediaStore.Video.VideoColumns.DATE_TAKEN + " COLLATE LOCALIZED DESC";

        Cursor videoCursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null, sortOrderDESC);

        int dataColumnIndex = videoCursor.getColumnIndex(projection[0]);

        String videoPath = "";

        Bitmap bitmap = null;
        while(bitmap == null){
            videoCursor.moveToNext();
            videoPath = videoCursor.getString(dataColumnIndex);
            bitmap = createThumbnailFromPath(videoPath);
        }

        return bitmap;
    }

    public Bitmap createThumbnailFromPath(String filePath){
        Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);

        if(bitmap != null){
            return bitmap;
        }else{
            return null;
        }

    }

    private void updatePickerThumbImg(Bitmap bitmap){


        new AsyncTask<Bitmap, Void, Bitmap>(){
            @Override
            protected Bitmap doInBackground(Bitmap... params) {
                Bitmap temp = params[0];
                return getRoundedBitmap(temp);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                pickerBtn.setImageBitmap(bitmap);
                super.onPostExecute(bitmap);
            }
        }.execute(bitmap);


    }

    public static Bitmap getRoundedBitmap(Bitmap bitmap) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final int color = Color.GRAY;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 12;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        bitmap.recycle();

        return output;
    }

    private String getTempPathFromIntent(){
        return getIntent().getExtras().getString(S.KEY_TEMP_PATH);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openCamera(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT, BACK_CAMERA);

        cameraSurface.onResume();

    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.invalidateHandler();
    }

    private void getScreenSize(){
        DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    private void updateRecordButton(){
        if(recordState == CAMERA_RECORD_STATE_OFF){
            recordBtn.setImageResource(R.drawable.camera_record_off_state);
            recordBtn.clearAnimation();

        }else if(recordState == CAMERA_RECORD_STATE_ON){
            recordBtn.setImageResource(R.drawable.camera_record_on_state);

            Animation anim = AnimationUtils.loadAnimation(this, R.anim.reocord_rotate_anim);
            recordBtn.startAnimation(anim);

        }
    }

    private void updateFlashUI(){
        if(cameraFacingState == BACK_CAMERA){
            flashSwitchBtn.setEnabled(true);
            if(cameraFlashState == FLASH_ON){
                flashSwitchBtn.setImageResource(R.drawable.camera_flash_on_state);
            }else if(cameraFlashState == FLASH_OFF){
                flashSwitchBtn.setImageResource(R.drawable.camera_flash_off_state);
            }
        }else if(cameraFacingState == FRONT_CAMERA){
            flashSwitchBtn.setImageResource(R.drawable.camera_flash_off_state);
            cameraFlashState = FLASH_OFF;
            flashSwitchBtn.setEnabled(false);
        }
    }

    private int cameraFlashSwitch(int state){

        if(state == FLASH_OFF){
            Camera.Parameters param = camera.getParameters();

            param.setFlashMode("torch");
            camera.setParameters(param);

            return FLASH_ON;
        }else{
            Camera.Parameters param = camera.getParameters();

            param.setFlashMode("off");
            camera.setParameters(param);

            return FLASH_OFF;
        }

    }

    private int openCamera(int desireWidth, int desireHeight, int cameraFacing){
        if(camera != null){
            throw new RuntimeException("camera already inited");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        int numCameras = Camera.getNumberOfCameras();

        if(cameraFacing == FRONT_CAMERA){
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    Log.d("OPEN", "CAMERA");
                    camera = Camera.open(i);
                    break;
                }
            }
        }else if(cameraFacing == BACK_CAMERA){
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    Log.d("OPEN", "CAMERA");
                    camera = Camera.open(i);
                    Log.d(TAG, "CAMERA_OPEN!!!");
                    break;
                }
            }
        }

        if(camera == null){
            camera = Camera.open();
        }

        if(camera == null){
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = camera.getParameters();
        CameraUtil.choosePreviewSize(parms, desireWidth, desireHeight);

        parms.setRecordingHint(true);
        camera.setParameters(parms);



        return cameraFacing;

    }

    private void releaseCamera(){
        if(camera != null){
            camera.stopPreview();
            camera.release();
            camera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    private void handleSetSurfaceTexture(SurfaceTexture st){
        st.setOnFrameAvailableListener(this);
        try{
            Log.d(TAG, camera.toString());
            camera.setPreviewTexture(st);
        }catch (IOException ioe){
            throw new RuntimeException(ioe);
        }
        camera.startPreview();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        cameraSurface.requestRender();
    }

    static class CameraHandler extends Handler {

        public static final int MSG_SET_SURFACE_TEXTURE = 0;

        private WeakReference<CameraActivity> weakActivity;

        public CameraHandler(CameraActivity activity){
            weakActivity = new WeakReference<CameraActivity>(activity);
        }


        public void invalidateHandler(){    weakActivity.clear();   }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;

            CameraActivity activity = weakActivity.get();
            if(activity == null){
                return;
            }

            switch(what){
                case MSG_SET_SURFACE_TEXTURE:
                    activity.handleSetSurfaceTexture((SurfaceTexture) msg.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }

    private void switchEncoding(){
        mRecordingEnabled = !mRecordingEnabled;
        cameraSurface.queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.changeRecordingState(mRecordingEnabled);
            }
        });
    }

    public class CameraSurfaceRenderer implements GLSurfaceView.Renderer{

        private CameraHandler handler;
        private SurfaceTexture surfaceTexture;

        /* for draw */
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
        private int textureId;

        public final static int RATIO_SQUARE = 0;
        public final static int RATIO_CIRCLE = 1;
        private int ratioState = RATIO_SQUARE;

        /* for Matrix */
        private float[] mDisplayProjectionMatrix = new float[16];
        private float posX, posY;
        private float angle;

        /* for Circle stencil */
        private Context context;
        private Model model;
        private Shader shader;

        /* for File IO */
        private String tempPath;

        /* for Record */
        private boolean mRecordingEnabled;

        private static final int RECORDING_OFF = 0;
        private static final int RECORDING_ON = 1;
        private static final int RECORDING_RESUMED = 2;
        private int mRecordingStatus;
        private TextureMovieEncoder mVideoEncoder;


        public CameraSurfaceRenderer(CameraHandler handler, Context context, String tempPath, TextureMovieEncoder encoder){
            this.handler = handler;
            this.context = context;
            this.tempPath = tempPath;
            this.mVideoEncoder = encoder;

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


            GLES20.glViewport(0, 0, cameraSurface.getWidth(), cameraSurface.getHeight());

            shader = new Shader(context);
            model = new Model(context, shader);


            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);

            textureId = mTexProgram.createTextureObject();

            surfaceTexture = new SurfaceTexture(textureId);


            mRect.setTexture(textureId);
            mRect2.setTexture(textureId);
            mRect3.setTexture(textureId);
            mRect4.setTexture(textureId);


            Log.d(TAG, surfaceTexture.toString());
            handler.sendMessage(handler.obtainMessage(CameraHandler.MSG_SET_SURFACE_TEXTURE, surfaceTexture));


            posX = cameraSurface.getWidth()/2;
            posY = cameraSurface.getHeight()/2;

            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, cameraSurface.getWidth(), 0, cameraSurface.getHeight(), -1, 1);

            if(cameraFacingState == BACK_CAMERA){
                mRect.setRotation(-90);
                mRect2.setRotation(-90);
                mRect3.setRotation(-90);
                mRect4.setRotation(-90);
            }else if(cameraFacingState == FRONT_CAMERA){
                mRect.setRotation(90);
                mRect2.setRotation(90);
                mRect3.setRotation(90);
                mRect4.setRotation(90);
            }

            //mRect.setScale(1280*screenWidth/720, screenWidth);    //1:1 ratio
            mRect.setScale(-(1280*screenWidth/720)/2, screenWidth/2);
            mRect2.setScale(-(1280*screenWidth/720)/2, -screenWidth/2);
            mRect3.setScale((1280*screenWidth/720)/2, screenWidth/2);
            mRect4.setScale((1280*screenWidth/720)/2, -screenWidth/2);
            //mRect.setPosition(posX, posY);

            mRect.setPosition(cameraSurface.getWidth()/4, cameraSurface.getHeight()/4-((7*cameraSurface.getHeight())/36));
            mRect2.setPosition(cameraSurface.getWidth()*3/4, cameraSurface.getHeight()/4-((7*cameraSurface.getHeight())/36));
            mRect3.setPosition(cameraSurface.getWidth()/4, cameraSurface.getHeight()*3/4+((7*cameraSurface.getHeight())/36));
            mRect4.setPosition(cameraSurface.getWidth()*3/4, cameraSurface.getHeight()*3/4+((7*cameraSurface.getHeight())/36));

            try{
                camera.setPreviewTexture(surfaceTexture);
            }catch (IOException ioe){
                throw new RuntimeException(ioe);
            }
            camera.startPreview();



        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {


        }

        @Override
        public void onDrawFrame(GL10 gl) {
            surfaceTexture.updateTexImage();

            if (mRecordingEnabled) {
                switch (mRecordingStatus) {
                    case RECORDING_OFF:
                        Log.d(TAG, "START recording");
                        // start recording
                        mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(tempPath, 720, 720, 12000000, EGL14.eglGetCurrentContext()));
                        mVideoEncoder.setScreenSize(cameraSurface.getWidth(), cameraSurface.getHeight());
                        mVideoEncoder.setCameraFacing(cameraFacingState);
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

            mVideoEncoder.setTextureId(textureId);
            mVideoEncoder.frameAvailable(surfaceTexture);

            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.5f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
            mRect2.draw(mTexProgram, mDisplayProjectionMatrix);
            mRect3.draw(mTexProgram, mDisplayProjectionMatrix);
            mRect4.draw(mTexProgram, mDisplayProjectionMatrix);


            if(ratioState == RATIO_SQUARE){
                updateFrontCameraFlip();
            }

            if(ratioState == RATIO_CIRCLE){
                updateFrontCameraFlip();
                shader.setShader();
                shader.setShaderParameters();
                model.draw();
            }

        }

        private void updateFrontCameraFlip(){
            if(cameraFacingState == FRONT_CAMERA){
                //mRect.setScale(1280*screenWidth/720, -screenWidth);
                mRect.setScale(-(1280*screenWidth/720)/2, -screenWidth/2);
                mRect2.setScale(-(1280*screenWidth/720)/2, screenWidth/2);
                mRect3.setScale((1280*screenWidth/720)/2, -screenWidth/2);
                mRect4.setScale((1280*screenWidth/720)/2, screenWidth/2);
            }else{
                //mRect.setScale(1280*screenWidth/720, screenWidth);
                mRect.setScale(-(1280*screenWidth/720)/2, screenWidth/2);
                mRect2.setScale(-(1280*screenWidth/720)/2, -screenWidth/2);
                mRect3.setScale((1280*screenWidth/720)/2, screenWidth/2);
                mRect4.setScale((1280*screenWidth/720)/2, -screenWidth/2);
            }
        }

        private int setRatioState(int state){
            ratioState = state;
            return ratioState;
        }


        public void notifyPausing() {
            if (surfaceTexture != null) {
                Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
                surfaceTexture.release();
                surfaceTexture = null;
            }
        }

        public void cancelRecord(){
            mVideoEncoder.cancelRecording();
        }

        private boolean checkCircleArea(int x,int y){
            final int CIRCLE_MARGIN = 150;
            int r = (screenWidth-(CIRCLE_MARGIN*2))/2;
            int cX = screenWidth/2;
            int cY = screenWidth/2;

            if(Math.pow(cX-x, 2)+Math.pow(cY-y, 2) <= Math.pow(r, 2)){
                return true;
            }else{
                return false;
            }
        }

    }



}