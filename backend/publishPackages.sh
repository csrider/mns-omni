#!/bin/bash
###############################################################################################################
# This will do all the necessary steps to "publish" or active a new version of app(s)... It will:
#	- update symlinks to latest version of APK
#	- copy those symlinked APK files to /home/silentm/public_html
#	- and generate corresponding MD5 checksum files for them
#
# This is necessary whenever you finish a development effort and before you do a pull request or before merging into main codebase.
# It might also be used if you want to test automatic updating in a development environment?
#
# Revisions:
#	2019.02.26	Chris Rider	Created.
#	2019.02.27	Chris Rider	Updated to update symlinks (otherwise it's kinda useless).
#	2019.07.17	Chris Rider	Added support for generating encrypted update-server package.
###############################################################################################################

# Update symlinks
printf "\nUpdating symbolic links to latest app versions...\n"
currDir=$(pwd)
for dAPK in releaseBuilds_com.messagenetsystems.*; do
	#figure out name for the symlink to the APK
	PACKAGE_APK_FILENAME_FOR_SYMLINK_NAME="$(echo $dAPK | cut -d'_' -f2).apk"

	#figure out the latest APK file to make our symlink's target
	PACKAGE_APK_FILENAME_FOR_SYMLINK_TARGET="$(ls -lt $dAPK/*.apk | head -n1 | cut -d'/' -f2)"

	#update symlink
	#printf "Will update symlink \"%s\" to point to \"%s/%s\"\n" "$PACKAGE_APK_FILENAME_FOR_SYMLINK_NAME" "$dAPK" "$PACKAGE_APK_FILENAME_FOR_SYMLINK_TARGET"	#DEBUG
	ln -sfn $dAPK/$PACKAGE_APK_FILENAME_FOR_SYMLINK_TARGET $PACKAGE_APK_FILENAME_FOR_SYMLINK_NAME
done

# Copy files
printf "\nCopying APK files to public_html...\n"
rsync -v -L com.messagenetsystems.*.apk /home/silentm/public_html

# Set permissions of copied files
printf "\nSetting permissions of copied files...\n"
chmod 444 /home/silentm/public_html/com.messagenetsystems.*.apk

# Generate checksum files of those copied files
printf "\nGenerating MD5 files in copied-files location...\n"
for fAPK in /home/silentm/public_html/com.messagenetsystems.*.apk; do
	APK_CHECKSUM_VALUE="$(md5sum $fAPK | awk '{printf $1}')"
	APK_CHECKSUM_FILE="$(echo "${fAPK%.*}").md5"

	#printf "DEBUG:  \"%s\" will save to \"%s\"\n" "$APK_CHECKSUM_VALUE" "$APK_CHECKSUM_FILE"		#DEBUG

	printf "$APK_CHECKSUM_VALUE" > $APK_CHECKSUM_FILE
done

# Tar and encrypt files for updater server package
# Note: this prepares an update package and places it in the local public_html directory (where else to put it??), so you'll need to copy it to the cloud server to make it available to auto update clients!
printf "\nPackaging, compressing, and encrypting files for update server package...\n"
PKG_FILE_TEMP="/tmp/omniUpdatePackage.tar"
PKG_FILE_FINAL="/home/silentm/public_html/omniUpdatePackage.tar.gz.gpg"
PKG_FILE_FINAL_MD5="/home/silentm/public_html/omniUpdatePackage.tar.gz.gpg.md5"
GPG_PW_FILE="/home/silentm/src/autoUpdatePassPhrase.txt"
rm -f $PKG_FILE_TEMP
for updatePackageMemberFile in /home/silentm/public_html/com.messagenetsystems.*; do
	tar --append --verbose --absolute-names --file $PKG_FILE_TEMP $updatePackageMemberFile
done
rm -f $PKG_FILE_TEMP.gz
gzip --verbose $PKG_FILE_TEMP								#this will compress in-place (replacing the $TAR_FILE)		#NOTE: unzip: gunzip -d [file]
PKG_FILE_TEMP="$PKG_FILE_TEMP.gz"
cat $GPG_PW_FILE | gpg --batch --yes --passphrase-fd 0 --symmetric $PKG_FILE_TEMP	#this will create a new .gpg file with the input's name, in the input's directory (decrypt: cat gpgPassPhrase.txt | gpg --decrypt --batch --yes --passphrase-fd 0 -o /tmp/dec /tmp/testzip.gz.gpg)
PKG_FILE_TEMP="$PKG_FILE_TEMP.gpg"
mv $PKG_FILE_TEMP $PKG_FILE_FINAL							#move the completed temp file (all compressed and encrypted now) to final destination
PKG_CHECKSUM_VALUE="$(md5sum $PKG_FILE_FINAL | awk '{printf $1}')"			#calculate checksum value
printf "$PKG_CHECKSUM_VALUE" > $PKG_FILE_FINAL_MD5					#save checksum to file (this is what the customer server will use to validate integrity of its download)
chmod 444 $PKG_FILE_FINAL
chmod 444 $PKG_FILE_FINAL_MD5
printf " (Remember to upload the update-package to the update-server when ready to release!)\n"

printf "DONE\n"
