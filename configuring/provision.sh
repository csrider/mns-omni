#!/bin/bash
############################################################################
# Script for provisioning a tablet.
# Tablet must be connected via USB to the machine running this script.
# Machine running this script must have adb (Android Debugging Bridge).
#
# NOTE:
# "Customer information" is information shared across multiple Omni devices.
# It may include WiFi SSID and such, to make error-prone repeated data entry
# unnecessary when provisioning multiple devices for a single customer.
#
# USAGE:
# (IMPORTANT: If you use arguments, they must be exactly structured as below)
#	./provision.sh
#	./provision.sh --customerinfo [file]
#
# TO-DO!
# - Add hostname assignment? Is this still needed to support DHCP (since the Omni is now doing it by reporting to the server what IP it is)
# 
#
# OLD...
# (USED FOR (redacted) PROVISIONING 2019-01-16):
#	./provision.sh factoryreset
#	./provision.sh provision yes dhcp "WPSD WIFI" WPA 88D02464C0 yes dhcp 10.200.0.12
#
# Revision History:
#	ORIGINAL...
# 	2018.08.23	Chris Rider	Creation. Tested to work with Multigold 13"
# 	2019.01.04	Chris Rider	Added support for OPEN Wi-Fi configurations.
# 	2019.01.10	Chris Rider	Added installation and config of NTP client.
# 	2019.01.16	Chris Rider	Final tweaks for provisioning first customer.
#					Adding arguments capability.
#	NEW...
#	2019.01.17	Chris Rider	Refactoring efforts begin.
#	2019.02.08	Chris Rider	Refactoring efforts mostly end.
#	2019.02.22-26	Chris Rider	Adding runtime stuff to govern automatic update downloading and installation, as well as shared-prefs updating from xml file.
#	2019.03.27	Chris Rider	Added camera hardware information save to file, so gstreamer RTSP server component can use it to be cross-tablet compatible.
#	2019.04.10	Chris Rider	Added installation of CamON RTSP camera server app.
#	2019.05.02	Chris Rider	Added proper fix for safe-volume-warning disablement by adding a line for "audio.safemedia.bypass=true".
#	2019.10.14	Chris Rider	Added app-restart flags stuff.
#	2019.10.29	Chris Rider	Added runtime flag for debugging info and disallow RequestActiveMessages.
#
############################################################################

# Temporary locations to build the files out before pushing to device
PROV_DATA_FILE="/tmp/evoProvisionData.xml"	#will get pushed/installed to device
PROV_DATA_FILE_NAME="evoProvisionData.xml"	
RUNTIME_FLAGS_FILE="/tmp/evoRuntimeFlagsFile"	#will get pushed/installed to device
RUNTIME_FLAGS_FILE_NAME="evoRuntimeFlagsFile"
CAMERA_INFO_FILE="/tmp/evoCameraInfoFile"	#will get pushed/installed to device
CAMERA_INFO_FILE_NAME="evoCameraInfoFile"
THISOMNI_PROBLEMLOG_PROV_TEMPFILE_WHOLE="/tmp/omniProblems.log"

# Define runtime flags' names
# Note: It's important these stay consistent with strings.xml values and code in apps!
RUNTIME_FLAG_NAME_PERIODIC_APP_RESTART_DISALLOW="PERIODIC_APP_RESTART_DISALLOW"
RUNTIME_FLAG_NAME_PERIODIC_APP_RESTART_WINDOW_START="PERIODIC_APP_RESTART_WINDOW_START"		#not yet implemented
RUNTIME_FLAG_NAME_PERIODIC_APP_RESTART_WINDOW_END="PERIODIC_APP_RESTART_WINDOW_END"		#not yet implemented
RUNTIME_FLAG_NAME_REBOOT_DISALLOW_ALL_APPS="REBOOT_DISALLOW_ALL_APPS"
RUNTIME_FLAG_NAME_WIFI_RECONNECT_DISALLOW="WIFI_RECONNECT_DISALLOW"
RUNTIME_FLAG_NAME_MAINAPP_START_SERVICE_DISALLOW="MAINAPP_START_SERVICE_DISALLOW"
RUNTIME_FLAG_NAME_UPDATE_DOWNLOAD_WINDOW_START="UPDATE_DOWNLOAD_WINDOW_START"
RUNTIME_FLAG_NAME_UPDATE_DOWNLOAD_WINDOW_END="UPDATE_DOWNLOAD_WINDOW_END"
RUNTIME_FLAG_NAME_UPDATE_INSTALL_WINDOW_START="UPDATE_INSTALL_WINDOW_START"
RUNTIME_FLAG_NAME_UPDATE_INSTALL_WINDOW_END="UPDATE_INSTALL_WINDOW_END"
RUNTIME_FLAG_NAME_UPDATE_DOWNLOAD_DISALLOW="UPDATE_DOWNLOAD_DISALLOW"
RUNTIME_FLAG_NAME_UPDATE_INSTALL_DISALLOW="UPDATE_INSTALL_DISALLOW"
RUNTIME_FLAG_NAME_DEBUG_SHOW_INFO="DEBUG_SHOW_INFO"
RUNTIME_FLAG_NAME_REQUEST_ACTIVE_MSGS_DISALLOW="REQUEST_ACTIVE_MSGS_DISALLOW"
CAMINFO_VAR_NAME_NUM_OF_CAMS="CAMINFO_NUM_OF_CAMS"
CAMINFO_VAR_NAME_FRONT_CAM_ID="CAMINFO_FRONT_CAM_ID"

# Init variables...
ARG_1A=$1
ARG_1B=$2
PROV_MODE=
PROV_MODE_TESTING="A"
USER_FULL_NAME=
USER_INITIALS=
USER_INITIALS_2=
CUSTOMER_ID=
OMNI_SERIAL_NUMBER=
OMNI_SERIAL_NUMBER_PREV=
OMNI_ARBITRARY_LABEL=
OMNI_ARBITRARY_LABEL_FORFILENAME=
WIFI_USE=0
WIFI_MAC_ADDRESS=
WIFI_MAC_ADDRESS_NOCOLON=
WIFI_SSID=
WIFI_SECURITY_TYPE=
WIFI_PASSWORD=
WIFI_IP_METHOD=
WIFI_IP_ADDRESS=
WIFI_SUBNET=
WIFI_GATEWAY=
WIFI_DNS1=
WIFI_DNS2=
ETHERNET_USE=0
ETHERNET_MAC_ADDRESS=
ETHERNET_MAC_ADDRESS_NOCOLON=
ETHERNET_IP_METHOD=
ETHERNET_IP_ADDRESS=
ETHERNET_SUBNET=
ETHERNET_GATEWAY=
ETHERNET_DNS1=
ETHERNET_DNS2=
CONNECTIONS_SERVER_ADDRESS=
CUSTOMER_INFO_FILE_PATH=
CUSTOMER_INFO_FILE_NAME=
THISOMNI_INFO_FILE_PATH=
THISOMNI_INFO_FILE_NAME=
DATETIME_RESET_START=
DATETIME_RESET_START_HUMAN=
DATETIME_RESET_START_THIS=
DATETIME_RESET_START_THIS_HUMAN=
DATETIME_PROVISION_START=
DATETIME_PROVISION_START_HUMAN=
DATETIME_PROVISION_START_THIS=
DATETIME_PROVISION_START_THIS_HUMAN=
DATETIME_PROVISION_END_THIS=
DATETIME_PROVISION_END_THIS_HUMAN=
LIGHT_CONTROLLER_MAC_ADDRESS=
DO_ANOTHER_PROVISION=0
ERROR_COUNT_PROV=0
WARNING_COUNT_PROV=0

#
#	Black        0;30     Dark Gray     1;30
#	Red          0;31     Light Red     1;31
#	Green        0;32     Light Green   1;32
#	Brown/Orange 0;33     Yellow        1;33
#	Blue         0;34     Light Blue    1;34
#	Purple       0;35     Light Purple  1;35
#	Cyan         0;36     Light Cyan    1;36
#	Light Gray   0;37     White         1;37
#    .---------- constant part!
#    vvvv vvvv-- the code from above
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
LIGHTGRAY='\033[0;37m'
DARKGRAY='\033[1;30m'
WHITE='\033[1;37m'
YELLOW='\033[1;33m'
ORANGE='\033[0;33m'
NOCOLOR='\033[0m' # No Color
FONT_BOLD=$(tput bold)
FONT_NORMAL=$(tput sgr0)
COLOR_ENTER_PROMPT=$CYAN
COLOR_ERROR=$RED
COLOR_WARNING=$ORANGE
COLOR_ATTENTION=$YELLOW
COLOR_SIDENOTE=$DARKGRAY
COLOR_OK=$GREEN


##################################################################################################
mainRoutine()
	{
	# Print introduction and verification to continue
	printf "${WHITE}"
	printf "===============================================================\n"
	printf "==  MessageNet Omni (Evolution) Setup and Provisioning Tool  ==${NOCOLOR}\n"
	showStartupChoices	
	}

showStartupChoices()
	{
	printf "${WHITE}"
	printf "***************************************************************${NOCOLOR}\n"
	printf " What do you want to do?\n"
	printf "  1) Factory Reset Tablet\n  2) Provision for MessageNet Testing/Burn-in\n  3) Provision for Customer\n  4) Check Provisioning (NOT DEVELOPED YET)\n  5) Get MAC Address\n  6) Quit\n"
	printf "  ${COLOR_ENTER_PROMPT}Enter choice -->  ${NOCOLOR}"; read PURPOSE
	
	printf "\n"
	if [ "$PURPOSE" = "1" ]; then
		printf " ${FONT_BOLD}=== Factory Reset ===${FONT_NORMAL}\n"
		purpose_factoryReset
	elif [ "$PURPOSE" = "2" ]; then
		printf " ${FONT_BOLD}=== Provision for MessageNet Testing/Burn-in ===${FONT_NORMAL}\n"
		PROV_MODE="$PROV_MODE_TESTING"
		purpose_provision "MNS"
	elif [ "$PURPOSE" = "3" ]; then
		printf " ${FONT_BOLD}=== Provision for Customer ===${FONT_NORMAL}\n"
		purpose_provision
	elif [ "$PURPOSE" = "4" ]; then
		printf " ${FONT_BOLD}=== Check Provisioning (DOES NOT WORK YET) ===${FONT_NORMAL}\n"
		#purpose_checkProvisioning
		showStartupChoices
	elif [ "$PURPOSE" = "5" ]; then
		printf " ${FONT_BOLD}=== Get MAC Address ===${FONT_NORMAL}\n"
		purpose_getMacAddress
	elif [ "$PURPOSE" = "6" ]; then
		printf "\n"
		exit 0
	else
		printf " ${FONT_BOLD}=== Invalid choice! ===${FONT_NORMAL}\n"
	fi

	printf "\n"
	showStartupChoices
	}

doExit()
	{
	printf "\n"
	exit 0;
	}

##################################################################################################
purpose_factoryReset()
	{
	# Grab the date-time of the start of overall resetting
	DATETIME_RESET_START="$(date +%Y%m%d-%H%M)"
	DATETIME_RESET_START_HUMAN="$(date)"

	printf " Ensure you have tablet connected to USB and booted.\n"
	printf "${COLOR_ENTER_PROMPT}"; read -p " Press Enter to continue..."; printf "${NOCOLOR}"
	printf "\n"
	printf " Resetting tablet...\n"
	DATETIME_RESET_START_THIS="$(date +%Y%m%d-%H%M)"
	DATETIME_RESET_START_THIS_HUMAN="$(date)"
	
	# Check ADB and connection to tablet
	# (note: these functions may write to screen if they encounter problems; otherwise, they won't)
	adb_checkBinaryExistence
	adb_checkDeviceConnection

	# Enter ADB root mode and remount system partition
	# (note: these functions will write a single status line each, no matter what)
	adb_turnScreenOn
	adb_unlockScreen
	adb_enterRootMode
	adb_remountSystemPartition

	# Reset any existing serial numbers on the tablet
	doTabletTask_clearPreviousSerialNumbers
	
	# Clean out any apps that may need to be removed from system
	doTabletTask_uninstallMessageNetSoftware	#we do this to remove any that may be on /system partition (which factory reset won't clear out)

	# Issue the command to do a factory reset
	printf "  Issuing factory-reset command... "
	adb shell am broadcast -a android.intent.action.MASTER_CLEAR > /dev/null 2>&1; sleep 1
	printf "${COLOR_OK}done${NOCOLOR}\n"

	# Prompt user
	printf "\n"
	printf " Tablet should now be starting a factory reset.\n"
	printf " NOTE: In some cases, you may need to long-press power button a couple times until it completes.\n"
	printf " ${COLOR_ENTER_PROMPT}Do another? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	if [ "$VERIFY_CONTINUE" = "y" ]; then
		printf "\n"
		purpose_factoryReset
	fi
	}

# Uninstall messagenet apps...
# WARNING: You must already have root and system remounted before running this!
doTabletTask_uninstallMessageNetSoftware()
	{
	printf "  Uninstalling MessageNet Software...\n"

	printf "   Uninstalling evolution updater..."
	if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolutionupdater") -eq 1 ]; then 
		sleep 1
		adb shell pm uninstall com.messagenetsystems.evolutionupdater > /dev/null 2>&1
		sleep 2
		if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolutionupdater") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to uninstall com.messagenetsystems.evolutionupdater."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi

	printf "   Uninstalling evolution flasher lights..."
	if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolutionflasherlights") -eq 1 ]; then 
		sleep 1
		adb shell pm uninstall com.messagenetsystems.evolutionflasherlights > /dev/null 2>&1
		sleep 2
		if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolutionflasherlights") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to uninstall com.messagenetsystems.evolutionflasherlights."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi

	printf "   Uninstalling evolution watchdog..."
	if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolutionwatchdog") -eq 1 ]; then 
		sleep 1
		adb shell pm uninstall com.messagenetsystems.evolutionwatchdog > /dev/null 2>&1
		sleep 2
		if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolutionwatchdog") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to uninstall com.messagenetsystems.evolutionwatchdog."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi

	printf "   Uninstalling evolution..."
	if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolution") -eq 1 ]; then 
		sleep 1
		adb shell pm uninstall com.messagenetsystems.evolution > /dev/null 2>&1
		sleep 3
		if [ $(adb shell pm list packages | grep -c "com.messagenetsystems.evolution") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to uninstall com.messagenetsystems.evolution."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi

	if [ $(adb shell ls -l /system/app/ | grep -c "com.messagenetsystems") -gt 0 ]; then
		sleep 1
		printf "   Removing MessageNet apps from /system/app..."
		adb shell rm -rf /system/app/com.messagenetsystems.* > /dev/null 2>&1; printf "."; sleep 1
		if [ $(adb shell ls -l /system/app/ | grep -c "com.messagenetsystems") -eq 0 ]; then printf "${COLOR_OK}"; printf "done.\n"; printf "${NOCOLOR}"; else printf "${COLOR_ERROR}ERROR!${NOCOLOR} Could not remove!\n"; fi; sleep 1
	fi
	if [ $(adb shell ls -l /system/priv-app/ | grep -c "com.messagenetsystems") -gt 0 ]; then
		sleep 1
		printf "   Removing MessageNet apps from /system/priv-app..."
		adb shell rm -rf /system/priv-app/com.messagenetsystems.* > /dev/null 2>&1; printf "."; sleep 1
		if [ $(adb shell ls -l /system/priv-app/ | grep -c "com.messagenetsystems") -eq 0 ]; then printf "${COLOR_OK}"; printf "done.\n"; printf "${NOCOLOR}"; else printf "${COLOR_ERROR}ERROR!${NOCOLOR} Could not remove!\n"; fi; sleep 1
	fi

	RESOURCE_TO_REMOVE="evoProvisionData.xml"		#/sdcard/evoProvisionData.xml
	printf "   Removing $(echo $RESOURCE_TO_REMOVE)..."
	if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 1 ]; then 
		sleep 1
		adb shell rm -f /sdcard/$RESOURCE_TO_REMOVE > /dev/null 2>&1
		sleep 1
		if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to remove $(echo $RESOURCE_TO_REMOVE)."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi
	
	RESOURCE_TO_REMOVE="evoRuntimeFlagsFile"		#/sdcard/evoRuntimeFlagsFile
	printf "   Removing $(echo $RESOURCE_TO_REMOVE)..."
	if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 1 ]; then 
		sleep 1
		adb shell rm -f /sdcard/$RESOURCE_TO_REMOVE > /dev/null 2>&1
		sleep 1
		if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to remove $(echo $RESOURCE_TO_REMOVE)."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi
	
	RESOURCE_TO_REMOVE="packageInstallInfo"			#/sdcard/packageInstallInfo*
	printf "   Removing all $(echo $RESOURCE_TO_REMOVE) files..."
	if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 1 ]; then 
		sleep 1
		adb shell rm -f /sdcard/$RESOURCE_TO_REMOVE* > /dev/null 2>&1
		sleep 1
		if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to remove all $(echo $RESOURCE_TO_REMOVE) files."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi
	
	RESOURCE_TO_REMOVE="speaker-test.mp3"			#/sdcard/speaker-test.mp3
	printf "   Removing $(echo $RESOURCE_TO_REMOVE)..."
	if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 1 ]; then 
		sleep 1
		adb shell rm -f /sdcard/$RESOURCE_TO_REMOVE > /dev/null 2>&1
		sleep 1
		if [ $(adb shell ls -l /sdcard/ | grep -c "$RESOURCE_TO_REMOVE") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to remove $(echo $RESOURCE_TO_REMOVE)."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi
	}

# Uninstall third-party apps...
# WARNING: You must already have root before running this!
doTabletTask_uninstallThirdPartyApps()
	{
	printf "  Uninstalling Third-Party Software...\n"

	printf "   Uninstalling NTP client..."
	if [ $(adb shell pm list packages | grep -c "ru.org.amip.ClockSync") -eq 1 ]; then 
		sleep 1
		adb shell pm uninstall ru.org.amip.ClockSync > /dev/null 2>&1
		sleep 1
		if [ $(adb shell pm list packages | grep -c "ru.org.amip.ClockSync") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERRO}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to uninstall ru.org.amip.ClockSync."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi

	printf "   Uninstalling SSH server..."
	if [ $(adb shell pm list packages | grep -c "org.galexander.sshd") -eq 1 ]; then 
		sleep 1
		adb shell pm uninstall org.galexander.sshd > /dev/null 2>&1
		sleep 1
		if [ $(adb shell pm list packages | grep -c "org.galexander.sshd") -eq 0 ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERRO}WARNING!${NOCOLOR} Could not remove!\n"
			addToProblemLog "WARN" "Failed to uninstall org.galexander.sshd."
		fi
		sleep 1
	else
		printf "${COLOR_OK}Not installed.${NOCOLOR}\n"
	fi
	}

# This function serves to wrap a provisioning session...
# It governs the flow of the session-as-a-whole, including provisioning of multiple tablets.
purpose_provision()
	{
	# Grab the date-time of the start of overall provisioning
	DATETIME_PROVISION_START="$(date +%Y%m%d-%H%M)"
	DATETIME_PROVISION_START_HUMAN="$(date)"

	# Prompt user to enter their information
	promptAndProcessResponse_userInfo

	# Handle customer information
	if [ "$1" = "MNS" ]; then
		promptAndProcessResponse_customerInfo "MNS"
	else
		printf "\n"
		if [ "$ARG_1A" == "--customerinfo" ]; then
			# Load the file specified
			printf " Loading specified customer information file, %s\n" "$ARG_1B"
			source "$ARG_1B"
	
			printf "\n"
			printf "  CUSTOMER_ID=\"%s\"\n" "$CUSTOMER_ID"
			printf "  WIFI_USE=%d (0=false, 1=true)\n" $WIFI_USE
			printf "  WIFI_SSID=\"%s\"\n" "$WIFI_SSID"
			printf "  WIFI_SECURITY_TYPE=\"%s\"\n" "$WIFI_SECURITY_TYPE"
			printf "  WIFI_PASSWORD=\"%s\"\n" "$WIFI_PASSWORD"
			#printf "  WIFI_IP_METHOD=\"%s\"\n" "$WIFI_IP_METHOD"
			#printf "  WIFI_IP_ADDRESS=\"%s\"\n" "$WIFI_IP_ADDRESS"
			#printf "  WIFI_SUBNET=\"%s\"\n" "$WIFI_SUBNET"
			#printf "  WIFI_GATEWAY=\"%s\"\n" "$WIFI_GATEWAY"
			#printf "  WIFI_DNS1=\"%s\"\n" "$WIFI_DNS1"
			#printf "  WIFI_DNS2=\"%s\"\n" "$WIFI_DNS2"
			#printf "  ETHERNET_USE=%d (0=false, 1=true)\n" $ETHERNET_USE
			#printf "  ETHERNET_IP_METHOD=\"%s\"\n" "$ETHERNET_IP_METHOD"
			#printf "  ETHERNET_IP_ADDRESS=\"%s\"\n" "$ETHERNET_IP_ADDRESS"
			#printf "  ETHERNET_SUBNET=\"%s\"\n" "$ETHERNET_SUBNET"
			#printf "  ETHERNET_GATEWAY=\"%s\"\n" "$ETHERNET_GATEWAY"
			#printf "  ETHERNET_DNS1=\"%s\"\n" "$ETHERNET_DNS1"
			#printf "  ETHERNET_DNS2=\"%s\"\n" "$ETHERNET_DNS2"
			printf "  CONNECTIONS_SERVER_ADDRESS=\"%s\"\n" "$CONNECTIONS_SERVER_ADDRESS"
	
			printf "\n"
			printf "  ${COLOR_ENTER_PROMPT}Is everything above correct? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
			if [ "$VERIFY_CONTINUE" = "y" ]; then
				printf "\n"
			else
				printf "\n"
				printf "  ${COLOR_ERROR}ERROR:${NOCOLOR} You need to restart provisioning to re-enter customer data!\n"
				doExit
			fi
		else
			# Prompt user to enter customer information to save
			# This may be re-used if they do multiple tablets (either in RAM for a script run or in a file for later run)
			promptAndProcessResponse_customerInfo
		fi
	fi

	# Prompt user to enter the first tablet info and start provisioning it
	printf "\n"
	if [ "$1" = "MNS" ]; then
		promptAndProcessResponse_tabletInfo "MNS"
	else
		promptAndProcessResponse_tabletInfo
	fi
	printf "\n"

	if [ "$1" = "MNS" ]; then
		doTabletProvisioning "MNS"
	else
		doTabletProvisioning
	fi

	printf "\n"
	printf " ${COLOR_ATTENTION}You may now disconnect tablet's USB!${NOCOLOR}\n"

	# Prompt user whether to provision another tablet
	printf "\n"
	printf " ${COLOR_ENTER_PROMPT}Provision another tablet? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	if [ "$VERIFY_CONTINUE" = "y" ]; then DO_ANOTHER_PROVISION=1; else DO_ANOTHER_PROVISION=0; fi

	while [ $DO_ANOTHER_PROVISION -eq 1 ]; do
		printf "\n"
		if [ "$1" = "MNS" ]; then
			promptAndProcessResponse_tabletInfo "MNS"
		else
			promptAndProcessResponse_tabletInfo
		fi
		printf "\n"

		if [ "$1" = "MNS" ]; then
			doTabletProvisioning "MNS"
		else
			doTabletProvisioning
		fi

		printf "\n"
		printf " ${COLOR_ATTENTION}You may disconnect tablet's USB at any time!${NOCOLOR}\n"

		# Prompt user whether to provision another tablet
		printf "\n"
		printf " ${COLOR_ENTER_PROMPT}Provision another tablet? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "y" ]; then DO_ANOTHER_PROVISION=1; else DO_ANOTHER_PROVISION=0; fi
	done

	# TODO
	# Once script arrives to this point, user has elected to not provision any more tablets
	# Generate/print a provisioning report?
	}

purpose_getMacAddress()
	{
	printf " Ensure you have tablet connected to USB and booted.\n"
	printf "${COLOR_ENTER_PROMPT}"; read -p " Press Enter to continue..."; printf "${NOCOLOR}"
	printf "\n"
	
	# Check ADB and connection to tablet
	# (note: these functions may write to screen if they encounter problems; otherwise, they won't)
	adb_checkBinaryExistence
	adb_checkDeviceConnection

	# Enter ADB root mode
	# (note: these functions will write a single status line each, no matter what)
	#adb_enterRootMode

	printf "\n"

	# Read MAC addresses
	MAC_WIFI="$(adb shell ifconfig -a | grep HWaddr | grep wlan | awk '{printf $5}')"; sleep 1
	MAC_ETH="$(adb shell ifconfig -a | grep HWaddr | grep eth | awk '{printf $5}')"; sleep 1

	# Display Wi-Fi MAC address
	if [ "$MAC_WIFI" = "" ]; then
		printf " Wi-Fi MAC Address:     ${COLOR_ERROR}(not available)${NOCOLOR}\n"
	else
		printf " Wi-Fi MAC Address:     ${COLOR_OK}%s${NOCOLOR}\n" "$MAC_WIFI"
	fi

	if [ "$MAC_ETH" = "" ]; then
		printf " Ethernet MAC Address:  ${COLOR_ERROR}(not available)${NOCOLOR}\n"
	else
		printf " Ethernet MAC Address:  ${COLOR_ERROR}%s${NOCOLOR}\n" "$MAC_ETH"
	fi

	printf " ${COLOR_ENTER_PROMPT}Do another? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	if [ "$VERIFY_CONTINUE" = "y" ]; then
		printf "\n"
		purpose_getMacAddress
	fi
	}

##################################################################################################
# This function generates a temporary file to print problems to as they arise.
# Those can later be concatenated into the provisioning report when all is done.
startTabletProvProblemLogFile()
	{
	printf " Preparing problem log..."
	printf "PROVISIONING PROBLEMS REPORT\n" > "$THISOMNI_PROBLEMLOG_PROV_TEMPFILE_WHOLE"
	if [ -f "$THISOMNI_PROBLEMLOG_PROV_TEMPFILE_WHOLE" ]; then
		printf " ${COLOR_OK}prepared.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
	fi
	}

# This function adds the provided argument to the temporary problem log for later inclusionin provisioning report.
# It also increments the type of counter applicable.
# Both arguments are required!
# $1 = "Type of problem" ("ERROR"|"WARN")
# $2 = "Problem text"
addToProblemLog()
	{
	if [ "$1" == "" ]; then
		printf "(unspecified problem)\n" >> "$THISOMNI_PROBLEMLOG_PROV_TEMPFILE_WHOLE"
		return 0
	elif [ "$1" == "ERROR" ]; then
		(( ERROR_COUNT_PROV++ ))
	elif [ "$1" == "WARN" ]; then
		(( WARNING_COUNT_PROV++ ))
	fi

	printf "%s \t%s \t%s\n" "$(date +"%Y.%m.%d %H:%M:%S")" "$1" "$2" >> "$THISOMNI_PROBLEMLOG_PROV_TEMPFILE_WHOLE"
	}

# This function actually takes all the information we have and does the work on the tablet.
doTabletProvisioning()
	{
	startTabletProvProblemLogFile
	printf "\n"

	printf " Ensure you have tablet connected to USB and booted.\n"
	printf "${COLOR_ENTER_PROMPT}"; read -p " Press Enter to continue..."; printf "${NOCOLOR}"
	printf "\n"
	printf " Provisioning tablet...\n"
	DATETIME_PROVISION_START_THIS="$(date +%Y%m%d-%H%M)"
	DATETIME_PROVISION_START_THIS_HUMAN="$(date)"

	# Check ADB and connection to tablet
	# (note: these functions may write to screen if they encounter problems; otherwise, they won't)
	adb_checkBinaryExistence
	adb_checkDeviceConnection

	# Enter ADB root mode and remount system partition
	# (note: these functions will write a single status line each, no matter what)
	adb_turnScreenOn
	adb_unlockScreen
	adb_enterRootMode
	adb_remountSystemPartition

	# Initialize error counter
	ERROR_COUNT_PROV=0
	WARNING_COUNT_PROV=0

	# Check for possible device problems
	adb_checkRemountedSystemPartitionHealth
	if [ $? -eq 0 ]; then
		(( ERROR_COUNT_PROV++ ))
		printf " ${COLOR_ERROR}POSSIBLE TABLET PROBLEM DETECTED!${NOCOLOR}\n"
		printf "  The /system mount is not utilizing all possible space reported as available by the device's partition.\n"
		printf "  This could indicate bad memory blocks or an invalid firmware image.\n"
		printf "  You probably should NOT use this device!\n"
		printf " ${COLOR_ENTER_PROMPT}Continue with it anyway? (y/n) -->  ${NOCOLOR}" read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "n" ]; then return 0; fi
	fi

	# Make sure to uninstall any previous software
	doTabletTask_uninstallMessageNetSoftware
	doTabletTask_uninstallThirdPartyApps

	# Perform tablet tasks to setup the system
	doTabletTask_disableDisplaySleep
	doTabletTask_disableDisplayLock
	doTabletTask_setSafeVolumeWarningBypass
	doTabletTask_setSerialNumber
	doTabletTask_turnOnBluetooth
	doTabletTask_setAutoTimeAcquisition
	doTabletTask_setTimeToComputer
	doTabletTask_setBootAnimation
	doTabletTask_setWallpaper
	doTabletTask_setLauncherIcons
	doTabletTask_restartLauncher
	doTabletTask_testSpeakers
	doTabletTask_setSystemSounds
	doTabletTask_setExternalMicrophone
	doTabletTask_setWifi
	doTabletTask_setEthernet

	# Prompt user to define this Omni in the test system
	if [ "$PROV_MODE" = "$PROV_MODE_TESTING" ]; then 
		printf "\n"
		if [ $ETHERNET_USE -eq 1 ]; then MAC_FOR_DEFINE="$ETHERNET_MAC_ADDRESS_NOCOLON"; else MAC_FOR_DEFINE="$WIFI_MAC_ADDRESS_NOCOLON"; fi
		printf " ${COLOR_ATTENTION}Now is a good time to define the Omni in preparation for testing!\n"
		printf " You may do so while this script continues to provision the Omni.\n"
		printf "  ${NOCOLOR}MAC address for relevant ext-mgr and HW-mgr fields:  ${WHITE}%s${NOCOLOR}\n" "$MAC_FOR_DEFINE"
		printf "  ${NOCOLOR}Description for relevant ext-mgr and HW-mgr fields:  ${WHITE}%s${NOCOLOR}\n" "$(echo "${OMNI_SERIAL_NUMBER//-/.}")"
		printf " ${COLOR_ENTER_PROMPT}"; read -p "Press Enter to continue provisioning while you define the Omni."; printf "${NOCOLOR}\n"
	fi

	# Perform tablet tasks to install software
	doTabletTask_installSoftware_sshServer
	doTabletTask_installSoftware_ntpClient
	doTabletTask_installSoftware_textToSpeech
	doTabletTask_installSoftware_rtspCameraServer

	# Reboot
	printf "  Tablet must now reboot for the next phase of provisioning. ${COLOR_ATTENTION}(if it hangs, manually reboot it)${NOCOLOR}\n"; sleep 3;
	adb shell su -c "/system/bin/svc power shutdown" > /dev/null 2>&1
	printf "  Waiting for tablet to finish rebooting..."; sleep 3
	ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"
	while [ "$ADB_CONNECTED_DEVICE" = "" ] || [ "$ADB_CONNECTED_DEVICE" = "*" ]; do printf "."; sleep 5; ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"; done; printf " booting"
	MAX_WAIT_ITERATIONS=30
	PROC_RESULT_MODE="dumpsys"
	PROC_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)"
	while [ "$PROC_RESULT" = "0" ]; do
		if [ $MAX_WAIT_ITERATIONS -gt 0 ] && [ "$PROC_RESULT_MODE" = "dumpsys" ]; then
			# use dumpsys to check for launcher
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			sleep 1
			PROC_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)"
		elif [ $MAX_WAIT_ITERATIONS -gt 0 ] && [ "$PROC_RESULT_MODE" = "ps" ]; then
			# could be that we missed the check for launcher3 to be in focus due to another app launching quickly
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			PROC_RESULT_MODE="ps"
			MAX_WAIT_ITERATIONS=15		#give a bit more time
			sleep 1
			PROC_RESULT="$(adb shell ps | grep -c launcher)"
		else
			#ran out of retries, give up
			printf " (giving up checking)"
			break
		fi
	done; printf "${NOCOLOR} done.\n"
	adb_enterRootMode
	adb_remountSystemPartition

	# Perform tablet tasks to install software
	doTabletTask_installSoftware_messagenet_evolution
	doTabletTask_installSoftware_messagenet_evolutionwatchdog
	doTabletTask_installSoftware_messagenet_omniwatchdogwatcher
	doTabletTask_installSoftware_messagenet_evolutionupdater
	
	# Generate and install provisioning information XML file to tablet
	generateProvisioningFile_xml
	doTabletTask_installProvisioningFile

	# Generate and install runtime flags file to tablet
	generateRuntimeFlagsFile
	doTabletTask_installRuntimeFlagFile

	# Generate and install camera info file to tablet
	generateCameraInfoFile
	doTabletTask_installCameraInfoFile

	# Install flasher lights app and configure lights
	# NOTE: Configuration requires main app and provisioning file to already be installed, so it can update its shared-prefs file.
	doTabletTask_installSoftware_messagenet_evolutionflasherlights
	doTabletTask_configure_evolutionflasherlights

	# Reboot
	printf "  Tablet must now reboot for the next phase of provisioning.${COLOR_ENTER_PROMPT}\n"
	read -p "   Press Enter to reboot now."; printf "${NOCOLOR}"
	adb shell su -c "/system/bin/svc power shutdown" > /dev/null 2>&1
	printf "  Waiting for tablet to finish rebooting..."
	ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"
	while [ "$ADB_CONNECTED_DEVICE" = "" ] || [ "$ADB_CONNECTED_DEVICE" = "*" ]; do printf "."; sleep 5; ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"; done; printf " booting"
	MAX_WAIT_ITERATIONS=30
	PROC_RESULT_MODE="dumpsys"
	PROC_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)"
	while [ "$PROC_RESULT" = "0" ]; do
		if [ $MAX_WAIT_ITERATIONS -gt 0 ] && [ "$PROC_RESULT_MODE" = "dumpsys" ]; then
			# use dumpsys to check for launcher
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			sleep 1
			PROC_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)"
		elif [ $MAX_WAIT_ITERATIONS -gt 0 ] && [ "$PROC_RESULT_MODE" = "ps" ]; then
			# could be that we missed the check for launcher3 to be in focus due to another app launching quickly
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			PROC_RESULT_MODE="ps"
			MAX_WAIT_ITERATIONS=15		#give a bit more time
			sleep 1
			PROC_RESULT="$(adb shell ps | grep -c launcher)"
		else
			#ran out of retries, give up
			printf " (giving up checking)"
			break
		fi
	done; printf "${NOCOLOR} done.\n"

	# Wait a bit for flasher lights app to clear the screen
	printf " Please wait..."; for i in {1..6}; do printf "."; sleep 1; done; printf "\n"

	# after first reboot...
	# (any auto-boot apps installed as system will immediately start?)
	# TODO: automate more of this and improve further, get rid of immersive notification
	printf "\n"
	printf " ${COLOR_ATTENTION}Do the following NOW!${NOCOLOR} (in this exact order)...\n"
	printf "   1. Swipe to pull down top notification bar...\n"
	printf "      - Verify \"SimpleSSHD listening\" is there.\n"
	printf "      - Verify \"Omni Update Manager\" is there.\n"
	printf "   2. Manually start the main \"Omni\" app. ${COLOR_ATTENTION}(prepare to watch bottom of screen!!!)\n"
	printf "      WATCH bottom of Omni startup screen, verify \"$LIGHT_CONTROLLER_MAC_ADDRESS\" is associated.${NOCOLOR}\n"
	if [ "$PROV_MODE" = "$PROV_MODE_TESTING" ]; then printf "      Note: If you defined the Omni, it will show the clock and be ready to test.\n"; fi

	# Clear immersive mode notification after main app starts
	printf "\n"
	printf " Waiting (up to 60s) for main app to start..."
	MAX_WAIT_ITERATIONS=30
	while [ "$(adb shell ps | grep -v omniwatchdogwatcher | grep -v evolutionflasherlights | grep -v evolutionwatchdog | grep -v evolutionupdater | grep -c evolution)" = "0" ]; do 
		if [ $MAX_WAIT_ITERATIONS -gt 0 ]; then
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			sleep 2
		else
			printf " ${COLOR_ATTENTION}App not starting.${COLOR_ENTER_PROMPT}\n"
			printf "  Try to start app and wait again? (y/n) -->  "; read VERIFY_CONTINUE; printf "${NOCOLOR}"
			if [ "$VERIFY_CONTINUE" = "y" ]; then
				MAX_WAIT_ITERATIONS=30
				printf " Waiting for main app to start..."
			fi
		fi
	done
	if [ "$(adb shell ps | grep -v omniwatchdogwatcher | grep -v evolutionflasherlights | grep -v evolutionwatchdog | grep -v evolutionupdater | grep -c evolution)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"

		printf " Waiting (up to 60s) for StartupActivity to load ${COLOR_ATTENTION}(watch for \"$LIGHT_CONTROLLER_MAC_ADDRESS\")${NOCOLOR}..."
		MAX_WAIT_ITERATIONS=60
		while [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c "com.messagenetsystems.evolution.StartupActivity")" = "0" ]; do
			if [ $MAX_WAIT_ITERATIONS -gt 0 ]; then
				printf "."
				(( MAX_WAIT_ITERATIONS-- ))
				sleep 1
			else
				printf " ${COLOR_ATTENTION}StartupActivity not loading.${COLOR_ENTER_PROMPT}\n"
				printf "  Wait again? (y/n) -->  "; read VERIFY_CONTINUE; printf "${NOCOLOR}"
				if [ "$VERIFY_CONTINUE" = "y" ]; then
					MAX_WAIT_ITERATIONS=30
				else
					break;
				fi
			fi
		done; printf " ${COLOR_OK}done.${NOCOLOR}\n"

		if [ "$1" = "MNS" ]; then
			printf " Waiting (up to 60s) for immersive mode notification to appear so it can be dismissed..."
			MAX_WAIT_ITERATIONS=30
			while [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c ImmersiveModeConfirmation)" = "0" ]; do
				if [ $MAX_WAIT_ITERATIONS -gt 0 ]; then
					printf "."
					(( MAX_WAIT_ITERATIONS-- ))
					sleep 2
				else
					printf " ${COLOR_ATTENTION}Notification not showing.${COLOR_ENTER_PROMPT}\n"
					printf "  Wait again? (y/n) -->  "; read VERIFY_CONTINUE; printf "${NOCOLOR}"
					if [ "$VERIFY_CONTINUE" = "y" ]; then
						MAX_WAIT_ITERATIONS=30
					else
						break;
					fi
				fi
			done
			printf " ${COLOR_OK}done.${NOCOLOR}\n"

			if [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c ImmersiveModeConfirmation)" = "1" ]; then
				sleep 1
				printf " Trying to automatically dismiss notification..."
				adb shell su -c "input touchscreen tap 1100 320" > /dev/null 2>&1; sleep 1
				if [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c ImmersiveModeConfirmation)" = "1" ]; then
					printf " ${COLOR_ATTENTION}failed! You must manually dismiss the notification!${NOCOLOR}\n"
	
					# Manual dismissal...
					printf " ${COLOR_ATTENTION}Dismiss full-screen notification NOW!${NOCOLOR}(you have 60s to tap OK in the teal window)..."
					MAX_WAIT_ITERATIONS=30
					while [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c ImmersiveModeConfirmation)" = "1" ]; do
						if [ $MAX_WAIT_ITERATIONS -gt 0 ]; then
							printf "."
							(( MAX_WAIT_ITERATIONS-- ))
							sleep 2
						else
							printf " ${COLOR_ATTENTION}Notification not clearing out.${COLOR_ENTER_PROMPT}\n"
							printf "  Wait again? (y/n) -->  "; read VERIFY_CONTINUE; printf "${NOCOLOR}"
							if [ "$VERIFY_CONTINUE" = "y" ]; then
								MAX_WAIT_ITERATIONS=30
								printf " Waiting for immersive mode notification to be dismissed..."
							fi
						fi
					done; printf " ${COLOR_OK}done.${NOCOLOR} (notification should not be visible, or you cancelled waiting)\n"
				else
					printf " ${COLOR_OK}done.${NOCOLOR}\n"
				fi
			fi
		fi
	else
		printf " ${RED}FAILED.${NOCOLOR} You should investigate this. Get an engineer if you need help!\n"
	fi

	# Reboot
	printf "  Tablet must now reboot to verify provisioning is complete.${COLOR_ENTER_PROMPT}\n"
	read -p "   Press Enter to reboot now."; printf "${NOCOLOR}"
	adb shell su -c "/system/bin/svc power shutdown" > /dev/null 2>&1
	printf "  Waiting for tablet to finish rebooting..."
	ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"
	while [ "$ADB_CONNECTED_DEVICE" = "" ] || [ "$ADB_CONNECTED_DEVICE" = "*" ]; do printf "."; sleep 5; ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"; done; printf " booting"
	MAX_WAIT_ITERATIONS=30
	PROC_RESULT_MODE="dumpsys"
	PROC_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)"
	while [ "$PROC_RESULT" = "0" ]; do
		if [ $MAX_WAIT_ITERATIONS -gt 0 ] && [ "$PROC_RESULT_MODE" = "dumpsys" ]; then
			# use dumpsys to check for launcher
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			sleep 1
			PROC_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)"
		elif [ $MAX_WAIT_ITERATIONS -gt 0 ] && [ "$PROC_RESULT_MODE" = "ps" ]; then
			# could be that we missed the check for launcher3 to be in focus due to another app launching quickly
			printf "."
			(( MAX_WAIT_ITERATIONS-- ))
			PROC_RESULT_MODE="ps"
			MAX_WAIT_ITERATIONS=15		#give a bit more time
			sleep 1
			PROC_RESULT="$(adb shell ps | grep -c launcher)"
		else
			#ran out of retries, give up
			printf " (giving up checking)"
			break
		fi
	done; printf "${NOCOLOR} done.\n"

	# Prompt for finish-up
	printf "\n"
	printf " ${COLOR_ATTENTION}Omni is now provisioned.${NOCOLOR}\n"

	# Generate a summary report
	generateSummaryReportAndPrint_tablet

	# Copy report to M-Drive
	copyFileToMdrive "$SUMMARY_REPORT_FILE_WHOLE" "$CUSTOMER_ID"
	}

##################################################################################################
promptAndProcessResponse_arbitraryLabel()
	{
	DESIRED_ARBITRARY_LABEL=""
	printf "  Arbitrary label (optional, usually for testing):\t"; read DESIRED_ARBITRARY_LABEL
	OMNI_ARBITRARY_LABEL="$DESIRED_ARBITRARY_LABEL"
	if [ "$OMNI_ARBITRARY_LABEL" = "" ]; then
		OMNI_ARBITRARY_LABEL_FORFILENAME=""
	else
		OMNI_ARBITRARY_LABEL_FORFILENAME=".$OMNI_ARBITRARY_LABEL"
	fi
	}

#NOTE: serial number delimiter should be '-' hyphen
promptAndProcessResponse_serialNumber()
	{
	DESIRED_SERIAL=""
	SERIAL_DATE_PORTION="$(date +"%y%m%d")"

	if [ "$1" = "MNS" ]; then
		printf "  Auto-Completing Serial Number for MessageNet...\n"
		DESIRED_SERIAL="MNS-$(echo $SERIAL_DATE_PORTION)-$(date +'%H%M')$(echo $USER_INITIALS_2)"	#just a dummy auto-population for now
		printf "   Serial number will be: \"%s\"\n" "$DESIRED_SERIAL"
		printf "   ${COLOR_ENTER_PROMPT}Is that OK? (y/n) ${NOCOLOR}"; read VERIFY_SERIAL
		if [ "$VERIFY_SERIAL" = "y" ]; then
			OMNI_SERIAL_NUMBER="$DESIRED_SERIAL"		#use what the entered as this tablet's serial number
			OMNI_SERIAL_NUMBER_PREV="$OMNI_SERIAL_NUMBER"	#and also remember this tablet's serial number for the next tablet's provisioning run to access and use
		elif [ "$VERIFY_SERIAL" = "n" ]; then
			promptAndProcessResponse_serialNumber
		else
			printf "   ${COLOR_ATTENTION}INVALID!${NOCOLOR} You must type choice (\"y\" or \"n\") exactly. Try again.\n"
			promptAndProcessResponse_serialNumber "MNS"
		fi
	else
		printf "  Serial Number...\n"
		printf "   Format: \"VVSSR-YYMMDD-NNNNII\"\n"
		printf "    R	= (OPTIONAL) Tablet revision code (for different hardware, firmware, etc.)\n"
		printf "    VV	= Vendor code (ex. Multigold = MG)\n"
		printf "    SS	= Size of screen (ex. 13-inch = 13\n"
		printf "    YY  = Year (ex. %s)\n" $(date +"%y")
		printf "    MM 	= Month (ex. %s)\n" $(date +"%d")
		printf "    DD 	= Date (ex. %s)\n" $(date +"%m")
		printf "    NNNN = Nth unit provisioned per date/customer/etc. (ex. first is 0001)\n"
		printf "    II	= Initials of person provisioning the device (ex. %s)\n" "$USER_INITIALS_2"
	
		# If there's no available previous number, they must type one
		# Else they can type one, or automatically increment from previous one
		if [ "$OMNI_SERIAL_NUMBER_PREV" = "" ]; then
			printf "   (format example:  MG13-%s-0001CR)\n" $SERIAL_DATE_PORTION
			printf "  ${COLOR_ENTER_PROMPT}Serial number --> ${NOCOLOR}"; read DESIRED_SERIAL
			if [ "$DESIRED_SERIAL" = "" ]; then
				printf "   ${COLOR_ATTENTION}INVALID! You must provide a serial number. Try again.${NOCOLOR}\n"
				promptAndProcessResponse_serialNumber
			fi
		else
			printf "   (format example for serial number:     MG13-%s-0001CR)\n" $SERIAL_DATE_PORTION
			printf "  ${COLOR_ENTER_PROMPT}Serial number (blank to increment) --> ${NOCOLOR}"; read DESIRED_SERIAL
			if [ "$DESIRED_SERIAL" = "" ]; then
				#TODO: increment serial number
				printf "   ${COLOR_ATTENTION}Auto-increment not developed yet, you must provide a serial number. Try again.${NOCOLOR}\n"
				promptAndProcessResponse_serialNumber
			fi
		fi
		
		# Whatever we have now, force user to verify it
		printf "  You entered: \"%s\"\n" $DESIRED_SERIAL
		printf "   ${COLOR_ATTENTION}(giving you 5s to double-check"; for i in {1..5}; do printf "."; sleep 1; done; printf ")${NOCOLOR}\n"
		printf "  ${COLOR_ENTER_PROMPT}Is that correct? (y/n) ${NOCOLOR}"; read VERIFY_SERIAL
		if [ "$VERIFY_SERIAL" = "y" ]; then
			OMNI_SERIAL_NUMBER="$DESIRED_SERIAL"		#use what the entered as this tablet's serial number
			OMNI_SERIAL_NUMBER_PREV="$OMNI_SERIAL_NUMBER"	#and also remember this tablet's serial number for the next tablet's provisioning run to access and use
		elif [ "$VERIFY_SERIAL" = "n" ]; then
			promptAndProcessResponse_serialNumber
		else
			printf "   ${COLOR_ATTENTION}INVALID! You must type choice (\"y\" or \"n\") exactly. Try again.${NOCOLOR}\n"
			promptAndProcessResponse_serialNumber
		fi
	fi
	}

promptAndProcessResponse_tabletInfo()
	{
	if [ "$1" = "MNS" ]; then
		printf " Omni Tablet Information for MessageNet:\n"
		THISOMNI_INFO_FILE_PATH="~/OmniProvisioning/"
		promptAndProcessResponse_arbitraryLabel
		promptAndProcessResponse_serialNumber "MNS"
	else
		printf " Omni Tablet Information for Customer (%s):\n" "$CUSTOMER_ID"
		printf "  Information File Save Path (enter for default):\t"; read THISOMNI_INFO_FILE_PATH; if [ "$THISOMNI_INFO_FILE_PATH" = "" ]; then THISOMNI_INFO_FILE_PATH="~/OmniProvisioning/"; fi
		promptAndProcessResponse_arbitraryLabel
		promptAndProcessResponse_serialNumber
	fi

	# Generate save file for this Omni
	# This may be used later to configure another tablet in the same manner as this one, or just as reference
	CURRENT_DIR="$(pwd)"						#remember current dir so we can go back to it
	THISOMNI_INFO_FILE_PATH="$(eval echo $THISOMNI_INFO_FILE_PATH)"	#evaluate any special operators (like ~ for user dir)
	mkdir $THISOMNI_INFO_FILE_PATH > /dev/null 2>&1			#make sure the path exists
	cd "$THISOMNI_INFO_FILE_PATH"					#change into the path
	THISOMNI_INFO_FILE_PATH="$(pwd)"				#remember the absolute path of it
	cd "$CURRENT_DIR"						#change back to where we were
	THISOMNI_INFO_FILE_NAME="$(echo $CUSTOMER_ID).omniinfo$(echo $OMNI_ARBITRARY_LABEL_FORFILENAME).$(echo $OMNI_SERIAL_NUMBER).$(echo $DATETIME_PROVISION_START_THIS)"	#construct the filename where we'll save the info
	THISOMNI_INFO_FILE_WHOLE="$(echo $THISOMNI_INFO_FILE_PATH)/$(echo $THISOMNI_INFO_FILE_NAME)"					#construct the complete path and filename
	touch "$THISOMNI_INFO_FILE_WHOLE"				#actually create the file

	printf "   (Omni information will be saved as %s)\n" "$THISOMNI_INFO_FILE_WHOLE"

	# Save Omni information to the file
	# We duplicate the customer info here, as well...
	printf "#/bin/bash\n" > "$THISOMNI_INFO_FILE_WHOLE"
	printf "\n" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "# Provisioning started by %s on %s for %s\n" "$USER_INITIALS" "$DATETIME_PROVISION_START_HUMAN" "$CUSTOMER_ID" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "# Provisioning of this Omni started by %s on %s for %s\n" "$USER_INITIALS" "$DATETIME_PROVISION_START_THIS_HUMAN" "$CUSTOMER_ID" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "\n" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "# Customer Information...\n" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "CUSTOMER_ID=\"%s\"\n" "$CUSTOMER_ID" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "\n" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "# This Omni Information...\n" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "OMNI_SERIAL_NUMBER=\"%s\"\n" "$OMNI_SERIAL_NUMBER" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "OMNI_ARBITRARY_LABEL=\"%s\"\n" "$OMNI_ARBITRARY_LABEL" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_USE=%d\n" $WIFI_USE >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_SSID=\"%s\"\n" "$WIFI_SSID" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_SECURITY_TYPE=\"%s\"\n" "$WIFI_SECURITY_TYPE" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_PASSWORD=\"%s\"\n" "$WIFI_PASSWORD" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_IP_METHOD=\"%s\"\n" "$WIFI_IP_METHOD" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_IP_ADDRESS=\"%s\"\n" "$WIFI_IP_ADDRESS" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_SUBNET=\"%s\"\n" "$WIFI_SUBNET" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_GATEWAY=\"%s\"\n" "$WIFI_GATEWAY" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_DNS1=\"%s\"\n" "$WIFI_DNS1" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "WIFI_DNS2=\"%s\"\n" "$WIFI_DNS2" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_USE=%d\n" $ETHERNET_USE >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_IP_METHOD=\"%s\"\n" "$ETHERNET_IP_METHOD" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_IP_ADDRESS=\"%s\"\n" "$ETHERNET_IP_ADDRESS" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_SUBNET=\"%s\"\n" "$ETHERNET_SUBNET" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_GATEWAY=\"%s\"\n" "$ETHERNET_GATEWAY" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_DNS1=\"%s\"\n" "$ETHERNET_DNS1" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "ETHERNET_DNS2=\"%s\"\n" "$ETHERNET_DNS2" >> "$THISOMNI_INFO_FILE_WHOLE"
	printf "CONNECTIONS_SERVER_ADDRESS=\"%s\"\n" "$CONNECTIONS_SERVER_ADDRESS" >> "$THISOMNI_INFO_FILE_WHOLE"
	}

promptAndProcessResponse_userInfo()
	{
	printf " Who are you?\n"
	printf "  1. Chris Rider\n"
	printf "  2. Jessica Neuner\n"
	printf "  3. HeeLa Yang\n"
	#printf "  4. Sandy Wegner\n"
	#printf "  5. Kevin Brown\n"
	printf "  9. (other)\n"
	printf "  ${COLOR_ENTER_PROMPT}Your choice -->  ${NOCOLOR}"; read VERIFY_CONTINUE; #printf "\n"
	if [ "$VERIFY_CONTINUE" = "1" ]; then 	USER_FULL_NAME="Chris Rider";		USER_INITIALS="CSR";	USER_INITIALS_2="CR"
	elif [ "$VERIFY_CONTINUE" = "2" ]; then	USER_FULL_NAME="Jessica Neuner";  	USER_INITIALS="JLN";	USER_INITIALS_2="JN"
	elif [ "$VERIFY_CONTINUE" = "3" ]; then	USER_FULL_NAME="HeeLa Yang";  		USER_INITIALS="HLY";	USER_INITIALS_2="HY"
	#elif [ "$VERIFY_CONTINUE" = "4" ]; then USER_FULL_NAME="Sandy Wegner";		USER_INITIALS="";	USER_INITIALS_2="SW"
	#elif [ "$VERIFY_CONTINUE" = "5" ]; then USER_FULL_NAME="Kevin Brown";		USER_INITIALS="";	USER_INITIALS_2="KB"
	else
		printf " Enter provisioner information:\n"
		printf "  Your Full Name:\t\t"; read USER_FULL_NAME
		printf "  Your 3 Letter Initials:\t"; read USER_INITIALS
		USER_INITIALS_2="$(echo $USER_INITIALS | head -c1)$(echo $USER_INITIALS | tail -c2)"
		printf "  Your 2 Letter Initials:\t%s\n" "$USER_INITIALS_2"
	fi
	}

promptAndProcessResponse_customerInfo()
	{
	if [ "$1" = "MNS" ]; then
		printf "\n Auto-Completing Customer Info for MessageNet...\n"
		promptAndProcessResponse_useWifi "MNS"
		promptAndProcessResponse_useEthernet "MNS"
		CUSTOMER_ID="MNS"
		CONNECTIONS_SERVER_ADDRESS="192.168.1.58"
		CUSTOMER_INFO_FILE_PATH="~/OmniProvisioning/"
	else
		printf " Enter customer information:\n"
		printf "  ${COLOR_ENTER_PROMPT}Use MessageNet? (y/n) --> ${NOCOLOR}"; read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "y" ]; then
			promptAndProcessResponse_useWifi "MNS"
			promptAndProcessResponse_useEthernet "MNS"
			CUSTOMER_ID="MNS"
			CONNECTIONS_SERVER_ADDRESS="192.168.1.58"
		else
			promptAndProcessResponse_useWifi
			promptAndProcessResponse_useEthernet
			printf "  Customer Name/ID:\t\t"; read CUSTOMER_ID
			printf "  Connections Server:\t\t"; read CONNECTIONS_SERVER_ADDRESS
		fi
		printf "  Custom Info File Path (opt):\t"; read CUSTOMER_INFO_FILE_PATH; if [ "$CUSTOMER_INFO_FILE_PATH" = "" ]; then CUSTOMER_INFO_FILE_PATH="~/OmniProvisioning/"; fi
	fi

	# Generate save file for this customer
	# This may be used later to configure more Omnis, or just as reference
	CURRENT_DIR="$(pwd)"
	CUSTOMER_INFO_FILE_PATH="$(eval echo $CUSTOMER_INFO_FILE_PATH)"	#evaluate any special operators (like ~ for user dir)
	mkdir $CUSTOMER_INFO_FILE_PATH > /dev/null 2>&1	#must use eval or "~" won't evaluate to our user's directory
	cd "$CUSTOMER_INFO_FILE_PATH"			#must use eval or "~" won't evaluate to our user's directory
	CUSTOMER_INFO_FILE_PATH="$(pwd)"
	cd "$CURRENT_DIR"
	CUSTOMER_INFO_FILE_NAME="$(echo $CUSTOMER_ID).custinfo.$(echo $DATETIME_PROVISION_START)"
	CUSTOMER_INFO_FILE_WHOLE="$(echo $CUSTOMER_INFO_FILE_PATH)/$(echo $CUSTOMER_INFO_FILE_NAME)"
	touch "$CUSTOMER_INFO_FILE_WHOLE"

	printf "   (Customer information will be saved as %s)\n" "$CUSTOMER_INFO_FILE_WHOLE"

	# Save customer information to the file
	printf "#/bin/bash\n" > "$CUSTOMER_INFO_FILE_WHOLE"
	printf "\n" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "# Provisioning started by %s on %s for %s\n" "$USER_INITIALS" "$DATETIME_PROVISION_START_HUMAN" "$CUSTOMER_ID" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "\n" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "CUSTOMER_ID=\"%s\"\n" "$CUSTOMER_ID" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_USE=%d\n" $WIFI_USE >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_SSID=\"%s\"\n" "$WIFI_SSID" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_SECURITY_TYPE=\"%s\"\n" "$WIFI_SECURITY_TYPE" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_PASSWORD=\"%s\"\n" "$WIFI_PASSWORD" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_IP_METHOD=\"%s\"\n" "$WIFI_IP_METHOD" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_SUBNET=\"%s\"\n" "$WIFI_SUBNET" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_GATEWAY=\"%s\"\n" "$WIFI_GATEWAY" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_DNS1=\"%s\"\n" "$WIFI_DNS1" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "WIFI_DNS2=\"%s\"\n" "$WIFI_DNS2" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "ETHERNET_USE=%d\n" $ETHERNET_USE >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "ETHERNET_IP_METHOD=\"%s\"\n" "$ETHERNET_IP_METHOD" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "ETHERNET_SUBNET=\"%s\"\n" "$ETHERNET_SUBNET" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "ETHERNET_GATEWAY=\"%s\"\n" "$ETHERNET_GATEWAY" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "ETHERNET_DNS1=\"%s\"\n" "$ETHERNET_DNS1" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "ETHERNET_DNS2=\"%s\"\n" "$ETHERNET_DNS2" >> "$CUSTOMER_INFO_FILE_WHOLE"
	printf "CONNECTIONS_SERVER_ADDRESS=\"%s\"\n" "$CONNECTIONS_SERVER_ADDRESS" >> "$CUSTOMER_INFO_FILE_WHOLE"
	}

promptAndProcessResponse_useWifi()
	{
	#WIFI_USE=0
	#WIFI_SSID=
	#WIFI_SECURITY_TYPE=
	#WIFI_PASSWORD=
	#WIFI_IP_METHOD=
	#WIFI_IP_ADDRESS=
	#WIFI_SUBNET=
	#WIFI_GATEWAY=
	#WIFI_DNS1=
	#WIFI_DNS2=
	if [ "$1" = "MNS" ]; then printf "  (Wi-Fi is recommended unless you're doing special testing)\n"; fi
	printf "  ${COLOR_ENTER_PROMPT}Use Wi-Fi? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	if [ "$VERIFY_CONTINUE" = "y" ]; then
	    WIFI_USE=1
	    if [ "$1" = "MNS" ]; then
		#argument passed to setup for messagenet environment
		printf "  Setting up for MessageNet Wi-Fi..."
		WIFI_SSID="REDACTED"
		WIFI_SECURITY_TYPE="WPA"
		WIFI_PASSWORD="REDACTED"
		WIFI_IP_METHOD="DHCP"
		printf " done\n"
	    else
		printf "  ${COLOR_ENTER_PROMPT}Use MessageNet Wi-Fi? (y/n) --> ${NOCOLOR}"; read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "y" ]; then
			WIFI_SSID="REDACTED"
			WIFI_SECURITY_TYPE="WPA"
			WIFI_PASSWORD="REDACTED"
			WIFI_IP_METHOD="DHCP"
		else
			printf "   Wi-Fi SSID\t\t\t"; read WIFI_SSID
			printf "   Wi-Fi Security (none/wpa)\t"; read WIFI_SECURITY_TYPE
			if [ "$WIFI_SECURITY_TYPE" = "wpa" ]; then
				WIFI_SECURITY_TYPE="WPA"
				printf "   Wi-Fi Password\t\t"; read WIFI_PASSWORD
			elif [ "$WIFI_SECURITY_TYPE" = "none" ]; then
				WIFI_SECURITY_TYPE="OPEN"
			else
				printf "    ${COLOR_ATTENTION}INVALID!${NOCOLOR} You must type choice (\"none\" or \"wpa\") exactly. Try again.\n"
				promptAndProcessResponse_useWifi
			fi
			printf "   Wi-Fi IP Method (static/dhcp):\t"; read WIFI_IP_METHOD
			if [ "$WIFI_IP_METHOD" = "static" ]; then
				WIFI_IP_METHOD="Static"
				printf "   Wi-Fi IP Address\t\t"; read WIFI_IP_ADDRESS
				printf "   Wi-Fi Subnet Mask\t\t"; read WIFI_SUBNET
				printf "   Wi-Fi Gateway\t\t\t"; read WIFI_GATEWAY
				printf "   Wi-Fi DNS 1\t\t\t"; read WIFI_DNS1
				printf "   Wi-Fi DNS 2\t\t\t"; read WIFI_DNS2
			elif [ "$WIFI_IP_METHOD" = "dhcp" ]; then
				WIFI_IP_METHOD="DHCP"
			else
				printf "    ${COLOR_ATTENTION}INVALID!${NOCOLOR} You must type choice (\"static\" or \"dhcp\") exactly. Try again.\n"
				promptAndProcessResponse_useWifi
			fi
		fi #end use-MNS question check
	    fi #end MNS argument check
	else
		printf "   ${COLOR_ATTENTION}WARNING!${NOCOLOR} It is generally required to use Wi-Fi (even if it will just serve as a backup to Ethernet)!\n"
		printf "   ${COLOR_ENTER_PROMPT}Are you really sure you want to skip Wi-Fi setup? (Yes/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "Yes" ]; then
			WIFI_USE=0
		else
			promptAndProcessResponse_useWifi
		fi
	fi
	}

promptAndProcessResponse_useEthernet()
	{
	#ETHERNET_USE=0
	#ETHERNET_IP_METHOD=
	#ETHERNET_IP_ADDRESS=
	#ETHERNET_SUBNET=
	#ETHERNET_GATEWAY=
	#ETHERNET_DNS1=
	#ETHERNET_DNS2=
	printf "  ${COLOR_ENTER_PROMPT}Use Wired Ethernet? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	if [ "$VERIFY_CONTINUE" = "y" ]; then
	    ETHERNET_USE=1
	    if [ "$1" = "MNS" ]; then
		#argument passed to setup for messagenet environment
		printf "  Setting up for MessageNet Ethernet..."
		ETHERNET_IP_METHOD="DHCP"
	    else
		printf "   Ethernet IP Method (static/dhcp):\t"; read ETHERNET_IP_METHOD
		if [ "$ETHERNET_IP_METHOD" = "static" ]; then
			ETHERNET_IP_METHOD="Static"
			printf "   Ethernet IP Address\t\t\t"; read ETHERNET_IP_ADDRESS
			printf "   Ethernet Subnet Mask\t\t\t"; read ETHERNET_SUBNET
			printf "   Ethernet Gateway\t\t\t"; read ETHERNET_GATEWAY
			printf "   Ethernet DNS 1\t\t\t"; read ETHERNET_DNS1
			printf "   Ethernet DNS 2\t\t\t"; read ETHERNET_DNS2
		elif [ "$ETHERNET_IP_METHOD" = "dhcp" ]; then
			ETHERNET_IP_METHOD="DHCP"
		else
			printf "    ${COLOR_ATTENTION}INVALID!${NOCOLOR} You must type choice (\"static\" or \"dhcp\") exactly. Try again.\n"
			promptAndProcessResponse_useEthernet
		fi
	    fi
	else
		#DEV-NOTE: the following depends on Wi-Fi setup being called first, to work as intended.
		if [ $WIFI_USE -eq 0 ]; then
			printf "   ${COLOR_ATTENTION}WARNING!${NOCOLOR} You've chosen to not setup any networks! This tablet will be useless!\n"
			printf "   ${COLOR_ENTER_PROMPT}You really should setup a network (wifi/ethernet/none): ${NOCOLOR}"; read VERIFY_NO_NETWORK
			if [ "$VERIFY_NO_NETWORK" = "wifi" ]; then
				promptAndProcessResponse_useWifi
				promptAndProcessResponse_useEthernet
			elif [ "$VERIFY_NO_NETWORK" = "ethernet" ]; then
				promptAndProcessResponse_useEthernet
			elif [ "$VERIFY_NO_NETWORK" = "none" ]; then
				ETHERNET_USE=0
			else
				printf "    ${COLOR_ATTENTION}INVALID!${NOCOLOR} You must type choice (\"wifi\", \"ethernet\", or \"none\") exactly. Try again.\n"
				promptAndProcessResponse_useEthernet
			fi
		fi
		ETHERNET_USE=0
	fi
	}

##################################################################################################
# Check if we have the ADB binary available
adb_checkBinaryExistence()
	{
	if [[ "$(which adb)" == "" ]]; then
		printf "  ${COLOR_ERROR}ERROR! Android debugging bridge program (adb) not found. Aborting.${NOCOLO}\n"
		printf "\n"
		doExit
	fi
	}

# This is meant just to provide a simple yes or no
adb_isDeviceAvailable()
	{
	ADB_DEVICES_RESULT="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"
	if [ "$ADB_DEVICES_RESULT" = "" ] || [ "$ADB_CONNECTED_DEVICE" = "*" ]; then
		#device is unavailable
		return 0
	else
		#device is available
		return 1
	fi
	}

# Check USB/ADB connection to device and handle any issues
adb_checkDeviceConnection()
	{
	printf "  Checking device connection...\n"
	ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"
	if [ "$ADB_CONNECTED_DEVICE" = "" ] || [ "$ADB_CONNECTED_DEVICE" = "*" ]; then

		#TODO: make this 2207 more flexible.. maybe an array at the top of this file for other possible future devices?
		if [ "$(lsusb | grep -c 2207)" = "0" ]; then
			#device not even plugged in correctly
			printf "   ${COLOR_ERROR}ERROR! Device not connected via USB cable, or connected improperly.${NOCOLOR}\n"
			printf "   ${COLOR_ATTENTION}Try reconnecting USB cable and make sure it's connected to \"USB\" port on device (not \"HOST\").${NOCOLOR}\n"
			printf "   ${COLOR_ENTER_PROMPT}"; read -p "Press Enter when ready to check again."; printf "${NOCOLOR}\n"
			adb_checkDeviceConnection
		fi

		printf "  ${COLOR_ERROR}ERROR! Device USB is connected, but debugging bridge is unavailable.${NOCOLOR}\n"
		printf "  ${COLOR_ATTENTION}Try enabling USB debugging, reconnecting cable, or restarting adb server.${NOCOLOR}\n"
		printf "   To enable USB debugging (most common problem):\n"
		printf "    1. Swipe up to access app drawer\n"
		printf "    2. Tap \"Settings\" gear icon\n"
		printf "    3. Tap \"Developer Options\" (at very bottom)\n"
		printf "    4. Enable \"USB debugging\" (\"OK\" to allow)\n"
		printf "    5. Try again\n"
		printf "${COLOR_ENTER_PROMPT}"; read -p "  Press Enter when ready to check again."; printf "${NOCOLOR}\n"
		adb_checkDeviceConnection
	else
		printf "   ${COLOR_OK}OK${NOCOLOR} Connected device: \"%s\"\n" "$ADB_CONNECTED_DEVICE"
	fi
	}

adb_isScreenOn()
	{
	ADB_RESULT="$(adb shell dumpsys power \| grep \"Display Power\" \| cut -d'=' -f2)"; sleep 1	#returns OFF or ON
	if [ "$ADB_RESULT" = "ON" ]; then
		#screen is on
		return 1
	else
		#screen is off
		return 0
	fi
	}

adb_turnScreenOn()
	{
	adbMaxWaitTime=10

	printf "  ADB turning screen on..."
	adb_isScreenOn
	if [ $? -eq 0 ]; then
		adb shell input keyevent KEYCODE_POWER > /dev/null 2>&1; sleep 1
		sleep 1; printf "."
		adb_isScreenOn
		while [ $? -eq 0 ]; do
			if [[ $adbMaxWaitTime -gt 0 ]]; then
				printf "."
				(( adbMaxWaitTime-- ))
				sleep 1
			else
				printf "${COLOR_ERROR}"; read -p " Gave up. Turn on screen now! Press enter when ready."; printf "${NOCOLOR}"
				break
			fi
			adb_isScreenOn
		done
		printf "${COLOR_OK}"; printf " done.\n"; printf "${NOCOLOR}"
	else
		printf "${COLOR_OK}"; printf "  ADB screen is already turned on... Nothing to do.\n"; printf "${NOCOLOR}"
	fi
	}

adb_isScreenLocked()
	{
	ADB_RESULT="$(adb shell dumpsys window | grep mCurrentFocus | grep -c StatusBar)"; sleep 1
	if [ "$ADB_RESULT" = "1" ]; then
		#screen is locked
		return 1
	fi
	return 0
	}

adb_unlockScreen()
	{
	printf "  ADB unlocking screen..."
	adb_isScreenLocked
	while [ $? -eq 1 ]; do
		adb shell input touchscreen swipe 960 1050 960 300 > /dev/null 2>&1; printf "."; sleep 2;	#swipe up to unlock
		adb_isScreenLocked
	done
	printf "${COLOR_OK}"; printf " OK.\n"; printf "${NOCOLOR}"
	}

COUNT_ENTER_ROOT_MODE=0
adb_enterRootMode()
	{
	adbRootingMaxWaitTime=45
	indent="  "
	if [ "$1" = "indentOverride" ]; then indent="$2"; fi

	printf "%sADB entering root mode...\n" "$indent"

	# check if device is available first
	UNAVAILABLE_COUNT=0
	adb_isDeviceAvailable
	while [ $? -eq 0 ]; do
		(( UNAVAILABLE_COUNT++ ))
		if [ $UNAVAILABLE_COUNT -eq 1 ]; then
			#first time
			printf "%s ${COLOR_ERROR}Device unavailable! ${COLOR_ENTER_PROMPT}" "$indent"
			read -p "Make sure device is powered and connected to USB. Press Enter to try again."; printf "${NOCOLOR}"
		elif [ $UNAVAILABLE_COUNT -eq 2 ]; then
			#second time
			printf "%s Restarting ADB..." "$indent"
			printf " (adb killing)"
			adb kill-server > /dev/null 2>&1; for i in {1..3}; do printf "."; sleep 1; done
			printf " (adb starting)"
			adb start-server > /dev/null 2>&1; for i in {1..8}; do printf "."; sleep 1; done
			printf " done? Checking again...\n"
		#elif [ $UNAVAILABLE_COUNT -eq 3 ]; then
			#third time
		else
			printf "%s ${COLOR_ERROR}Device unavailable multiple times! Get engineering help. ${COLOR_ENTER_PROMPT}" "$indent"
			read -p "Press Enter to try again."; printf "${NOCOLOR}"
		fi

		#check if available
		adb_isDeviceAvailable
	done

	adb_isDeviceAvailable
	if [ $? -eq 0 ]; then
		printf "%s ${COLOR_ERROR}Device unavailable! ${COLOR_ATTENTION}Make sure it's connected, turned on and online. ${COLOR_ENTER_PROMPT}" "$indent"
		read -p "Press Enter to try again."; printf "${NOCOLOR}"

		# try checking device availability again
		adb_isDeviceAvailable
		if [ $? -eq 0 ]; then
			# if still problematic, let's try to fix things
			printf "%s ${COLOR_ERROR}Device is still unavailable! ${COLOR_ENTER_PROMPT}" "$indent"
			read -p "Press Enter to try to repair the ADB connection."; printf "${NOCOLOR}"

			printf "%s Restarting ADB..." "$indent"
			adb kill-server > /dev/null 2>&1; sleep 1; printf "."; sleep 1; printf "."
			adb start-server > /dev/null 2>&1; sleep 1; printf "."; sleep 1; printf "."; sleep 1; printf "."; sleep 1; printf "."; sleep 1; printf ".";

			# try checking device availability again
			adb_isDeviceAvailable
			if [ $? -eq 0 ]; then
				printf "%s ${COLOR_ERROR}Device remains unavailable! Get engineering help!${COLOR_ENTER_PROMPT}" "$indent"
				read -p "Press Enter when approved by an engineer."; printf "${NOCOLOR}"
			fi
		fi
	fi

	# check if we're already in root mode
	if [ "$(adb shell getprop service.adb.root)" = "1" ]; then
		printf " %s${COLOR_OK}ADB already in root mode.${NOCOLOR}\n" "$indent"
		COUNT_ENTER_ROOT_MODE=0
		return 1
	fi

	# issue root command
	if [ $COUNT_ENTER_ROOT_MODE -gt 0 ]; then printf "    (This is retry #%d)\n" $COUNT_ENTER_ROOT_MODE; fi
	adb root > /dev/null 2>&1; (( COUNT_ENTER_ROOT_MODE++ )); sleep 1
	printf "%s Root command issued..." "$indent"

	# wait here until device becomes available again (rooting takes some time)
	adb_isDeviceAvailable
	while [ $? -eq 0 ]; do
		sleep 1
		if [[ $adbRootingMaxWaitTime -gt 0 ]]; then
			printf "."
			(( adbRootingMaxWaitTime-- ))
		else
			#max time has elapsed, fail it out and try again
			printf " ${COLOR_ERROR}FAILED!${NOCOLOR} Trying again...\n" "$indent"
			adb_enterRootMode $1 $2
		fi
		adb_isDeviceAvailable
	done

	if [ "$(adb shell getprop service.adb.root)" = "1" ]; then
		printf " ${COLOR_OK}Done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED! Get engineering help!${COLOR_ENTER_PROMPT}\n" "$indent"
		read -p "Press Enter when approved by an engineer."; printf "${NOCOLOR}"
	fi
	}

adb_exitRootMode()
	{
	# DEV-NOTE: This can cause a hard hangup on Jet's Jan-2019 tablets!
	adbUnrootingMaxWaitTime=45
	indent="  "

	if [ "$1" = "indentOverride" ]; then indent="$2"; fi

	printf "%sADB un-rooting..." "$indent"
	#adb shell 'setprop service.adb.root 0; setprop ctl.restart adbd'; printf "."; sleep 1
	adb shell 'setprop service.adb.root 0'; printf "."; sleep 1
	adb shell 'su -c "setprop ctl.restart adbd"'; printf "."; sleep 1; printf "."; sleep 1
	adb_isDeviceAvailable
	while [ $? -eq 0 ]; do
		if [[ $adbUnrootingMaxWaitTime -gt 0 ]]; then
			printf "."
			(( adbUnrootingMaxWaitTime-- ))
			sleep 1
		else
			printf " ${COLOR_ERROR}FAILED!${NOCOLOR}"
			return 0
		fi
		adb_isDeviceAvailable
	done
	printf "${COLOR_OK}"; printf " done.\n"; printf "${NOCOLOR}"

	return 1
	}

adb_remountSystemPartition()
	{
	adbRemountMaxWaitTime=30

	printf "  ADB remounting system partition..."
	adb remount > /dev/null 2>&1
	sleep 1; printf "."
	adb_isDeviceAvailable
	while [ $? -eq 0 ]; do
		if [[ $adbRemountMaxWaitTime -gt 0 ]]; then
			printf "."
			(( adbRemountMaxWaitTime-- ))
			sleep 1
		else
			printf " ${COLOR_ERROR} FAILED!${NOCOLOR} Trying again...\n"
			adb_remountSystemPartition
		fi
		adb_isDeviceAvailable
	done
	printf " ${COLOR_OK}done.${NOCOLOR}\n"

	return 1
	}

adb_checkRemountedSystemPartitionHealth()
	{
	# It seems possible that if the /system partition has less space than 
	# what fdisk reports for its block device, then it might have bad blocks??

	# Returns 0 for problem; Returns 1 for OK.

	printf "  Checking /system partition health..."

	CHKSYSPART_TOLERANCE_PERCENT=80	#less than this percent of possible space actually mounted will result in a bad health report

	ADB_SYSTEM_BLOCK_DEVICE_PATH="$(adb shell mount | grep "\/system" | awk '{print $1}')"; sleep 1	#Example: /dev/block/mmcblk1p11

	ADB_SYSTEM_PARTITION_SIZE_BLOCKS=$(adb shell cat /proc/partitions | grep mmcblk1p11 | awk '{print $3}'); sleep 1
	ADB_SYSTEM_MOUNTED_SIZE_BLOCKS=$(adb shell df | grep "\/system" | awk '{print $2}'); sleep 1

	CHKSYSPART_CALC_PERCENT_UTILIZATION="$(bc -l <<< 'scale=2; ('$ADB_SYSTEM_MOUNTED_SIZE_BLOCKS'/'$ADB_SYSTEM_PARTITION_SIZE_BLOCKS')*100' | cut -d'.' -f1)"
	if [ $CHKSYSPART_CALC_PERCENT_UTILIZATION -lt $CHKSYSPART_TOLERANCE_PERCENT ]; then
		# mount is utilizing less than what the device actually is
		# this could indicate bad blocks?
		printf " ${COLOR_ERROR}Problem?${NOCOLOR}\n"
		return 0
	else
		printf " ${COLOR_OK}OK${NOCOLOR}\n"
		return 1
	fi
	}

##################################################################################################
doTabletTask_testSpeakers()
	{
	printf "  Testing Speakers..."
	adb push "speaker-test.mp3" /sdcard/ > /dev/null 2>&1; printf "."; sleep 1; printf ". ${COLOR_OK}test file copied.${NOCOLOR}\n"
	printf "   ${COLOR_ATTENTION}ATTENTION:${NOCOLOR} Next, you will hear three tones (left, right, center). Be sure amp is ON and be ready to listen...\n";
	printf "   ${COLOR_ENTER_PROMPT}"; read -p "Press Enter when ready to test."; printf "${NOCOLOR}"
	adb shell "am start -a android.intent.action.VIEW -d file:///sdcard/speaker-test.mp3 -t audio/mp3" > /dev/null 2>&1
	printf "   ${COLOR_ATTENTION}Did you hear the correct tones? ${COLOR_ENTER_PROMPT}(y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	while [ "$VERIFY_CONTINUE" = "n" ] || [ "$VERIFY_CONTINUE" = "" ]; do
		printf "   Make sure all audio cables are correct and secure.\n"
		printf "   If you cannot figure it out or fix it right now, make a note and continue with provisioning.\n"
		adb shell "am force-stop com.android.music" > /dev/null 2>&1; sleep 1
		adb shell "am start -a android.intent.action.VIEW -d file:///sdcard/speaker-test.mp3 -t audio/mp3" > /dev/null 2>&1; sleep 1
		printf "   ${COLOR_ATTENTION}Did you hear the correct tones? (or continue anyway?) ${COLOR_ENTER_PROMPT}(y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
	done
	adb shell "am force-stop com.android.music" > /dev/null 2>&1
	}

doTabletTask_turnOnBluetooth()
	{
	printf "  Turning on Bluetooth..."
	adb shell su -c "service call bluetooth_manager 6" > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
	while [ "$(adb shell dumpsys bluetooth_manager | grep "state:" | grep -c "BLE_TURNING_ON")" = "1" ]; do printf "."; sleep 1; done
	if [ "$(adb shell dumpsys bluetooth_manager | grep "state:" | grep -c "ON")" = "1" ]; then
		#success
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
		sleep 1
	else
		#unknown
		printf " ${COLOR_ERROR}UNKNOWN RESULT.\n   ${COLOR_ATTENTION}Check whether Bluetooth is ON now!\n${COLOR_ENTER_PROMPT}"
		read -p "   Press Enter when done."; printf "${NOCOLOR}"
	fi
	}

doTabletTask_disableDisplaySleep()
	{
	printf "  Disabling Display Sleep..."
	DISPLAY_SLEEP_VALUE="2147483647"
	adb shell su -c "settings put system screen_off_timeout $DISPLAY_SLEEP_VALUE" > /dev/null 2>&1; printf "."; sleep 1
	RES="$(adb shell settings get system screen_off_timeout)"
	if [ "$RES" = "$DISPLAY_SLEEP_VALUE" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED.${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to disable display sleep (result: \"$RES\" instead of \"$DISPLAY_SLEEP_VALUE\")"
	fi
	sleep 1
	}

doTabletTask_disableDisplayLock()
	{
	printf "  Disabling Screen Lock..."
	adb shell su -c "settings put secure lockscreen.disabled 1" > /dev/null 2>&1; printf "."; sleep 1
	adb shell su -c "rm /data/system/locksettings.db" > /dev/null 2>&1; printf "."; sleep 1
	adb shell su -c "rm /data/system/locksettings.db-shm" > /dev/null 2>&1; printf "."; sleep 1
	adb shell su -c "rm /data/system/locksettings.db-wal" > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get secure lockscreen.disabled)" = "1" ] && 
	   [ "$(adb shell ls -l /data/system/locksettings.db | grep -v "No such file" | grep -c locksettings.db)" = "0" ] &&
	   [ "$(adb shell ls -l /data/system/locksettings.db-shm | grep -v "No such file" | grep -c locksettings.db-shm)" = "0" ] &&
	   [ "$(adb shell ls -l /data/system/locksettings.db-wal | grep -v "No such file" | grep -c locksettings.db-wal)" = "0" ]; then
		printf " ${COLOR_OK}done${NOCOLOR} (will take effect after reboot).\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to disable display lock."
	fi
	sleep 1
	}

doTabletTask_setAutoTimeAcquisition()
	{
	# Since our ClockSync app will handle NTP for us, we don't want to use Android's network-time BS (which only works on mobile networks)
	printf "  Disabling Mobile Network Automatic Time Settings..."
	adb shell settings put global auto_time 0 > /dev/null 2>&1; printf "."; sleep 1
	adb shell settings put global auto_time_zone 0 > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get global auto_time)" = "0" ] && 
	   [ "$(adb shell settings get global auto_time_zone)" = "0" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "WARN" "Failed to disable mobile network automatic time settings."
	fi
	sleep 1
	}

doTabletTask_setTimeToComputer()
	{
	printf "  Setting Initial Timezone (settings)..."
	adb shell settings put global time_zone America/New_York > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get global time_zone)" = "America/New_York" ]; then
		printf "${COLOR_OK}done.${NOCOLOR}\n"
	else 
		printf "${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "WARN" "Failed to set initial timezone (settings put global time_zone America/New_York)."
	fi

	printf "  Setting Initial Timezone (setprop)..."
	adb shell setprop persist.sys.timezone America/New_York > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell getprop persist.sys.timezone)" = "America/New_York" ]; then
		printf "${COLOR_OK}done.${NOCOLOR}\n"
	else 
		printf "${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "WARN" "Failed to set initial timezone (setprop persist.sys.timezone America/New_York)."
	fi

	printf "  Setting Initial Date and Time..."
	adb shell su -c "date -u $(date -u +%m%d%H%M%G.%S)" > /dev/null 2>&1; printf "."; sleep 1
	adb shell su -c "am broadcast -a android.intent.action.TIME_SET" > /dev/null 2>&1; printf "."; sleep 1
	adb shell su -c "hwclock -w" > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell date -u +%m%d%Y%H%M)" = "$(date -u +%m%d%Y%H%M)" ]; then
		printf "${COLOR_OK}done.${NOCOLOR}\n"
	else 
		printf "${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "WARN" "Failed to set initial date, time, or timezone to provisioner's clock."
	fi
	sleep 1
	}

doTabletTask_setBootAnimation()
	{
	printf "  Disabling Android Boot Animation (this may or may not actually work)..."
	if [ "$(adb shell ls -l /system/bin/bootanimationANDROID | grep -c ANDROID)" = "1" ]; then
		printf " ${COLOR_OK}already done.${NOCOLOR}\n"
	else
		sleep 1
		adb shell mv /system/bin/bootanimation /system/bin/bootanimationANDROID > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell ls -l /system/bin/bootanimationANDROID | grep -c ANDROID)" = "1" ]; then
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else
			printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
			addToProblemLog "WARN" "Failed to disable Android boot animation."
		fi
	fi
	sleep 1
	}

doTabletTask_setWallpaper()
	{
	if [ -f /sdcard/Pictures/wallpaper.jpg ]; then
		printf "  Wallpaper already setup\n"
	else
		#DEV-NOTE: this uses GUI method... consider using another (like settings or setprop?) later!
		adb_turnScreenOn
		adb_unlockScreen
		printf "  Setting Up Wallpaper ${COLOR_ATTENTION}(WARNING: Do NOT touch screen!)${NOCOLOR}..."
		adb push wallpaper.jpg /sdcard/Pictures/ > /dev/null 2>&1; printf "."; sleep 1
		#MAX_RETRIES=10; while [ "$(adb shell ls -l /sdcard/Pictures/wallpaper.jpg | grep -c wallpaper)" = "0" ] && [ $MAX_RETRIES -gt 0 ]; do printf "."; sleep 1; (( MAX_RETRIES-- )); done
		adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; printf "."; sleep 1
		adb shell am start -a android.intent.action.ATTACH_DATA -c android.intent.category.DEFAULT -d file:///sdcard/Pictures/wallpaper.jpg -t 'image/*' -e mimeType 'image/*' > /dev/null 2>&1; printf "."; sleep 1
		adb shell input tap 750 860 > /dev/null 2>&1; printf "."; sleep 1	#tap use wallpaper to open
		adb shell input tap 750 860 > /dev/null 2>&1; printf "."; sleep 1	#tap use wallpaper to open again
		#adb shell input touchscreen swipe 150 500 400 500 1500 > /dev/null 2>&1 #align image		#old image needed shifted
		adb shell input tap 50 100 > /dev/null 2>&1; printf "."; sleep 1	#tap save
		#TODO: Add verification check here
		printf " done ${DARKGRAY}(unverified)${NOCOLOR}\n"
	fi
	}

doTabletTask_setLauncherIcons()
	{
	printf "  Cleaning Up Homescreen..."
	adb shell 'sqlite3 /data/data/com.android.launcher3/databases/launcher.db "DELETE from favorites"' > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell 'sqlite3 /data/data/com.android.launcher3/databases/launcher.db "SELECT * FROM favorites"')" = "" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "WARN" "Failed to clean up appearance of Android home screen."
	fi
	sleep 1
	}

doTabletTask_restartLauncher()
	{
	printf "  Refreshing Launcher ${COLOR_ATTENTION}(verify you see cleaned up home screen)${NOCOLOR}..."
	adb shell am force-stop com.android.launcher3 > /dev/null 2>&1; printf "."; sleep 3
	MAX_RETRIES=10; while [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)" = "0" ] && [ $MAX_RETRIES -gt 0 ]; do printf "."; sleep 1; (( MAX_RETRIES-- )); done
	if [ "$(adb shell dumpsys window | grep mCurrentFocus | grep -c launcher3)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; (( ERROR_COUNT_PROV++ ))
		addToProblemLog "WARN" "Failed to refresh launcher screen for provisioner to verify whether cleanup was successful."
	fi
	sleep 1
	}

doTabletTask_setSystemSounds()
	{
	#printf "  Disabling charging sounds..."
	#adb shell busybox sed -i 's/name="charging_sounds_enabled" value="1"/name="charging_sounds_enabled" value="0"/' /data/system/users/0/settings_global.xml > /dev/null 2>&1; printf "."; sleep 1
	#if [ "adb shell grep 'charging_sounds_enabled' /data/system/users/0/settings_global.xml | grep -c '0'" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; fi

	printf "  Disabling power on sounds..."
	adb shell busybox sed -i 's/name="power_sounds_enabled" value="1"/name="power_sounds_enabled" value="0"/' /data/system/users/0/settings_global.xml > /dev/null 2>&1; printf "."; sleep 1
	adb shell settings put global power_sounds_enabled 0 > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get global power_sounds_enabled)" = "0" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to disable power-on sounds."; fi; sleep 1

	printf "  Disabling lock screen sounds..."
	adb shell busybox sed -i 's/name="lockscreen_sounds_enabled" value="1"/name="lockscreen_sounds_enabled" value="0"/' /data/system/users/0/settings_system.xml > /dev/null 2>&1; printf "."; sleep 1
	adb shell settings put system lockscreen_sounds_enabled 0 > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get system lockscreen_sounds_enabled)" = "0" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to disable lock-screen sounds."; fi; sleep 1

	printf "  Disabling system sound effects..."
	adb shell busybox sed -i 's/name="sound_effects_enabled" value="1"/name="sound_effects_enabled" value="0"/' /data/system/users/0/settings_system.xml > /dev/null 2>&1; printf "."; sleep 1
	adb shell settings put system sound_effects_enabled 0 > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get system sound_effects_enabled)" = "0" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to disable system sound effects."; fi; sleep 1

	# NOTE: these seem to reset after reboot and are not reliable... use audio.safemedia.bypass=true in build.prop instead!
	#printf "  Disabling safe maximum headphone volume warning..."
	#adb shell busybox sed -i 's/name="audio_safe_volume_state" value="3"/name="audio_safe_volume_state" value="1"/' /data/system/users/0/settings_global.xml > /dev/null 2>&1; printf "."; sleep 1
	#adb shell settings put global audio_safe_volume_state 1 > /dev/null 2>&1; printf "."; sleep 1
	#if [ "$(adb shell settings get global audio_safe_volume_state)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to disable safe maximum headphone volume warning."; fi; sleep 1

	printf "  Setting initial headphone volume..."
	adb shell busybox sed -i 's/name="volume_music_headphone" value="8"/name="volume_music_headphone" value="15"/' /data/system/users/0/settings_system.xml > /dev/null 2>&1; printf "."; sleep 1
	adb shell settings put system volume_music_headphone 15 > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get system volume_music_headphone)" = "15" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to set maximum headphone volume."; fi; sleep 1

	printf "  Setting initial music/media channel volume..."
	adb shell busybox sed -i 's/name="volume_music" value="11"/name="volume_music" value="15"/' /data/system/users/0/settings_system.xml > /dev/null 2>&1; printf "."; sleep 1
	adb shell settings put system volume_music 15 > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell settings get system volume_music)" = "15" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to set maximum music/media channel volume."; fi; sleep 1
	}

doTabletTask_setExternalMicrophone()
	{
	printf "  Enabling External Microphone..."

	# Check if it even needs to be done (might've already)
	if [ "$(adb shell 'grep attached_input_devices /system/etc/audio_policy.conf | grep -c AUDIO_DEVICE_IN_WIRED_HEADSET')" = "0" ]; then

		# First, attempt to automatically do it
		adb shell busybox sed -i '/attached_input_devices AUDIO_DEVICE_IN_BUILTIN_MIC|AUDIO_DEVICE_IN_REMOTE_SUBMIX/s/$/|AUDIO_DEVICE_IN_WIRED_HEADSET/' /system/etc/audio_policy.conf
		printf "."; sleep 1
		
		# Check if our update worked
		if [ "$(adb shell 'grep attached_input_devices /system/etc/audio_policy.conf | grep -c AUDIO_DEVICE_IN_WIRED_HEADSET')" = "0" ]; then
			# For some reason (got warning about being out of space?) we couldn't programatically edit the file, so it must be done manually!
			printf "\n"
			printf "   ${COLOR_ATTENTION}ATTENTION:${NOCOLOR} Must be manually enabled with: busybox vi /system/etc/audio_policy.conf\n"
			printf "   In another window, use adb shell to edit above file, append \"attached_input_devices\" line with \"|AUDIO_DEVICE_IN_WIRED_HEADSET\"\n"
			printf "${COLOR_ENTER_PROMPT}"; read -p "   To continue provisioning, press enter when finished..."; printf "${NOCOLOR}"
		else
			printf " ${COLOR_OK}done.${NOCOLOR}\n"
		fi
	else
		printf " ${COLOR_OK}already done.${NOCOLOR}\n"
	fi
	sleep 1
	}

doTabletTask_setWifi()
	{
	if [ $WIFI_USE -eq 0 ]; then
		printf "  Skipping Wifi Setup.\n"
	else
		printf "  Enabling Wi-Fi...\n"
	
		# If static method, then set that up
		if [ "$WIFI_IP_METHOD" = "Static" ]; then
			printf "   Setting up static addressing..."
	
			#TODO
			printf " (not developed yet, using DHCP instead!)"
	
			printf " done.\n"
		fi
	
		# Setup to connect to the specified Wi-Fi
		if [[ "$WIFI_SECURITY_TYPE" == "WPA" ]]; then
			printf "   Setting up WPA supplicant config file... "
			adb shell su -c "svc wifi enable" > /dev/null 2>&1
			sleep 2
		
			printf "p2p_disabled=1\n" > /tmp/wpasupp
			printf "network={\n" >> /tmp/wpasupp
			printf "	ssid=\"%s\"\n" "$WIFI_SSID" >> /tmp/wpasupp
			printf "	psk=\"%s\"\n" $WIFI_PASSWORD >> /tmp/wpasupp
			printf "}" >> /tmp/wpasupp
		
			adb push /tmp/wpasupp /sdcard/ > /dev/null 2>&1
			sleep 1
			adb shell su -c "cp -a /data/misc/wifi/wpa_supplicant.conf /data/misc/wifi/wpa_supplicant.conf.ORIG" > /dev/null 2>&1; sleep 1
			adb shell su -c "cat /sdcard/wpasupp >> /data/misc/wifi/wpa_supplicant.conf" > /dev/null 2>&1; sleep 1
			adb shell su -c "rm -f /sdcard/wpasupp" > /dev/null 2>&1; sleep 1
			printf "done.\n"
			sleep 1
		
			printf "   Reloading new WPA supplicant configuration... "
			adb shell su -c "wpa_cli -p /data/misc/wifi/sockets -i wlan0 reconfigure" > /dev/null 2>&1
			sleep 6
			printf "done.\n"
		
			printf "   Connecting to \"%s\"... " "$WIFI_SSID"
			adb shell su -c "svc wifi disable" > /dev/null 2>&1
			sleep 3
			adb shell su -c "svc wifi enable" > /dev/null 2>&1
			sleep 10
			if [[ "$(adb shell su -c 'ifconfig | grep -c wlan0')" > "0" ]]; then
				printf "done.\n"
			else
				printf "${COLOR_ERROR}ERROR${NOCOLOR}, could not start wlan0.\n"
				addToProblemLog "ERROR" "Could not start wlan0 during initial connection attempt."
				return
			fi
			sleep 1
		
			WIFI_IP_ADDRESS="$(adb shell ip addr show wlan0 | grep "inet\ " | cut -d' ' -f6 | cut -d'/' -f1)"; sleep 1
			WIFI_MAC_ADDRESS="$(adb shell su -c 'cat /sys/class/net/wlan0/address' | sed -e 's/\r//')"; sleep 1
			WIFI_MAC_ADDRESS_NOCOLON=$(echo $WIFI_MAC_ADDRESS | tr -d :)
		elif [[ "$WIFI_SECURITY_TYPE" == "OPEN" ]]; then
			printf "   Setting up supplicant config file... "
			adb shell su -c "svc wifi enable" > /dev/null 2>&1
			sleep 2
			
			printf "p2p_disabled=1\n" > /tmp/wpasupp
			printf "network={\n" >> /tmp/wpasupp
			printf "	ssid=\"%s\"\n" "$WIFI_SSID" >> /tmp/wpasupp
			printf "	key_mgmt=NONE" >> /tmp/wpasupp
			printf "}" >> /tmp/wpasupp
		
			adb push /tmp/wpasupp /sdcard/ > /dev/null 2>&1
			sleep 1
			adb shell su -c "cp -a /data/misc/wifi/wpa_supplicant.conf /data/misc/wifi/wpa_supplicant.conf.ORIG" > /dev/null 2>&1; sleep 1
			adb shell su -c "cat /sdcard/wpasupp >> /data/misc/wifi/wpa_supplicant.conf" > /dev/null 2>&1; sleep 1
			adb shell su -c "rm -f /sdcard/wpasupp" > /dev/null 2>&1; sleep 1
			printf "done.\n"
			sleep 1
		
			printf "   Reloading new WPA supplicant configuration... "
			adb shell su -c "wpa_cli -p /data/misc/wifi/sockets -i wlan0 reconfigure" > /dev/null 2>&1
			sleep 6
			printf "done.\n"
		
			printf "   Connecting to \"%s\" (this might fail, if so, connect manually)... " "$WIFI_SSID"
			adb shell su -c "svc wifi disable" > /dev/null 2>&1
			sleep 3
			adb shell su -c "svc wifi enable" > /dev/null 2>&1
			sleep 10
			if [[ "$(adb shell su -c 'ifconfig | grep -c wlan0')" > "0" ]]; then
				printf "done.\n"
			else
				printf "${COLOR_ERROR}ERROR${NOCOLOR}, could not start wlan0.\n"
				addToProblemLog "ERROR" "Could not start wlan0 during initial connection attempt."
				return
			fi
			sleep 1
	
			WIFI_IP_ADDRESS="$(adb shell ip addr show wlan0 | grep "inet\ " | cut -d' ' -f6 | cut -d'/' -f1)"; sleep 1
			WIFI_MAC_ADDRESS="$(adb shell su -c 'cat /sys/class/net/wlan0/address' | sed -e 's/\r//')"; sleep 1
			WIFI_MAC_ADDRESS_NOCOLON=$(echo $WIFI_MAC_ADDRESS | tr -d :)
		else
			printf "   ${COLOR_ERROR}ERROR:${NOCOLOR} Only WPA and OPEN networks are supported in this script. Wi-Fi has NOT been configured!)\n"
			printf "${COLOR_ENTER_PROMPT}"; read -p "    Press enter to continue anyway... "; printf "${NOCOLOR}"
		fi
	fi
	}

doTabletTask_setEthernet()
	{
	if [ $ETHERNET_USE -eq 0 ]; then
		printf "  Skipping Wired Ethernet Setup.\n"
	else
		printf "  Enabling Wired Ethernet...\n"
		printf "   You MUST have the USB dongle connected to tablet's \"Host\" USB port!\n"
		printf "   ${COLOR_ENTER_PROMPT}Ready to continue? (y/n) ${NOCOLOR}"; read VERIFY_CONTINUE
	
		if [ "$VERIFY_CONTINUE" = "y" ]; then
			# If static method, then set that up
			if [ "$ETHERNET_IP_METHOD" = "Static" ]; then
				printf "   Setting up static addressing..."
		
				#TODO
				printf " (not developed yet, using DHCP instead!)"
		
				printf " done.\n"
			fi
	
			printf "   Determining MAC address..."
			ETH_MAC_ADDRESS="$(adb shell su -c 'cat /sys/class/net/eth0/address' | sed -e 's/\r//')"; printf "."; sleep 1
			ETH_MAC_ADDRESS_NOCOLON=$(echo $ETH_MAC_ADDRESS | tr -d :); printf "."; sleep 1
			#TODO: Add verification check here
			printf " done.\n"
		else
			printf "   ${COLOR_ATTENTION}WARNING:${NOCOLOR} Setup will be skipped. This means we can't know the MAC address.\n"
			addToProblemLog "WARN" "Wired Ethernet setup skipped. Wired MAC is unknown."
		fi
	fi
	}

doTabletTask_clearPreviousSerialNumbers()
	{
	# Reset any serial numbers
	# (note: preserves them in the file but commented out, so we have a history)
	if [ $(adb shell grep ro.serialno /system/build.prop | grep -v -c '#') -gt 0 ]; then
		sleep 1
		printf "  Clearing old serial number information... "
		CHECK_RESULT_BEFORE=$(adb shell grep ro.serialno /system/build.prop | grep -c '#'); sleep 1
		adb shell cp -a /system/build.prop /sdcard/build.prop.$(echo $DATETIME_RESET_START_THIS) > /dev/null 2>&1; sleep 1
		adb shell sed -i 's/ro.serialno=/#ro.serialno/g' /sdcard/build.prop.$(echo $DATETIME_RESET_START_THIS) > /dev/null 2>&1; sleep 1
		adb shell mv /sdcard/build.prop.$(echo $DATETIME_RESET_START_THIS) /system/build.prop > /dev/null 2>&1; sleep 1
		adb shell chmod 644 /system/build.prop > /dev/null 2>&1; sleep 1
		CHECK_RESULT_AFTER=$(adb shell grep ro.serialno /system/build.prop | grep -c '#'); sleep 1
		if [ $CHECK_RESULT_AFTER -eq $CHECK_RESULT_BEFORE ]; then 
			printf "${COLOR_ERROR}ERROR!${NOCOLOR} Failed to remove old serial numbers. Edit /system/build.prop manually.\n"
			addToProblemLog "ERROR" "Failed to remove old serial numbers. Provisioner should edit /system/build.prop manually to remedy."
		else
			printf "${COLOR_OK}done.${NOCOLOR}\n"
		fi
	fi
	}

doTabletTask_setSerialNumber()
	{
	doTabletTask_clearPreviousSerialNumbers

	printf "  Writing serial number to device..."
	adb shell "echo \"ro.serialno=$OMNI_SERIAL_NUMBER\" >> /system/build.prop" > /dev/null 2>&1; for i in {1..3}; do printf "."; sleep 1; done
	if [ $(adb shell grep \"$OMNI_SERIAL_NUMBER\" /system/build.prop | grep -c -v '#') -eq 0 ]; then
		printf " ${COLOR_ERROR}ERROR!${NOCOLOR} Failed to write serial number. Edit /system/build.prop manually.\n"
		addToProblemLog "ERROR" "Failed to write serial number. Provisioner should edit /system/build.prop manually to remedy."
	else
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	fi
	}

doTabletTask_setSafeVolumeWarningBypass()
	{
	printf "  Writing disable flag for safe-volume-warning to device..."
	adb shell "echo \"audio.safemedia.bypass=true\" >> /system/build.prop" > /dev/null 2>&1; for i in {1..2}; do printf "."; sleep 1; done
	if [ $(adb shell grep \"safemedia\" /system/build.prop | grep -c -v '#') -eq 0 ]; then
		printf " ${COLOR_ERROR}ERROR!${NOCOLOR} Failed to write flag. Edit /system/build.prop and manually add \"audio.safemedia.bypass=true\".\n"
		addToProblemLog "ERROR" "Failed to write safe-volume-warning disable flag. Provisioner should edit /system/build.prop manually to remedy, or maximum audio volume will be impossible to set via app."
	else
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	fi
	}

doTabletTask_installSoftware_sshServer()
	{
	printf "  Installing SSH Server..."
	adb push sshServer.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r sshServer.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/org.galexander.sshd | grep -v "No such file" | grep -c org.galexander.sshd)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to install SSH server. This is CRITICAL! Remote access won't be possible!"
	fi
	sleep 1

	printf "   Configuring runtime permissions via PackageManager...\n"
	printf "    READ_EXTERNAL_STORAGE..."; adb shell pm grant org.galexander.sshd android.permission.READ_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package org.galexander.sshd | grep READ_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant READ_EXTERNAL_STORAGE permission to SSH server app."; fi; sleep 1
	printf "    WRITE_EXTERNAL_STORAGE..."; adb shell pm grant org.galexander.sshd android.permission.WRITE_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package org.galexander.sshd | grep WRITE_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant WRITE_EXTERNAL_STORAGE permission to SSH server app."; fi; sleep 1

	printf "   Configuring SSH server..."
	adb shell su -c "am start -n org.galexander.sshd/org.galexander.sshd.SimpleSSHD" > /dev/null 2>&1; printf "."; sleep 1		#have to start it to generate shared_prefs subdir where we copy config file
	printf "."; sleep 1; printf "."; sleep 1
	#TODO: Redo the following.. may work but I don't like it
	adb shell 'if [ ! -e /data/user/0/org.galexander.sshd/shared_prefs ]; then printf "\n    ERROR: shared_prefs does not exist.\n    Manually run: adb push org.galexander.sshd_preferences.xml /data/user/0/org.galexander.sshd/shared_prefs/\n    read -p \"Press enter to continue\"\n    "; fi'; sleep 1
	adb push org.galexander.sshd_preferences.xml /data/user/0/org.galexander.sshd/shared_prefs/ > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -l /data/user/0/org.galexander.sshd/shared_prefs/org.galexander.sshd_preferences.xml | grep -v "No such file" | grep -c org.galexander.sshd_preferences.xml)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to configure SSH server. This is CRITICAL! Remote access may not be possible!"
	fi
	sleep 1

	adb shell su -c "am start -a android.intent.action.MAIN -c android.intent.category.HOME" > /dev/null 2>&1; sleep 1
	}

doTabletTask_installSoftware_ntpClient()
	{
	printf "  Installing NTP Client..."
	adb push ntpClient.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1; printf "."; sleep 1
	adb install -r ntpClient.apk > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/ru.org.amip.ClockSync | grep -v "No such file" | grep -c ru.org.amip.ClockSync)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install NTP client. Time will not stay in sync."; fi; sleep 1

	printf "   Configuring runtime permissions via PackageManager...\n"
	printf "    ACCESS_FINE_LOCATION"; adb shell pm grant ru.org.amip.ClockSync android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package ru.org.amip.ClockSync | grep ACCESS_FINE_LOCATION | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant ACCESS_FINE_LOCATION permission to NTP client app."; fi; sleep 1
	printf "    ACCESS_COARSE_LOCATION"; adb shell pm grant ru.org.amip.ClockSync android.permission.ACCESS_COARSE_LOCATION > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package ru.org.amip.ClockSync | grep ACCESS_COARSE_LOCATION | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant ACCESS_COARSE_LOCATION permission to NTP client app."; fi; sleep 1

	printf "   Starting NTP Client Main-App..."
	adb shell su -c "am start -n ru.org.amip.ClockSync/.view.Main" > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
	if [ "$(adb shell ps | grep -c ru.org.amip.ClockSync)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to start NTP client main activity after install."; fi; sleep 1

	printf "   Starting NTP Client Preferences-Activity..."
	adb shell su -c "am start -n ru.org.amip.ClockSync/.view.EditPreferences" > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1		#have to start it to generate shared_prefs subdir where we copy config file, as well as the xml file
	if [ "$(adb shell dumpsys window windows | grep mCurrentFocus | grep -c ru.org.amip.ClockSync.view.EditPreferences)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to start NTP client prefs activity after install."; fi; sleep 1

	printf "   Configuring NTP client..."
	#TODO: Redo the following.. may work but I don't like it
	adb shell 'if [ ! -e /data/user/0/ru.org.amip.ClockSync/shared_prefs ]; then printf "    ERROR: shared_prefs does not exist.\n    Manually run: adb push ru.org.amip.ClockSync_preferences.xml /data/user/0/ru.org.amip.ClockSync/shared_prefs/\n                  adb shell su -c \"busybox sed -i [TICK]s/pool.ntp.org/'$CONNECTIONS_SERVER_ADDRESS'/[TICK] /data/user/0/ru.org.amip.ClockSync/shared_prefs/ru.org.amip.ClockSync_preferences.xml\"\n    "; fi'; sleep 1
	adb push ru.org.amip.ClockSync_preferences.xml /data/user/0/ru.org.amip.ClockSync/shared_prefs/ > /dev/null 2>&1; printf "."; sleep 1
	adb shell su -c "busybox sed -i 's/pool.ntp.org/'$CONNECTIONS_SERVER_ADDRESS'/' /data/user/0/ru.org.amip.ClockSync/shared_prefs/ru.org.amip.ClockSync_preferences.xml"; printf "."; sleep 1
	if [ "$(adb shell grep -c \"$CONNECTIONS_SERVER_ADDRESS\" /data/user/0/ru.org.amip.ClockSync/shared_prefs/ru.org.amip.ClockSync_preferences.xml)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to specifiy NTP client Connections server as the NTP server."; fi; sleep 1

	adb shell su -c "am start -a android.intent.action.MAIN -c android.intent.category.HOME" > /dev/null 2>&1; sleep 1
	}

doTabletTask_installSoftware_rtspCameraServer()
	{
	printf "  Installing RTSP camera server..."
	adb push rtspCameraServer.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1; printf "."; sleep 1
	adb install -r -g rtspCameraServer.apk > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1; printf "."; sleep 1		#NOTE: -g installs with all runtime permissions granted!
	if [ "$(adb shell ls -ld /data/user/0/com.spynet.camon | grep -v "No such file" | grep -c com.spynet.camon)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install RTSP camera server. No streaming video will be available from this device."; fi; sleep 1

	printf "   Starting RTSP camera server app for first time (to allow subsequent auto-start-on-boot, and setup)..."
	adb shell su -c "am start -n com.spynet.camon/.ui.MainActivity" > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
	if [ "$(adb shell ps | grep -c com.spynet.camon)" > "0" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to start RTSP camera server main activity after install."; fi; sleep 1

	printf "   Starting RTSP camera server preferences activity..."
	adb shell su -c "am start -n com.spynet.camon/.ui.SettingsActivity" > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1		#have to start it to generate shared_prefs subdir where we copy config file, as well as the xml file
	if [ "$(adb shell dumpsys window windows | grep mCurrentFocus | grep -c com.spynet.camon.ui.SettingsActivity)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to start RTSP camera server prefs activity after install."; fi; sleep 1

	printf "   Configuring RTSP camera server..."
	#TODO: Redo the following.. may work but I don't like it
	adb shell 'if [ ! -e /data/user/0/com.spynet.camon/shared_prefs ]; then printf "    ERROR: shared_prefs does not exist.\n    Manually run: adb push com.spynet.camon_preferences.xml /data/user/0/com.spynet.camon/shared_prefs/\n"; fi'; sleep 1
	adb push com.spynet.camon_preferences.xml /data/user/0/com.spynet.camon/shared_prefs/ > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell grep -c \"$CONNECTIONS_SERVER_ADDRESS\" /data/user/0/com.spynet.camon/shared_prefs/com.spynet.camon_preferences.xml)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to push shared-prefs XML file to device."; fi; sleep 1

	adb shell su -c "am start -a android.intent.action.MAIN -c android.intent.category.HOME" > /dev/null 2>&1; sleep 1
	}

doTabletTask_installSoftware_textToSpeech()
	{
	printf "  Installing Text-to-Speech Engine..."
	adb push googleTTS.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r googleTTS.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/com.google.android.tts | grep -v "No such file" | grep -c com.google.android.tts)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install Text-to-Speech engine."; fi; sleep 1
	}

doTabletTask_installSoftware_messagenet_evolution()
	{
	printf "  Installing Omni App..."
	adb push com.messagenetsystems.evolution.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r -t com.messagenetsystems.evolution.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/com.messagenetsystems.evolution | grep -v "No such file" | grep -c com.messagenetsystems.evolution)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install Omni main app."; fi; sleep 1
	}

doTabletTask_installSoftware_messagenet_evolution2()
	{
	printf "  Installing Omni v2 App..."
	adb push com.messagenetsystems.evolution2.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r -t com.messagenetsystems.evolution2.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/com.messagenetsystems.evolution2 | grep -v "No such file" | grep -c com.messagenetsystems.evolution2)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install Omni v2 main app."; fi; sleep 1

	printf "   Configuring runtime permissions via PackageManager...\n"
	printf "    READ_EXTERNAL_STORAGE..."; adb shell pm grant com.messagenetsystems.evolution2 android.permission.READ_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolution2 | grep READ_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant READ_EXTERNAL_STORAGE permission to Omni v2 app."; fi; sleep 1
	printf "    WRITE_EXTERNAL_STORAGE..."; adb shell pm grant com.messagenetsystems.evolution2 android.permission.WRITE_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolution2 | grep WRITE_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant WRITE_EXTERNAL_STORAGE permission to Omni v2 app."; fi; sleep 1
	printf "    ACCESS_FINE_LOCATION"; adb shell pm grant com.messagenetsystems.evolution2 android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolution2 | grep ACCESS_FINE_LOCATION | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant ACCESS_FINE_LOCATION permission to Omni v2 app."; fi; sleep 1
	printf "    ACCESS_COARSE_LOCATION"; adb shell pm grant com.messagenetsystems.evolution2 android.permission.ACCESS_COARSE_LOCATION > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolution2 | grep ACCESS_COARSE_LOCATION | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant ACCESS_COARSE_LOCATION permission to Omni v2 app."; fi; sleep 1
	printf "   USE_SIP "; adb shell pm grant com.messagenetsystems.evolution2 android.permission.USE_SIP > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolution2 | grep USE_SIP | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant USE_SIP permission to Omni v2 app."; fi; sleep 1
	printf "    RECORD_AUDIO"; adb shell pm grant com.messagenetsystems.evolution2 android.permission.RECORD_AUDIO > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolution2 | grep RECORD_AUDIO | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant RECORD_AUDIO permission to Omni v2 app."; fi; sleep 1
	}

doTabletTask_installSoftware_messagenet_evolutionwatchdog()
	{
	printf "  Installing Omni Watchdog..."
	adb push com.messagenetsystems.evolutionwatchdog.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r -t com.messagenetsystems.evolutionwatchdog.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/com.messagenetsystems.evolutionwatchdog | grep -v "No such file" | grep -c com.messagenetsystems.evolutionwatchdog)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install Omni watchdog app."; fi; sleep 1
	}

doTabletTask_installSoftware_messagenet_omniwatchdogwatcher()
	{
	printf "  Installing Omni Watchdog watcher (in /system/priv-app)..."
	adb push com.messagenetsystems.omniwatchdogwatcher.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb push com.messagenetsystems.omniwatchdogwatcher.apk /system/priv-app/ > /dev/null 2>&1; printf "."; sleep 1
	adb shell chmod 644 /system/priv-app/com.messagenetsystems.omniwatchdogwatcher.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -l /system/priv-app/com.messagenetsystems.omniwatchdogwatcher* | grep -v "No such file" | grep -c com.messagenetsystems.omniwatchdogwatcher)" = "1" ]; then 
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else 
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to install Omni watchdog watcher app."
		printf "   ${COLOR_ENTER_PROMPT}Try again? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "y" ]; then doTabletTask_installSoftware_messagenet_omniwatchdogwatcher; fi
	fi
	sleep 1
	}

doTabletTask_installSoftware_messagenet_evolutionupdater()
	{
	printf "  Installing Omni Updater..."
	adb push com.messagenetsystems.evolutionupdater.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r -t com.messagenetsystems.evolutionupdater.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/com.messagenetsystems.evolutionupdater | grep -v "No such file" | grep -c com.messagenetsystems.evolutionupdater)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to install Omni updater app."; fi; sleep 1

	printf "   Configuring runtime permissions via PackageManager...\n"
	printf "    READ_EXTERNAL_STORAGE..."; adb shell pm grant com.messagenetsystems.evolutionupdater android.permission.READ_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolutionupdater | grep READ_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant READ_EXTERNAL_STORAGE permission to Omni updater app."; fi; sleep 1
	printf "    WRITE_EXTERNAL_STORAGE..."; adb shell pm grant com.messagenetsystems.evolutionupdater android.permission.WRITE_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolutionupdater | grep WRITE_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant WRITE_EXTERNAL_STORAGE permission to Omni updater app."; fi; sleep 1

	printf "   Starting app as shell user so it starts automatically from now on..."
	if [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionupdater)" = "1" ]; then
		printf " ${COLOR_OK}Already started succesfully.${NOCOLOR}\n"
	else
		printf "\n"
		#adb_exitRootMode indentOverride "    "
		#if [ $? -eq 1 ]; then
#			printf "    Interacting with screen to start app ${COLOR_ATTENTION}(WARNING: Do NOT touch screen!)${NOCOLOR}..."
#			adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; printf "."; sleep 1
#			adb shell input touchscreen swipe 960 1050 960 500; printf "."; sleep 1; printf "."; sleep 1	#swipe up app drawer and give time for it to complete
#			adb shell input touchscreen tap 1440 450; printf "."; sleep 1; printf "."; sleep 1			#tap updater icon to start the app
#			if [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionupdater)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to initially start Omni updater app as shell user via GUI."; fi; sleep 1
	
#			adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; sleep 1
		#else 
		#	printf "    ${COLOR_ERROR}ERROR:${NOCOLOR} could not un-root. You must start app manually!\n"
		#fi

		printf "    Using ActivityManager to start app..."
		adb shell am start -a -n com.messagenetsystems.evolutionupdater/.StartupActivity > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionupdater)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${COLOR_ATTENTION} Manually start Evolution Updater app now!\n${COLOR_ENTER_PROMPT}"; read -p "Press Enter when done."; printf "${NOCOLOR}"; fi; sleep 1

		#adb_enterRootMode indentOverride "    "
	fi

	#adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; sleep 2
	}

doTabletTask_installSoftware_messagenet_evolutionflasherlights()
	{
	printf "  Installing Omni Flasher Lights App..."
	adb push com.messagenetsystems.evolutionflasherlights.apk /sdcard/ > /dev/null 2>&1; printf "."; sleep 1
	adb install -r -t com.messagenetsystems.evolutionflasherlights.apk > /dev/null 2>&1; printf "."; sleep 1
	if [ "$(adb shell ls -ld /data/user/0/com.messagenetsystems.evolutionflasherlights | grep -v "No such file" | grep -c com.messagenetsystems.evolutionflasherlights)" = "1" ]; then 
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to install Omni flasher lights app."
	fi
	sleep 1

	printf "   Configuring runtime permissions via PackageManager...\n"
	printf "    ACCESS_FINE_LOCATION"; adb shell pm grant com.messagenetsystems.evolutionflasherlights android.permission.ACCESS_FINE_LOCATION > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolutionflasherlights | grep ACCESS_FINE_LOCATION | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant ACCESS_FINE_LOCATION permission to Omni flasher lights app."; fi; sleep 1
	printf "    ACCESS_COARSE_LOCATION"; adb shell pm grant com.messagenetsystems.evolutionflasherlights android.permission.ACCESS_COARSE_LOCATION > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolutionflasherlights | grep ACCESS_COARSE_LOCATION | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant ACCESS_COARSE_LOCATION permission to Omni flasher lights app."; fi; sleep 1
	printf "    READ_EXTERNAL_STORAGE..."; adb shell pm grant com.messagenetsystems.evolutionflasherlights android.permission.READ_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolutionflasherlights | grep READ_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant READ_EXTERNAL_STORAGE permission to Omni flasher lights app."; fi; sleep 1
	printf "    WRITE_EXTERNAL_STORAGE..."; adb shell pm grant com.messagenetsystems.evolutionflasherlights android.permission.WRITE_EXTERNAL_STORAGE > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
		if [ "$(adb shell dumpsys package com.messagenetsystems.evolutionflasherlights | grep WRITE_EXTERNAL_STORAGE | grep -c "granted=true")" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"
		else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "ERROR" "Failed to grant WRITE_EXTERNAL_STORAGE permission to Omni flasher lights app."; fi; sleep 1

#	printf "   Starting app as shell user so it starts automatically from now on..."
#	if [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionflasherlights)" = "1" ]; then
#		printf " ${COLOR_OK}Already started succesfully.${NOCOLOR}\n"
#	else
#		printf "\n"
		#adb_exitRootMode indentOverride "    "
		#if [ $? -eq 1 ]; then
#			printf "    Interacting with screen to start app ${COLOR_ATTENTION}(WARNING: Do NOT touch screen!)${NOCOLOR}..."
#			adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; printf "."; sleep 1
#			adb shell input touchscreen swipe 960 1050 960 500; printf "."; sleep 1; printf "."; sleep 1	#swipe up app drawer and give time for it to complete
#			adb shell input touchscreen tap 1440 450; printf "."; sleep 1; printf "."; sleep 1			#tap updater icon to start the app
	
#			MAX_RETRIES=15; while [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionflasherlights)" = "0" ] && [ $MAX_RETRIES -gt 0 ]; do printf "."; sleep 1; (( MAX_RETRIES-- )); done
#			if [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionflasherlights)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to initially start Omni flasher lights app as shell user via GUI."; fi; sleep 1
	
#			printf "    Waiting for app's StartupActivity to self-finish...";
#			for i in {1..12}; do printf "."; sleep 1; done; printf " done.\n"

		#	adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; sleep 2
		#else 
		#	printf "    ${COLOR_ERROR}ERROR:${NOCOLOR} could not un-root. You must start app manually!\n"
		#fi
		#printf "    Using ActivityManager to start app..."
		#adb shell am start -a -n com.messagenetsystems.evolutionflasherlights/.StartupActivity > /dev/null 2>&1; printf "."; sleep 2
		#if [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionflasherlights)" = "1" ]; then printf " ${COLOR_OK}done.${NOCOLOR}\n"; else printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"; addToProblemLog "WARN" "Failed to initially start Omni flasher lights app via ActivityManager."; fi; sleep 1
	
		#adb_enterRootMode indentOverride "    "
#	fi

	#adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; sleep 2
	}

doTabletTask_configure_evolutionflasherlights()
	{
	printf "  Configure Omni Flasher Lights App:\n"

	printf "   ${COLOR_ATTENTION}ATTENTION:${NOCOLOR} Connect DC POWER cable to light controller NOW! (if not already connected)\n${COLOR_ENTER_PROMPT}"
	read -p "   Press Enter to continue."; printf "${NOCOLOR}"

	#printf "   Rebooting tablet..."
	#adb shell su -c '/system/bin/svc power shutdown' > /dev/null 2>&1; for i in {1..10}; do printf "."; sleep 1; done

	#printf "   Soft-rebooting tablet..."
	#adb shell su -c 'setprop ctl.restart zygote' > /dev/null 2>&1; for i in {1..10}; do printf "."; sleep 1; done
	#ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"
	#while [ "$ADB_CONNECTED_DEVICE" = "" ] || [ "$ADB_CONNECTED_DEVICE" = "*" ]; do printf "."; sleep 3; ADB_CONNECTED_DEVICE="$(adb devices | head -2 | tail -n1 | awk '{print $1}')"; done; printf " ${COLOR_OK}booted up.${NOCOLOR}\n"

	printf "   Loading app ${COLOR_ATTENTION}(if it takes a long time, start app manually)${NOCOLOR}..."
	if [ "$(adb shell dumpsys window | grep "mCurrentFocus" | grep -c "com.messagenetsystems.evolutionflasherlights")" = "1" ]; then
		printf " ${COLOR_OK}already loaded.${NOCOLOR}\n"
	else
		adb shell am start -a -n com.messagenetsystems.evolutionflasherlights/.StartupActivity > /dev/null 2>&1; sleep 1; printf "."; sleep 1; printf "."
		while [ "$(adb shell dumpsys window | grep "mCurrentFocus" | grep -c "com.messagenetsystems.evolutionflasherlights")" = "0" ]; do printf "."; sleep 2; done; printf " ${COLOR_OK}done.${NOCOLOR}\n"
	fi

	#printf "   Loading the app..."
	#adb shell am start -a -n com.messagenetsystems.evolutionflasherlights/.StartupActivity > /dev/null 2>&1; sleep 1; printf "."; sleep 1; printf ".\n"
	#printf "   ${COLOR_ATTENTION}Launch the EvolutionFlasherLights app now!${COLOR_ENTER_PROMPT}"; read -p " Press enter when it loads."; printf "${NOCOLOR}"
	#printf "   ${COLOR_ATTENTION}Launch the EvolutionFlasherLights app now!${NOCOLOR} Waiting."
	#while [ "$(adb shell ps | grep -c com.messagenetsystems.evolutionflasherlights)" = "0" ]; do printf "."; sleep 2; done; printf "\n"

	#printf "   ${COLOR_ATTENTION}WAIT! "; for i in {1..12}; do printf "."; sleep 1; done; printf ")\n"

	#sleep 3

	printf "   Cancelling auto finish... ${COLOR_ATTENTION}please wait${NOCOLOR}..."; printf "."; sleep 1; printf ".\n"
	adb shell input touchscreen tap 960 300; sleep 1; adb shell input touchscreen tap 960 300; sleep 5
	#printf "   ${COLOR_ATTENTION}TAP the \"CANCEL AUTO ACTIVITY FINISH\" button NOW! (if you wait too long, start the app again)${COLOR_ENTER_PROMPT}"; read -p " Press enter when done."; printf "${NOCOLOR}"

	# Prompt user to connect and setup the lights properly
	printf "   ${COLOR_ATTENTION}ATTENTION:${NOCOLOR} On tablet screen, you should now tap the following buttons (you may skip #1 if already done for this unit)...\n    1) Set light controller to safe value\n"
	sleep 5 #force them to wait so they're inclined to actually do the tasks
	printf "${COLOR_ENTER_PROMPT}"; read -p "   Press Enter when finished..."; printf "${NOCOLOR}"

	printf "   ${COLOR_ATTENTION}ATTENTION:${NOCOLOR} On tablet screen, you should now tap the following buttons (you may skip #2 if already done for this unit)...\n    1) Associate lights with this Omni\n    2) Connect LED light cable to light controller\n    3) Test colors (especially white)\n"
	sleep 5 #force them to wait so they're inclined to actually test
	printf "${COLOR_ENTER_PROMPT}"; read -p "   Press Enter when finished..."; printf "${NOCOLOR}"

	# Check whether association was successful
	if [ "$(adb shell grep -c '\<lightControllerMacAddress\>NotAssociated\</lightControllerMacAddress\>' /storage/emulated/0/evoProvisionData.xml)" = "0" ]; then
		sleep 1
		LIGHT_CONTROLLER_MAC_ADDRESS="$(adb shell grep lightControllerMacAddress /storage/emulated/0/evoProvisionData.xml | cut -d'>' -f2 | cut -d'<' -f1)"; sleep 1
		printf "   Association ${COLOR_OK}OK${NOCOLOR} (%s)\n" "$LIGHT_CONTROLLER_MAC_ADDRESS"
	else
		printf "   Association ${COLOR_ERROR}NOT PRESENT!${NOCOLOR}\n"
		printf "   ${COLOR_ENTER_PROMPT}Really skip associating the lights now? (y/n) -->  ${NOCOLOR}"; read VERIFY_CONTINUE
		if [ "$VERIFY_CONTINUE" = "y" ]; then
			addToProblemLog "WARN" "Flasher light controller associate has been skipped by provisioner."
			printf "   ${COLOR_ATTENTION}Association Was Not Performed!${NOCOLOR}\n   (You MUST ensure light association is done PROPERLY)\n"
		else
			doTabletTask_configure_evolutionflasherlights
		fi
	fi
	sleep 1
	}

doTabletTask_installProvisioningFile()
	{
	printf "  Installing Provisioning File..."
	adb push $PROV_DATA_FILE /storage/emulated/0/ > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
	if [ "$(adb shell ls -l /storage/emulated/0/$PROV_DATA_FILE_NAME | grep -v "No such file" | grep -c $PROV_DATA_FILE_NAME)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to install provisioning XML file."
	fi
	sleep 1
	}

doTabletTask_installRuntimeFlagFile()
	{
	printf "  Installing Runtime Flags File..."
	adb push $RUNTIME_FLAGS_FILE /storage/emulated/0/ > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
	if [ "$(adb shell ls -l /storage/emulated/0/$RUNTIME_FLAGS_FILE_NAME | grep -v "No such file" | grep -c $RUNTIME_FLAGS_FILE_NAME)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to install runtime flags file."
	fi
	sleep 1
	}

doTabletTask_installCameraInfoFile()
	{
	printf "  Installing Camera Info File..."
	adb push $CAMERA_INFO_FILE /storage/emulated/0/ > /dev/null 2>&1; printf "."; sleep 1; printf "."; sleep 1
	if [ "$(adb shell ls -l /storage/emulated/0/$CAMERA_INFO_FILE_NAME | grep -v "No such file" | grep -c $CAMERA_INFO_FILE_NAME)" = "1" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to install camera info file."
	fi
	sleep 1
	}


##################################################################################################
generateCameraInfoFile()
	{
	# Dump device's camera information to temp file
	adb shell dumpsys media.camera > /tmp/caminfo

	# Parse the camera count and do a best-guess of which one is the front camera
	# TODO: later, check for "FRONT" or "BACK" and use that to be more certain! This is fine for now with known firmware, though.
	CAMINFO_NUM_CAMS=$(grep "Number of camera devices" /tmp/caminfo | awk '{printf $5}')
	CAMINFO_FRONT_CAM_ID=0;
	if [ "$CAMINFO_NUM_CAMS" == "" ]; then
		printf "   ${COLOR_ATTENTION}WARNING:${NOCOLOR} Camera counting problem: Count not available.\n"
		addToProblemLog "WARN" "Camera count problem (dumpsys media.camera 'Number of camera devices' not available). Defaulting to camera device #0."
	elif [ $CAMINFO_NUM_CAMS -eq 1 ]; then
		CAMINFO_FRONT_CAM_ID=0
	elif [ $CAMINFO_NUM_CAMS -eq 2 ]; then
		CAMINFO_FRONT_CAM_ID=1
	else
		printf "   ${COLOR_ATTENTION}WARNING:${NOCOLOR} Camera counting problem: Unexpected count value (%s).\n" "$CAMINFO_NUM_CAMS"
		addToProblemLog "WARN" "Camera count problem (dumpsys media.camera 'Number of camera devices' count unexpected: %s). Defaulting to camera device #0." "$CAMINFO_NUM_CAMS"
	fi

	# Generate the info file
	printf " Generating a camera hardware information file... "

	printf "# Omni Camera Hardware Information\n" > $CAMERA_INFO_FILE
	printf "# (note: front camera value is read in as an override by android_camera.c in evolution app)\n" > $CAMERA_INFO_FILE
	printf "%s=%d\n" "$CAMINFO_VAR_NAME_NUM_OF_CAMS" "$CAMINFO_NUM_CAMS" >> $CAMERA_INFO_FILE
	printf "%s=%d\n" "$CAMINFO_VAR_NAME_FRONT_CAM_ID" "$CAMINFO_FRONT_CAM_ID" >> $CAMERA_INFO_FILE

	printf "\n" >> $CAMERA_INFO_FILE
	printf "# Capabilities Information:\n" >> $CAMERA_INFO_FILE

	# Verify file creation
	if [ -e "$CAMERA_INFO_FILE" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to generate the camera info file during provisioning."
	fi
	}

generateRuntimeFlagsFile()
	{
	printf "  Generating a runtime-flags file... "
	
	printf "# Omni App Runtime Flags\n" > $RUNTIME_FLAGS_FILE
	printf "# These control behavior of the software applications\n" > $RUNTIME_FLAGS_FILE
	printf "# Generated on %s\n" "$(date)" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Disallow all tablet reboot commands, across all apps (0=allow, 1=disallow)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_REBOOT_DISALLOW_ALL_APPS" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Disallow all Wi-Fi reset routines (could help stabilize connection when troubleshooting) (0=allow, 1=disallow)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_WIFI_RECONNECT_DISALLOW" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Disallow main app from going past StartupActivity screen (0=allow, 1=disallow)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_MAINAPP_START_SERVICE_DISALLOW" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Disallow automatic updating (0=allow, 1=disallow)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_UPDATE_DOWNLOAD_DISALLOW" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_UPDATE_INSTALL_DISALLOW" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Time window (in 24hr format) that background downloads of updates can happen\n# (this overrides update app's strings.xml value, leave blank to not override)\n# (value example = 0:00, 23:59, etc.)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=\n" "$RUNTIME_FLAG_NAME_UPDATE_DOWNLOAD_WINDOW_START" >> $RUNTIME_FLAGS_FILE
	printf "%s=\n" "$RUNTIME_FLAG_NAME_UPDATE_DOWNLOAD_WINDOW_END" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Time window (in 24hr format) that installation of updates can happen\n# (this overrides update app's strings.xml value, leave blank to not override)\n# (value example = 0:00, 23:59, etc.)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=\n" "$RUNTIME_FLAG_NAME_UPDATE_INSTALL_WINDOW_START" >> $RUNTIME_FLAGS_FILE
	printf "%s=\n" "$RUNTIME_FLAG_NAME_UPDATE_INSTALL_WINDOW_END" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Disallow periodic app restarts (0=allow, 1=disallow)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_PERIODIC_APP_RESTART_DISALLOW" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	# NOT YET IMPLEMENTED
	printf "# Time window (in 24hr format) that periodic app restarts can happen\n# (this overrides omniwatchdogwatcher app's strings.xml value, leave blank to not override)\n# (value example = 0:00, 23:59, etc.)\n" >> $RUNTIME_FLAGS_FILE
	printf "#%s=\n" "$RUNTIME_FLAG_NAME_PERIODIC_APP_RESTART_WINDOW_START" >> $RUNTIME_FLAGS_FILE
	printf "#%s=\n" "$RUNTIME_FLAG_NAME_PERIODIC_APP_RESTART_WINDOW_END" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Debug show info flag (0=don't show, 1=show)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_DEBUG_SHOW_INFO" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	printf "# Disallow AJAX acquisition of active messages (0=allow, 1=disallow)\n" >> $RUNTIME_FLAGS_FILE
	printf "%s=0\n" "$RUNTIME_FLAG_NAME_REQUEST_ACTIVE_MSGS_DISALLOW" >> $RUNTIME_FLAGS_FILE
	printf "\n" >> $RUNTIME_FLAGS_FILE

	if [ -e "$RUNTIME_FLAGS_FILE" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to generate the runtime flags file during provisioning."
	fi
	}

# Create the XML file that the Omni app depends on to set itself up.
# It shall use all the data we've collected up to this point.
generateProvisioningFile_xml()
	{
	printf "  Generating a provisioning XML data file... "
	
	printf "<?xml version=\"1.0\" ?>\n\n" > $PROV_DATA_FILE
	printf "<provisioning>\n\n" >> $PROV_DATA_FILE
	
	printf "	<overwriteExistingSharedPrefsWithThese>false</overwriteExistingSharedPrefsWithThese>\n" >> $PROV_DATA_FILE
	
	printf "	<provDataFile>\n" >> $PROV_DATA_FILE
	printf "		<createdBy>%s</createdBy>\n" "$USER_INITIALS" >> $PROV_DATA_FILE
	printf "		<createdWhen>%s</createdWhen>\n" "$(date)" >> $PROV_DATA_FILE
	printf "		<provStartedWhen>%s</provStartedWhen>\n" "$DATETIME_PROVISION_START" >> $PROV_DATA_FILE
	printf "		<provStartedWhenHuman>%s</provStartedWhenHuman>\n" "$DATETIME_PROVISION_START_HUMAN" >> $PROV_DATA_FILE
	printf "		<provThisDeviceStartedWhen>%s</provThisDeviceStartedWhen>\n" "$DATETIME_PROVISION_START_THIS" >> $PROV_DATA_FILE
	printf "		<provThisDeviceStartedWhenHuman>%s</provThisDeviceStartedWhenHuman>\n" "$DATETIME_PROVISION_START_THIS_HUMAN" >> $PROV_DATA_FILE
	printf "	</provDataFile>\n\n" >> $PROV_DATA_FILE

	printf "	<customerInfo>\n" >> $PROV_DATA_FILE
	printf "		<id>%s</id>\n" "$CUSTOMER_ID" >> $PROV_DATA_FILE
	printf "	</customerInfo>\n\n" >> $PROV_DATA_FILE
	
	printf "	<deviceInfo>\n" >> $PROV_DATA_FILE
	printf "		<mnsSerial>%s</mnsSerial>\n" "$OMNI_SERIAL_NUMBER" >> $PROV_DATA_FILE
	printf "		<arbitraryLabel>%s</arbitraryLabel>\n" "$OMNI_ARBITRARY_LABEL" >> $PROV_DATA_FILE
	printf "	</deviceInfo>\n\n" >> $PROV_DATA_FILE

	printf "	<peripheralInfo>\n" >> $PROV_DATA_FILE
	printf "		<lightControllerMacAddress>NotAssociated</lightControllerMacAddress>\n" >> $PROV_DATA_FILE		# This is just a placeholder for later association
	printf "	</peripheralInfo>\n\n" >> $PROV_DATA_FILE
	
	printf "	<wifi>\n" >> $PROV_DATA_FILE
	printf "		<use>%d</use>\n" $WIFI_USE >> $PROV_DATA_FILE
	printf "		<mac>%s</mac>\n" "$WIFI_MAC_ADDRESS" >> $PROV_DATA_FILE
	printf "		<macNoColon>%s</macNoColon>\n" "$WIFI_MAC_ADDRESS_NOCOLON" >> $PROV_DATA_FILE
	printf "		<ssid>%s</ssid>\n" "$WIFI_SSID" >> $PROV_DATA_FILE
	printf "		<security>%s</security>\n" "$WIFI_SECURITY_TYPE" >> $PROV_DATA_FILE
	printf "		<password>%s</password>\n" "$WIFI_PASSWORD" >> $PROV_DATA_FILE
	printf "		<method>%s</method>\n" "$WIFI_IP_METHOD" >> $PROV_DATA_FILE
	printf "		<ip>%s</ip>\n" "$WIFI_IP_ADDRESS" >> $PROV_DATA_FILE
	printf "		<subnet>%s</subnet>\n" "$WIFI_SUBNET" >> $PROV_DATA_FILE
	printf "		<gateway>%s</gateway>\n" "$WIFI_GATEWAY" >> $PROV_DATA_FILE
	printf "		<dns1>%s</dns1>\n" "$WIFI_DNS1" >> $PROV_DATA_FILE
	printf "		<dns2>%s</dns2>\n" "$WIFI_DNS2" >> $PROV_DATA_FILE
	printf "	</wifi>\n\n" >> $PROV_DATA_FILE

	printf "	<ethernet>\n" >> $PROV_DATA_FILE
	printf "		<use>%d</use>\n" $ETHERNET_USE >> $PROV_DATA_FILE
	printf "		<mac>%s</mac>\n" "$ETHERNET_MAC_ADDRESS" >> $PROV_DATA_FILE
	printf "		<macNoColon>%s</macNoColon>\n" "$ETHERNET_MAC_ADDRESS_NOCOLON" >> $PROV_DATA_FILE
	printf "		<method>%s</method>\n" "$ETHERNET_IP_METHOD" >> $PROV_DATA_FILE
	printf "		<ip>%s</ip>\n" "$ETHERNET_IP_ADDRESS" >> $PROV_DATA_FILE
	printf "		<subnet>%s</subnet>\n" "$ETHERNET_SUBNET" >> $PROV_DATA_FILE
	printf "		<gateway>%s</gateway>\n" "$ETHERNET_GATEWAY" >> $PROV_DATA_FILE
	printf "		<dns1>%s</dns1>\n" "$ETHERNET_DNS1" >> $PROV_DATA_FILE
	printf "		<dns2>%s</dns2>\n" "$ETHERNET_DNS2" >> $PROV_DATA_FILE
	printf "	</ethernet>\n\n" >> $PROV_DATA_FILE

	printf "	<server>\n" >> $PROV_DATA_FILE
	printf "		<ipv4>%s</ipv4>\n" $CONNECTIONS_SERVER_ADDRESS >> $PROV_DATA_FILE
	printf "	</server>\n\n" >> $PROV_DATA_FILE

	printf "</provisioning>\n\n" >> $PROV_DATA_FILE

	if [ -e "$PROV_DATA_FILE" ]; then
		printf " ${COLOR_OK}done.${NOCOLOR}\n"
	else
		printf " ${COLOR_ERROR}FAILED!${NOCOLOR}\n"
		addToProblemLog "ERROR" "Failed to generate the provisioning XML file."
	fi
	}

generateSummaryReportAndPrint_tablet()
	{
	# Generate file for this Omni
	SUMMARY_REPORT_FILE_PATH="~/OmniProvisioning"
	CURRENT_DIR="$(pwd)"							#remember current dir so we can go back to it
	SUMMARY_REPORT_FILE_PATH="$(eval echo $SUMMARY_REPORT_FILE_PATH)"	#evaluate any special operators (like ~ for user dir)
	mkdir $SUMMARY_REPORT_FILE_PATH > /dev/null 2>&1			#make sure the path exists
	cd "$SUMMARY_REPORT_FILE_PATH"						#change into the path
	SUMMARY_REPORT_FILE_PATH="$(pwd)"					#remember the absolute path of it
	cd "$CURRENT_DIR"							#change back to where we were
	SUMMARY_REPORT_FILE_NAME="$(echo $CUSTOMER_ID).provreport$(echo $OMNI_ARBITRARY_LABEL_FORFILENAME).$(echo $OMNI_SERIAL_NUMBER).$(echo $DATETIME_PROVISION_START_THIS).txt"	#construct the filename where we'll save the info
	SUMMARY_REPORT_FILE_WHOLE="$(echo $SUMMARY_REPORT_FILE_PATH)/$(echo $SUMMARY_REPORT_FILE_NAME)"					#construct the complete path and filename
	touch "$SUMMARY_REPORT_FILE_WHOLE"					#actually create the file

	# Populate the file
	printf " OMNI PROVISIONING SUMMARY REPORT\n" > "$SUMMARY_REPORT_FILE_WHOLE" 
	printf "  Provisioning Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Provisioner:      %s\n" "$USER_INITIALS" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Provision Date:   %s\n" "$DATETIME_PROVISION_START_THIS_HUMAN" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Errors Counted:   %d\n" "$ERROR_COUNT_PROV" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Warnings Counted: %d\n" "$WARNING_COUNT_PROV" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "  Customer Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Customer ID:      %s\n" "$CUSTOMER_ID" >> "$SUMMARY_REPORT_FILE_WHOLE"	
	printf "  Device Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Arbitrary Label:  %s\n" "$OMNI_ARBITRARY_LABEL" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Serial Number:    %s\n" "$OMNI_SERIAL_NUMBER" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "  Wi-Fi Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	if [ $WIFI_USE -eq 0 ]; then
	 printf "	(no Wi-Fi was elected to be configured)\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	else
	 printf "	MAC Address:      %s (%s)\n" "$WIFI_MAC_ADDRESS" "$WIFI_MAC_ADDRESS_NOCOLON" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	SSID:             %s\n" "$WIFI_SSID" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	Security:         %s\n" "$WIFI_SECURITY_TYPE" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	Password:         %s\n" "$WIFI_PASSWORD" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	IP Method:        %s\n" "$WIFI_IP_METHOD" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	IP Address:       %s\n" "$WIFI_IP_ADDRESS" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	Subnet Mask:      %s\n" "$WIFI_SUBNET" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	Gateway:          %s\n" "$WIFI_GATEWAY" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	DNS Server:       %s\n" "$WIFI_DNS1" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	DNS Server:       %s\n" "$WIFI_DNS2" >> "$SUMMARY_REPORT_FILE_WHOLE"
	fi
	printf "  Wired Ethernet Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	if [ $ETHERNET_USE -eq 0 ]; then
	 printf "	(no wired Ethernet was elected to be configured)\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	else
	 printf "	MAC Address:      %s (%s)\n" "$ETHERNET_MAC_ADDRESS" "$ETHERNET_MAC_ADDRESS_NOCOLON" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	IP Method:        %s\n" "$ETHERNET_IP_METHOD" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	IP Address:       %s\n" "$ETHERNET_IP_ADDRESS" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	Subnet Mask:      %s\n" "$ETHERNET_SUBNET" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	Gateway:          %s\n" "$ETHERNET_GATEWAY" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	DNS Server:       %s\n" "$ETHERNET_DNS1" >> "$SUMMARY_REPORT_FILE_WHOLE"
	 printf "	DNS Server:       %s\n" "$ETHERNET_DNS2" >> "$SUMMARY_REPORT_FILE_WHOLE"
	fi
	printf "  Flasher Light Controller Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	MAC Address:      %s\n" "$LIGHT_CONTROLLER_MAC_ADDRESS" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "  Connections & Software Information:\n" >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "	Server Address:   %s\n" $CONNECTIONS_SERVER_ADDRESS >> "$SUMMARY_REPORT_FILE_WHOLE"
	printf "\n" >> "$SUMMARY_REPORT_FILE_WHOLE"

	# Concatenate the problems log into the report file
	cat "$THISOMNI_PROBLEMLOG_PROV_TEMPFILE_WHOLE" >> "$SUMMARY_REPORT_FILE_WHOLE"

	# Print the report contents to the screen
	printf "**********************************************************\n"
	cat "$SUMMARY_REPORT_FILE_WHOLE"
	printf "**********************************************************\n"
	}

copyFileToMdrive()
	{
	printf " Copying files to M-Drive..."
	if [ "$1" = "" ]; then
		printf " ${COLOR_ERROR}Error:${NOCOLOR} No filename provided. Doing nothing.\n"
	elif [ ! -f "$1" ]; then
		printf " ${COLOR_ERROR}Error:${NOCOLOR} Invalid file (\"%s\") provided. Doing nothing.\n" "$1"
	else
		MOUNT_POINT="/mnt/mdrive_forOmniProvisioning"
		MOUNT_POINT_DESTINATION="$MOUNT_POINT/array/OmniProvisioning"

		if [ "$2" = "" ]; then
			printf " ${COLOR_ATTENTION}Warning:${NOCOLOR} No customer ID provided. There will be no subdirectory organization.\n"
			MOUNT_POINT_SUB_DESTINATION="$MOUNT_POINT_DESTINATION"
		else
			MOUNT_POINT_SUB_DESTINATION="$MOUNT_POINT_DESTINATION/$2"
		fi

		# take care of mounting
		if [ "$(mount | grep -c mdrive_forOmniProvisioning)" = "0" ]; then
			#ensure mountpoint exists
			mkdir -p "$MOUNT_POINT" > /dev/null 2>&1; printf "."
	
			#mount mdrive to the mountpoint
			mount -t nfs 192.168.1.59:/mdrive/ "$MOUNT_POINT" > /dev/null 2>&1; printf "."
	
			#verify mounted
			if [ "$(mount | grep -c "$MOUNT_POINT")" = "0" ]; then
				printf " ${COLOR_ERROR}FAILED!${NOCOLOR} Unable to mount M-Drive.\n"
				return 0
			fi
		fi

		# setup directory organization
		mkdir -p "$MOUNT_POINT_SUB_DESTINATION"

		# copy file to mount
		RES=$(cp "$1" "$MOUNT_POINT_SUB_DESTINATION/") > /dev/null 2>&1; printf "."
	fi
	}


# WPA_CLI METHOD
#printf " Configuring Wi-Fi... \n"
#printf "  Turning on tablet Wi-Fi... "
#adb shell su -c "svc wifi enable" > /dev/null 2>&1; sleep 5; printf "done.\n"
#printf "  Connecting to WPA supplicant and adding network information... "
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 add_network" > /dev/null 2>&1
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 set_network 0 auth_alg OPEN" > /dev/null 2>&1
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 set_network 0 key_mgmt WPA-PSK" > /dev/null 2>&1
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 set_network 0 ssid \"$WIFI_SSID\""
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 set_network 0 proto RSN" > /dev/null 2>&1
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 set_network 0 mode 0" > /dev/null 2>&1
#adb shell su -c "wpa_cli -p /data/misc/wifi/sockets/ -i wlan0 set_network 0 psk \"$WIFI_PASSWORD\""
#printf "done.\n"
#sleep 5
#printf "  Connecting to network... ";		adb shell su -c "" > /dev/null 2>&1; printf "done.\n"
#printf "  Saving network information... ";	adb shell su -c "" > /dev/null 2>&1; printf "done.\n"
#printf "\n"

############################################################################
#NOTE: This must be at the very bottom so all functions are available!
clear
mainRoutine



