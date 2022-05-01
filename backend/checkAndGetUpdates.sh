#!/bin/bash

#######################################################################
# Check for software updates and download them, if warranted.
# Originally developed for 03.14.101 primarily for Omni auto updates.
#
# Revisions:
#	2019.07.17/19	Chris Rider	Created.
#######################################################################


#######################################################################
### CONFIGURATION #####################################################

# Configure update package and checksum filenames...
FILENAME_PKG_OMNI_TAR="omniUpdatePackage.tar"
FILENAME_PKG_OMNI_GZ="$FILENAME_PKG_OMNI_TAR.gz"
FILENAME_PKG_OMNI_GPG="$FILENAME_PKG_OMNI_GZ.gpg"
FILENAME_PKG_OMNI_GPG_MD5="$FILENAME_PKG_OMNI_GPG.md5"

# Configure update-server assets...
HOST_UPDATESERVER="updates.messagenetsystems.com"	#subdomain record created 2019.07.17 and points to 38.123.6.65 (mnet001hosted1.messagenetsystems.com cloud)
PATH_UPDATESERVER_DEVELOPMENT="updates/dev"			#relative to the webserver root directory (so you shouldn't include the leading / here!)
PATH_UPDATESERVER_TESTING="updates/test"			#relative to the webserver root directory (so you shouldn't include the leading / here!)
PATH_UPDATESERVER_PRODUCTION="updates/prod"			#relative to the webserver root directory (so you shouldn't include the leading / here!)
PATH_UPDATESERVER=$PATH_UPDATESERVER_PRODUCTION		#make this equal to the flavor you actually want to update from

# Configure local-machine assets...
FILE_PASSPHRASE="/home/silentm/autoUpdatePassPhrase.txt"
PATH_LOCAL_PKG_OMNI="/home/silentm/public_html"
FILE_LOG_OMNI="/home/silentm/log/autoUpdate.omni.log"

# Configure behaviors...
MAX_DOWNLOAD_ATTEMPTS_OMNI=10		#this comes into play whether download process fails or we need to rety download due to bad integrity check


#######################################################################
### FUNCTIONS #########################################################

# This function adds the provided text to the log file.
# $1 = "Some log text"
addToLogOmni()
	{
	LOG_DATE="$(date +"%Y.%m.%d %H:%M:%S")"
	if [ "$1" == "" ]; then
		printf "%s \t%s\n" "$LOG_DATE" "(no log text provided)" >> $FILE_LOG_OMNI
		return 0
	else
		printf "%s \t%s\n" "$LOG_DATE" "$1" >> $FILE_LOG_OMNI
	fi
	}

# This function checks local package's MD5 with that on the update-server.
# It returns 0 if they match, otherwise 1 if they are different.
# Note: If local package doesn't exist, it will return 1 (different).
# Note: If server is not reachable, it will return 2.
checkLocalPackageChecksumAgainstServerOmni()
	{
	ping -c1 -W10 $HOST_UPDATESERVER > /dev/null 2>&1
	EXIT_STATUS=$?
	if [ $EXIT_STATUS -gt 0 ]; then
		addToLogOmni "Server ($(echo $HOST_UPDATESERVER)) is not reachable at this time."
		printf "Server is not reachable.\n"
		return 2
	fi
	LOCAL_MD5="$(md5sum $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG | awk '{printf $1}')"
	SERVER_MD5="$(curl -sL http://$HOST_UPDATESERVER/$PATH_UPDATESERVER/$FILENAME_PKG_OMNI_GPG_MD5)"
	if [ "$SERVER_MD5" = "dummy" ]; then
		addToLogOmni "Server checksum file indicates no update available. Returning match so no update download happens."
		printf "Server checksum file indicates no update available. Returning match so no update download happens.\n"
		return 0
	fi
	if [ "$LOCAL_MD5" = "$SERVER_MD5" ]; then
		addToLogOmni "Local package checksum ($(echo $LOCAL_MD5)) matches what is on the update server ($(echo $SERVER_MD5))."
		printf "Local package checksum matches what is on the update server.\n"
		return 0
	else
		addToLogOmni "Local package checksum ($(echo $LOCAL_MD5)) is different than what is on the update server ($(echo $SERVER_MD5))."
		printf "Local package checksum is different than what is on the update server.\n"
		return 1
	fi
	}

# This function downloads the package from the update server to the package's appropriate location.
# Returns 0 if download succeeds AND its integrity matches expected checksum
# Returns 1 if download fails
# Returns 2 if checksum comparison fails
downloadPackageOmni()
	{
	if [ -e $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG ]; then
		rm -f $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG
	fi
	addToLogOmni "Downloading: http://$(echo $HOST_UPDATESERVER/$PATH_UPDATESERVER/$FILENAME_PKG_OMNI_GPG)"
	wget -O $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG http://$HOST_UPDATESERVER/$PATH_UPDATESERVER/$FILENAME_PKG_OMNI_GPG
	EXIT_STATUS=$?
	if [ $EXIT_STATUS -eq 0 ]; then
		printf "Download finished.\n"
		addToLogOmni "Download finished. Checking integrity..."
		checkLocalPackageChecksumAgainstServerOmni
		RESULT_CODE=$?
		if [ $RESULT_CODE -eq 0 ]; then
			#local and server checksums match
			return 0
		else
			#local and server checksums are different
			printf "Package download failed integrity check!\n"
			addToLogOmni "Package download failed integrity check."
			return 2
		fi
	else
		printf "Download failed!\n"
		addToLogOmni "Download failed (wget exit status: $(echo $EXIT_STATUS))."
		return 1
	fi
	}

# This function decrypts the package file.
# Note: this will create a new decrypted file (preserving the original encrypted file).
# Note: any pre-existing decrypted file will be preserved via rename to .prev extension.
# Returns 0 for success, 1 for failure, 2 for file not found.
decryptPackageOmni()
	{
	if [ ! -e $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG ]; then
		addToLogOmni "Encrypted file ($(echo $PATH_LOCAL_PKG_OMNI)/$(echo $FILENAME_PKG_OMNI_GPG)) not found."
		return 2
	fi
	if [ -e $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GZ ]; then
		rm -f $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GZ.prev
		mv $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GZ $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GZ.prev
	fi
	cat $FILE_PASSPHRASE | gpg --batch --passphrase-fd 0 $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG	#note: this will create a new file (updatePackageFile.tar.gz), preserving the encrypted original file
	EXIT_STATUS=$?
	if [ $EXIT_STATUS -eq 0 ]; then
		printf "Package decryption succeeded.\n"
		addToLogOmni "Package decryption succeeded."
		return 0
	else
		printf "Package decryption failed!\n"
		addToLogOmni "Package decryption failed (gpg exit status: $(echo $EXIT_STATUS))."
		return 1
	fi
	}

# This function decompresses the package file.
# Note: this will replace the tar.gz file with just the .tar file.
# Returns 0 for success, 1 for failure, 2 for file not found.
decompressPackageOmni()
	{
	if [ ! -e $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GZ ]; then
		addToLogOmni "Compressed file ($(echo $PATH_LOCAL_PKG_OMNI)/$(echo $FILENAME_PKG_OMNI_GZ)) not found."
		return 2
	fi
	if [ -e $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_TAR ]; then
		#delete any pre-existing tar file before we extract the new tar
		rm -f $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_TAR
	fi
	gunzip $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GZ	#note: this will replace the updatePackageFile.tar.gz file with the decompressed version, updatePackageFile.tar
	EXIT_STATUS=$?
	if [ $EXIT_STATUS -eq 0 ]; then
		printf "Package decompression succeeded.\n"
		addToLogOmni "Package decompression succeeded."
		return 0
	else
		printf "Package decompression failed!\n"
		addToLogOmni "Package decompression failed (gunzip exit status: $(echo $EXIT_STATUS))."
		return 1
	fi
	}

# This function will unpack the package file (storing its files wherever the tar archive's files' paths dictate)
# Returns 0 for success, 1 for failure, 2 for file not found
unpackPackageOmni()
	{
	if [ ! -e $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_TAR ]; then
		addToLogOmni "Archive (tar) file ($(echo $PATH_LOCAL_PKG_OMNI)/$(echo $FILENAME_PKG_OMNI_TAR)) not found."
		return 2
	fi
	tar --absolute-names -xf $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_TAR
	EXIT_STATUS=$?
	if [ $EXIT_STATUS -eq 0 ]; then
		printf "Package extraction succeeded.\n"
		addToLogOmni "Package extraction succeeded."
		return 0
	else
		printf "Package extraction failed!\n"
		addToLogOmni "Package extraction failed (tar exit status: $(echo $EXIT_STATUS))."
		return 1
	fi
	}


#######################################################################
### MAIN ROUTINE ######################################################

# Check update-server for available update package...
printf "\n" >> $FILE_LOG_OMNI
addToLogOmni "Checking for need to update..."
printf "Checking for need to update...\n"
checkLocalPackageChecksumAgainstServerOmni
FUNCTION_RESULT_CODE=$?
if [ $FUNCTION_RESULT_CODE -eq 0 ]; then
	#local and server checksums match
	addToLogOmni "Local package checksum matches what is on the update server. Nothing to do."
	printf "Local package checksum matches what is on the update server. Nothing to do.\n"
	DO_DOWNLOAD_OMNI="false"
elif [ $FUNCTION_RESULT_CODE -eq 2 ]; then
	#cannot reach server
	addToLogOmni "Cannot reach server to compare checksum. Retrying once..."
	printf "Cannot reach server to compare checksum. Retrying once...\n"
	checkLocalPackageChecksumAgainstServerOmni
	FUNCTION_RESULT_CODE=$?
	if [ $FUNCTION_RESULT_CODE -eq 0 ]; then
		#local and server checksums match
		addToLogOmni "Retry succeeded. Local package checksum matches what is on the update server. Nothing to do."
		printf "Retry succeeded. Local package checksum matches what is on the update server. Nothing to do.\n"
		DO_DOWNLOAD_OMNI="false"
	elif [ $FUNCTION_RESULT_CODE -eq 2 ]; then
		#cannot reach server
		addToLogOmni "Retry failed. Cannot reach server to compare checksum. Giving up for now."
		printf "Retry failed. Cannot reach server to compare checksum. Giving up for now.\n"
		DO_DOWNLOAD_OMNI="false"
	else 
		#local and server checksums are different
		addToLogOmni "Retry succeeded. Update server contains different package than local checksum. Need to download it."
		printf "Retry succeeded. Update server contains different package than local checksum. Need to download it.\n"
		DO_DOWNLOAD_OMNI="true"
	fi
else 
	#local and server checksums are different
	addToLogOmni "Update server contains different package than local checksum. Need to download it."
	printf "Update server contains different package than local checksum. Need to download it.\n"
	DO_DOWNLOAD_OMNI="true"
fi

# Download Omni update package if necessary...
DO_UNPACK_OMNI="false"		#go ahead and initialize next step default (to NOT unpack.. only an explicit download success will enable unpacking)
if [ "$DO_DOWNLOAD_OMNI" = "true" ]; then
	DOWNLOAD_ATTEMPT_COUNT=1	#init retry counter
	while [ $DOWNLOAD_ATTEMPT_COUNT -le $MAX_DOWNLOAD_ATTEMPTS_OMNI ]; do
		addToLogOmni "Attempting package download (attempt $(echo $DOWNLOAD_ATTEMPT_COUNT) of $(echo $MAX_DOWNLOAD_ATTEMPTS_OMNI) maximum)..."
		printf "Attempting package download (attempt #$(echo $DOWNLOAD_ATTEMPT_COUNT) of $(echo $MAX_DOWNLOAD_ATTEMPTS_OMNI) maximum)...\n"
		downloadPackageOmni
		FUNCTION_RESULT_CODE=$?
		if [ $FUNCTION_RESULT_CODE -eq 0 ]; then
			#download and integrity check succeeded
			DO_UNPACK_OMNI="true"
			break
		fi
		sleep 1
		(( DOWNLOAD_ATTEMPT_COUNT++ ))
	done
	if [ $DOWNLOAD_ATTEMPT_COUNT -ge $MAX_DOWNLOAD_ATTEMPTS_OMNI ]; then
		#we just hit our maximum attempts, so check whether it worked to be safe...
		checkLocalPackageChecksumAgainstServerOmni
		FUNCTION_RESULT_CODE=$?
		if [ $FUNCTION_RESULT_CODE -eq 1 ]; then
			#all retries failed to get a valid download completed
			#let's delete the file so we will retry later when this script runs again
			addToLogOmni "All download attempts and integrity checks failed. Deleting local file since it's invalid."
			printf "All download attempts and integrity checks failed. Deleting local file since it's invalid.\n"
			rm -f $PATH_LOCAL_PKG_OMNI/$FILENAME_PKG_OMNI_GPG
		fi
	fi
fi

# Unpack Omni package if necessary...
if [ "$DO_UNPACK_OMNI" = "true" ]; then
	addToLogOmni "Attempting package decryption..."
	printf "Attempting package decryption...\n"
	decryptPackageOmni
	FUNCTION_RESULT_CODE=$?
	if [ $FUNCTION_RESULT_CODE -eq 0 ]; then
		#decryption succeeded
		addToLogOmni "Attempting package decompression..."
		printf "Attempting package decompression...\n"
		decompressPackageOmni
		FUNCTION_RESULT_CODE=$?
		if [ $FUNCTION_RESULT_CODE -eq 0 ]; then
			#decompression succeeded
			addToLogOmni "Attempting package unpacking..."
			printf "Attempting package unmpacking...\n"
			unpackPackageOmni
			FUNCTION_RESULT_CODE=$?
			if [ $FUNCTION_RESULT_CODE -eq 0 ]; then
				#unpackaging succeeded
				addToLogOmni "Package unpacked successfully. Done."
				printf "Package unpacked successfully. Done.\n"
			else
				#unpackaging failed
				addToLogOmni "Package failed to unpack, aborting."
				printf "Package failed to unpack, aborting.\n"
			fi
		else
			#decompression failed
			addToLogOmni "Package failed to decompress, aborting."
			printf "Package failed to decompress, aborting.\n"
		fi
	else
		#decryption failed
		addToLogOmni "Package failed to decrypt, aborting."
		printf "Package failed to decrypt, aborting.\n"
	fi
fi
