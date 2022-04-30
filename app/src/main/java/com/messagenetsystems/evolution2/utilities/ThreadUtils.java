package com.messagenetsystems.evolution2.utilities;

/* ThreadUtils
 * Threading related utilities.
 *
 * Notes:
 *  Android Threading Constructs:   http://techtej.blogspot.com/2011/03/android-thread-constructspart-4.html
 *
 * EX. When you want to start a service:
 *  private MyService myService = new MyService();  //optional depending on how you code your service
 *  private Intent myServiceIntent = new Intent(this, myService.getClass());
 *  ThreadUtils.doStartService(this, myServiceIntent)
 *
 * Ex. When you want to start a thread in the same thread as the calling thread:
 *  private MyThread myThread = new MyThread();
 *  ThreadUtils.doStartThread(this, myThread, ThreadUtils.SPAWN_NEW_THREAD_FALSE);
 *
 * Ex. When you want to start a thread in a new thread spawned just for it:
 *  private MyThread myThread = new MyThread();
 *  ThreadUtils.doStartThread(this, myThread, ThreadUtils.SPAWN_NEW_THREAD_TRUE);
 *
 * Revisions:
 *  2020.07.28      Chris Rider     Created.
 *  2020.08.04      Chris Rider     Added analyzeProcessingTime method to assist in code optimization efforts.
 *                                  Added priority setting capabilities for threads and processes.
 *  2020.08.11      Chris Rider     Added priorities LOW and HIGH and made them about half way between NORM and MIN/MAX.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bosphere.filelogger.FL;

import java.util.Date;


public class ThreadUtils {
    private static final String TAG = ThreadUtils.class.getSimpleName();

    public static final boolean SPAWN_NEW_THREAD_FALSE = false;
    public static final boolean SPAWN_NEW_THREAD_TRUE = true;

    public static final int PRIORITY_MINIMUM = Thread.MIN_PRIORITY;  //1
    public static final int PRIORITY_LOW = Thread.NORM_PRIORITY - Math.round((Thread.NORM_PRIORITY - Thread.MIN_PRIORITY) / 2); //about halfway between NORM and MIN
    public static final int PRIORITY_NORMAL = Thread.NORM_PRIORITY;  //5
    public static final int PRIORITY_HIGH = Thread.MAX_PRIORITY - Math.round((Thread.MAX_PRIORITY - Thread.NORM_PRIORITY) / 2); //about halfway between MAX and NORM
    public static final int PRIORITY_MAXIMUM = Thread.MAX_PRIORITY;  //10

    /** Analyze the processing/completion time for the calling class and method, and log appropriately.
     * Note: This is merely helpful for designing optimized code, and just logs for development and debugging purposes.
     * @param callingClassName Name of the calling class (for logging purposes)
     * @param methodName Name of the calling method or routine (for logging purposes)
     * @param startedTime Date.getTime value for when the method or routine began.
     */
    public static void analyzeProcessingTime(final String callingClassName, String methodName, final long startedTime) {
        final String TAGG = "analyzeProcessingTime: ";

        // Configuration
        final byte anrSecsMaximum = 5;      //number of seconds at which an ANR (application not responding) dialog is likely to appear
        final byte anrSecsWarning = 4;      //number of seconds at which an ANR (application not responding) dialog could apear if it takes much longer
        final byte blockFrameTimeMS = 16;   //about how long Android takes to process each block to ensure smooth appearance at 60fps

        try {
            long processingTime = new Date().getTime() - startedTime;

            // Try to figure out ideal/max desired processing time to ensure smooth appearance
            // Anything longer than 16ms *may* start to degrade 60fps performance... assuming 32ms for 30fps?
            final byte blockFrameTimeMS_60fps = blockFrameTimeMS;
            final byte blockFrameTimeMS_30fps = blockFrameTimeMS_60fps * 2;
            final byte blockFrameTimeMS_15fps = blockFrameTimeMS_30fps * 2;

            if (processingTime > (anrSecsMaximum*1000)) {
                FL.w(TAG, TAGG + callingClassName + "."+methodName+" took about " + processingTime + "ms to process. This would almost certainly cause an ANR (application not responding) to happen if it runs under the main/UI thread!");
            } else if (processingTime > (anrSecsWarning*1000)) {
                FL.w(TAG, TAGG + callingClassName + "."+methodName+" took about " + processingTime + "ms to process. This is close to causing an ANR (application not responding) to happen if it runs under the main/UI thread!");
            } else if (processingTime > blockFrameTimeMS_15fps) {
                FL.i(TAG, TAGG + callingClassName + "." + methodName + " took about " + processingTime + "ms to process. This could cause non-smooth scrolling or UI performance if it runs under the main/UI thread!");
            } else if (processingTime > blockFrameTimeMS_30fps) {
                FL.d(TAG, TAGG + callingClassName + "."+methodName+" took about " + processingTime + "ms to process. This could cause non-smooth scrolling or UI performance if it runs under the main/UI thread!");
            } else {
                FL.v(TAG, TAGG + callingClassName + "."+methodName+" took about " + processingTime + "ms to process.");
            }
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /* Notes on priorities:
     *  There are two spaces for this: Linux/OS and Java-VM.
     *      Linux/OS processes (or, the main thread) can be affected with android.os.Process methods.
     *      Java-VM (logical threads that run under the OS process) can be affected with Thread.currentThread() methods.
     *
     *  There are also two Android terms to know difference between: Process and Thread.
     *      "Process" is the main thread in Android, and it is synonymous with the Linux/OS process ID (PID).
     *      "Thread" (except for main thread) is used most often to refer to worker threads.
     */

    /** Set Process (main thread) priority (in Linux/OS space).
     * NOTE: This is not needed, if you set Thread (app-space method below) priority using main thread's TID as that automatically sets this as well if you do.
     * @param threadPriority The Process class' constant-defined priority value or equivalent integer
     * @return Process priority that was set (or thread's original priority if setting failed)
     */
    public static int setProcessPriority(int threadPriority) {
        final String TAGG = "setProcessPriority("+threadPriority+"): ";

        int ret = android.os.Process.getThreadPriority(android.os.Process.myTid());

        try {
            if (android.os.Process.myTid() == android.os.Process.myPid()) {
                FL.i(TAG, TAGG+"NOTE: Thread ID is same as Process ID, so we will effectively be setting priority for main thread! Be sure you want to do this.");
            }

            android.os.Process.setThreadPriority(threadPriority);
            ret = android.os.Process.getThreadPriority(android.os.Process.myTid());
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        return ret;
    }

    /** Set thread priority (in Java VM space) for whatever thread is calling this method. If possible, you should probably set it directly during startup, though.
     * NOTE: This will also set the OS priority to its equivalent, if you set this from the main thread!
     * @param threadPriority The Thread class' constant-defined priority value or equivalent integer
     * @return Thread priority that was set (or thread's original priority if setting failed)
     */
    public static int setThreadPriority(int threadPriority) {
        final String TAGG = "setThreadPriority("+threadPriority+"): ";

        int ret = Thread.currentThread().getPriority();

        try {
            Thread.currentThread().setPriority(threadPriority);
            ret = Thread.currentThread().getPriority();
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        return ret;
    }

    /** Thread sleep...
     * NOTE: Consider delayed handler or similar, if possible; unless you know what you're doing!
     * Definitely avoid running this in any process that runs on the main/UI thread!
     * @param ms Milliseconds to try to sleep for
     */
    public static void doSleep(long ms) {
        final String TAGG = "doSleep("+Long.toString(ms)+"): ";

        try {
            FL.v(TAG, TAGG+"Thread sleeping for "+ms+"ms...");
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            FL.w(TAG, TAGG+"Exception caught trying to sleep: "+ie.getMessage());
        }
    }
    public static void doSleep(int ms) {
        doSleep(ms);
    }


    /* Notes on services:
     *  Service - Runs on main thread, so if you're not careful, it may block it or trigger an ANR if it does work without background threads!
     *  IntentService - Runs on separate worker thread, but cannot run parallel tasks (intents are queued and sequentially processed). More difficult to maintain code, as well?
     *
     *  Your best bet is to manually just spawn whatever services you need, and be sure to do any work in those services in background threads (doStarThread with SPAWN_NEW_THREAD_TRUE)
     */

    /** Start specified service (via the intent for it you should have already instantiated).
     * @param context Context from which to start the service
     * @param serviceIntent Intent you created for the service
     * @param spawnNewThread Whether to try to spawn in a new thread or not
     */
    private static void doStartService(final Context context, final Intent serviceIntent, boolean spawnNewThread) {
        String TAGG;
        Class processClass = null;
        String processClassShortName;
        try {
            processClass = Class.forName(serviceIntent.getComponent().getClassName());
            processClassShortName = processClass.getSimpleName();
            TAGG = "doStartService(" + processClassShortName + "): ";
        } catch (ClassNotFoundException e) {
            processClassShortName = "(process class not found)";
            TAGG = "doStartService: ";
        } catch (NullPointerException e) {
            processClassShortName = "(unknown process class name, intent's getComponent returns null? "+serviceIntent.toString()+"): "+e.getMessage();
            TAGG = "doStartService: ";
        } catch (Exception e) {
            processClassShortName = "(process class unable to be found)";
            TAGG = "doStartService: ";
        }

        long mainThreadID = android.os.Process.myPid();

        if (spawnNewThread) {
            //TODO: investigate if we can do this later maybe.. as long as we know not to do work in each service (use threads in them instead), we should be alright?
            FL.w(TAG, TAGG+"NOTE: Specified new thread spawn, but that's not possible with services! \""+processClassShortName+"\" will start on main thread ("+mainThreadID+").");
            spawnNewThread = false;
        }

        if (spawnNewThread) {
            // DEV-NOTE:
            // This apparently is not possible! So make sure all work under services is done in dedicated worker threads.

            FL.w(TAG, TAGG+"Starting \""+processClassShortName+"\" spawned in its own thread (WARNING: Actually not likely to work that way!)");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    context.startService(serviceIntent);
                }
            }).start();
        } else {
            FL.d(TAG, TAGG+"Starting \""+processClassShortName+"\" within the "+context.getClass().getSimpleName()+" thread.");
            context.startService(serviceIntent);
        }

        // Add & register this service in our process status list so we can track its health
        // (note: the service itself should be responsible for recording its own startup, heartbeat interval, heartbeats, etc.)
        //omniApplication.processStatusList.addAndRegisterProcess(ProcessStatus.PROCESS_TYPE_SERVICE, processClass, this.getClass());
    }

    /** Start specified service (via the intent for it you should have already instantiated).
     * @param context Context from which to start the service
     * @param serviceIntent Intent you created for the service
     */
    public static void doStartService(final Context context, final Intent serviceIntent) {
        doStartService(context, serviceIntent, SPAWN_NEW_THREAD_FALSE);
    }

    /** Start specified thread instance (you should have already instantiated it).
     * NOTE: For Thread classes, you should use .start() to ensure each thread executes on its own spawned thread.
     * If you use .run(), the thread will run on this MainService thread, rather than on its own new thread.
     * @param context Context from which to start the service
     * @param threadInstance Thread object you created that will be started
     * @param spawnNewThread Whether to try to spawn in a new thread or not
     * @param threadPriority Thread-class priority to start the thread with (if omitted, defaults to normal)
     */
    public static void doStartThread(Context context, final Thread threadInstance, boolean spawnNewThread, int threadPriority) {
        String TAGG;
        Class processClass = null;
        String processClassShortName;
        try {
            TAGG = "doStartThread(" + threadInstance.getClass().getSimpleName() + "): ";
        } catch (Exception e) {
            TAGG = "doStartThread: ";
        }

        if (threadInstance.isAlive()) {
            FL.e(TAG, TAGG+"Specified thread is already alive! Aborting.");
            return;
        }

        try {
            processClass = threadInstance.getClass();
            processClassShortName = processClass.getSimpleName();
        } catch (Exception e) {
            processClassShortName = "(unknown class name, exception occurred): "+e.getMessage();
        }

        // Validate specified threadPriority value
        try {
            if (threadPriority < PRIORITY_MINIMUM) {
                FL.w(TAG, TAGG+"Specified thread priority ("+threadPriority+") is too low, using minimum ("+PRIORITY_MINIMUM+").");
                threadPriority = PRIORITY_MINIMUM;
            } else if (threadPriority > PRIORITY_MAXIMUM) {
                FL.w(TAG, TAGG+"Specified thread priority ("+threadPriority+") is too high, using maximum ("+PRIORITY_MAXIMUM+").");
                threadPriority = PRIORITY_MAXIMUM;
            }
        } catch (Exception e) {
            FL.w(TAG, TAGG+"Exception caught validating threadPriority ("+String.valueOf(threadPriority)+"), using normal ("+PRIORITY_NORMAL+"): "+e.getMessage());
            threadPriority = PRIORITY_NORMAL;
        }

        if (spawnNewThread) {
            threadInstance.setPriority(threadPriority);
            FL.d(TAG, TAGG+"Starting \""+processClassShortName+"\" spawned in its own thread with priority "+threadInstance.getPriority()+".");
            threadInstance.start();
        } else {
            FL.d(TAG, TAGG+"Starting \""+processClassShortName+"\" within the "+context.getClass().getSimpleName()+" thread but ignoring specified threadPriority ("+threadPriority+") and preserving priority of host thread.");
            threadInstance.run();
        }

        // Add & register this thread in our process status list so we can track its health
        // (note: the thread itself should be responsible for recording its own startup, heartbeat interval, heartbeats, etc.)
        //omniApplication.processStatusList.addAndRegisterProcess(ProcessStatus.PROCESS_TYPE_THREAD, processClass, this.getClass());
    }
    /** Start specified thread instance (you should have already instantiated it), with normal default priority.
     * NOTE: For Thread classes, you should use .start() to ensure each thread executes on its own spawned thread.
     * If you use .run(), the thread will run on this MainService thread, rather than on its own new thread.
     * @param context Context from which to start the service
     * @param threadInstance Thread object you created that will be started
     * @param spawnNewThread Whether to try to spawn in a new thread or not
     */
    public static void doStartThread(Context context, final Thread threadInstance, boolean spawnNewThread) {
        doStartThread(context, threadInstance, spawnNewThread, PRIORITY_NORMAL);
    }


    public static void doStartReceiver(Context context, BroadcastReceiver receiver, boolean spawnNewThread) {
        //TODO

        /*
        Android Broadcast receivers are by default start in GUI thread (main thread) if you use RegisterReceiver(broadcastReceiver, intentFilter).

        But it can be run in a worker thread as follows:

        When using a HandlerThread, be sure to exit the thread after unregistering the BroadcastReceiver.
        If not, File Descriptor (FD) leaks occur in Linux level and finally the application gets crashed if continue to Register / Unregister.

        unregisterReceiver(...);

        Then looper.quit(); Or looper.quitSafely();
         */

        /*
        private Handler broadcastReceiverHandler = null;
        private HandlerThread broadcastReceiverThread = null;
        private Looper broadcastReceiverThreadLooper = null;

        private BroadcastReceiver broadcastReceiverReadScans = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        };

        private void registerForIntents() {
            broadcastReceiverThread = new HandlerThread("THREAD_NAME");//Create a thread for BroadcastReceiver
            broadcastReceiverThread.start();

            broadcastReceiverThreadLooper = broadcastReceiverThread.getLooper();
            broadcastReceiverHandler = new Handler(broadcastReceiverThreadLooper);

            IntentFilter filterScanReads = new IntentFilter();
            filterScanReads.addAction("ACTION_SCAN_READ");
            filterScanReads.addCategory("CATEGORY_SCAN");

            context.registerReceiver(broadcastReceiverReadScans, filterScanReads, null, broadcastReceiverHandler);
        }

        private void unregisterIntents() {
            context.unregisterReceiver(broadcastReceiverReadScans);
            broadcastReceiverThreadLooper.quit();//Don't forget
        }
        */
    }
}
