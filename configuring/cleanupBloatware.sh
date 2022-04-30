#!/bin/bash
############################################################################
# Script for cleanup of bloatware on a tablet.
# Tablet must be connected via USB to the machine running this script.
# Machine running this script must have adb (Android Debugging Bridge).
#
# Revision History:
# 2018.08.20	Chris Rider	Creation. Tested to work with Multigold 13"
############################################################################

# List of packages installed on tablet from factory...
#package:com.android.cts.priv.ctsshim
#package:com.android.providers.telephony
#package:com.android.providers.calendar
#package:com.android.providers.media
#package:com.android.wallpapercropper
#package:com.android.documentsui
#package:com.android.externalstorage
#package:com.android.htmlviewer
#package:com.android.quicksearchbox
#package:com.android.mms.service
#package:com.android.providers.downloads
#package:com.android.browser
#package:com.android.soundrecorder
#package:com.android.defcontainer
#package:com.android.providers.downloads.ui
#package:com.android.pacprocessor
#package:com.android.certinstaller
#	package:android.rockchip.update.service
#package:android
#package:com.android.contacts
#package:com.android.camera2
#package:com.android.egg
#package:com.android.mtp
#package:com.android.launcher3
#package:com.android.backupconfirm
#package:com.android.provision
#package:com.android.statementservice
#package:com.android.calendar
#package:com.android.apkinstaller
#package:com.android.providers.settings
#package:com.android.sharedstoragebackup
#package:com.android.printspooler
#package:com.android.dreams.basic
#package:com.android.webview
#package:com.android.rk
#package:com.android.inputdevices
#package:com.android.retaildemo
#package:com.android.musicfx
#package:com.rockchip.wfd
#package:android.ext.shared
#package:com.android.onetimeinitializer
#package:com.android.server.telecom
#package:com.android.keychain
#package:com.android.printservice.recommendation
#package:com.android.gallery3d
#package:com.google.android.tts
#package:android.ext.services
#package:com.android.packageinstaller
#package:com.svox.pico
#package:com.android.proxyhandler
#package:com.android.inputmethod.latin
#package:com.android.managedprovisioning
#package:com.android.rk.mediafloat
#package:com.android.dreams.phototable
#package:com.android.smspush
#package:com.android.wallpaper.livepicker
#	package:com.cghs.stresstest
#package:com.android.storagemanager
#package:jp.co.omronsoft.openwnn
#package:com.android.bookmarkprovider
#package:com.android.settings
#package:com.android.calculator2
#package:com.android.cts.ctsshim
#package:com.android.vpndialogs
#package:com.android.email
#package:com.android.music
#package:com.android.phone
#package:com.android.shell
#package:com.android.wallpaperbackup
#package:com.android.providers.blockednumber
#package:com.android.providers.userdictionary
#package:com.android.location.fused
#package:com.android.deskclock
#package:com.android.systemui
#package:com.android.bluetoothmidiservice
#package:com.DeviceTest
#package:com.android.bluetooth
#package:com.android.wallpaperpicker
#package:com.android.providers.contacts
#package:com.android.captiveportallogin
#package:android.rk.RockVideoPlayer

printf "**************************************************************\n"
printf " MessageNet Evolution Tablet Bloatware Cleanup\n"

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
printf " Cleaning up bloatware on device...\n"
printf "   "
adb root
sleep 3

#printf "   "
#adb remount
#sleep 3

#printf "   "
#adb shell "echo \"ro.serialno=$DESIRED_SERIAL\" >> /system/build.prop"
#sleep 2

printf "   " && adb shell 'pm disable com.cghs.stresstest'
printf "   " && adb shell 'pm disable android.rockchip.update.service'

#printf "rebooting tablet"
#adb reboot

printf "\n\n"
