#!/bin/bash

#######################################################################
# Access an Omni's command line, remotely using SSH.
# This can be done manually, but it's hard to remember all args...
#	OMNIIP=192.168.1.83; ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /root/.ssh/id_rsa root@$OMNIIP -p 8022
#
# Usage:
# sshOmni.sh [Omni IP]
#
# Exit codes:
# 1: No arguments provided.
#
# Revisions:
# 2019.07.25	CSR	Created.
#######################################################################


#######################################################################
### CONFIGURATION #####################################################
OMNI_SSH_USER="root"
OMNI_SSH_PORT=8022
SERVER_RSA_ID_FILE="/root/.ssh/id_rsa"

#######################################################################
### FUNCTIONS #########################################################


#######################################################################
### MAIN ROUTINE ######################################################
if [ $# -eq 0 ]; then
	printf "No arguments supplied! At least provide Omni IP address.\n"
	exit 1;
fi

OMNI_IP=$1
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $SERVER_RSA_ID_FILE $OMNI_SSH_USER@$OMNI_IP -p $OMNI_SSH_PORT
