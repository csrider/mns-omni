#!/bin/bash
#
# Prints the most recent IPv4 address logged by each Omni.
#

for f in /home/silentm/log/*OMNI*.log; do
	OMNI_DEVICE_ID="$(echo "$f" | cut -d"." -f3)"
	MOST_RECENT_LOG_LINE="$(grep "pong" "$f" | tail -n1)"
	
	printf "%s:\t" "$OMNI_DEVICE_ID"
	
	if [ "$MOST_RECENT_LOG_LINE" = "" ]; then
		printf "(never connected to server)\n"
	else
		LOG_DATE="$(echo $MOST_RECENT_LOG_LINE | awk '{printf $1}')"
		LOG_TIME="$(echo $MOST_RECENT_LOG_LINE | awk '{printf $2}')$(echo $MOST_RECENT_LOG_LINE | awk '{printf $3}')"
		LOG_IPv4="$(echo $MOST_RECENT_LOG_LINE | cut -d',' -f3 | cut -d":" -f2 | cut -d "\"" -f2 | cut -d "\"" -f1)"
		LOG_BATT="$(echo $MOST_RECENT_LOG_LINE | cut -d',' -f6 | cut -d":" -f2 | cut -d "\"" -f2 | cut -d "\"" -f1)"
		LOG_BATT_NET="$(echo $MOST_RECENT_LOG_LINE | cut -d',' -f7 | cut -d":" -f2 | cut -d "\"" -f2 | cut -d "\"" -f1)"
		LOG_WIFI="$(echo $MOST_RECENT_LOG_LINE | cut -d',' -f10 | cut -d":" -f2 | cut -d "\"" -f2 | cut -d "\"" -f1)"
		printf "%s %s\t IP: %s\t Battery: %s\t Power: %s\t Wifi: %s\n" "$LOG_DATE" "$LOG_TIME" "$LOG_IPv4" "$LOG_BATT" "$LOG_BATT_NET" "$LOG_WIFI"
	fi
done
