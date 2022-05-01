#!/bin/bash
#
# Parses a specified Omni log file.
# You must redirect for output to be saved.
#
# Usage:  ./parseOmniLogData_toCSV.sh "LOG FILE" > outputFile.csv

# Example log entry:
# {"CPU":"10%","appHeapAvail":"503MB","battSOC":"31%","battNetUse":"-763mA","battTemp":"18.8C","battTime":"-171mins","battCharging":"false","wifiSignal":"-61dBm (Fair)"}

printf "Datetime,SOC,NetCurrent,CPU,WiFi\n"

#grep battSOC log/smbanner.mndemo.OMNI\ TEST\ 8.log | while read -r line ; do
grep battSOC "$1" | while read -r line ; do
	#echo "Processing $line"

	DATE=$(echo $line | awk '{printf $1}')
	TIME=$(echo $line | awk '{printf $2$3}')
	JSON=$(echo $line | awk '{printf $8}')

	BATTSOC=$(echo $JSON | cut -d',' -f3 | cut -d':' -f2 | cut -d'"' -f2 | cut -d'%' -f1)
	BATTNET=$(echo $JSON | cut -d',' -f4 | cut -d':' -f2 | cut -d'"' -f2 | cut -d'm' -f1)
	CPU=$(echo $JSON | cut -d',' -f1 | cut -d':' -f2 | cut -d'"' -f2 | cut -d'%' -f1)
	WIFI=$(echo $JSON | cut -d',' -f8 | cut -d':' -f2 | cut -d'"' -f2 | cut -d'd' -f1)

	printf "%s %s,%s,%s,%s,%s\n" $DATE $TIME $BATTSOC $BATTNET $CPU $WIFI
done
