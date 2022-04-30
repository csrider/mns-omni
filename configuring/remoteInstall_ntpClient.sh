#!/bin/bash
############################################################################
# Script for network-installing an NTP client to an Omni.
#
# Revision History:
# 2019.01.10	Chris Rider	Created.
############################################################################


############################################################################
### Function: Prepare provisioning data file
prepareProvisioningDataFile() {
printf " Creating a provisioning data file... "
PROV_DATA_FILE="/tmp/evoProvisionData.xml"

printf "<?xml version=\"1.0\" ?>\n\n" > $PROV_DATA_FILE

printf "<provDataFile>\n" >> $PROV_DATA_FILE
printf "	<createdWhen>%s</createdWhen>\n" "$(date)" >> $PROV_DATA_FILE
printf "</provDataFile>\n\n" >> $PROV_DATA_FILE

printf "done (%s).\n" $PROV_DATA_FILE
printf "\n"
}

############################################################################
### Function: Save provisioning data file to device
installProvisioningDataFile() {
printf " Installing provisioning data file to device... "
adb push /tmp/evoProvisionData.xml /storage/emulated/0/ > /dev/null 2>&1
printf "done.\n"
printf "\n"
}

############################################################################
### Function: Save server info
### Note: You MUST run prepareProvisioningDataFile, first!
configureServerInfo() {
printf "  Enter server IP --> "
read SERVER_IP
printf "\n"

printf "<server>\n" >> $PROV_DATA_FILE
printf "	<ipv4>%s</ipv4>\n" $SERVER_IP >> $PROV_DATA_FILE
printf "</server>\n\n" >> $PROV_DATA_FILE

printf "\n"
}


############################################################################
############################################################################


# Print introduction and verification to continue
printf "**************************************************************\n"
printf " MessageNet Evolution (Omni) NTP Client Setup & Configuration\n\n"

printf " This will perform a remote (network) install and config of\n"
printf " the applicable software.\n\n"

printf " WARNING: Before you continue, you must ensure existing set-\n"
printf " date is not too different than actual date.       Continue? (y/n) "
read VERIFY_CONTINUE
printf "\n"
if [[ "$VERIFY_CONTINUE" == "n" ]]; then
	printf "\n"
	exit 0;
fi

# Prompt for device address
printf "  Enter device address --> "
read DEVICE_ADDRESS

printf "\n"
printf "  You entered: %s\n" $DEVICE_ADDRESS
printf "  Is that correct? (y/n) "
read VERIFY_INPUT
printf "\n"
if [ "$VERIFY_INPUT" == "n" ]; then
        printf "\n"
        exit 0;
fi

# Prompt for server address
printf "  Enter NTP server address --> "
read SERVER_ADDRESS

printf "\n"
printf "  You entered: %s\n" $SERVER_ADDRESS
printf "  Is that correct? (y/n) "
read VERIFY_INPUT
printf "\n"
if [ "$VERIFY_INPUT" == "n" ]; then
        printf "\n"
        exit 0;
fi

printf "\n"

# Install & configure NTP client
printf " Installing NTP Client... "
scp -p -P 8022 -i /root/.ssh/id_rsa ntpClient.apk root@$DEVICE_ADDRESS:/sdcard/
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /root/.ssh/id_rsa root@$DEVICE_ADDRESS -p 8022 'su -c "pm install -r -t /sdcard/ntpClient.apk"'
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /root/.ssh/id_rsa root@$DEVICE_ADDRESS -p 8022 'su -c "am start -n ru.org.amip.ClockSync/.view.Main"'
printf "done.\n"
printf "\n Configuring NTP client... "
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /root/.ssh/id_rsa root@$DEVICE_ADDRESS -p 8022 'su -c "am start -n ru.org.amip.ClockSync/.view.EditPreferences"'
sleep 2
sed -i 's/pool.ntp.org/'$SERVER_ADDRESS'/' ru.org.amip.ClockSync_preferences.xml
scp -p -P 8022 -i /root/.ssh/id_rsa ru.org.amip.ClockSync_preferences.xml root@$DEVICE_ADDRESS:/sdcard/
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /root/.ssh/id_rsa root@$DEVICE_ADDRESS -p 8022 'su -c "cp /sdcard/ru.org.amip.ClockSync_preferences.xml /data/user/0/ru.org.amip.ClockSync/shared_prefs/"'
printf "done.\n"

printf "\n"

############################################################################
# Prompt for reboot of tablet
printf " Reboot tablet now, to enact changes? (y/n) "
read VERIFY_CONTINUE
if [ "$VERIFY_CONTINUE" == "y" ]; then
	printf "  Rebooting tablet... "
	ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /root/.ssh/id_rsa root@$DEVICE_ADDRESS -p 8022 'su -c "reboot -p"'
	printf "done."
fi
printf "\n\n"

# Space down a line to visually separate command prompt from this script session
printf "\n\n"
