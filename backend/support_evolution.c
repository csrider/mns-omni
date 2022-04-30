/********************************************************************
** 	Module:	support_evolution.c
**
**	Author:	Chris Rider
**
**	Revisions:
**	2017.05 - 06	Chris Rider	Creation (based on support_ipspeaker.c)
**	2018.11.13	Chris Rider	Implemented evolution_clear_ip (not yet sure 
**   whether it'll fix multiple forked banner nodes issue, but can't hurt?)
**
********************************************************************/

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <ctype.h>

#ifdef USE_XML_PARSER
#include <libxml/parser.h>
#endif

#include "local.h"
#include "db_wtc.h"
#include "fm_hardw.h"
#include "db_hardw.h"
#include "fm_syspa.h"
#include "db_syspa.h"
#include "fm_banne.h"
#include "db_banne.h"
#include "db_forw.h"
#include "allsigns.h"
#include "banner.h"
#include "diagnost.h"
#include "timeout.h"
#include "api_asterisk.h"
#include "support_evolution.h"
#include "db_voice.h"
#include "db_signs.h"
#include "db_clist.h"
#include "fm_staff.h"
#include "db_staff.h"

int evolution_debug = FALSE;

char ip_port[10] = "8080";

static int pause_first;		//ipspeaker relic resurrected

static int evolution_get_color(char *message);
static int evolution_get_bgcolor(char *message);

DBRECORD new_msg_recno_just_sent_by_newmsg = 0;

struct _hardware *hw_ptr_local;

/*****************************************************************
** static void evolution_clear_ip(struct _hardware *hw_ptr)
**
**	might have been an error so if this IP speaker is using
**	auto_term_ip from asterisk then clear it as we might have 
**	a new IP address.
**
****************************************************************/
static void evolution_clear_ip(struct _hardware *hw_ptr)
{
if(hw_ptr->auto_term_ip)
	{
	strcpy(hw_ptr->term_ip, "");
	}
}

/*******************************************************
** int evolution_appliance_find_address(struct _hardware *hw_ptr)
** 
**	return 0  no address
**	return 1 address found 
**
*******************************************************/
int evolution_appliance_find_address(struct _hardware *hw_ptr)
{
int found = FALSE;

#ifdef linux
if(notjustspace(hw_ptr->term_ip, IP_LENGTH))
	{
	found = TRUE;
	}
#endif

return(found);
}

/*******************************************************
** int BannerMonitorEvolutionApp(struct _hardware *hw_ptr)
**
** 	This will open a socket, make a connection to an
** 	Evolution device, and see if it's reachable. The
** 	result will be reflected on device-status screens.
**
**	This is normally called from the support.c file, 
**	as part of the smbanner process, every 5 mins.
**
**	What device it attempts to make contact with is
**	passed in via that device's hardware structure.
**
**	- Return 1 success
**	- Return 0 failure
**
**	2017.05.24	CSR	Created.
**
*******************************************************/
int BannerMonitorEvolutionApp(struct _hardware *hw_ptr)
{
int ret = FALSE;
int socket_return;

char http_txn_string[500] = "";

if(evolution_debug || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
	{
        DIAGNOSTIC_LOG("BannerMonitorEvolutionApp() excuting...");
	}

if(evolution_appliance_find_address(hw_ptr) == FALSE)
	{
	HardwareReportSystemAlerts(hw_ptr);
        HardwareDisablePort(hw_ptr, TRUE, TRUE);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);

	// error already printed
	return(-1);
	}

strcatl(http_txn_string, "GET ", sizeof(http_txn_string));
strcatl(http_txn_string, "/ping?password=", sizeof(http_txn_string));				//this is just a GET string we've arbitrarily made up and coded into the Evolution app (which looks for it to respond with)
strcatl(http_txn_string, hw_ptr->hardware_device_password, sizeof(http_txn_string));
remove_trailing_space(http_txn_string);
strcatl(http_txn_string, " HTTP/1.1\r\n", sizeof(http_txn_string));
strcatl(http_txn_string, "\r\n", sizeof(http_txn_string));

if(evolution_debug || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
	{
        DIAGNOSTIC_LOG_2("BannerMonitorEvolutionApp(): Will check %s:%s", hw_ptr->term_ip, ip_port);
        DIAGNOSTIC_LOG_1("BannerMonitorEvolutionApp(): http_txn_string = '%s'", http_txn_string);
	}

hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, 0);
if(hw_ptr->fd < 0)
	{
       	// took too long to send data to port so lets disable it
        DIAGNOSTIC_LOG_1("SystemSocketConnect() report error %s", HardwareReportPortError(hw_ptr));

	evolution_clear_ip(hw_ptr);
	HardwareReportSystemAlerts(hw_ptr);
        HardwareDisablePort(hw_ptr, TRUE, TRUE);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);

        ret = -1;
        }
else
	{
	HardwareSystemAlertClear(hw_ptr);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);

	SystemSocketWrite(hw_ptr->fd, http_txn_string, strlen(http_txn_string));

	socket_return = SystemSocketReadTimeout(hw_ptr->fd, http_txn_string, sizeof(http_txn_string), 10);
	SystemTruncateReturnBuffer(http_txn_string, sizeof(http_txn_string), socket_return);
	SystemSocketClose(hw_ptr->fd);

	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG_1("SystemSocketReadTimeout() Response = '%s'", http_txn_string);
		}
	else if (DiagnosticCheck(DIAGNOSTIC_EVOLUTION))
		{
		// just trying to minimize log file size
		DIAGNOSTIC_LOG_1("%s", http_txn_string);
		}

	ret = 1;
	}

return(ret);
}

/*******************************************
** static int evolution_get_color(char *message)
** 
*******************************************/
static int evolution_get_color(char *message)
{
char *ptr;
char *color_str = "\x1b\x43";		/* "\x1bC" */

int ret = SIGN_COLOR_RED;		/* red is default */

ptr = strstr(message, color_str);
if(ptr)
	{
	ptr += 2;	/* get the color */

	ret = *ptr; 
	}

return(ret);
}

/*******************************************
** static int evolution_get_bgcolor(char *message)
** 
*******************************************/
static int evolution_get_bgcolor(char *message)
{
char *ptr;
char *color_str = "\x1b\x42";		/* "\x1bB" */

int ret = SIGN_COLOR_BLACK;		/* black is default */

ptr = strstr(message, color_str);
if(ptr)
	{
	ptr += 2;	/* get the color */

	ret = *ptr; 
	}

return(ret);
}

/******************************************************************
** static int translate_for_evo(struct _hardware *hw_ptr, char inbuffer[], char *outbuffer, int outbuffer_size)
**
**	return TRANSLATE_NOT_SUPPORTED command is not supported and just return.
**
*******************************************************************/
static int translate_for_evo(struct _hardware *hw_ptr, char *inbuffer, char *outbuffer, int outbuffer_size)
{
int ret = TRANSLATE_SUPPORTED;
int done = FALSE;
int current_color = evolution_get_color(inbuffer);
int current_bgcolor = evolution_get_bgcolor(inbuffer);

char *begin = outbuffer;

char new_font[40] = "";
char new_mode[40] = "";
char speed[15] = "";

while(*inbuffer && done == FALSE && ((outbuffer - begin) < (outbuffer_size - 20)))
	{
	switch(*inbuffer)
		{
		/* this is where you should substitute characters that would mess up JSON */
		/*
		case ' ':
			*outbuffer++ = '+';
			*outbuffer = 0;

			inbuffer++;
			break;

		case '&':
			*outbuffer++ = '%';
			*outbuffer++ = '2';
			*outbuffer++ = '6';
			*outbuffer = 0;

			inbuffer++;
			break;
		*/
		case '"':
			*outbuffer++ = '\\';
			*outbuffer++ = '"';
			*outbuffer = 0;

			inbuffer++;
			break;

		case SIGN_COMMAND:
			{
			inbuffer++;
			switch(*inbuffer++)
				{
				case SIGN_SPEED:
					{
					switch(*inbuffer)
						{
						case '1':
							sprintf(speed, "P%d", 1);
							break;
						case '2':
							sprintf(speed, "P%d", 1);
							break;
						case '3':
							sprintf(speed, "P%d", 1);
							break;
						case '4':
							sprintf(speed, "P%d", 1);
							break;
						case '5':
							sprintf(speed, "P%d", 1);
							break;
						case '6':
							sprintf(speed, "P%d", 1);
							break;
						case '7':
							sprintf(speed, "P%d", 1);
							break;
						case '8':
							sprintf(speed, "P%d", 1);
							break;
						}

					inbuffer = pointer_increment(inbuffer);
					}
					break;

				case SIGN_CONFIGURE:
					/* nothing to send */
					/* go back to time */
					return(0);
					break;

				case SIGN_FONT:
					switch(*inbuffer)
						{
						default:
						case SIGN_FONT_NORMAL:
							strcpy(new_font, "");
							break;

						case SIGN_FONT_BOLD:
							strcpy(new_font, "");
							break;

						case SIGN_FONT_LARGE:
							strcpy(new_font, "");
							break;

						case SIGN_FONT_FIVEHIGH:
							strcpy(new_font, "");
							break;

						case SIGN_FONT_SEVENHIGH_STANDARD:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_SEVENHIGH_FANCY:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_TENHIGH_STANDARD:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_SIXTEENHIGH_FANCY:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_SIXTEENHIGH_STANDARD:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_MAXHIGH_FANCY:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_MAXHIGH_STANDARD:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_DOUBLE_STROKE_ON:
							break;
							
						case SIGN_FONT_DOUBLE_STROKE_OFF:
							break;
							
						case SIGN_FONT_DOUBLE_WIDE_ON:
							break;
							
						case SIGN_FONT_DOUBLE_WIDE_OFF:
							break;
							
						case SIGN_FONT_SPACING_CONSTANT:
							strcpy(new_font, "");
							break;
							
						case SIGN_FONT_PROPORTIONAL_SPACE:
							strcpy(new_font, "");
							break;
						}

					inbuffer = pointer_increment(inbuffer);
					break;

				case SIGN_DATEEMBED:
					/* not currently supported - difficult for them to do */
					break;

				case SIGN_TIMEEMBED:
					if(strlen(begin) == 0)
						{
						/* Showing time first in message so pause */
						pause_first = TRUE;
						}

//					strcatl(outbuffer, "{pause=2}", outbuffer_size);		/* show time for 2 seconds */
//					outbuffer += strlen(outbuffer);
					/* going back to time so empty message */
					break;

				case SIGN_SIGNATUREEMBED:
					inbuffer = pointer_increment(inbuffer);

#ifdef SUNRISE_SUPPORT_GRAPHICS
#else
					strcat(outbuffer, SUBSTITUTE_SIGNATURE_TEXT);
					outbuffer += strlen(SUBSTITUTE_SIGNATURE_TEXT);
#endif
					break;

				case SIGN_AUTHORITYEMBED:
					/* ignore */
					inbuffer = pointer_increment(inbuffer);
					break;

/*
				case SIGN_GRAPHEMBED:
					{
					UCHAR graphic_label;
					UCHAR graphic_name_text[DECODE_LENGTH];

					graphic_label = *inbuffer;

					inbuffer = extract_graphic_name_text(inbuffer, graphic_name_text, sizeof(graphic_name_text));

#ifdef SUNRISE_SUPPORT_GRAPHICS
#else
					strcat(outbuffer, graphic_name_text);
					outbuffer += strlen(graphic_name_text);
#endif
					}
*/
					break;

				case SIGN_MODE:
					if(strlen(new_mode) > 0)
						{
						/* add the last mode before setting the new mode */
						/* start a new control */
						/* but start only on the second mode not the first */
						/* as an ^i^i mess's up the sign */
						strcatl(outbuffer, "", 3);
						outbuffer += 2;
						}

					inbuffer = pointer_increment(inbuffer);
					break;

				case SIGN_SEQUENCE:
					/* ignore */
					inbuffer = pointer_increment(inbuffer);
					done = TRUE;
					break;

				case SIGN_FCOLOR:
					//#define USE_AND_COLOR (1)
					#ifdef USE_AND_COLOR
					if(current_color != *inbuffer)
						{
						switch(*inbuffer)
							{
							default:
							case SIGN_COLOR_RED:
								strcatl(outbuffer, "{color=red}", outbuffer_size);
								break;
							case SIGN_COLOR_GREEN:
								strcatl(outbuffer, "{color=green}", outbuffer_size);
								break;
							case SIGN_COLOR_AMBER:
								strcatl(outbuffer, "{color=amber}", outbuffer_size);
								break;
							case SIGN_COLOR_YELLOW:
								strcatl(outbuffer, "{color=yellow}", outbuffer_size);
								break;
							case SIGN_COLOR_ORANGE:
								strcatl(outbuffer, "{color=orange}", outbuffer_size);
								break;
							case SIGN_COLOR_BLACK:
								strcatl(outbuffer, "{color=black}", outbuffer_size);
								break;
	
							case SIGN_COLOR_MULTI:
							case SIGN_COLOR_SWITCH:
							case SIGN_COLOR_PURPLE:
							case SIGN_COLOR_BROWN:
							case SIGN_COLOR_MAGENTA:
							case SIGN_COLOR_LIGHT_GREEN:
							case SIGN_COLOR_LIGHT_BLUE:
							case SIGN_COLOR_CYAN:
							case SIGN_COLOR_PINK:
							case SIGN_COLOR_BLUE:
							case SIGN_COLOR_CREAM:
							case SIGN_COLOR_GRAY:
							case SIGN_COLOR_WHITE:
								strcatl(outbuffer, "", outbuffer_size);			/* */
								break;
							}
						}

					current_color = *inbuffer;
					inbuffer = pointer_increment(inbuffer);
					outbuffer += strlen(outbuffer);
					#else
					inbuffer = pointer_increment(inbuffer);			 /* ignore the color */
					#endif
					break;

				case SIGN_JUSTIFY:
					/* ignore */
					inbuffer = pointer_increment(inbuffer);
					break;

				case SIGN_BCOLOR:
					#ifdef USE_AND_COLOR
					if(current_bgcolor != *inbuffer)
						{
						switch(*inbuffer)
							{
							default:
							case SIGN_COLOR_RED:
								strcatl(outbuffer, "{bgcolor=red}", outbuffer_size);
								break;
							case SIGN_COLOR_GREEN:
								strcatl(outbuffer, "{bgcolor=green}", outbuffer_size);
								break;
							case SIGN_COLOR_AMBER:
								strcatl(outbuffer, "{bgcolor=amber}", outbuffer_size);
								break;
							case SIGN_COLOR_YELLOW:
								strcatl(outbuffer, "{bgcolor=yellow}", outbuffer_size);
								break;
							case SIGN_COLOR_ORANGE:
								strcatl(outbuffer, "{bgcolor=orange}", outbuffer_size);
								break;
							case SIGN_COLOR_BLACK:
								strcatl(outbuffer, "{bgcolor=black}", outbuffer_size);
								break;

							case SIGN_COLOR_MULTI:
							case SIGN_COLOR_SWITCH:
							case SIGN_COLOR_PURPLE:
							case SIGN_COLOR_BROWN:
							case SIGN_COLOR_MAGENTA:
							case SIGN_COLOR_LIGHT_GREEN:
							case SIGN_COLOR_LIGHT_BLUE:
							case SIGN_COLOR_CYAN:
							case SIGN_COLOR_PINK:
							case SIGN_COLOR_BLUE:
							case SIGN_COLOR_CREAM:
							case SIGN_COLOR_GRAY:
							case SIGN_COLOR_WHITE:
								strcatl(outbuffer, "", outbuffer_size);			/* */
								break;
							}
						}

					current_bgcolor = *inbuffer;
					inbuffer = pointer_increment(inbuffer);
					outbuffer += strlen(outbuffer);
					#else
					inbuffer = pointer_increment(inbuffer);			 /* ignore the color */
					#endif
					break;

				case SIGN_TIMESET:
					/* skip this command ntp is used */
					break;

				default:
					/* skip this command not implemented */
					break;
				}
			}
			break;

		case SIGN_CR:
			/* not supported on ipspeaker */
			inbuffer++;
			break;

		case SIGN_BLOCK_CHARACTER:
			inbuffer++;
			break;

		default:
			*outbuffer++ = *inbuffer++;
			*outbuffer = 0;
			break;
		}
	}

return(ret);
}

int isFileOpen = FALSE;
int isFileOutOpen = FALSE;
/** does_msg_json_exist_in_file
 * Return the number of times the specified json_msg string exists in the file.
 * NOTE: This will disregard the sequence number portion of the JSON!
 * NOTE: This will disregard the dtsec portion of the JSON! (since it changes even for the same active message, if reissued by banner on some kind of update)
 * 	2017.08.30	CSR:	Creation.
 * 	2017.08.31	CSR:	Doesn't seem to be matching properly in bench testing?
 * 	2017.09.01	CSR:	Still using, but depending more heavily on remove_msg_json_from_file() to work in the first place.
 * 	2017.09.12	CSR:	Adding is-open flags, and NULL assignment after file close.
 * 				Updated json msg length.
 */
int does_msg_json_exist_in_file(struct _hardware *hw_ptr, char *json_msg)
	{
	int ret = 0;
	char str_filename[256];
	FILE *file_evolution_active_msgs = NULL;
	char json_msg_copy[EVOLUTION_JSON_MESSAGE_LENGTH] = "";
	int len;

	char line_buffer[EVOLUTION_JSON_MESSAGE_LENGTH];			//make sure we're larger than the line could possibly be, or fgets will iterate at the limit and not the new-line character as we desire

	char json_msg_without_seqnum[EVOLUTION_JSON_MESSAGE_LENGTH];		//just for storing a copy of the line buffer without the sequence number in it (so comparisons will be accurate)
	char line_buffer_without_seqnum[EVOLUTION_JSON_MESSAGE_LENGTH];		//just for storing a copy of the line buffer without the sequence number in it (so comparisons will be accurate)
	char json_msg_without_dtsec[EVOLUTION_JSON_MESSAGE_LENGTH];		//just for storing a copy of the line buffer (without seqnum) without the dtsec in it (so comparisons will be accurate)
	char line_buffer_without_dtsec[EVOLUTION_JSON_MESSAGE_LENGTH];		//just for storing a copy of the line buffer (without seqnum) without the dtsec in it (so comparisons will be accurate)

	char *tokenPtr = NULL;							//for supporting parsing so we can disregard certain portions of the JSON (quick and dirty parsing)
	char tempTokenStr[BUFSIZ] = "";
	int i;

	int debug_to_log = 1;

	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_ptr->record_number);
	remove_trailing_space(str_filename);

	file_evolution_active_msgs = fopen(str_filename, "r");
	if(file_evolution_active_msgs)
		{
		isFileOpen = TRUE;

		//make a copy of the json_msg string parameter (so we don't mangle the original -- remember, this is C)
		strcpyl(json_msg_copy, json_msg, EVOLUTION_JSON_MESSAGE_LENGTH);

		//save another copy of the copied json param, but with the seq num stripped out
		tokenPtr = strtok(json_msg_copy, ",");					//the first comma should be the one right after the "signseqnum" name/value pair... move pointer to that location in the string
		tokenPtr++;								//move past the comma one byte
		i = 1;
		while(tokenPtr != NULL)							/* loop through the rest of the record-line to the end, iterating over each delimiter */
			{
			if (i > 1)							/* if we are past the first token (seqnum), then go on */
				{
				sprintf(tempTokenStr, ",%s", tokenPtr);
				strcat(json_msg_without_seqnum, tempTokenStr);
				}
			tokenPtr = strtok(NULL, ",");					/* attempt to move to next token (returns null if no more exist, which will cause an exit from this loop) */
			i++;
			}

		//save a copy of the json param, but with the seq num AND dtsec stripped out
		//NOTE: this is a destructive operation to the data in json_msg_without_seqnum, but we probably don't care
		tokenPtr = strtok(json_msg_without_seqnum, ",");			//the first comma should be the one right after the "dbb_rec_dtsec" name/value pair... move pointer to that location in the string
		tokenPtr++;								//move past the comma one byte
		i = 1;
		while(tokenPtr != NULL)							/* loop through the rest of the record-line to the end, iterating over each delimiter */
			{
			if (i > 1)							/* if we are past the first token (seqnum), then go on */
				{
				sprintf(tempTokenStr, ",%s", tokenPtr);
				strcat(json_msg_without_dtsec, tempTokenStr);
				}
			tokenPtr = strtok(NULL, ",");					/* attempt to move to next token (returns null if no more exist, which will cause an exit from this loop) */
			i++;
			}
		if (debug_to_log) DIAGNOSTIC_LOG_1("***** DEBUG does_msg_json_exist_in_file: json_msg (no seqnum and dtsec) = %s", json_msg_without_dtsec);

		//go through each line in the file (each line corresponds with a json message object)... iterating at each new-line character
		while(fgets(line_buffer, sizeof(line_buffer), file_evolution_active_msgs))
			{
			//remove newline [LF] at the end of the line so comparisons will be reliable (since our json arg doesn't have it)
			len = strlen(line_buffer);
			if (line_buffer[len-1] == '\n') line_buffer[len-1] = 0;

			//save a copy of the line, but with seq num stripped out (like above)
			//NOTE: this is a destructive operation to the data in line_buffer, but we probably don't care
			tokenPtr = strtok(line_buffer, ",");					//the first comma should be the one right after the "signseqnum" name/value pair... move pointer to that location in the string
			tokenPtr++;								//move past the comma one byte
			i = 1;
			while(tokenPtr != NULL)							/* loop through the rest of the record-line to the end, iterating over each delimiter */
				{
				if (i > 1)							/* if we are past the first token (seqnum), then go on */
					{
					sprintf(tempTokenStr, ",%s", tokenPtr);
					strcat(line_buffer_without_seqnum, tempTokenStr);
					}
				tokenPtr = strtok(NULL, ",");					/* attempt to move to next token (returns null if no more exist, which will cause an exit from this loop) */
				i++;
				}

			//save a copy of the line, but with seq num AND dtsec stripped out (like above)
			//NOTE: this is a destructive operation to the data in line_buffer_without_seqnum, but we probably don't care
			tokenPtr = strtok(line_buffer_without_seqnum, ",");			//the first comma should be the one right after the "dbb_rec_dtsec" name/value pair... move pointer to that location in the string
			tokenPtr++;								//move past the comma one byte
			i = 1;
			while(tokenPtr != NULL)							/* loop through the rest of the record-line to the end, iterating over each delimiter */
				{
				if (i > 1)							/* if we are past the first token (seqnum), then go on */
					{
					sprintf(tempTokenStr, ",%s", tokenPtr);
					strcat(line_buffer_without_dtsec, tempTokenStr);
					}
				tokenPtr = strtok(NULL, ",");					/* attempt to move to next token (returns null if no more exist, which will cause an exit from this loop) */
				i++;
				}
			//if (debug_to_log) DIAGNOSTIC_LOG_1("DEBUG does_msg_json_exist_in_file: line_buf (no seqnum and dtsec) = %s", line_buffer_without_dtsec);

			//if messages match, then increment counter
			if (strcmp(line_buffer_without_dtsec, json_msg_without_dtsec) == 0)
				{
				if (debug_to_log) DIAGNOSTIC_LOG("DEBUG does_msg_json_exist_in_file: Match found! Incrementing counter to return.");
				if (debug_to_log) DIAGNOSTIC_LOG_1("      line_buf (no seqnum and dtsec) = %s", line_buffer_without_dtsec);
				ret++;					//increment the counter for each found match
				}
			else if (strcmp(line_buffer_without_dtsec, json_msg_without_dtsec) < 0)
				{
				if (debug_to_log) DIAGNOSTIC_LOG("DEBUG does_msg_json_exist_in_file: NO match (file line < json arg).");
				if (debug_to_log) DIAGNOSTIC_LOG_1("      line_buf (no seqnum and dtsec) = %s", line_buffer_without_dtsec);
				}
			else if (strcmp(line_buffer_without_dtsec, json_msg_without_dtsec) > 0)
				{
				if (debug_to_log) DIAGNOSTIC_LOG("DEBUG does_msg_json_exist_in_file: NO match (file line > json arg).");
				if (debug_to_log) DIAGNOSTIC_LOG_1("      line_buf (no seqnum and dtsec) = %s", line_buffer_without_dtsec);
				}
			}

		fclose(file_evolution_active_msgs);			//close the file
		file_evolution_active_msgs = NULL;
		isFileOpen = FALSE;
		}

	DIAGNOSTIC_LOG_1("does_msg_json_exist_in_file: Returning %d.", ret);
	return ret;
	}
/** append_msg_json_to_file()
 * Appends a JSON object (containing msg data) to the file. (example JSON msg object in the file: {"signseqnum":3,"recno_zx":"345",...}\n )
 * Each JSON object (message) is delineated by a new-line (\n) just to make our lives in C a bit easier whenever we read (can just use fgets in a loop).
 * So, remember to account for that when parsing the JSON later (JSON expects a "," delimiter between objects in an array).
 * 	2017.08.30	CSR:	Creation & bench tested to work.
 * 	2017.09.08	CSR:	Changed stack buffer for json_msg_copy to a malloc'd object in memory - hoping to fix sometimes malformed JSON being saved to file
 * 	2017.09.12	CSR:	Adding is-open flags, and NULL assignment after file close.
 * 	2017.09.13	CSR:	Added wait loop if file is open by another task - won't append if file never becomes available.
 */
void append_msg_json_to_file(struct _hardware *hw_ptr, char *json_msg)
	{
	char str_filename[256];
	FILE *file_evolution_active_msgs = NULL;
	//char json_msg_copy[MAX_CHARS] = "";
	char *json_msg_copy = malloc(strlen(json_msg) + 1);
	int maxSecsToWait = 5;
	int i_wait = 0;
	int okayToAppend = TRUE;

	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_ptr->record_number);
	remove_trailing_space(str_filename);

	//if there are no matching msgs in the file, then add this to the file (function returns number of matches, so 0 means no matches and that we can add to the file)
	//strcpyl(json_msg_copy, json_msg, strlen(json_msg));
	//remove_trailing_space(json_msg_copy);
	strcpy(json_msg_copy, json_msg);
	if (does_msg_json_exist_in_file(hw_ptr, json_msg_copy) == 0)
		{
		//there are no matching json objects already in the file, so go ahead and add 

		//first, make sure file isn't already open by some other task (wait here for a bit if it is, hoping it will close and become available soon)
		while( isFileOutOpen )
			{
			DIAGNOSTIC_LOG_1("append_msg_json_to_file: JSON file is not closed. Waiting %d more seconds...", maxSecsToWait-i_wait);

			if( i_wait > maxSecsToWait )
				{
				DIAGNOSTIC_LOG("append_msg_json_to_file: WARNING: JSON file doesn't seem to be closing. Append will NOT happen!");
				okayToAppend = FALSE;
				break;
				}

			i_wait++;
			sleep(1);
			}

		//if we're okay to append, then do it
		if( okayToAppend )
			{
			file_evolution_active_msgs = fopen(str_filename, "a");		//try to open for appending (creates file if doesn't exist)
			if (file_evolution_active_msgs != NULL)
				{
				isFileOutOpen = TRUE;
	
				fprintf(file_evolution_active_msgs, json_msg_copy);	//write the JSON msg object to the file
				fprintf(file_evolution_active_msgs, "\n");		//write the new-line delimiter (for C we just delineate JSON objects with a new-line to make life easier... remember to parse and accommodate with a comma for actual JSON usage)
		
				fclose(file_evolution_active_msgs);			//close the file
				file_evolution_active_msgs = NULL;
				isFileOutOpen = FALSE;
				}
			else
				{
				DIAGNOSTIC_LOG_1("append_msg_json_to_file: ERROR! Could not open file for appending: %s", str_filename);
				}
			}
		}
	else
		{
		DIAGNOSTIC_LOG("append_msg_json_to_file: JSON message data already exists in the file. Not appending it.");
		}

	free(json_msg_copy);
	}

/** does_recno_exist_in_file()
 * Looks in the file to determine how many matching recnos exist.
 * Returns number of matching recnos.
 * Note: this is essentially a very limited JSON parser. It depends on a specifically structured JSON string (as generated further below).
 * 	2017.08.31	CSR:	Creation.
 * 	2017.09.01	CSR:	Bench tested to work.
 * 	2017.09.12	CSR:	Adding is-open flags, and NULL assignment after file close.
 * 				Updated json msg length.
 */
int does_recno_exist_in_file(struct _hardware *hw_ptr, DBRECORD recno)
	{
	int ret = 0;
	char str_filename[256];
	FILE *file_evolution_active_msgs = NULL;
	char line_buffer[EVOLUTION_JSON_MESSAGE_LENGTH] = "";		//for whole lines
	char *tokenPtr = NULL;						//for pointing to substrings
	char tokenStr_recno[BUFSIZ] = "";				//for holding substring value for recno value
	char strRecno[30];						//for holding a string version of the recno arg provided (so we can do a strcmp later)
	
	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_ptr->record_number);
	remove_trailing_space(str_filename);

	file_evolution_active_msgs = fopen(str_filename, "r");			//try to open for reading
	if( file_evolution_active_msgs )
		{
		isFileOpen = TRUE;

		//for each line in the file...
		//EXAMPLE LINE:  {"signseqnum":1,"dbb_rec_dtsec":"1188659674","recno_zx":"345","recno_template":"305","dbb_duration":50,"msgtype":"ANNOUNCEMENT","msgtext":"Test 2 |X|Admin System","msgdetails":"","dbb_audio_groups":[],"dbb_playtime_duration":0,"dbb_flasher_duration":0,"dbb_light_signal":" ","dbb_light_duration":0,"dbb_audio_tts_gain":0,"dbb_flash_new_message":"N","dbb_visible_time":"0","dbb_visible_frequency":"A","dbb_visible_duration":"0","dbb_record_voice_at_launch_selection":0,"dbb_record_voice_at_launch":"N","dbb_audio_recorded_gain":0,"dbb_pa_delivery_mode":"0","dbb_audio_repeat":"1","dbb_speed":4,"dbb_priority":200,"dbb_expire_priority":200,"dbb_priority_duration":50,"multimediatype":"Message","dbb_multimedia_audio_gain":0,"webpageurl":"FALSE"}
		while( fgets(line_buffer, sizeof(line_buffer), file_evolution_active_msgs) != NULL )
			{
			//find the first comma and move the ptr to it (tokenPtr becomes the value up to but excluding the comma)
			tokenPtr = strtok(line_buffer, ",");					//the first comma should be the one right after the "signseqnum" name/value pair... move pointer to that location in the string

			//loop through the rest of the record-line to the end, iterating over each comma delimiter until we find the name/value pair we want
			//for each delimited section of JSON until the end...
			//EXAMPLE SECTION:  "dbb_rec_dtsec":"1188659674"
			while(tokenPtr != NULL)
				{
				//DIAGNOSTIC_LOG_1("***************DEBUG: tokenPtr = '%s'", tokenPtr);	//each tokenPtr value should be the full and valid JSON name/value pair (ex. "dbb_visible_frequency":"A" )

				//advance the token to include the name of the name/value pair (up until the : char)
				tokenPtr = strtok(NULL, ":");

				//if we've arrived at the recno_zx name/value pair, dive into parsing its value (break out and continue to next line read in from file, when done)
				if( strcmp(tokenPtr, "\"recno_zx\"") == 0 )
					{
					//advance the token to the start of the actual recno value
					tokenPtr = strtok(NULL, ",");	//tokenPtr value should now be our recno value
					//DIAGNOSTIC_LOG_1("************DEBUG: tokenPtr = %s", tokenPtr);

					//see if the recno matches what we were given
					sprintf(strRecno, "\""FORMAT_DBRECORD_STR"\"", recno);	//since recno in JSON is a string, need to add quotes around the integer-like recno to compare it accurately
					remove_trailing_space(strRecno);
					if( strcmp(tokenPtr, strRecno) == 0 )
						{
						//DIAGNOSTIC_LOG_1("************DEBUG: MATCH!!! (recno %s)", tokenPtr);
						ret++;
						}

					tokenPtr = NULL;						//cause an exit of the tokenization loop, and continue with the line-read loop to the next line in the file
					}
				else
					{
					tokenPtr = strtok(NULL, ",");					//attempt to move to next token (name/value pair)... returns null if no more exist, which will cause an exit from this loop and continuance to the next line in the file
					}
				// (continue to the next section of JSON / name-value pair as long as there are any remaining or we haven't found the recno)
				}

			// (continue to next line in the file...)
			}

		fclose(file_evolution_active_msgs);
		file_evolution_active_msgs = NULL;
		isFileOpen = FALSE;
		}

	return ret;
	}

/** does_recno_exist_in_json_line()
 * Looks in the provided line of JSON to determine if it contains a matching recno.
 * The json_line string passed in should (must?) be of length EVOLUTION_JSON_MESSAGE_LENGTH.
 * Returns integer based true (1) or false (0).
 * Note: this is essentially a very limited JSON parser. It depends on a specifically structured JSON string (as generated further below).
 * 	2017.09.01	CSR:	Creation & bench tested to work.
 * 	2017.09.12	CSR:	Updated json msg length.
 */
int does_recno_exist_in_json_line(char *json_line, DBRECORD recno)
	{
	int ret = 0;							//initialize with default of false
	char json_line_copy[EVOLUTION_JSON_MESSAGE_LENGTH] = "";	//initialize a place to store a copy of the string we're given, so we don't mangle the original while tokenizing/parsing it
	char *tokenPtr = NULL;						//for pointing to substrings
	char tokenStr_recno[BUFSIZ] = "";				//for holding substring value for recno value
	char strRecno[30];						//for holding a string version of the recno arg provided (so we can do a strcmp later)
	
	strcpy(json_line_copy, json_line);		//DEV-NOTE: this should work as long as they're the same size?

	//find the first comma and move the ptr to it (tokenPtr becomes the value up to but excluding the comma)
	tokenPtr = strtok(json_line_copy, ",");		//the first comma should be the one right after the "signseqnum" name/value pair... move pointer to that location in the string

	//loop through the rest of the record-line to the end, iterating over each comma delimiter until we find the name/value pair we want
	//for each delimited section of JSON until the end...
	//EXAMPLE SECTION:  "dbb_rec_dtsec":"1188659674"
	while(tokenPtr != NULL)
		{
		//DIAGNOSTIC_LOG_1("***************DEBUG: tokenPtr = '%s'", tokenPtr);	//each tokenPtr value should be the full and valid JSON name/value pair (ex. "dbb_visible_frequency":"A" )

		//advance the token to include the name of the name/value pair (up until the : char) - so we can do a strcmp to figure out if the name matches what we're looking for
		tokenPtr = strtok(NULL, ":");

		//if we've arrived at the recno_zx name/value pair, dive into parsing its value (break out and continue to next line read in from file, when done)
		if( strcmp(tokenPtr, "\"recno_zx\"") == 0 )
			{
			//advance the token to the start of the actual recno value
			tokenPtr = strtok(NULL, ",");	//tokenPtr value should now be our recno value
			//DIAGNOSTIC_LOG_1("************DEBUG: tokenPtr = %s", tokenPtr);

			//see if the recno matches what we were given
			sprintf(strRecno, "\""FORMAT_DBRECORD_STR"\"", recno);	//since recno in JSON is a string, need to add quotes around the integer-like recno to compare it accurately
			remove_trailing_space(strRecno);
			if( strcmp(tokenPtr, strRecno) == 0 )
				{
				//DIAGNOSTIC_LOG_1("************DEBUG: MATCH!!! (recno %s)", tokenPtr);
				ret++;
				}

			tokenPtr = NULL;						//cause an exit of the tokenization loop, and continue with the line-read loop to the next line in the file
			}
		else
			{
			tokenPtr = strtok(NULL, ",");					//attempt to move to next token (name/value pair)... returns null if no more exist, which will cause an exit from this loop and continuance to the next line in the file
			}
		// (continue to the next section of JSON / name-value pair as long as there are any remaining or we haven't found the recno)
		}

	return ret;
	}

/** remove_msg_json_from_file()
 * Removes the JSON object (line) from the active msgs file for the specified ZX recno.
 * Essentially works by opening two files (one input, one output), and copying each line from in to out,
 * skipping the one we want to remove, and then deleting the original file and renaming the output to it.
 * 	2017.08.30	CSR:	Creation.
 * 	2017.09.01	CSR:	Bench tested to work.
 * 	2017.09.12	CSR:	Adding is-open flags, and NULL assignment after file close.
 * 				Added wait loop and unlink/rename override just in case files won't close. (shouldn't happen, but trying to insure against crashes)
 * 				Modified read loop and fgets to handle bad lines / bad data that could cause seg faulting.
 * 				Updated json msg length.
 */
void remove_msg_json_from_file(struct _hardware *hw_ptr, DBRECORD recno)
	{
	char str_filename[256];
	char str_filename_out[256];
	FILE *file_evolution_active_msgs = NULL;		//input file (with original data)
	FILE *file_evolution_active_msgs_out = NULL;		//output file (with updated data)
	char line_buffer[EVOLUTION_JSON_MESSAGE_LENGTH] = "";
	int didComplete = FALSE;
	int maxSecsToWait = 5;
	int i_wait = 0;
	int i_line = 0;

	//sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs.%s.json", hw_ptr->hardware_deviceid);
	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_ptr->record_number);
	remove_trailing_space(str_filename);

	//sprintf(str_filename_out, "/home/silentm/record/evolutionActiveMsgs.%s.out.json", hw_ptr->hardware_deviceid);
	sprintf(str_filename_out, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".out.json", hw_ptr->record_number);
	remove_trailing_space(str_filename_out);

	file_evolution_active_msgs = fopen(str_filename, "r");			//try to open for reading input
	if(file_evolution_active_msgs)
		{
		isFileOpen = TRUE;

		file_evolution_active_msgs_out = fopen(str_filename_out, "w");		//try to open for writing output (if exists, overwrite; if not, create new)
		if(file_evolution_active_msgs_out)
			{
			isFileOutOpen = TRUE;

			//for each line read from the file... (copy over if not removing; don't, if removing)
			/*
			while( fgets(line_buffer, sizeof(line_buffer), file_evolution_active_msgs) != NULL )
				{
				//if( feof(file_evolution_active_msgs) )
				//	{
				//	DIAGNOSTIC_LOG_1("EOF reached while reading %s. Finishing up.", str_filename);
				//	break;
				//	}

				if( does_recno_exist_in_json_line(line_buffer, recno) )
					{
					//line contains the recno we want to remove from the file, so don't copy it to the output file (which effectively deletes it)
					}
				else
					{
					//line does not contain the recno we want to remove, so try to copy this line over to the output file
					if( fputs(line_buffer, file_evolution_active_msgs_out) == EOF )
						{
						//some error happened, skip to next (effectively deleting the problematic line)
						DIAGNOSTIC_LOG("WARNING: There was some issue with a JSON line. It will be removed.");
						continue;
						}
					}
				}
			*/
			
			while( TRUE )
				{
				i_line++;

				//check whether fgets is reading valid lines (if NULL is returned then we are at EOF or some error)
				if( fgets(line_buffer, sizeof(line_buffer), file_evolution_active_msgs) == NULL) 
					{
					DIAGNOSTIC_LOG_2("EOF (or ERROR) reached while reading line #%d of file %s. Finishing up.", i_line, str_filename);
					break;
					}
				
				//at this point, we can be relatively sure that we have a valid line to pass to the test function
				if( does_recno_exist_in_json_line(line_buffer, recno) )
					{
					//line contains the recno we want to remove from the file, so don't copy it to the output file (which effectively deletes it)
					}
				else
					{
					//line does not contain the recno we want to remove, so try to copy this line over to the output file
					if( fputs(line_buffer, file_evolution_active_msgs_out) == EOF )
						{
						//some error happened, skip to next (effectively deleting the problematic line)
						DIAGNOSTIC_LOG("WARNING: There was some issue with a JSON line. It will be removed.");
						}
					}
				}
			
			fclose(file_evolution_active_msgs_out);
			file_evolution_active_msgs_out = NULL;
			isFileOutOpen = FALSE;
			didComplete = TRUE;
			}
		else
			{
			DIAGNOSTIC_LOG_1("remove_msg_json_from_file: ERROR! Could not open file '%s' for writing.", str_filename);
			}

		fclose(file_evolution_active_msgs);
		file_evolution_active_msgs = NULL;
		isFileOpen = FALSE;

		while( isFileOpen || isFileOutOpen )
			{
			DIAGNOSTIC_LOG("JSON file(s) are not closed. Waiting...");

			if( i_wait > maxSecsToWait )
				{
				DIAGNOSTIC_LOG("WARNING: JSON file(s) don't seem to be closing. Delete and rename will NOT happen!");
				didComplete = FALSE;		//override delete and rename below since file isn't closed
				break;
				}

			i_wait++;
			sleep(1);
			}

		if( didComplete == TRUE )
			{
			DIAGNOSTIC_LOG_1("DEBUG: Preparing to delete json file (%s)...", str_filename);
			unlink(str_filename);			//delete the original file
			DIAGNOSTIC_LOG_2("DEBUG: Preparing to rename json file (%s) -> (%s)...", str_filename_out, str_filename);
			rename(str_filename_out, str_filename); //and rename the output to the original's name
			}
		}
	else
		{
		DIAGNOSTIC_LOG_1("remove_msg_json_from_file: Could not open file '%s' for reading.", str_filename);	//this may be alright and expected
		}
	}

/** delete_msg_json_file()
 * Deletes the active msgs file for the specified hardware's banner client/node process.
 * 	2017.08.30	CSR:	Creation
 */
void delete_msg_json_file(struct _hardware *hw_ptr)
	{
	char str_filename[256];

	//sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs.%s.json", hw_ptr->hardware_deviceid);
	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_ptr->record_number);
	remove_trailing_space(str_filename);
	
	unlink(str_filename);
	}

/** Array for keeping track of active messages by their ZX recno.
 * evolution_active_msg_recnos[0] = recno of newest message
 * evolution_active_msg_recnos[1] = recno of second newest message
 * evolution_active_msg_recnos[2] = recno of third newest message
 * evolution_active_msg_recnos[MAX_SIGN_SEQUENCE] = recno of oldest message	*/
DBRECORD evolution_active_msg_recnos[MAX_SIGN_SEQUENCE] = { 0 };	//Initialize array of msg zx recnos (int) corresponding to active messages, hopefully kept up to date along with sequence data. This will live within scope of an active banner client node process (i.e. lives and dies with smstart/smstop).
int debug_evolution_active_msgs = 1;
int holdWhileRemoving = 0;

/** dump_contents_of_active_msg_array_to_file()
 * Dump the current contents of the array to a file.
 * 	2017.08.26	CSR:	Creation.
 * 	2017.08.30	CSR:	Might be deprecating in favor of the logic above.
 */
void dump_contents_of_active_msg_array_to_file(struct _hardware *hw_ptr)
	{
	int i;
	int count = 0;
	char str_filename[256];
	FILE *file_evolution_active_msg_recnos = NULL;

	//sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs.%s.json", hw_ptr_local->hardware_deviceid);
	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_ptr->record_number);
	remove_trailing_space(str_filename);

	file_evolution_active_msg_recnos = fopen(str_filename, "w");
	fprintf(file_evolution_active_msg_recnos, "{\"evolution_active_msg_recnos\":[");

	//for (i = 0; i <= MAX_SIGN_SEQUENCE; i++) 
	for (i = 0; i < MAX_SIGN_SEQUENCE; i++)	/*trying to fix ccpcheck error about buffer being accessed out of bounds for evolution_active_msg_recnos below*/
		{
		if (count > 0) fprintf(file_evolution_active_msg_recnos, ",");
		fprintf(file_evolution_active_msg_recnos, FORMAT_DBRECORD_STR, evolution_active_msg_recnos[i]);
		count++;
		}

	fprintf(file_evolution_active_msg_recnos, "]}\n");
	fclose(file_evolution_active_msg_recnos);
	file_evolution_active_msg_recnos = NULL;
	}

/** print_contents_of_active_msg_array()
 * Print the contents of the array to the log.
 * 	2017.08.22	CSR:	Creation.
 * 	2017.08.23	CSR:	Added argument to print only populated elements.
 * 				Added ability to print from the dtsec array.
 * 	2017.08.30	CSR:	Might be deprecating in favor of the logic above.
 */
void print_contents_of_active_msg_array(int printOnlyPopulatedElements)
	{
	int i;
	int emptyElementValue = 0;

	for (i = 0; i <= MAX_SIGN_SEQUENCE; i++) 
		{
		if (printOnlyPopulatedElements)
			{
			//print only populated elements
			if (evolution_active_msg_recnos[i] != emptyElementValue)
				{
				DIAGNOSTIC_LOG_2("  evolution_active_msg_recnos[%d] = "FORMAT_DBRECORD_STR, i, evolution_active_msg_recnos[i]);
				}
			}
		else
			{
			//print all elements, empty or not
			DIAGNOSTIC_LOG_2("  evolution_active_msg_recnos[%d] = "FORMAT_DBRECORD_STR, i, evolution_active_msg_recnos[i]);
			}
		}
	}

/** insert_recno_to_active_msg_array()
 * Insert a single new recno in front of array, shifting everything else off toward the end (last max element will be lost)
 * If pre-existing older matching recno(s) already exist in the array, clean them out before adding this one at the front.
 * 	2017.08.22	CSR:	Creation.
 * 	2017.08.24	CSR:	Added logic to prevent adding duplicate recnos (which should never be a valid situation when it comes to ZX records of currently-active messages).
 * 	2017.08.30	CSR:	Might be deprecating in favor of the logic above.
 */
void insert_recno_to_active_msg_array(DBRECORD valueToAdd)
	{
	int i;
	int position = 0;			//position where we want to insert the new value
	int count = 0;

	DIAGNOSTIC_LOG_1("insert_recno_to_active_msg_array: Starting for recno "FORMAT_DBRECORD_STR"...", valueToAdd);

	//count how many pre-existing matches there are, starting at oldest... also remember position of oldest duplicate
	for (i = MAX_SIGN_SEQUENCE; i >= 0; i--)
		{
		if (evolution_active_msg_recnos[i] == valueToAdd)
			{
			count++;
			}
		}

	//only insert if value to add doesn't already exist in the array (there should never be multiple same ZX recnos for active messages... a pre-existing duplicate would be old and no longer active... or sent to us in error -unlikely unless there's a problem in the core of banner)
	if (count == 0) 
		{
		DIAGNOSTIC_LOG_1(" Inserting new recno "FORMAT_DBRECORD_STR" and shifting all else toward end.", valueToAdd);

		//shift all values to one later slot in the array
		//(we start at the back of the array so we don't lose data as we pull elements' values over)
		for (i = MAX_SIGN_SEQUENCE - 1; i >= position - 1; i--)
			{
			//evolution_active_msg_recnos[i+1] = evolution_active_msg_recnos[i];	//save over the ith element with the i-1th element's value
			/*trying to fix ccpcheck (error) Array 'evolution_active_msg_recnos[31]' accessed at index 31, which is out of bounds.*/
			if (i+1 < MAX_SIGN_SEQUENCE)
				{
				evolution_active_msg_recnos[i+1] = evolution_active_msg_recnos[i];	//save over the ith element with the i-1th element's value
				}
			}

		//save our value to the 0th element
		evolution_active_msg_recnos[position] = valueToAdd;

		if (debug_evolution_active_msgs == 1) { DIAGNOSTIC_LOG(" AFTER INSERT..."); print_contents_of_active_msg_array(1); }
		}
	else
		{
		DIAGNOSTIC_LOG_2(" There are %d other '"FORMAT_DBRECORD_STR"' recnos in the active msg array. Removing oldest before recursing and inserting new...", count, valueToAdd);
		
		remove_recno_from_active_msg_array(valueToAdd, 1, 1);	//remove all pre-existing duplicates (beginning with oldest) --note: this function may recurse
		holdWhileRemoving = 1;					//set hold flag
		while (holdWhileRemoving);				//hold here until the removal routine clears the flag when it's done

		insert_recno_to_active_msg_array(valueToAdd);		//recurse into this function to try adding the value again
		}
	}

/** remove_recno_from_active_msg_array()
 * Remove a specific recno from array, shifting anything after it back toward the front.
 * Also, if multiple, consider argument to remove all matching multiple recnos.
 * 	2017.08.22	CSR:	Creation.
 * 	2017.08.23	CSR:	Added argument and logic to find all matching recnos and remove them, if they exist (shouldn't ever happen, but just to be safe).
 * 	2017.08.24	CSR:	Added ability to start removing duplicates from the oldest (right) instead of just the most recent (left-most).
 * 	2017.08.30	CSR:	Might be deprecating in favor of the logic above.
 */
void remove_recno_from_active_msg_array(DBRECORD valueToRemove, int removeAllMatching, int startFromOldest)
	{
	int i;
	int position = -1;
	int count = 0;

	DIAGNOSTIC_LOG_1("remove_recno_from_active_msg_array: Starting for recno "FORMAT_DBRECORD_STR"...", valueToRemove);
	
	//count how many matches there are
	for (i = 0; i <= MAX_SIGN_SEQUENCE; i++)
		{
		if (evolution_active_msg_recnos[i] == valueToRemove)
			{
			count++;
			}
		}

	//find the position of any match
	if (startFromOldest == 1)
		{
		//find the position of the oldest matching value
		//(note: this only find the last one (oldest one) if there are multiple (which should not happen)
		for (i = MAX_SIGN_SEQUENCE; i >= 0; i++)
			{
			if (evolution_active_msg_recnos[i] == valueToRemove)
				{
				position = i;
				break;
				}
			}
		}
	else
		{
		//find the position of the most recent matching value
		//(note: this only find the first one (most recent one) if there are multiple (which should not happen)
		for (i = 0; i <= MAX_SIGN_SEQUENCE; i++)
			{
			if (evolution_active_msg_recnos[i] == valueToRemove)
				{
				position = i;
				break;
				}
			}
		}

	//if we found a matched value's position, then proceed to remove it and shift stuff
	if (position > -1)
		{
		DIAGNOSTIC_LOG_1(" Removing recno "FORMAT_DBRECORD_STR" and shifting all else toward front.", valueToRemove);

		//starting at that position, shift all elements on the right toward the left
		//(we start at the position of the element that's being removed and work to the right, shifting each element left from there)
		for (i = position; i <= MAX_SIGN_SEQUENCE; i++)
			{
			if (position < MAX_SIGN_SEQUENCE)
				{
				//save the element's value on the right to us
				evolution_active_msg_recnos[i] = evolution_active_msg_recnos[i+1];	//save over the ith element with the i+1th element's value
				}
			else
				{
				//we're at last element, so handle differently so we don't segfault or something
				evolution_active_msg_recnos[i] = 0;
				}
			}

		if (debug_evolution_active_msgs == 1) { DIAGNOSTIC_LOG(" AFTER REMOVE..."); print_contents_of_active_msg_array(1); }
		}
	else
		{
		DIAGNOSTIC_LOG_1(" Could not find recno to remove ("FORMAT_DBRECORD_STR") in the evolution_active_msg_recnos array. Nothing removed or shifted.", valueToRemove);
		}

	//if there were multiple matches and we were told to remove all matches, recurse and do again
	if (count > 1 && removeAllMatching == 1)
		{
		DIAGNOSTIC_LOG_2(" There are %d additional matching '"FORMAT_DBRECORD_STR"' recnos to remove, recursing...", count-1, valueToRemove);
		remove_recno_from_active_msg_array(valueToRemove, removeAllMatching, startFromOldest);
		}

	if (count == 0)
		{
		holdWhileRemoving = 0;	//clear flag since we're definitely done with any/all removals
		}
	}

/** clear_active_msg_array() 
 * Initialize the array with all zeroes.
 * 	2017.08.23	CSR	Creation
 * 	2017.08.30	CSR:	Might be deprecating in favor of the logic above.
 */
void clear_active_msg_array()
	{
	int i;

	if (debug_evolution_active_msgs == 1) { DIAGNOSTIC_LOG(" BEFORE CLEAR..."); print_contents_of_active_msg_array(0); }

	//for (i = 0; i <= MAX_SIGN_SEQUENCE; i++)
	for (i = 0; i < MAX_SIGN_SEQUENCE; i++)	/*trying to fix ccpcheck error about buffer being accessed out of bounds for evolution_active_msg_recnos below*/
		{
		evolution_active_msg_recnos[i] = 0;
		}

	if (debug_evolution_active_msgs == 1) { DIAGNOSTIC_LOG(" AFTER CLEAR..."); print_contents_of_active_msg_array(0); }
	}

/*****************************************************************
** int send_to_evolution_appliance_legacyBehavior()
**
** Will send the message, along with all other currently active messages.
** This emulates the behavior of legacy devices that are quite dumb, but Kevin wants it this way.
**
** Here's how this needs to work...
** 	Every time we send stuff to the Omni (for online-check, stop msg, send msg, etc.) we need to take
** 	that opportunity to also send along current message data to it. That's because the Omni can often
** 	lose connection and not know what it really needs to be showing.
** 
** Return: Normally 0.
**
** 2018.04.16	Chris Rider	Creation.
**
*******************************************************************/
/*
int send_to_evolution_appliance_legacyBehavior(struct _hardware *hw_ptr, const int banner_evo_cmd, DBRECORD bann_recno_zx, int sequence_number, char message[], DBRECORD bann_recno_template)
{
int ret = 0;
#ifdef USE_EVOLUTION

// Local declares and inits...
int socket_return;

// File-level inits...
hw_ptr_local = hw_ptr;	//update the file-local variable so other functions in this file can use the data

DIAGNOSTIC_FUNCTION("send_to_evolution_appliance_legacyBehavior");

if(evolution_debug || banner_debug > 1)
	{
	DIAGNOSTIC_LOG_2("***DEBUG*** send_to_evolution_appliance_legacyBehavior(): banner_evo_cmd = %d for bann_recno = "FORMAT_DBRECORD_STR, banner_evo_cmd, bann_recno);
	}

remove_trailing_space(hw_ptr->hardware_device_password);


#endif
return ret;
}
*/

/*****************************************************************
** find_camera_stream(cam_stream, db_bann->dbb_camera_deviceid, sizeof(cam_stream))
** 
** Using a camera's deviceid, get its hardware record, and 
** save its IP address field into cam_stream, along with the stream path.
**
** Restores currency when done.
******************************************************************/
void find_camera_stream(char *cam_stream, int cam_stream_length, char *camera_deviceid)
{
char camWholePath[150] = "";

if(notjustspace(camera_deviceid, DEVICEID_LENGTH))
        {
	char *ptr;
        int key = hard_key;                     /* remember current key */
        DBRECORD hard_cur = db_hard_getcur();   /* remember currency */
        char deviceid[DEVICEID_LENGTH];
	char camIpAddr[IP_LENGTH] = "";
	char camStreamPath[100] = "";

	// Find the camera's hardware record...
        db_hard_select(4);                      /* res id and device id */
        strcpysl(deviceid, camera_deviceid, DEVICEID_LENGTH);
        strcpy(db_hard->res_id, res_id);
        strcpy(db_hard->dhc_deviceid, deviceid);
        if(db_hard_find() > 0
                && !strcmp(db_hard->res_id, res_id)
                && !strcmp(db_hard->dhc_deviceid, deviceid))
                {
		// Grab the camera's network location
		strcpyl(camIpAddr, db_hard->dhc_terminal_server_ip, IP_LENGTH);
		remove_trailing_space(camIpAddr);

		// Figure out the camera stream path
		if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_EVOLUTION_APP].dv_name))
			{
			strcpyl(camStreamPath, "/evolution", sizeof(camStreamPath));
			snprintf(camWholePath, sizeof(camWholePath), "rtsp://%s:%s%s", camIpAddr, MEDIAPORT_CAMERA_RTSP_PORT, camStreamPath);	//use same port as mediaport for now
			}
		/*** TODO!! ***
		else if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_CISCO_RTSP].dv_name))
			{
			strcpyl(camStreamPath, camera_linksys_wvc210_video_url, sizeof(camStreamPath));
			}
		else if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_CISCO_WVC54G].dv_name))
			{
			strcpyl(camStreamPath, camera_linksys_wvc54g_video_url, sizeof(camStreamPath));
			}
		else if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_MEDIAPORT].dv_name))
			{
			strcpyl(camStreamPath, camera_mediaport_video_url, sizeof(camStreamPath));
			snprintf(camWholePath, sizeof(camWholePath), "rtsp://%s:%s%s", [GET IP ADDRESS], MEDIAPORT_CAMERA_RTSP_PORT, camera_mediaport_video_url);
			}
		else if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_MULTICAST_RTSP].dv_name))
			{
			strcpyl(camStreamPath, db_hard->dhc_epage, sizeof(camStreamPath));
			}
		else if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_AXIS_HTTP].dv_name)
			|| !strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_AXIS_RTSP].dv_name))
			{
			strcpyl(camStreamPath, camera_axis_video_url, sizeof(camStreamPath));
			}
		*/
		else if(!strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_LEVELONE_HTTP].dv_name)
			|| !strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_LEVELONE_RTSP].dv_name)
			|| !strcmp(db_hard->dhc_device_type, hc_valid_device[DEVICE_CAMERA_LEVELONE_FCS6020].dv_name))
			{
			strcpyl(camStreamPath, camera_levelone_video_url, sizeof(camStreamPath));
			snprintf(camWholePath, sizeof(camWholePath), "rtsp://%s%s", camIpAddr, camStreamPath);
			}
		else
			{
			//generic grab video URL from epage field??
			DIAGNOSTIC_LOG_1("WARNING! Unhandled camera type. Sending generic epage field value (%s).", db_hard->dhc_epage);
			strcpyl(camStreamPath, db_hard->dhc_epage, sizeof(camStreamPath));
			snprintf(camWholePath, sizeof(camWholePath), camStreamPath);	//use same port as mediaport for now
			}

                }

	db_hard_select(key);		/* restore key */
	db_hard_setcur(hard_cur);	/* restore currency */
        }

remove_trailing_space(camWholePath);
//DIAGNOSTIC_LOG_1("RTSP stream = '%s'", camWholePath);
//strcpy(cam_stream, camWholePath);	//works, but spits out a huge result
mn_snprintf(cam_stream, sizeof(camWholePath), "%s", camWholePath);
}

/*****************************************************************
** int send_to_evolution_appliance_discreteMsg(DBRECORD bann_recno)
**
** Will send a single message to the evolution appliance.
**
** How it works:
** First, we first construct some kind of JSON string, depending on what command we have.
** Then, we take that JSON string (whatever it is), and try to send it to the hardware via HTTP POST request, using a socket.
** The end device then takes care of receiving the request and doing something with it.
**
** return: normally 0.
**
*******************************************************************/
int send_to_evolution_appliance_discreteMsg(struct _hardware *hw_ptr, const int banner_evo_cmd, DBRECORD bann_recno, int sequence_number, char message[], DBRECORD template_recno)
{
int ret = 0;

#ifdef USE_EVOLUTION
int socket_return;
char temp_conversion_buf[10] = "";
char http_req_headers[200] = "";							//initialize here and now, so we can simply do strcat w/out need for initial strcpy (just makes less chance of making mistakes)
char json_bannmsg[EVOLUTION_JSON_MESSAGE_LENGTH] = "";
char http_req_body[EVOLUTION_JSON_MESSAGE_LENGTH] = "";
char post_cmd_buf[sizeof(http_req_body) + sizeof(http_req_headers)] = "";
char *ptr;

DIAGNOSTIC_FUNCTION("send_to_evolution_appliance_discreteMsg");

if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
	{
	//DIAGNOSTIC_LOG_2("***DEBUG*** send_to_evolution_appliance_discreteMsg(): banner_evo_cmd = %d for bann_recno = "FORMAT_DBRECORD_STR, banner_evo_cmd, bann_recno);
	}

remove_trailing_space(hw_ptr->hardware_device_password);

hw_ptr_local = hw_ptr;	//update the file-local variable so other functions in this file can use the data

if(banner_evo_cmd == BANNER_EVOLUTION_CMD_STOP_MESSAGE)
	{
	// STOP a message...
	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG_1("send_to_evolution_appliance_discreteMsg(): Stop message "FORMAT_DBRECORD_STR".", bann_recno);
		}

	//remove_recno_from_active_msg_array(bann_recno, 1, 0);	//remove the message from the active messages array	//deprecated 2017.08.30
	//dump_contents_of_active_msg_array_to_file(hw_ptr);	//make a file of the array				//deprecated 2017.08.30
//	remove_msg_json_from_file(hw_ptr, bann_recno);

	strcpyl(http_req_body, "{", sizeof(http_req_body));

	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));	

	strcatl(http_req_body, ",\"bannerpurpose\":\"stopscrollingmessage\"", sizeof(http_req_body));		//NOTE: This is a misnomer... it actually stops any message, not just scrolling
	
	strcatl(http_req_body, ",\"recno_zx\":", sizeof(http_req_body));
	mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", bann_recno);
	strcatl(http_req_body, temp_conversion_buf, sizeof(http_req_body));
	
	strcatl(http_req_body, "}", sizeof(http_req_body));
	
	}//end STOP_MESSAGE
else if(banner_evo_cmd == BANNER_EVOLUTION_CMD_CLEAR_SIGN)
	{
	// CLEAR the sign...
	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG("send_to_evolution_appliance_discreteMsg(): Clear sign.");
		}

	//clear_active_msg_array();	//clear the entire active messages array		//deprecated 2017.08.30
	//dump_contents_of_active_msg_array_to_file(hw_ptr);	//make a file of the array	//deprecated 2017.08.30
//	delete_msg_json_file(hw_ptr);	//no messages for this sign, so just remove the file

	// begin assembling the JSON of message data that will ship to the device...
	strcpyl(http_req_body, "{", sizeof(http_req_body));

	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));	

	strcatl(http_req_body, ",\"bannerpurpose\":\"clearsign\"", sizeof(http_req_body));

	strcatl(http_req_body, "}", sizeof(http_req_body));

	//EXPERIMENTAL: trying to clear RAM struct
	//memset(hw_ptr->seq_string, 0, MAX_SIGN_SEQUENCE);										//no difference
	//DIAGNOSTIC_LOG_1("******** hw_ptr->board_ptr.stream_record = "FORMAT_DBRECORD_STR, hw_ptr->board_ptr->stream_record);		//always 0
	
                //strcpyl(hw_ptr->board_messages[sequence_number], message, MAX_CHARS_IN_MSG);
		//if(translate_for_evo(hw_ptr, hw_ptr->board_messages[(db_wtc->dwc_sequence[0] - SIGN_BASE)], ptr, sizeof(json_bannmsg)) == TRANSLATE_NOT_SUPPORTED)	// attempt to format our message and clean it up, while writing the final version to our place in memory we stored above
//	memset(hw_ptr->board_messages[sequence_number], 0, MAX_CHARS_IN_MSG);
/*
	int i;
	for(i = 0; i < hw_ptr->max_seq; i++)
                {
                //hw_ptr->board_ptr[i].sound = SOUND_NONE;
                //memset(hw_ptr->board_messages[i], 0, MAX_CHARS_IN_MSG);
                DIAGNOSTIC_LOG_1("BEFORE CLEAR *************  %s  *************", hw_ptr->board_messages[i]);
                }
	for(i = 0; i < hw_ptr->max_seq; i++)
                {
                strcpyl(hw_ptr->board_messages[i], "", MAX_CHARS_IN_MSG);
                }
	for(i = 0; i < hw_ptr->max_seq; i++)
                {
                //hw_ptr->board_ptr[i].sound = SOUND_NONE;
                //memset(hw_ptr->board_messages[i], 0, MAX_CHARS_IN_MSG);
                DIAGNOSTIC_LOG_1("AFTER CLEAR *************  %s  *************", hw_ptr->board_messages[i]);
                }
*/
	}
else if(banner_evo_cmd == BANNER_EVOLUTION_CMD_SEQ_NUMBER)
	{
	// SEQUENCE CHANGED...
	
	//if this is a sequence change resulting from the double-hit (new, then seq of that new msg launch), ignore it
	//if(new_msg_recno_just_sent == hw_ptr->board_ptr[sequence_number].bann_recno) 
	
	int i;
	int first_seq = db_wtc->dwc_sequence[0] - SIGN_BASE;

	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG_3("%s SEQUENCE first_seq=%d '%s'.", EVOLUTION_PRODUCT_NAME, first_seq, db_wtc->dwc_sequence);
		//DIAGNOSTIC_LOG_4("%s SEQUENCE first_seq=%d '%s' '%s'.", EVOLUTION_PRODUCT_NAME, first_seq, db_wtc->dwc_sequence, message);
		}

	strcpyl(http_req_body, "{", sizeof(http_req_body));

	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));	

	strcatl(http_req_body, ",\"bannerpurpose\":\"updateseq\"", sizeof(http_req_body));
	
	strcatl(http_req_body, ",\"seqstring\":", sizeof(http_req_body));
	//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%s\"", db_wtc->dwc_sequence);
	//strcatl(http_req_body, temp_conversion_buf, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, db_wtc->dwc_sequence, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));

	//extract messages out of hw_ptr
	strcatl(http_req_body, ",\"bannermessages\":[", sizeof(http_req_body));
	for(i = 0; i < hw_ptr->max_seq; i++)
                {
		if(!strcmp(hw_ptr->board_messages[i], ""))
			{
			continue;
			}

		//DIAGNOSTIC_LOG_2("TEST!!! msg #%d: \"%s\"", i, hw_ptr->board_messages[i]);
		//DIAGNOSTIC_LOG_1("        recno: "FORMAT_DBRECORD_STR, hw_ptr->board_ptr[i].bann_recno);

		// start a JSON object for this iteration's message
		if(i == 0)
			{
			strcatl(json_bannmsg, "{", sizeof(json_bannmsg));
			}
		else
			{
			strcatl(json_bannmsg, ",{", sizeof(json_bannmsg));
			}

		// sign sequence number... (higher values should show first)
		strcatl(json_bannmsg, "\"signseqnum\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", i);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		// message's ZX banner recno...
		strcatl(json_bannmsg, ",\"recno_zx\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", hw_ptr->board_ptr[i].bann_recno);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
	
		// message text...
		strcatl(json_bannmsg, ",\"msgtext\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		ptr = json_bannmsg + strlen(json_bannmsg);	//get a pointer to the place in memory that we just wrote to (so we can continue where that left off in the function below)
		if(translate_for_evo(hw_ptr, hw_ptr->board_messages[i], ptr, sizeof(json_bannmsg)) == TRANSLATE_NOT_SUPPORTED)	// attempt to format our message and clean it up, while writing the final version to our place in memory we stored above
			{
			DIAGNOSTIC_LOG("ERROR: Could not translate evolution message text. Message not sent to device!");
			return(0);
			}
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		// close the JSON object for this iteration's message
		strcatl(json_bannmsg, "}", sizeof(json_bannmsg));
                }
	strcatl(json_bannmsg, "]", sizeof(json_bannmsg));

	// Append this now-completed message object to the file and the whole-json string
	remove_trailing_space(json_bannmsg);
	strcatl(http_req_body, json_bannmsg, sizeof(http_req_body));
	json_bannmsg[0] = '\0';	//reinitialize

	strcatl(http_req_body, "}", sizeof(http_req_body));
//        DIAGNOSTIC_LOG_1("JSON to be sent to device:\n%s", http_req_body);
	}
else if(banner_evo_cmd == BANNER_EVOLUTION_CMD_NEW_MESSAGE)
	{
	//BannerOptions(bann_recno, DB_ISAM_FIND);
	//BannerOptions(template_recno, DB_ISAM_READ);

	// NEW message...
	// NOTE: depends on ZX currency being set already!
	char alert_status_str[2];
	char tmp_str[3];	//for translating UCHAR typed numbers to integers for easy array lookup
	int tmp_i;		//for translating UCHAR typed numbers to integers for easy array lookup

	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG_1("send_to_evolution_appliance_discreteMsg(): New message "FORMAT_DBRECORD_STR".", bann_recno);
		}
	else if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION))
		{
		DIAGNOSTIC_LOG_1("New msg "FORMAT_DBRECORD_STR"...", bann_recno);
		}

	//insert_recno_to_active_msg_array(bann_recno);	//add the message to the active messages array	//deprecated 2017.08.30
	//dump_contents_of_active_msg_array_to_file(hw_ptr);	//make a file of the array		//deprecated 2017.08.30

	if(hw_ptr->board_messages)
                {
                /* save the new message off for use later */
                strcpyl(hw_ptr->board_messages[sequence_number], message, MAX_CHARS_IN_MSG);
                }

	// set our recno-just-sent flag so subsequent double-hit seq-change won't do anything
	new_msg_recno_just_sent_by_newmsg = bann_recno;

	// begin assembling the JSON of message data that will ship to the device...
	strcpyl(http_req_body, "{", sizeof(http_req_body));

	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));	

	//TODO: some logic to determine multimedia type?
	strcatl(http_req_body, ",\"bannerpurpose\":", sizeof(http_req_body));
	switch(db_bann->dbb_multimedia_type)
		{
		case MULTIMEDIA_VIDEO:
		case MULTIMEDIA_VIDEO_STRETCHED:
		case MULTIMEDIA_VIDEO_ZOOM1:
		case MULTIMEDIA_VIDEO_ZOOM2:
			strcatl(http_req_body, "\"newvideo\"", sizeof(http_req_body));
			break;
		case MULTIMEDIA_WEBPAGE:
		case MULTIMEDIA_WEBMEDIA:
			strcatl(http_req_body, "\"newwebpage\"", sizeof(http_req_body));
			break;
		case MULTIMEDIA_LOCATIONS_DISPLAY:
			strcatl(http_req_body, "\"newlocationsdisplay\"", sizeof(http_req_body));
			break;
		case MULTIMEDIA_GEO_LOCATIONS_MAP:
			strcatl(http_req_body, "\"newgeolocationsmap\"", sizeof(http_req_body));
			break;
		case MULTIMEDIA_NONE:
		case MULTIMEDIA_MESSAGE:
		case MULTIMEDIA_MESSAGE_FULL_SCREEN:
		default:
			//DIAGNOSTIC_LOG_3("show_camera=%c, BB_CHOICE_YES=%c, camera_deviceid='%s'", db_bann->dbb_show_camera, BannerEncodeYesNoChoose(BB_CHOICE_YES), db_bann->dbb_camera_deviceid);
			//DIAGNOSTIC_LOG_2("show_camera==BB_CHOICE_YES=%d, notjustspace(camera_deviceid)=%d", db_bann->dbb_show_camera==BannerEncodeYesNoChoose(BB_CHOICE_YES), notjustspace(db_bann->dbb_camera_deviceid, DEVICEID_LENGTH));
			if (db_bann->dbb_show_camera == BannerEncodeYesNoChoose(BB_CHOICE_YES)
				&& notjustspace(db_bann->dbb_camera_deviceid, DEVICEID_LENGTH))
				{
				strcatl(http_req_body, "\"newcameramessage\"", sizeof(http_req_body));
				}
			else
				{
				strcatl(http_req_body, "\"newscrollingmessage\"", sizeof(http_req_body));
				}
			break;
		}

	//hardware info
	strcatl(http_req_body, ",\"hardware_deviceid\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_deviceid, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));

	strcatl(http_req_body, ",\"hardware_recno\":", sizeof(http_req_body));
	mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", hw_ptr->record_number);
	strcatl(http_req_body, temp_conversion_buf, sizeof(http_req_body));
	
	// assemble the array of banner messages (NOTE: it will be just one, but making an array for possible future scalability)...
	strcatl(http_req_body, ",\"bannermessages\":[", sizeof(http_req_body));

		// generate a JSON object for the message and add to the http_req_body string
		strcatl(json_bannmsg, "{", sizeof(json_bannmsg));

		//sign sequence number... (higher values should show first)
		strcatl(json_bannmsg, "\"signseqnum\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", sequence_number);
//	DIAGNOSTIC_LOG_1("db_wtc->dwc_operation = \"%c\"", db_wtc->dwc_operation);
		//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%s\"", db_wtc->dwc_sequence);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message's ZX dtsec (launch time)...
		strcatl(json_bannmsg, ",\"dbb_rec_dtsec\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, remove_leading_space(db_bann->dbb_rec_dtsec), sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//message's ZX banner recno...
		strcatl(json_bannmsg, ",\"recno_zx\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", bann_recno);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message's template banner recno...
		strcatl(json_bannmsg, ",\"recno_template\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", template_recno);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
	
		//message duration...
		strcatl(json_bannmsg, ",\"dbb_duration\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_duration);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
	
		//alert status (message type)...
		strcatl(json_bannmsg, ",\"msgtype\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		tmp_str[0] = db_bann->dbb_alert_status;
		tmp_str[1] = 0;
		tmp_i = atoi(tmp_str);
		strcatl(json_bannmsg, bb_alert_status[tmp_i], sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));	
	
		//actual message text...
		strcatl(json_bannmsg, ",\"msgtext\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		ptr = json_bannmsg + strlen(json_bannmsg);	//get a pointer to the place in memory that we just wrote to (so we can continue where that left off in the function below)
		if(translate_for_evo(hw_ptr, hw_ptr->board_messages[(db_wtc->dwc_sequence[0] - SIGN_BASE)], ptr, sizeof(json_bannmsg)) == TRANSLATE_NOT_SUPPORTED)	// attempt to format our message and clean it up, while writing the final version to our place in memory we stored above
			{
			DIAGNOSTIC_LOG("ERROR: Could not translate evolution message text. Message not sent to device!");
			return(0);
			}
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//message details text...
		strcatl(json_bannmsg, ",\"msgdetails\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "", sizeof(json_bannmsg));	//TODO: hardcoded empty for now - future capability to be developed later
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//audio group list-DB recnos that this message has defined...
		/*
		strcatl(json_bannmsg, ",\"mo_multi_audio_records\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, db_bann_message_options->mo_multi_audio_records, sizeof(json_bannmsg));	//TODO: hardcoded empty for now - future capability to be developed later		//db_bann_message_options->mo_multi_audio_records
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		*/

		//audio group name(s) that include this device --as JSON array in case there are multiple...
		//(this data is stored in SIGN database)
		//(note: yes, this is technically hardware-related data and could be in ext-mgr tftp/config file, but this way ensures it's always up-to-date during runtime)
		strcatl(json_bannmsg, ",\"dsi_audio_group_name\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "[", sizeof(json_bannmsg));
			//select for all records containing this hardware deviceid and audiogroup entries...
			db_signs_select(0);
			strcpy(db_sign->res_id, res_id);
			strcpysl(db_sign->dsi_deviceid, hw_ptr->hardware_deviceid, DEVICEID_LENGTH);
			strcpyl(db_sign->dsi_sign_group_name, "", AUDIOGROUP_LENGTH);
			int db_signs_i = 0;
			int db_signs_nextptr = db_signs_find();
			//loop through all results and print them to the JSON array...
			while(db_signs_nextptr > 0
				&& !strcmp(db_sign->res_id, res_id)
				&& !strcmp(db_sign->dsi_deviceid, hw_ptr->hardware_deviceid)
				&& notjustspace(db_sign->dsi_audio_group_name, sizeof(db_sign->dsi_audio_group_name)))
				{
				if(db_signs_i > 0) strcatl(json_bannmsg, ",", sizeof(json_bannmsg));
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				strcatl(json_bannmsg, db_sign->dsi_audio_group_name, sizeof(json_bannmsg));
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				db_signs_i++;
				db_signs_nextptr = db_signs_next();
				}
		strcatl(json_bannmsg, "]", sizeof(json_bannmsg));

		//audio group name(s) that this message is supposed to go to --as JSON array in case there are multiple...
		//(note: we make the db_banne DB field name, which is normally dbb_audio_group, plural here)
		strcatl(json_bannmsg, ",\"dbb_audio_groups\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "[", sizeof(json_bannmsg));
		//DIAGNOSTIC_LOG_1("Assembling JSON array of audio groups for device %s", hw_ptr->hardware_deviceid);
		if(!strcmp(db_bann->dbb_audio_group, bba_multiple))
			{
			//multiple audio groups defined in the message template, so need to list all that this device belongs to
			BannerOptions(template_recno, DB_ISAM_READ);
			char *ptr_mo;
			int i_ag = 0;
			for(ptr_mo = db_bann_message_options->mo_multi_audio_records; ptr_mo; )
				{
				char *ptr_comma;
				DBRECORD multi_recno;
				ptr_comma = strchr(ptr_mo, ',');
				if(ptr_comma) *ptr_comma = 0;
				multi_recno = AlphaToRecordNumber(ptr_mo);
				if(multi_recno > 0 && db_list_setcur(multi_recno) > 0 && db_list->dli_type == LIST_INTERCOM_AUDIO_GROUP)
					{
					if(i_ag > 0) strcatl(json_bannmsg, ",", sizeof(json_bannmsg));

					/* print audio group recno derived from message options file... */
					//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", multi_recno);
					//strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
					
					/* print audio group name derived from database decoding of message options file derived recnos... */
					strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
					strcatl(json_bannmsg, db_list->dli_name, sizeof(json_bannmsg));
					strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

					i_ag++;
					}
				if(ptr_comma) {ptr_mo = ptr_comma + 1;} else {ptr_mo = NULL;}
				}
			}
		else if(!strcmp(db_bann->dbb_audio_group, bbs_audio_group_choose))
			{
			//multiple audio groups chosen during message launch, so need to list all that this device belongs to
			/*
			BannerOptions(bann_recno, DB_ISAM_READ);
			char *ptr_mo;
			int i_ag = 0;
			for(ptr_mo = db_bann_message_options->mo_multi_audio_records; ptr_mo; )
				{
				char *ptr_comma;
				DBRECORD multi_recno;
				ptr_comma = strchr(ptr_mo, ',');
				if(ptr_comma) *ptr_comma = 0;
				multi_recno = AlphaToRecordNumber(ptr_mo);
				if(multi_recno > 0 && db_list_setcur(multi_recno) > 0 && db_list->dli_type == LIST_INTERCOM_AUDIO_GROUP)
					{
					if(i_ag > 0) strcatl(json_bannmsg, ",", sizeof(json_bannmsg));

					// print audio group recno derived from message options file... 
					//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", multi_recno);
					//strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
					
					// print audio group name derived from database decoding of message options file derived recnos... 
					strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
					strcatl(json_bannmsg, db_list->dli_name, sizeof(json_bannmsg));		//DESTIN_LENGTH
					strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

					i_ag++;
					}
				if(ptr_comma) {ptr_mo = ptr_comma + 1;} else {ptr_mo = NULL;}
				}
			*/
			
			/*
			char audio[DESTIN_LENGTH];
			strcpy(audio, LookupStreamDecodeString(launch_decoding, "#audiogroup"));
			if(strlen(audio) == 0) strcpy(audio, bbs_audio_group_choose);
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, audio, sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			*/

			// NOT WORKING YET... no reason it shouldn't work, but it's Jerry's ass-backward code, so who the hell knows... work on it later 
			int i_ag = 0;
			DBRECORD list_record;
			list_record = FindMultiAudioSignStreamNumberData(bann_recno);	//dev-note: this is same as stream number in banner record --verified
			if(list_record > 0)
				{
				do
					{
					if(db_list_setcur(list_record) > 0)
						{
						if(i_ag > 0) strcatl(json_bannmsg, ",", sizeof(json_bannmsg));
						strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
						strcatl(json_bannmsg, db_list->dli_name, sizeof(json_bannmsg));
						strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
						i_ag++;
						}
					list_record = NextMultiAudioSignStreamNumberData(bann_recno);
					}
				while(list_record > 0);
				}
			else 
				{
				//unable to get chosen audio group recnos
				}
			}
		else
			{
			if(notjustspace(db_bann->dbb_audio_group, sizeof(db_bann->dbb_audio_group)))
				{
				//just one defined, so save disk IO and don't bother reading signs database since we already have it in currency
				//DIAGNOSTIC_LOG_1("  Adding %s", db_bann->dbb_audio_group);
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				strcatl(json_bannmsg, db_bann->dbb_audio_group, sizeof(json_bannmsg));
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				}
			else
				{
				//NO audio group defined in the message
				}
			}
		strcatl(json_bannmsg, "]", sizeof(json_bannmsg));
		
		//message playtime duration
		strcatl(json_bannmsg, ",\"dbb_playtime_duration\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_playtime_duration);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//flasher duration
		strcatl(json_bannmsg, ",\"dbb_flasher_duration\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_flasher_duration);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_light_signal
		strcatl(json_bannmsg, ",\"dbb_light_signal\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_light_signal)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_light_signal);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//light duration
		strcatl(json_bannmsg, ",\"dbb_light_duration\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_light_duration);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_audio_tts_gain
		strcatl(json_bannmsg, ",\"dbb_audio_tts_gain\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_audio_tts_gain);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_flash_new_message
		strcatl(json_bannmsg, ",\"dbb_flash_new_message\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_flash_new_message)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_flash_new_message);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//UCHAR dbb_visible_time;
		strcatl(json_bannmsg, ",\"dbb_visible_time\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_visible_time)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_visible_time);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//UCHAR dbb_visible_frequency;
		strcatl(json_bannmsg, ",\"dbb_visible_frequency\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_visible_frequency)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_visible_frequency);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}
			
		//UCHAR dbb_visible_duration;
		strcatl(json_bannmsg, ",\"dbb_visible_duration\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_visible_duration)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_visible_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//UCHAR dbb_record_voice_at_launch_selection; //NOT SURE WHY WE NEED THIS YET.. Maybe not
		strcatl(json_bannmsg, ",\"dbb_record_voice_at_launch_selection\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_record_voice_at_launch_selection);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_record_voice_at_launch; //(true if a recorded voice at launch was made)
		strcatl(json_bannmsg, ",\"dbb_record_voice_at_launch\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_record_voice_at_launch)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_record_voice_at_launch);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//UCHAR dbb_audio_recorded_gain
		strcatl(json_bannmsg, ",\"dbb_audio_recorded_gain\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_audio_recorded_gain);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_pa_delivery_mode
		strcatl(json_bannmsg, ",\"dbb_pa_delivery_mode\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_pa_delivery_mode)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_pa_delivery_mode);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//UCHAR dbb_audio_repeat
		strcatl(json_bannmsg, ",\"dbb_audio_repeat\":", sizeof(json_bannmsg));
		if (!db_bann->dbb_audio_repeat)
			{
			strcatl(json_bannmsg, "\"\"", sizeof(json_bannmsg));
			}
		else
			{
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_audio_repeat);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			}

		//UCHAR dbb_speed
		strcatl(json_bannmsg, ",\"dbb_speed\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_speed);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message priority level...
		strcatl(json_bannmsg, ",\"dbb_priority\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_priority);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message expire priority...
		strcatl(json_bannmsg, ",\"dbb_expire_priority\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_expire_priority);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message priority duration...
		strcatl(json_bannmsg, ",\"dbb_priority_duration\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_priority_duration);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message at launch priority level (if user specified priority at launch time - overrides defined value)...
		strcatl(json_bannmsg, ",\"dbb_page_priority_at_launch\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_page_priority_at_launch);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_multimedia_type
		strcatl(json_bannmsg, ",\"multimediatype\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, multimedia_type_str[db_bann->dbb_multimedia_type], sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));	

		//UCHAR dbb_multimedia_audio_gain
		strcatl(json_bannmsg, ",\"dbb_multimedia_audio_gain\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_multimedia_audio_gain);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//UCHAR dbb_replay_media...
		//NOTE: probably incorrect way.. just need to refactor this to integer and inore 'C' ??
		//strcatl(json_bannmsg, ",\"dbb_replay_media\":", sizeof(json_bannmsg));
		//strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		//tmp_str[0] = db_bann->dbb_replay_media;
		//tmp_str[1] = 0;
		//tmp_i = atoi(tmp_str);
		//strcatl(json_bannmsg, bb_alert_status[tmp_i], sizeof(json_bannmsg));
		//strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));	
	
		//UCHAR dbb_multimedia_type_waiting_to_import  (see MULTIMEDIA_IMPORT_TYPE_*)

		//webpage URL... (need to read in from file pointed to by db_voic->dvc_disk_path)
		strcatl(json_bannmsg, ",\"webpageurl\":", sizeof(json_bannmsg));
		if(db_bann->dbb_multimedia_type == MULTIMEDIA_WEBPAGE
			|| db_bann->dbb_multimedia_type == MULTIMEDIA_WEBMEDIA)
			{
			char bann_dtsec[DTSEC_LENGTH] = "";
			strcpyl(bann_dtsec, db_bann->dbb_rec_dtsec, DTSEC_LENGTH);
			if(BannerFindMultimediaFile(template_recno, "") > 0)
				{
				char web_page[MAX_CHARS] = "";
				SystemReadDataFromFile(web_page, sizeof(web_page), db_voic->dvc_disk_path);
				remove_trailing_space(web_page);
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				strcatl(json_bannmsg, web_page, sizeof(json_bannmsg));
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				}
			else
				{
				strcatl(json_bannmsg, "\"NULL\"", sizeof(json_bannmsg));	//we couldn't pull the URL for the message template (perhaps user didn't define or there was some error)
				}
			}
		else if (db_bann->dbb_multimedia_type == MULTIMEDIA_VIDEO) 
			{
			if(BannerFindMultimediaFile(template_recno, "") > 0)
				{
				char multimedia_name[200] = "";
				char *ptr;
				ptr = strrchr(db_voic->dvc_disk_path, '/');
				ptr++;
				strcpyl(multimedia_name, ptr, sizeof(multimedia_name));
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				strcatl(json_bannmsg, multimedia_name, sizeof(json_bannmsg));
				strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
				}
			else 
				{
				strcatl(json_bannmsg, "\"NULL\"", sizeof(json_bannmsg));
				}
			}
		else if (db_bann->dbb_show_camera == BannerEncodeYesNoChoose(BB_CHOICE_YES)
			&& notjustspace(db_bann->dbb_camera_deviceid, DEVICEID_LENGTH))
			{
			//need to figure out the IP and path of the RTSP server for the Omni to stream video from
			//char cam_stream[MAX_CHARS] = "";
			//find_camera_stream(cam_stream, sizeof(cam_stream), db_bann->dbb_camera_deviceid);
			//strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			//strcatl(json_bannmsg, cam_stream, sizeof(json_bannmsg));
			//strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

			char *ptr;
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			ptr = json_bannmsg + strlen(json_bannmsg);	//get a pointer to the place in memory that we last wrote to (so we can continue where that left off in the function below)
			find_camera_stream(ptr, MAX_CHARS, db_bann->dbb_camera_deviceid);
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			}
		else
			{
			strcatl(json_bannmsg, "\"FALSE\"", sizeof(json_bannmsg));	//there simply is no URL involved with this type of message (this is not an error situation)
			}

		//launcher's PIN
		strcatl(json_bannmsg, ",\"dbb_launch_pin\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, db_bann->dbb_launch_pin, sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//sender gender
		strcatl(json_bannmsg, ",\"dss_gender\":", sizeof(json_bannmsg));
		DBRECORD staff_cur_recno = db_staff_getcur();
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		if(db_staff_pin_valid(db_bann->dbb_launch_pin))
			{
			strcatl(json_bannmsg, db_staf->dss_gender, sizeof(json_bannmsg));
			}
		else
			{
			strcatl(json_bannmsg, "", sizeof(json_bannmsg));
			}
		db_staff_setcur(staff_cur_recno);
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		/*** DEV-NOTE: Any additional fields for the message get added ^^ABOVE THIS LINE^^ ***/
		/*** NOTE: below, any datatype may be substituted for the temp_conversion_buf assignment ***/
		/* TEMPLATE FOR: int
		strcatl(json_bannmsg, ",\"dbb_?\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_?);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
		 */
		/* TEMPLATE FOR: long
		strcatl(json_bannmsg, ",\"dbb_?\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_?);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
		 */
		/* TEMPLATE FOR: uchar
		strcatl(json_bannmsg, ",\"dbb_?\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%c", db_bann->dbb_?);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
		 */
		/* TEMPLATE FOR: string (as a variable)    ---WARNING--- if you use mn_snprintf or sprintf, things get weird and JSON syntax is ruined
		strcatl(json_bannmsg, ",\"NAME\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, VARIABLE, sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		 */
		/* TEMPLATE FOR: string (as a hardcoded value)
		strcatl(json_bannmsg, ",\"NAME\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"VALUE\"", sizeof(json_bannmsg));
		 */

		strcatl(json_bannmsg, "}", sizeof(json_bannmsg));
	
		// Append this now-completed message object to the file and the whole-json string
		remove_trailing_space(json_bannmsg);
//		append_msg_json_to_file(hw_ptr, json_bannmsg);		//add this message (as a JSON object) to the file for tracking
		strcatl(http_req_body, json_bannmsg, sizeof(http_req_body));

		json_bannmsg[0] = '\0';	//reinitialize

	//close-up shop on the array of banner messages generated in the for loop above
	strcatl(http_req_body, "]", sizeof(http_req_body));

	//end the JSON string
	strcatl(http_req_body, "}", sizeof(http_req_body));

	}//end NEW_MESSAGE

// Construct the HTTP POST request...
if(notjustspace(http_req_body, sizeof(http_req_body)))
	{
	remove_trailing_space(http_req_body);		//probably not needed, but just to be safe that we get a valid Content-Length value below

	//put the headers together
	strcatl(http_req_headers, "POST / HTTP/1.1\r\n", sizeof(http_req_headers));
	strcatl(http_req_headers, "User-Agent: MessageNet Evolution Banner Socket\r\n", sizeof(http_req_headers));
	strcatl(http_req_headers, "Content-Type: application/json\r\n", sizeof(http_req_headers));
	sprintf(temp_conversion_buf, "Content-Length: "FORMAT_STRLEN_STR"\r\n", strlen(http_req_body));
	strcatl(http_req_headers, temp_conversion_buf, sizeof(http_req_headers));
	strcatl(http_req_headers, "\r\n", sizeof(http_req_headers));

	remove_trailing_space(http_req_headers);	//probably not needed, but just to be safe

	//put all the request into the command buffer
	strcatl(post_cmd_buf, http_req_headers, sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, http_req_body, sizeof(post_cmd_buf));
	}
else
	{
	DIAGNOSTIC_LOG("WARNING: No request was constructed, so nothing to send.");
	ret = -1;
	goto done2;
	}

/****** signal light method
// Handle sending the PUT request
socket = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, FALSE);

if(socket < 0)                                                  // if the socket connection was not successful
        {
        // took too long to send data to port so lets disable it
        DIAGNOSTIC_LOG_1("send_to_signallight_device(): SystemSocketConnect() report error %s", HardwareReportPortError(hw_ptr));

        HardwareReportSystemAlerts(hw_ptr);
        HardwareDisablePort(hw_ptr, TRUE, TRUE);
        HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);
        }
else
        {
        HardwareSystemAlertClear(hw_ptr);
        HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);

        if(SystemSocketWrite(socket, light_command_buffer, sizeof(light_command_buffer)) > 0)
                {
                if(SystemSocketReadTimeout(socket, light_command_buffer, sizeof(light_command_buffer), 5) > 0)
                        {
                        ret = TRUE;
                        }
                }

        SystemSocketClose(socket);
        }

mn_delay(100);

return(ret);
******/

// Try to send the request to the device...
hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, FALSE);
/* dev-note: if using nonblock, then timeout (4th arg) should be handle to check connect status...
hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, SYSTEM_SOCKET_OPTION_NONBLOCK);
*/
if(hw_ptr->fd < 0)
	{
	/* ORIGINAL CODE: 2017.12.05
       	// took too long to send data to port so lets disable it and abort
        DIAGNOSTIC_LOG_1("SystemSocketConnect() report error %s", HardwareReportPortError(hw_ptr));

	HardwareReportSystemAlerts(hw_ptr);
        HardwareDisablePort(hw_ptr, TRUE, TRUE);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);

	ret = -1;
        goto done2;
	*/

	//try again (created 2017.12.05 to try to overcome stop attempts not succeeding with bad connections)
	int retrySocketConnectMax = 5;
	do 
		{
		mn_delay(1*1000);
        	if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG)) DIAGNOSTIC_LOG_2("SystemSocketConnect() report error %s. Trying again (%d retries remaining).", HardwareReportPortError(hw_ptr), retrySocketConnectMax);
		hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, SYSTEM_SOCKET_OPTION_PRINT_ERRORS);
        	if(hw_ptr->fd >= 0) 
			{
			if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG)) DIAGNOSTIC_LOG("SystemSocketConnect() Retry succeeded connecting to client!");

			// our device is alive, so set status and allow to continue sending
			HardwareSystemAlertClear(hw_ptr);
			HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);
			}
		retrySocketConnectMax--;
		}
	while(hw_ptr->fd < 0 && retrySocketConnectMax > 0);

	if(hw_ptr->fd < 0)
		{
		evolution_clear_ip(hw_ptr);
        	if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION)) DIAGNOSTIC_LOG_1("SystemSocketConnect() report error %s. Giving up!", HardwareReportPortError(hw_ptr));
		HardwareReportSystemAlerts(hw_ptr);
        	HardwareDisablePort(hw_ptr, TRUE, TRUE);
		HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);
		ret = -1;
        	goto done2;
		}
        }
else
	{
	// our device is alive, so set status and allow to continue sending
	HardwareSystemAlertClear(hw_ptr);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);
	}

strcatl(post_cmd_buf, "\r\n", sizeof(post_cmd_buf));				//just to be safe that we send a new-line character

//SystemSocketWrite(hw_ptr->fd, post_cmd_buf, strlen(post_cmd_buf));		//actually send the command to hardware!	//NOTE: This may be able to return number of bytes written to the socket?! Useful?
if( SystemSocketWrite(hw_ptr->fd, post_cmd_buf, strlen(post_cmd_buf)) > 0 )
	{
	socket_return = SystemSocketReadTimeout(hw_ptr->fd, post_cmd_buf, sizeof(post_cmd_buf), 5);	//wait for a response and save back to post_cmd_buf if there is one within the timeout period
	if( socket_return > 0 )
		{
		SystemTruncateReturnBuffer(post_cmd_buf, sizeof(post_cmd_buf), socket_return);
		if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
			{
			DIAGNOSTIC_LOG_1("send_to_evolution_appliance_discreteMsg: SystemSocketReadTimeout() Omni response: %s", post_cmd_buf);
			}
		else if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION))
			{
			DIAGNOSTIC_LOG_1("Response: %s", post_cmd_buf);	//print what the Omni responded with
			}
		}
	else
		{
		if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION)) DIAGNOSTIC_LOG_1("send_to_evolution_appliance_discreteMsg: WARNING, SystemSocketReadTimeout() Omni NO response (socket_return = %d)", socket_return);

		int retrySocketReadMax = 5;
		do
			{
			mn_delay(1*1000);
			if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG)) DIAGNOSTIC_LOG_1("send_to_evolution_appliance_discreteMsg: SystemSocketReadTimeout() Omni read trying again (%d retries remaining).", retrySocketReadMax);
			socket_return = SystemSocketReadTimeout(hw_ptr->fd, post_cmd_buf, sizeof(post_cmd_buf), 5);	//wait for a response and save back to post_cmd_buf if there is one within the timeout period
			if(socket_return > 0) 
				{
				if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG)) DIAGNOSTIC_LOG("send_to_evolution_appliance_discreteMsg: SystemSocketReadTimeout() Omni read retry succeeded!");

				//omni responded, so handle that
				SystemTruncateReturnBuffer(post_cmd_buf, sizeof(post_cmd_buf), socket_return);
				if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
					{
					DIAGNOSTIC_LOG_1("send_to_evolution_appliance_discreteMsg: SystemSocketReadTimeout() Omni response: %s", post_cmd_buf);
					}
				else if(DiagnosticCheck(DIAGNOSTIC_EVOLUTION))
					{
					DIAGNOSTIC_LOG_1("Response: %s", post_cmd_buf);
					}
				}
			retrySocketReadMax--;
			}
		while(socket_return <= 0 && retrySocketReadMax > 0);
		}
	}
else
	{
	DIAGNOSTIC_LOG("send_to_evolution_appliance_discreteMsg: ERROR with SystemSocketWrite call (no bytes written?)");
	}

//if(message_type & BANNER_IPSPEAKER_NO_RETURN_STATUS)
//	{
//	// read status and close the port in board_verify_read() 
//      goto done2;
//	}

SystemSocketClose(hw_ptr->fd);

done2:
DIAGNOSTIC_FUNCTION_EXIT();
#endif

return ret;
}

/*****************************************************************
** int send_to_evolution_appliance(struct _hardware *hw_ptr, char message[], int sequence_number, int message_type)
**
** Will interpret the generic message data in message for
** the Evolution appliance and send via POST HTTP request.
**
** return: normally 0 - on for BANNER_IPSPEAKER_CHECKING_CONNECT is 0 or 1.
**
*******************************************************************/
int send_to_evolution_appliance(struct _hardware *hw_ptr, char message[], int sequence_number, int message_type, DBRECORD stream_number)
{
#ifdef USE_EVOLUTION
int ret = 0;
int socket_return;

char *ptr;

char temp_conversion_buf[10];
char http_req_headers[200] = "";	//initialize here and now, so we can simply do strcat w/out need for initial strcpy (just makes less chance of making mistakes)
char http_req_body[MAX_CHARS] = "";
char json_bannmsg[MAX_CHARS] = "";

char post_cmd_buf[MAX_CHARS] = "";


DIAGNOSTIC_FUNCTION("send_to_evolution_appliance");

remove_trailing_space(hw_ptr->hardware_device_password);

pause_first = FALSE;

if(message_type == BANNER_IPSPEAKER_CHECKING_CONNECT)
	{
	/* NEW...
	strcpyl(json_whole, "{", sizeof(json_whole));

	strcatl(json_whole, "\"password\":", sizeof(json_whole));
	strcatl(json_whole, "\"", sizeof(json_whole));
	strcatl(json_whole, hw_ptr->hardware_device_password, sizeof(json_whole));
	strcatl(json_whole, "\"", sizeof(json_whole));

	strcatl(json_whole, "}", sizeof(json_whole));

	mn_snprintf(post_command, sizeof(post_command), "/usr/bin/curl --header 'Content-Type: application/json' -X POST --data '%s' http://%s:%s", json_whole, hw_ptr->term_ip, ip_port);

	cmd_return = system(post_command);

	if(cmd_return > -1)
		{
		DIAGNOSTIC_LOG_1("send_to_evolution_appliance() check connection returned '%d'", cmd_return);

		//hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, 0);
		//if(hw_ptr->fd > -1)
		//	{
			HardwareSystemAlertClear(hw_ptr);
			HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);
		//	}

		ret = TRUE;
		}
	else
		{
		DIAGNOSTIC_LOG_1("send_to_evolution_appliance() check connection nothing '%d'", cmd_return);

		//try old way (maybe curl didn't work as expected?)
		hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, 0);
		if(hw_ptr->fd < 0)
			{
	       		// took too long to send data to port so lets disable it
	        	DIAGNOSTIC_LOG_1("SystemSocketConnect() report error %s", HardwareReportPortError(hw_ptr));

			HardwareReportSystemAlerts(hw_ptr);
		        HardwareDisablePort(hw_ptr, TRUE, TRUE);
			HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);
			}
		else
			{
			HardwareSystemAlertClear(hw_ptr);
			HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);
			
			ret = TRUE;
			}
		}
	*/

	// OLD method
	strcatl(post_cmd_buf, "GET ", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, "/ping?password=", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, hw_ptr->hardware_device_password, sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, " HTTP/1.1\r\n", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, "\r\n", sizeof(post_cmd_buf));

	hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, 0);
	if(hw_ptr->fd < 0)
		{
       		// took too long to send data to port so lets disable it
        	DIAGNOSTIC_LOG_1("SystemSocketConnect() report error %s", HardwareReportPortError(hw_ptr));

		HardwareReportSystemAlerts(hw_ptr);
	        HardwareDisablePort(hw_ptr, TRUE, TRUE);
		HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);
		}
	else
		{
		HardwareSystemAlertClear(hw_ptr);
		HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);

		if(SystemSocketWrite(hw_ptr->fd, post_cmd_buf, strlen(post_cmd_buf)) > 0)
			{
			socket_return = SystemSocketReadTimeout(hw_ptr->fd, post_cmd_buf, sizeof(post_cmd_buf), 5);
			if(socket_return > 0)
				{
				SystemTruncateReturnBuffer(post_cmd_buf, sizeof(post_cmd_buf), socket_return);

				if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
					{
					DIAGNOSTIC_LOG_1("send_to_evolution_appliance() check connection returned '%s'", post_cmd_buf);
					}

				ret = TRUE;
				}
			else
				{
				if(evolution_debug || banner_debug > 1 || (socket_return <= 0) || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
					{
					DIAGNOSTIC_LOG_1("send_to_evolution_appliance() check connection nothing '%d'", socket_return);
					}
				}
			}

		SystemSocketClose(hw_ptr->fd);
		}
	

	return(ret);
	}

//the first command coming in is to populate the message slot in banner, with the message (subsequent commands coming in will then actually send it(them) from the slot(s)
if(message_type & BANNER_NEW_MESSAGE)
	{
	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG_3("%s received msg for slot=%d '%s'.", EVOLUTION_PRODUCT_NAME, sequence_number, message);
		}

	if(hw_ptr->board_messages)
                {
                /* save the new message off for use later */
                strcpyl(hw_ptr->board_messages[sequence_number], message, MAX_CHARS_IN_MSG);

                //hw_ptr->board_ptr[sequence_number].fg_color = evolution_get_color(message);                     /* take first color */
                //hw_ptr->board_ptr[sequence_number].bg_color = evolution_get_bgcolor(message);                   /* take first color */
                }

	// no data to send at this time just fill message slot 
	return(0);
	}

// if this is a new message then put it in the correct slot as we need to remember all messages 
if(message_type & BANNER_SEQUENCE_NUMBER)
	{
	int i;
	int first_seq = db_wtc->dwc_sequence[0] - SIGN_BASE;

	if(evolution_debug || banner_debug > 1 || DiagnosticCheck(DIAGNOSTIC_EVOLUTION_DEBUG))
		{
		DIAGNOSTIC_LOG_3("%s SEQUENCE first_seq=%d '%s'.", EVOLUTION_PRODUCT_NAME, first_seq, db_wtc->dwc_sequence);
		}

	if(hw_ptr->board_messages)
		{
		int i;
		int msg_seq;
		int messages_are_only_date_and_time = TRUE;
		int hold_mode = FALSE;

		char hold_str[10];

		char alert_status_str[2];
		int j;
		int last_j = -1;
		DBRECORD curr;

		//for translating UCHAR typed numbers to integers for eacy array lookup
		char tmp_str[3];
		int tmp_i;

		mn_snprintf(hold_str, sizeof(hold_str), "%c%c%s", SIGN_COMMAND, SIGN_MODE, SIGN_MODE_HOLD);

		for(i = 0; i < hw_ptr->max_seq && db_wtc->dwc_sequence[i]; i++)
			{
			char *ptr_string;

			msg_seq = (db_wtc->dwc_sequence[i] - SIGN_BASE);
			ptr_string = hw_ptr->board_messages[msg_seq];

			if(strstr(ptr_string, hold_str))
				{
				hold_mode = TRUE;
				}

			while(*ptr_string)
				{
				if(*ptr_string == BB_ESC_CHAR)
					{
					// skip two character escape codes 
					ptr_string = pointer_increment(ptr_string);
					if(*ptr_string == SIGN_TIMEEMBED || *ptr_string == SIGN_DATEEMBED)
						{
						ptr_string = pointer_increment(ptr_string);
						}
					else
						{
						ptr_string = pointer_increment(ptr_string);
						ptr_string = pointer_increment(ptr_string);
						}
					}
				else if(*ptr_string == ' ')
					{
					ptr_string = pointer_increment(ptr_string);
					}
				else
					{
					messages_are_only_date_and_time = FALSE;
					i = MAX_SIGN_SEQUENCE;				// stop 'i' loop 
					break;						// stop while loop 
					}
				}
			}

		// begin assembling the HTTP-POSTed JSON...
		strcpyl(http_req_body, "{", sizeof(http_req_body));

		strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
		strcatl(http_req_body, "\"", sizeof(http_req_body));
		strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
		strcatl(http_req_body, "\"", sizeof(http_req_body));	

		strcatl(http_req_body, ",\"bannerpurpose\":\"showmessage\"", sizeof(http_req_body));

		if(messages_are_only_date_and_time)
			{
			// date does not show anything, and if all is showing is time then loop it so no blink 
			strcatl(http_req_body, ",\"loops\":1", sizeof(http_req_body));
			}
		else
			{
			strcatl(http_req_body, ",\"loops\":0", sizeof(http_req_body));
			}

		if(hold_mode)
			{
			// use TWO line mode in message HOLD mode 
			strcatl(http_req_body, ",\"autosplit\":1", sizeof(http_req_body));
			strcatl(http_req_body, ",\"splitting\":2", sizeof(http_req_body));
			}

		// assemble the array of banner messages...
		//strcatl(post_cmd_buf, "&msgtext=", sizeof(post_cmd_buf));
		strcatl(http_req_body, ",\"bannermessages\":[", sizeof(http_req_body));

		// for each banner message, generate a JSON object for it and add to the http_req_body string
		for(i = 0; i < hw_ptr->max_seq && db_wtc->dwc_sequence[i]; i++)
			{
			msg_seq = (db_wtc->dwc_sequence[i] - SIGN_BASE);

			if(i == 0 && evolution_debug)
				{
				DIAGNOSTIC_LOG_3("Constructing JSON from message in slot #%d (banner msg_seq %d, banner stream recno "FORMAT_DBRECORD_STR")", i, msg_seq, stream_number);
				}
			if(i > 0 && evolution_debug)
				{
				DIAGNOSTIC_LOG_3("Concatenating JSON from message in slot #%d (banner msg_seq %d, banner stream recno "FORMAT_DBRECORD_STR")", i, msg_seq, stream_number);
				}

			if(i > 0)
				{
				//add a JSON delineation for the next message object in the JSON array
				strcatl(json_bannmsg, ",", sizeof(json_bannmsg));
				}

			// Begin assembling the discrete banner message JSON-object with all of its attributes...
			strcatl(json_bannmsg, "{", sizeof(json_bannmsg));

			//sign sequence number...
			strcatl(json_bannmsg, "\"signseqnum\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", i);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//banner sequence number...
			strcatl(json_bannmsg, ",\"bannseqnum\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", msg_seq);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//wtc record number...
			//strcatl(json_bannmsg, ",\"wtcrecno\":", sizeof(json_bannmsg));
			//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", db_wtc_getcur());
			//strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//board message...
			strcatl(json_bannmsg, ",\"boardmsgs\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, hw_ptr->board_messages[msg_seq], sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));	

			//board message stream recno...
			//strcatl(json_bannmsg, ",\"streamrecno\":", sizeof(json_bannmsg));
			//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", hw_ptr->board_ptr[msg_seq].stream_record);
			//strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

//DIAGNOSTIC_LOG_1("STREAM NUMBER = "FORMAT_DBRECORD_STR, stream_number); //debug
//DIAGNOSTIC_LOG_1("FIFO PTR = "FORMAT_DBRECORD_STR, hw_ptr->fifo_ptr.stream_record);

			//alert status (message type)...
			strcatl(json_bannmsg, ",\"msgtype\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			if(db_bann_setcur(db_wtc->dwc_stream_number) > 0)
				{
				tmp_str[0] = db_bann->dbb_alert_status;
				tmp_str[1] = 0;
				tmp_i = atoi(tmp_str);
				strcatl(json_bannmsg, bb_alert_status[tmp_i], sizeof(json_bannmsg));
				}
			else
				{
				DIAGNOSTIC_LOG_1("WARNING: Could not determine message alert_status. Substituting with %d.", bb_alert_status[BB_ALERT_STATUS_MESSAGE]);
				strcatl(json_bannmsg, bb_alert_status[BB_ALERT_STATUS_MESSAGE], sizeof(json_bannmsg));
				}
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));	

			//message's ZX banner recno...
			strcatl(json_bannmsg, ",\"recno_zx\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", db_bann_getcur());
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//message's ZX dtsec...
			strcatl(json_bannmsg, ",\"dbb_rec_dtsec\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, remove_leading_space(db_bann->dbb_rec_dtsec), sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

			//actual message text...
			strcatl(json_bannmsg, ",\"msgtext\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			ptr = json_bannmsg + strlen(json_bannmsg);	//get a pointer to the place in memory that we just wrote to (so we can continue where that left off in the function below)
			if(translate_for_evo(hw_ptr, hw_ptr->board_messages[msg_seq], ptr, sizeof(json_bannmsg)) == TRANSLATE_NOT_SUPPORTED)	/* attempt to format our message and clean it up, while writing the final version to our place in memory we stored above */
                                {
                                DIAGNOSTIC_LOG_3("%s unsupported command (message_type=%xh) %s", EVOLUTION_PRODUCT_NAME, message_type, hw_ptr->board_messages[msg_seq]);
                                return(0);
                                }
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

			//message details text...
			strcatl(json_bannmsg, ",\"msgdetails\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "", sizeof(json_bannmsg));	//TODO: hardcoded empty for now - future capability to be developed later
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

			//message duration...
			strcatl(json_bannmsg, ",\"dbb_duration\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//message priority level...
			strcatl(json_bannmsg, ",\"dbb_priority\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_priority);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//message expire priority...
			strcatl(json_bannmsg, ",\"dbb_expire_priority\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_expire_priority);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//message priority duration...
			strcatl(json_bannmsg, ",\"dbb_priority_duration\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_priority_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_multimedia_type
			strcatl(json_bannmsg, ",\"multimediatype\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, multimedia_type_str[db_bann->dbb_multimedia_type], sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));	

			//UCHAR dbb_multimedia_type_waiting_to_import  (see MULTIMEDIA_IMPORT_TYPE_*)

			//webpage URL... (need to read in from file pointed to by db_voic->dvc_disk_path)
			char web_page[1000];
			BannerFindMultimediaFile(db_wtc->dwc_stream_number, "");
			SystemReadDataFromFile(web_page, sizeof(web_page), db_voic->dvc_disk_path);
			remove_trailing_space(web_page);
			strcatl(json_bannmsg, ",\"webpageurl\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, web_page, sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

			//message playtime duration
			strcatl(json_bannmsg, ",\"dbb_playtime_duration\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_playtime_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//flasher duration
			strcatl(json_bannmsg, ",\"dbb_flasher_duration\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_flasher_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_light_signal
			strcatl(json_bannmsg, ",\"dbb_light_signal\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_light_signal);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//light duration
			strcatl(json_bannmsg, ",\"dbb_light_duration\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_light_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_audio_tts_gain
			strcatl(json_bannmsg, ",\"dbb_audio_tts_gain\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_audio_tts_gain);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_flash_new_message
			strcatl(json_bannmsg, ",\"dbb_flash_new_message\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_flash_new_message);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_visible_time;
			strcatl(json_bannmsg, ",\"dbb_visible_time\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_visible_time);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_visible_frequency;
			strcatl(json_bannmsg, ",\"dbb_visible_frequency\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_visible_frequency);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			
			//UCHAR dbb_visible_duration;
			strcatl(json_bannmsg, ",\"dbb_visible_duration\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_visible_duration);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_record_voice_at_launch_selection; //(true if a recorded voice at launch was made)
			strcatl(json_bannmsg, ",\"dbb_record_voice_at_launch_selection\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_record_voice_at_launch_selection);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_audio_recorded_gain
			strcatl(json_bannmsg, ",\"dbb_audio_recorded_gain\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_audio_recorded_gain);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_pa_delivery_mode
			strcatl(json_bannmsg, ",\"dbb_pa_delivery_mode\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_pa_delivery_mode);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_audio_repeat
			strcatl(json_bannmsg, ",\"dbb_audio_repeat\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%c\"", db_bann->dbb_audio_repeat);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

			//UCHAR dbb_speed
			strcatl(json_bannmsg, ",\"dbb_speed\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_speed);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));


			//UCHAR dbb_backcolor
			//UCHAR dbb_show_camera
			//UCHAR dbb_geolocation_latitude[12]
			//UCHAR dbb_geolocation_longitude[12]
			//UCHAR dbb_geolocation_altitude[12]

			/* TEMPLATE FOR: int
			strcatl(json_bannmsg, ",\"dbb_?\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", db_bann->dbb_?);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			 */
			/* TEMPLATE FOR: long
			strcatl(json_bannmsg, ",\"dbb_?\":", sizeof(json_bannmsg));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%ld", db_bann->dbb_?);
			strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
			 */
			/* TEMPLATE FOR: string (as a variable)    ---WARNING--- if you use mn_snprintf or sprintf, things get weird and JSON syntax is ruined
			strcatl(json_bannmsg, ",\"NAME\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			strcatl(json_bannmsg, VARIABLE, sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
			 */
			/* TEMPLATE FOR: string (as a hardcoded value)
			strcatl(json_bannmsg, ",\"NAME\":", sizeof(json_bannmsg));
			strcatl(json_bannmsg, "\"VALUE\"", sizeof(json_bannmsg));
			 */

			/*** DEV-NOTE: Any additional fields for the message get added ^^ABOVE THIS LINE^^ ***/
			strcatl(json_bannmsg, "}", sizeof(json_bannmsg));

			// Append this now-completed message object to the whole-json string
			strcatl(http_req_body, json_bannmsg, sizeof(http_req_body));
			json_bannmsg[0] = '\0';

			}

		// close-up shop on the array of banner messages...
		strcatl(http_req_body, "]", sizeof(http_req_body));

		/*
		if(pause_first)
			{
			strcatl(post_cmd_buf, "&pauseseconds=2&pausefirst=1", sizeof(post_cmd_buf));
			}
		*/

		strcatl(http_req_body, "}", sizeof(http_req_body));
		}
	}
else if(message_type & BANNER_CLEAR_SIGN)
	{
	int i;

	if(evolution_debug || banner_debug > 1)
		{
		DIAGNOSTIC_LOG_2("%s clear sign '%s'", EVOLUTION_PRODUCT_NAME, message);
		}

	strcatl(http_req_body, "{", sizeof(http_req_body));
	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, ",\"bannerpurpose\":\"clearsign\"", sizeof(http_req_body));
	strcatl(http_req_body, "}", sizeof(http_req_body));
	}
else if(message_type & BANNER_RAW_DATA)
	{
	int i;
	int flash_bits = 0;

	if(evolution_debug || banner_debug > 1)
		{
		DIAGNOSTIC_LOG_2("%s received raw for slot=%d.", EVOLUTION_PRODUCT_NAME, sequence_number);
		}

	}
else if(message_type & BANNER_IPSPEAKER_NO_RETURN_STATUS)
	{
	strcpyl(http_req_body, "{", sizeof(http_req_body));
	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, ",\"bannerpurpose\":\"noreturnstatus\"", sizeof(http_req_body));
	strcatl(http_req_body, "}", sizeof(http_req_body));
	}
else
	{
	int fg_color = 0;					// take first color 

	if(evolution_debug || banner_debug)
		{
		DIAGNOSTIC_LOG_2("%s raw data %s.", EVOLUTION_PRODUCT_NAME, message);
		}

	// ORIGINAL GET method (seems unique to IPSpeaker, so we just substitute with our own equivalent command to show the clock - triggered by else bannerpurpose) ~~ keeping it around for reference later if needed
	/*
	strcatl(post_cmd_buf, "GET ", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, "/signmsg?password=", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, hw_ptr->hardware_device_password, sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, "&loops=1", sizeof(post_cmd_buf));			// loops=1 stops the other message and shows clock 
	strcatl(post_cmd_buf, "&msgtext=", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, " HTTP/1.1\r\n", sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, "\r\n", sizeof(post_cmd_buf));
	*/

	strcpyl(http_req_body, "{", sizeof(http_req_body));
	strcatl(http_req_body, "\"password\":", sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, hw_ptr->hardware_device_password, sizeof(http_req_body));
	strcatl(http_req_body, "\"", sizeof(http_req_body));
	strcatl(http_req_body, ",\"bannerpurpose\":\"else\"", sizeof(http_req_body));
	strcatl(http_req_body, "}", sizeof(http_req_body));
	}

// Construct the request...
if(notjustspace(http_req_body, sizeof(http_req_body)))
	{
	remove_trailing_space(http_req_body);		//probably not needed, but just to be safe that we get a valid Content-Length value below

	//put the headers together
	strcatl(http_req_headers, "POST / HTTP/1.1\r\n", sizeof(http_req_headers));
	strcatl(http_req_headers, "User-Agent: MessageNet Evolution Banner Socket\r\n", sizeof(http_req_headers));
	strcatl(http_req_headers, "Content-Type: application/json\r\n", sizeof(http_req_headers));
	sprintf(temp_conversion_buf, "Content-Length: "FORMAT_STRLEN_STR"\r\n", strlen(http_req_body));
	strcatl(http_req_headers, temp_conversion_buf, sizeof(http_req_headers));
	strcatl(http_req_headers, "\r\n", sizeof(http_req_headers));

	remove_trailing_space(http_req_headers);	//probably not needed, but just to be safe

	//put all the request into the command buffer
	strcatl(post_cmd_buf, http_req_headers, sizeof(post_cmd_buf));
	strcatl(post_cmd_buf, http_req_body, sizeof(post_cmd_buf));
	}

// Try to send the request to the device...
hw_ptr->fd = SystemSocketConnect("", hw_ptr->term_ip, ip_port, 5, 0);
if(hw_ptr->fd < 0)
	{
       	// took to long to send data to port so lets disable it and abort
        DIAGNOSTIC_LOG_1("SystemSocketConnect() report error %s", HardwareReportPortError(hw_ptr));

	HardwareReportSystemAlerts(hw_ptr);
        HardwareDisablePort(hw_ptr, TRUE, TRUE);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_CLOSED);

        goto done;
        }
else
	{
	// our device is alive, so set status and allow to continue sending
	HardwareSystemAlertClear(hw_ptr);
	HardwareUpdateDeviceStatus(hw_ptr, DEVICE_CONNECTION_ACTIVE);
	}

if(evolution_debug || banner_debug > 1)
	{
	DIAGNOSTIC_LOG_4("%s message [%d] type=%02xh %s.", EVOLUTION_PRODUCT_NAME, mn_getpid(), message_type, post_cmd_buf);
	}

strcatl(post_cmd_buf, "\r\n", sizeof(post_cmd_buf));				//just to be safe that we send a new-line character
SystemSocketWrite(hw_ptr->fd, post_cmd_buf, strlen(post_cmd_buf));

if(message_type & BANNER_IPSPEAKER_NO_RETURN_STATUS)
	{
	// read status and close the port in board_verify_read() 
        goto done;
	}

socket_return = SystemSocketReadTimeout(hw_ptr->fd, post_cmd_buf, sizeof(post_cmd_buf), 5);
SystemTruncateReturnBuffer(post_cmd_buf, sizeof(post_cmd_buf), socket_return);
SystemSocketClose(hw_ptr->fd);

if(evolution_debug || banner_debug > 1)
	{
	DIAGNOSTIC_LOG_1("SystemSocketReadTimeout() %s", post_cmd_buf);
	}

done:
DIAGNOSTIC_FUNCTION_EXIT();

#endif
return(0);
}
