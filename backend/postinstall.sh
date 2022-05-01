#!/bin/bash
#
# postinstall.sh 	[-disable_oem] [-update_gifs] [-update_cap]
#
SILENTM_HOME=`cat /etc/silentm/home`
PATH="$PATH:$SILENTM_HOME/bin:"

source $SILENTM_HOME/bin/smfunctions

# Update the system_information folder for server
check_kernel_features

echo "Executing postinstall.sh $COMPANY_ARG $COMPANY"

# 
# make sure all directories are up to date and present
#
ConnectionsDirectories
ConnectionsMotd

##### REDACTED #####

update_omni_appliances()
	{
	if [ "$COMPANY" = "" ]
	then
		echo "Updating Omni appliances"
		
	 	$SILENTM_HOME/bin/dumpisam $COMPANY_ARG $COMPANY -listomnis | \
 		while read IPADDRESS DEVICEID EXTENSION
 		do
 			if [ "$IPADDRESS" = "NO_IP" ]
			then
				echo "Updating Omni appliance: $DEVICEID ..."
				IPADDRESS=$($SILENTM_HOME/bin/pbx.showpeers | grep -q $EXTENSION | awk '{print $2}')
				# curl outputs any logs error/success from the Omni.
				curl "$IPADDRESS:8080/update?password="
			else
				echo "Updating Omni appliance: $DEVICEID $IPADDRESS ..."
				curl "$IPADDRESS:8080/update?password="
			fi
 		done
	fi
	}

##### REDACTED #####

#
# Look for command line options and if they exist only do what they specify and then exit
#
if [ "$1" != "" ]
then
	if [ "$1" = "-update_mediaports" ]
	then
		update_mediaport_controllers

	elif [ "$1" = "-update_omnis" ]
	then
		update_omni_appliances
	
	##### REDACTED #####
	
	else
		echo "Unknown option $1"
	fi

	exit 0
fi

##### REDACTED #####

result=`echo 03.14.99 $PREVIOUS_VERSION | awk '$1 >= $2 {print "update"}'`
THIS_VERSION="03.14.99"
 vercomp $THIS_VERSION $PREVIOUS_VERSION		# returns:  0 is '=' / 1 is '>' /  2 is '<'
 case $? in
  0) result="update";;	# =
  1) result="update";;	# >
  2) result="";;		# <
 esac
if [ "$result" = "update" ]
then
	printf "\n"
	printf "===========================================\n"
	printf "03.14.99\n\n"

	# enable favorites via the runtime flag
	echo
	echo Enabling Connections-Mobile...
	if [ -e /etc/silentm/smcgi.diagnostic.level ]; then
		if [ "$(grep -c evolution /etc/silentm/smcgi.diagnostic.level)" = "0" ]; then
			echo -e "LABEL=favorites\n" >> /etc/silentm/smcgi.diagnostic.level
		else
			printf " Already enabled; no runtime label needs to be added.\n"
		fi
	else
		printf " File /etc/silentm/smcgi.diagnostic.level does not exist. Creating it and enabling mobile flag.\n"
		echo -e "LABEL=favorites\n" > /etc/silentm/smcgi.diagnostic.level
		chmod 644 /etc/silentm/smbanner.diagnostic.level
		chown root:root /etc/silentm/smbanner.diagnostic.level
	fi

	# configure apache gzip compression (makes entire GUI *MUCH* faster)
	echo
	echo Configuring Apache gzip compression to make GUI faster...
	/home/silentm/bin/smset_apache_compression.sh

	# copy new mediaport assets to the http directory so the mediaport update routine can download and install them
	# note: the first update command should at least get the updated smmon onto the mediaports. then the delayed update should make sure they actually get these new scripts as indicated in the updated smmon.
	echo
	echo Copying new MediaPort files to downloadable location...
	cp -a $SILENTM_HOME/bin/mediaportCleanup.sh $SILENTM_HOME/public_html/multimedia/
	cp -a $SILENTM_HOME/bin/periodicReboot.sh $SILENTM_HOME/public_html/multimedia/
	cp -a $SILENTM_HOME/bin/watchdog.sh $SILENTM_HOME/public_html/multimedia/
	cp -a $SILENTM_HOME/MessageNet/wikiplayer $SILENTM_HOME/public_html/multimedia/

	#echo Installing updated MediaPort update routine so MediaPorts can get new assets...
	update_mediaport_controllers
	
	# Don't try to execute this on MessageNet's local office environment
	if [ $(hostname | grep -c msgnet.com) -gt 0 ]; then
		echo Skipping MediaPort update procedure on the MessageNet office network.
		echo "  - To run it, use: postinstall.sh -update_mediaports"
	else
		echo Setting up automatic installation of new MediaPort scripts 5 minutes from now...
		echo "postinstall.sh -update_mediaports" | at now + 5 min 
	fi

	# iboot added hardware database fields, so do a genisam to be safe
	echo
	echo Updating databases...
	genisam $COMPANY_ARG $COMPANY -default
fi

THIS_VERSION="03.14.100"
 vercomp $THIS_VERSION $PREVIOUS_VERSION	# returns:  0 is '=' / 1 is '>' /  2 is '<'
 case $? in 0) result="update";; 1) result="update";; 2) result="";; esac
if [ "$result" = "update" ]
then
	printf "\n"
	printf "===========================================\n"
	printf "03.14.100 (03.15.00)\n\n"

	printf "NEW! \"watchdog_ipspeakers.sh\" - A watchdog script for IPSpeaker devices.\n"
	printf "Do not run it unless you're ready for Asterisk to possibly be restarted!\n"
	printf "Meant to be run primarily as a cronjob.\n"
	printf "For mere reporting, provide the -showonly switch.\n\n"

	# added database fields for evolution hardware, so do a genisam to be safe
	echo Updating databases...
	genisam $COMPANY_ARG $COMPANY -default

fi

THIS_VERSION="03.14.101"
 vercomp $THIS_VERSION $PREVIOUS_VERSION	# returns:  0 is '=' / 1 is '>' /  2 is '<'
 case $? in 0) result="update";; 1) result="update";; 2) result="";; esac
if [ "$result" = "update" ]
then
	printf "\n"
	printf "===========================================\n"
	printf "$THIS_VERSION\n\n"

	# The usual database update
	printf "\n"
	printf "Updating databases...\n"
	genisam $COMPANY_ARG $COMPANY -default

	printf "NEW! Support for Omni (codename: Evolution) products.\n"

	# Enable SSH login to Omni from server
	printf "\n"
	printf "Making a public RSA key available for download...\n"
	if [ ! -f /root/.ssh/id_rsa.pub ]; then
		ssh-keygen -t rsa -N "" -f /root/.ssh/id_rsa
	fi
	cp /root/.ssh/id_rsa.pub /home/silentm/public_html/
	chown 555 /home/silentm/public_html/id_rsa.pub

	# Add smajax build-up patch
	printf "\n"
	if [ $(grep -c "killall smajax" /etc/crontab) -gt 0 ]; then
		printf "Build-up control for smajax already installed to cron.\n"
	else
		printf "Adding smajax build-up control to cron...\n"
		echo "20 * * * *      root    /usr/bin/killall smajax smajax.cgi" >> /etc/crontab
	fi

	# Check and install/configure NTP server if needed
	printf "\n"
	printf "Enabling local NTP server...\n"
	if [ -e /usr/sbin/ntpd ]; then
		printf "  The ntpd binary is already installed.\n"
	else
		printf "  Installing ntpd...\n"
		yum install -y ntp
	fi
	printf "  Adding public time servers...\n"
	echo "server time.nist.gov" >> /etc/ntp.conf
	printf "  Enabling start at boot...\n"
	chkconfig ntpd on
	printf "  Starting service...\n"
	service ntpd start

	# Check and install PHP if needed
	printf "\n"
	printf "Checking whether PHP is installed...\n"
	if [ "$(rpm -qa | grep php)" == "" ]; then
		printf " PHP is not installed on this server, trying to install...\n"
		
		echo -e "GET http://centos.org HTTP/1.0\n\n" | nc centos.org 80 > /dev/null 2>&1
		if [ $? -eq 0 ]; then
			yum install -y php
		else
			printf "  WARNING: No internet connection, so yum cannot get PHP to install.\n"
		fi
	else
		printf " PHP is installed, nothing to do.\n"
	fi

	# Add any runtime flags for logging
	printf "\n"
	printf "Adding logging runtime flags...\n"
	if [ -e /etc/silentm/smbanner.diagnostic.level ]; then
		if [ "$(grep -c evolution /etc/silentm/smbanner.diagnostic.level)" = "0" ]; then
			echo "LABEL=evolution" >> /etc/silentm/smbanner.diagnostic.level
			echo "#LABEL=evolution_debug" >> /etc/silentm/smbanner.diagnostic.level
		else
			printf " Flags already exist in /etc/silentm/smbanner.diagnostic.level; not adding.\n"
		fi
	else
		printf " File /etc/silentm/smbanner.diagnostic.level does not exist. Creating it and adding logging flags.\n"
		echo "LABEL=evolution" > /etc/silentm/smbanner.diagnostic.level
		echo "#LABEL=evolution_debug" >> /etc/silentm/smbanner.diagnostic.level
		chmod 644 /etc/silentm/smbanner.diagnostic.level
		chown root:root /etc/silentm/smbanner.diagnostic.level
	fi

	##### REDACTED #####

	# MediaPort updated files
	echo Copying updated MediaPort files to downloadable location...
	cp -a $SILENTM_HOME/bin/mediaportCleanup.sh $SILENTM_HOME/public_html/multimedia/

	# Add omni auto-update-from-cloud-server script to run at random minute with 1am hour
	printf "\n"
	if [ $(grep -c "checkAndGetUpdates" /etc/crontab) -gt 0 ]; then
		printf "Omni auto-cloud-update routine already installed to cron.\n"
	else
		printf "Adding Omni auto-cloud-update routine to cron...\n"
		which shuf; EXIT_STATUS=$?
		if [ $EXIT_STATUS -eq 0 ]; then
			RANDOM_MINUTE="$(shuf -i 0-55 -n 1)"
			echo "$RANDOM_MINUTE 1 * * *      root    /home/silentm/bin/checkAndGetUpdates.sh" >> /etc/crontab
		else
			echo "0 1 * * *      root    /home/silentm/bin/checkAndGetUpdates.sh" >> /etc/crontab
		fi
	fi

else
	echo "else"
fi


# Always run these commands (smindex.sh is needed to set permissions for bin files on older systems)
printf "\n"
printf "===========================================\n"
printf "Finishing up\n\n"

echo "Updating GIFS..."
update_gifs
echo "Updating CAP..."
update_cap
echo "Syncing Server Directories..."
sync_server_directories


##### REDACTED #####

