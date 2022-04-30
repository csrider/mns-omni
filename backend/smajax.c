/************************************************************************
** 	Module: 	smajax.c 
**
**	Author:		(Redacted), Chris Rider
**			Copyright (c) 1991-2021
***********************************************************************/

#include <stdio.h>
#include <signal.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#include "local.h"

// PORTIONS REDACTED

#include "support_evolution.h"

// PORTIONS REDACTED

#define MAX_MSG (1000)

DTSEC cur_time = 1L;
char cur_time_dtsec[DTSEC_LENGTH];

// PORTIONS REDACTED

char *cgi_product_name = PRODUCT_NAME;

// PORTIONS REDACTED

#define MAX_BUFFER	(30000)
#define MAX_ARGS	(1000)

char parsed_buffer[MAX_BUFFER];
char *parsed_input[MAX_ARGS];

/************************************************************************
** void int_hup_handler(int sig)
**
************************************************************************/
void int_hup_handler(int sig)
{
smdatabase_close_signal_received = TRUE;
}

/************************************************************************
** void int_usr1_handler(int sig)
**
************************************************************************/
void int_usr1_handler(int sig)
{
smdatabase_close_signal_received = TRUE;
}

/************************************************************************
** void int_pipe_handler(int sig)
**
************************************************************************/
void int_pipe_handler(int sig)
{
smdatabase_close_signal_received = TRUE;
}

/************************************************************************
** void inthandler(int sig)
**
************************************************************************/
void inthandler(int sig)
{
printf("smajax stopped due to signal (%d)\n", sig);
exit(1);
}

/***********************************************************************
** int cgi_ascii_to_hex(int value)
**
***********************************************************************/
int cgi_ascii_to_hex(int value)
{
if(value >= '0' && value <= '9')
	{
	return(value - '0');
	}
else if(value >= 'A' && value <= 'F')
	{
	return(value - 55);
	}
else if(value >= 'a' && value <= 'f')
	{
	return(value - 87);
	}
else
	{
	return(' ');
	}
}

/***********************************************************************
** int cgi_decode_form_string(char *where_to_put_it, char *in_str, int space_fill)
**
**	need to decode the string look for
**	'+'-> space
**	'%xx' -> hex equivalent
**
***********************************************************************/
void cgi_decode_form_string(char *where_to_put_it, char *in_str, int space_fill)
{
int i;
int j;

for(i = j = 0; in_str[j] ; )
	{
	if(in_str[j] == '+')
		{
		j++;
		where_to_put_it[i++] = ' ';
		}
	else if(in_str[j] == '%')
		{
		int hex = 0;

		j++;
		if(in_str[j])
			{
			hex = cgi_ascii_to_hex(in_str[j++]);
			}

		if(in_str[j])
			{
			hex = hex*16 + cgi_ascii_to_hex(in_str[j++]);
			}

		switch(hex)
			{
			case '\"':
			case '\'':
				hex = '`';
				break;

			default:
				break;
			}

		where_to_put_it[i++] = (char) hex;
		}
	else
		{
		where_to_put_it[i++] = in_str[j++];
		}

	/* dont copy more characters than the space allows */
	if(space_fill > 0 && i >= (space_fill - 1))
		{
		break;
		}
	}

where_to_put_it[i] = 0;		/* ensure null terminated */

if(space_fill > 0)
	{
	add_trailing_space(where_to_put_it, space_fill);
	}
}

/***********************************************************************
** int cgi_parsed_lookup(char *string_to_find, char *where_to_put_it, int space_fill)
**
**	Return TRUE if found or FALSE if not found
**
***********************************************************************/
int cgi_parsed_lookup(char *string_to_find, char *where_to_put_it, int space_fill)
{
int i;
int length = strlen(string_to_find);

strcpy(where_to_put_it, "");			/* clear the string */

for(i = 0; parsed_input[i]; i++)
	{
	if(!strncmp(parsed_input[i], string_to_find, length))
		{
		/* skip over the matching name and copy the data */
		cgi_decode_form_string(where_to_put_it, parsed_input[i] + length, space_fill);
		return(TRUE);
		}
	}

return(FALSE);
}

/***********************************************************************
 * ** DBRECORD cgi_parsed_lookup_record_number(char *string_to_find, DBRECORD not_found_default)
 * **
 * **      Return integer if found or 0.
 * **
 * ***********************************************************************/
DBRECORD cgi_parsed_lookup_record_number(char *string_to_find, DBRECORD not_found_default)
{
DBRECORD ret;

char space_for_long[100];

if(cgi_parsed_lookup(string_to_find, space_for_long, sizeof(space_for_long)))
        {
        ret = AlphaToRecordNumber(space_for_long);      /* information found */
        }
else
        {
        ret = not_found_default;                        /* information not found */
        }

return(ret);
}

/***********************************************************************
** int cgi_parse_input(void)
**
**	parse the stdin and fill the parsed_input array pointers.
**
**	Returns number of items in parsed_input array.
**
***********************************************************************/
int cgi_parse_input(void)
{
int i = 0;			/* number of pointers */
int j = 0;			/* number of characters read */
int ch;
int items = 0;
int done = FALSE;

char *query_string = NULL;
char *method = getenv("REQUEST_METHOD");

parsed_input[i] = parsed_buffer;
parsed_input[i + 1] = NULL;

/* is this a POST or GET method */
if(method && !strncmp(method, "GET", 3))
	{
	query_string = getenv("QUERY_STRING");
	strcpyl(parsed_buffer, query_string, sizeof(parsed_buffer) - 1);
	ch = *query_string;
	}
else
	{
	ch = getchar();
	}

if(1)
	{
	/* read in the entire submitted form */
	if(query_string == NULL)
		{
		parsed_buffer[j++] = ch;							/* save the first character read above */
		while((fgets(&parsed_buffer[j], sizeof(parsed_buffer) - j - 1, stdin) != NULL) && j < sizeof(parsed_buffer))
			{
			j += strlen(&parsed_buffer[j]);
			}
		}

	for(j = 0; done == FALSE && j < sizeof(parsed_buffer); j++)
		{
		switch(parsed_buffer[j])
			{
			case 0:
				done = TRUE;
				/* yes fall on throug here */

			case '&':
				/* reset for the next field */
				parsed_buffer[j] = 0;
	
				/* dont count empty input */
				if(strlen(parsed_input[i]) > 0)
					{
					items++;
					}
	
				parsed_input[++i] = &parsed_buffer[j + 1];
				parsed_input[i + 1] = NULL;
				break;
			}
		}
	}

return(items);
}

/***********************************************************************
** void int_file_size_handler(int sig)
**
***********************************************************************/
void int_file_size_handler(int sig)
{
DiagnosticOverrideDataCall(1);

DIAGNOSTIC_LOG_1("smajax file size signal (%d)", sig);
SignalTrimlog();

DiagnosticOverrideDataCall(-1);
}

/**********************************************************
** static int AJAXCheckStopped(void)
**
*********************************************************/
static int AJAXCheckStopped(void)
{
int ret = FALSE;

char filename[FILENAME_LENGTH];

if(notjustspace(TimeShareCompanyNameGet(), TIMESHARE_COMPANY_NAME_LENGTH))
        {
        mn_snprintf(filename, sizeof(filename), "/home/silentm/%s.%s", LOCKED_SEMAPHORE_FILE, TimeShareCompanyNameGet());
        }
else
        {
        mn_snprintf(filename, sizeof(filename), "/home/silentm/%s", LOCKED_SEMAPHORE_FILE);
        }

if(SystemCheckFileExists(filename, "") > 0)
	{
	ret = TRUE;
	}
	
return(ret);
}

/***********************************************************************************************************************************************
************************************************************************************************************************************************
 THE FOLLOWING IS NEEDED TO DECODE MESSAGE TEXT
************************************************************************************************************************************************
***********************************************************************************************************************************************/

/********************************************************************
 * ** void string_insert_string(char *str, char *dest)
 * **
 * ********************************************************************/
void string_insert_string(char *str, char *dest)
{
#ifndef MSGNET_WIN32
st_ins(str, dest, 0);
#endif
}

/*******************************************************************
 * ** void banner_set_current_time(void)
 * **
 * *******************************************************************/
void banner_set_current_time(void)
{
get_dtsec(cur_time_dtsec);                      /* set cur_time for schedule loading */
cur_time = AlphaToDTSEC(cur_time_dtsec);
}


/***********************************************************************
** void main(int argc, char *argv[], char *env[])
**
***********************************************************************/
int main(int argc, char *argv[], char *env[])
{
int i;
int next;
int top;
int left;
int stopped = FALSE;

char buffer[80];
char banner_host[80];
char *ptr;

cgi_parse_input();

if(stopped)
	{
	}
	
// PORTIONS REDACTED

else if(cgi_parsed_lookup("evolutionGetActiveMessagesForDevice=", buffer, sizeof(buffer)))
	{
	/* NOTE: Tried to make the global active msgs array work here, but it can't read its values for some reason. Go file-based for now (2016.08.28). */
	int count = 0;
	DBRECORD hw_recno = 0;
	char str_filename[256];
	FILE *file_evolution_active_msg_recnos = NULL;
	char line_buffer[MAX_CHARS];

	hw_recno = cgi_parsed_lookup_record_number("devicerecno=", 0);

	sprintf(str_filename, "/home/silentm/record/evolutionActiveMsgs."FORMAT_DBRECORD_STR".json", hw_recno);
	remove_trailing_space(str_filename);

	file_evolution_active_msg_recnos = fopen(str_filename, "r");

	if(file_evolution_active_msg_recnos)
		{
		printf("{\"evolution_active_msgs\":[");

		while(fgets(line_buffer, sizeof(line_buffer), file_evolution_active_msg_recnos))	/* loop each line of the file (each line is a single JSON message object) */
			{
			if( count > 0 ) { printf(","); }
			printf("%s", line_buffer);
			count++;
			}

		fclose(file_evolution_active_msg_recnos);

		printf("]}");

		DIAGNOSTIC_LOG_1("evolutionGetActiveMessagesForDevice: Responded for Evolution device with recno "FORMAT_DBRECORD_STR, hw_recno);
		}
	else
		{
		DIAGNOSTIC_LOG_1("evolutionGetActiveMessagesForDevice: Could not open file, %s", str_filename);
		}


	/*	Global array method (not working as of 2017.08.26)...
	printf("{");
	printf("\"evolution_active_msg_recnos\":[");

	for (i = 0; i <= MAX_SIGN_SEQUENCE; i++)
		{
		if (count > 0) printf(",");
		if (evolution_active_msg_recnos[i] > 0) printf(FORMAT_DBRECORD_STR, evolution_active_msg_recnos[i]);
		count++;
		}

	printf("]");
	printf("}");
	*/

	}
else if(cgi_parsed_lookup("evolutionGetBannerMessageRecord=", buffer, sizeof(buffer)))
	{
	/* Given a banner record number, look it up and return for Omni.
	 *
	 * Revisions:
	 * 	2018.09.14	Chris Rider	Creation.
	 */
	DBRECORD recno = 0;

	recno = cgi_parsed_lookup_record_number("recno=", 0);

	if(db_syspa_init() || db_bann_init())
		{
		DIAGNOSTIC_LOG("Database initialization error");
		printf("-1");
		}
	else
		{
		//initialize a stucture with which to construct a record we'll write.
		memset(db_bann, 0, sizeof(DB_WTC));

		//generate JSON
		//*** TODO ***

		}//end else db did init

	db_syspa_close();
	db_bann_close();
	}
else if(cgi_parsed_lookup("evolutionGetActiveMessagesForDevice_recnosOnly=", buffer, sizeof(buffer)))
	{
	/* This is intended to work like when the "show sign messages" button is clicked for a hardware device.
	 * Essentially, you command WTC and then read WTC to get the messages. You then put those into an AJAX response.
	 *
	 * Revisions:
	 * 	2018.09.14	Chris Rider	Creation.
	 */
	int done;
	int message_type = 0;
	int i = 0;
	DBRECORD hw_recno = 0;
	hw_recno = cgi_parsed_lookup_record_number("devicerecno=", 0);
    	char response[MAX_CHARS] = "";
	char temp_conversion_buf[10];

	if(db_syspa_init() || db_wtc_init())
		{
		DIAGNOSTIC_LOG("Database initialization error");
    		strcatl(response, "Database initialization error", sizeof(response));
		}
	else
		{
		//initialize a wtc stucture with which to construct a WTC record we'll write.
		memset(db_wtc, 0, sizeof(DB_WTC));

        	// First, send a command to initiate getting messages.
        	// It seems that right after this point, WTC will then give us messages which we'll need to loop through to get.
		wtc_getcur = hw_recno;       //set this db_wtc.c global that its functions use
		db_wtc->dwc_record_num = hw_recno;	//necessary for WTC_SIGN_MESSAGES to banner (per db_wtc.c comments in command_wtc) - without this, we get segfault
	        if(command_wtc(WTC_WRITE, WTC_SIGN_MESSAGES, WTC_BROWSER, WTC_BANNER_BOARD, getpid(), 0))
	                {
	                wtc_write_error_log(__FILE__, __LINE__);
    			strcatl(response, "WTC command failed to write.", sizeof(response));
	                }
		else
			{
    			strcatl(response, "{", sizeof(response));

    			strcatl(response, "\"hwRecno\":\"", sizeof(response));
			mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), FORMAT_DBRECORD_STR, hw_recno);
    			strcatl(response, temp_conversion_buf, sizeof(response));
    			strcatl(response, "\"", sizeof(response));

    			strcatl(response, ",", sizeof(response));

			strcatl(response, "\"activeMessages\":[", sizeof(response));
			// Now loop and get all wtc records generated as a result of the above.
			// There should be one for each message?
			for(done = FALSE; done == FALSE;)
				{
				if(command_wtc(WTC_READ, WTC_SIGN_MESSAGES, WTC_BANNER_BOARD, WTC_BROWSER, getpid(), 0))
	        	                {
					// We have the WTC record for a message in RAM, so go ahead and clean it up on disk by deleting record there...
					db_wtc_delete();
					//DIAGNOSTIC_LOG_2("recno="FORMAT_DBRECORD_STR" / flag=%d", db_wtc->dwc_record_num, db_wtc->dwc_message_type);	//DEBUG

					// Do some basic checks that exit out of the loop if something's not right
					// else, then try to actually get message data.
					if(db_wtc->dwc_flag == 2)
                        	        	{
                                		done = TRUE;    //exit out of wtc-read loop
                                		}
					else if(db_wtc->dwc_flag == 1)
		                                {
	        	                        done = TRUE;    //exit out of wtc-read loop
	                	                }
        	                	else if(db_wtc->dwc_record_num > 0)
	                	                {
						//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", db_bann_getcur());
    						//strcatl(response, "[a message with recno ", sizeof(response));
    						//strcatl(response, temp_conversion_buf, sizeof(response));
    						//strcatl(response, "]", sizeof(response));
						//DIAGNOSTIC_LOG_2("[a message with recno "FORMAT_DBRECORD_STR" and type %d]", db_wtc->dwc_record_num, db_wtc->dwc_message_type);	//DEBUG
						
						// Construct JSON for each active message
	        		                //if(message_type != db_wtc->dwc_message_type)
	        		                //        {
	        		                //        message_type = db_wtc->dwc_message_type;

	        		                        switch(db_wtc->dwc_message_type)
	        		                                {
	        		                                case 1:
									//Messages
									if(i > 0)
										{
    										strcatl(response, ",", sizeof(response));
										}
									mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), FORMAT_DBRECORD_STR, db_wtc->dwc_record_num);
    									strcatl(response, "{\"recno\":\"", sizeof(response));
    									strcatl(response, temp_conversion_buf, sizeof(response));
    									strcatl(response, "\",\"type\":\"active\"}", sizeof(response));
									i++;
	        		                                        break;
	
	        		                                case 2:
									//Messages Waiting
									if(i > 0)
										{
    										strcatl(response, ",", sizeof(response));
										}
									mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), FORMAT_DBRECORD_STR, db_wtc->dwc_record_num);
    									strcatl(response, "{\"recno\":\"", sizeof(response));
    									strcatl(response, temp_conversion_buf, sizeof(response));
    									strcatl(response, "\",\"type\":\"waiting\"}", sizeof(response));
									i++;
	        		                                        break;
	
	        		                                case 3:
									//Messages Hidden
									if(i > 0)
										{
    										strcatl(response, ",", sizeof(response));
										}
									mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), FORMAT_DBRECORD_STR, db_wtc->dwc_record_num);
    									strcatl(response, "{\"recno\":\"", sizeof(response));
    									strcatl(response, temp_conversion_buf, sizeof(response));
    									strcatl(response, "\",\"type\":\"hidden\"}", sizeof(response));
									i++;
	        		                                        break;
	        		                                default:
									//Messages unknown
									if(i > 0)
										{
    										strcatl(response, ",", sizeof(response));
										}
									mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), FORMAT_DBRECORD_STR, db_wtc->dwc_record_num);
    									strcatl(response, "{\"recno\":\"", sizeof(response));
    									strcatl(response, temp_conversion_buf, sizeof(response));
    									strcatl(response, "\",\"type\":\"unknown\"}", sizeof(response));
									i++;
	        		                                        break;
	        		                                }//end switch
	        		                //        }//end if
						}//end else valid recno
					}
				else
	                        	{
					mn_delay(100);  //slight delay before trying to read next WTC record for next message
					}
				}//end for
    			strcatl(response, "]}", sizeof(response));
			}//end else wtc wrote
		}//end else db did init

	printf("%s", response);
	//DIAGNOSTIC_LOG_1("\"%s\"", response);	//DEBUG

	db_syspa_close();
	db_wtc_close();
	}
else if(cgi_parsed_lookup("evolutionGetActiveMessagesForDevice_countsOnly=", buffer, sizeof(buffer)))
	{
	/* This is intended to work like when the "show sign messages" button is clicked for a hardware device.
	 * Essentially, you command WTC and then read WTC to get the messages. You then put those into an AJAX response.
	 * 
	 *
	 * Revisions:
	 * 	2018.09.14	Chris Rider	Creation.
	 */
	int done;
	int message_type = 0;
	DBRECORD hw_recno = 0;
	hw_recno = cgi_parsed_lookup_record_number("devicerecno=", 0);
	int total_active_messages = 0;
	int total_active_messages_waiting = 0;
	int total_active_messages_hidden = 0;

	if(db_syspa_init() || db_wtc_init())
		{
		DIAGNOSTIC_LOG("Database initialization error");
		printf("-1");
		}
	else
		{
		//initialize a wtc stucture with which to construct a WTC record we'll write.
		memset(db_wtc, 0, sizeof(DB_WTC));

        	// First, send a command to initiate getting messages.
        	// It seems that right after this point, WTC will then give us messages which we'll need to loop through to get.
		wtc_getcur = hw_recno;       //set this db_wtc.c global that its functions use
		db_wtc->dwc_record_num = hw_recno;	//necessary for WTC_SIGN_MESSAGES to banner (per db_wtc.c comments in command_wtc) - without this, we get segfault
	        if(command_wtc(WTC_WRITE, WTC_SIGN_MESSAGES, WTC_BROWSER, WTC_BANNER_BOARD, getpid(), 0))
	                {
	                wtc_write_error_log(__FILE__, __LINE__);
			printf("WTC command failed to write.");
	                }
		else
			{
			// Now loop and get all wtc records generated as a result of the above.
			// There should be one for each message?
			for(done = FALSE; done == FALSE;)
				{
				if(command_wtc(WTC_READ, WTC_SIGN_MESSAGES, WTC_BANNER_BOARD, WTC_BROWSER, getpid(), 0))
	        	                {
					// We have the WTC record for a message in RAM, so go ahead and clean it up on disk by deleting record there...
					db_wtc_delete();


					// Do some basic checks that exit out of the loop if something's not right
					// else, then try to actually get message data.
					if(db_wtc->dwc_flag == 2)
                        	        	{
                                		done = TRUE;    //exit out of wtc-read loop
                                		}
					else if(db_wtc->dwc_flag == 1)
		                                {
	        	                        done = TRUE;    //exit out of wtc-read loop
	                	                }
        	                	else if(db_wtc->dwc_record_num > 0)
	                	                {
						//printf("[a message with recno "FORMAT_DBRECORD_STR"]", db_bann_getcur());
						//DIAGNOSTIC_LOG_1("[a message with recno "FORMAT_DBRECORD_STR"]", db_bann_getcur());
						
						// Count each type of active message
	        		                if(message_type != db_wtc->dwc_message_type)
	        		                        {
	        		                        message_type = db_wtc->dwc_message_type;

	        		                        switch(message_type)
	        		                                {
	        		                                case 1:
									//Messages
									total_active_messages++;
	        		                                        break;
	
	        		                                case 2:
									//Messages Waiting
									total_active_messages_waiting++;
	        		                                        break;
	
	        		                                case 3:
									//Messages Hidden
									total_active_messages_hidden++;
	        		                                        break;
	        		                                }//end switch
	        		                        }//end if
						}//end else valid recno
					}
				else
	                        	{
					mn_delay(100);  //slight delay before trying to read next WTC record for next message
					}
				}//end for
			}//end else wtc wrote
		}//end else db did init

	db_syspa_close();
	db_wtc_close();

	printf("{");
	printf("\"active_messages\":%d,", total_active_messages);
	printf("\"active_messages_waiting\":%d,", total_active_messages_waiting);
	printf("\"active_messages_hidden\":%d", total_active_messages_hidden);
	printf("}");
	}
else if(cgi_parsed_lookup("evolutionGetActiveMessagesForDevice_likeShowSignMsgsScreen=", buffer, sizeof(buffer)))
	{
	/* This is intended to work like when the "show sign messages" button is clicked for a hardware device.
	 * Essentially, you command WTC and then read WTC to get the messages. You then put those into an AJAX response.
	 *
	 * Revisions:
	 * 	2018.09.10	Chris Rider	Creation.
	 */
	int done;
	DBRECORD hw_recno = 0;
	hw_recno = cgi_parsed_lookup_record_number("devicerecno=", 0);
	int total_active_messages = 0;

	if(db_syspa_init() || db_wtc_init() || db_bann_init())
		{
		DIAGNOSTIC_LOG("Database initialization error");
		printf("-1");
		}
	else
		{
		//initialize a wtc stucture with which to construct a WTC record we'll write.
		memset(db_wtc, 0, sizeof(DB_WTC));

        	// First, send a command to initiate getting messages.
        	// It seems that right after this point, WTC will then give us messages which we'll need to loop through to get.
		wtc_getcur = hw_recno;       //set this db_wtc.c global that its functions use
		db_wtc->dwc_record_num = hw_recno;	//necessary for WTC_SIGN_MESSAGES to banner (per db_wtc.c comments in command_wtc) - without this, we get segfault
	        if(command_wtc(WTC_WRITE, WTC_SIGN_MESSAGES, WTC_BROWSER, WTC_BANNER_BOARD, getpid(), 0))
	                {
	                wtc_write_error_log(__FILE__, __LINE__);
			printf("WTC command failed to write.");
	                }
		else
			{
			// Now loop and get all wtc records generated as a result of the above.
			// There should be one for each message?
			for(done = FALSE; done == FALSE;)
				{
				if(command_wtc(WTC_READ, WTC_SIGN_MESSAGES, WTC_BANNER_BOARD, WTC_BROWSER, getpid(), 0))
	        	                {
					// We have the WTC record for a message in RAM, so go ahead and clean it up on disk by deleting record there...
					db_wtc_delete();

					// Do some basic checks that exit out of the loop if something's not right
					// else, then try to actually get message data.
					if(db_wtc->dwc_flag == 2)
                        	        	{
                                		done = TRUE;    //exit out of wtc-read loop
                                		}
					else if(db_wtc->dwc_flag == 1)
		                                {
	        	                        done = TRUE;    //exit out of wtc-read loop
	                	                }
        	                	else if(db_bann_setcur(db_wtc->dwc_record_num) > 0)
	                	                {
        	                	        // Got currency for this message's bann record,so we can get all actual message data.
						//printf("[a message with recno "FORMAT_DBRECORD_STR"]", db_bann_getcur());
						//DIAGNOSTIC_LOG_1("[a message with recno "FORMAT_DBRECORD_STR"]", db_bann_getcur());
						
						total_active_messages++;
						
						}
					}
				else
	                        	{
					mn_delay(100);  //slight delay before trying to read next WTC record for next message
					}
				}//end for
			}//end else wtc wrote
		}//end else db did init

	db_syspa_close();
	db_wtc_close();
	db_bann_close();
	}
else if(cgi_parsed_lookup("evolutionGetActiveMessagesForDevice_serverPushTriggerUpdateDeviceStatus=", buffer, sizeof(buffer)))
	{
	/* This is intended to trigger a message push to the Omni, in the same way as happens
	 * whenever the "show sign messages" button is clicked for a hardware device.
	 * We modeled logic from the "Show Sign Messages" screen in smcgi. The core
	 * of that logic is in the smcgi_sign_control.c file.
	 *
	 * The basic idea is to vector a command toward the banner process via WTC database.
	 * We just construct a WTC record and write it. The banner process should take it from there.
	 *
	 * NOTE: This mostly worked, but didn't reflect ALL messages for some reason like the CGI page does.
	 */
	DBRECORD hw_recno = 0;
	hw_recno = cgi_parsed_lookup_record_number("devicerecno=", 0);

	if(db_syspa_init() || db_wtc_init())
		{
		DIAGNOSTIC_LOG("Database initialization error");
		printf("-1");
		}
	else
		{
		//initialize a wtc stucture with which to construct a WTC record we'll write.
		memset(db_wtc, 0, sizeof(DB_WTC));

		//strcpysl(db_wtc->dwc_return_node, "", sizeof(db_wtc->dwc_return_node));
		//	call execute_bann_sync with dwc_record_num = hardware recno?
		//db_wtc->dwc_record_num = hw_recno;
		//strcpy(db_wtc->dwc_rec_type, wtc_commands[WTC_BANNER_SYNC]);
		//strcpy(db_wtc->dwc_msg_destin, wtc_sources[WTC_BANNER_BOARD]);
		//strcpy(db_wtc->dwc_node_name, db_sysp->dsy_node_name);
		
		strcpysl(db_wtc->dwc_msg_buffer, "", sizeof(db_wtc->dwc_msg_buffer));
		strcpysl(db_wtc->dwc_return_node, "", sizeof(db_wtc->dwc_return_node));
		strcpy(db_wtc->dwc_rec_type, wtc_commands[WTC_OMNI_SYNC]);
		db_wtc->dwc_hard_recno = hw_recno;
		strcpysl(db_wtc->dwc_msg_destin, "", sizeof(db_wtc->dwc_msg_destin)); 
		strcpysl(db_wtc->dwc_node_name, "", sizeof(db_wtc->dwc_node_name));

		//if(command_wtc(WTC_WRITE, WTC_BANNER_SYNC, WTC_BANNER_BOARD, WTC_BANNER_BOARD, 0, 0))
		if(command_wtc(WTC_WRITE, WTC_OMNI_SYNC, WTC_BANNER_MSG, WTC_BANNER_BOARD, 0, 0))
		        {
		        wtc_write_error_log(__FILE__, __LINE__);
			printf("WTC command failed to write.");
		        }
		else
			{
			printf("WTC command written. Active messages should be arriving.");
			//printf("db_wtc->dwc_rec_type = %s\n", db_wtc->dwc_rec_type);
			//printf("db_wtc->dwc_msg_destin = %s\n", db_wtc->dwc_msg_destin);
			//printf("db_wtc->dwc_node_name = %s\n", db_wtc->dwc_node_name);
			//printf("db_wtc->dwc_msg_buffer = %s\n", db_wtc->dwc_msg_buffer);
			//printf("db_wtc->dwc_return_node = %s\n", db_wtc->dwc_return_node);
			//printf("db_wtc->dwc_hard_recno = "FORMAT_DBRECORD_STR"\n", db_wtc->dwc_hard_recno);

			WakeUpBannerServer();
			}

		}//end else (for db init)

	db_syspa_close();
	db_wtc_close();
	}
else if(cgi_parsed_lookup("evolutionGetMessageDataForRecnoZX=", buffer, sizeof(buffer)))
  {
    DBRECORD recno_zx;
    char hardware_deviceid[DEVICEID_LENGTH];
    char json_bannmsg[MAX_CHARS] = "";
    DBRECORD template_recno = 0;
    char temp_conversion_buf[10];
    char alert_status_str[2];
    char tmp_str[3];        //for translating UCHAR typed numbers to integers for easy array lookup
    int tmp_i;              //for translating UCHAR typed numbers to integers for easy array lookup
	
    recno_zx = cgi_parsed_lookup_record_number("msgrecno=", 0);
    cgi_parsed_lookup("deviceid=", hardware_deviceid, sizeof(hardware_deviceid));

    //DIAGNOSTIC_LOG_1("Getting ZX message record data for "FORMAT_DBRECORD_STR".", recno_zx);		//DEBUG

    strcatl(json_bannmsg, "{", sizeof(json_bannmsg));		//begin assembling a JSON object

    if(db_syspa_init() || db_bann_init() || db_list_init() || db_signs_init() || db_hard_init() || db_staff_init())
	{
	DIAGNOSTIC_LOG("Database initialization error");
	printf("Database initialization error");
	}
    else
	{

	if (db_bann_setcur(recno_zx) > 0)
		{
		//sign sequence number... (higher numbers should show first)
		strcatl(json_bannmsg, "\"signseqnum\":-1", sizeof(json_bannmsg));
		//mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "%d", sequence_number);
		//strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));

		//message's ZX banner recno...
		strcatl(json_bannmsg, ",\"recno_zx\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", recno_zx);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));
	
		//message's ZX dtsec (launch time)...
		strcatl(json_bannmsg, ",\"dbb_rec_dtsec\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, remove_leading_space(db_bann->dbb_rec_dtsec), sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//message's template banner recno...
		strcatl(json_bannmsg, ",\"recno_template\":", sizeof(json_bannmsg));
		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\""FORMAT_DBRECORD_STR"\"", db_bann->dbb_parent_record);
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
/*		strcatl(json_bannmsg, ",\"msgtext\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		ptr = json_bannmsg + strlen(json_bannmsg);	//get a pointer to the place in memory that we just wrote to (so we can continue where that left off in the function below)
		if(translate_for_evo(hw_ptr, hw_ptr->board_messages[(db_wtc->dwc_sequence[0] - SIGN_BASE)], ptr, sizeof(json_bannmsg)) == TRANSLATE_NOT_SUPPORTED)	// attempt to format our message and clean it up, while writing the final version to our place in memory we stored above
			{
			DIAGNOSTIC_LOG("ERROR: Could not translate evolution message text. Message not sent to device!");
			return(0);
			}
		//strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));*/
/*		mn_snprintf(temp_conversion_buf, sizeof(temp_conversion_buf), "\"%s\"", db_bann->);
		strcatl(json_bannmsg, temp_conversion_buf, sizeof(json_bannmsg));*/
		strcatl(json_bannmsg, ",\"msgtext\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		char message[PAGE_MESSAGE_LENGTH];
		strcpyl(message, db_bann->dbb_msg_1, sizeof(message));
		strcatl(message, db_bann->dbb_msg_2, sizeof(message));
		strcatl(message, db_bann->dbb_msg_3, sizeof(message));
		strcatl(message, db_bann->dbb_msg_4, sizeof(message));
		strcatl(message, db_bann->dbb_msg_5, sizeof(message));
		//
		//StreamInitialize();
		//LoadLaunchStreamNumberData(db_bann->dbb_stream_number, NULL, NULL, 0, NULL);
		//LoadAnswerStreamNumberData(db_bann->dbb_stream_number, NULL, NULL, 0);
		//lm_decode(1, "", FALSE, db_bann->dbb_stream_number);
		//BannerReplaceMessageVariablesForPaging(message);	//TODO!!!!
		//
		strcatl(json_bannmsg, message, sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//message details text...
		strcatl(json_bannmsg, ",\"msgdetails\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "", sizeof(json_bannmsg));	//TODO: hardcoded empty for now - future capability to be developed later
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

		//audio group name(s) that this device belongs to --as JSON array in case there are multiple...
		strcatl(json_bannmsg, ",\"dsi_audio_group_name\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "[", sizeof(json_bannmsg));
			db_signs_select(0);
			strcpy(db_sign->res_id, res_id);
			strcpysl(db_sign->dsi_deviceid, hardware_deviceid, DEVICEID_LENGTH);
			strcpyl(db_sign->dsi_sign_group_name, "", AUDIOGROUP_LENGTH);
			int db_signs_i = 0;
			int db_signs_nextptr = db_signs_find();
			while(db_signs_nextptr > 0
				&& !strcmp(db_sign->res_id, res_id)
				&& !strcmp(db_sign->dsi_deviceid, hardware_deviceid)
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

		//audio group name(s) that this device belongs to --as JSON array in case there are multiple...
		strcatl(json_bannmsg, ",\"dbb_audio_groups\":", sizeof(json_bannmsg));
		strcatl(json_bannmsg, "[", sizeof(json_bannmsg));
		if(!strcmp(db_bann->dbb_audio_group, bba_multiple))
			{
			//multiple audio groups defined in the message template, so need to list all that this device belongs to

			BannerOptions(db_bann->dbb_parent_record, DB_ISAM_READ);
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
			
			// NOT WORKING YET... no reason it shouldn't work, but it's Jerry's ass-backward code, so who the hell knows... work on it later
			int i_ag = 0;
                        DBRECORD list_record;
                        list_record = FindMultiAudioSignStreamNumberData(bann_recno);   //dev-note: this is same as stream number in banner record --verified
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

                        strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));

			/* This works, but doesn't include path for cam stream...
			char camIP[IP_LENGTH];
			HardwareGetIP_byDeviceID(camIP, db_bann->dbb_camera_deviceid, TRUE);
			trcatl(json_bannmsg, camIP, sizeof(json_bannmsg));
			*/

			char *ptr;
			ptr = json_bannmsg + strlen(json_bannmsg);      //get a pointer to the place in memory that we last wrote to (so we can continue where that left off in the function below)
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
		strcatl(json_bannmsg, "\"", sizeof(json_bannmsg));
		DBRECORD staff_cur_recno = db_staff_getcur();
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

		strcatl(json_bannmsg, "}", sizeof(json_bannmsg));
		}
	else
		{
		//no currency set
		printf("Could not set currency");
		}

	}

    //finalize and send the JSON response
    strcatl(json_bannmsg, "}\n", sizeof(json_bannmsg));		//end assembling a JSON object
    printf("%s", json_bannmsg);

    //cleanup database stuff
    db_syspa_close();
    db_bann_close();
    db_list_close();
    db_signs_close();
    db_hard_close();
    db_staff_close();
    }
else if(cgi_parsed_lookup("evolutionReportNetworkInfo=", buffer, sizeof(buffer)))
	{
	/* This is intended to inform the server of an Omni's current IP address.
	 * This is the new preferable way to handle DHCP addressing... just update the hardware record with the IP.
	 *
	 * Data in the GET request:
	 *	&devicerecno=
	 *	&ipMethodConfig=
	 *	&ipMethodCurrent=
	 *	&ipAddress=
	 *
	 * Full example of GET data #1:		http://192.168.1.58/~silentm/bin/smajax.cgi?evolutionReportNetworkInfo=1&devicerecno=363&ipMethodConfig=DHCP&ipMethodCurrent=DHCP&ipAddress=192.168.1.229
	 * Full example of GET data #2:		http://192.168.1.58/~silentm/bin/smajax.cgi?evolutionReportNetworkInfo=1&devicerecno=363&ipMethodConfig=Static&ipMethodCurrent=DHCP&ipAddress=192.168.1.229
	 * Full example of GET data #3:		http://192.168.1.58/~silentm/bin/smajax.cgi?evolutionReportNetworkInfo=1&devicerecno=363&ipMethodConfig=DHCP&ipMethodCurrent=STATIC&ipAddress=192.168.1.229
	 */
	DBRECORD hw_recno = 0;
	char ipMethodConfigured[7];
	char ipMethodActual[7];
	char ipAddress[IP_LENGTH];

	hw_recno = cgi_parsed_lookup_record_number("devicerecno=", 0);
	cgi_parsed_lookup("ipMethodConfig=", ipMethodConfigured, sizeof(ipMethodConfigured));
	cgi_parsed_lookup("ipMethodCurrent=", ipMethodActual, sizeof(ipMethodActual));
	cgi_parsed_lookup("ipAddress=", ipAddress, sizeof(ipAddress));

	// DEBUGGING...
	//DIAGNOSTIC_LOG_1("TEST: GET-parsed ipMethodConfigured = \"%s\"", ipMethodConfigured);
	//DIAGNOSTIC_LOG_1("TEST: GET-parsed ipMethodActual = \"%s\"", ipMethodActual);
	//DIAGNOSTIC_LOG_1("TEST: GET-parsed ipAddress = \"%s\"", ipAddress);

	if(db_syspa_init() || db_wtc_init() || db_hard_init())
		{
		DIAGNOSTIC_LOG("Database initialization error");
		printf("Database initialization error");
		}
	else
		{
		if(hw_recno > 0 && db_hard_setcur(hw_recno) > 0)
			{
			//RESPONSES TO USE:
			//<string name="SMAJAX_RESPONSE_HW_NETWORK_NOT_CHANGED">Hardware record network info not changed</string>
			//<string name="SMAJAX_RESPONSE_HW_NETWORK_CHANGED_SUCCEEDED">Hardware record network info updated</string>
			//<string name="SMAJAX_RESPONSE_HW_NETWORK_CHANGED_FAILED">Hardware record network info failed to update</string>
			
			// If provided IP is different than what's defined for this hardware, then we have work to do... (else we don't have anything to do)
			if (strcmp(ipAddress, db_hard->dhc_terminal_server_ip))
				{
				// If Omni IP method is DHCP, check hardware record and update if needed
				if (!strcmp(ipMethodConfigured, "DHCP  ") || !strcmp(ipMethodActual, "DHCP  "))
					{
					//Omni IP method is DHCP so we should update record to maintain communication with it
					//DIAGNOSTIC_LOG("TEST: Hardware record network info is different and needs to update");//REMOVE THIS

					//update the IP field...
					strcpyl(db_hard->dhc_terminal_server_ip, ipAddress, IP_LENGTH);

					if (db_hard_write() < 0)
						{
						DIAGNOSTIC_LOG_1("Failed to update IP address for hardware record "FORMAT_DBRECORD_STR, hw_recno);
						printf("Hardware record network info failed to update");
						}
					else
						{
						printf("Hardware record network info updated");

						//update our hardware as online (obviously or it wouldn't have worked to this point), so it can immediately keep working
						//(note: this wtc bit is what happens after a hardware record Save (UPDATE cmd), so it seems right)
						memset(db_wtc, 0, sizeof(DB_WTC));
						db_wtc->dwc_flag = 0;
						wtc_delete_record = db_hard_getcur();
						command_wtc(WTC_WRITE, WTC_HARD_UPDATE, WTC_HARDWARE, WTC_BANNER_BOARD, 0, 0);
						}
					}
				else
					{
					//Omni IP method is static of non-DHCP so let's not update the hardware as that might ruin someone's purpose
					printf("Hardware record network info not changed (non-DHCP address provided by Omni, and don't want to obliterate a potentially purposeful database field)");
					}
				}
			else
				{
				printf("Hardware record network info not changed (IP provided by Omni matches what's in database)");
				}
			}
		else 
			{
			DIAGNOSTIC_LOG_1("Failed to set currency for hardware record "FORMAT_DBRECORD_STR, hw_recno);
			printf("Could not set currency");
			}
		}//end else (for db init)

	db_syspa_close();
	db_wtc_close();
	db_hard_close();
	}
else
	{
	DIAGNOSTIC_LOG("no command found");

	//NOTE: the following string is depended on by clients (like Omni) to know if something isn't right. So don't change it!
	printf("No command found\n");

	#ifdef USE_SMDATABASE
	smdatabase_close();
	#endif
	}

return(0);
}
