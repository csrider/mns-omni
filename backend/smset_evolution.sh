#!/bin/sh
#
# smset_evolution.sh $SERVERIP $MAC $USERID $AUTHCODE $LINEAPPEARANCE $NTPADDRESS $TIMEZONE $TIMEFORMAT $SPEAKER_VOLUME $MICROPHONE_MONITOR $MICROPHONE_VOLUME $SHOW_SECONDS $DESCRIPTION $NO_DISPLAY $HW_RECNO $HW_DEVICEID
# smset_evolution.sh -setinactivation "MM-DD-YYYY HH:MM:SS"
#
#	Will setup Evolution boot files in /tftpboot, and then reboot the devices so they can get the updated files.
#
#	NOTE: Whenever you update any arguments or add data, be sure to update the code that executes this script: api_asterisk.c 
#
# TIMEFORMAT is 12 or 24
#
# XML Validation Procedure (should be followed if any changes to the output are made):
#	1. Go to the XML validation website http://www.xmlvalidation.com/.
#	2. Load the configuration file to be verified as the XML document. (the one that this script generates)
#	3. Check the “Validate against external XML schema” option, then click the “Validate” button.
#	3b.Upload AND_Gen2_IPSpeakerConfigurationSchema.xsd as the XML Schema (available at http://www.[REDACTED]Schema.xsd).
#	4. Click the “Continue Validation” button.
#	5. Any errors in the configuration file will be highlighted. Modify the configuration file as needed to correct the errors.
#
# 25 Mar 2008, JPG - creation
# 2016.07.--	CSR	Fixed numerous syntax issues (per [REDACTED]'s analysis of our old cfg files), 
#					and now supporting firmware v1.5 (the syntax issues were not allowing 
#					the sign to update). Details below, per [REDACTED] at [REDACTED]:
#	- Should be whitespace (carriage return or at least a space) between the "SIPConfig" tag and the first parameter, "SIP_server_addr"
#	- Should be whitespace after each parameter and the next:
#		Between SIP_server_addr="192.168.1.58" and SIP_port="5060"
#		Between SIP_port ="5060" and id="716"
#	- There is a permanent stream channel fragment for 239.168.3.9 within the Firmware20 and 
#		Firmware21 file definitions.  This must be enclosed/defined within the PermanentStreams tag.
#		This same stream is repeated below in the proper location, so it looks like it was improperly added to this section.
#	- Several streams are duplicated in the PermanentStreams section, 239.168.3.8, 239.168.3.7, 239.168.3.6, 
#		239.168.3.5, 239.168.3.4 - these duplicates should be removed.
#	- There is a fragment of stream 239.168.3.3 within the other streams that should be cleaned up.
#	- After the closing tag </IPSpeakerConfiguration>, 3 permanent streams (.3, .2, and .1) and the 
#		display settings and closing tag are repeated again - these were removed.
#	- Also, the number of permanent streams is limited to 6, so not all 10 streams can be listed.
# 2017.06.--	CSR	Modified for use with Evolution.
# 2017.09.05	CSR	Added argument for passing in hardware device recno.
# 2018.03.05	CSR	Added argument for passing in and writing hardware device ID.
# 2019.04.15	CSR	Added license tag and incactivation date attribute.
# 2019.04.18	CSR	Corrected missing root node closing tag; changed reboot to app-restart; added ability to modify licensing inactivation date.
# 2020.05.01	CSR	Added parsing of HW volume mixer and mic values (these 0-100 values should really be used instead of EXT-MGR fields).
#					Also went ahead and added clock-format value (0 is 12-hr, 1 is 24-hr).
#
SILENTM_HOME=`cat /etc/silentm/home`

if [ "$1" = "-setinactivation" ]; then
	echo "Setting inactivation date..."

	# WHAT WE'RE LOOKING FOR:	<Licensing inactivationDate="12-31-9999 23:59:59" />
	# WHAT WE'RE LOOKING FOR:	<Licensing inactivationDate="12/31/9999 23:59:59" />
	# WHAT WE'RE LOOKING FOR:	<Licensing inactivationDate="12/31/9999 23:59:59 PM" />
	#REGEX_TO_MATCH_FOR="inactivationDate=\"[0-9]{2}\.{1}[0-9]{2}\.{1}[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}( [a-zA-Z]{2})?\""
	REGEX_TO_MATCH_FOR="inactivationDate=\"[0-9]{2,4}[-/.]{1}[0-9]{2}[-/.]{1}[0-9]{2,4} [0-9]{2}:[0-9]{2}:[0-9]{2}( [a-zA-Z]{2})?\""

	if [ "$2" != "" ]; then
		REGEX_TO_REPLACE_WITH="inactivationDate=\"$2\""
	else
		echo "NOTE! No inactivation date specified, using default inactivation-disabled value."
		REGEX_TO_REPLACE_WITH="inactivationDate=\"12-31-9999 23:59:59\""
	fi

	SED_STRING="s/$REGEX_TO_MATCH_FOR/$REGEX_TO_REPLACE_WITH/"

	for FILE in /tftpboot/Evolution*.cfg; do
		sed -r -i "$SED_STRING" $FILE
	done
	
	exit 0
fi

SERVERIP=$1
MACADDRESS=$2
USERID=$3
AUTHCODE=$4
NTPADDRESS=$6
TIMEZONE=$7
TIMEFORMAT=$8
SPEAKER_VOLUME=$9
MICROPHONE_MONITOR="${10}"
MICROPHONE_VOLUME="${11}"
SHOW_SECONDS="${12}"
DESCRIPTION="${13}"
NO_DISPLAY="${14}"
HW_RECNO="${15}"
HW_DEVICEID="${16}"
HW_VOLUME="${17}"
HW_VOLUME_MIC="${18}"
HW_CLOCK_FORMAT="${19}"

# convert MAC to lower and upper cases, in case the user used upper case (AND signs appear to only know lower case?)
MACADDRESS_LOWERCASE=`echo $MACADDRESS | tr '[:upper:]' '[:lower:]'`
MACADDRESS_UPPERCASE=`echo $MACADDRESS | tr '[:lower:]' '[:upper:]'`

if [ "$TIMEZONE" = "US/Eastern" ]
then
	TIMEZONE="America/New_York"
elif [ "$TIMEZONE" = "US/East-Indiana" ]
then
	TIMEZONE="America/New_York"
elif [ "$TIMEZONE" = "US/Michigan" ]
then
	TIMEZONE="America/Chicago"
elif [ "$TIMEZONE" = "US/Central" ]
then
	TIMEZONE="America/Chicago"
elif [ "$TIMEZONE" = "US/Mountain" ]
then
	TIMEZONE="America/Denver"
elif [ "$TIMEZONE" = "US/Arizona" ]
then
	TIMEZONE="America/Los_Angeles"
elif [ "$TIMEZONE" = "US/Pacific" ]
then
	TIMEZONE="America/Los_Angeles"
elif [ "$TIMEZONE" = "US/Alaska" ]
then
	TIMEZONE="America/Alaska"
elif [ "$TIMEZONE" = "US/Hawaii" ]
then
	TIMEZONE="America/Hawaii"
elif [ "$TIMEZONE" = "Atlantic/Bermuda" ]
then
	TIMEZONE="Atlantic/Bermuda"
else
	TIMEZONE="America/New_York"
fi

# Check for changing the SERVERIP address
SERVER_IP_FOR_PHONES=`$SILENTM_HOME/bin/dumpisam -config SERVER_IP_FOR_PHONES`
if [ "$SERVER_IP_FOR_PHONES" != "" ]
then
	SERVERIP=$SERVER_IP_FOR_PHONES
fi

# Remove any pre-existing file since we're creating a new one
rm /tftpboot/Evolution$MACADDRESS.cfg 2> /dev/null > /dev/null
rm /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg 2> /dev/null > /dev/null
rm /tftpboot/Evolution$MACADDRESS_UPPERCASE.cfg 2> /dev/null > /dev/null

# Begin creating the configuration file...
echo "<EvolutionConfiguration>" > /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

# Configure SIP attributes...
# NOTE: There must be white space between attributes (CR or space, not just LF)
function randomize_sip_registration_interval {
	POSSIBLE_REGISTRATION_INTERVALS=(300 360 420 480 540)
	RANDOMLY_CHOSEN_INTERVAL=${POSSIBLE_REGISTRATION_INTERVALS[$RANDOM % ${#POSSIBLE_REGISTRATION_INTERVALS[@]} ]}
	SIP_REG_INTERVAL_SECS=$RANDOMLY_CHOSEN_INTERVAL
}
echo "<SIPConfig " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " SIP_server_addr=\"$SERVERIP\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " SIP_port=\"5060\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " id=\"$USERID\" volume=\"$SPEAKER_VOLUME\" mic_volume=\"$MICROPHONE_VOLUME\" feedback_suppression=\"ultralow\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " password=\"$AUTHCODE\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
randomize_sip_registration_interval
echo " registration_interval=\"$SIP_REG_INTERVAL_SECS\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo "/>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<HardwareData " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " hw_recno=\"$HW_RECNO\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " hw_deviceid=\"$HW_DEVICEID\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " clock_format=\"$HW_CLOCK_FORMAT\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo "/>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<NTPConfig timezone_name=\"$TIMEZONE\" refresh_minutes=\"30\" >" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo "    <Server url=\"$NTPADDRESS\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo "</NTPConfig>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<Development password=\"$AUTHCODE\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<AudioConfig " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " volumeDefault=\"$HW_VOLUME\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo " volumeMicrophone=\"$HW_VOLUME_MIC\" " >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo "/>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<Licensing inactivationDate=\"12-31-9999 23:59:59\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<GPIO_callback url=\"http://$SERVERIP/~silentm/bin/smgpio.cgi?\" min_update_period_ms=\"500\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

if [ "$MICROPHONE_MONITOR" = "1" ]
then
	echo "<MicrophoneStats_callback url=\"http://$SERVERIP/~silentm/bin/smmicrophone.cgi?\" trigger_level=\"0\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
	echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
fi

#
# non-zero audio_boost_limit that specifies the maximum amount of change in volume levels (on our 0-13 scale) to boost the local volume as needed.
# each volume level is approximately 6db so to get a +10db gain (against ambient) volume level of +2 is +12db gain or boost.
# non-zero audio_boost_trigger_level above which the volume boost will start to be applied.  This value is on the same pseudo-dB scale as the reported ambient sound level. 
# UPDATE 7/19/2016 per XML validation and Sean at AND: audio_boost_limit maxes out at 3, but any higher level should use 3.
#
echo "<Microphone volume=\"$MICROPHONE_VOLUME\" audio_boost_limit=\"3\" audio_boost_trigger_level=\"7\" audio_boost_reaction_time=\"short\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<PermanentStreams>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
#DEV-NOTE: The signs only support up to 6 streams... anything beyond that is ignored
for MULTICAST in MULTICAST_0 MULTICAST_1 MULTICAST_2 MULTICAST_3 MULTICAST_4 MULTICAST_5 MULTICAST_6 MULTICAST_7 MULTICAST_8 MULTICAST_9
do
	TMP="`$SILENTM_HOME/bin/dumpisam -config $MULTICAST`"
	ADDR_PORT=`$SILENTM_HOME/bin/dumpisam -config "$MULTICAST"`
	ADDR=`echo $ADDR_PORT | cut -d ':' -f 1`
	PORT=`echo $ADDR_PORT | cut -d ':' -f 2`
	
	if [ "$ADDR" != "" ]
	then
		# note: you may add an audio_channel attribute to the <Channel> tag if you need to differentiate between left or right channels; but if not, then don't include it at all (per XML validation results).
		echo "    <Channel stream=\"$ADDR\" port=\"$PORT\" priority=\"99\" volume=\"$SPEAKER_VOLUME\" latency=\"low\">" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
		echo "        <AllowedSource ip=\"$SERVERIP\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
		echo "    </Channel>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
		echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
	fi
done
echo "</PermanentStreams>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "<Display minute_progress_critical_start_second=\"0\" time_format=\"$TIMEFORMAT\" show_seconds=\"$SHOW_SECONDS\" blink_colon=\"0\" />" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg
echo >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

echo "</EvolutionConfiguration>" >> /tftpboot/Evolution$MACADDRESS_LOWERCASE.cfg

# Go out and restart the signs' main evolution apps so they get their config file...
echo "$SILENTM_HOME/bin/dumpisam -evolution_address $USERID"
ADDRESS="$($SILENTM_HOME/bin/dumpisam -evolution_address $USERID)"
if [ "$ADDRESS" != "" ]
then
	#curl "http://$ADDRESS:8080/reboot?password=$AUTHCODE" 2> /dev/null > /dev/null
	curl "http://$ADDRESS:8080/restart?password=$AUTHCODE" 2> /dev/null > /dev/null
fi
