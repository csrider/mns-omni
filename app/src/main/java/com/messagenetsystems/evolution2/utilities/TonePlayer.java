package com.messagenetsystems.evolution2.utilities;

/* TonePlayer
 * Inspired by m-abboud on GitHub.
 * It would be easiest to extend custom tone classes with this one (see threads.TonePlayerBeep class for example).
 *
 * Revisions:
 *  2018.10.03      Chris Rider     Creation.
 *  2020.04.22      Chris Rider     Migrated from v1; untested, but builds.
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public abstract class TonePlayer {

    private double toneFreqInHz = 440;
    private final Object toneFreqInHzSyncObj = new Object();
    private int volume = 100;

    private AudioTrack audioTrack = null;
    private int audTrackBufferSize = 0;
    private boolean isPlaying = false;
    protected Thread playerWorker;

    private double lastToneFreqInHz = 0.0;
    private int lastNumSamplesCount = 0;
    private double lastDoubleSamples[] = null;
    private byte lastSoundBytes[] = null;


    protected TonePlayer() {
    }

    public TonePlayer(double toneFreqInHz) {
        this.toneFreqInHz = toneFreqInHz;
    }

    public void play() {
        if (isPlaying)
            return;

        stop();

        isPlaying = true;
        asyncPlayTrack();
    }

    public void stop() {
        isPlaying = false;
        if (audioTrack == null)
            return;

        tryStopPlayer();
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public int getVolume() {
        return volume;
    }

    private double getToneFreqInHz() {
        synchronized (toneFreqInHzSyncObj) {
            return toneFreqInHz;
        }
    }

    public void setToneFreqInHz(double toneFreqInHz) {
        synchronized (toneFreqInHzSyncObj) {
            this.toneFreqInHz = toneFreqInHz;
        }
    }

    public abstract void asyncPlayTrack();

    private void tryStopPlayer() {
        isPlaying = false;
        try {
            if (playerWorker != null)
                playerWorker.interrupt();

            // pause() appears to be more snappy in audio cutoff than stop()
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        } catch (IllegalStateException e) {
            // Likely issue with concurrency, doesn't matter, since means it's stopped anyway
            // so we just eat the exception
        }
    }

    /**
     * below from http://stackoverflow.com/questions/2413426/playing-an-arbitrary-tone-with-android
     */
    private void playTone(double seconds, boolean continuousFlag) {
        int sampleRate = 8000;

        double dnumSamples = seconds * sampleRate;
        dnumSamples = Math.ceil(dnumSamples);
        int numSamples = (int) dnumSamples;
        final double freqInHz = getToneFreqInHz();

        double sample[];
        byte soundData[];
        if (numSamples == lastNumSamplesCount) {
            // if same tone then
            if (freqInHz == lastToneFreqInHz) {
                // repeat with same data
                playSound(sampleRate, lastSoundBytes);
                return;
            }

            // different tone freq but same duration
            // reuse arrays
            sample = lastDoubleSamples;
            soundData = lastSoundBytes;
        } else {  //different duration; create (and save) new arrays
            sample = lastDoubleSamples = new double[numSamples];
            soundData = lastSoundBytes = new byte[2 * numSamples];
            lastNumSamplesCount = numSamples;
        }
        lastToneFreqInHz = freqInHz;

        // Fill the sample array
        for (int i = 0; i < numSamples; ++i)
            sample[i] = Math.sin(freqInHz * 2 * Math.PI * i / (sampleRate));

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalized.
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        int i;

        // Amplitude ramp as a percent of sample count
        //  (smaller ramp for continuous; trying for cleaner sound)
        int ramp = numSamples / (continuousFlag ? 200 : 20);

        // Ramp amplitude up (to avoid clicks)
        for (i = 0; i < ramp; ++i) {
            double dVal = sample[i];
            // Ramp up to maximum
            final short val = (short) ((dVal * 32767 * i / ramp));
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        // Max amplitude for most of the samples
        for (i = i; i < numSamples - ramp; ++i) {
            double dVal = sample[i];
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        // Ramp amplitude down
        for (i = i; i < numSamples; ++i) {
            double dVal = sample[i];
            // Ramp down to zero
            final short val = (short) ((dVal * 32767 * (numSamples - i) / ramp));
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        playSound(sampleRate, soundData);
    }

    protected void playTone(double seconds) {
        playTone(seconds, false);
    }

    private void playSound(int sampleRate, byte[] soundData) {
        try {
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

            //if buffer-size changing or no previous obj then allocate:
            if (bufferSize != audTrackBufferSize || audioTrack == null) {
                if (audioTrack != null) {
                    //release previous object
                    audioTrack.pause();
                    audioTrack.flush();
                    audioTrack.release();
                }

                //allocate new object:
                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                        AudioTrack.MODE_STREAM);
                audTrackBufferSize = bufferSize;
            }

            float gain = (float) (volume / 100.0);
            //noinspection deprecation
            audioTrack.setStereoVolume(gain, gain);

            audioTrack.play();
            audioTrack.write(soundData, 0, soundData.length);
        } catch (Exception e) {
            Log.e("tone player", e.toString(), e);
        }
    }

}
