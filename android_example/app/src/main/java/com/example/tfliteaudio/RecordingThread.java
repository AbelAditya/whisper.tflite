package com.example.tfliteaudio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingThread extends Thread {
    private final String TAG = "RecordingThread";
    private final Context mContext;
    private final UpdateListener mUpdateListener;
    private static final AtomicBoolean mRecordingInProgress = new AtomicBoolean(false);

    public RecordingThread(MainActivity mainActivity) {
        mContext = mainActivity;
        mUpdateListener = mainActivity;
    }

    public void setRecordingInProgress(boolean value) {
        mRecordingInProgress.set(value);
    }

    public static boolean isRecordingInProgress() {
        return mRecordingInProgress.get();
    }

    @Override
    public void run() {
        try {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            mUpdateListener.updateStatus(mContext.getString(R.string.recording));

            int channels = 1;
            int bytesPerSample = 2;
            int sampleRateInHz = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO; // as per channels
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // as per bytesPerSample
            int audioSource = MediaRecorder.AudioSource.MIC;

            int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
            audioRecord.startRecording();

            int durationInSeconds = 30;
            int bufferSize30Sec = durationInSeconds * sampleRateInHz * bytesPerSample * channels;
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize30Sec);

            int totalBytesRead = 0;
            byte[] buffer = new byte[bufferSize];
            while (mRecordingInProgress.get() && (totalBytesRead < bufferSize30Sec)) {
                int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                if (bytesRead > 0) {
                    byteBuffer.put(buffer, 0, bytesRead);
                } else {
                    Log.d(TAG, "AudioRecord error, bytes read: " + bytesRead);
                }

                totalBytesRead = totalBytesRead + bytesRead;
            }

            audioRecord.stop();
            audioRecord.release();

            mRecordingInProgress.set(false);

            String wavePath = mContext.getFilesDir() + File.separator + WaveUtil.RECORDING_FILE;
            WaveUtil.createWaveFile(wavePath, byteBuffer.array(), sampleRateInHz, channels, bytesPerSample);
            Log.d(TAG, "Recorded file: " + wavePath);
            mUpdateListener.updateStatus(mContext.getString(R.string.recording_is_completed));
        } catch (Exception e) {
            throw new RuntimeException("Writing of recorded audio failed", e);
        }
    }
}
