package com.messagenetsystems.evolution2.models;

/* ProcessStatusList
 * A model class for a list of ProcessStatus objects.
 * Initially created for simplifying monitoring health of processes.
 *
 * How to use this feature...
 * Basically, all of this should be done within the process' class. How convenient!
 *  1. When you instantiate a thread/service/etc (in its onCreate method), register it with this list:
 *     This method creates a ProcessStatus object for your process, and populates it with thread type, process class name, and the process' parent's class name as well.
 *      Ex. omniApplication.processStatusList.addAndRegisterProcess(ProcessStatus.PROCESS_TYPE_THREAD, processClass);
 *  2. Once the thread/service/etc actually starts (onStart, run(), etc.), record the start with the list:
 *     This method will record start of your process, as well as update the record with the thread ID.
 *      Ex. omniApplication.processStatusList.recordProcessStart(this.getClass(), android.os.Process.myTid());
 *  3. As the thread runs (anything that runs), optionally record run intervals / heartbeats:
 *      Ex. omniApplication.processStatusList.recordProcessHeartbeat(this.getClass());
 *
 * Revisions:
 *  2020.07.24-27   Chris Rider     Created.
 */

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.utilities.SystemUtils;

import java.util.ArrayList;


public class ProcessStatusList extends ArrayList<ProcessStatus> {
    private final String TAG = ProcessStatusList.class.getSimpleName();

    /** Constructor */
    public ProcessStatusList() {

    }

    /** Get ProcessStatus object matching process class provided.
     * Note: Overload methods also provided to allow multiple search criteria capability.
     * @return ProcessStatus object matching class name, or null if not found */
    public ProcessStatus getProcessStatusObj(String processClassName) {
        final String TAGG = "getProcessStatusObj("+processClassName+"): ";
        ProcessStatus ret = null;

        for (ProcessStatus processStatus : this) {
            if (processStatus.getProcessClassName().equals(processClassName)) {
                FL.v(TAG, TAGG+"Found! ID#: "+processStatus.getProcessThreadID()+", Parent: \""+processStatus.getProcessParentClassName()+"\"");

                ret = processStatus;
                break;
            }
        }

        FL.v(TAG, TAGG+"Returning: "+String.valueOf(ret));
        return ret;
    }
    public ProcessStatus getProcessStatusObj(Class processClass) {
        return getProcessStatusObj(processClass.getSimpleName());
    }
    public ProcessStatus getProcessStatusObj(ProcessStatus processStatusObj) {
        return getProcessStatusObj(processStatusObj.getProcessClassName());
    }

    /** Add a new process to the list.
     * @return Whether process was registered or not */
    public boolean addAndRegisterProcess(ProcessStatus processStatusObj) {
        final String TAGG = "registerProcess("+processStatusObj.getProcessClassName()+"): ";

        // Check if process exists in the list already
        if (getProcessStatusObj(processStatusObj) != null) {
            FL.w(TAG, TAGG+"Process already exists in list. Aborting.");
            return false;
        }

        // Record registered date and add to list
        processStatusObj.recordProcessRegistered();
        this.add(processStatusObj);

        return true;
    }
    public boolean addAndRegisterProcess(byte processType, Class processClass) {
        ProcessStatus processStatusObj = new ProcessStatus(processType, processClass);

        // Set the process class object member
        processStatusObj.setProcessClass(processClass);

        // Set parent's class name member (derived from stacktrace)
        processStatusObj.setProcessParentClassName(SystemUtils.getParentClassNameFromStack(processClass));

        return addAndRegisterProcess(processStatusObj);
    }
    /*
    public boolean addAndRegisterProcess(byte processType, Class processClass, String parentClassName) {
        ProcessStatus processStatusObj = new ProcessStatus(processType, processClass);
        processStatusObj.setProcessParentClassName(parentClassName);
        return addAndRegisterProcess(processStatusObj);
    }
    */

    /** Set maximum heartbeat interval parameter for the specified process. */
    public void setMaxHeartbeatIntervalForProcess(ProcessStatus processStatusObj, long intervalMS) {
        getProcessStatusObj(processStatusObj).setProcessMaxHeartbeatIntervalMS(intervalMS);
    }
    public void setMaxHeartbeatIntervalForProcess(Class processClass, long intervalMS) {
        getProcessStatusObj(processClass).setProcessMaxHeartbeatIntervalMS(intervalMS);
    }

    /** Set process' parent class name. */
    public void setParentClassName(Class processClass, String parentClassName) {
        getProcessStatusObj(processClass).setProcessParentClassName(parentClassName);
    }
    public void setParentClassName(Class processClass, Class parentClass) {
        getProcessStatusObj(processClass).setProcessParentClassName(parentClass.getSimpleName());
    }

    /** Set number of child processes for the specified process. */
    public void setNumberOfExpectedChildrenForProcess(ProcessStatus processStatusObj, int numberOfChildren) {
        getProcessStatusObj(processStatusObj).setProcessNumberOfExpectedChildren(numberOfChildren);
    }
    public void setNumberOfExpectedChildrenForProcess(Class processClass, int numberOfChildren) {
        getProcessStatusObj(processClass).setProcessNumberOfExpectedChildren(numberOfChildren);
    }

    /** Record a process start.
     * @param processClass Class object of the process to record start for
     * @param processThreadID This process' thread ID
     */
    public void recordProcessStart(Class processClass, int processThreadID) {
        final String TAGG = "recordProcessStart("+processClass.getSimpleName()+")";

        // Get matching process in list
        ProcessStatus processStatusObj = getProcessStatusObj(processClass);

        if (processStatusObj == null) {
            FL.e(TAG, TAGG+"Process not found. Aborting.");
            return;
        }

        if (processStatusObj.getProcessRegisteredDate() == null) {
            FL.w(TAG, TAGG+"Process was never registered. Must register first in order to record data about it. Aborting.");
            return;
        }

        processStatusObj.setProcessThreadID(processThreadID);
        processStatusObj.recordProcessStart();
    }
    public void recordProcessStart(Class processClass) {
        recordProcessStart(processClass, 0);
    }

    /** Record a process stop. */
    public void recordProcessStop(Class processClass) {
        final String TAGG = "recordProcessStop("+processClass.getSimpleName()+")";

        // Get matching process in list
        ProcessStatus processStatusObj = getProcessStatusObj(processClass);

        if (processStatusObj == null) {
            FL.e(TAG, TAGG+"Process not found. Aborting.");
            return;
        }

        if (processStatusObj.getProcessStartedDate() == null) {
            FL.w(TAG, TAGG+"Process was never started. Must start it first in order to record data about stopping it. Aborting.");
            return;
        }

        processStatusObj.recordProcessStop();
    }

    /** Record a process heartbeat. */
    public void recordProcessHeartbeat(Class processClass) {
        final String TAGG = "recordProcessHeartbeat("+processClass.getSimpleName()+")";

        // Get matching process in list
        ProcessStatus processStatusObj = getProcessStatusObj(processClass);

        if (processStatusObj == null) {
            FL.e(TAG, TAGG+"Process not found. Aborting.");
            return;
        }

        if (processStatusObj.getProcessRegisteredDate() == null) {
            FL.w(TAG, TAGG+"Process was never registered. Must register first in order to record data about it. Aborting.");
            return;
        }

        processStatusObj.recordProcessHeartbeat();
    }
}
