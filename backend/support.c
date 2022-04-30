/**********************************************************************
**  Module:     support.c
**
**  Author:     (redacted), Chris Rider
**          Copyright (c) 1991 - 2021
**
**********************************************************************/

#define DEBUG_LOG_SEQUENCING 0

#define USE_SOUND 1
#define CLEAR_EMPTY_SLOTS 1
#define USE_FLASH_NEW_MESSAGE 1
#define PHOENIX_SEND_AS_ONE_MESSAGE 1
#define USE_MULTIPLE_ACU_PACKET 1
#define USE_VLC_STREAMING 1		/* vlc does video streaming */
#define USE_GSTREAMER_STREAMING 1	/* gstreamer does video streaming */
#define USE_LIST_IN_LIST 1		/* support lists in lists */
#define USE_IPIO_HELLO 1		/* IPIO8 needs to login with hello string */
#define USE_NONBLOCK_SOCKET 1
#define SMBANNER_ACU_RETRY_COUNT 	(5)

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <fcntl.h>
#include <ctype.h>

#ifndef MSGNET_WIN32

#if defined (MSGNET_MAC)
#include <termios.h>
#else
#include <termio.h>
#endif
#include <math.h>

#endif

#include <errno.h>

#ifndef MSGNET_WIN32

#include <netdb.h>
#include <sys/time.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/file.h>
#include <arpa/inet.h>
#include <fnmatch.h>
#include <pwd.h>


/***** PORTIONS REDACTED (681 lines) *****/


#else

#include <winsock2.h>
#include <windows.h>
#include <stddef.h>
#include <process.h>
#include <stdio.h>
#include <conio.h>
#include "commport.h"
#include "signapi.h"
#include "clntsock.h"

#endif

#include <unistd.h>
#include <time.h>
// PORTIONS REDACTED
#include "support_signallight.h"
#include "support_evolution.h"
#include "smmulticast.h"
#include "api_asterisk.h"
#include "voicetts.h"


/***** PORTIONS REDACTED (212 lines) *****/


#define BANNER_FLASH_MESSAGE_DURATION		(20)		/* seconds to flash the new message message */

#define TEMPERATURE_MONITOR_INTERVAL		(5*60)
#define USB_CAMERA_MONITOR_INTERVAL			(5*60)		/* check every 5 minutes */
#define	EXTERNAL_STROBE_RECONNECT_INTERVAL	(60*60)		/* reconnect every 60 minutes */


/***** PORTIONS REDACTED (6,611 lines) *****/


/******************************************************************
** static int TypeIsEvolutionApp(int type)
**
**  Evolution app.
**
******************************************************************/
static int TypeIsEvolutionApp(int type)
{
int ret = FALSE;

#ifdef USE_EVOLUTION
switch(type)
	{
	case DEVICE_EVOLUTION_APP:
		ret = TRUE;
		break;

	default:
		ret = FALSE;
		break;
	}
#endif

return(ret);
}


/***** PORTIONS REDACTED (9,382 lines) *****/


#define CHECK_FIFO_LATER 	(1)
#define CHECK_FIFO_NOW		(-1)
#define CHECK_FIFO_EVOLUTION	(666)
/*********************************************************************
** static int check_pop_fifo(int action)
**
**  Will search all boards for poping messages of the stack.
**
**	only actually check when (when_to_check == 0)
**	otherwise not needed
**
*********************************************************************/
static int check_pop_fifo(int action)
{
static int when_to_check = 0;

if(DiagnosticCheck(DIAGNOSTIC_FIFO)) DIAGNOSTIC_LOG_2("check_pop_fifo() action=%d when_to_check=%d.", action, when_to_check);
//DIAGNOSTIC_LOG_2("ZZZ check_pop_fifo() action=%d when_to_check=%d.", action, when_to_check);

if(action == CHECK_FIFO_LATER)
	{
	/* check for resetting as multiple CHECK_FIFO_NOW */
	/* the when_to_check would be very negative */
	if(when_to_check < 0)
		{
		when_to_check = 0;
		}
	}
else if(action == CHECK_FIFO_EVOLUTION)
	{
	when_to_check = -661;	// 666 will be added below, making it 5
	}
else 
	{
	/* never be delayed too long to return to checking off fifo */
	/* was a result of thousands in a command list when_to_check was thousands */
	if(when_to_check > 5)
		{
		when_to_check = 5;
		}
	}

when_to_check += action;

if(when_to_check == 0)
	{
//DIAGNOSTIC_LOG("ZZZ check_pop_fifo() POPPING!!!");
	PriorityPopHigherPriorityMessages(NULL);
	PriorityPushLowerPriorityMessages(NULL, -1, -1);

	/* make sure we have everything poped off as the original */
	/* message is now off the board_ptr[] array from the push and */
	/* since that slot is now available check for poping anything off */
	PriorityPopHigherPriorityMessages(NULL);
	}

return(0);
}


/***** PORTIONS REDACTED (10,845 lines) *****/


#ifdef USE_EVOLUTION
if(doPopForEvolution)
	{
	//DIAGNOSTIC_LOG("ZZZ ********** GOING TO POP FIFO! ******************");
	check_pop_fifo(CHECK_FIFO_EVOLUTION);
	//PriorityPushLowerPriorityMessages(NULL, -1, -1);
	//PriorityPopHigherPriorityMessages(NULL);
	}
else
	{
	check_pop_fifo(CHECK_FIFO_LATER);
	}
#else
check_pop_fifo(CHECK_FIFO_LATER);
#endif

DIAGNOSTIC_FUNCTION_EXIT_R("%d", ret);
return(ret);
}


/***** LARGE PORTIONS REDACTED (14,782 lines) *****/


/*******************************************************************
** int banner_init(int ignore_delete_broadcast, int startup_purge, DBRECORD slow_device_record_number, int api_only)
**
**  Initialize all the needed databases for the banner board
**  process system.
**
**  Returns:
**      1 - error
**      0 - ok
**
********************************************************************/
int banner_init(int ignore_delete_broadcast, int startup_purge, DBRECORD slow_device_record_number, int api_only)
{
int i;

int next;

char buffer[40];


/***** LARGE PORTIONS REDACTED (4,225 lines) *****/


if(check_if_server())
	{
	int i;

	/* load in devices in the correct order */
	const HARDWARE_DEVICE_NAME server_hardware_list[] =
		{
		DEVICE_DIALOUT,

		DEVICE_PULL_STATION,
		DEVICE_DUCT_DETECTOR,
		DEVICE_SMOKE_DETECTOR,
		DEVICE_MOTION_DETECTOR,
		DEVICE_DOOR_OPEN,
		DEVICE_WATER_FLOW,

		DEVICE_SOUND_METER_MS6701,
		DEVICE_SOUND_METER_IPSPEAKER,

		DEVICE_POCSAG_TRANSMIT,
		DEVICE_TNPP_TRANSMIT,
		DEVICE_JTECH_TRANSMIT,
		DEVICE_TAP_TERMINAL_SINGLE,
		DEVICE_TAP_TERMINAL_MULTIPLE,
	
		DEVICE_ACU,
		DEVICE_EXTERNAL_STROBE,
		DEVICE_INOVONICS_FA403,
		DEVICE_INOVONICS_ECHOSTREAM,
		DEVICE_WIRED_CC_IN,
		DEVICE_WIRED_CC_OUT,
		DEVICE_AND_CONTACT_IN,
		DEVICE_AND_CONTACT_OUT,
		DEVICE_WIRED_DIGITAL_RELAY_IN,
		DEVICE_WIRED_DIGITAL_RELAY_OUT,
		DEVICE_WIRED_IPIO8_RELAY_IN,
		DEVICE_WIRED_IPIO8_RELAY_OUT,
		DEVICE_WIRED_IBOOT,
		DEVICE_WIRED_MOBILE_RADIO,
		DEVICE_VOICE_SERVER,
		DEVICE_VOICE_SOUNDCARD,
		DEVICE_VOICE_PORT,
		DEVICE_DIGIUM_DAHDI_TRUNK,
		DEVICE_DIGIUM_SIP_TRUNK,
		DEVICE_DIGIUM_IAX2_TRUNK,
		DEVICE_DIGIUM_H323_TRUNK,
		DEVICE_DIGIUM_DIRECT_LINE,
		DEVICE_VOICE_PORT_IN_AND_OUT,
		DEVICE_SMDR_INPUT,
		DEVICE_CAMERA,
		DEVICE_CAMERA_RTSP,
		DEVICE_CAMERA_MEDIAPORT,
		DEVICE_CAMERA_AXIS_HTTP,
		DEVICE_CAMERA_AXIS_RTSP,
		DEVICE_CAMERA_LEVELONE_HTTP,
		DEVICE_CAMERA_LEVELONE_RTSP,
		DEVICE_CAMERA_LEVELONE_FCS6020,
		DEVICE_CAMERA_CISCO_RTSP,
		DEVICE_CAMERA_CISCO_WVC54G,
		DEVICE_CAMERA_MULTICAST_RTSP,

		DEVICE_BANB,
		DEVICE_BANB_32K,
		DEVICE_BETA_DIRECTOR,
		DEVICE_BETA_WINDOW,
		DEVICE_ALPHABIGDOT,
		DEVICE_ALPHAECLIPSE,
		DEVICE_ALPHA215C,
		DEVICE_ALPHA220C,
		DEVICE_ALPHA320C,
		DEVICE_ALPHA330C,
		DEVICE_ALPHAPPD,
		DEVICE_ALPHA4080C,
		DEVICE_ALPHA4120C,
		DEVICE_ALPHA4160C,
		DEVICE_ALPHA4200C,
		DEVICE_ALPHA4240C,
		DEVICE_ALPHA7080C,
		DEVICE_ALPHA7120C,
		DEVICE_ALPHA7160C,
		DEVICE_ALPHA7200C,
		DEVICE_AMS_CLOCK,
		DEVICE_AMS_LOCAL_WIRELESS_TIME_SYNC,
		DEVICE_PHOENIX_3035,
		DEVICE_PHOENIX_4045,
		DEVICE_PHOENIX_5010,
		DEVICE_CREE_RC880,
		DEVICE_CREE_RC8196,
		DEVICE_CREE_RC8200,
		DEVICE_EXL_3000,
		#ifdef USE_EVOLUTION
		DEVICE_EVOLUTION_APP,
		#endif
		DEVICE_AND_IPSPEAKER,
		DEVICE_AND_IPSPEAKER_ONLY,
		DEVICE_PA_IP_SIP,
		DEVICE_MEDIAPORT_FIREPANEL_EVAC_SIP,
		#ifdef USE_HUE_LIGHT
		DEVICE_HUE_LIGHT,
		DEVICE_HUE_BRIDGE,
		#endif
		DEVICE_RSS_INPUT,
		DEVICE_RSS_OUTPUT,
		DEVICE_DAKTRONICS_GALAXY,
		DEVICE_HX_SIGNS,
		DEVICE_PROLITE_TRUCOLORII,
		DEVICE_PROLITE_XP2020,
		DEVICE_PROLITE_XP3014,
		DEVICE_PROLITE_SUNBUSTER,
		DEVICE_EACAP_OUTPUT,
		DEVICE_EACAP_INPUT,
		DEVICE_WINDOWS_PCALERT,
		DEVICE_MEDIAPORT_LCD,
		DEVICE_MEDIAPORT_LCD_GA,
		DEVICE_MEDIAPORT_LCD_TVC,
		DEVICE_MEDIAPORT_PCALERT,
		DEVICE_MEDIAPORT_CONTROL_PAD,
		DEVICE_BROWSER_SIGN_TICKER,
		DEVICE_BROWSER_SIGN_FULL,
		DEVICE_PUSHED_INBOX,
		DEVICE_OPERATOR_CONSOLE,
		DEVICE_VIRTUAL_PAGER,
	
		DEVICE_VOICE_MAIL_PAGE,
		DEVICE_RS232_INPUT,
		DEVICE_RS232_INPUT_HARD_FLOW,
		DEVICE_FIREPANEL_INPUT,
		DEVICE_FIREPANEL_INPUT_HARD_FLOW,
		DEVICE_MEDIAPORT_AUDIO_VISUAL_STATION,
		DEVICE_MEDIAPORT_TWOWAY_RADIO,
		DEVICE_MEDIAPORT_FIREPANEL_EVAC,
		DEVICE_MEDIAPORT_AUDIO_STREAMING,
		DEVICE_ASCII_SIGN,
		DEVICE_ASCII_SIGN_HARD_FLOW,

		DEVICE_LINE_PRINTER_OUT,
		DEVICE_LINE_PRINTER_OUT_HARD_FLOW,
	
		DEVICE_TAP_DIRECT,

		DEVICE_NOTHING
		};


/***** PORTIONS REDACTED (721 lines) *****/


}


/***** LARGE PORTIONS REDACTED (17,829 lines) *****/


/********************************************************************
** static int BannerCheckAudioToMediaPortsAndPCAlerts(char *audio_group, DBRECORD mediaport_recno, int *intercom_state_return)
**
**	This can be audio to PC Alerts or to Soundcards.
**
**	return is the number of pc alerts. can be 1 for all pc's or 1 
**	for just 1 pc.
**
********************************************************************/
static int BannerCheckAudioToMediaPortsAndPCAlerts(char *audio_group, DBRECORD mediaport_recno, int *intercom_state_return)
{


/***** LARGE PORTIONS REDACTED (7,882 lines) *****/


		else if(BannerCheckRecordedVoiceFile(db_bann->dbb_stream_number, input_file, sizeof(input_file), BannerGetVoiceSuffix()) >= 0)
			{
			/* convert the vox voice file to a wav file */
			/* something windows likes */
			DIAGNOSTIC_LOG_1("BannerCheckAudioToMediaPortsAndPCAlerts() using recorded file %s.", input_file);

			if(SystemSoxConvertFile(input_file, output_file, db_bann->dbb_audio_recorded_gain) != 0)
				{
				//ignore error_occured = TRUE;
				}

			strcpyl(use_previous_generated_output_file, output_file, sizeof(output_file));

			doCopyToHTTP = 1;	//set flag to copy the file to a public-downloadable HTTP location (for Omni/Evolution, etc.)
			typeOfAudioFlag = toa_RecordedVoice;
			}
		

/***** LARGE PORTIONS REDACTED (3,112 lines) *****/


		if(doCopyToHTTP == 1) 
		//if(1)	/*temporary allow omni to use cepstral for kevin's demo*/
			{
			#ifdef USE_EVOLUTION
			// Make the pc.###.wav file available for HTTP download so the device can simply download it. We do this with a simple symbolic link.
			char destFile[FILENAME_LENGTH];
			char execute_command[400];

			switch (typeOfAudioFlag) 
				{
				case toa_RecordedVoice:
					sprintf(destFile, "/home/silentm/public_html/multimedia/paFile_recordedVoice."FORMAT_DBRECORD_STR".%s.wav", db_bann_getcur(), remove_leading_space(db_bann->dbb_rec_dtsec));
					break;
				case toa_TTS:
					sprintf(destFile, "/home/silentm/public_html/multimedia/paFile_serverTTS."FORMAT_DBRECORD_STR".%s.wav", db_bann_getcur(), remove_leading_space(db_bann->dbb_rec_dtsec));
					break;
				}

			DIAGNOSTIC_LOG_1("Creating HTML destFile (%s) for PA file download by Evolution/Omni appliance.", destFile);
			//sprintf(execute_command, "/bin/ln -sfn %s %s", output_file, destFile);
			sprintf(execute_command, "/bin/cp %s %s", output_file, destFile);

			DiagnosticSystemCommand(execute_command, FALSE);
			#endif
			}


/***** LARGE PORTIONS REDACTED (2,884 lines) *****/

		
}


/***** LARGE PORTIONS REDACTED (992 lines) *****/


		#ifdef USE_EVOLUTION
		else if(TypeIsEvolutionApp(hw_ptr->type))
			{
			/* This seems necessary to get sequencing right -CR 2018.09.24 */
        		sprintf(buffer, "%c", EV_BASE + tseq[i]);
    			strcat(only_seq_letters, buffer);
			}
		#endif


/***** LARGE PORTIONS REDACTED (4,002 lines) *****/


	#ifdef USE_EVOLUTION
	else if(TypeIsEvolutionApp(hw_ptr->type))
        	{
        //	strcpy(ptr, sign_buf);
        //WARNING: the above line causes a message launch including an Omni to cause banner to segfault and the server to crash
        	strcpy(ptr, all_buf);
        	}
	#endif
	

/***** LARGE PORTIONS REDACTED (1,220 lines) *****/


/******************************************************************
** static void BannerCheckGenerateAsteriskFiles(struct _hardware *hw_ptr)
**
******************************************************************/
static void BannerCheckGenerateAsteriskFiles(struct _hardware *hw_ptr)
{
#ifndef MSGNET_WIN32
if(hw_ptr
	&& (hw_ptr->type == DEVICE_MEDIAPORT_FIREPANEL_EVAC
		 || hw_ptr->type == DEVICE_MEDIAPORT_FIREPANEL_EVAC_SIP
		 || hw_ptr->type == DEVICE_MEDIAPORT_TWOWAY_RADIO
		 || hw_ptr->type == DEVICE_MEDIAPORT_AUDIO_VISUAL_STATION
		 || hw_ptr->type == DEVICE_MEDIAPORT_AUDIO_STREAMING
		 || TypeIsEvolutionApp(hw_ptr->type)
		 || TypeIsMultimediaBoard(hw_ptr->type)))
	{
	AsteriskGenerateDNISFile(FALSE, FALSE, FALSE, FALSE, FALSE);

	AsteriskGenerateExtensionsFile(FALSE, NULL, 0);
	AsteriskGenerateExtensionsFile(TRUE, NULL, 0);

	if(hw_ptr->type == DEVICE_MEDIAPORT_FIREPANEL_EVAC_SIP
		&& CheckValidExtension(hw_ptr->hardware_device_username) > 0)
		{
		AsteriskGeneratePhoneConfig(db_list_getcur(), FALSE);
		}
	// AHG - Added to specifically regenerate the Omni config file we've updated
	if(TypeIsEvolutionApp(hw_ptr->type))
		{
        AsteriskGeneratePhoneConfig(db_list_getcur(), 2);
		}
	}
#endif
}


/***** LARGE PORTIONS REDACTED (1,117 lines) *****/


		#ifdef USE_EVOLUTION
		total += BannerSendDeviceToClient(DEVICE_EVOLUTION_APP, return_node, pid, child_pid, WTC_HARD_NEW);
		#endif
		

/***** LARGE PORTIONS REDACTED (3,020 lines) *****/


	/* for omni, whose wifi is less reliable, we just need to set flag and execute sync no matter what */
	if (hw_ptr && TypeIsEvolutionApp(hw_ptr->type))
		{
		// Update the device online/offline status...
		hw_ptr->device_connect_status = db_wtc->dwc_flag;
		db_hard->dhc_device_connected = db_wtc->dwc_flag;
		db_hard_write();
		HardwareDeviceStatusPublish();

		// Send current active messages...
		banner_clear(hw_ptr);					/* send configuration to board */
		execute_bann_sync_sign(hw_ptr);				/* send present messages */
		}


/***** LARGE PORTIONS REDACTED (5,449 lines) *****/


/********************************************************************
** int check_banner_node_commands(void)
** CR NOTE: This is the forked banner node for a given device. Device is selected by conditional tests and wtc data.
**
**  return:
**	0 is no error
**	1 is slow device was forked.
**      error is < 0
**
********************************************************************/
int check_banner_node_commands(void)
{


/***** PORTIONS REDACTED (2,478 lines) *****/


while(found > 0 && KendraDataErrorStatus() == FALSE)
	{
	

/***** LARGE PORTIONS REDACTED (6,872 lines) *****/


				/* Evolution Appliance...
				 * The Evolution app is capable of tracking and managing delivery of messages using its own processes.
				 * We just need to send it message data whenever key message events happen (like launch, clear, etc.). */
				/* Update 2017.08.22: FYI, we will also need to provide currently-active msgs whenever it requests it, which
				 * shall be done via the server-side "evolution_active_msg_recnos" array in the banner client node. */
				/* Update 2018.04.16: Idea might now be to use sequence in addition to new-msg, so server does the work for Omni. */
				else if(TypeIsEvolutionApp(HardwareDecodeDevice(db_wtc->dwc_return_node)))
					{
					#if defined(MSGNET_WIN32) || defined(MSGNET_MAC)
					DIAGNOSTIC_LOG_1("New Evolution message '%s'", db_wtc->dwc_return_node);
					#else

					#ifdef USE_EVOLUTION
					DBRECORD evolution_device_msg_stream_recno = db_wtc->dwc_stream_number;		//get this message stream's record number	
					DBRECORD evolution_device_msg_template_recno = db_wtc->dwc_parent_recno;	//get this message template's record number	

					/** NOTE: This seems to be the trick to not sending multiple copies of (old sequence?) messages */
        	               		if(db_wtc->dwc_message_type != BANNER_SEQUENCE_NUMBER)
						{	
						// The following fires when there is a new message to be sent to the sign.
						// Note: We need to get all banner record data for shipment to the device, so it knows what to do. To do that, we simply grab currency for the banner record, using stream info.
						if(db_wtc->dwc_message_type & BANNER_NEW_MESSAGE)
							{
							if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
								{
								DIAGNOSTIC_LOG("*************************************************");
								DIAGNOSTIC_LOG_1("***DEBUG*** check_banner_node_commands: New Message! ("FORMAT_DBRECORD_STR")", evolution_device_msg_stream_recno);
								}
							//get currency for stream record so we can get any/all of its data
							if(db_bann_setcur(evolution_device_msg_stream_recno) > 0)
								{
								if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG)) DIAGNOSTIC_LOG_3("            Stream "FORMAT_DBRECORD_STR". Duration %ld. Sequence #%d.", evolution_device_msg_stream_recno, db_bann->dbb_duration, db_wtc->dwc_operation);
	
								//send_to_evolution_appliance(hw_ptr, db_wtc->dwc_msg_buffer, db_wtc->dwc_operation, db_wtc->dwc_message_type, evolution_device_new_msg_stream_recno);
								//send_to_evolution_appliance_discreteMsg(hw_ptr, BANNER_EVOLUTION_CMD_NEW_MESSAGE, evolution_device_msg_stream_recno, db_wtc->dwc_operation, db_wtc->dwc_msg_buffer);	//we'll depend on this routine to pull fields from the currency
								send_to_evolution_appliance_discreteMsg(hw_ptr, BANNER_EVOLUTION_CMD_NEW_MESSAGE, evolution_device_msg_stream_recno, db_wtc->dwc_operation, db_wtc->dwc_msg_buffer, evolution_device_msg_template_recno);	//we'll depend on this routine to pull fields from the currency
								}
							}

						// The following fires when there are NO messages supposed to be on the sign.
						if(db_wtc->dwc_message_type & BANNER_CLEAR_SIGN)
							{
							if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
								{
								DIAGNOSTIC_LOG("*************************************************");
								DIAGNOSTIC_LOG("***DEBUG*** check_banner_node_commands: Clear Sign!");
								}

							send_to_evolution_appliance_discreteMsg(hw_ptr, BANNER_EVOLUTION_CMD_CLEAR_SIGN, 0, db_wtc->dwc_operation, db_wtc->dwc_msg_buffer, 0);

							//hw_ptr->board_ptr[db_wtc->dwc_operation].
							UCHAR start_slot = BB_BASE;
							int k;
							for(k = 0; k < hw_ptr->max_seq; k++, start_slot++)
								{
								if(hw_ptr->board_ptr[k].bann_recno > 0
									&& strchr(db_wtc->dwc_sequence, start_slot) == NULL)
									{
									hw_ptr->board_ptr[k].bann_recno = 0;
									}
								}

							if(banner_check_node_wtc_delete())
								{
			        				db_wtc_delete();
								}
							}

						// The following fires when there is a message to remove from the sign.
						if(db_wtc->dwc_message_type & BANNER_EVOLUTION_CMD_STOP_MESSAGE)
							{
							if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
								{
								DIAGNOSTIC_LOG("*************************************************");
								DIAGNOSTIC_LOG_1("***DEBUG*** check_banner_node_commands: Stop a message! ("FORMAT_DBRECORD_STR")", evolution_device_msg_stream_recno);
								}
							send_to_evolution_appliance_discreteMsg(hw_ptr, BANNER_EVOLUTION_CMD_STOP_MESSAGE, evolution_device_msg_stream_recno, db_wtc->dwc_operation, db_wtc->dwc_msg_buffer, evolution_device_msg_template_recno);
							}

						}	//end   db_wtc->dwc_message_type != BANNER_SEQUENCE_NUMBER

					// The following fires when the messages have changed somehow
					else if(db_wtc->dwc_message_type & BANNER_SEQUENCE_NUMBER)
						{
						if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
							{
							DIAGNOSTIC_LOG("*************************************************");
							DIAGNOSTIC_LOG_2("***DEBUG*** check_banner_node_commands: Sequence Number Change! (seq %d: stream recno "FORMAT_DBRECORD_STR")", db_wtc->dwc_operation, evolution_device_msg_stream_recno);
							}
						send_to_evolution_appliance_discreteMsg(hw_ptr, BANNER_EVOLUTION_CMD_SEQ_NUMBER, evolution_device_msg_stream_recno, db_wtc->dwc_operation, db_wtc->dwc_msg_buffer, evolution_device_msg_template_recno);		//we'll depend on this routine to pull fields from the currency
						}



					#else
					DIAGNOSTIC_LOG("Evolution not defined as enabled in this codebase! Doing nothing.");
					#endif

					#endif
					}
				

/***** LARGE PORTIONS REDACTED (3,581 lines) *****/


			else if(TypeIsEvolutionApp(hw_ptr->type))
				{
				if(BannerMonitorEvolutionApp(hw_ptr) >= 0)
					{
					socketid = 1;
					}

				int current_fd = hw_ptr->fd;

				if(send_to_evolution_appliance(hw_ptr, "", 0, BANNER_IPSPEAKER_CHECKING_CONNECT))
					{
					if(current_fd < 0 && hw_ptr->fd >= 0)
						{
						DIAGNOSTIC_LOG_1("Port now active on %s", HardwareReportPort(hw_ptr));
						banner_client_sync(hw_ptr->record_number, SMBANNER_SYNC_MESSAGES_ONLY);
						}

					socketid = 1;
					}
				else
					{
					DIAGNOSTIC_LOG_1("Port not active on %s", HardwareReportPort(hw_ptr));
					HardwareDisablePort(hw_ptr, TRUE, TRUE);
					socketid = -1;
					}

				valid_device = TRUE;
				}
	

/***** LARGE PORTIONS REDACTED (2,398 lines) *****/