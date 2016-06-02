package edu.cmu.cs.gabriel.network;

public class NetworkProtocol {

	public static final int NETWORK_RET_FAILED = 1;
	public static final int NETWORK_RET_RESULT = 2;
	public static final int NETWORK_RET_CONFIG = 3;
	public static final int NETWORK_RET_TOKEN = 4;
	public static final int NETWORK_MEASUREMENT = 4;
	
	public static final String HEADER_MESSAGE_CONTROL = "control";
	public static final String HEADER_MESSAGE_RESULT = "result";
	public static final String HEADER_MESSAGE_INJECT_TOKEN = "token_inject";
	public static final String HEADER_MESSAGE_FRAME_ID = "id";
	public static final String HEADER_MESSAGE_ENGINE_ID = "engine_id";

	public static final String CUSTOM_DATA_MESSAGE_TYPE = "type";
	//response packet data type
	public static final String CUSTOM_DATA_MESSAGE_TYPE_ADD_PERSON= "add_person";
	public static final String CUSTOM_DATA_MESSAGE_TYPE_TRAIN= "train";
	public static final String CUSTOM_DATA_MESSAGE_TYPE_DETECT = "detect";
	public static final String CUSTOM_DATA_MESSAGE_TYPE_GET_STATE = "get_state";
	public static final String CUSTOM_DATA_MESSAGE_TYPE_LOAD_STATE = "load_state";
	public static final String CUSTOM_DATA_MESSAGE_TYPE_IMG = "image";
	public static final String CUSTOM_DATA_MESSAGE_VALUE= "value";
	public static final String CUSTOM_DATA_MESSAGE_TYPE_PERSON= "people";



	public static final String CUSTOM_DATA_MESSAGE_NUM = "num";
	public static final String CUSTOM_DATA_MESSAGE_ROI_TEMPLATE_X1 = "item_%_roi_x1";
	public static final String CUSTOM_DATA_MESSAGE_ROI_TEMPLATE_Y1 = "item_%_roi_y1";
	public static final String CUSTOM_DATA_MESSAGE_ROI_TEMPLATE_X2 = "item_%_roi_x2";
	public static final String CUSTOM_DATA_MESSAGE_ROI_TEMPLATE_Y2 = "item_%_roi_y2";
	public static final String CUSTOM_DATA_MESSAGE_IMG_TEMPLATE = "item_%_img";

	public static final String CUSTOM_DATA_MESSAGE_ROI_X1 = "roi_x1";
	public static final String CUSTOM_DATA_MESSAGE_ROI_Y1 = "roi_y1";
	public static final String CUSTOM_DATA_MESSAGE_ROI_X2 = "roi_x2";
	public static final String CUSTOM_DATA_MESSAGE_ROI_Y2 = "roi_y2";
	public static final String CUSTOM_DATA_MESSAGE_NAME = "name";
	public static final String CUSTOM_DATA_MESSAGE_IMG = "data";


}
