package me.ggum.gum.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import me.ggum.gum.activity.EffectActivity;

/**
 * Created by sb on 2017. 1. 18..
 */

public class EffectDecoder extends Thread{
    private static final String TAG = "EffectDecoder";
    private static final String VIDEO = "video/";

    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;

    private boolean eosReceived;
    private boolean firstDecode;
    private boolean endSignal;

    private double frameRate;

    private long touchTime;

    private boolean seekMode;

    private OnEffectDecoderListener listener;

    private int effectState = -1;
    private long leftTime, rightTime, effectDuration;

    public void setOnEffectDecoderListener(OnEffectDecoderListener listener){
        this.listener = listener;
    }

    public boolean init(Surface surface, String filePath, double frameRate){
        eosReceived = false;
        seekMode = false;
        endSignal = false;
        this.frameRate = frameRate;



        firstDecode = true;
        try{
            Log.d(TAG, filePath);
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(filePath);


            for( int i = 0; i < mExtractor.getTrackCount(); i++){
                MediaFormat format = mExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if(mime.startsWith(VIDEO)){
                    mExtractor.selectTrack(i);
                    mDecoder = MediaCodec.createDecoderByType(mime);
                    try{
                        mDecoder.configure(format, surface, null, 0);

                    }catch(IllegalStateException e){
                        e.printStackTrace();
                        return false;
                    }
                    if(listener != null){
                        listener.onDecoderStart();
                    }

                    mDecoder.start();
                    break;
                }
            }
        }catch(IOException ie){
            ie.printStackTrace();
        }
        return true;
    }


    @Override
    public void run() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        mDecoder.getOutputBuffers();

        boolean isInput = true;
        boolean first = false;

        while(!eosReceived){
            int inputIndex = mDecoder.dequeueInputBuffer(1000);
            while(inputIndex < 0){
                Log.d(TAG, "inputIndex < 0 : "+inputIndex);
                inputIndex = mDecoder.dequeueInputBuffer(1000);
            }

            if(isInput){
                ByteBuffer inputBuffer = inputBuffers[inputIndex];

                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                if ( sampleSize > 0) {
                    if(!seekMode){
                        long presentationTimeUs = mExtractor.getSampleTime();

                        mExtractor.advance();
                        if(firstDecode){
                            if(listener != null){
                                listener.decoderRestart();
                            }
                            mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            firstDecode = false;
                        }

                        presentationTimeUs = mExtractor.getSampleTime();

                        if(listener != null){
                            listener.onPlayTime(presentationTimeUs/1000);
                        }
                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);

                    }else if(seekMode){
                        mExtractor.seekTo(touchTime*1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

                        if(listener != null){
                            listener.onPlayTime(touchTime);
                        }
                        long presentationTimeUs = mExtractor.getSampleTime();
                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);

                        firstDecode = true;
                    }


                } else {
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    firstDecode = true;
                    mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    continue;

                    //mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    //isInput = false;
                }
            }

            if(!isInput){
                eosReceived = true;
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
                mExtractor.release();
                mExtractor = null;
                break;
            }

            int outIndex = mDecoder.dequeueOutputBuffer(info, 1000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mDecoder.getOutputBuffers();
                    break;

                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mDecoder.getOutputFormat());
                    break;

                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;

                default:
                    Log.d("DECODERFRAMERATE", "D: "+frameRate);
                    if (!first) {

                        first = true;
                    }
                    if(!seekMode){
                        long sleepTime =(long) ((double)1000000/frameRate);

                        try {
                            Log.d(TAG, "SLEEP: "+sleepTime+", "+sleepTime/1000+", "+(int)sleepTime%1000);
                            Thread.sleep(sleepTime/1000, (int)(sleepTime%1000));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        Log.d(TAG, "sleepTime: "+sleepTime+", "+frameRate);
                    }


                    mDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                eosReceived = true;
                break;
            }
        }
        if(listener != null){
            listener.onDecoderStop();
        }

        if(mDecoder != null){
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
        if(mExtractor != null){
            mExtractor.release();
            mExtractor = null;
        }
    }

    public void requestEndSignal() {    endSignal = true;   }

    public void close(){
        eosReceived = true;
    }

    public void setSeekMode(boolean seekMode){
        this.seekMode = seekMode;
    }

    public boolean getSeekMode(){
        return seekMode;
    }

    public void setTouchTime(long time){
        this.touchTime = time;
    }

    public void setLeftTime(long time){
        this.leftTime = time;
    }

    public void setRightTime(long time){
        this.rightTime = time;
    }

    public void setEffectDuration(long time){
        this.effectDuration = time;
    }

    public void setEffectState(int state){
        this.effectState = state;
    }

    public void setPlayFirst(){
        this.firstDecode = true;
    }

    public interface OnEffectDecoderListener{
        void onDecoderStart();
        void onPlayTime(long time);
        void onDecoderStop();
        void decoderRestart();
    }

}
