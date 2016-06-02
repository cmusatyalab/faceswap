#!/usr/bin/env python
class AppDataProtocol():
    TYPE_add_person = "add_person"
    TYPE_get_person = "get_person"
    TYPE_train = "train"
    TYPE_detect = "detect"
    TYPE_get_state = "get_state"
    TYPE_load_state = "load_state"
    TYPE_reset = "reset"
    TYPE_remove_person = "remove_person"

#    TYPE_img = "image"

class FaceRecognitionServerProtocol():
    TYPE_add_person = "ADD_PERSON"
    TYPE_set_training = "TRAINING"
    TYPE_set_state="ALL_STATE"
    TYPE_get_state="GET_STATE"
    TYPE_get_people = "GET_PEOPLE"
    TYPE_frame = "FRAME"
    TYPE_remove_person="REMOVE_PERSON"
    TYPE_get_training="GET_TRAINING"

    TYPE_add_person_resp    = "ADD_PERSON_RESP"
    TYPE_set_training_resp  = "TRAINING_RESP"
    TYPE_set_state_resp     ="ALL_STATE_RESP"
    TYPE_get_state_resp     ="GET_STATE_RESP"
    TYPE_get_people_resp    = "GET_PEOPLE_RESP"
    TYPE_frame_resp        = "FRAME_RESP"
    TYPE_remove_person_resp ="REMOVE_PERSON_RESP"
    TYPE_get_training_resp  ="GET_TRAINING_RESP"
