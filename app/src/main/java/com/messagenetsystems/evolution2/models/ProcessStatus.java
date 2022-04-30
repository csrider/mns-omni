package com.messagenetsystems.evolution2.models;

/* ProcessStatus
 * A model class for storing status about a process (Service, thread, etc.).
 * Initially created for simplifying monitoring health of processes.
 *
 * For normal usage, you should use this in conjunction with ProcessStatusList, and use its
 * registerProcess() method to pass an instance of this into it for inclusion in the list.
 *
 * How to use (independently of ProcessStatusList.registerProcess):
 *  1. Instantiate a ProcessStatus object...
 *      ProcessStatus processStatus_healthService = new ProcessStatus(HealthService);
 *  2. Configure the ProcessStatus object...
 *      processStatus_healthService.setMaxHeartbeatIntervalMS(5000);
 *  2. Track start of the process...
 *      processStatus.registerStart();
 *  3. Track update of the process...
 *      processStatus.registerHeartbeat();
 *
 * Revisions:
 *  2020.07.24-27   Chris Rider     Created.
 *  2020.07.29      Chris Rider     Added maximum runtime before restart desired & required, as well as action-needed flag.
 *                                  Refactored isNominal to getNominalStatus so we can return descriptive codes.
 */

import com.bosphere.filelogger.FL;

import java.util.Date;


public class ProcessStatus {
    private String TAG = ProcessStatus.class.getSimpleName();

    public static final byte PROCESS_TYPE_UNKNOWN = 0;
    public static final byte PROCESS_TYPE_SERVICE = 1;
    public static final byte PROCESS_TYPE_THREAD = 2;
    public static final byte PROCESS_TYPE_RECEIVER = 3;

    public static final byte NOMINAL_TRUE = 1;
    public static final byte NOMINAL_UNKNOWN = 0;
    public static final byte NOMINAL_FALSE = -1;
    public static final byte NOMINAL_FALSE_FAULTY_HEARTBEAT = -2;
    public static final byte NOMINAL_FALSE_MAX_DESIRED_RUNTIME = -3;
    public static final byte NOMINAL_FALSE_MAX_REQUIRED_RUNTIME = -4;

    public static final byte ACTION_NONE = 0;
    public static final byte ACTION_RESTART = 1;

    private byte processType = PROCESS_TYPE_UNKNOWN;
    private int processThreadID = 0;
    private Class processClass = null;
    private String processClassName;
    private Date processRegisteredDate = null;
    private String processParentClassName = null;
    private long processMaxHeartbeatIntervalMS = Long.MAX_VALUE;
    private Date processStartedDate = null;
    private Date processStoppedDate = null;
    private Date processPreviousHeartbeatDate = null;
    private Date processMostRecentHeartbeatDate = null;
    private int processNumberOfExpectedChildren = 0;
    private long processMaxRuntimeMsBeforeRestartDesired = Long.MAX_VALUE;
    private long processMaxRuntimeMsBeforeRestartRequired = Long.MAX_VALUE;
    private byte processActionNeededFlag = ACTION_NONE;

    /** Constructor */
    public ProcessStatus(byte processType, Class processClass) {
        setProcessType(processType);
        setProcessClassName(processClass.getSimpleName());

        this.TAG = TAG + "{"+getProcessClassName()+"}";

        FL.v(TAG, "Instance created for \""+getProcessClassName()+"\"");
    }


    /*============================================================================================*/
    /* Data Calculation & Analysis Methods */

    /** Determine if the process is considered problem-free.
     *  DEV-NOTE: Only one code can be returned, even if multiple are in effect...
     *      So be careful to cascade your logic flow properly to return your desired severity.
     *      Only the most severe code should be returned!
     *      Typically in If/Else or Switch/Case patterns, for instance, most severe should come FIRST and you should return value ASAP! */
    public byte getNominalStatus() {
        final String TAGG = "getNominalStatus: ";
        byte ret = NOMINAL_UNKNOWN;

        if (getProcessStartedDate() == null) {
            // We don't need to investigate a process that has not yet started
            FL.i(TAG, TAGG+"Process \""+getProcessClassName()+"\" has not yet started. Nothing to investigate.");
        } else if (getProcessStoppedDate() != null) {
            // We don't need to investigate stopped processes
            FL.i(TAG, TAGG+"Process \""+getProcessClassName()+"\" has been stopped. Nothing to investigate.");
        } else {
            switch (getProcessType()) {
                case PROCESS_TYPE_RECEIVER:
                    // receivers don't have heartbeats, but they should be registered   //TODO
                    break;
                case PROCESS_TYPE_SERVICE:
                    // Note: Services typically have threads that run under them, but may also have receivers, etc. //TODO
                    break;
                case PROCESS_TYPE_THREAD:

                    // Investigate problematic heartbeat times, but only if configured interval for the process is valid
                    // Reminder: Threads typically have loops that should record a heartbeat on each iteration, so that is the value we're checking with.
                    // NOTE: Heartbeat delays or failures usually indicate some kind of processing problem.
                    if (getProcessMaxHeartbeatIntervalMS() == 0 || getProcessMaxHeartbeatIntervalMS() == Long.MAX_VALUE) {
                        FL.v(TAG, TAGG+"Process \""+getProcessClassName()+"\" has no valid max interval specified.");
                    } else {
                        long msSinceMostRecentHeartbeat = new Date().getTime() - getProcessMostRecentHeartbeatDate().getTime();
                        if (msSinceMostRecentHeartbeat > getProcessMaxHeartbeatIntervalMS() + 100) {    //we add some ms to account for processing time so we don't get false positives
                            FL.i(TAG, TAGG + "Process \"" + getProcessClassName() + "\" has not recorded a heartbeat in over " + msSinceMostRecentHeartbeat + "ms");
                            ret = NOMINAL_FALSE_FAULTY_HEARTBEAT;
                            break;  //force return of this value, as it's more severe than anything below
                        } else if (getTimeBetweenHeartbeats_milliseconds() > getProcessMaxHeartbeatIntervalMS() + 100) {    //we add some ms to account for processing time so we don't get false positives
                            FL.i(TAG, TAGG + "Process \"" + getProcessClassName() + "\" has too long heartbeat interval (" + getTimeBetweenHeartbeats_milliseconds() + "ms but should be less than " + getProcessMaxHeartbeatIntervalMS() + "ms)");
                            ret = NOMINAL_FALSE_FAULTY_HEARTBEAT;
                            break;  //force return of this value, as it's more severe than anything below
                        }
                    }

                    // Investigate whether this process has been running longer than it should between restarts, but only if configured max-runtime is valid
                    // NOTE: This is considered less-severe than heartbeat issues (above), as this is somewhat planned or built-in when process is created.
                    if (getProcessMaxRuntimeMsBeforeRestartRequired() == 0 || getProcessMaxRuntimeMsBeforeRestartRequired() == Long.MAX_VALUE) {
                        FL.v(TAG, TAGG+"Process \""+getProcessClassName()+"\" has no valid max required runtime specified.");
                    } else {
                        long msSinceStart = new Date().getTime() - getProcessStartedDate().getTime();
                        if (msSinceStart > getProcessMaxRuntimeMsBeforeRestartRequired() + 100) {    //we add some ms to account for processing time so we don't get false positives
                            FL.i(TAG, TAGG + "Process \"" + getProcessClassName() + "\" has been running for over " + Long.toString(msSinceStart) + "ms (required max runtime is "+Long.toString(getProcessMaxRuntimeMsBeforeRestartRequired())+"ms).");
                            ret = NOMINAL_FALSE_MAX_REQUIRED_RUNTIME;
                            break;  //force return of this value, as it's more severe than anything below
                        }
                    }

                    // Investigate whether this process has been running longer than desired between restarts, but only if configured max-desired-runtime is valid
                    // NOTE: This is considered less-severe than heartbeat issues (above), as well as less-severe than hard-max runtime (above), as this is just "desired" maximum runtime.
                    if (getProcessMaxRuntimeMsBeforeRestartDesired() == 0 || getProcessMaxRuntimeMsBeforeRestartDesired() == Long.MAX_VALUE) {
                        FL.v(TAG, TAGG+"Process \""+getProcessClassName()+"\" has no valid max desired runtime specified.");
                    } else {
                        long msSinceStart = new Date().getTime() - getProcessStartedDate().getTime();
                        if (msSinceStart > getProcessMaxRuntimeMsBeforeRestartDesired() + 100) {    //we add some ms to account for processing time so we don't get false positives
                            FL.i(TAG, TAGG + "Process \"" + getProcessClassName() + "\" has been running for over " + Long.toString(msSinceStart) + "ms (desired max runtime is "+Long.toString(getProcessMaxRuntimeMsBeforeRestartDesired())+"ms).");
                            ret = NOMINAL_FALSE_MAX_DESIRED_RUNTIME;
                            break;  //force return of this value, as it's more severe than anything below
                        }
                    }

                    break;
                case PROCESS_TYPE_UNKNOWN:
                default:
                    break;
            }//end switch
        }

        FL.v(TAG, TAGG+"Returning: "+ Byte.toString(ret));
        return ret;
    }

    /** Get time (in milliseconds) between most-recent and previous Date values.
     * @return Milliseconds from previous to most-recent dates, or 0 if both/either are not available */
    public long getTimeBetweenHeartbeats_milliseconds() {
        final String TAGG = "timeBetweenHeartbeats_milliseconds: ";
        long ret = 0;

        if (getProcessPreviousHeartbeatDate() == null
                && getProcessMostRecentHeartbeatDate() == null) {
            //both values are null
            FL.d(TAG, TAGG+"Both heartbeat values are null. This is normal at startup, otherwise a result of not updating heartbeat from process.");
        } else if (getProcessPreviousHeartbeatDate() == null) {
            //only previous is null (we only have a most-recent value - there may be only that one in beginning)
            FL.d(TAG, TAGG+"Previous value is null, but most-recent value exists ("+getProcessMostRecentHeartbeatDate().toString()+"). This is normal at startup, otherwise a result of not updating heartbeat from process.");
        } else if (getProcessMostRecentHeartbeatDate() == null) {
            //only most-recent is null (we only have a previous value - this is odd)
            FL.w(TAG, TAGG+"Most recent value is null, but previous value exists ("+getProcessPreviousHeartbeatDate().toString()+"). This is unexpected!");
        } else {
            //we have two values, so we can do math and get a return value
            long getTimePrev = getProcessPreviousHeartbeatDate().getTime();
            long getTimeRecent = getProcessMostRecentHeartbeatDate().getTime();
            ret = (getTimeRecent - getTimePrev);
        }

        FL.v(TAG, TAGG+"Returning: "+String.valueOf(ret));
        return ret;
    }


    /*============================================================================================*/
    /* Data Update/Set Methods */

    /** Record the date this process was registered */
    public void recordProcessRegistered(Date date) {
        final String TAGG = "recordProcessRegistered: ";
        setProcessRegisteredDate(date);
        FL.v(TAG, TAGG+"Registered date is now: "+getProcessRegisteredDate().toString());
    }
    public void recordProcessRegistered() {
        recordProcessRegistered(new Date());
    }

    /** Record the date this process started */
    public void recordProcessStart(Date date, long processID, long processThreadID) {
        final String TAGG = "recordProcessStart: ";
        setProcessStartedDate(date);
        FL.v(TAG, TAGG+"Started date is now: "+getProcessStartedDate().toString());
    }
    public void recordProcessStart() {
        recordProcessStart(new Date(), 0, 0);
    }
    public void recordProcessStart(long processID) {
        recordProcessStart(new Date(), processID, 0);
    }
    public void recordProcessStart(long processID, long processThreadID) {
        recordProcessStart(new Date(), processID, processThreadID);
    }

    /** Record the date this process stopped */
    public void recordProcessStop(Date date) {
        final String TAGG = "recordProcessStop: ";
        setProcessStoppedDate(date);
        FL.v(TAG, TAGG+"Stopped date is now: "+getProcessStoppedDate().toString());
    }
    public void recordProcessStop() {
        recordProcessStop(new Date());
    }

    /** Record the date this process had its most recent heartbeat.
     * This automatically shifts and saves the previous value as well. */
    public void recordProcessHeartbeat(Date date) {
        final String TAGG = "recordProcessHeartbeat: ";
        setProcessPreviousHeartbeatDate(getProcessMostRecentHeartbeatDate());                       //shift most-recent value to previous, since we now have a new most-recent value
        setProcessMostRecentHeartbeatDate(date);
        FL.v(TAG, TAGG+"Most recent is now: "+getProcessMostRecentHeartbeatDateStr()+" (previous is now: "+getProcessPreviousHeartbeatDateStr()+")");
    }
    public void recordProcessHeartbeat() {
        recordProcessHeartbeat(new Date());
    }


    /*============================================================================================*/
    /* Generic Getter & Setter Methods */

    public byte getProcessType() {
        return processType;
    }
    private void setProcessType(byte val) {
        this.processType = val;
    }

    public int getProcessThreadID() {
        return processThreadID;
    }
    public void setProcessThreadID(int val) {
        this.processThreadID = val;
    }

    public Class getProcessClass() {
        return processClass;
    }
    public void setProcessClass(Class val) {
        this.processClass = val;
    }

    public String getProcessClassName() {
        return processClassName;
    }
    private void setProcessClassName(String val) {
        this.processClassName = val;
    }

    public Date getProcessRegisteredDate() {
        return processRegisteredDate;
    }
    private void setProcessRegisteredDate(Date val) {
        this.processRegisteredDate = val;
    }

    public String getProcessParentClassName() {
        return processParentClassName;
    }
    public void setProcessParentClassName(String val) {
        this.processParentClassName = val;
    }

    public long getProcessMaxHeartbeatIntervalMS() {
        return processMaxHeartbeatIntervalMS;
    }
    public void setProcessMaxHeartbeatIntervalMS(long val) {
        this.processMaxHeartbeatIntervalMS = val;
    }

    public Date getProcessStartedDate() {
        return processStartedDate;
    }
    private void setProcessStartedDate(Date val) {
        this.processStartedDate = val;
    }

    public Date getProcessStoppedDate() {
        return processStoppedDate;
    }
    private void setProcessStoppedDate(Date val) {
        this.processStoppedDate = val;
    }

    public Date getProcessPreviousHeartbeatDate() {
        return processPreviousHeartbeatDate;
    }
    public String getProcessPreviousHeartbeatDateStr() {
        if (processPreviousHeartbeatDate == null) {
            return "(null)";
        } else {
            return processPreviousHeartbeatDate.toString();
        }
    }
    private void setProcessPreviousHeartbeatDate(Date val) {
        this.processPreviousHeartbeatDate = val;
    }

    public Date getProcessMostRecentHeartbeatDate() {
        return processMostRecentHeartbeatDate;
    }
    public String getProcessMostRecentHeartbeatDateStr() {
        if (processMostRecentHeartbeatDate == null) {
            return "(null)";
        } else {
            return processMostRecentHeartbeatDate.toString();
        }
    }
    private void setProcessMostRecentHeartbeatDate(Date val) {
        this.processMostRecentHeartbeatDate = val;
    }

    public int getProcessNumberOfExpectedChildren() {
        return processNumberOfExpectedChildren;
    }
    public void setProcessNumberOfExpectedChildren(int val) {
        this.processNumberOfExpectedChildren = val;
    }

    public long getProcessMaxRuntimeMsBeforeRestartDesired() {
        return processMaxRuntimeMsBeforeRestartDesired;
    }
    public void setProcessMaxRuntimeMsBeforeRestartDesired(long val) {
        this.processMaxRuntimeMsBeforeRestartDesired = val;
    }

    public long getProcessMaxRuntimeMsBeforeRestartRequired() {
        return processMaxRuntimeMsBeforeRestartRequired;
    }
    public void setProcessMaxRuntimeMsBeforeRestartRequired(long val) {
        this.processMaxRuntimeMsBeforeRestartRequired = val;
    }

    public byte getProcessActionNeededFlag() {
        return processActionNeededFlag;
    }
    public void setProcessActionNeededFlag(byte val) {
        this.processActionNeededFlag = val;
    }
}
