#!/bin/bash
#
# update_evolution.sh [-all] | [$IPADDRESS]
# 
# Will update Omni device that you specify. You can also use the -all flag to update
# all Omni appliances. (This works the same as it does in postinstall.sh, but ported 
# here for convenience.)
#
# 07.08.2018 - Chris Rider - Created with Austin as part of training

SILENTM_HOME=$(cat /etc/silentm/home)

if [ "$1" != "" ]
then
	if [[ "$1" = "-all" ]]
	then
		$SILENTM_HOME/bin/dumpisam $COMPANY_ARG $COMPANY -listomnis | \
		while read IPADDRESS NODENAME
		do
			if [ "$IPADDRESS" = "NO_IP" ]
			then
				echo "Updating Omni Appliance: $DEVICEID ..."
				IPADDRESS=$($SILENTM_HOME/bin/pbx.showpeers | grep -q $EXTENSION | awk '{print $2}')
				# curl outputs any logs error/success from the Omni.
				curl "$IPADDRESS:8080/update?password="
			else
				echo "Updating Omni Appliance: $DEVICEID $IPADDRESS ..."
				curl "$IPADDRESS:8080/update?password="
			fi
		done
	else	
		IPADDRESS=$1
		#Make sure this is a valid IP
		if [[ $IPADDRESS =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]
		then
			echo "updating Omni appliance $IPADDRESS ..."
			curl "$IPADDRESS:8080/update?password="	
		else
			echo "$IPADDRESS is invalid, please try again."
		fi
	fi
fi
