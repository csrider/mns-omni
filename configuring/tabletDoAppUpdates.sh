# Run this as root user or by su -c method!
# Note: remember on Android, to run the script using 'sh' command, rather than making executable.
#

# Ex:   sh omniTabletUpdate.sh com.messagenetsystems.evolution_release.apk com.messagenetsystems.evolution
# Ex:   su -c "sh omniTabletUpdate.sh com.messagenetsystems.evolution_release.apk com.messagenetsystems.evolution"
#OMNIFILE=$1
#OMNINAME=$2

SERVER_ADDRESS=192.168.1.58

OMNIFILE_MAINAPP=com.messagenetsystems.evolution.apk
OMNIFILE_WATCHDOG=com.messagenetsystems.evolutionwatchdog.apk

OMNINAME_MAINAPP=com.messagenetsystems.evolution
OMNINAME_WATCHDOG=com.messagenetsystems.evolutionwatchdog

# Stop watchdog so it won't interrupt things
am force-stop $OMNINAME_WATCHDOG

# Also stop the main app, so it won't start the watchdog
am force-stop $OMNINAME_MAINAPP

# Download files from server
busybox wget -O /sdcard/$OMNIFILE_MAINAPP $SERVER_ADDRESS/~silentm/$OMNIFILE_MAINAPP
busybox wget -O /sdcard/$OMNIFILE_WATCHDOG $SERVER_ADDRESS/~silentm/$OMNIFILE_WATCHDOG

# Reinstall main app and restart it
am force-stop $OMNINAME_MAINAPP
pm install -r -t /sdcard/$OMNIFILE_MAINAPP
am start -n $OMNINAME_MAINAPP/$OMNINAME_MAINAPP.StartupActivity

# Reinstall watchdog app and start it
pm install -r -t /sdcard/$OMNIFILE_WATCHDOG
am start -n $OMNINAME_WATCHDOG/$OMNINAME_WATCHDOG.StartupActivity

# Print out processes to verify things are running
sleep 3
ps | grep $OMNINAME_MAINAPP

# Reboot the device
#reboot
