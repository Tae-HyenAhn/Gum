package me.ggum.gum.encoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by sb on 2017. 1. 4..
 */

public class VideoEncoderCore {
    private static final String TAG = "VideoEncoderCore";

    /* video */
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 0;

    private Surface mInputSurface;
    public static MediaMuxer mMuxer;

    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;

    private int mTrackIndex;

    public static boolean mMuxerStarted;


    public VideoEncoderCore(int width, int height, int bitrate, String outputPath)
        throws IOException{
        /* video */
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();


        mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        //mTrackIndex = mMuxer.addTrack(format);
        mTrackIndex = -1;

        mMuxerStarted = false;

    }



    public Surface getInputSurface(){
        return mInputSurface;
    }

    public void cancel(){
        if(mEncoder != null){
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if(mMuxer != null){
            mMuxer.release();
            mMuxer = null;
        }
    }

    public void release(){
        if(mEncoder != null){
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if(mMuxer != null){
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }


    public void drainEncoder(boolean endOfStream){

        final int TIMEOUT_USEC = 10000;

        if(endOfStream){
            mEncoder.signalEndOfInputStream();

        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        int count = 0;
        while(true){

            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if(encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER){

                if(!endOfStream){
                    break;
                }else{
                    Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if(encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if(encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){

                if(mMuxerStarted){
                    throw new RuntimeException("format changed twice");
                }


                MediaFormat newForamt = mEncoder.getOutputFormat();


                Log.d(TAG, "format change!!");
                mTrackIndex = mMuxer.addTrack(newForamt);
                while(true){
                    if(mMuxerStarted){
                        break;
                    }
                }
                mMuxer.start();
                mMuxerStarted = true;

            } else if(encoderStatus < 0){
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
            } else{
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if(encodedData == null){
                    throw new RuntimeException("encoderOutputBuffer "+ encoderStatus + " was null!");
                }

                if((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){
                    mBufferInfo.size = 0;
                }

                if(mBufferInfo.size != 0){
                    if(!mMuxerStarted){
                        throw new RuntimeException("muxer hasn't started");
                    }

                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    count++;
                    Log.d(TAG, "COUNT: "+count);
                    //Log.d("Time", "TIME_A :"+mBufferInfo.presentationTimeUs);
                    Log.d(TAG, "VIDEO_TIME = "+mBufferInfo.presentationTimeUs);
                    //mBufferInfo.presentationTimeUs = getPTSUs();
                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);

                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0){
                    if(!endOfStream){
                        Log.w(TAG, "reached end of stream unexpectedly");
                    }else{

                        Log.d(TAG, "end of stream reached");
                    }
                    break;
                }
            }
        }
    }



    private long prevOutputPTSUs = 0;
    public long getPTSUs(){
        long result = System.nanoTime()/1000L;

        if(result < prevOutputPTSUs){
            result = (prevOutputPTSUs - result) + result;
        }
        return result;
    }






}
