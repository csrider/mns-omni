package com.messagenetsystems.evolution2.threads;

/* TonePlayerBeep
 * Inspired by m-abboud's OneTimeBuzzer class on GitHub.
 *
 * How to use:
 *  Call asyncPlayTrack_waitOnOthers() to guarantee playback of your tone (in case others are playing when you call).
 *  Call asyncPlayTrack_waitOnOthers(#) --with int-- to play specified number of tones.
 *
 * Revisions:
 *  2018.10.03      Chris Rider     Creation.
 *  2020.04.22      Chris Rider     Migrated from v1; untested, but builds.
 *  2020.07.09      Chris Rider     Added method for returning duration in (long) milliseconds.
 *  2020.07.10      Chris Rider     Fixed asynchronous playback and ability to wait on other tones to finish before playing a new one.
 *  2020.08.11      Chris Rider     Implemented (lower) async thread priorities, tweaked wait lool, and optimized to init threads at instantiation to hopefully make it run more lean when called upon to actually beep.
 */

import android.util.Log;

import com.messagenetsystems.evolution2.utilities.TonePlayer;

public class TonePlayerBeep extends TonePlayer {
    protected double duration = 0.2;

    private Thread tonePlayerThread_async;
    private Thread tonePlayerThread_asyncWaitOnOthers;

    private volatile boolean toneIsInProgress = false;

    /** Constructors */
    public TonePlayerBeep(double duration) {
        this.duration = duration;
        initThreads();
    }
    public TonePlayerBeep() {
        initThreads();
    }

    private void initThreads() {
        tonePlayerThread_async = new Thread(new Runnable() {
            @Override
            public void run() {
                toneIsInProgress = true;
                playTone(duration);
                stop();
                toneIsInProgress = false;
            }
        });
        tonePlayerThread_async.setPriority(Thread.MIN_PRIORITY);

        tonePlayerThread_asyncWaitOnOthers = new Thread(new Runnable() {
            @Override
            public void run() {
                while (toneIsInProgress) {
                    //wait here for other tone to finish
                    //doSleep((duration*1000)+15);    //makes sense to wait at least as long as the default duration    --NOTE: often breaks secondary beep
                    doSleep(150);
                }
                toneIsInProgress = true;
                playTone(duration);
                stop();
                toneIsInProgress = false;
            }
        });
        tonePlayerThread_asyncWaitOnOthers.setPriority(Thread.MIN_PRIORITY);
    }

    public double getDuration() {
        return duration;
    }

    public long getDurationMS() {
        if (duration == 1) {
            return 1000;
        } else {
            return Math.round(duration * 1000);
        }
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public void asyncPlayTrack() {
        tonePlayerThread_async.start();
    }

    /** Asynchronously play tone (after others are done).
     */
    public void asyncPlayTrack_waitOnOthers() {
        tonePlayerThread_asyncWaitOnOthers.start();
    }

    /** Asynchronously play tone (after others are done), with number of times to play.
     * @param numberOfTimesToPlay Number of times to play tone.
     */
    public void asyncPlayTrack_waitOnOthers(int numberOfTimesToPlay) {
        while (numberOfTimesToPlay > 0) {
            asyncPlayTrack_waitOnOthers();
            numberOfTimesToPlay--;
        }
    }

    private void doSleep(int ms) {
        try {
            Log.v("TonePlayerBeep", "Sleeping "+ms+"ms");
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.e("TonePlayerBeep", "Exception caught sleeping: "+e.getMessage());
        }
    }
    private void doSleep(double ms) {
        if (ms > Integer.MAX_VALUE) {
            doSleep(Integer.MAX_VALUE);
        } else {
            doSleep(Math.round(ms));
        }
    }
}
