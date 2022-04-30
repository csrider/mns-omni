package com.messagenetsystems.evolution2.threads;

/* MessageDeliverableProcessor
 * Controls the issuance-to-delivery, expiration/take-down-from-delivery, etc. of standardized/modeled message data.
 * In other words, it examines OmniRawMessage RAM data and informs MainService as needed.
 * Remember, DeliveryService has its own thread to execute/rotate actual deliveries.
 * The "informs MainService" is done simply by affecting MainService's static RAM object (omniMessages_deliverable) directly.
 *
 * This thread lives under MessageService.
 * It takes OmniRawMessage data (whether it originated from Banner, API, etc.) and standardizes its JSON to an OmniMessage object that gets passed to MainService RAM as needed.
 * All modeled data is vectored via the "MainService.omniMessages_deliverable" object.
 *
 * SO... In summary, all this really does is copy raw RAM data to deliverable RAM. It also removes anything that doesn't exist in raw RAM.
 *
 * Summary of tasks:
 *  1) Syncs [MainService.omniRawMessages] to [MainService.omniMessages_deliverable]...
 *      [MainService.omniRawMessages]  --(convert and add/remove/clear)-->  [MainService.omniMessages_deliverable]
 *
 * DEV-NOTES...
 *  It's a thread, because it requires no UI thread access.
 *  This also converts OmniRawMessage objects to OmniMessage objects for direct saving to deliverable OmniMessages list.
 *  The deliverables list does not care about any particular order for delivery-rotation, it's just purely for message data.
 *
 * Usage Example (declare, create, configure, and run):
 *  MessageDeliverableProcessor messageDeliverableProcessor;
 *  messageDeliverableProcessor = new MessageDeliverableProcessor(getApplicationContext(), MessageDeliverableProcessor.LOG_METHOD_FILELOGGER);
 *  messageDeliverableProcessor.start();
 *
 * Usage Example (stop the thread-loop and free up resources):
 *  messageDeliverableProcessor.cleanup();
 *
 * Usage Example (pause processing - may be easily resumed later)
 *  messageDeliverableProcessor.pauseProcessing();
 *
 * Usage Example (resume processing)
 *  messageDeliverableProcessor.resumeProcessing();
 *
 * Revisions:
 *  2019.12.09      Chris Rider     Created.
 *  2019.12.20      Chris Rider     Bringing in send-Android-message to MainService Android-message handler capability.
 *  2020.01.02      Chris Rider     Refactored class name to MessageDeliverableProcessor to more clearly reflect what it does (sync messages up for and vector toward actual delivery).
 *  2020.01.03-06   Chris Rider     Migrated from MainService Handler/Message stuff to LocalBroadcast methodology --no longer need to instantiate with Handler that's in MainService!
 *  2020.01.20      Chris Rider     Refactored to utilize MainService-based RAM structure, instead of MessageService-based RAM structure; no longer need Android-Message Handler stuff.
 *  2020.01.21      Chris Rider     Fixed bug where deliverables list wasn't clearing properly.
 *  2020.01.28      Chris Rider     Fixed a weird bug where omniMessage_reusableObj wasn't working correctly, instead need to create new object each iteration.
 *  2020.01.29      Chris Rider     Made initial load of deliverables flag it as such, so first sync operation uses persisted raw data.
 *  2020.01.31      Chris Rider     Removed sync logic, as OmniMessages can now directly invoke OmniRawMessages to flush to disk. This thread should just load deliverables and focus on existence.
 *                                  Cleanup comments and stuff to be up-to-date with actual latest state of the code.
 *  2020.05.07-08   Chris Rider     Updated calls to isExpired() to support improved behavior.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.models.OmniRawMessage;
import com.messagenetsystems.evolution2.services.MainService;

import java.lang.ref.WeakReference;


public class MessageDeliverableProcessor extends Thread {
    private final String TAG = this.getClass().getSimpleName();

    // Constants..


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;       //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;


    /** Constructor */
    public MessageDeliverableProcessor(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating.");

        this.logMethod = logMethod;

        this.appContextRef = new WeakReference<Context>(appContext);

        try {
            this.omniApplication = ((OmniApplication) appContext);
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Initialize values
        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        this.activeProcessingSleepDuration = 1000;
        this.pausedProcessingSleepDuration = 5000;
        this.loopIterationCounter = 1;
    }


    /*============================================================================================*/
    /* Thread Methods */

    /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
     * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
     * If you directly call .execute(), this run() method will invoke on the same thread you call it from. */
    @Override
    public void run() {
        final String TAGG = "run: ";
        logV(TAGG+"Invoked.");

        long pid = Thread.currentThread().getId();
        logI(TAGG+"Thread starting as process ID #"+ pid);

        int omniRawMessagesSize;
        // NOTE-BELOW LINE... You can NOT do this for some reason... even re-init somehow reuses data/memory
        //OmniMessage omniMessage_reusableObj = new OmniMessage(appContextRef.get(), logMethod);   //instantiate an empty object here so it's not so expensive in the loop during every iteration (populate in each iteration, though)

        String jsonKeyName_scrollsDone = "msgTextScrollsDone";

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

            logV(TAGG+"-------- Iteration #"+loopIterationCounter+" ------------------------");

            // Either do nothing (if paused) or allow work to happen (if not paused)...
            if (pauseProcessing) {
                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(pausedProcessingSleepDuration);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
                }

                logD(TAGG + "Processing is paused. Thread continuing to run, but no work is occurring.");
            } else {
                // Do a short delay to help prevent the thread loop from eating cycles
                try {
                    Thread.sleep(activeProcessingSleepDuration);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }

                try {
                    ////////////////////////////////////////////////////////////////////////////////
                    // DO THE BULK OF THE ACTUAL WORK HERE...

                    // RULES:
                    // There should be no messages in deliverable that aren't in raw... (raw is authoritative, as far as existence of records goes)

                    // Sync existence of message records...
                    // MainService.omniRawMessages is authoritative over MainService.omniMessages_deliverable.
                    // First loop adds records from raw to deliverable.
                    // Second loop removes records from deliverable where none exist in raw.
                    logV(TAGG + "Processing raw->deliverables existence:\n"+
                            "MainService.omniRawMessages contains " + MainService.omniRawMessages.size() + " messages\n"+
                            "MainService.omniMessages_deliverables contains "+MainService.omniMessages_deliverable.size());
                    if (MainService.omniRawMessages.size() == 0) {
                        if (MainService.omniMessages_deliverable.size() != 0) {
                            clearMessagesInMainService();
                            logV(TAGG + " Cleared MainService.omniMessages_deliverable.");
                        }
                    } else {
                        // First loop, to add recrods from raw to deliverable...
                        //for (OmniRawMessage omniRawMessage : MainService.omniRawMessages) {           //DEV-NOTE: This was contributing to a ConcurrentModificationException
                        for (int i = MainService.omniRawMessages.size() - 1; i >= 0; i--) {             //DEV-NOTE: This reverse-loop technique should help out a lot (if not fix it outright)
                            OmniRawMessage omniRawMessage = MainService.omniRawMessages.get(i);

                            // Ensure it's added to deliverable list (the routine will avoid duplicates)
                            logV(TAGG + " Ensuring raw message (" + omniRawMessage.getMessageUUID().toString() + ") exists in MainService.omniMessages_deliverable...");
                            // DEV-NOTE BELOW... this doesn't work for some reason, see note above... you must create a new object.. WHY?!?! oh well, seems to work ok
                            //omniMessage_reusableObj.initWithRawData(omniApplication.getEcosystem(), omniRawMessage);
                            //addMessageToMainService(omniMessage_reusableObj);
                            OmniMessage omniMessage = new OmniMessage(appContextRef.get(), logMethod);
                            omniMessage.initWithRawData(omniApplication.getEcosystem(), omniRawMessage);
                            omniMessage.setThisLastModifiedDate(null);  //initially set to null, so sync routine below can know to prefer persisted raw data
                            addMessageToMainService(omniMessage);
                        }
                        // Second loop, to remove records from deliverable where none exist in raw...
                        //for (OmniMessage omniMessage : MainService.omniMessages_deliverable) {        //DEV-NOTE: This was contributing to a ConcurrentModificationException
                        for (int i = MainService.omniMessages_deliverable.size() - 1; i >= 0; i--) {    //DEV-NOTE: This reverse-loop technique should help out a lot (if not fix it outright)
                            OmniMessage omniMessage = MainService.omniMessages_deliverable.get(i);

                            // Check if this OmniMessage exists in raw-list and remove it from deliverables-list if not
                            if (MainService.omniRawMessages.getOmniRawMessage(omniMessage.getMessageUUID()) == null) {
                                //this deliverable message does not exist in raw-list, so we should remove it from deliverables
                                logV(TAGG + " Removing raw's corresponding OmniMessage (" + omniMessage.getMessageUUID().toString() + ") from MainService.omniMessages_deliverable...");
                                removeMessageFromMainService(omniMessage);
                            }

                            // While we're here, let's also just go ahead and check expiration...
                            // This is not in accordance with our rule as established above, but it can't hurt to make sure (it's kinda related).
                            // (regardless, expired messages don't belong ANYWHERE, so this is insurance)
                            if (omniMessage.isExpired(OmniMessage.EXPIRATION_CALC_METHOD_RELATIVE_DURATION_FROM_DELIVERY, false, appContextRef.get())) {
                                logV(TAGG + " Removing expired raw message (" + omniMessage.getMessageUUID().toString() + ") from MainService.omniRawMessages...");
                                try {
                                    OmniRawMessage omniRawMessage = MainService.omniRawMessages.getOmniRawMessage(omniMessage.getMessageUUID());
                                    MainService.omniRawMessages.removeOmniRawMessage(omniRawMessage);
                                } catch (Exception e) {
                                    logE(TAGG + "  Exception caught: " + e.getMessage());
                                }
                            }
                        }
                    }

                    // END THE BULK OF THE ACTUAL WORK HERE...
                    ////////////////////////////////////////////////////////////////////////////////
                } catch (NullPointerException e) {
                    // This can happen if parent process dies (taking context reference with it) before this loop breaks
                    // So, let's make sure that's not what's happening (we can depend on this flag to be set by .cleanup() which should be called upon destruction of parent process)...
                    if (!isStopRequested) {
                        logW(TAGG + "NullPointerException caught; shutting down!\n"+e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }
            }

            try {
                if (loopIterationCounter + 1 < Long.MAX_VALUE)
                    loopIterationCounter++;
                else
                    loopIterationCounter = 1;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
                loopIterationCounter = 1;
            }

            // this is the end of the loop-iteration, so check whether we will stop or continue
            if (Thread.currentThread().isInterrupted()) {
                logI(TAGG+"Thread will now stop.");
                isThreadRunning = false;
            }
            if (isStopRequested) {
                logI(TAGG+"Thread has been requested to stop and will now do so.");
                isThreadRunning = false;
                break;
            }
        }
    }

    /** Call this to pause processing.
     * This essentially just sets the pause flag (which prevents any work being done).
     */
    public void pauseProcessing() {
        this.pauseProcessing = true;
    }

    /** Call this to resume paused processing.
     * This essentially just resets the pause flag (which allows work to be done).
     */
    public void resumeProcessing() {
        this.pauseProcessing = false;
    }

    /** Call this to terminate the loop and release resources. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            this.isStopRequested = true;

            // Note: At this point, the thread-loop should break on its own, since we check isInterrupted in the while-loop's condition
        } catch (Exception e) {
            logE(TAGG+"Exception caught invoking .interrupt(): "+e.getMessage());
        }

        this.appContextRef = null;
    }


    /*============================================================================================*/
    /* Processing Methods */

    /** Method to add an OmniMessage to the static OmniMessages resource in MainService.
     * (doing this in protest for now Jan-06, as datatype conversion takes too much time for deadline)
     * @param omniMessage
     */
    private void addMessageToMainService(OmniMessage omniMessage) {
        final String TAGG = "addMessageToMainService: ";
        logV(TAGG+"Invoked ("+String.valueOf(omniMessage.getThisLastModifiedDate())+").");

        try {
            MainService.omniMessages_deliverable.addOmniMessage(omniMessage, OmniMessages.ADD_AVOIDING_DUPLICATES);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void removeMessageFromMainService(OmniMessage omniMessage) {
        final String TAGG = "removeMessageFromMainService: ";
        logV(TAGG+"Invoked.");

        try {
            MainService.omniMessages_deliverable.removeOmniMessage(omniMessage);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    private void clearMessagesInMainService() {
        final String TAGG = "clearMessagesInMainService: ";
        logV(TAGG+"Invoked.");

        try {
            MainService.omniMessages_deliverable.clear();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Getter/Setter Methods */

    public boolean isThreadRunning() {
        return this.isThreadRunning;
    }


    /*============================================================================================*/
    /* Logging Methods */

    private void logV(String tagg) {
        log(LOG_SEVERITY_V, tagg);
    }
    private void logD(String tagg) {
        log(LOG_SEVERITY_D, tagg);
    }
    private void logI(String tagg) {
        log(LOG_SEVERITY_I, tagg);
    }
    private void logW(String tagg) {
        log(LOG_SEVERITY_W, tagg);
    }
    private void logE(String tagg) {
        log(LOG_SEVERITY_E, tagg);
    }
    private void log(int logSeverity, String tagg) {
        switch (logMethod) {
            case Constants.LOG_METHOD_LOGCAT:
                switch (logSeverity) {
                    case LOG_SEVERITY_V:
                        Log.v(TAG, tagg);
                        break;
                    case LOG_SEVERITY_D:
                        Log.d(TAG, tagg);
                        break;
                    case LOG_SEVERITY_I:
                        Log.i(TAG, tagg);
                        break;
                    case LOG_SEVERITY_W:
                        Log.w(TAG, tagg);
                        break;
                    case LOG_SEVERITY_E:
                        Log.e(TAG, tagg);
                        break;
                }
                break;
            case Constants.LOG_METHOD_FILELOGGER:
                switch (logSeverity) {
                    case LOG_SEVERITY_V:
                        FL.v(TAG, tagg);
                        break;
                    case LOG_SEVERITY_D:
                        FL.d(TAG, tagg);
                        break;
                    case LOG_SEVERITY_I:
                        FL.i(TAG, tagg);
                        break;
                    case LOG_SEVERITY_W:
                        FL.w(TAG, tagg);
                        break;
                    case LOG_SEVERITY_E:
                        FL.e(TAG, tagg);
                        break;
                }
                break;
        }
    }
}
