package com.messagenetsystems.evolution2.services;

/* DeliveryService
 * This is the primary service responsible for providing the ability to invoke delivery activities.
 * It should not be confused with Message-data management! This is focused just on deliveries of messages we alread have data for in RAM.
 * Whatever message data resides in MainService.omniMessages_deliverable, we assume is eligible for delivery.
 *
 * Incarnation & History...
 *  Initially, all this really did was host a broadcast receiver so that any process can broadcast to for requesting/commanding delivery-related activities.
 *  Refer to constants below for broadcast/intent stuff to use, to actually make that work.
 *  Also, all the supporting stuff (activity class instances, intents, etc.
 *
 *  Soon after, this also evolved to host a rotator thread process to act as our main message-rotator.
 *  That process communicates back to this service by way of Android-Messages and a subclassed Handler.
 *  So, the child threads are basically just timers that invoke our handler (most actual work is done below in our subclassed handler!).
 *
 *  This service also contains our lists of message-UUIDs that are meant to reflect the groupings and orders of actual delivery.
 *  In fact, much of the hardcore logic and work is simply to generate/populate those lists. The rotator merely cycles through them.
 *
 * Being a service, we should be more resilient against Android's task-killer and can thus keep
 * delivery activities going even if the feeder threads are killed, network goes down, etc.
 *
 * This is hosted by MainService.
 *
 * Message Delivery Rules!
 *  - "Delivery" means that a message gets conveyed to humans through audio and/or visual means.
 *  - Delivery of scrolling text should scroll twice upon very first dispatch, then once each time thereafter.
 *  - Message must never be interrupted in mid-delivery (even for a higher priority message), unless done so by explicit received command.
 *  - If multimode audio or video component of a message finishes before the other component, don't start another repeat until message repeats again (if applicable).
 *  - Only one message at a time may be delivered, so as to avoid conflicts of information.
 *  - Deliveries of multiple messages should be delineated by a brief clock view.
 *  - Overall delivery needs to be smooth and non-jarring to recipients.
 *  - Per Kevin, we should support priority-tolerance, as well.
 *  - Any new messages that come in while others are rotating (all of equal priority) need to be inserted into next-delivered, then resume normal position after that.
 *      Example:    Messages actively in-rotation:          A  [B]  C       (3 active messages in rotation, with B currently delivering)
 *                  New message (D) comes in during:        A  [B]  D   C   (D needs to be next after B --but only the first time --after D delivers, it needs to go after C on next rotation)
 *                  Message (D) during/after delivery:      A   B  [D]  C   (new message D is delivering)
 *                                                          A   B   D  [C]  (D has finished delivery, so original C is up next as usual)
 *                                                         [A]  B   C   D   (next rotation naturally includes newest D message in a normal order)
 *
 *      Example:    Messages actively in-rotation:         [A]  B
 *                  New messages (C & D) come in during:   [A]  C   D   B   (new messages get
 *  - The existence of a higher priority message should prevent delivery of lesser priority messages, until higher msg expires or is closed.
 *      * Unless, priority tolerance value is greater than 0, then you should allow that number of lower priority messages to be intermixed among the high priority cycling.
 *      Example:    Messages actively in-rotation:          A1 [B1] C1      (3 active equal-low priority messages in rotation, with B currently delivering)
 *                  New message (A2) comes in during:       A2
 *
 * Revisions:
 *  2019.12.10      Chris Rider     Created.
 *  2019.12.16      Chris Rider     Moved in TextToSpeechServicer from MainService.
 *                                  Added finish methods and various intent/etc. strings.
 *  2019.12.18-20   Chris Rider     Implemented support for current/active messages list of deliverable OmniMessages (MainService.omniMessages_deliverable).
 *                                  Implemented handler from MainService for deliverable OmniMessages (MainService.omniMessages_deliverable).
 *  2020.01.02      Chris Rider     Finishing out from before holiday, with creating and handing off Android-Message Handler to DeliveryRotator.
 *  2020.01.03      Chris Rider     Migrated from MainService Handler/Message stuff to LocalBroadcast methodology --no longer need to instantiate with Handler that's in MainService!
 *  2020.01.06      Chris Rider     Added DeliveryQueueProcessor thread, and added forgotten thread .cleanup() method calls, as well as now utilizing Android-msg handler subclass.
 *  2020.01.23      Chris Rider     Refactored local OmniMessages datastore to instead be a list of UUIDs and reference the MainService deliverables RAM.
 *  2020.01.24      Chris Rider     Added thread to monitor child processes and methods to restart them as needed.
 *  2020.01.28      Chris Rider     Added ability to flag delivery activity whether to write last-delivered UUID or not.
 *  2020.02.20      Chris Rider     Fixed some null-ref / race condition on removal/delivery of messages.
 *  2020.04.21      Chris Rider     Now supporting higher priority messages taking delivery precedence over lower priority messages.
 *  2020.06.26      Chris Rider     Ability to derive and save the next-next message's flasher light command, so delivery activity may use it if needed.
 *  2020.07.29      Chris Rider     Implemented newly-integrated flasher light driver. Started attempting next-msg data acquisition, but still so far unused.. might need refactored or reworked entirely?
 *  2020.08.05      Chris Rider     Moved handler's MSGHANDLER_ACTION_LIST_SYNC logic into a new thread with lower priority, as well as all child threads so we can control their priority.
 *  2020.08.07      Chris Rider     Added thread-ID acquisition and output to notification.
 *  2020.09.28      Chris Rider     Fixed theoretical potential for uncaught overflow in loop counter.
 */

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.OmniApplication;
import com.messagenetsystems.evolution2.activities.ClockActivity;
import com.messagenetsystems.evolution2.activities.DeliverScrollingMsgActivity;
import com.messagenetsystems.evolution2.activities.DeliverWebpageActivity;
import com.messagenetsystems.evolution2.models.BannerMessage;
import com.messagenetsystems.evolution2.models.FlasherLights;
import com.messagenetsystems.evolution2.models.OmniMessage;
import com.messagenetsystems.evolution2.models.OmniMessages;
import com.messagenetsystems.evolution2.threads.DeliveryQueueProcessor;
import com.messagenetsystems.evolution2.threads.DeliveryRotator;
import com.messagenetsystems.evolution2.utilities.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class DeliveryService extends Service {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final int MSGHANDLER_ACTION_DELIVER_MESSAGE = 1;      // Deliver whatever message is up for delivery
    public static final int MSGHANDLER_ACTION_LIST_LOAD = 2;            // Load the queue with whatever is in the deliverables list
    public static final int MSGHANDLER_ACTION_LIST_SYNC = 3;            // Update the queue with any differences from the deliverables list (add new, remove gone, etc.)

    public static final String BROADCAST_RECEIVER_NAME = "com.messagenetsystems.evolution2.DeliveryService.broadcastReceiver";

    public static final String BROADCAST_INTENTKEY_ACTION = "com.messagenetsystems.evolution2.DeliveryService.actionToDo";
    public static final String BROADCAST_INTENTVALUE_ACTION_ACTIVITY_LAUNCH = "com.messagenetsystems.evolution2.DeliveryService.actionLaunchActivity";
    public static final String BROADCAST_INTENTVALUE_ACTION_ACTIVITY_FINISH = "com.messagenetsystems.evolution2.DeliveryService.actionFinishActivity";
    public static final String BROADCAST_INTENTVALUE_ACTION_ACTIVITY_FINISHALL = "com.messagenetsystems.evolution2.DeliveryService.actionFinishAllActivities";

    public static final String BROADCAST_INTENTKEY_ACTIVITY_NAME = "com.messagenetsystems.evolution2.DeliveryService.activityName";
    public static final String ACTIVITY_NAME_CLOCK = ClockActivity.class.getSimpleName();
    public static final String ACTIVITY_NAME_SCROLLINGMSG = DeliverScrollingMsgActivity.class.getSimpleName();
    public static final String ACTIVITY_NAME_WEBVIEW_WEB = DeliverWebpageActivity.class.getSimpleName();

    public static final String BROADCAST_INTENTKEY_MSG_OBJECT = "com.messagenetsystems.evolution2.DeliveryService.messageObject";
    public static final String BROADCAST_INTENTKEY_MSG_UUID = "com.messagenetsystems.evolution2.DeliveryService.messageUuid";

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private OmniApplication omniApplication;
    public volatile boolean hasFullyStarted;
    private MonitorChildProcesses monitorChildProcesses;
    public volatile boolean isThreadAlive_rotator, isThreadAlive_queue;

    private int tid = 0;

    private DeliveryQueueProcessor deliveryQueueProcessor;
    private DeliveryRotator deliveryRotator;

    protected Handler deliveryServiceHandler;                                                       //Handler-thread for taking care of Android-Messages from child threads (so it can talk back to this class)

    private DeliveryServiceDeliveryActionReceiver deliveryServiceDeliveryActionReceiver;

    private Intent textToSpeechServicerIntent;

    private Intent clockActivityIntent;
    private Intent clockActivityIntentFinish;

    private Intent scrollingMsgActivityIntent;
    private Intent scrollingMsgActivityIntentFinish;

    private Intent webpageMsgActivityIntent;
    private Intent webpageMsgActivityIntentFinish;

    // Our ordered lists of OmniMessage UUID(s) to rotate through for delivery
    // (these get populated and ordered by the SYNC routine in the handler that gets triggered by DeliveryQueueProcessor)
    // IMPORTANT! -- Per our rules, these lists should contain messages of EQUAL PRIORITY. Differing priorities
    public static volatile List<UUID> omniMessageUUIDsToRotate;                                     //Our primary-current list of message(s) to rotate
    public static volatile List<UUID> omniMessageUUIDsToRotate_new;                                 //Any newly-arrived message(s) to include in rotation

    // Flags for current delivery status...
    // It's important for delivery activities to update these as delivery progresses, so that
    // DeliveryRotator (and possible others) knows what to do.
    public static volatile UUID omniMessageUuidDelivery_loading;          //message has begun loading
    public static volatile UUID omniMessageUuidDelivery_currently;        //message has positively begun delivery
    public static volatile UUID omniMessageUuidDelivery_lastCompleted;    //message that last completed delivery
    public static volatile UUID omniMessageUuidDelivery_next;             //next message for delivery after current

    public static FlasherLights.OmniCommandCodes flasherLightCommandCodes;
    public static volatile boolean currentMsgHasLightCommand;
    public static byte flasherLightCommandCode_nextMessage;

    private Intent deliveryStatusInformIntent;


    /** Constructors (singleton pattern) */
    public DeliveryService(Context appContext) {
        super();
    }
    public DeliveryService() {
    }


    /*============================================================================================*/
    /* Service methods */

    /** Service onCreate handler **/
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        logV(TAGG+"Invoked.");

        this.appContextRef = new WeakReference<Context>(getApplicationContext());

        this.logMethod = Constants.LOG_METHOD_FILELOGGER;

        try {
            this.omniApplication = ((OmniApplication) getApplicationContext());
        } catch (Exception e) {
            logE("Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        this.hasFullyStarted = false;

        this.deliveryServiceHandler = new DeliveryServiceHandler();

        this.deliveryQueueProcessor = new DeliveryQueueProcessor(getApplicationContext(), logMethod, deliveryServiceHandler);
        this.deliveryRotator = new DeliveryRotator(getApplicationContext(), logMethod, deliveryServiceHandler);

        this.deliveryServiceDeliveryActionReceiver = new DeliveryServiceDeliveryActionReceiver();
        registerReceiver(this.deliveryServiceDeliveryActionReceiver, new IntentFilter(BROADCAST_RECEIVER_NAME));

        this.textToSpeechServicerIntent = new Intent(this, TextToSpeechServicer.class);

        this.clockActivityIntent = new Intent(getApplicationContext(), ClockActivity.class);
        this.clockActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);                          //for the clock, no other activities should ever exist above it
        //this.clockActivityIntentFinish = new Intent("");                                            //TODO create and use intent filter for clock

        this.scrollingMsgActivityIntent = new Intent(getApplicationContext(), DeliverScrollingMsgActivity.class);
        this.scrollingMsgActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);                    //required to actually show a started activity from a service
        //this.scrollingMsgActivityIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        this.scrollingMsgActivityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.scrollingMsgActivityIntentFinish = new Intent(DeliverScrollingMsgActivity.INTENT_ACTION_CMD_FINISH);

        this.webpageMsgActivityIntent = new Intent(getApplicationContext(), DeliverWebpageActivity.class);
        this.webpageMsgActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.webpageMsgActivityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.webpageMsgActivityIntentFinish = new Intent(DeliverWebpageActivity.INTENT_ACTION_CMD_FINISH);

        omniMessageUUIDsToRotate = new ArrayList<>();
        omniMessageUUIDsToRotate_new = new ArrayList<>();

        omniMessageUuidDelivery_loading = null;
        omniMessageUuidDelivery_currently = null;
        omniMessageUuidDelivery_lastCompleted = null;
        omniMessageUuidDelivery_next = null;

        flasherLightCommandCodes = new FlasherLights.OmniCommandCodes(FlasherLights.PLATFORM_MNS); //TODO make this not hard-coded
        flasherLightCommandCode_nextMessage = flasherLightCommandCodes.CMD_UNKNOWN;

        this.deliveryStatusInformIntent = new Intent(Constants.Intents.Filters.MAIN_APP_DELIVERY_STATUS);


        // Initialize our monitoring process
        this.monitorChildProcesses = new MonitorChildProcesses();
    }

    /** Service onStart handler **/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        logV(TAGG+"Invoked.");

        // Running in foreground better ensures Android won't kill us   //TODO: evaluate whether this is proper for this class or not?
        startForeground(0, null);

        tid = android.os.Process.myTid();

        // Go ahead and show clock
        launchActivity(ACTIVITY_NAME_CLOCK);

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start services...
        // Remember that any Service you start here (startService) will exist in the same thread as DeliveryService!
        // So, if you want to avoid that, you should wrap them in new-Thread (with nested new-Runnable to easily catch issues upon compile rather than runtime)
        new Thread(new Runnable() {
            @Override
            public void run() {
                startService(textToSpeechServicerIntent);
            }
        }).start();

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start threads...
        ThreadUtils.doStartThread(getBaseContext(), deliveryQueueProcessor, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_NORMAL-2);
        ThreadUtils.doStartThread(getBaseContext(), deliveryRotator, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_NORMAL-2);

        while (!this.deliveryQueueProcessor.isThreadRunning() && !this.deliveryRotator.isThreadRunning()) {
            //wait here while threads start up
            logV(TAGG+"Waiting for child threads to start.");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
            }
        }

        // Update flag that we appear healthy
        this.hasFullyStarted = true;

        // Finish service startup...
        logI(TAGG+"Service started.");
        omniApplication.appendNotificationWithText(TAG+" started. (tid:"+tid+")");    // Update notification that everything is started and running

        // Start our child-monitoring process
        ThreadUtils.doStartThread(getBaseContext(), monitorChildProcesses, ThreadUtils.SPAWN_NEW_THREAD_TRUE, ThreadUtils.PRIORITY_MINIMUM);

        // Inform process monitor that we have started
        //omniApplication.processStatusList.recordProcessStart(this.getClass());

        // Ensure this service is very hard to kill and that it even restarts if needed
        //return START_STICKY;      //NOTE: not necessary since this service lives under MainService (which itself starts sticky)???
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // This gets invoked when the app is killed either by Android or the user.
        // To absolutely ensure it gets invoked, it's best-practice to call stopService somewhere if you can.
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Invoked.");

        // Update notification so we can know something went wrong if it wasn't supposed to
        // (a legit stop will then reset the notification so you don't get a false positive)
        omniApplication.appendNotificationWithText("DeliveryService died! ("+new Date().toString()+")");

        // Unregister receiver that we registered in onCreate
        if (deliveryServiceDeliveryActionReceiver != null) {
            unregisterReceiver(deliveryServiceDeliveryActionReceiver);
            deliveryServiceDeliveryActionReceiver = null;
        }

        // Stop any stuff we started
        stopService(textToSpeechServicerIntent);

        if (this.monitorChildProcesses != null) {
            this.monitorChildProcesses.cleanup();
            this.monitorChildProcesses = null;
        }
        if (this.deliveryQueueProcessor != null) {
            this.deliveryQueueProcessor.cleanup();
            this.deliveryQueueProcessor = null;
        }
        if (this.deliveryRotator != null) {
            this.deliveryRotator.cleanup();
            this.deliveryRotator = null;
        }

        // Explicitly release variables (not strictly necessary, but can't hurt to force garbage collection)
        this.deliveryServiceHandler = null;
        this.textToSpeechServicerIntent = null;
        this.clockActivityIntent = null;
        this.scrollingMsgActivityIntent = null;
        this.scrollingMsgActivityIntentFinish = null;
        this.webpageMsgActivityIntent = null;
        this.webpageMsgActivityIntentFinish = null;
        this.omniApplication = null;

        // Clean up anything else
        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    /*============================================================================================*/
    /* Delivery-Message Methods */

    /** Figure out what next new message to deliver should be and invoke deliverMessage() for it. */
    private void deliverNewMessage() {
        final String TAGG = "deliverNewMessage: ";

        if (omniMessageUUIDsToRotate_new.size() <= 0) {
            logD(TAGG+"The omniMessageUUIDsToRotate_new list is empty. Nothing there to deliver.");
            return;
        }

        try {
            //get and load the first message into this routine
            UUID firstMessageUUID = omniMessageUUIDsToRotate_new.get(0);
            OmniMessage omniMessageFromNewListToDeliver = MainService.omniMessages_deliverable.getOmniMessage(firstMessageUUID, OmniMessages.GET_OMNIMESSAGE_AS_REFERENCE);

            if (firstMessageUUID == null || omniMessageFromNewListToDeliver == null) {
                logW(TAGG+"Failed to get first message in the rotation_new list, aborting.");
                return;
            }

            //now remove it from new-msg list
            //(this should help ensure it doesn't have a chance of resurrecting or persisting in new)
            if(!omniMessageUUIDsToRotate_new.remove(omniMessageFromNewListToDeliver.getMessageUUID())) {
                //element failed to remove from list
                logW(TAGG+"Failed to remove element ("+omniMessageFromNewListToDeliver.getMessageUUID().toString()+") from omniMessageUUIDsToRotate_new!'");
            }

            //deliver that new message
            deliverMessage(omniMessageFromNewListToDeliver, true); //we don't write last-delivered UUID upon completion of new messages, so rotator can continue after last existing message (see our main delivery rules)

            //save it to end of main list
            omniMessageUUIDsToRotate.add(omniMessageFromNewListToDeliver.getMessageUUID());
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Figure out what next existing message to deliver should be and invoke deliverMessage() for it. */
    private void deliverExistingMessage() {
        final String TAGG = "deliverExistingMessage: ";

        if (omniMessageUUIDsToRotate.size() <= 0) {
            logD(TAGG+"The omniMessageUUIDsToRotate list is empty. Nothing there to deliver.");
            return;
        }

        try {
            int indexOfPreviousMessageUUID = -1;
            int indexOfNextMessageUUID;

            //we want to get the next index after previously-delivered message...
            //so first, get list-index of previously-delivered message
            if (omniMessageUuidDelivery_lastCompleted == null) {
                //no previous message delivered yet, so use first in list
                logV(TAGG+"No previous message delivered yet (is null), so just simply use first in list.");
                indexOfPreviousMessageUUID = 0;
            } else {
                //find index of previous UUID in list
                logV(TAGG+"Last-delivery-completed message is "+ omniMessageUuidDelivery_lastCompleted +". Finding its index in rotation list...");
                for (int i = 0; i < omniMessageUUIDsToRotate.size(); i++) {
                    logV(TAGG+" Checking "+omniMessageUUIDsToRotate.get(i).toString());
                    if (omniMessageUUIDsToRotate.get(i).toString().equals(omniMessageUuidDelivery_lastCompleted.toString())) {
                        logV(TAGG+"  Found last-delivered message "+omniMessageUuidDelivery_lastCompleted.toString()+" in rotation list at index position #"+i);
                        indexOfPreviousMessageUUID = i;
                        break;
                    }
                }
            }

            //then use that previous index to derive next index value for our main list
            if (omniMessageUuidDelivery_lastCompleted == null) {
                //we want to use the first in the list (oldest deliverable), no need to derive next
                indexOfNextMessageUUID = indexOfPreviousMessageUUID;
            } else {
                indexOfNextMessageUUID = deriveNextIndexFrom(indexOfPreviousMessageUUID, omniMessageUUIDsToRotate);
            }

            //get the OmniMessage object at the above-derived next index and deliver it
            deliverMessage(MainService.omniMessages_deliverable.getOmniMessage(omniMessageUUIDsToRotate.get(indexOfNextMessageUUID), OmniMessages.GET_OMNIMESSAGE_AS_REFERENCE), false);

            //go ahead and get the following message's data
            flasherLightCommandCode_nextMessage = deriveNextLightCmdFrom(indexOfPreviousMessageUUID, omniMessageUUIDsToRotate);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This is essentially the MAIN method for initiating actual delivery of a message.
     * If things are right, then it is what sets the UUID that the delivery activity (which it also executes) should reference for delivery.
     * @param omniMessage OmniMessage object to deliver.
     * @param skipWriteLastDeliveredUUID Whether to skip saving the last-delivered UUID upon delivery completion (you would skip for a newly injected message). */
    private void deliverMessage(OmniMessage omniMessage, final boolean skipWriteLastDeliveredUUID) {
        String TAGG;
        if (omniMessage == null) {
            TAGG = "deliverMessage(null): ";
            logW(TAGG+"Provided OmniMessage is null, aborting.");
            return;
        } else {
            TAGG = "deliverMessage(" + omniMessage.getMessageUUID() + "): ";
        }

        try {
            //figure out what kind of message this is and deliver
            //it to the appropriate activity(ies) depending on that
            int msgType = omniMessage.getMsgType();
            switch (msgType) {
                case OmniMessage.MSG_TYPE_WEB_PAGE:
                    logV(TAGG+"Message type is: MSG_TYPE_WEB_PAGE");

                    omniMessageUuidDelivery_loading = omniMessage.getMessageUUID();

                    //broadcast any light command now, so it gets going by the time scrolling begins
                    if (FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)) == DeliveryService.flasherLightCommandCodes.CMD_LIGHT_NONE
                            || FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)) == DeliveryService.flasherLightCommandCodes.CMD_UNKNOWN) {
                        //(no light mode for this message, so no need to send command)
                        currentMsgHasLightCommand = false;
                    } else {
                        currentMsgHasLightCommand = true;
                        /*
                        FlasherLights.broadcastLightCommand(getApplicationContext(),
                                FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)),
                                //omniMessage.getFlasherDurationSecondsToDo(),  TODO
                                omniMessage.getBannerMessage().dbb_light_duration,
                                omniMessage.getMessageUUID().toString());
                                */
                        //FlasherLightService.startLight(getApplicationContext(), FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)));
                        FlasherLightService.startLightForMessage(getApplicationContext(), omniMessage.getMessageUUID());
                    }

                    //start delivery activity
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            launchActivity(ACTIVITY_NAME_WEBVIEW_WEB, skipWriteLastDeliveredUUID);
                        }
                    }, 900);

                    break;
                case OmniMessage.MSG_TYPE_VIDEO_FILE:
                    logV(TAGG+"Message type is: MSG_TYPE_VIDEO_FILE");

                    //TODO

                    break;
                case OmniMessage.MSG_TYPE_VIDEO_STREAM:
                    logV(TAGG+"Message type is: MSG_TYPE_VIDEO_STREAM");

                    //TODO

                    break;
                case OmniMessage.MSG_TYPE_PICTURE:
                    logV(TAGG+"Message type is: MSG_TYPE_PICTURE");

                    //TODO

                    break;
                case OmniMessage.MSG_TYPE_AUDIO_FILE:
                    logV(TAGG+"Message type is: MSG_TYPE_AUDIO_FILE");

                    //TODO

                    break;
                case OmniMessage.MSG_TYPE_AUDIO_STREAM:
                    logV(TAGG+"Message type is: MSG_TYPE_AUDIO_STREAM");

                    //TODO

                    break;
                case OmniMessage.MSG_TYPE_LOCATIONMAP:
                    logV(TAGG+"Message type is: MSG_TYPE_LOCATIONMAP");

                    //TODO

                    break;
                case OmniMessage.MSG_TYPE_TEXT:
                    logV(TAGG+"Message type is: MSG_TYPE_TEXT");

                    omniMessageUuidDelivery_loading = omniMessage.getMessageUUID();

                    //broadcast any light command now, so it gets going by the time scrolling begins
                    if (FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)) == DeliveryService.flasherLightCommandCodes.CMD_LIGHT_NONE
                            || FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)) == DeliveryService.flasherLightCommandCodes.CMD_UNKNOWN) {
                        //(no light mode for this message, so no need to send command)
                        currentMsgHasLightCommand = false;
                    } else {
                        currentMsgHasLightCommand = true;
                        /*
                        FlasherLights.broadcastLightCommand(getApplicationContext(),
                                FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)),
                                //omniMessage.getFlasherDurationSecondsToDo(),  TODO
                                omniMessage.getBannerMessage().dbb_light_duration,
                                omniMessage.getMessageUUID().toString());
                                */
                        //FlasherLightService.startLight(getApplicationContext(), FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage.getBannerMessage().dbb_light_signal.charAt(0)));
                        FlasherLightService.startLightForMessage(getApplicationContext(), omniMessage.getMessageUUID());
                    }

                    //start delivery activity
                    //launchActivity(ACTIVITY_NAME_SCROLLINGMSG, skipWriteLastDeliveredUUID);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            launchActivity(ACTIVITY_NAME_SCROLLINGMSG, skipWriteLastDeliveredUUID);
                        }
                    }, 900);

                    break;
                case OmniMessage.MSG_TYPE_UNKNOWN:
                    logW(TAGG+"Message type is: MSG_TYPE_UNKNOWN");

                    // We could have encountered an issue/error with converting old v1 banner JSON,
                    // so check for that and convert as best you can. //TODO: should properly fix this in OmniMessage or BannerMessage export?
                    BannerMessage bannerMessage = omniMessage.getBannerMessage();
                    //bannerMessage

                    break;
                default:
                    logW(TAGG+"Message type is not specified.");

                    //TODO

                    break;
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** This finds the "next" list item, after the provided index, in the provided list
     * and returns its index/position in the list, in a round-robin style.
     * @param providedIndex Index from which to find and return the next item.
     * @param providedList List object to work within.
     * @return Index-position where the next UUID is to be found in the provided list.
     */
    private static int deriveNextIndexFrom(final int providedIndex, final List<UUID> providedList) {
        final String TAGG = "deriveNextIndexFrom: ";

        int ret = 0;
        int indexOfNextListItem;
        int sizeOfList = 0;

        //validate provided data
        sizeOfList = providedList.size();
        if (sizeOfList <= 0) {
            //provided list is empty
            FL.d(TAGG+"Provided list is empty, returning "+ret+".");
            return ret;
        }
        if (providedIndex < 0 || providedIndex >= sizeOfList) {
            //provided index is out of bounds of provided list
            FL.d(TAGG+"Provided index ("+providedIndex+") is out of bounds of provided list ("+sizeOfList+"), returning "+ret+".");
            return ret;
        }

        //if we got to here, then inputs are valid, so continue processing...
        try {
            int endingListIndex = sizeOfList - 1;

            //figure out what the next index after provided one should be
            if (providedIndex >= endingListIndex) {
                //provided index is as the end of the list, so wrap back around, round-robin style
                indexOfNextListItem = 0;
            } else {
                indexOfNextListItem = providedIndex + 1;
            }

            //just to extra safe, validate calculated index of next message
            if (indexOfNextListItem >=0 && indexOfNextListItem < sizeOfList) {
                FL.v(TAGG+"Index found ("+indexOfNextListItem+") for next item in list.");
                ret = indexOfNextListItem;
            } else {
                FL.w(TAGG+"Calculated index of next item ("+indexOfNextListItem+") is out of bounds of provided list ("+sizeOfList+"), returning "+ret+".");
                return ret;
            }
        } catch (Exception e) {
            FL.e(TAGG+"Exception caught: "+e.getMessage());
        }

        FL.v(TAGG+"Returning "+ret);
        return ret;
    }

    /** This finds the "next" list item, after the provided index, in the provided list
     * and returns its light command, in a round-robin style.
     * @param providedIndex Index from which to find and return the next item.
     * @param providedList List object to work within.
     * @return Flasher ligth command code byte of the next message in the provided list.
     */
    public static byte deriveNextLightCmdFrom(final int providedIndex, final List<UUID> providedList) {
        final String TAGG = "deriveNextLightCmdFrom: ";

        byte ret = DeliveryService.flasherLightCommandCodes.CMD_LIGHT_NONE;

        try {
            int indexOfNextListItem = deriveNextIndexFrom(providedIndex, providedList);
            UUID uuidOfNextListItem = providedList.get(indexOfNextListItem);
            OmniMessage omniMessage_next = MainService.omniMessages_deliverable.getOmniMessage(uuidOfNextListItem, OmniMessages.GET_OMNIMESSAGE_AS_REFERENCE);
            //OmniMessage omniMessage_next = MainService.omniMessages_deliverable.getOmniMessage(omniMessageUUIDsToRotate.get(indexOfNextListItem), OmniMessages.GET_OMNIMESSAGE_AS_REFERENCE);

            ret = FlasherLights.OmniCommandCodes.asciiToDecByte(omniMessage_next.getBannerMessage().dbb_light_signal.charAt(0));
        } catch (Exception e) {
            FL.e(TAGG+"Exception caught: "+e.getMessage());
        }

        FL.v(TAGG+"Returning "+ret);
        return ret;
    }

    /*============================================================================================*/
    /* Delivery-Activity Methods */

    /** Public-static method for anyone to call to launch an activity.
     * It basically just broadcasts an intent for you, that DeliveryServiceDeliveryActionReceiver gets.
     * That receiver then will call our non-static launchActivity method for us.
     * @param activityName Activity-name constant (DeliveryService) for the activity you want to launch.
     * @return Boolean of whether broadcast happened or not.
     */
    public static boolean launchActivity(Context context, @NonNull String activityName) {
        final String TAGG = "launchActivity(\""+activityName+"\"): ";
        boolean ret;

        try {
            //TODO: add putextra for additional fields (like doWriteLastDeliveredUUID)

            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(BROADCAST_INTENTKEY_ACTION, BROADCAST_INTENTVALUE_ACTION_ACTIVITY_LAUNCH);
            i.putExtra(BROADCAST_INTENTKEY_ACTIVITY_NAME, activityName);
            context.sendBroadcast(i);
            ret = true;
        } catch (Exception e) {
            Log.e(DeliveryService.class.getSimpleName(), TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        return ret;
    }

    /** Local method to call to launch an activity from here in this class.
     * Normally, this is what the BroadcastReceiver calls.
     * @param activityName Activity-name constant (DeliveryService) for the activity you want to launch.
     */
    private void launchActivity(@NonNull String activityName, boolean skipWriteLastDeliveredUUID) {
        final String TAGG = "launchActivity(\""+activityName+"\"): ";
        logV(TAGG+"Invoked.");

        try {
            String currentActivityName = omniApplication.getCurrentVisibleActivityName();

            if (activityName.equals(currentActivityName)) {
                //specified activity is already active
                logW(TAGG+"Activity is already active.");
            } else {
                //specified activity is not active, so let's look into launching it
                if (activityName.equals(ACTIVITY_NAME_CLOCK)) {
                    //launch clock activity
                    startActivity(clockActivityIntent);
                } else if (activityName.equals(ACTIVITY_NAME_SCROLLINGMSG)) {
                    //add any necessary stuff to the intent
                    scrollingMsgActivityIntent.putExtra("skipWriteLastDeliveredUUID", skipWriteLastDeliveredUUID);

                    //launch activity to deliver message
                    startActivity(scrollingMsgActivityIntent);
                } else if (activityName.equals(ACTIVITY_NAME_WEBVIEW_WEB)) {
                    startActivity(webpageMsgActivityIntent);
                /* TODO: Other delivery activities go here in more else-if statements... */
                } else {
                    logW(TAGG+"Specified activity is not handled.");
                }
            }
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }
    private void launchActivity(@NonNull String activityName) {
        launchActivity(activityName, false);
    }

    public static boolean finishActivity(Context context, @NonNull String activityName) {
        final String TAGG = "finishActivity(\""+activityName+"\"): ";
        boolean ret;

        try {
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(BROADCAST_INTENTKEY_ACTION, BROADCAST_INTENTVALUE_ACTION_ACTIVITY_FINISH);
            i.putExtra(BROADCAST_INTENTKEY_ACTIVITY_NAME, activityName);
            context.sendBroadcast(i);
            ret = true;
        } catch (Exception e) {
            Log.e(DeliveryService.class.getSimpleName(), TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        return ret;
    }
    private void finishActivity(@NonNull String activityName) throws Exception {
        final String TAGG = "finishActivity(\""+activityName+"\"): ";

        if (activityName.equals(ClockActivity.class.getSimpleName())) {
            sendBroadcast(this.clockActivityIntentFinish);
        } else if (activityName.equals(DeliverScrollingMsgActivity.class.getSimpleName())) {
            sendBroadcast(this.scrollingMsgActivityIntentFinish);
        } else {
            logW(TAGG+"Unhandled activityName.");
        }
    }

    public static boolean finishAllDeliveryActivities(Context context) {
        final String TAGG = "finishAllDeliveryActivities: ";
        boolean ret;

        try {
            Intent i = new Intent(BROADCAST_RECEIVER_NAME);
            i.putExtra(BROADCAST_INTENTKEY_ACTION, BROADCAST_INTENTVALUE_ACTION_ACTIVITY_FINISHALL);
            context.sendBroadcast(i);
            ret = true;
        } catch (Exception e) {
            Log.e(DeliveryService.class.getSimpleName(), TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        return ret;
    }
    private void finishAllDeliveryActivities() {
        final String TAGG = "finishAllDeliveryActivities: ";

        try {
            finishActivity(DeliverScrollingMsgActivity.class.getSimpleName());
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Utility Methods */

    private void restartThread_rotator() {
        final String TAGG = "restartThread_rotator: ";
        logV(TAGG+"Trying to restart DeliveryRotator...");

        int maxWaitForStart = 10;

        try {
            if (this.deliveryRotator != null) {
                this.deliveryRotator.cleanup();
            }

            this.deliveryRotator = new DeliveryRotator(appContextRef.get(), logMethod, deliveryServiceHandler);
            this.deliveryRotator.start();

            while (!this.deliveryRotator.isThreadRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for thread to start.");

                maxWaitForStart--;
                if (maxWaitForStart < 0) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void restartThread_queue() {
        final String TAGG = "restartThread_queue: ";
        logV(TAGG+"Trying to restart DeliveryQueueProcessor...");

        int maxWaitForStart = 10;

        try {
            if (this.deliveryQueueProcessor != null) {
                this.deliveryQueueProcessor.cleanup();
            }

            this.deliveryQueueProcessor = new DeliveryQueueProcessor(appContextRef.get(), logMethod, deliveryServiceHandler);
            this.deliveryQueueProcessor.start();

            while (!this.deliveryQueueProcessor.isThreadRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for thread to start.");

                maxWaitForStart--;
                if (maxWaitForStart < 0) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private int findHighestPriorityValueInRotation() {
        final String TAGG = "findHighestPriorityValueInRotation";
        int ret = -1;

        try {
            int thisPriority;
            int highestPriorityFound = 0;
            for (UUID uuid : omniMessageUUIDsToRotate) {
                thisPriority = MainService.omniMessages_deliverable.getOmniMessage(uuid, OmniMessages.GET_OMNIMESSAGE_AS_REFERENCE).getMsgPriority();
                if (thisPriority > highestPriorityFound) {
                    highestPriorityFound = thisPriority;
                }
            }
            ret = highestPriorityFound;
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret < 0) {
            logW(TAGG + "No highest priority found, is list empty?");
        }

        logV(TAGG + "Returning "+String.valueOf(ret));
        return ret;
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Thread to monitor child processes, and restart them if necessary.
     * WARNING: You should start this thread only after you're sure the monitored processes have started! */
    private class MonitorChildProcesses extends Thread {
        private final String TAGG = MonitorChildProcesses.class.getSimpleName()+": ";

        private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
        private volatile boolean isThreadRunning;           //just a status flag
        private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

        private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
        private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

        private long loopIterationCounter;

        /** Constructor */
        public MonitorChildProcesses() {
            // Initialize values
            this.isStopRequested = false;
            this.isThreadRunning = false;
            this.pauseProcessing = false;
            this.activeProcessingSleepDuration = 1000;
            this.pausedProcessingSleepDuration = 10000;
            this.loopIterationCounter = 1;
        }

        /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
         * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
         * If you directly call .run(), this run() method will invoke on the same thread you call it from. */
        @Override
        public void run() {
            final String TAGG = this.TAGG+"run: ";
            logV(TAGG + "Invoked.");

            // As long as our thread is supposed to be running...
            while (!Thread.currentThread().isInterrupted()) {

                // Our thread has started or is still running
                isThreadRunning = true;

                // Either do nothing (if paused) or allow work to happen (if not paused)...
                logV(TAGG + "-------- Iteration #" + loopIterationCounter + " ------------------------");
                if (pauseProcessing) {
                    doSleepPaused();
                    logD(TAGG + "Processing is paused. Thread continuing to run, but no work is occurring.");
                } else {
                    // Do a short delay to help prevent the thread loop from eating cycles
                    doSleepActive();

                    try {
                        ////////////////////////////////////////////////////////////////////////////////
                        // DO THE BULK OF THE ACTUAL WORK HERE...

                        if (!deliveryRotator.isAlive()) {
                            isThreadAlive_rotator = false;
                            logW(TAGG+"DeliveryRotator is not alive! Restarting it...");
                            restartThread_rotator();
                        } else {
                            isThreadAlive_rotator = true;
                        }

                        if (!deliveryQueueProcessor.isAlive()) {
                            isThreadAlive_queue = false;
                            logW(TAGG+"DeliveryQueueProcessor is not alive! Restarting it...");
                            restartThread_queue();
                        } else {
                            isThreadAlive_queue = true;
                        }

                        // END THE BULK OF THE ACTUAL WORK HERE...
                        ////////////////////////////////////////////////////////////////////////////////
                    } catch (Exception e) {
                        logE(TAGG+"Exception caught: "+e.getMessage());
                    }
                }

                doCounterIncrement();

                // this is the end of the loop-iteration, so check whether we will stop or continue
                if (doCheckWhetherNeedToStop()) {
                    isThreadRunning = false;
                    break;
                }
            }//end while
        }//end run()

        private void doSleepPaused() {
            final String TAGG = this.TAGG+"doSleepPaused: ";

            try {
                Thread.sleep(pausedProcessingSleepDuration);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
            }
        }

        private void doSleepActive() {
            final String TAGG = this.TAGG+"doSleepActive: ";

            try {
                Thread.sleep(activeProcessingSleepDuration);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
            }
        }

        private void doCounterIncrement() {
            final String TAGG = this.TAGG+"doCounterIncrement: ";

            try {
                if (loopIterationCounter + 1 < Long.MAX_VALUE)
                    loopIterationCounter++;
                else
                    loopIterationCounter = 1;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
                loopIterationCounter = 1;
            }
        }

        private boolean doCheckWhetherNeedToStop() {
            final String TAGG = this.TAGG+"doCheckWhetherNeedToStop: ";
            boolean ret = false;

            try {
                if (Thread.currentThread().isInterrupted()) {
                    logI(TAGG + "Thread will now stop.");
                    isThreadRunning = false;
                }
                if (isStopRequested) {
                    logI(TAGG + "Thread has been requested to stop and will now do so.");
                    isThreadRunning = false;
                    ret = true;
                }
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }

            return ret;
        }

        /** Call this to terminate the loop and release resources. */
        public void cleanup() {
            final String TAGG = "cleanup: ";

            try {
                this.isStopRequested = true;

                // Note: At this point, the thread-loop should break on its own
            } catch (Exception e) {
                logE(TAGG+"Exception caught calling stopListening(): "+e.getMessage());
            }
        }
    }

    /** Handler for working with Android-Messages from child processes.
     * This is basically the MAIN bulk of delivery logic (the threads are just timers that trigger this regularly). */
    volatile boolean syncStarted = false;
    private class DeliveryServiceHandler extends Handler {
        final String TAGG = this.getClass().getSimpleName()+": ";

        // Constructor
        public DeliveryServiceHandler() {
        }

        @Override
        public void handleMessage(Message androidMessage) {
            final String TAGGG = "handleMessage: ";
            String tag = "";
            //super.handleMessage(androidMessage);  //TODO: needed??

            // First, check if this service is still running before we do anything with its resources (to avoid null pointer exceptions)
            if (omniApplication == null) {
                logI(TAGG+TAGGG+"Host service has been destroyed, aborting.");
                return;
            }

            // See what our command-request is and handle accordingly
            switch (androidMessage.arg1) {

                // Initialization of rotation lists and loading with initial data if available...
                case MSGHANDLER_ACTION_LIST_LOAD:
                    tag = "MSGHANDLER_ACTION_LIST_LOAD: ";

                    try {
                        // Broadcast our number of current deliverables in existence
                        deliveryStatusInformIntent.setAction(Constants.Intents.Actions.UPDATE_NUMBER_DELIVERING_MSGS);
                        deliveryStatusInformIntent.putExtra(Constants.Intents.ExtrasKeys.MAIN_APP_NUMBER_DELIVERING_MSGS, MainService.omniMessages_deliverable.size());
                        sendBroadcast(deliveryStatusInformIntent);

                        // Recreate new OmniMessages objects just to make sure we're dealing with freshness
                        omniMessageUUIDsToRotate = new ArrayList<>();
                        omniMessageUUIDsToRotate_new = new ArrayList<>();

                        // Populate main delivery list with our deliverable-messages data, as-is
                        // On startup, this will always contain at least persisted messages (if any brand-new ones got in, that's ok, as we just started anyway)
                        //TODO: Does this instead need to be highest priority messages??
                        int sizeOfDeliverablesList = MainService.omniMessages_deliverable.size();
                        if (sizeOfDeliverablesList > 0) {
                            for (OmniMessage omniMessage_deliverable : MainService.omniMessages_deliverable) {
                                logV(TAGG + TAGGG + tag + "Adding \"" + omniMessage_deliverable.getMessageUUID() + "\" from deliverables to queue list...");
                                omniMessageUUIDsToRotate.add(omniMessage_deliverable.getMessageUUID());
                            }
                            int sizeOfQueueList = omniMessageUUIDsToRotate.size();
                            if (sizeOfDeliverablesList != sizeOfQueueList) {
                                logW(TAGG+TAGGG+tag+"Queue size ("+sizeOfQueueList+") is different than deliverable-msg list size ("+sizeOfDeliverablesList+").");
                            }
                        } else {
                            logI(TAGG+TAGGG+tag+"Deliverable-messages list is empty at this time, nothing to load right now.");
                        }
                    } catch (Exception e) {
                        logE(TAGG+TAGGG+tag+"Exception caught updating queue: "+e.getMessage());
                    }

                    break;

                // Synchronization of rotation lists' data with current deliverables data...
                case MSGHANDLER_ACTION_LIST_SYNC:
                    tag = "MSGHANDLER_ACTION_LIST_SYNC: ";
                    final String tagg = tag;

                    // Make sure no other sync operations are running before starting this one
                    if (syncStarted) {
                        logV(TAGG+TAGGG+tag+"Another sync has started, no need to do one now, skipping.");
                        break;
                    }
                    syncStarted = true; //flag that we've started a sync run so we can avoid multiples conflicting

                    ThreadUtils.doStartThread(getBaseContext(),
                            new Thread(new Runnable() {
                                @Override
                                public void run() {

                                try {
                                    logV(TAGG + TAGGG + tagg + "------------------------------------------------------------------------");

                                    // Broadcast our number of current deliverables in existence
                                    deliveryStatusInformIntent.setAction(Constants.Intents.Actions.UPDATE_NUMBER_DELIVERING_MSGS);
                                    deliveryStatusInformIntent.putExtra(Constants.Intents.ExtrasKeys.MAIN_APP_NUMBER_DELIVERING_MSGS, MainService.omniMessages_deliverable.size());
                                    sendBroadcast(deliveryStatusInformIntent);

                                    // Check whether there are messages in the deliverables list, and proceed accordingly...
                                    // If there are none in deliverables list, then there should be none in rotation lists.
                                    if (MainService.omniMessages_deliverable.size() == 0) {
                                        // No deliverables, so just clear the rotation lists and be done for now
                                        logV(TAGG + TAGGG + tagg + "***Deliverables list is empty, so clearing all rotation lists now...");
                                        omniMessageUUIDsToRotate.clear();
                                        omniMessageUUIDsToRotate_new.clear();

                                        // Ensure no devices are still/stuck delivering anything
                                        // (but only if there aren't any ongoing deliveries that we assume/hope are still finishing)
                                        if (omniMessageUuidDelivery_currently == null) {
                                            //FlasherLights.broadcastLightCommand(getApplicationContext(), flasherLightCommandCodes.CMD_LIGHT_STANDBY, Long.MAX_VALUE, null);
                                            FlasherLightService.startLight(getApplicationContext(), flasherLightCommandCodes.CMD_LIGHT_STANDBY);
                                        }

                                        // Reset flag and break out of this case's execution
                                        syncStarted = false;
                                        //break;
                                        Thread.currentThread().interrupt();
                                    }

                                    // The following will execute if there are actually message(s) in the deliverables list...
                                    // (note: deliverable-messages' existence is authoritative over existence in UUID rotation lists)
                                    // We will need to handle things more intelligently, as follows:
                                    //  Removal of message in rotation lists:
                                    //   -If a rotating-msg does not exist in deliverable, then remove it from rotation.
                                    //   -If a rotation_new-msg does not exist in deliverable, then remove it from rotation_New.
                                    //  Addition of messages to rotation lists:
                                    //   -If nothing in rotation but stuff in deliverables, just load the rotation list up directly.
                                    //      (be sure to load only the highest priority message(s) to the rotation list!)
                                    //   -If a deliverable does not exist in rotation, add it to rotation, as follows:
                                    //      If deliverables contain higher priority msg(s), only those get to populate rotation list.
                                    //      If deliverables contain same priority msg(s), then need to check whether anything new (so we inject delivery where needed in rotation, correctly -- see message delivery rules in class header comments)
                                    //          If

                                    // Nothing exists in rotation (even though there are message(s) in deliverables)...
                                    // Need to populate rotation list with deliverables.
                                    if (omniMessageUUIDsToRotate.size() == 0) {
                                        // Simply populate the rotation list with deliverables data, as-is.
                                        logV(TAGG + TAGGG + tagg + "***Rotation list is empty but deliverables is not, so loading current highest-priority deliverables' UUIDs into rotation now...");
                                        for (OmniMessage deliverableOfHighestPriority : MainService.omniMessages_deliverable.getOmniMessagesOfHighestPriority()) {
                                            logV(TAGG + TAGGG + tagg + " Adding " + deliverableOfHighestPriority.getMessageUUID().toString() + " to rotation...");
                                            omniMessageUUIDsToRotate.add(deliverableOfHighestPriority.getMessageUUID());
                                        }

                                        // Reset flag and break out of this case's execution
                                        syncStarted = false;
                                        Thread.currentThread().interrupt();
                                        return;
                                    }

                                    // Stuff exists in rotation (and also in deliverables)...
                                    // Need to sort out what should be where, exactly.
                                    if (omniMessageUUIDsToRotate.size() > 0) {
                                        logV(TAGG + TAGGG + tagg + "***Rotation list AND deliverables contains data, so proceeding to process various situations...");

                                        // Remove non-existent deliverables from rotation
                                        ArrayList<Integer> indicesToRemove = new ArrayList<>();
                                        for (int i = 0; i < omniMessageUUIDsToRotate.size(); i++) {
                                            UUID uuid = omniMessageUUIDsToRotate.get(i);
                                            if (MainService.omniMessages_deliverable.doesOmniMessageExist(uuid)) {
                                                continue;
                                            } else {
                                                logV(TAGG + TAGGG + tagg + "Marking \"" + uuid.toString() + "\" (#" + i + ") for removal from rotation list, as it's no longer in deliverables...");
                                                indicesToRemove.add(i);
                                            }
                                        }
                                        if (indicesToRemove.size() > 0) {
                                            //we need to work backwards, so the index is not shifted (which is what would happen if we started removing at the beginning)
                                            for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
                                                logV(TAGG + TAGGG + tagg + "  Removing #" + String.valueOf(indicesToRemove.get(i)) + " (" + omniMessageUUIDsToRotate.get(indicesToRemove.get(i)).toString() + ") from rotation list...");
                                                omniMessageUUIDsToRotate.remove(indicesToRemove.get(i).intValue());
                                            }
                                        }

                                        // Remove non-existent deliverables from rotation_new
                                        indicesToRemove = new ArrayList<>();
                                        for (int i = 0; i < omniMessageUUIDsToRotate_new.size(); i++) {
                                            UUID uuid = omniMessageUUIDsToRotate_new.get(i);
                                            if (MainService.omniMessages_deliverable.doesOmniMessageExist(uuid)) {
                                                continue;
                                            } else {
                                                logV(TAGG + TAGGG + tagg + "Marking \"" + uuid.toString() + "\" (#" + i + ") for removal from rotation_new list, as it's no longer in deliverables...");
                                                indicesToRemove.add(i);
                                            }
                                        }
                                        if (indicesToRemove.size() > 0) {
                                            //we need to work backwards, so the index is not shifted (which is what would happen if we started removing at the beginning)
                                            for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
                                                logV(TAGG + TAGGG + tagg + "  Removing #" + String.valueOf(indicesToRemove.get(i)) + " (" + omniMessageUUIDsToRotate_new.get(indicesToRemove.get(i)).toString() + ") from rotation_new list...");
                                                omniMessageUUIDsToRotate_new.remove(indicesToRemove.get(i).intValue());
                                            }
                                        }

                                        int highestPriorityInDeliverables = MainService.omniMessages_deliverable.findHighestPriorityValue();
                                        int highestPriorityInRotation = findHighestPriorityValueInRotation();

                                        // If deliverables' highest priority is greater than what's currently in rotation, then we need to rotate that instead of current
                                        if (highestPriorityInDeliverables > highestPriorityInRotation) {
                                            // Clear out current rotation list and repopulate with highest priority message(s) in deliverables
                                            logV(TAGG + TAGGG + tagg + "*Rotation list has " + highestPriorityInRotation + "-priority message(s) but deliverables has " + highestPriorityInDeliverables + "-priority message(s), so loading current highest-priority deliverables' UUIDs into rotation now...");
                                            omniMessageUUIDsToRotate.clear();
                                            omniMessageUUIDsToRotate_new.clear();
                                            for (OmniMessage deliverableOfHighestPriority : MainService.omniMessages_deliverable.getOmniMessagesOfHighestPriority()) {
                                                logV(TAGG + TAGGG + tagg + " Adding " + deliverableOfHighestPriority.getMessageUUID().toString() + " to rotation...");
                                                omniMessageUUIDsToRotate.add(deliverableOfHighestPriority.getMessageUUID());
                                            }

                                            // Reset flag and break out of this case's execution
                                            syncStarted = false;
                                            Thread.currentThread().interrupt();
                                            return;
                                        }

                                        // Ensure we don't load lower priority from delivery in with the higher priority messages already in rotation
                                        if (MainService.omniMessages_deliverable.doesContainMultiplePriorities()) {
                                            //we can't do anything until higher priority message(s) have gone away somehow (closed, expired, etc.)
                                            logV(TAGG + TAGGG + tagg + "*Rotation list already has highest (" + highestPriorityInRotation + ") priority message(s), even though deliverables has other-priority message(s), so just keeping what's in rotation now.");

                                            // Reset flag and break out of this case's execution
                                            syncStarted = false;
                                            Thread.currentThread().interrupt();
                                            return;
                                        }

                                        // If deliverables' highest priority and rotation's priority are the same, then our current rotation's priority is correct, but we need to check for new messages
                                        if (highestPriorityInDeliverables == highestPriorityInRotation) {
                                            // (looping through deliverables, check for their existence in rotation... if not exists, then it's a new one)
                                            logV(TAGG + TAGGG + tagg + "*Rotation list has same priority message(s) as deliverables (" + highestPriorityInDeliverables + "), so checking for any new messages to inject for delivery...");
                                            for (OmniMessage deliverable : MainService.omniMessages_deliverable) {
                                                boolean deliverableFoundInRotation = false;
                                                UUID deliverableUUID = deliverable.getMessageUUID();
                                                logV(TAGG + TAGGG + tagg + " Examining deliverable " + deliverableUUID.toString() + "...");

                                                // First, see if deliverable already exists in rotation list...
                                                // If it does, then do nothing and skip to next deliverable examination
                                                for (UUID rotationUUID : omniMessageUUIDsToRotate) {
                                                    if (rotationUUID.toString().equals(deliverableUUID.toString())) {
                                                        deliverableFoundInRotation = true;
                                                        break;  //found a match, so break out of this loop as there's no need to keep looping
                                                    }
                                                }
                                                if (deliverableFoundInRotation) {
                                                    logV(TAGG + TAGGG + tagg + "   " + deliverableUUID.toString() + " already exists in rotation, skipping to examination of next deliverable.");
                                                    continue;   //skip to examination of next deliverable
                                                }

                                                // If we get here, deliverable was not found in main rotation list, so search the rotation_new list...
                                                // If it exists there, skip to next deliverable examination (otherwise, add it to rotation_new)
                                                deliverableFoundInRotation = false;
                                                for (UUID rotationNewUUID : omniMessageUUIDsToRotate_new) {
                                                    if (rotationNewUUID.toString().equals(deliverableUUID.toString())) {
                                                        deliverableFoundInRotation = true;
                                                        break;  //found a match, so break out of this loop as there's no need to keep looping
                                                    }
                                                }
                                                if (!deliverableFoundInRotation) {
                                                    logV(TAGG + TAGGG + tagg + "   " + deliverableUUID.toString() + " does not exist in any rotation list, so adding it to rotation_new...");
                                                    omniMessageUUIDsToRotate_new.add(deliverableUUID);
                                                }
                                            }
                                            //(at this point, any new message(s) are now added to rotation-new list, and Delivery threads / MSGHANDLER_ACTION_DELIVER_MESSAGE will take it from there)
                                        }
                                    }
                                } catch (Exception e) {
                                    logE(TAGG+TAGGG+tagg+"Exception caught syncing rotation lists with deliverables: "+e.getMessage());
                                }

                                syncStarted = false;    //reset flag now that this sync run is done

                                }
                            }),
                            ThreadUtils.SPAWN_NEW_THREAD_TRUE,
                            ThreadUtils.PRIORITY_MINIMUM);

                    break;

                // Handle kicking off actual delivery of whatever is in the current rotation lists...
                case MSGHANDLER_ACTION_DELIVER_MESSAGE:
                    tag = "MSGHANDLER_ACTION_DELIVER_MESSAGE: ";

                    //if this branch is invoked, then we need to deliver a message...
                    //so try to figure out which one to deliver!

                    int sizeOfRotation = omniMessageUUIDsToRotate.size();
                    int sizeOfRotationNew = omniMessageUUIDsToRotate_new.size();

                    logV(TAGG+TAGGG+tag+"Rotation size: "+sizeOfRotation+" / Rotation_new size: "+sizeOfRotationNew);



                    // Figure out new vs. existing messages (give new messages preference)          // TODO: Need to accommodate msg-priority value and how it will fit into this scheme
                    if (sizeOfRotationNew > 0) {
                        logV(TAGG+TAGGG+tag+"There are new message(s), so need to deliver one...");
                        //messages delivered from new list should get immediately moved to end of main list
                        //so we can just deliver the first message in the new list
                        deliverNewMessage();
                    } else if (sizeOfRotation > 0) {
                        logV(TAGG+TAGGG+tag+"No new message(s), but there are existing message(s) to deliver...");
                        deliverExistingMessage();
                    } else {
                        logV(TAGG+TAGGG+tag+"No message(s) for delivery.");
                    }

                    break;

                // Unhandled case...
                default:
                    logW(TAGG+TAGGG+"Unhandled case ("+String.valueOf(androidMessage.arg1)+"). Aborting.");
                    return;
            }
        }
    }

    /** Broadcast receiver to listen for and service delivery-action commands. */
    private class DeliveryServiceDeliveryActionReceiver extends BroadcastReceiver {
        final String TAGG = this.getClass().getSimpleName()+": ";

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGGG = "onReceive: ";

            String broadcastAction;

            try {
                broadcastAction = intent.getExtras().getString(BROADCAST_INTENTKEY_ACTION);
            } catch (Exception e) {
                logE(TAGG+TAGGG+"Exception caught (aborting): "+e.getMessage());
                return;
            }

            if (broadcastAction == null) {
                logW(TAGG+TAGGG+"Broadcast action from intent is null. Don't know what to do, aborting.");
            } else if (broadcastAction.equals(BROADCAST_INTENTVALUE_ACTION_ACTIVITY_LAUNCH)) {
                try {
                    String activityNameToLaunch = intent.getStringExtra(BROADCAST_INTENTKEY_ACTIVITY_NAME);
                    logI(TAGG+TAGGG+"Broadcast received to launch activity (\"" + activityNameToLaunch + "\").");
                    launchActivity(activityNameToLaunch);
                } catch (Exception e) {
                    logE(TAGG+TAGGG+"Exception caught (aborting): "+e.getMessage());
                }
            } else if (broadcastAction.equals(BROADCAST_INTENTVALUE_ACTION_ACTIVITY_FINISH)) {
                try {
                    String activityNameToFinish = intent.getStringExtra(BROADCAST_INTENTKEY_ACTIVITY_NAME);
                    logI(TAGG+TAGGG+"Broadcast received to finish activity (\""+activityNameToFinish+"\").");
                    finishActivity(activityNameToFinish);
                } catch (Exception e) {
                    logE(TAGG+TAGGG+"Exception caught (aborting): "+e.getMessage());
                }
            } else if (broadcastAction.equals(BROADCAST_INTENTVALUE_ACTION_ACTIVITY_FINISHALL)) {
                try {
                    logI(TAGG+TAGGG+"Broadcast received to finish all delivery activities.");
                    finishAllDeliveryActivities();
                } catch (Exception e) {
                    logE(TAGG+TAGGG+"Exception caught (aborting): "+e.getMessage());
                }
            } else {
                logW(TAGG+TAGGG+"Unhandled broadcast action. Don't know what to do, aborting.");
            }
        }
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
