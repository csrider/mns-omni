/*********************************************************************
**	Module:		support_evolution.h
**
**	Author:		Chris Rider
**
**	Revisions:
**	2017.05.23	Chris Rider	Creation (based on (redacted).h)
**
*********************************************************************/

#define IPS_SLOT_0		(0)		// SIGN_BASE + IPS_SLOT_0 is first message
#define IPS_SLOT_25		(25)		// SIGN_BASE + IPS_SLOT_26 is first message
#define IPS_FLASH_MESSAGE_SEQ	(26)		// next slot available

/*
extern int ips_debug;

int send_to_IPS_device(struct _hardware *hw_ptr, char ptr[], int sequence_number, int message_type);
int BannerSetup_AND_GPIO(struct _hardware *hw_ptr, int value);
int BannerMonitorAND(struct _hardware *hw_ptr);
*/

typedef enum
	{
	BANNER_EVOLUTION_CMD_NONE = 201,	/* just being extra sure that we're different than BANNER_* commands in case we use these in banner routines */ 
	BANNER_EVOLUTION_CMD_STOP_MESSAGE,
	BANNER_EVOLUTION_CMD_NEW_MESSAGE,
	BANNER_EVOLUTION_CMD_SEQ_NUMBER,
	BANNER_EVOLUTION_CMD_CLEAR_SIGN
	} BANNER_EVOLUTION_CMD;

extern int isFileOpen;
extern int isFileOutOpen;

//DBRECORD evolution_active_msg_recnos[MAX_SIGN_SEQUENCE];
extern void print_contents_of_active_msg_array(int printOnlyPopulatedElements);
extern void insert_recno_to_active_msg_array(DBRECORD recnoToAdd);
extern void remove_recno_from_active_msg_array(DBRECORD recnoToRemove, int removeAllMatching, int startFromOldest);
extern void clear_active_msg_array();
extern void find_camera_stream(char *cam_stream, int cam_stream_length, char *camera_deviceid);

int BannerMonitorEvolutionApp(struct _hardware *hw_ptr);
int send_to_evolution_appliance_discreteMsg(struct _hardware *hw_ptr, const int banner_evo_cmd, DBRECORD bann_recno, int sequence_number, char message[], DBRECORD template_recno); 
int send_to_evolution_appliance(struct _hardware *hw_ptr, char ptr[], int sequence_number, int message_type, DBRECORD stream_number);
