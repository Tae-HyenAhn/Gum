package me.ggum.gum.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by sb on 2017. 1. 20..
 */

public class PreDecoder extends Thread{
    private static final String TAG = "PreDecoder";

    private static final String VIDEO = "video/";
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;

    private boolean eosReceived;


    private long startTime;
    private long endTime;


    private OnPreDecoderListener listener;

    public void setOnPreDecoderListener(OnPreDecoderListener listener){
        this.listener = listener;
    }

    public boolean init(Surface surface, String filePath, long startTime, long endTime) {
        eosReceived = false;
        this.startTime = startTime;
        this.endTime = endTime;



        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(filePath);


            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);

                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    mExtractor.selectTrack(i);
                    mDecoder = MediaCodec.createDecoderByType(mime);
                    try {
                        Log.d(TAG, "format : " + format);
                        mDecoder.configure(format, surface, null, 0 /* Decoder */);

                    } catch (IllegalStateException e) {
                        Log.e(TAG, "codec '" + mime + "' failed configuration. " + e);
                        return false;
                    }
                    if(listener != null){
                        listener.onStartDecode();
                    }

                    mDecoder.start();
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
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
        long startWhen = 0;


        while (!eosReceived) {
            if (isInput) {
                int inputIndex = mDecoder.dequeueInputBuffer(10000);

                if (inputIndex >= 0) {
                    // fill inputBuffers[inputBufferIndex] with valid data
                    ByteBuffer inputBuffer = inputBuffers[inputIndex];

                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                    if (sampleSize > 0) {
                        mExtractor.advance();

                        long presentationTimeUs = mExtractor.getSampleTime();
                        if(listener != null){
                            listener.onPlayTime(presentationTimeUs/1000);
                        }

                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);


                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput = false;
                    }
                }
            }


            if(!isInput){
                if(listener != null){
                    listener.onStopDecode();
                }

                eosReceived = true;
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
                mExtractor.release();
                mExtractor = null;
                break;
            }

            int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mDecoder.getOutputBuffers();
                    break;

                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mDecoder.getOutputFormat());
                    break;

                case MediaCodec.INFO_TRY_AGAIN_LATER:
    				Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                    break;

                default:

                    if (!first) {
                        startWhen = System.currentTimeMillis();
                        first = true;
                    }
                    try {
                        long sleepTime = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - startWhen);
                        Log.d(TAG, "info.presentationTimeUs : " + (info.presentationTimeUs / 1000) + " playTime: " + (System.currentTimeMillis() - startWhen) + " sleepTime : " + sleepTime);

                        if (sleepTime > 0)
                            Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }


                    mDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }


        if(mDecoder != null){
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }

        if(mExtractor != null){
            mExtractor.release();
            mDecoder = null;
        }
    }


    public void close(){
        eosReceived = true;
    }

    public interface OnPreDecoderListener {
        void onStartDecode();
        void onPlayTime(long nowTime);
        void onStopDecode();
    }
}
