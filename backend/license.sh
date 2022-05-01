#!/bin/sh
#
# license.sh [-software_license_file] [-license_date MM/DD/YYYY -license_nodename XX ] - license information for database
#
#	-node XX to license a customer and put the file in /home/silentm/update
#
# 24 Dec 1996, Creation
#
SILENTM_HOME=`cat /etc/silentm/home`

if [ $(ls -l /bin/sh | grep -c dash) -eq 1 ]
then
        # pure posix shells (e.g. dash on ubuntu) don't like the bash-specific source keyword
        . $SILENTM_HOME/bin/smfunctions
else
        # bash on centos is fine with source
        source $SILENTM_HOME/bin/smfunctions
fi

##### REDACTED #####

function update_text()
	{
	echo "Attached is the new license for this project."
	echo "    To install it"
	echo "    DO NOT change the name, it must be license.bin"
	echo "    Save file to your local hard drive (desktop perhaps)"
	echo "    log on to browser application"
	echo "    Go to Other Screen tab."
	echo "    Select \“Import Database Records and Files\” "
	echo "    Click on \“Choose File\” select the license.bin file on your hard drive."
	echo "    Click on \“Import Records\” "
	echo "    Verify the import was successful by going to Other Screen select \“System Parameters\”"
	echo "    Scroll to bottom of screen and verify the new license information is there."
	echo ""
	}

##### REDACTED #####

TOTAL=$#
BASE_DIRECTORY=`dirname $0`

##### REDACTED #####

# $1 is the ##### REDACTED #####
# $2 is the ##### REDACTED #####
SoftwareLicenseFile()
	{
	# Generate the software license file
	##### REDACTED #####
	pass_phrase=`cat $##### REDACTED #####/##### REDACTED #####`

	##### REDACTED #####

	PASS_DATE="$3"
	if [ "$1" = "-new_install" ]
	then
		# need to make a date for one year out as $3 is empty here...
		# Format for date is "MM/DD/YYYY"
		MONTH=`date +%m`
		DAY=`date +%d`
		YEAR=`date +%Y`
		YEAR=`echo $YEAR + 1 | bc`
		PASS_DATE="$MONTH/$DAY/$YEAR"
	fi

	##### REDACTED #####

	##### REDACTED #####

if [ "$SOFTWARE_LICENSE_FILE" = "1" ]
then
	SoftwareLicenseFile "-update_install" "$LICENSE_NODENAME" "$LICENSE_DATE"
	exit 0
fi

##### REDACTED #####

echo -n "Enter the signal light license number? "
read result
$SILENTM_HOME/bin/dumpisam $COMPANY_ARG $COMPANY -license_signal_lights $julian $result

echo -n "Enter the Evolution appliance license number? "
read result
$SILENTM_HOME/bin/dumpisam $COMPANY_ARG $COMPANY -license_evolution_apps $julian $result

##### REDACTED #####

echo -n "Enter the license number for the number of twitters? "
read result
$SILENTM_HOME/bin/dumpisam $COMPANY_ARG $COMPANY -license_twitter $julian $result

##### REDACTED #####

if [ "$COMPANY" = "" ]
then
	smindex.sh
fi

echo -n "What will the name of this machine be [Primary/Secondary this is the assumed name]? [$host]"
read result
if [ "$result" != "" ]
then
	host=$result
fi

# Generate the machines software license file
SoftwareLicenseFile "-new_install" $host
