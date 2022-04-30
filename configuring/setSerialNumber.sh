#!/bin/bash
############################################################################
# Script for setting the serial number of a tablet.
# Tablet must be connected via USB to the machine running this script.
# Machine running this script must have adb (Android Debugging Bridge).
#
# Revision History:
# 2018.07.26	Chris Rider	Creation. Tested to work with Multigold 13"
############################################################################

SERIAL_DATE_PORTION="$(date +"%Y%m%d")"

printf "**************************************************************\n"
printf " MessageNet Evolution Tablet Serial Number Setup\n"
printf " This tool sets the serial number for the connected tablet.\n"

if [ "$(which adb)" == "" ]; then
	printf "\n"
	printf " ERROR! Android debugging bridge program (adb) not found.\n\n"
	exit 0;
fi

ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1)"
if [ "$ADB_CONNECTED_DEVICE" == "" ]; then
	printf "\n"
	printf " ERROR! No device detected or connected.\n"
	printf " Try reconnecting tablet, enabling USB debugging, or killing adb server.\n\n"
	exit 0;
fi

printf "\n"
printf " Connected device is:\n   "
echo $ADB_CONNECTED_DEVICE
printf "\n"
printf " Is that correct? (y/n)"
read -n 1 VERIFY_DEVICE
printf "\n"
if [ "$VERIFY_DEVICE" == "n" ]; then
	printf "\n"
	exit 0;
fi

printf "\n"
printf " Serial number should be formatted like \"YYYYMMDDXXXX\"\n"
printf "   YYYY = Full year (ex. %s)\n" $(date +"%Y")
printf "   MM = Month (ex. %s)\n" $(date +"%d")
printf "   DD = Date (ex. %s)\n" $(date +"%m")
printf "   XXXX = Nth unit provisioned this date (ex. first is 0001)\n"

printf "\n"
printf " (serial number format example:  %s0001)\n" $SERIAL_DATE_PORTION
printf " Enter desired serial number --> "
read DESIRED_SERIAL

printf " You entered: %s\n" $DESIRED_SERIAL
printf "\n"
printf " Is that correct? (y/n)"
read -n 1 VERIFY_SERIAL
printf "\n"
if [ "$VERIFY_SERIAL" == "n" ]; then
	printf "\n"
	exit 0;
fi

printf "\n"
printf " Writing serial number to device...\n"
printf "   "
adb root
sleep 3

printf "   "
adb remount
sleep 3

printf "   "
adb shell "echo \"ro.serialno=$DESIRED_SERIAL\" >> /system/build.prop"
sleep 2

printf "rebooting tablet"
adb reboot

printf "\n\n"
