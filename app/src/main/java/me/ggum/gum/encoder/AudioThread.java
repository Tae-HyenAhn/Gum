package me.ggum.gum.encoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by sb on 2017. 1. 9..
 */

public class AudioThread extends Thread {

    private static final String TAG = "AudioThread";

    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;	// 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;
    public static final int SAMPLES_PER_FRAME = 1470;	// AAC, bytes/frame/channel
    public static final int FRAMES_PER_BUFFER = 30; 	// AAC, frame/buffer/sec
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackId;

    public boolean endVideoEncode;



    private static final int[] AUDIO_SOURCES = new int[] {
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
    };

    private boolean endOfStream;

    public AudioThread(){

        endVideoEncode = false;
        endOfStream = false;
        mTrackId = -1;

        MediaCodecInfo mediaCodecInfo = selectAudioCodec(MIME_TYPE);
        if(mediaCodecInfo == null){
            return;
        }

        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);

        try{
            mBufferInfo = new MediaCodec.BufferInfo();
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void stopRecording(){
        endOfStream = true;
        endVideoEncode = true;
        if(mEncoder != null){
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;

        }
    }

    @Override
    public void run() {
        audioRecord();
    }

    private void audioRecord(){
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        final int min_buffer_size = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
        if (buffer_size < min_buffer_size)
            buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

        AudioRecord audioRecord = null;

        for (final int source : AUDIO_SOURCES) {
            try {
                audioRecord = new AudioRecord(
                        source, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
                    audioRecord = null;
            } catch (final Exception e) {
                audioRecord = null;
            }
            if (audioRecord != null) break;
        }

        audioRecord.startRecording();

        final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
        int readBytes;

        while(!endOfStream){
            buf.clear();
            readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);

            long time = getPTSUs();
            if (readBytes > 0) {
                // set audio data to encoder
                buf.position(readBytes);
                buf.flip();
            }

            drainAudio(buf, readBytes, time);

            Log.d(TAG, "AUDIO_TIME: "+time);


        }


        Log.d(TAG, "END!!");
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
    }

    private void drainAudio(ByteBuffer buf, int readBytes, long time) {

        if(endVideoEncode){
            return;
        }
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        int inputBufferIndex = -1;
        try{
            inputBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        }catch(IllegalStateException e){
            return;
        }
        if(inputBufferIndex >= 0){
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            if(buf != null){
                inputBuffer.put(buf);
            }

            if(readBytes <= 0){
                mEncoder.queueInputBuffer(inputBufferIndex, 0, 0, time, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }else{
                mEncoder.queueInputBuffer(inputBufferIndex, 0, readBytes, time, 0);
            }
        }else if(inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){

        }
        if(endVideoEncode){
            return;
        }
        ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
        int outputBufferIndex = -1;
        try{
            outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        }catch(IllegalStateException e){
            return;
        }
        if(outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER){

        } else if(outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
            outputBuffers = mEncoder.getOutputBuffers();
        } else if(outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            MediaFormat newFormat = mEncoder.getOutputFormat();
            mTrackId = VideoEncoderCore.mMuxer.addTrack(newFormat);
            if(mTrackId >= 0){
                VideoEncoderCore.mMuxerStarted = true;
            }

        } else if(outputBufferIndex < 0){

        } else{
            ByteBuffer encodedData = outputBuffers[outputBufferIndex];
            if(encodedData == null){
                throw new RuntimeException("out");
            }
            if((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){
                mBufferInfo.size = 0;
            }

            if(mBufferInfo.size != 0){
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                VideoEncoderCore.mMuxer.writeSampleData(mTrackId, encodedData, mBufferInfo);
            }


            mEncoder.releaseOutputBuffer(outputBufferIndex, false);
        }


    }

    private long prevOutputPTSUs = 0;
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    /**
     * select the first codec that match a specific MIME type
     * @param mimeType
     * @return
     */
    private static final MediaCodecInfo selectAudioCodec(final String mimeType) {
        Log.v(TAG, "selectAudioCodec:");

        MediaCodecInfo result = null;
        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        LOOP:	for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {	// skipp decoder
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (result == null) {
                        result = codecInfo;
                        break LOOP;
                    }
                }
            }
        }
        return result;
    }

}
