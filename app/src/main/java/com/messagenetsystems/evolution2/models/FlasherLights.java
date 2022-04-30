package com.messagenetsystems.evolution2.models;

/* FlasherLights
 * This is intended to contain everything you might need to command lights to do stuff.
 *
 * This is actually its own class file in the flasher-lights controller app, and has most likely been
 * copied and pasted into another app that needs to send commands to the controller app. We do it
 * like that, so that intent strings and such are guaranteed to match as needed.
 *
 * !!! WARNING !!!
 *  If you make any changes to this class, you must be especially careful to update the
 *  corresponding class class file in the companion app as well!
 *
 * How to use it...
 *  First, you should load the appropriate command codes for your platform (MNS, API, etc.), by simply instantiating this class.
 *  You simply do that by creating an instance of this class, while passing in the desired PLATFORM_* constant provided below.
 *  Then to actually send light commands, it's easiest to just invoke the static broadcastLightCommand method.
 *
 *  NOTE:
 *  Do not confuse this class and its scope with actually sending the command to the light hardware! They are different!
 *
 * Revisions:
 *  2020.06.01-03   Chris Rider     Created.
 *  2020.06.16      Chris Rider     Added message UUID support.
 */

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FlasherLights {
    private final static String TAG = FlasherLights.class.getSimpleName();

    public static final String CONTROLLER_APP_PACKAGE_NAME = "com.messagenetsystems.evolutionflasherlights";

    public static final byte PLATFORM_UNKNOWN = 0;
    public static final byte PLATFORM_MNS = 1;
    public static final byte PLATFORM_API = 2;

    public static final byte CMD_UNKNOWN = 0;
    public static final byte CMD_LIGHT = 1;

    public static volatile byte currentlyActiveLightCommand = CMD_UNKNOWN;


    /** OmniCommandCodes subclass
     * These are internal/generalized Omni light command codes...
     * They unify MessageNet- and API-provided raw light values, into a common set of commands.
     * These will need to eventually get translated to a byte-array for the eventual characteristic-write to the BLE device!
     *
     * NOTE: You MUST instantiate this class to use it!
     * That's because instantiation is what populates the values, depending on the ecosystem/platform being used.
     * When instantiation occurs, the Omni-standard command codes will be loaded automatically depending on the platform being used, as specified during instantiation.
     * Since commands are stored as byte primitives (to keep memory usage low), this class supports 256 possible commands (valued -127 to 127).
     */
    public static class OmniCommandCodes {
        final String TAG = "OmniCommandCodes: ";

        public byte CMD_UNKNOWN;

        public byte CMD_LIGHT_NONE;
        public byte CMD_LIGHT_OFF;
        public byte CMD_LIGHT_STANDBY;
        public byte CMD_LIGHT_BLUE_DIM;
        public byte CMD_LIGHT_BLUE_MED;
        public byte CMD_LIGHT_BLUE_BRI;
        public byte CMD_LIGHT_GREEN_DIM;
        public byte CMD_LIGHT_GREEN_MED;
        public byte CMD_LIGHT_GREEN_BRI;
        public byte CMD_LIGHT_ORANGE_DIM;
        public byte CMD_LIGHT_ORANGE_MED;
        public byte CMD_LIGHT_ORANGE_BRI;
        public byte CMD_LIGHT_PINK_DIM;
        public byte CMD_LIGHT_PINK_MED;
        public byte CMD_LIGHT_PINK_BRI;
        public byte CMD_LIGHT_PURPLE_DIM;
        public byte CMD_LIGHT_PURPLE_MED;
        public byte CMD_LIGHT_PURPLE_BRI;
        public byte CMD_LIGHT_RED_DIM;
        public byte CMD_LIGHT_RED_MED;
        public byte CMD_LIGHT_RED_BRI;
        public byte CMD_LIGHT_WHITECOOL_DIM;
        public byte CMD_LIGHT_WHITECOOL_MED;
        public byte CMD_LIGHT_WHITECOOL_BRI;
        public byte CMD_LIGHT_WHITEPURE_DIM;
        public byte CMD_LIGHT_WHITEPURE_MED;
        public byte CMD_LIGHT_WHITEPURE_BRI;
        public byte CMD_LIGHT_WHITEWARM_DIM;
        public byte CMD_LIGHT_WHITEWARM_MED;
        public byte CMD_LIGHT_WHITEWARM_BRI;
        public byte CMD_LIGHT_YELLOW_DIM;
        public byte CMD_LIGHT_YELLOW_MED;
        public byte CMD_LIGHT_YELLOW_BRI;
        public byte CMD_LIGHT_FADING_BLUE;
        public byte CMD_LIGHT_FADING_GREEN;
        public byte CMD_LIGHT_FADING_ORANGE;
        public byte CMD_LIGHT_FADING_PINK;
        public byte CMD_LIGHT_FADING_PURPLE;
        public byte CMD_LIGHT_FADING_RED;
        public byte CMD_LIGHT_FADING_WHITECOOL;
        public byte CMD_LIGHT_FADING_WHITEPURE;
        public byte CMD_LIGHT_FADING_WHITEWARM;
        public byte CMD_LIGHT_FADING_YELLOW;
        public byte CMD_LIGHT_FLASHING_BLUE;
        public byte CMD_LIGHT_FLASHING_GREEN;
        public byte CMD_LIGHT_FLASHING_ORANGE;
        public byte CMD_LIGHT_FLASHING_PINK;
        public byte CMD_LIGHT_FLASHING_PURPLE;
        public byte CMD_LIGHT_FLASHING_RED;
        public byte CMD_LIGHT_FLASHING_WHITECOOL;
        public byte CMD_LIGHT_FLASHING_WHITEPURE;
        public byte CMD_LIGHT_FLASHING_WHITEWARM;
        public byte CMD_LIGHT_FLASHING_YELLOW;

        /** Constructor
         * @param platform Platform that we should use (in FlasherLights constants).
         */
        public OmniCommandCodes(byte platform) {
            loadCodes(platform);
        }

        /** Load/initialize all the codes for whichever platform we're using. */
        private void loadCodes(byte platform) {
            this.CMD_UNKNOWN = FlasherLights.CMD_UNKNOWN;
            
            switch (platform) {
                case PLATFORM_MNS:
                    this.CMD_LIGHT_NONE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_NONE);
                    this.CMD_LIGHT_OFF = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_OFF);
                    this.CMD_LIGHT_STANDBY = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_STANDBY);
                    this.CMD_LIGHT_BLUE_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_BLUE_DIM);
                    this.CMD_LIGHT_BLUE_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_BLUE_MED);
                    this.CMD_LIGHT_BLUE_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_BLUE_BRI);
                    this.CMD_LIGHT_GREEN_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_GREEN_DIM);
                    this.CMD_LIGHT_GREEN_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_GREEN_MED);
                    this.CMD_LIGHT_GREEN_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_GREEN_BRI);
                    this.CMD_LIGHT_ORANGE_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_ORANGE_DIM);
                    this.CMD_LIGHT_ORANGE_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_ORANGE_MED);
                    this.CMD_LIGHT_ORANGE_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_ORANGE_BRI);
                    this.CMD_LIGHT_PINK_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_PINK_DIM);
                    this.CMD_LIGHT_PINK_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_PINK_MED);
                    this.CMD_LIGHT_PINK_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_PINK_BRI);
                    this.CMD_LIGHT_PURPLE_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_PURPLE_DIM);
                    this.CMD_LIGHT_PURPLE_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_PURPLE_MED);
                    this.CMD_LIGHT_PURPLE_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_PURPLE_BRI);
                    this.CMD_LIGHT_RED_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_RED_DIM);
                    this.CMD_LIGHT_RED_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_RED_MED);
                    this.CMD_LIGHT_RED_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_RED_BRI);
                    this.CMD_LIGHT_WHITECOOL_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITECOOL_DIM);
                    this.CMD_LIGHT_WHITECOOL_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITECOOL_MED);
                    this.CMD_LIGHT_WHITECOOL_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITECOOL_BRI);
                    this.CMD_LIGHT_WHITEPURE_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITEPURE_DIM);
                    this.CMD_LIGHT_WHITEPURE_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITEPURE_MED);
                    this.CMD_LIGHT_WHITEPURE_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITEPURE_BRI);
                    this.CMD_LIGHT_WHITEWARM_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITEWARM_DIM);
                    this.CMD_LIGHT_WHITEWARM_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITEWARM_MED);
                    this.CMD_LIGHT_WHITEWARM_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_WHITEWARM_BRI);
                    this.CMD_LIGHT_YELLOW_DIM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_YELLOW_DIM);
                    this.CMD_LIGHT_YELLOW_MED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_YELLOW_MED);
                    this.CMD_LIGHT_YELLOW_BRI = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_YELLOW_BRI);
                    this.CMD_LIGHT_FADING_BLUE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_BLUE);
                    this.CMD_LIGHT_FADING_GREEN = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_GREEN);
                    this.CMD_LIGHT_FADING_ORANGE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_ORANGE);
                    this.CMD_LIGHT_FADING_PINK = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_PINK);
                    this.CMD_LIGHT_FADING_PURPLE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_PURPLE);
                    this.CMD_LIGHT_FADING_RED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_RED);
                    this.CMD_LIGHT_FADING_WHITECOOL = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_WHITECOOL);
                    this.CMD_LIGHT_FADING_WHITEPURE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_WHITEPURE);
                    this.CMD_LIGHT_FADING_WHITEWARM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_WHITEWARM);
                    this.CMD_LIGHT_FADING_YELLOW = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FADING_YELLOW);
                    this.CMD_LIGHT_FLASHING_BLUE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_BLUE);
                    this.CMD_LIGHT_FLASHING_GREEN = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_GREEN);
                    this.CMD_LIGHT_FLASHING_ORANGE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_ORANGE);
                    this.CMD_LIGHT_FLASHING_PINK = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_PINK);
                    this.CMD_LIGHT_FLASHING_PURPLE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_PURPLE);
                    this.CMD_LIGHT_FLASHING_RED = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_RED);
                    this.CMD_LIGHT_FLASHING_WHITECOOL = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_WHITECOOL);
                    this.CMD_LIGHT_FLASHING_WHITEPURE = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_WHITEPURE);
                    this.CMD_LIGHT_FLASHING_WHITEWARM = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_WHITEWARM);
                    this.CMD_LIGHT_FLASHING_YELLOW = asciiToDecByte(MessageNetSystems.SIGNALLIGHT_CMD_CHAR_FLASHING_YELLOW);
                    break;
                case PLATFORM_API:
                default:
                    this.CMD_LIGHT_NONE = CommandsAPI.CMD_LIGHT_NONE;
                    this.CMD_LIGHT_OFF = CommandsAPI.CMD_LIGHT_OFF;
                    this.CMD_LIGHT_STANDBY = CommandsAPI.CMD_LIGHT_STANDBY;
                    this.CMD_LIGHT_BLUE_DIM = CommandsAPI.CMD_LIGHT_BLUE_DIM;
                    this.CMD_LIGHT_BLUE_MED = CommandsAPI.CMD_LIGHT_BLUE_MED;
                    this.CMD_LIGHT_BLUE_BRI = CommandsAPI.CMD_LIGHT_BLUE_BRI;
                    this.CMD_LIGHT_GREEN_DIM = CommandsAPI.CMD_LIGHT_GREEN_DIM;
                    this.CMD_LIGHT_GREEN_MED = CommandsAPI.CMD_LIGHT_GREEN_MED;
                    this.CMD_LIGHT_GREEN_BRI = CommandsAPI.CMD_LIGHT_GREEN_BRI;
                    this.CMD_LIGHT_ORANGE_DIM = CommandsAPI.CMD_LIGHT_ORANGE_DIM;
                    this.CMD_LIGHT_ORANGE_MED = CommandsAPI.CMD_LIGHT_ORANGE_MED;
                    this.CMD_LIGHT_ORANGE_BRI = CommandsAPI.CMD_LIGHT_ORANGE_BRI;
                    this.CMD_LIGHT_PINK_DIM = CommandsAPI.CMD_LIGHT_PINK_DIM;
                    this.CMD_LIGHT_PINK_MED = CommandsAPI.CMD_LIGHT_PINK_MED;
                    this.CMD_LIGHT_PINK_BRI = CommandsAPI.CMD_LIGHT_PINK_BRI;
                    this.CMD_LIGHT_PURPLE_DIM = CommandsAPI.CMD_LIGHT_PURPLE_DIM;
                    this.CMD_LIGHT_PURPLE_MED = CommandsAPI.CMD_LIGHT_PURPLE_MED;
                    this.CMD_LIGHT_PURPLE_BRI = CommandsAPI.CMD_LIGHT_PURPLE_BRI;
                    this.CMD_LIGHT_RED_DIM = CommandsAPI.CMD_LIGHT_RED_DIM;
                    this.CMD_LIGHT_RED_MED = CommandsAPI.CMD_LIGHT_RED_MED;
                    this.CMD_LIGHT_RED_BRI = CommandsAPI.CMD_LIGHT_RED_BRI;
                    this.CMD_LIGHT_WHITECOOL_DIM = CommandsAPI.CMD_LIGHT_WHITECOOL_DIM;
                    this.CMD_LIGHT_WHITECOOL_MED = CommandsAPI.CMD_LIGHT_WHITECOOL_MED;
                    this.CMD_LIGHT_WHITECOOL_BRI = CommandsAPI.CMD_LIGHT_WHITECOOL_BRI;
                    this.CMD_LIGHT_WHITEPURE_DIM = CommandsAPI.CMD_LIGHT_WHITEPURE_DIM;
                    this.CMD_LIGHT_WHITEPURE_MED = CommandsAPI.CMD_LIGHT_WHITEPURE_MED;
                    this.CMD_LIGHT_WHITEPURE_BRI = CommandsAPI.CMD_LIGHT_WHITEPURE_BRI;
                    this.CMD_LIGHT_WHITEWARM_DIM = CommandsAPI.CMD_LIGHT_WHITEWARM_DIM;
                    this.CMD_LIGHT_WHITEWARM_MED = CommandsAPI.CMD_LIGHT_WHITEWARM_MED;
                    this.CMD_LIGHT_WHITEWARM_BRI = CommandsAPI.CMD_LIGHT_WHITEWARM_BRI;
                    this.CMD_LIGHT_YELLOW_DIM = CommandsAPI.CMD_LIGHT_YELLOW_DIM;
                    this.CMD_LIGHT_YELLOW_MED = CommandsAPI.CMD_LIGHT_YELLOW_MED;
                    this.CMD_LIGHT_YELLOW_BRI = CommandsAPI.CMD_LIGHT_YELLOW_BRI;
                    this.CMD_LIGHT_FADING_BLUE = CommandsAPI.CMD_LIGHT_FADING_BLUE;
                    this.CMD_LIGHT_FADING_GREEN = CommandsAPI.CMD_LIGHT_FADING_GREEN;
                    this.CMD_LIGHT_FADING_ORANGE = CommandsAPI.CMD_LIGHT_FADING_ORANGE;
                    this.CMD_LIGHT_FADING_PINK = CommandsAPI.CMD_LIGHT_FADING_PINK;
                    this.CMD_LIGHT_FADING_PURPLE = CommandsAPI.CMD_LIGHT_FADING_PURPLE;
                    this.CMD_LIGHT_FADING_RED = CommandsAPI.CMD_LIGHT_FADING_RED;
                    this.CMD_LIGHT_FADING_WHITECOOL = CommandsAPI.CMD_LIGHT_FADING_WHITECOOL;
                    this.CMD_LIGHT_FADING_WHITEPURE = CommandsAPI.CMD_LIGHT_FADING_WHITEPURE;
                    this.CMD_LIGHT_FADING_WHITEWARM = CommandsAPI.CMD_LIGHT_FADING_WHITEWARM;
                    this.CMD_LIGHT_FADING_YELLOW = CommandsAPI.CMD_LIGHT_FADING_YELLOW;
                    this.CMD_LIGHT_FLASHING_BLUE = CommandsAPI.CMD_LIGHT_FLASHING_BLUE;
                    this.CMD_LIGHT_FLASHING_GREEN = CommandsAPI.CMD_LIGHT_FLASHING_GREEN;
                    this.CMD_LIGHT_FLASHING_ORANGE = CommandsAPI.CMD_LIGHT_FLASHING_ORANGE;
                    this.CMD_LIGHT_FLASHING_PINK = CommandsAPI.CMD_LIGHT_FLASHING_PINK;
                    this.CMD_LIGHT_FLASHING_PURPLE = CommandsAPI.CMD_LIGHT_FLASHING_PURPLE;
                    this.CMD_LIGHT_FLASHING_RED = CommandsAPI.CMD_LIGHT_FLASHING_RED;
                    this.CMD_LIGHT_FLASHING_WHITECOOL = CommandsAPI.CMD_LIGHT_FLASHING_WHITECOOL;
                    this.CMD_LIGHT_FLASHING_WHITEPURE = CommandsAPI.CMD_LIGHT_FLASHING_WHITEPURE;
                    this.CMD_LIGHT_FLASHING_WHITEWARM = CommandsAPI.CMD_LIGHT_FLASHING_WHITEWARM;
                    this.CMD_LIGHT_FLASHING_YELLOW = CommandsAPI.CMD_LIGHT_FLASHING_YELLOW;
                    break;
            }
        }

        /** Converts the ASCII character to a decimal byte.
         * NOTE: This is simple/possible because ASCII maximum decimal value is 127 (the maximum size of a byte primitive). */
        public static byte asciiToDecByte(char character) {
            return (byte) character;
        }

        /** Specify raw command codes we expect from the MessageNet Systems platform.
         * Those raw command codes are provided to us as simple ASCII characters.
         * MessageNet raw codes are originally defined in the support_signal.h source code file. */
        class MessageNetSystems {
            static final char SIGNALLIGHT_CMD_CHAR_NONE = ' ';
            static final char SIGNALLIGHT_CMD_CHAR_OFF = '!';
            static final char SIGNALLIGHT_CMD_CHAR_STANDBY = '#';
            static final char SIGNALLIGHT_CMD_CHAR_BLUE_DIM = '(';
            static final char SIGNALLIGHT_CMD_CHAR_BLUE_MED = ')';
            static final char SIGNALLIGHT_CMD_CHAR_BLUE_BRI = '*';
            static final char SIGNALLIGHT_CMD_CHAR_GREEN_DIM = '+';
            static final char SIGNALLIGHT_CMD_CHAR_GREEN_MED = ',';
            static final char SIGNALLIGHT_CMD_CHAR_GREEN_BRI = '-';
            static final char SIGNALLIGHT_CMD_CHAR_ORANGE_DIM = '.';
            static final char SIGNALLIGHT_CMD_CHAR_ORANGE_MED = '/';
            static final char SIGNALLIGHT_CMD_CHAR_ORANGE_BRI = '0';
            static final char SIGNALLIGHT_CMD_CHAR_PINK_DIM = '1';
            static final char SIGNALLIGHT_CMD_CHAR_PINK_MED = '2';
            static final char SIGNALLIGHT_CMD_CHAR_PINK_BRI = '3';
            static final char SIGNALLIGHT_CMD_CHAR_PURPLE_DIM = '4';
            static final char SIGNALLIGHT_CMD_CHAR_PURPLE_MED = '5';
            static final char SIGNALLIGHT_CMD_CHAR_PURPLE_BRI = '6';
            static final char SIGNALLIGHT_CMD_CHAR_RED_DIM = '7';
            static final char SIGNALLIGHT_CMD_CHAR_RED_MED = '8';
            static final char SIGNALLIGHT_CMD_CHAR_RED_BRI = '9';
            static final char SIGNALLIGHT_CMD_CHAR_WHITECOOL_DIM = ':';
            static final char SIGNALLIGHT_CMD_CHAR_WHITECOOL_MED = ';';
            static final char SIGNALLIGHT_CMD_CHAR_WHITECOOL_BRI = '?';
            static final char SIGNALLIGHT_CMD_CHAR_WHITEPURE_DIM = '@';
            static final char SIGNALLIGHT_CMD_CHAR_WHITEPURE_MED = 'A';
            static final char SIGNALLIGHT_CMD_CHAR_WHITEPURE_BRI = 'B';
            static final char SIGNALLIGHT_CMD_CHAR_WHITEWARM_DIM = 'C';
            static final char SIGNALLIGHT_CMD_CHAR_WHITEWARM_MED = 'D';
            static final char SIGNALLIGHT_CMD_CHAR_WHITEWARM_BRI = 'E';
            static final char SIGNALLIGHT_CMD_CHAR_YELLOW_DIM = 'F';
            static final char SIGNALLIGHT_CMD_CHAR_YELLOW_MED = 'G';
            static final char SIGNALLIGHT_CMD_CHAR_YELLOW_BRI = 'H';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_BLUE = 'U';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_GREEN = 'V';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_ORANGE = 'W';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_PINK = 'X';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_PURPLE = 'Y';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_RED = 'Z';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_WHITECOOL = '[';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_WHITEPURE = '\\';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_WHITEWARM = ']';
            static final char SIGNALLIGHT_CMD_CHAR_FADING_YELLOW = '^';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_BLUE = 'd';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_GREEN = 'e';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_ORANGE = 'f';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_PINK = 'g';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_PURPLE = 'h';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_RED = 'i';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_WHITECOOL = 'j';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_WHITEPURE = 'k';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_WHITEWARM = 'l';
            static final char SIGNALLIGHT_CMD_CHAR_FLASHING_YELLOW = 'm';
        }

        class CommandsAPI {
            static final int CMD_LIGHT_NONE = 0;
            static final int CMD_LIGHT_OFF = 1;
            static final int CMD_LIGHT_STANDBY = 2;
            static final int CMD_LIGHT_BLUE_DIM = 3;
            static final int CMD_LIGHT_BLUE_MED = 4;
            static final int CMD_LIGHT_BLUE_BRI = 5;
            static final int CMD_LIGHT_GREEN_DIM = 6;
            static final int CMD_LIGHT_GREEN_MED = 7;
            static final int CMD_LIGHT_GREEN_BRI = 8;
            static final int CMD_LIGHT_ORANGE_DIM = 9;
            static final int CMD_LIGHT_ORANGE_MED = 10;
            static final int CMD_LIGHT_ORANGE_BRI = 11;
            static final int CMD_LIGHT_PINK_DIM = 12;
            static final int CMD_LIGHT_PINK_MED = 13;
            static final int CMD_LIGHT_PINK_BRI = 14;
            static final int CMD_LIGHT_PURPLE_DIM = 15;
            static final int CMD_LIGHT_PURPLE_MED = 16;
            static final int CMD_LIGHT_PURPLE_BRI = 17;
            static final int CMD_LIGHT_RED_DIM = 18;
            static final int CMD_LIGHT_RED_MED = 19;
            static final int CMD_LIGHT_RED_BRI = 20;
            static final int CMD_LIGHT_WHITECOOL_DIM = 21;
            static final int CMD_LIGHT_WHITECOOL_MED = 22;
            static final int CMD_LIGHT_WHITECOOL_BRI = 23;
            static final int CMD_LIGHT_WHITEPURE_DIM = 24;
            static final int CMD_LIGHT_WHITEPURE_MED = 25;
            static final int CMD_LIGHT_WHITEPURE_BRI = 26;
            static final int CMD_LIGHT_WHITEWARM_DIM = 27;
            static final int CMD_LIGHT_WHITEWARM_MED = 28;
            static final int CMD_LIGHT_WHITEWARM_BRI = 29;
            static final int CMD_LIGHT_YELLOW_DIM = 30;
            static final int CMD_LIGHT_YELLOW_MED = 31;
            static final int CMD_LIGHT_YELLOW_BRI = 32;
            static final int CMD_LIGHT_FADING_BLUE = 33;
            static final int CMD_LIGHT_FADING_GREEN = 34;
            static final int CMD_LIGHT_FADING_ORANGE = 35;
            static final int CMD_LIGHT_FADING_PINK = 36;
            static final int CMD_LIGHT_FADING_PURPLE = 37;
            static final int CMD_LIGHT_FADING_RED = 38;
            static final int CMD_LIGHT_FADING_WHITECOOL = 39;
            static final int CMD_LIGHT_FADING_WHITEPURE = 40;
            static final int CMD_LIGHT_FADING_WHITEWARM = 41;
            static final int CMD_LIGHT_FADING_YELLOW = 42;
            static final int CMD_LIGHT_FLASHING_BLUE = 43;
            static final int CMD_LIGHT_FLASHING_GREEN = 44;
            static final int CMD_LIGHT_FLASHING_ORANGE = 45;
            static final int CMD_LIGHT_FLASHING_PINK = 46;
            static final int CMD_LIGHT_FLASHING_PURPLE = 47;
            static final int CMD_LIGHT_FLASHING_RED = 48;
            static final int CMD_LIGHT_FLASHING_WHITECOOL = 49;
            static final int CMD_LIGHT_FLASHING_WHITEPURE = 50;
            static final int CMD_LIGHT_FLASHING_WHITEWARM = 51;
            static final int CMD_LIGHT_FLASHING_YELLOW = 52;
        }
    }


    /** Intent-strings subclass
     * NOTE: These are organized here to unify their very special requirement: That the values MUST match what other apps broadcast!
     */
    public static class Intents {

        private static final String PREFIX = CONTROLLER_APP_PACKAGE_NAME + ".intent";

        public class Filters {
            private static final String PREFIX = Intents.PREFIX + ".filter";
            public static final String LIGHTCMD = PREFIX + ".lightCmd";
        }

        public class Actions {
            private static final String PREFIX = Intents.PREFIX + ".action";
            public static final String DO_LIGHT_COMMAND = PREFIX + ".doLightCommand";
            public static final String DO_LIGHT_COMMAND_LEGACY = PREFIX + ".doLegacyLightCommand";
            public static final String POPULATE_LIGHT_CONTROLLER_ASSOCIATION_FILE = PREFIX + ".populateLightControllerAssociationFile";
            public static final String ASSOCIATE_NEAREST_LIGHT_CONTROLLER = PREFIX + ".associateNearestLights";
        }

        public class Extras {
            private static final String PREFIX = Intents.PREFIX + ".extra";

            public class Keys {
                public static final String LIGHT_CMD_PLATFORM = PREFIX + ".lightCmdPlatform";
                public static final String LIGHT_CMD = PREFIX + ".lightCmd";                        //This key's corresponding value is expected to be from one of the appropriate "Commands" subclasses!

                public static final String LIGHT_CMD_PLATFORM_MNS_BANNER = PREFIX + ".lightCmdPlatformMNSBanner";   //The corresponding extras-value is expected to be from the CommandsMNS subclass!

                public static final String LIGHT_CMD_DURATION_S = PREFIX + ".lightCmdDurationS";
                public static final String LIGHT_CMD_MESSAGE_UUID_STR = PREFIX + ".lightCmdMsgUuidStr";
            }
        }
    }


    /** Easy way to broadcast a light command, just invoke statically from anywhere. *
     * @param context Application context
     * @param lightCommand Byte for the light command to broadcast
     * @param lightDurationS Long for number of seconds to deliver light command.
     * @param messageUuidString String representation of the associated message's UUID. Used to track certain things (like duration).
     * @return Best guess whether broadcast occurred or not
     */
    public static boolean broadcastLightCommand(Context context, byte lightCommand, long lightDurationS, String messageUuidString) {
        final String TAGG = "broadcastLightCommand: ";
        boolean ret;

        try {
            Intent myIntent = new Intent(Intents.Filters.LIGHTCMD);
            myIntent.setAction(Intents.Actions.DO_LIGHT_COMMAND);
            myIntent.putExtra(Intents.Extras.Keys.LIGHT_CMD, lightCommand);
            myIntent.putExtra(Intents.Extras.Keys.LIGHT_CMD_DURATION_S, lightDurationS);
            myIntent.putExtra(Intents.Extras.Keys.LIGHT_CMD_MESSAGE_UUID_STR, messageUuidString);
            Log.v(TAG, TAGG+"Broadcasting light command: "+ Byte.toString(lightCommand) + ", "+ Long.toString(lightDurationS)+"seconds, msg "+ String.valueOf(messageUuidString));
            context.sendBroadcast(myIntent);
            ret = true;
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        return ret;
    }
    public static boolean broadcastLightCommand(Context context, byte lightCommand) {
        Log.w(TAG, "broadcastLightCommand: No extra arguments provided, unable to provide certain features (like duration tracking, etc.).");
        return broadcastLightCommand(context, lightCommand, Integer.MAX_VALUE, null);
    }


    /** Easy way to broadcast a MessageNet legacy light command, just invoke statically from anywhere. *
     * @param context Application context
     * @param dbb_light_signal Raw BannerMessage dbb_light_signal string value
     * @param lightDurationS Long for number of seconds to deliver light command.
     * @return Best guess whether broadcast occurred or not
     */
    public static boolean broadcastLegacyLightCommand(Context context, String dbb_light_signal, long lightDurationS, String messageUuidString) {
        final String TAGG = "broadcastLegacyLightCommand: ";
        boolean ret;

        try {
            Intent myIntent = new Intent(Intents.Filters.LIGHTCMD);
            myIntent.setAction(Intents.Actions.DO_LIGHT_COMMAND_LEGACY);
            myIntent.putExtra(Intents.Extras.Keys.LIGHT_CMD, dbb_light_signal);
            myIntent.putExtra(Intents.Extras.Keys.LIGHT_CMD_DURATION_S, lightDurationS);
            Log.v(TAG, TAGG+"Broadcasting legacy light command: \""+dbb_light_signal+"\""+ ", "+ Long.toString(lightDurationS)+"seconds, msg "+ String.valueOf(messageUuidString));
            context.sendBroadcast(myIntent);
            ret = true;
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        return ret;
    }
    public static boolean broadcastLegacyLightCommand(Context context, String dbb_light_signal) {
        return broadcastLegacyLightCommand(context, dbb_light_signal, Integer.MAX_VALUE, null);
    }


    /** Determine with light-command byte equates to the provided flasher values from the OmniMessage
     * @param flasherLightOmniCommandCodes Instance of FlasherLights.OmniCommandCodes
     * @param flasherMode OmniMessage.flasherMode value
     * @param flasherBrightness OmniMessage.flasherBrightness value
     * @param flasherColor OmniMessage.flasherColor value
     * @return Light-command byte for the provided values
     */
    /*  NOTE: May not be needed for now, until Open-API version is developed...
    public static byte determineLightCommandFromOmniMessage(FlasherLights.OmniCommandCodes flasherLightOmniCommandCodes, int flasherMode, int flasherBrightness, int flasherColor) {
        final String TAGG = "determineLightCommandFromOmniMessage: ";
        byte ret = flasherLightOmniCommandCodes.CMD_UNKNOWN;

        try {

            Log.w(TAG, TAGG+"TODO!"); //TODO!

        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = flasherLightOmniCommandCodes.CMD_UNKNOWN;
        }

        return ret;
    }
    */


    /** Determine which light-command byte equates to the provided legacy BannerMessage value.
     * (that is actually the raw character sent by a MessageNet server as part of the message record)
     * @param flasherLightOmniCommandCodes Instance of FlasherLights.OmniCommandCodes
     * @param dbb_light_signal The raw character/string sent by the MessageNet server for the message
     * @return Light-command byte for the provided legacy value
     */
    /*  NOTE: May not be needed for now, as the flasher lights app's old code translates this for us -- certainly clean up and improve later when time allows, though
    public static byte determineLightCommandFromBannerMessage(FlasherLights.OmniCommandCodes flasherLightOmniCommandCodes, String dbb_light_signal) {
        final String TAGG = "determineLightCommandFromBannerMessage: ";
        byte ret = flasherLightOmniCommandCodes.CMD_UNKNOWN;

        try {

        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = flasherLightOmniCommandCodes.CMD_UNKNOWN;
        }

        return ret;
    }
    */
}
