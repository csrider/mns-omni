package com.messagenetsystems.evolution2.models;

/* OmniMessages
 * This is a container for a list of OmniMessage objects.
 *
 * Revisions:
 *  2019.12.18-19   Chris Rider     Created (used OmniMessages as a template)
 *  2020.01.16      Chris Rider     Added ability to get OmniMessage and return as a cloned object (instead of by reference).
 *  2020.01.20      Chris Rider     Added synchronized to possibly competing methods among threads.
 *  2020.01.23      Chris Rider     Added overload method to doesOmniMessageExist to accept UUID, also fixed potential UUID string comparison bug.
 *  2020.01.28      Chris Rider     Added verbose logging to help debug existing message check.
 *  2020.01.29-31   Chris Rider     Added support to auto-update OmniRawMessages list whenever changes are made (only updates, not additions or removals).
 *  2020.02.04      Chris Rider     Added methods to find highest priority value and to return a list of the highest priority OmniMessage items in this list.
 *  2020.04.16      Chris Rider     Added method, removeOmniMessage_byBannerRecnoZX, to remove an OmniMessage by legacy MNS Banner ZX-recno value contained in BannerMessage.
 *  2020.04.20      Chris Rider     Added methods, findLowestPriorityValue() and doesContainMultiplePriorities().
 *  2020.05.08      Chris Rider     Improved logging for updateOmniMessage method and made it easier to understand and debug.
 */

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;
import com.messagenetsystems.evolution2.services.MainService;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.UUID;


public class OmniMessages extends ArrayList<OmniMessage> /*implements BroadcastReceiver*/ {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final boolean ADD_AVOIDING_DUPLICATES = true;
    public static final boolean ADD_IGNORING_DUPLICATES = false;
    public static final String ASCENDING = "ascending";     //just an arbitrary-value constant so we can make code and logs easy to read
    public static final String DESCENDING = "descending";   //just an arbitrary-value constant so we can make code and logs easy to read
    public static final boolean SYNC_DB_TRUE = true;
    public static final boolean SYNC_DB_FALSE = false;
    public static final int GET_OMNIMESSAGE_AS_REFERENCE = 1;
    public static final int GET_OMNIMESSAGE_AS_COPY = 2;

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;
    private OmniRawMessages omniRawMessagesToUpdate;


    /** Constructor
     * @param appContext                Application context.
     * @param logMethod                 Logging method to use.
     * @param omniRawMessagesToUpdate   OmniRawMessages list to automatically update whenever this list changes.
     */
    public OmniMessages(Context appContext, int logMethod, @Nullable OmniRawMessages omniRawMessagesToUpdate) {
        Log.v(TAG, "Instantiating.");

        this.appContextRef = new WeakReference<Context>(appContext);
        this.logMethod = logMethod;
        this.omniRawMessagesToUpdate = omniRawMessagesToUpdate;
    }
    public OmniMessages(Context appContext, int logMethod) {
        Log.v(TAG, "Instantiating (no OmniRawMessages specified, so using MainService.omniRawMessages).");

        this.appContextRef = new WeakReference<Context>(appContext);
        this.logMethod = logMethod;
        this.omniRawMessagesToUpdate = MainService.omniRawMessages;
    }


    /*============================================================================================*/
    /* Class methods */

    /** Add the provided OmniMessage object to the collection.
     * @param omniMessage The OmniMessage object to add.
     * @return Whether the object was added.
     */
    public boolean addOmniMessage(@NonNull OmniMessage omniMessage, @NonNull boolean avoidDuplicates) {
        String TAGG = "addOmniMessage: ";
        try {
            TAGG = "addOmniMessage(" + omniMessage.getMessageUUID().toString() + "): ";
        } catch (Exception e) {
            logW(TAGG+"Provided OmniMessage does not have a UUID.");
        }
        boolean ret;

        // Handle possibly adding the provided OmniMessage object to this list
        if (avoidDuplicates && doesOmniMessageExist(omniMessage)) {
            logD(TAGG+"Duplicate detected (and we were told avoid duplicates). Not adding.");
            ret = false;
        }
        else {
            try {
                ret = this.add(omniMessage);
            } catch (Exception e) {
                //the Collection interface throws an exception if something went wrong
                logW(TAGG + "Provided OmniMessage could not be added.");
                ret = false;
            }
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+" (at this time, there are "+String.valueOf(this.size())+" OmniMessage objects in the list).");
        return ret;
    }

    /** Check if provided OmniMessage object already exists in the dataset.
     * @param omniMessage The OmniMessage object to check for.
     * @return Whether the specified OmniMessage exists.
     */
    public boolean doesOmniMessageExist(@NonNull OmniMessage omniMessage) {
        String TAGG = "doesOmniMessageExist: ";
        try {
            TAGG = "doesOmniMessageExist(" + omniMessage.getMessageUUID().toString() + "): ";
        } catch (Exception e) {
            logW(TAGG+"Provided OmniMessage does not have a UUID.");
        }
        boolean ret = false;

        try {
            logV(TAGG+"This OmniMessages instance contains "+this.size()+" OmniMessage objects. Will now search for match.");
            for (OmniMessage om : this) {
                if (om.getMessageUUID().toString().equals(omniMessage.getMessageUUID().toString())) {
                    ret = true;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }
    public boolean doesOmniMessageExist(@NonNull UUID uuid) {
        final String TAGG = "doesOmniMessageExist("+uuid.toString()+"): ";
        boolean ret = false;

        try {
            logV(TAGG+"This OmniMessages instance contains "+this.size()+" OmniMessage objects. Will now search for match.");
            for (OmniMessage om : this) {
                if (om.getMessageUUID().toString().equals(uuid.toString())) {
                    ret = true;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Retrieves the OmniMessage object corresponding to the specified UUID.
     * @param uuid The UUID of the OmniMessage you want to get.
     * @param getAsReferenceOrClone The constant to determine whether we return a refernce or a clone of the OmniMessage.
     * @return The OmniMessage object found with the specified UUID, or null if not found.
     */
    public synchronized OmniMessage getOmniMessage(@NonNull UUID uuid, int getAsReferenceOrClone) {
        final String TAGG = "getOmniMessage: ";
        OmniMessage ret = null;

        try {
            for (OmniMessage om : this) {
                if (om.getMessageUUID().equals(uuid)) {
                    ret = om;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret != null) {
            logV(TAGG + "Returning OmniMessage object for " + uuid.toString() + ".");

            // By default, Java will return reference, so only special logic for explicit clone
            if (getAsReferenceOrClone == GET_OMNIMESSAGE_AS_COPY) {
                ret = new OmniMessage(ret);     //use the OmniMessage copy-constructor
            }
        } else {
            logV(TAGG + "Returning null OmniMessage object for " + uuid.toString() + ".");
        }

        return ret;
    }

    /** Find and return the highest priority value in this list.
     * @return Highest priority value found in this list.
     */
    public int findHighestPriorityValue() {
        final String TAGG = "findHighestPriorityValue";
        int ret = -1;

        try {
            int highestPriorityFound = 0;
            for (OmniMessage om : this) {
                if (om.getMsgPriority() > highestPriorityFound) {
                    highestPriorityFound = om.getMsgPriority();
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

    /** Find and return the lowest priority value in this list.
     * @return Lowest priority value found in this list, or -1 if none found.
     */
    public int findLowestPriorityValue() {
        final String TAGG = "findLowestPriorityValue: ";
        int ret = -1;

        try {
            if (this.size() > 0) {
                int lowestPriorityFound = Integer.MAX_VALUE;
                for (OmniMessage om : this) {
                    if (om.getMsgPriority() < lowestPriorityFound) {
                        lowestPriorityFound = om.getMsgPriority();
                    }
                }
                ret = lowestPriorityFound;
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret < 0) {
            logI(TAGG + "No lowest priority found, list is probably empty.");
        }

        logV(TAGG + "Returning "+String.valueOf(ret));
        return ret;
    }

    /** Create and return an OmniMessages list of the highest priority OmniMessage items.
     * @return OmniMessages list of the highest priority OmniMessage items.
     */
    public synchronized OmniMessages getOmniMessagesOfHighestPriority() {
        final String TAGG = "getOmniMessagesOfHighestPriority: ";
        OmniMessages ret = new OmniMessages(appContextRef.get(), logMethod, null);

        try {
            int highestPriority = findHighestPriorityValue();
            for (OmniMessage omniMessage : this) {
                if (omniMessage.getMsgPriority() == highestPriority) {
                    ret.addOmniMessage(omniMessage, ADD_AVOIDING_DUPLICATES);
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        if (ret.size() > 0) {
            logV(TAGG + "Returning OmniMessages ArrayList object of size " + ret.size() + ".");
        } else {
            logV(TAGG + "Returning empty OmniMessages ArrayList object.");
        }

        return ret;
    }

    /** Retrieves the OmniMessage object's position in the list.
     * @param uuid The UUID of the OmniMessage you want to find.
     * @return The position in the list that it was found, or -1 if not found.
     */
    public synchronized int getOmniMessageListPosition(@NonNull UUID uuid) {
        final String TAGG = "getOmniMessageListPosition: ";
        int ret = -1;

        try {
            for (int i = 0; i < this.size(); i++) {
                if (this.get(i).getMessageUUID().equals(uuid)) {
                    ret = i;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning int "+String.valueOf(ret)+".");
        return ret;
    }

    /** Retrieves the OmniMessage object's position in the list.
     * @param omniMessage The OmniMessage you want to find.
     * @return The position in the list that it was found, or -1 if not found.
     */
    public synchronized int getOmniMessageListPosition(@NonNull OmniMessage omniMessage) {
        final String TAGG = "getOmniMessageListPosition: ";
        int ret = -1;

        try {
            for (int i = 0; i < this.size(); i++) {
                if (this.get(i).getMessageUUID().equals(omniMessage.getMessageUUID())) {
                    ret = i;
                    break;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG+"Returning int "+String.valueOf(ret)+".");
        return ret;
    }

    /** Remove the specified OmniMessage object from the collection.
     * @param omniMessage The OmniMessage object to find and remove.
     * @return Whether the object was removed.
     */
    public synchronized boolean removeOmniMessage(@NonNull OmniMessage omniMessage) {
        final String TAGG = "removeOmniMessage: ";
        boolean ret;

        try {
            ret = this.remove(omniMessage);
        } catch (Exception e) {
            //the Collection interface throws an exception if something went wrong
            logW(TAGG+"Specified OmniMessage could not be removed.");
            ret = false;
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Remove the specified OmniMessage object (by MNS ZX-recno) from the collection.
     * @param bannerRecnoZX The MNS Banner ZX-recno to find and remove its OmniMessage.
     * @return Whether the specified record's OmniMessage object was removed.
     */
    public synchronized boolean removeOmniMessage_byBannerRecnoZX(int bannerRecnoZX) {
        final String TAGG = "removeOmniMessage_byBannerRecnoZX: ";
        boolean ret;
        OmniMessage omniMessage = null;

        try {
            for (OmniMessage om : this) {
                if (om.getBannerMessage() != null
                        && om.getBannerMessage().recno == bannerRecnoZX) {
                    omniMessage = om;
                    break;
                }
            }

            ret = this.remove(omniMessage);
        } catch (Exception e) {
            //the Collection interface throws an exception if something went wrong
            logW(TAGG+"Specified OmniMessage could not be removed.");
            ret = false;
        }

        // Handle possibly removing the corresponding message in the OmniRawMessages list as well
        if (ret && this.omniRawMessagesToUpdate != null) {
            // First, get a copy of the corresponding OmniRawMessage (by UUID)
            // We have to modify a local copy (i.e. different instance), so ORM updateOmniRawMessage check will not equate, and will thus flush changes.
            OmniRawMessage omniRawMessage = new OmniRawMessage(this.omniRawMessagesToUpdate.getOmniRawMessage(omniMessage.getMessageUUID()));

            // Only remove raw if there is a corresponding object there
            if (omniRawMessage != null) {
                // Invoke the update method to kick off the actual removal
                if (omniRawMessagesToUpdate.removeOmniRawMessage(omniRawMessage)) {
                    logV(TAGG + "Corresponding OmniRawMessage removed.");
                } else {
                    logW(TAGG + "Corresponding OmniRawMessage NOT removed.");
                }
            }
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Remove the specified OmniMessage object from the collection.
     * @param uuid The OmniMessage object to find and remove.
     * @return Whether the object was removed.
     */
    public synchronized boolean removeOmniMessage(@NonNull UUID uuid) {
        final String TAGG = "removeOmniMessage: ";
        boolean ret;

        try {
            OmniMessage omniMessage = getOmniMessage(uuid, GET_OMNIMESSAGE_AS_REFERENCE);
            ret = removeOmniMessage(omniMessage);
        } catch (Exception e) {
            //the Collection interface throws an exception if something went wrong
            logW(TAGG+"Specified OmniMessage could not be removed.");
            ret = false;
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Find and update an existing OmniMessage with the provided one.
     * Uses the UUID value to match (which should never change).
     * @param omniMessage The OmniMessage object to update into the list.
     * @return Boolean of whether any change occurred.
     */
    public synchronized boolean updateOmniMessage(@NonNull OmniMessage omniMessage) {
        final String TAGG = "updateOmniMessage: ";
        boolean ret;

        // Find the corresponding message, test equality to that provided, and update the OmniMessage object if needed
        try {
            // Find the position of the matching existing object
            int positionOfExistingMatch = getOmniMessageListPosition(omniMessage);
            if (positionOfExistingMatch < 0 || positionOfExistingMatch >= this.size()) {
                logW(TAGG+"OmniMessage: Invalid matching position. Cannot continue.");
                return false;
            } else {
                logV(TAGG+"OmniMessage: Matching OmniMessage at position "+String.valueOf(positionOfExistingMatch));
            }

            // Check if the object is equal to its matching object already (no need to update if it is)
            if (this.get(positionOfExistingMatch).equals(omniMessage)) {
                logD(TAGG+"OmniMessage: Existing match is the same as provided. No update necessary.");
                return false;
            } else {
                logV(TAGG+"OmniMessage: Existing match is different than OmniMessage provided. Updating...");
            }

            // Update the item at the found position (returns previous object that was replaced)
            OmniMessage omniMesssage_orig = this.set(positionOfExistingMatch, omniMessage);

            // Check if the change was successful or not
            if (omniMesssage_orig.equals(omniMessage)) {
                logW(TAGG+"OmniMessage: Specified OmniMessage did not result in a change!");
                ret = false;
            } else {
                logD(TAGG+"OmniMessage: Specified OmniMessage has been updated.");
                ret = true;
            }
        } catch (Exception e) {
            //the Collection interface throws an exception if something went wrong
            logW(TAGG+"OmniMessage: Specified OmniMessage could not be updated.");
            ret = false;
        }

        // Handle possibly updating the corresponding message in the OmniRawMessages list as well
        if (ret && this.omniRawMessagesToUpdate != null) {
            // First, get a copy of the corresponding OmniRawMessage (by UUID)
            // We have to modify a local copy (i.e. different instance), so ORM updateOmniRawMessage check will not equate, and will thus flush changes.
            OmniRawMessage omniRawMessage = new OmniRawMessage(this.omniRawMessagesToUpdate.getOmniRawMessage(omniMessage.getMessageUUID()));

            // Only update raw if there is a corresponding object there
            if (omniRawMessage != null) {
                // Export JSON from the OmniMessage
                //JSONObject messageJSON_omniMessage = omniMessage.exportMsgToJSONObject(omniMessage.getEcosystem());   //TODO (finish OmniMessage export routine)
                JSONObject metadataJSON_omniMessage = omniMessage.exportMetaToJSONObject();

                if (/*messageJSON_omniMessage == null || */metadataJSON_omniMessage == null) {
                    logW(TAGG+"OmniRawMessage: Exported JSON is null, this is probably not right, unless you are explicitly setting null!");
                //} else {
                    //logV(TAGG+"OmniRawMessage's exported JSON...\n" +
                    //        /*"Message-JSON:  \""+messageJSON_omniMessage.toString()+"\"\n"+*/
                    //        "Metadata-JSON: \""+metadataJSON_omniMessage.toString()+"\"");
                }

                // Save our exported JSON to the OmniRawMessage object
                // (DEV-NOTE: If we modify a direct reference to the main ORMs, this will update in-place and update method below will not execute (and nothing will get saved to disk)
                //omniRawMessage.setMessageJSONObject(messageJSON_omniMessage);
                omniRawMessage.setMetadataJSONObject(metadataJSON_omniMessage);

                // Invoke the update method to kick off the actual update
                if (omniRawMessagesToUpdate.updateOmniRawMessage(omniRawMessage)) {
                    logV(TAGG + "OmniRawMessage: Corresponding OmniRawMessage updated.");
                } else {
                    logW(TAGG + "OmniRawMessage: Corresponding OmniRawMessage NOT updated.");
                }
            }
        }

        logV(TAGG+"Returning "+Boolean.toString(ret)+".");
        return ret;
    }

    /** Determine whether there are multiple different priorities in this list of messages.
     * Basically just looks at highest and lowest priorities, and if there is a difference, then we can assume there are multiple different priority messages.
     * @return True if there are different priority messages, or false if not (including no messages).
     */
    public boolean doesContainMultiplePriorities() {
        final String TAGG = "doesContainMultiplePriorities: ";
        boolean ret = false;

        try {
            // Check for size here, so as to avoid any potential weirdness around find...() methods returning -1
            if (this.size() > 0) {
                int highestPriorityFound = findHighestPriorityValue();
                int lowestPriorityFound = findLowestPriorityValue();

                if (lowestPriorityFound != highestPriorityFound) {
                    ret = true;
                }
            }
        } catch (Exception e) {
            logW(TAGG+"Exception caught: "+e.getMessage());
        }

        logV(TAGG + "Returning "+String.valueOf(ret));
        return ret;
    }


    /*============================================================================================*/
    /* Class housekeeping methods */

    /** Call this to cleanup everything. */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        try {
            this.clear();
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
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
