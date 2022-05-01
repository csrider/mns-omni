#!/bin/sh
#
# version.sh
#
SILENTM_HOME=`cat /etc/[REDACTED]`
TARBUILD=$SILENTM_HOME/Versions

if [ $(ls -l /bin/sh | grep -c dash) -eq 1 ]
then
        # pure posix shells (e.g. dash on ubuntu) don't like the bash-specific source keyword
        . $SILENTM_HOME/bin/smfunctions
else
        # bash on centos is fine with source
        source $SILENTM_HOME/bin/smfunctions
fi

release=`grep SILENT_MESSENGER_BUILD $SILENTM_HOME/src/smrev.h | cut -d ' ' -f 7`

yui_clean_for_release()
	{
	# The following commands will take YUI's build directory from 39MB to 16MB:
	echo Clean YUI files
	find ../public_html/javascripts/yui/build -name "*-coverage.js" -print0 | xargs -0 rm 2> /dev/null
	find ../public_html/javascripts/yui/build -name "*-debug.js" -print0 | xargs -0 rm 2> /dev/null

	# remove the non "*-min.js" files
	echo Clean YUI files leaving min.js files
	# cannot do this one as it removes widget-base.css and there is no widget-base-min.css
	# find ../public_html/javascripts/yui/build -not -name "*-min.js" -print0 | xargs -0 rm 2> /dev/null

	# Then, you could completely delete the following (cuts out ~31MB):
	echo Clean YUI directories
	rm -rf /home/silentm/public_html/javascripts/yui/docs 2> /dev/null
	rm -rf /home/silentm/public_html/javascripts/yui/tests 2> /dev/null
	rm -rf /home/silentm/public_html/javascripts/yui/releasenotes 2> /dev/null

	# A special note about the "api" subdirectory... I removed it and tested on my dev environment, and everything still works. I'm now fairly certain that it is just the API documentation, and is safe to remove for production. And it really is worth considering for deletion, since it's the largest aspect (at 157MB)!
	rm -rf /home/silentm/public_html/javascripts/yui/api 2> /dev/null
	}

yui_clean_for_release

##### REDACTED #####

# remove debugging symbols from executables

##### REDACTED #####

strip ../bin/smtwitter

##### REDACTED #####

strip ../bin/smajax

cd /

chgrp_and_owner silentm silentm home/silentm/bin/smajax

##### REDACTED #####

chgrp_and_owner root root "home/silentm/bin/*.def"
chmod 644 home/silentm/bin/*.def

echo Tarring /home/silentm/bin

##### REDACTED #####

tar -rpvf $TARBUILD/silentm.tar home/silentm/bin/smtwitter

##### REDACTED #####

# adding the self extractor tools
tar -rpvf $TARBUILD/silentm.tar home/silentm/bin/unzipsfx.exe

# MessageNet
chown silentm /home/silentm/public_html/bin/*.cgi
chgrp silentm /home/silentm/public_html/bin/*.cgi
chmod 744     /home/silentm/public_html/bin/*.cgi

##### REDACTED #####

substitute $TARBUILD/silentm.tar home/silentm/public_html/bin	smomninotify.cgi /home/silentm/src/smomninotify.cgi	silentm silentm 755

##### REDACTED #####

substitute $TARBUILD/silentm.tar home/silentm/bin 	smset_evolution.sh /home/silentm/src/smset_evolution.sh		root root 744

##### REDACTED #####

substitute $TARBUILD/silentm.tar home/silentm/bin 	update_evolution.sh /home/silentm/src/update_evolution.sh	root root 744
substitute $TARBUILD/silentm.tar home/silentm/bin 	runPatchBI325.sh /home/silentm/src/patchScripts/runPatchBI325.sh	root root 744
substitute $TARBUILD/silentm.tar home/silentm/bin 	runPatchBI325.2.sh /home/silentm/src/patchScripts/runPatchBI325.2.sh	root root 744
substitute $TARBUILD/silentm.tar home/silentm/bin 	runPatchBI325.3.sh /home/silentm/src/patchScripts/runPatchBI325.3.sh	root root 744
substitute $TARBUILD/silentm.tar home/silentm/bin 	runPatchBI325.4.sh /home/silentm/src/patchScripts/runPatchBI325.4.sh	root root 744
substitute $TARBUILD/silentm.tar home/silentm/bin 	parseOmniLogData_toCSV.sh /home/silentm/src/parseOmniLogData_toCSV.sh	root root 744
substitute $TARBUILD/silentm.tar home/silentm/bin 	parseOmniLogs.sh /home/silentm/src/parseOmniLogs.sh		root root 744

##### REDACTED #####

substitute $TARBUILD/silentm.tar home/silentm/bin 	strttwitter.sh /home/silentm/src/strttwitter.sh			root root 744

##### REDACTED #####

substitute $TARBUILD/silentm.tar home/silentm/bin 	watchdog.sh /home/silentm/src/watchdog.sh			silentm users 544
substitute $TARBUILD/silentm.tar home/silentm/bin 	mediaportCleanup.sh /home/silentm/src/mediaportCleanup.sh	silentm users 544
substitute $TARBUILD/silentm.tar home/silentm/bin 	periodicReboot.sh /home/silentm/src/periodicReboot.sh		silentm users 544

##### REDACTED #####

tar -rpvf $TARBUILD/silentm.tar home/silentm/twitter/OAuth.php
tar -rpvf $TARBUILD/silentm.tar home/silentm/twitter/tweetViaOAuth_args.php
tar -rpvf $TARBUILD/silentm.tar home/silentm/twitter/twitteroauth.php

##### REDACTED #####

# these files are for installation only and get put into an install
# directory, doinst.sh looks for LCK...silentm to determine install/update
substitute $TARBUILD/silentm.tar install 		doinst.sh /home/silentm/src/doinst.sh				root root 700
##### REDACTED #####

# Permissions are for silentm so the CGI can update these files.
##### REDACTED #####

# Prepare the gifs
chmod 644 home/silentm/public_html/gifs/*.gif home/silentm/public_html/gifs/*.JPG home/silentm/public_html/gifs/*.jpg home/silentm/public_html/gifs/*.png home/silentm/public_html/gifs/*.ico
chmod 644 home/silentm/public_html/gifs.original/*.gif home/silentm/public_html/gifs.original/*.JPG home/silentm/public_html/gifs.original/*.jpg home/silentm/public_html/gifs.original/*.png /home/silentm/public_html/gifs.original/*.ico
chgrp_and_owner silentm silentm "home/silentm/public_html/gifs/*.gif home/silentm/public_html/gifs/*.JPG home/silentm/public_html/gifs/*.jpg home/silentm/public_html/gifs/*.png home/silentm/public_html/gifs/*.ico"
chgrp_and_owner silentm silentm "home/silentm/public_html/gifs.original/*.gif home/silentm/public_html/gifs.original/*.JPG home/silentm/public_html/gifs.original/*.jpg home/silentm/public_html/gifs.original/*.png home/silentm/public_html/gifs.original/*.ico"

##### REDACTED #####

tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/gaScreenNew.png
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/gaScreenAssociated.png

##### REDACTED #####

tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/favicon.ico
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/icons

##### REDACTED #####

tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/more.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/undo.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/help2.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/straighten0.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/straighten1.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_route.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_route_add.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_route_delete.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_route_add_depressed.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_route_add_depressed_ghosted.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_mediaport_map_off_with_route_connected.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_mediaport_map_off_with_route_not_connected.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/maps_button.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/saveRoute.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/showRoute.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/showRoute_depressed.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeNormal.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeThick.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeThin.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeNormal_selected.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeThick_selected.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeThin_selected.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routeMgrBtn.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/deviceMgrBtn.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routingCanvasSample.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routingColorChooserRed.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routingThickChooser.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/routingToolbar.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_mediaport_with_route_connected.gif
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/gifs.original/map_mediaport_with_route_not_connected.gif

# Include the help files
chmod 644 home/silentm/public_html/*.js home/silentm/public_html/*.css home/silentm/public_html/*.htm home/silentm/public_html/*.html

##### REDACTED #####

chgrp_and_owner silentm silentm "home/silentm/public_html/*.js"
chgrp_and_owner silentm silentm "home/silentm/public_html/*.css"
chgrp_and_owner silentm silentm "home/silentm/public_html/*.htm"
chgrp_and_owner silentm silentm "home/silentm/public_html/*.html"
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/css/smcgi_favorites.min.css

##### REDACTED #####

tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/graphical_annunciator_help.htm
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/locations_manager_help.htm
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/routing_manager_help.htm

tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/how_to_define_omni.htm

##### REDACTED #####

chown -R silentm /home/silentm/public_html/javascripts
chgrp -R silentm /home/silentm/public_html/javascripts
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/context_menu.js
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/colorPicker.js
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/colorPickerPopup.html
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/excanvas.js
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/modalbox
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/favorites

tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/yui
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/javascripts/yui.messagenet

##### OMNI STUFF #############################################
# Package up the Omni app builds
# (note: these are symlinks and depend on them pointing to the correct actual APK)
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolution.apk
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolutionwatchdog.apk
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolutionupdater.apk
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolutionflasherlights.apk
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.omniwatchdogwatcher.apk

# Update and include Omni app build checksum files
# (note: these calculate APK files pointed to by symlinks and depend on them pointing to the correct actual APK)
md5sum /home/silentm/public_html/com.messagenetsystems.evolution.apk | awk '{printf $1}' > /home/silentm/public_html/com.messagenetsystems.evolution.md5
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolution.md5

md5sum /home/silentm/public_html/com.messagenetsystems.evolutionwatchdog.apk | awk '{printf $1}' > /home/silentm/public_html/com.messagenetsystems.evolutionwatchdog.md5
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolutionwatchdog.md5

md5sum /home/silentm/public_html/com.messagenetsystems.evolutionupdater.apk | awk '{printf $1}' > /home/silentm/public_html/com.messagenetsystems.evolutionupdater.md5
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolutionupdater.md5

md5sum /home/silentm/public_html/com.messagenetsystems.evolutionflasherlights.apk | awk '{printf $1}' > /home/silentm/public_html/com.messagenetsystems.evolutionflasherlights.md5
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.evolutionflasherlights.md5

md5sum /home/silentm/public_html/com.messagenetsystems.omniwatchdogwatcher.apk | awk '{printf $1}' > /home/silentm/public_html/com.messagenetsystems.omniwatchdogwatcher.md5
tar -rpvf $TARBUILD/silentm.tar home/silentm/public_html/com.messagenetsystems.omniwatchdogwatcher.md5

# Omni support files and stuff
substitute $TARBUILD/silentm.tar home/silentm/bin 	checkAndGetUpdates.sh 		/home/silentm/src/checkAndGetUpdates.sh		root root 755
substitute $TARBUILD/silentm.tar home/silentm 		autoUpdatePassPhrase.txt 	/home/silentm/src/autoUpdatePassPhrase.txt 	root root 444
substitute $TARBUILD/silentm.tar home/silentm/bin 	sshOmni.sh 			/home/silentm/src/sshOmni.sh			root root 755

##### REDACTED #####

# Include sample gif for Location screens.
chgrp_and_owner silentm silentm "home/silentm/public_html/floor_plans/sample.gif"
tar -rpvf $TARBUILD/silentm.tar /home/silentm/public_html/floor_plans/sample.gif

cd $TARBUILD

##### REDACTED #####

gzip -v silentm.tar

if [ -e /etc/redhat-release ] 
then
	if [ "`uname -a | grep x86_64`" = "" ]
	then
		BUILD_TYPE="i386"
	else
		BUILD_TYPE="x86_64"
	fi

	# This is also in rpm.sh
	RHVERSION1=`cat /etc/redhat-release | cut -f 1 -d ' '`
	if [ "$RHVERSION1" = "CentOS" ] 
	then
		# Example: CentOS release 4.0 (Final)
		RHVERSION2=""
		RHVERSION3=$MN_OS
	else
		# Example: Red Hat Linux release 7.3 (Valhalla)
		RHVERSION2=`cat /etc/redhat-release | cut -f 2 -d ' ' | cut -f 1 -d '.'`
		RHVERSION3=`cat /etc/redhat-release | cut -f 5 -d ' ' | cut -f 1 -d '.'`
	fi

	mv silentm.tar.gz sm1.$release/$RHVERSION1$RHVERSION2.$RHVERSION3.silentm.$release.$BUILD_TYPE.tgz

	# Note: --passphrase-file is only a valid option on OS >= CentOS 5. Older systems will just error out and thats OK.
	gpg --batch --passphrase-file /home/silentm/src/pass_phrase.txt -c sm1.$release/$RHVERSION1$RHVERSION2.$RHVERSION3.silentm.$release.$BUILD_TYPE.tgz
else
	mv silentm.tar.gz sm1.$release/silentm.$release.tgz

	# Note: --passphrase-file is only a valid option on OS >= CentOS 5. Older systems will just error out and thats OK.
	gpg --passphrase-file /home/silentm/src/pass_phrase.txt -c sm1.$release/silentm.$release.tgz
fi

cd ../src
