package com.messagenetsystems.evolution2.devices;

/* BluetoothLightController_HY254117V9 class for managing and working with HY254117V9 controller.
 *
 * Basic idea is to instantiate this wherever needed to do things with these specific lights.
 * Try to leave general BLE or GATT stuff outside of this class/instance (pass in as needed).
 * Rather, try to dedicate this class specifically to this particular light device.
 *
 * The below specified data is given to us by the manufacturer of the HY-254117 V9 light-control board...
 * Remember this controller is a BLE device... It provides a service with write, read, write-extra characteristics.
 * Then we send/read raw bytes, for example:
 *  Ex. Turn lights off/on:  0xb8 0x03 [0x00|0x01]
 *  Ex. Half-bright white:   0xb8 0x06 0x01 0x07 0x00
 *
 * !!! WARNING !!!
 * The LED chips must not exceed certain voltage/current and heat values.
 * We supply about 12v - 12.6v to the device. The lights are connected in series. But voltage can still be dangerous!
 * For example, 100% bright at 12v will theoretically result in 6v to each LED chip (since there are two in series).
 * So, max brightness value would be around 6v equivalent -- WAY TOO HIGH for the diodes! They will fry!
 *  All     Max-temperature = 85C.
 *  White   Voltage: 2.85-3.6 v     Current: 350-1000 mA    (307 lumens)
 *  Red     Voltage: 2.15-2.9 v     Current: 350-700 mA     (162 lumens)
 *  Green   Voltage: 2.85-3.7 v     Current: 350-1000 mA    (285 lumens)
 *  Blue    Voltage: 2.85-3.7 v     Current: 350-1000 mA    (100 lumens)
 *
 * !!! WARNING !!!
 * As of June-2018 shipment of 300-qty, units MUST NOT ever be logically turned off without correct 0x02 byte at end.
 * If you do, then if power is cycled, lights will automatically come back on full bright white and burn out.
 * You must never logically turn them off without that ending byte!
 *
 * NOTE ABOUT BRIGHTNESS LEVELS:
 *  Brightness "power" and brightness "saturation" are two ways to control intensity, and work together.
 *  Power is the coarse power level to the diodes, and saturation is the fine power level to the diodes within each coarse power level.
 *  There are 16 coarse levels, and 255 fine levels within each, for a total of 4,080 brightness levels.
 *
 * 2018.03.14       Chris Rider     Creation begun.
 * 2018.05.01       Chris Rider     Nearing completion of initial creation.
 * 2018.05.02       Chris Rider     Now intelligently and safely calculating maximum power values.
 * 2018.05.10       Chris Rider     Added byteToHexString, validateCommandSafety to ensure LED safety, and constructor check for invalid defined constant values.
 * 2018.10-11       Chris Rider     Numerous updates to prepare lights for Demo in Wisconsin, and generally bring closer to finished product.
 * 2020.05.28       Chris Rider     Migrated to v2 -- But will probably deprecate it in favor of using the standalone app.
 * 2020.07.10       Chris Rider     Updated and added comments for UUIDs found in nRF Connect app (no logic changes, so no testing required).
 */

/*******************************************************
 * DEPRECATED! Use EvolutionFlasherLights app instead! *
 *******************************************************/

import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolution2.Constants;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Arrays;


public class BluetoothLightController_HY254117V9 {
    private static final String TAG = BluetoothLightController_HY254117V9.class.getSimpleName();

    public static final int COLOR_BRIGHTNESS_MIN = 0;   //for fine-tuning brightness
    public static final int COLOR_BRIGHTNESS_MED = 1;
    public static final int COLOR_BRIGHTNESS_MAX = 2;

    public final static String UUID_BASE_96 = "-0000-1000-8000-00805F9B34FB";                       //the last 96-bits of the standard BLE UUID base

    private final static int COLOR_BYTE_RED   = 0;                                                  //just for indicating which element in the color byte array is this color
    private final static int COLOR_BYTE_GREEN = 1;                                                  //just for indicating which element in the color byte array is this color
    private final static int COLOR_BYTE_BLUE  = 2;                                                  //just for indicating which element in the color byte array is this color

    /** Voltage and power level figures... */
    private final static float VOLTS_MAX_SUPPLY = (float) 12.8;                                     //highest theoretically expected light controller input voltage, raised a bit over expected normal just to be safe
    private final static float VOLTS_MAX_PER_DIODE_WHITE = (float) 3.6;                             //note: this assumes fantastic cooling capability, but brief pulses might be alright?
    private final static float VOLTS_MAX_PER_DIODE_RED = (float) 2.9;                               //note: this assumes fantastic cooling capability, but brief pulses might be alright?
    private final static float VOLTS_MAX_PER_DIODE_GREEN = (float) 3.7;                             //note: this assumes fantastic cooling capability, but brief pulses might be alright?
    private final static float VOLTS_MAX_PER_DIODE_BLUE = (float) 3.7;                              //note: this assumes fantastic cooling capability, but brief pulses might be alright?

    private final static int NUM_LIGHTS_IN_SERIES = 2;                                              //number of lights connected in electrical series
    private final static int BRIGHT_PWR_STEPS_POSSIBLE = 16;                                        //0-15 (decimal)
    private final static double MAX_POWER_SAFETY_MARGIN = 0.8;                                      //1 = no margin; <1 = percent margin (ex. 0.8 would result in max power of 80% of theoretical max)

    //calculate what % of max voltage to run this set of diodes at (takes theoretical maximum and reduces that by the safety margin)
    private final static double MAX_BRIGHT_PWR_FACTOR_WHITE = ((VOLTS_MAX_PER_DIODE_WHITE * NUM_LIGHTS_IN_SERIES) / VOLTS_MAX_SUPPLY) * MAX_POWER_SAFETY_MARGIN;
    private final static double MAX_BRIGHT_PWR_FACTOR_RED = ((VOLTS_MAX_PER_DIODE_RED * NUM_LIGHTS_IN_SERIES) / VOLTS_MAX_SUPPLY) * MAX_POWER_SAFETY_MARGIN;
    private final static double MAX_BRIGHT_PWR_FACTOR_GREEN = ((VOLTS_MAX_PER_DIODE_GREEN * NUM_LIGHTS_IN_SERIES) / VOLTS_MAX_SUPPLY) * MAX_POWER_SAFETY_MARGIN;
    private final static double MAX_BRIGHT_PWR_FACTOR_BLUE = ((VOLTS_MAX_PER_DIODE_BLUE * NUM_LIGHTS_IN_SERIES) / VOLTS_MAX_SUPPLY) * MAX_POWER_SAFETY_MARGIN;

    //what step (rounding down) to safely set max brightness at (ex. 0-15 - which will next get converted to hex byte for sending to device)
    private final static int MAX_BRIGHT_PWR_STEP_WHITE = (int) Math.floor(BRIGHT_PWR_STEPS_POSSIBLE * MAX_BRIGHT_PWR_FACTOR_WHITE);
    private final static int MAX_BRIGHT_PWR_STEP_RED = (int) Math.floor(BRIGHT_PWR_STEPS_POSSIBLE * MAX_BRIGHT_PWR_FACTOR_RED);
    private final static int MAX_BRIGHT_PWR_STEP_GREEN = (int) Math.floor(BRIGHT_PWR_STEPS_POSSIBLE * MAX_BRIGHT_PWR_FACTOR_GREEN);
    private final static int MAX_BRIGHT_PWR_STEP_BLUE = (int) Math.floor(BRIGHT_PWR_STEPS_POSSIBLE * MAX_BRIGHT_PWR_FACTOR_BLUE);

    //byte equivalent of the max-brightness value calculated above (what can be sent in a command to the light controller device)
    private final static byte MAX_BRIGHT_PWR_BYTE_WHITE = Byte.parseByte( Integer.toHexString(MAX_BRIGHT_PWR_STEP_WHITE), 16);
    private final static byte MAX_BRIGHT_PWR_BYTE_RED = Byte.parseByte( Integer.toHexString(MAX_BRIGHT_PWR_STEP_RED),   16);
    private final static byte MAX_BRIGHT_PWR_BYTE_GREEN = Byte.parseByte( Integer.toHexString(MAX_BRIGHT_PWR_STEP_GREEN), 16);
    private final static byte MAX_BRIGHT_PWR_BYTE_BLUE = Byte.parseByte( Integer.toHexString(MAX_BRIGHT_PWR_STEP_BLUE),  16);


    /** UUID values for services and characteristics... */
    public final static String GENERIC_SERV_UUID                       = "1800";                    //Generic Access Service
    public final static String GENERIC_CHAR_UUID_DEVICENAME            = "00002A00"+UUID_BASE_96;   //Device Name                                   READ            Ex. Ble_Light
    public final static String GENERIC_CHAR_UUID_APPEARANCE            = "00002A01"+UUID_BASE_96;   //Appearance                                    READ            Ex. [0] Unknown
    public final static String GENERIC_CHAR_UUID_PERIPHERALPRIVACYFLAG = "00002A02"+UUID_BASE_96;   //Peripheral Privacy Flag                       READ,WRITE      Ex. Privacy is disabled in this device
    public final static String GENERIC_CHAR_UUID_RECONNECTIONADDRESS   = "00002A03"+UUID_BASE_96;   //Reconnection Address                          WRITE
    public final static String GENERIC_CHAR_UUID_PERIPHPREFCONNPARAMS  = "00002A04"+UUID_BASE_96;   //Peripheral Preferred Connection Paramaters    READ            Ex. Connection Interval: 100.00ms - 200.00ms, Slave latency: 0, Supervision Timeout Multiplier: 1000

    public final static String INFO_SERV_UUID                          = "180A";                    //Device Information Service
    public final static String INFO_CHAR_UUID_SYSTEMID                 = "00002A23"+UUID_BASE_96;   //System ID                                     READ            Ex. (0x) 14-28-1A-E5-A6-44-00-00
    public final static String INFO_CHAR_UUID_MODELNUMBER              = "00002A24"+UUID_BASE_96;   //Model Number String                           READ            Ex. Blue Light(High)
    public final static String INFO_CHAR_UUID_SERIALNUMBER             = "00002A25"+UUID_BASE_96;   //Serial Number String                          READ            Ex. 2017-08-14
    public final static String INFO_CHAR_UUID_FIRMWAREREVISION         = "00002A26"+UUID_BASE_96;   //Firmware Revision String                      READ            Ex. V1.2.9
    public final static String INFO_CHAR_UUID_HARDWAREREVISION         = "00002A27"+UUID_BASE_96;   //Hardware Revision String                      READ            Ex. V1.2.9
    public final static String INFO_CHAR_UUID_SOFTWAREREVISION         = "00002A28"+UUID_BASE_96;   //Software Revision String                      READ            Ex. 20170814_V1.2.9
    public final static String INFO_CHAR_UUID_MANUFACTURERNAME         = "00002A29"+UUID_BASE_96;   //Manufacturer Name String                      READ            Ex. RESET
    public final static String INFO_CHAR_UUID_CERTIFICATIONDATA        = "00002A2A"+UUID_BASE_96;   //IEEE 11073-20601 Regulatory Cert Data         READ            Ex. (0x) FE-00-65-78-70-65-72-69-6D-65-6E-74-61-6C
    public final static String INFO_CHAR_UUID_PNPID                    = "00002A50"+UUID_BASE_96;   //PnP ID                                        READ            Ex. Bluetooth SIG Company: Texas Instruments Inc. <0x000D> Product Id: 0 Product Version: 272

    public final static String CONTROL_SERV_UUID                       = "00001000"+UUID_BASE_96;   //Main control service (match for service discovery?)
    public final static String CONTROL_CHAR_UUID_1001                  = "00001001"+UUID_BASE_96;   //primary light controls (color, bright, etc.)  NOTIFY,READ,WRITE,WRITE-NO-RESPONSE
    public final static String CONTROL_CHAR_UUID_1002                  = "00001002"+UUID_BASE_96;   //unknown                                       NOTIFY
    public final static String CONTROL_CHAR_UUID_1003                  = "00001003"+UUID_BASE_96;   //extra/admin controls (password, alarm, etc.)  READ,WRITE


    /** Define our datagram values (pieces of what gets sent to the control device)... */
    //values for writing
    final byte DATAGRAM_W_HEADER = (byte) 0xb8;                                                     //required data-header to start the beginning of each command to our controller

    final byte DATAGRAM_W_CMD_COLOR  = (byte) 0x01;                                                 //control "toning" (colors)
    final byte DATAGRAM_W_CMD_SCENE  = (byte) 0x02;                                                 //control scene mode type (1-12)
    final byte DATAGRAM_W_CMD_POWER  = (byte) 0x03;                                                 //control turning lights on or off
    final byte DATAGRAM_W_CMD_FLASH  = (byte) 0x04;                                                 //control turning flash on or off
    final byte DATAGRAM_W_CMD_SENSE  = (byte) 0x05;                                                 //control "induction" / turning sensor on or off    --what??
    final byte DATAGRAM_W_CMD_WHITE  = (byte) 0x06;                                                 //control white light
    final byte DATAGRAM_W_CMD_SIDE1  = (byte) 0x07;                                                 //control side lights on or off                     --what??
    final byte DATAGRAM_W_CMD_SIDE2  = (byte) 0x08;                                                 //control side lights on or off                     --what??
    final byte DATAGRAM_W_CMD_ALARM  = (byte) 0x09;                                                 //control alarm cancel on or off                    --what??

    final byte DATAGRAM_W_DATA_OFF   = (byte) 0x00;
    final byte DATAGRAM_W_DATA_ON    = (byte) 0x01;

    final byte DATAGRAM_W_DATA_RED_MINBRIGHT_PWR         = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_RED_MINBRIGHT_SAT         = (byte) 0x31;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the diode will even work)
    //final byte DATAGRAM_W_DATA_RED_MINBRIGHT_SAT_RGB     = (byte) 0x36;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks good)
    final byte DATAGRAM_W_DATA_RED_MINBRIGHT_SAT_RGB     = (byte) 0x32;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks good)
    final byte DATAGRAM_W_DATA_RED_DIMBRIGHT_PWR         = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_RED_DIMBRIGHT_SAT         = (byte) 0x36;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the color looks "dim")
    final byte DATAGRAM_W_DATA_RED_DIMBRIGHT_SAT_RGB     = (byte) 0x36;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks "dim")
    final byte DATAGRAM_W_DATA_RED_MEDBRIGHT_PWR         = (byte) 0x01;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_RED_MEDBRIGHT_SAT         = (byte) 0x36;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the color looks "medium")
    final byte DATAGRAM_W_DATA_RED_MEDBRIGHT_SAT_RGB     = (byte) 0x36;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks "medium")
    byte DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY          = (byte) 0x03;                             //0-15(dec)/0x00-0x0f(hex)  (should be maximum value that doesn't result in long-term heat build-up)
    final byte DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_PEAK    = MAX_BRIGHT_PWR_BYTE_RED;                 //WARNING: Make sure this is active only a short time or heat will build up and may destroy the diode.

    final byte DATAGRAM_W_DATA_GREEN_MINBRIGHT_PWR       = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_GREEN_MINBRIGHT_SAT       = (byte) 0x40;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the diode will even work)
    //final byte DATAGRAM_W_DATA_GREEN_MINBRIGHT_SAT_RGB   = (byte) 0x45;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks good)
    final byte DATAGRAM_W_DATA_GREEN_MINBRIGHT_SAT_RGB   = (byte) 0x46;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks good)
    final byte DATAGRAM_W_DATA_GREEN_DIMBRIGHT_PWR       = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_GREEN_DIMBRIGHT_SAT       = (byte) 0x45;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the color looks "dim")
    final byte DATAGRAM_W_DATA_GREEN_DIMBRIGHT_SAT_RGB   = (byte) 0x45;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks "dim")
    final byte DATAGRAM_W_DATA_GREEN_MEDBRIGHT_PWR       = (byte) 0x02;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_GREEN_MEDBRIGHT_SAT       = (byte) 0x45;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the color looks "medium")
    final byte DATAGRAM_W_DATA_GREEN_MEDBRIGHT_SAT_RGB   = (byte) 0x45;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks "medium")
    byte DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY        = (byte) 0x05;                             //0-15(dec)/0x00-0x0f(hex)  (should be maximum value that doesn't result in long-term heat build-up)
    final byte DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_PEAK  = MAX_BRIGHT_PWR_BYTE_GREEN;               //WARNING: Make sure this is active only a short time or heat will build up and may destroy the diode.

    final byte DATAGRAM_W_DATA_BLUE_MINBRIGHT_PWR        = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_BLUE_MINBRIGHT_SAT        = (byte) 0x31;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the diode will even work)
    //final byte DATAGRAM_W_DATA_BLUE_MINBRIGHT_SAT_RGB    = (byte) 0x43;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks good)
    final byte DATAGRAM_W_DATA_BLUE_MINBRIGHT_SAT_RGB    = (byte) 0x32;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks good)
    final byte DATAGRAM_W_DATA_BLUE_DIMBRIGHT_PWR        = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_BLUE_DIMBRIGHT_SAT        = (byte) 0x43;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the color looks "dim")
    final byte DATAGRAM_W_DATA_BLUE_DIMBRIGHT_SAT_RGB    = (byte) 0x43;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks "dim")
    final byte DATAGRAM_W_DATA_BLUE_MEDBRIGHT_PWR        = (byte) 0x02;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_BLUE_MEDBRIGHT_SAT        = (byte) 0x43;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which the color looks "medium")
    final byte DATAGRAM_W_DATA_BLUE_MEDBRIGHT_SAT_RGB    = (byte) 0x43;                             //0-255(dec)/0x00-0xff(hex)  (min individual color saturation-power at min brightness-power, in which RGB white looks "medium")
    byte DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY         = (byte) 0x05;                             //0-15(dec)/0x00-0x0f(hex)  (should be maximum value that doesn't result in long-term heat build-up)
    final byte DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_PEAK   = MAX_BRIGHT_PWR_BYTE_BLUE;                //WARNING: Make sure this is active only a short time or heat will build up and may destroy the diode.

    final byte DATAGRAM_W_DATA_WHITE_MINBRIGHT_PWR       = (byte) 0x00;                             //0-15(dec)/0x00-0x0f(hex)
    final byte DATAGRAM_W_DATA_WHITE_MEDBRIGHT_PWR       = (byte) 0x02;                             //0-15(dec)/0x00-0x0f(hex)
    byte DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY        = (byte) 0x05;                             //0-15(dec)/0x00-0x0f(hex)  (should be maximum value that doesn't result in long-term heat build-up)
    final byte DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_PEAK  = MAX_BRIGHT_PWR_BYTE_WHITE;               //WARNING: Make sure this is active only a short time or heat will build up and may destroy the diode.

    final byte DATAGRAM_W_DATA_WHITE_TONE_COOL = (byte) 0x00;                                       //0-20(dec)/0x00-0x0f(hex)  Note: We don't actually have white tone capability with our device at this time.

    final byte DATAGRAM_W_DATA_SPEED_SLOWEST = (byte) 0x00;                                         //0-10(dec)/0x00-0x0a(hex)  Speed of change (e.g. for color set)
    final byte DATAGRAM_W_DATA_SPEED_FASTEST = (byte) 0x0a;                                         //0-10(dec)/0x00-0x0a(hex)  Speed of change (e.g. for color set)
    final byte DATAGRAM_W_DATA_SPEED_DESIRED = (byte) 0x08;                                         //0-10(dec)/0x00-0x0a(hex)  Speed of change (e.g. for color set)

    //values for reading -TODO


    /** Hardware & Security related values... */
    public final static String MAC_ADDR_PREFIX = "44:A6:E5";                                        //manufacturer portion (first half) of the controller's MAC address
    public final static String DEVICE_NAME = "Ble_Light";                                           //default device name reported by our controller device

    private static byte[] DEVICE_PIN_BYTES;
    private final static String DEVICE_PIN = "000000";

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;


    /** Constructor */
    public BluetoothLightController_HY254117V9(int logMethod) {
        this.logMethod = logMethod;
        logD("Constructing an instance of "+TAG+".");

        // Detect and correct any miscalculated max-brightness values...
        if (DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY >= DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_PEAK) {
            DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY = (byte) (DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_PEAK - 2);
            logW("White maximum steady brightness is defined >= peak brightness. Reduced to less than peak... (you should correct the code!)");
        }
        if (DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY > DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_PEAK) {
            logW("Red maximum steady brightness is defined >= peak brightness. Reducing to less than peak... (you should correct the code!)");
            DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY = (byte) (DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_PEAK - 2);
        }
        if (DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY > DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_PEAK) {
            logW("Green maximum steady brightness is defined >= peak brightness. Reducing to less than peak... (you should correct the code!)");
            DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY = (byte) (DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_PEAK - 2);
        }
        if (DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY > DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_PEAK) {
            logW("Blue maximum steady brightness is defined >= peak brightness. Reducing to less than peak... (you should correct the code!)");
            DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY = (byte) (DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_PEAK - 2);
        }
    }


    /** Getters */
    String getServiceUUID_mainForServiceDiscovery() {
        return CONTROL_SERV_UUID;
    }
    String getServiceUUID_characteristicForWriting() {
        return CONTROL_CHAR_UUID_1001;
    }


    /***********************************************************************************************
     ** Controller Chip-specific Light command construction methods...
     ** The output of these gets written directly to a characteristic to actually do something.
     ** Note: to perform complex functions, you will need to compound/chain write commands. For
     ** example, to flash red, you would first need to change color to red, then send flash command.
     **
     ** To get data to write to the characteristic, simply call Characteristic.setValue(), providing return value of one of these.
     **     Ex. mCharacteristic.setValue( constructLightCommandByteSequence_turnOff() )
     **     Ex. mCharacteristic.setValue( constructLightCommandByteSequence_colorMinBrightness(constructDataBytes_color_red()) )
     **
     ** Then, just write that characteristic to Gatt as normal.
     **     Ex. mBluetoothGatt.writeCharacteristic(mCharacteristic)
     */

    public byte[] constructLightCommandByteSequence_turnOn() {
        final String TAGG = "constructLightCommandByteSequence_turnOn: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_POWER,
                DATAGRAM_W_DATA_ON
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_turnOff() {
        final String TAGG = "constructLightCommandByteSequence_turnOff: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_POWER,
                DATAGRAM_W_DATA_OFF,
                (byte) 0x02     /* IMPORTANT!!! needed to prevent automatically going max white brightness after power cycle - will return to last set state instead when input power is resumed */
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }

    // NOTE: these "flashing" commands are for the light chip to handle on its own
    public byte[] constructLightCommandByteSequence_flashingOn() {
        final String TAGG = "constructLightCommandByteSequence_flashingOn: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_FLASH,
                DATAGRAM_W_DATA_ON
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_flashingOff() {
        final String TAGG = "constructLightCommandByteSequence_flashingOff: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_FLASH,
                DATAGRAM_W_DATA_OFF
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }

    // RGB WHITE...
    public byte[] constructLightCommandByteSequence_whiteRgbMinBrightness() {
        final String TAGG = "constructLightCommandByteSequence_whiteRgbMinBrightness: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_COLOR,
                DATAGRAM_W_DATA_RED_MINBRIGHT_SAT_RGB, DATAGRAM_W_DATA_GREEN_MINBRIGHT_SAT_RGB, DATAGRAM_W_DATA_BLUE_MINBRIGHT_SAT_RGB,     /*(byte) 0x36, (byte) 0x45, (byte) 0x43,*/
                0x00,   /*min bright-power for RGB*/
                DATAGRAM_W_DATA_SPEED_DESIRED
        };

        return cmd;
    }
    public byte[] constructLightCommandByteSequence_whiteRgbMedBrightness() {
        final String TAGG = "constructLightCommandByteSequence_whiteRgbMedBrightness: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_COLOR,
                DATAGRAM_W_DATA_RED_MEDBRIGHT_SAT_RGB, DATAGRAM_W_DATA_GREEN_MEDBRIGHT_SAT_RGB, DATAGRAM_W_DATA_BLUE_MEDBRIGHT_SAT_RGB,    /*(byte) 0x48, (byte) 0xff, (byte) 0x88,*/
                0x02,   /*medium bright-power for RGB*/
                DATAGRAM_W_DATA_SPEED_DESIRED
        };

        return cmd;
    }

    // WHITE DIODE...
    public byte[] constructLightCommandByteSequence_whiteMinBrightness() {
        final String TAGG = "constructLightCommandByteSequence_whiteMinBrightness: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_WHITE,
                DATAGRAM_W_DATA_ON,
                DATAGRAM_W_DATA_WHITE_MINBRIGHT_PWR,
                DATAGRAM_W_DATA_WHITE_TONE_COOL
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_whiteMedBrightness() {
        final String TAGG = "constructLightCommandByteSequence_whiteMedBrightness: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_WHITE,
                DATAGRAM_W_DATA_ON,
                DATAGRAM_W_DATA_WHITE_MEDBRIGHT_PWR,
                DATAGRAM_W_DATA_WHITE_TONE_COOL
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_whiteMaxBrightnessPeak() {
        final String TAGG = "constructLightCommandByteSequence_whiteMaxBrightnessPeak: ";
        Log.w(TAG, TAGG+"WARNING! This brightness is not intended to be long lasting due to over-heat/voltage.");

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_WHITE,
                DATAGRAM_W_DATA_ON,
                DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_PEAK,
                DATAGRAM_W_DATA_WHITE_TONE_COOL
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_whiteMaxBrightnessSteady() {
        final String TAGG = "constructLightCommandByteSequence_whiteMaxBrightnessSteady: ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_WHITE,
                DATAGRAM_W_DATA_ON,
                DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY,
                DATAGRAM_W_DATA_WHITE_TONE_COOL
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }

    // COLORS...
    public byte[] constructLightCommandByteSequence_colorMinBrightness(byte[] colorData) {
        final String TAGG = "constructLightCommandByteSequence_colorMinBrightness("+byteArrayToHexString(colorData)+"): ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_COLOR,
                colorData[COLOR_BYTE_RED], colorData[COLOR_BYTE_GREEN], colorData[COLOR_BYTE_BLUE],
                getMinBrightnessForColors(colorData),
                DATAGRAM_W_DATA_SPEED_DESIRED
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_colorMedBrightness(byte[] colorData) {
        final String TAGG = "constructLightCommandByteSequence_colorMedBrightness("+byteArrayToHexString(colorData)+"): ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_COLOR,
                colorData[COLOR_BYTE_RED], colorData[COLOR_BYTE_GREEN], colorData[COLOR_BYTE_BLUE],
                getMedBrightnessForColors(colorData),
                DATAGRAM_W_DATA_SPEED_DESIRED
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_colorMaxBrightnessPeak(byte[] colorData) {
        final String TAGG = "constructLightCommandByteSequence_colorMaxBrightnessPeak("+byteArrayToHexString(colorData)+"): ";
        Log.w(TAG, TAGG+"WARNING! This brightness is not intended to be long lasting due to over-heat/voltage.");

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_COLOR,
                colorData[COLOR_BYTE_RED], colorData[COLOR_BYTE_GREEN], colorData[COLOR_BYTE_BLUE],
                DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_PEAK,
                DATAGRAM_W_DATA_SPEED_DESIRED
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }
    public byte[] constructLightCommandByteSequence_colorMaxBrightnessSteady(byte[] colorData) {
        final String TAGG = "constructLightCommandByteSequence_colorMaxBrightnessSteady("+byteArrayToHexString(colorData)+"): ";

        final byte[] cmd = {DATAGRAM_W_HEADER,
                DATAGRAM_W_CMD_COLOR,
                colorData[COLOR_BYTE_RED], colorData[COLOR_BYTE_GREEN], colorData[COLOR_BYTE_BLUE],
                getMaxBrightnessSteadyForColors(colorData),
                DATAGRAM_W_DATA_SPEED_DESIRED
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(cmd)+"\".");
        return cmd;
    }


    /***********************************************************************************************
    ** Light data construction methods...
    ** The output of these gets built into a command (see above section).
    ** Note: Providing fineTuneSatLevel allows us to have finer granularity in brightness control.
    */

    public byte[] constructDataBytes_color_red() {
        return constructDataBytes_color_red(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_red(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_red: ";

        byte satValue;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
                satValue = DATAGRAM_W_DATA_RED_MINBRIGHT_SAT_RGB;
                break;
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValue = (byte) 0xff;
                break;
        }

        final byte[] bytes = {
                satValue,
                (byte) 0x00,    /* green    0-255   0x00-0xff */
                (byte) 0x00     /* blue     0-255   0x00-0xff */
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }
    public byte[] constructDataBytes_color_green() {
        return constructDataBytes_color_green(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_green(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_green: ";

        byte satValue;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
                satValue = DATAGRAM_W_DATA_GREEN_MINBRIGHT_SAT_RGB;
                break;
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValue = (byte) 0xff;
                break;
        }

        final byte[] bytes = {
                (byte) 0x00,    /* red      0-255   0x00-0xff */
                satValue,
                (byte) 0x00     /* blue     0-255   0x00-0xff */
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }
    public byte[] constructDataBytes_color_blue() {
        return constructDataBytes_color_blue(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_blue(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_blue: ";

        byte satValue;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
                satValue = DATAGRAM_W_DATA_BLUE_MINBRIGHT_SAT_RGB;
                break;
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValue = (byte) 0xff;
                break;
        }

        final byte[] bytes = {
                (byte) 0x00,    /* red      0-255   0x00-0xff */
                (byte) 0x00,    /* green    0-255   0x00-0xff */
                satValue
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }
    public byte[] constructDataBytes_color_yellow() {
        return constructDataBytes_color_yellow(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_yellow(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_yellow: ";

        byte satValueRed, satValueGreen;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValueRed = (byte) 0xaa;
                satValueGreen = (byte) 0xff;
                break;
        }

        final byte[] bytes = {
                satValueRed,
                satValueGreen,
                (byte) 0x00     /* blue     0-255   0x00-0xff */
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }
    public byte[] constructDataBytes_color_orange() {
        return constructDataBytes_color_orange(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_orange(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_orange: ";

        byte satValueRed, satValueGreen;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValueRed = (byte) 0xff;
                satValueGreen = (byte) 0x88;
                break;
        }

        final byte[] bytes = {
                satValueRed,
                satValueGreen,
                (byte) 0x00     /* blue     0-255   0x00-0xff */
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }
    public byte[] constructDataBytes_color_purple() {
        return constructDataBytes_color_purple(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_purple(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_purple: ";

        byte satValueRed, satValueBlue;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValueRed = (byte) 0x66;
                satValueBlue = (byte) 0xff;
                break;
        }

        final byte[] bytes = {
                satValueRed,
                (byte) 0x00,    /* green    0-255   0x00-0xff */
                satValueBlue
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }
    public byte[] constructDataBytes_color_pink() {
        return constructDataBytes_color_pink(COLOR_BRIGHTNESS_MIN);
    }
    public byte[] constructDataBytes_color_pink(int fineTuneSatLevel) {
        final String TAGG = "constructDataBytes_color_pink: ";

        byte satValueRed, satValueBlue;

        switch (fineTuneSatLevel) {
            case COLOR_BRIGHTNESS_MIN:
            case COLOR_BRIGHTNESS_MED:
            case COLOR_BRIGHTNESS_MAX:
            default:
                satValueRed = (byte) 0xff;
                satValueBlue = (byte) 0xcc;
                break;
        }

        final byte[] bytes = {
                satValueRed,
                (byte) 0x00,    /* green    0-255   0x00-0xff */
                satValueBlue
        };

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(bytes)+"\".");
        return bytes;
    }


    /***********************************************************************************************
     ** Misc. methods...
     */

    // Take provided command and make sure it's safe, returning the original or corrected command
    private byte[] validateCommandSafety(byte[] commandToCheck) {                                   //TODO: split this into more discrete methods
        final String TAGG = "validateCommandSafety("+byteArrayToHexString(commandToCheck)+"): ";

        final int HEADER_BYTE_POS = 0;                //for all commands, first byte is header
        final int CMD_BYTE_POS = 1;                   //for all commands, second byte is command
        final int BRIGHTNESS_BYTE_WHITE_POS = 3;      //for white commands, fourth byte is the brightness
        final int BRIGHTNESS_BYTE_COLOR_POS = 5;      //for color commands, sixth byte is the brightness
        final int COLOR_BYTE_RED_POS = 2;             //for color commands, third byte is the red value
        final int COLOR_BYTE_GREEN_POS = 3;           //for color commands, fourth byte is the green value
        final int COLOR_BYTE_BLUE_POS = 4;            //for color commands, fifth byte is the blue value

        // First, check the first byte to make sure it's a valid header byte; correct it, if not...
        if (commandToCheck[HEADER_BYTE_POS] != DATAGRAM_W_HEADER) {
            Log.w(TAG, TAGG+"Data header needs correcting ("+byteToHexString(commandToCheck[HEADER_BYTE_POS])+" -> "+byteToHexString(DATAGRAM_W_HEADER)+").");
            commandToCheck[HEADER_BYTE_POS] = DATAGRAM_W_HEADER;
        }

        // Second, check the second byte to determine what kind of other checks we'll need to do (then do them, as appropriate)...
        switch (commandToCheck[CMD_BYTE_POS]) {
            case DATAGRAM_W_CMD_COLOR:
                /* check that colors are safe with specified power level
                 *  (the primary concern is with red, since it requires a lower voltage than other colors)
                 *  (for now, just assume steady safe value, not peak --to be extra safe)
                 */

                //NOTE: start first with the color that can handle the highest power level... (since we're correcting a shared brightness value for all colors, we want lowest common denominator)

                //if only green is on AND the command's brightness is above green's safe max brightness, reset brightness to maximum green can safely tolerate
                if (commandToCheck[COLOR_BYTE_GREEN_POS] > (byte) 0x00
                        && commandToCheck[COLOR_BYTE_BLUE_POS] <= (byte) 0x00
                        && commandToCheck[COLOR_BYTE_RED_POS] <= (byte) 0x00
                        && commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] > DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY) {
                    Log.w(TAG, TAGG + "Brightness needs changed to protect green diode (" + byteToHexString(commandToCheck[BRIGHTNESS_BYTE_COLOR_POS]) + " -> " + byteToHexString(DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY) + ").");
                    commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] = DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY;
                }

                //if only blue is on AND the command's brightness is above blue's safe max brightness, reset brightness to maximum blue can safely tolerate
                if (commandToCheck[COLOR_BYTE_BLUE_POS] > (byte) 0x00
                        && commandToCheck[COLOR_BYTE_GREEN_POS] <= (byte) 0x00
                        && commandToCheck[COLOR_BYTE_RED_POS] <= (byte) 0x00
                        && commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] > DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY) {
                    Log.w(TAG, TAGG + "Brightness needs changed to protect blue diode (" + byteToHexString(commandToCheck[BRIGHTNESS_BYTE_COLOR_POS]) + " -> " + byteToHexString(DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY) + ").");
                    commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] = DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY;
                }

                //if only red is on AND the command's brightness is above red's safe max brightness, reset brightness to maximum red can safely tolerate
                if (commandToCheck[COLOR_BYTE_RED_POS] > (byte) 0x00
                        && commandToCheck[COLOR_BYTE_GREEN_POS] <= (byte) 0x00
                        && commandToCheck[COLOR_BYTE_BLUE_POS] <= (byte) 0x00
                        && commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] > DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY) {
                    Log.w(TAG, TAGG + "Brightness needs changed to protect red diode (" + byteToHexString(commandToCheck[BRIGHTNESS_BYTE_COLOR_POS]) + " -> " + byteToHexString(DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY) + ").");
                    commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] = DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY;
                }

                //NOTE: at this point, if only a single color was commanded, we should now be at the safest brightness level that the most delicate diode can handle
                //(next will need to investigate more complex color combinations)

                //for color combos including red, use red's max safe value as brightness
                if (commandToCheck[COLOR_BYTE_RED_POS] > (byte) 0x00
                        && (commandToCheck[COLOR_BYTE_GREEN_POS] > (byte) 0x00 || commandToCheck[COLOR_BYTE_BLUE_POS] > (byte) 0x00)
                        && commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] > DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY) {
                    Log.w(TAG, TAGG + "Brightness needs changed to protect red diode (" + byteToHexString(commandToCheck[BRIGHTNESS_BYTE_COLOR_POS]) + " -> " + byteToHexString(DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY) + ").");
                    commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] = DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY;     //TODO: in future, make corrected-value calculation more sophisticated?
                }

                //NOTE: at this point, any color command that includes the most sensitive color should now only use its max safe brightness

                break;
            case DATAGRAM_W_CMD_WHITE:
                if (commandToCheck[BRIGHTNESS_BYTE_WHITE_POS] > DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY) {
                    Log.w(TAG, TAGG + "Brightness needs changed to protect white diode (" + byteToHexString(commandToCheck[BRIGHTNESS_BYTE_WHITE_POS]) + " -> " + byteToHexString(DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY) + ").");
                    commandToCheck[BRIGHTNESS_BYTE_COLOR_POS] = DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY;
                }
                break;
            default:
                break;
        }

        Log.v(TAG, TAGG+"Returning \""+byteArrayToHexString(commandToCheck)+"\".");
        return commandToCheck;
    }

    public byte getMinBrightnessForColors(byte[] colorData) {
        final String TAGG = "getMinBrightnessForColors: ";
        byte brightnessToUse;

        if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            //only color provided is red, so return that diode's minimum
            brightnessToUse = DATAGRAM_W_DATA_RED_MINBRIGHT_PWR;
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            //only color provided is green, so return that diode's minimum
            brightnessToUse = DATAGRAM_W_DATA_GREEN_MINBRIGHT_PWR;
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            //only color provided is blue, so return that diode's minimum
            brightnessToUse = DATAGRAM_W_DATA_BLUE_MINBRIGHT_PWR;
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            //red & green colors provided, so return the lowest minimum of either
            brightnessToUse = getLowestValue_ofTwoHexBytes(DATAGRAM_W_DATA_RED_MINBRIGHT_PWR, DATAGRAM_W_DATA_GREEN_MINBRIGHT_PWR);
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            //red & blue colors provided, so return the lowest minimum of either
            brightnessToUse = getLowestValue_ofTwoHexBytes(DATAGRAM_W_DATA_RED_MINBRIGHT_PWR, DATAGRAM_W_DATA_BLUE_MINBRIGHT_PWR);
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            //green & blue colors provided, so return the lowest minimum of either
            brightnessToUse = getLowestValue_ofTwoHexBytes(DATAGRAM_W_DATA_GREEN_MINBRIGHT_PWR, DATAGRAM_W_DATA_BLUE_MINBRIGHT_PWR);
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            //all colors provided, so return the lowest minimum among them
            brightnessToUse = getLowestValue_ofThreeHexBytes(DATAGRAM_W_DATA_RED_MINBRIGHT_PWR, DATAGRAM_W_DATA_GREEN_MINBRIGHT_PWR, DATAGRAM_W_DATA_BLUE_MINBRIGHT_PWR);
        else {
            Log.w(TAG, TAGG+"Unhandled colors. Will return white medium bright power value for brightness.");
            brightnessToUse = DATAGRAM_W_DATA_WHITE_MEDBRIGHT_PWR;
        }

        return brightnessToUse;
    }
    public byte getMedBrightnessForColors(byte[] colorData) {
        byte brightnessToUse;

        if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            brightnessToUse = DATAGRAM_W_DATA_RED_MEDBRIGHT_PWR;
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            brightnessToUse = DATAGRAM_W_DATA_GREEN_MEDBRIGHT_PWR;
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = DATAGRAM_W_DATA_BLUE_MEDBRIGHT_PWR;
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            brightnessToUse = getMiddleValue_ofTwoHexBytes(DATAGRAM_W_DATA_RED_MEDBRIGHT_PWR, DATAGRAM_W_DATA_GREEN_MEDBRIGHT_PWR);
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = getMiddleValue_ofTwoHexBytes(DATAGRAM_W_DATA_RED_MEDBRIGHT_PWR, DATAGRAM_W_DATA_BLUE_MEDBRIGHT_PWR);
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = getMiddleValue_ofTwoHexBytes(DATAGRAM_W_DATA_GREEN_MEDBRIGHT_PWR, DATAGRAM_W_DATA_BLUE_MEDBRIGHT_PWR);
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = getMiddleValue_ofThreeHexBytes(DATAGRAM_W_DATA_RED_MEDBRIGHT_PWR, DATAGRAM_W_DATA_GREEN_MEDBRIGHT_PWR, DATAGRAM_W_DATA_BLUE_MEDBRIGHT_PWR);
        else
            brightnessToUse = DATAGRAM_W_DATA_WHITE_MEDBRIGHT_PWR;

        return brightnessToUse;
    }
    public byte getMaxBrightnessSteadyForColors(byte[] colorData) {
        byte brightnessToUse;

        if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            brightnessToUse = DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY;
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            brightnessToUse = DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY;
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY;
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] == (byte) 0x00)
            brightnessToUse = getHighestValue_ofTwoHexBytes(DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY, DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY);
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] == (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = getHighestValue_ofTwoHexBytes(DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY, DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY);
        else if (colorData[COLOR_BYTE_RED] == (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = getHighestValue_ofTwoHexBytes(DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY, DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY);
        else if (colorData[COLOR_BYTE_RED] > (byte) 0x00 && colorData[COLOR_BYTE_GREEN] > (byte) 0x00 && colorData[COLOR_BYTE_BLUE] > (byte) 0x00)
            brightnessToUse = getHighestValue_ofThreeHexBytes(DATAGRAM_W_DATA_RED_MAXBRIGHT_PWR_STDY, DATAGRAM_W_DATA_GREEN_MAXBRIGHT_PWR_STDY, DATAGRAM_W_DATA_BLUE_MAXBRIGHT_PWR_STDY);
        else
            brightnessToUse = DATAGRAM_W_DATA_WHITE_MAXBRIGHT_PWR_STDY;

        return brightnessToUse;
    }

    public static byte getLowestValue_ofTwoHexBytes(byte val1, byte val2) {
        if (val1 < val2)
            return val1;
        else if (val2 < val1)
            return val2;
        else
            return val1;
    }

    public static byte getLowestValue_ofThreeHexBytes(byte val1, byte val2, byte val3) {
        byte highestRound1;
        byte highestRoundFinal;

        if (val1 < val2)
            highestRound1 = val1;
        else if (val2 < val1)
            highestRound1 = val2;
        else
            highestRound1 = val1;

        if (highestRound1 < val3)
            highestRoundFinal = highestRound1;
        else if (val3 < highestRound1)
            highestRoundFinal = val3;
        else
            highestRoundFinal = highestRound1;

        return highestRoundFinal;
    }

    public static byte getMiddleValue_ofTwoHexBytes(byte val1, byte val2) {
        //TODO: calculate hex average of values and return
        //for now, just return lowest of the two...
        if (val1 < val2)
            return val1;
        else if (val2 < val1)
            return val2;
        else
            return val1;
    }

    public static byte getMiddleValue_ofThreeHexBytes(byte val1, byte val2, byte val3) {
        //TODO: calculate hex average of values and return
        //for now, just return lowest of the two...
        byte lowestRound1;
        byte lowestRoundFinal;

        if (val1 < val2)
            lowestRound1 = val1;
        else if (val2 < val1)
            lowestRound1 = val2;
        else
            lowestRound1 = val1;

        if (lowestRound1 < val3)
            lowestRoundFinal = lowestRound1;
        else if (val3 < lowestRound1)
            lowestRoundFinal = val3;
        else
            lowestRoundFinal = lowestRound1;

        return lowestRoundFinal;
    }

    public static byte getHighestValue_ofTwoHexBytes(byte val1, byte val2) {
        if (val1 > val2)
            return val1;
        else if (val2 > val1)
            return val2;
        else
            return val1;
    }

    public static byte getHighestValue_ofThreeHexBytes(byte val1, byte val2, byte val3) {
        byte highestRound1;
        byte highestRoundFinal;

        if (val1 > val2)
            highestRound1 = val1;
        else if (val2 > val1)
            highestRound1 = val2;
        else
            highestRound1 = val1;

        if (highestRound1 > val3)
            highestRoundFinal = highestRound1;
        else if (val3 > highestRound1)
            highestRoundFinal = val3;
        else
            highestRoundFinal = highestRound1;

        return highestRoundFinal;
    }


    /***********************************************************************************************
    ** Conversion methods...
    */

    public static String intToHexString8(int i) {
        try {
            return String.format("%01x", new BigInteger(1, String.valueOf(i).getBytes("UTF-8")));
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }
    public static String intToHexString16(int i) {
        try {
            return String.format("%02x", new BigInteger(1, String.valueOf(i).getBytes("UTF-8")));
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static byte[] stringToByteArray_forPin(String pin) {
        //characteristic.setValue( hexStringToByteArray( intToHexString8(255) ) );
        final String TAGG = "stringToByteArray_forPin: ";
        try {
            return String.valueOf(pin).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                Log.e(TAG, TAGG+"Exception caught trying to getBytes for \""+ String.valueOf(pin)+"\". Returning "+ Arrays.toString(String.valueOf("").getBytes("UTF-8")) +".");
                return String.valueOf("").getBytes("UTF-8");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
                return null;
            }
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        final String TAGG = "hexStringToByteArray: ";
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }
        Log.d(TAG, TAGG+"\""+s+"\" -> "+ Arrays.toString(data));
        return data;
    }
    public static String byteArrayToHexString(byte[] bytes) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String byteToHexString(byte mByte) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[2];
        int v = mByte & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];
        return new String(hexChars);
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
