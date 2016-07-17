#! /usr/bin/env python
# in this file, the input frame is in rgb format

from MyUtils import *
from vision import *
import Queue
import StringIO
import json
import logging
import multiprocessing
import sys
import os
import threading
import time
from operator import itemgetter
import cv2
import dlib
import numpy as np
from PIL import Image
import traceback
from NetworkProtocol import *
from openfaceClient import OpenFaceClient, AsyncOpenFaceClientProcess
from demo_config import Config

WRITE_PICTURE_DEBUG=Config.WRITE_PICTURE_DEBUG
if WRITE_PICTURE_DEBUG:
    remove_dir(Config.WRITE_PICTURE_DEBUG_PATH)
    create_dir(Config.WRITE_PICTURE_DEBUG_PATH)
DETECT_TRACK_RATIO = 10

class RecognitionRequestUpdate(object):
    def __init__(self, recognition_frame_id, location):
        self.recognition_frame_id = recognition_frame_id
        self.location=location

class FaceTransformation(object):
    def __init__(self):
        self.cnt=0
        self.detector = dlib.get_frontal_face_detector()
        self.faces=[]
        self.face_table = {}
        
        self.faces_lock=threading.Lock()
        self.img_queue = multiprocessing.Queue()
        self.trackers_queue = multiprocessing.Queue()
        self.recognition_queue = multiprocessing.Queue()
        
        # openface related
        self.training_cnt = 0
        self.server_ip = u"ws://localhost"
        self.server_port = 9000
        # changed to two openface_client
        # 1 sync for blocking response
        # another for non-blocking response in detection_process
        self.openface_client = OpenFaceClient(self.server_ip, self.server_port)
        resp = self.openface_client.isTraining()
        LOG.info('resp: {}'.format(resp))
        self.training = json.loads(resp)['val']
        LOG.info('openface is training?{}'.format(self.training))

        mpl = multiprocessing.log_to_stderr()
        mpl.setLevel(custom_logger_level)
        
        self.correct_tracking_event = multiprocessing.Event()
        self.correct_tracking_event.clear()
        self.tracking_thread_idle_event = threading.Event()
        self.tracking_thread_idle_event.clear()
        self.sync_thread_stop_event=threading.Event()
        self.sync_thread_stop_event.clear()
        self.sync_faces_thread = threading.Thread(target=self.correct_tracking,
                                                  name='bgThread',
                                                  kwargs={'stop_event' : self.sync_thread_stop_event})
        self.sync_faces_thread.start()

        self.detection_process_shared_face_fragments=[]
        self.detection_process_stop_event = multiprocessing.Event()
        self.detection_process_stop_event.clear()
        self.detection_process = multiprocessing.Process(
            target = self.detection_and_recognition,
            name='DetectionProcess',
            args=(self.img_queue,
                  self.trackers_queue,
                  self.recognition_queue,
                  self.server_ip,
                  self.server_port,
                  self.correct_tracking_event,
                  self.detection_process_stop_event,))
        self.detection_process.start()
        self.image_width=Config.MAX_IMAGE_WIDTH

    # background thread that updates self.faces once
    # detection process signaled
    def correct_tracking(self, stop_event=None):
        #TODO: should yield computing power unless there are events going on
        # right now in between frames, this loop just keeps computing forever
        in_fly_recognition_info={}
        while (not stop_event.is_set()):
            self.tracking_thread_idle_event.wait(1)
#            self.sync_face_event.wait(0.1)
            if (not self.tracking_thread_idle_event.is_set()):
                continue
            if (self.correct_tracking_event.is_set()):
                LOG.debug('bg-thread getting detection updates')
                try:
                    tracker_updates = self.trackers_queue.get(timeout=1)
                    faces = tracker_updates['faces']
                    tracker_frame = tracker_updates['frame']
                    for face in faces:
                        nearest_face = self.find_nearest_face(face, self.faces)
#                                                              max_distance=self.image_width*Config.FACE_MAX_DRIFT_PERCENT)
                        if (nearest_face):
                            face.name = nearest_face.name
                        else:
                            face.name=""
                        tracker = create_tracker(tracker_frame, face.roi)
                        face.tracker = tracker

                    LOG.debug('bg-thread updating faces from detection process!')
                    self.faces_lock.acquire()
                    self.faces = faces
                    self.faces_lock.release()
                    self.correct_tracking_event.clear()
                except Queue.Empty:
                    LOG.info('bg-thread updating faces queue empty!')
            else:
                try:
                    update = self.recognition_queue.get_nowait()
                    if (isinstance(update, RecognitionRequestUpdate)):
                        in_fly_recognition_info[update.recognition_frame_id] = update.location
                    else:
                        LOG.debug('main process received recognition resp {}'.format(update))
                        recognition_resp = json.loads(update)
                        if (recognition_resp['type'] == FaceRecognitionServerProtocol.TYPE_frame_resp
                            and recognition_resp['success']):
                            frame_id = recognition_resp['id']
                            if (frame_id in in_fly_recognition_info):
                                # TODO: add in min distance requirement
                                self.faces_lock.acquire()
                                nearest_face = self.find_nearest_face(in_fly_recognition_info.pop(frame_id), self.faces)
#                , max_distance=self.image_width*Config.FACE_MAX_DRIFT_PERCENT)
                                if (nearest_face):
                                    nearest_face.name = recognition_resp['name']
                                self.faces_lock.release()
                                LOG.debug('main process received recognition name {}'
                                    .format(recognition_resp['name']))
                            else:
                                LOG.error('received response but no frame info about the request')
                        else:
                            LOG.error('received response is not frame_resp or frame_resp success is false')
                except Queue.Empty:
                    pass


    def terminate(self):
        self.detection_process_stop_event.set()
        self.sync_thread_stop_event.set()
        self.detection_process.join()
        LOG.info('detection process shutdown!')        
        self.sync_faces_thread.join()
        LOG.info('sync faces thread shutdown!')                
        self.openface_client.terminate()        
        LOG.debug('transformer terminate!')


    def find_nearest_face(self, src, nearby_faces, max_distance=None):
        distances = []
        # find the closest face object
        for face in nearby_faces:
            face_center = face.get_location()
            if (isinstance(src, FaceROI)):
                src_center = src.get_location()
            else:
                src_center=src
            distance = euclidean_distance_square(face_center, src_center)
            if max_distance is not None:
                if distance <= max_distance:
                    distances.append(distance)
                else:
                    LOG.info('drift too much. do not update recognition result')         
            else:
                distances.append(distance)                
        if distances:
            (face_idx, _) = min(enumerate(distances), key=itemgetter(1))
            return nearby_faces[face_idx]
        else:
            return None

    # TODO: to be finished!!!
    # called in another recognize listener process (listener)
    def on_receive_openface_server_result(self, resp, queue=None, busy_event=None):
        # parse the resp
        resp_json=json.loads(resp)
        if (resp_json['type']== FaceRecognitionServerProtocol.TYPE_frame_resp):
            if (self.training):
                LOG.error('training is using async openface response')
                return

            if (busy_event.is_set()):
                LOG.debug('cleared recognition busy')
                busy_event.clear()
            else:
                LOG.debug('server busy event not set')
                
            LOG.debug('server response: {}'.format(resp[:40]))
            queue.put(resp)

                # resp_dict = json.loads(resp)
                # success=resp_dict['success']
                # if success:
                #     name = resp_dict['name']
                #     LOG.debug('recognized: {}'.format(name))
                #     # notify main process
                #     if queue:


    def send_face_recognition_requests(self, openface_client, frame, rois, frame_id):
        for roi in rois:
            (x1,y1,x2,y2) = roi
            # TODO: really need copy here?
            face_pixels = np.copy(frame[y1:y2+1, x1:x2+1])
            face_string = np_array_to_jpeg_data_url(face_pixels)
            # has to use the same client as mainprocess
            openface_client.addFrameWithID(face_string, 'detect', frame_id)
            frame_id+=1
        return frame_id

    def pic_output_path(self,idx):
        return os.path.normpath(Config.WRITE_PICTURE_DEBUG_PATH+'/'+ str(idx)+'.jpg')

    def detection_and_recognition(self,
                  img_queue,
                  trackers_queue,
                  recognition_queue,
                  openface_ip,
                  openface_port,
                  sync_face_event,
                  stop_event):

        recognition_busy_event = multiprocessing.Event()
        recognition_busy_event.clear()
        
        try:
            LOG.info('created')
            detector = dlib.get_frontal_face_detector()
            detection_process_openface_client=AsyncOpenFaceClientProcess(call_back=self.on_receive_openface_server_result, queue=recognition_queue, busy_event=recognition_busy_event)
            # create face recognition thread to be in background
    #        face_recognition_thread = threading.Thread(target=self.bg_face_recognition, name='bg_recognition_thread')

            recognition_frame_id=0
            frame_id=0
            while (not stop_event.is_set()):
                try:
                    frame = img_queue.get(timeout=1)
                except Queue.Empty:                
                    continue

                rois = detect_faces(frame, detector)
                rois = filter_small_faces(rois)
                LOG.debug('finished detecting')
                if (len(rois)>0):
                    if (not recognition_busy_event.is_set() ):
                        recognition_busy_event.set()                                            
                        for roi in rois:
                            (x1,y1,x2,y2) = roi
                            # TODO: really need copy here?
        #                    face_pixels = np.copy(frame[y1:y2+1, x1:x2+1])
                            face_pixels = frame[y1:y2+1, x1:x2+1]
                            face_string = np_array_to_jpeg_data_url(face_pixels)
                            # has to use the same client as mainprocess
                            detection_process_openface_client.addFrameWithID(face_string, 'detect', recognition_frame_id)
                            LOG.debug('send out recognition request')                    
                            roi_center=((x1 + x2)/2, (y1+y2)/2)
                            recognition_queue.put(RecognitionRequestUpdate(recognition_frame_id, roi_center))
                            recognition_frame_id+=1
                            LOG.debug('after putting updates on queues')
                    else:
                        LOG.debug('skipped sending recognition')
                        
                if WRITE_PICTURE_DEBUG:
                    draw_rois(frame,rois)
                    imwrite_rgb(self.pic_output_path(str(frame_id)+'_detect'), frame)
                    
                frame_id+=1

                trackers = create_trackers(frame, rois)
                frame_available = True
                frame_cnt = 0
                while frame_available:
                    try:
                        frame = img_queue.get_nowait()
                        update_trackers(trackers, frame)

                        rois=[drectangle_to_tuple(tracker.get_position()) for tracker in trackers]

                        if WRITE_PICTURE_DEBUG:                        
                            draw_rois(frame,rois)
                            imwrite_rgb(self.pic_output_path(frame_id), frame)
                        frame_id+=1
                        frame_cnt +=1
                    except Queue.Empty:
                        LOG.debug('all image catched up! # images {}'.format(frame_cnt))    
                        frame_available = False
                        faces=[]
                        for idx, tracker in enumerate(trackers):
                            new_roi = tracker.get_position()
                            cur_roi = (int(new_roi.left()),
                                             int(new_roi.top()),
                                             int(new_roi.right()),
                                             int(new_roi.bottom()))
                            name = ""
    #                        LOG.info('recognized faces {0} {1}'.format(idx, name))
                            face = FaceROI(cur_roi, name=name)
                            faces.append(face)

                        tracker_updates = {'frame':frame, 'faces':faces}
                        trackers_queue.put(tracker_updates)
                        sync_face_event.set()
            # wake thread up for terminating
            sync_face_event.set()
        except Exception as e:
            traceback.print_exc()
            raise e
            
    def track_faces(self, frame, faces):
        LOG.debug('# faces tracking {} '.format(len(faces)))
        to_be_removed_face = []
        # cvtColor is an expensive operation 
        hsv_frame = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
        if (len(faces) == 0):
            # sleep for 10 ms
            time.sleep(0.005)
        else:
            for idx, face in enumerate(faces):
                tracker = face.tracker
                
                if Config.DEBUG:
                    start = time.time()

                (x1,y1,x2,y2)=face.roi
                tracker.update(hsv_frame, is_hsv=True)
                new_roi = tracker.get_position()

                if Config.DEBUG:
                    end = time.time()
                    LOG.debug('tracker run: {}'.format((end-start)*1000))

                (x1,y1,x2,y2) = (int(new_roi.left()),
                              int(new_roi.top()),
                              int(new_roi.right()),
                              int(new_roi.bottom()))
                face.roi = (x1,y1,x2,y2)
                if (is_small_face(face.roi)):
                    to_be_removed_face.append(face)
                else:
                    face.data = np.copy(frame[y1:y2+1, x1:x2+1])
                faces = [face for face in faces if face not in to_be_removed_face]
        return faces
        
    def swap_face(self,frame):
        # change training to true
        if self.training:
            LOG.debug('main-process stopped openface training!')            
            self.training=False
            self.openface_client.setTraining(False)

        self.image_width=frame.shape[1]
        LOG.debug('image width: {}'.format(self.image_width))
        # forward img to DetectionProcess
        # preprocessing to grey scale can reduce run time for detection process only handle greyscale
        # openface need rgb images
        grey_frame = frame
#        grey_frame = cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY)

        faces=self.track_faces(frame, self.faces)
        self.faces_lock.acquire()                    
        self.faces=faces
        self.faces_lock.release()        
        
        ret = []
        for face in self.faces:
            try:
                face_json = face.get_json(send_data=True)
                ret.append(face_json)
            except ValueError:
                pass

        self.img_queue.put(grey_frame)                
        LOG.debug('# faces in image: {}'.format(len(self.faces)))
        return ret
        
    def addPerson(self, name):
        return self.openface_client.addPerson(name)

    # frame is a numpy array
    def train(self, frame, name):
        # change training to true
        if self.training == False:
            self.training = True
            self.training_cnt = 0
            self.openface_client.setTraining(True)

        # detect the largest face
        rois = detect_faces(frame, self.detector, largest_only=True)

        # only the largest face counts
        if (len(rois) > 1):
            LOG.info("more than 1 faces detected in training frame. abandon frame")
            return self.training_cnt, None

        if (len(rois) == 0):
            LOG.debug("No faces detected in training frame. abandon frame")
            return self.training_cnt, None

        LOG.info("training: sucesss - detected 1 face. add frame")            

        if 1 == len(rois) :
            (x1,y1,x2,y2) = rois[0]
            face_pixels = np.copy(frame[y1:y2+1, x1:x2+1]) 

        face = FaceROI(rois[0], data=face_pixels, name="training")            
        face_string = np_array_to_jpeg_data_url(face_pixels)
        
        resp = self.openface_client.addFrame(face_string, name)
        resp = json.loads(resp)
        success = resp['success']
        if success:
            self.training_cnt +=1

        return self.training_cnt, face.get_json()
