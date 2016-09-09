#! /usr/bin/env python
# in this file, the frames by default are rgb unless state otherwise by the variable name

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
    track_frame_id=0
    
DETECT_TRACK_RATIO = 10
PROFILE_FACE = 'profile_face'
# use a moving average here?
TRACKER_CONFIDENCE_THRESHOLD=5

class RecognitionRequestUpdate(object):
    def __init__(self, recognition_frame_id, location):
        self.recognition_frame_id = recognition_frame_id
        self.location=location

class FaceTransformation(object):
    def __init__(self):
        custom_logger_level=logging.WARNING
        if Config.DEBUG:
            custom_logger_level=logging.DEBUG
        logging.basicConfig(
                    format='%(asctime)s %(name)-12s %(levelname)-8s %(thread)d %(message)s',
                    filename='faceswap-proxy.log',
                    filemode='w+'                            
        )
        formatter = logging.Formatter('%(asctime)-15s %(levelname)-8s %(processName)s %(message)s')   
        LOG=logging.getLogger(__name__)
        LOG.setLevel(custom_logger_level)
        ch = logging.StreamHandler(sys.stdout)
        ch.setFormatter(formatter)
        LOG.addHandler(ch)
        
        self.detector = dlib.get_frontal_face_detector()
        self.need_detection=False
        self.faces=[]
        
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
        
        # controlled from faceswap-proxy.py
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
            target = self.detect,
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

        self.frame_id=0
        
    # background thread that updates self.faces once
    # detection process signaled
    def correct_tracking(self, stop_event=None):
        #TODO: should yield computing power unless there are events going on
        # right now in between frames, this loop just keeps computing forever
        in_fly_recognition_info={}
        while (not stop_event.is_set()):
            self.tracking_thread_idle_event.wait(1)
            if (not self.tracking_thread_idle_event.is_set()):
                continue
            # update detection
            if (self.correct_tracking_event.is_set()):
                try:
                    tracker_updates = self.trackers_queue.get(timeout=1)
                    faces = tracker_updates['faces']
                    LOG.debug('bg-thread received detection # {} faces'.format(len(faces)))           
                    tracker_frame = tracker_updates['frame']

                    self.faces_lock.acquire()        
                    for face in faces:
                        nearest_face = self.find_nearest_face(face, self.faces)
                        if (nearest_face):
                            face.name = nearest_face.name
                        else:
                            face.name=""
                        tracker = create_tracker(tracker_frame, face.roi, use_dlib=Config.DLIB_TRACKING)
                        face.tracker = tracker
                    self.faces = faces
                    self.faces_lock.release()                                            
                    LOG.debug('bg-thread updated self.faces # {} faces'.format(len(self.faces))) 
                    self.correct_tracking_event.clear()
                except Queue.Empty:
                    LOG.debug('bg-thread updating faces queue empty!')                    
                    pass

            # update recognition response
            try:
                update = self.recognition_queue.get_nowait()
                if (isinstance(update, RecognitionRequestUpdate)):
                    in_fly_recognition_info[update.recognition_frame_id] = update.location
                else:
                    LOG.debug('received recognition resp {}'.format(update))
                    recognition_resp = json.loads(update)
                    if (recognition_resp['type'] == FaceRecognitionServerProtocol.TYPE_frame_resp
                        and recognition_resp['success']):
                        frame_id = recognition_resp['id']
                        if (frame_id in in_fly_recognition_info):
                            # TODO: add in min distance requirement
                            self.faces_lock.acquire()
                            nearest_face = self.find_nearest_face(in_fly_recognition_info.pop(frame_id), self.faces)
                            if (nearest_face):
                                if 'name' in recognition_resp:
                                    nearest_face.name = recognition_resp['name']
                            self.faces_lock.release()
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
            # doesn't match profile faces
            if face.name == PROFILE_FACE:
                continue
                
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

    # called by recognize listener process in OpenFaceClient.py
    def on_receive_openface_server_result(self, resp, queue=None, recognition_busy_event=None):
        # parse the resp
        resp_json=json.loads(resp)
        if (resp_json['type']== FaceRecognitionServerProtocol.TYPE_frame_resp):
            if (self.training):
                LOG.error('training should use async openface response')
                return

            if (recognition_busy_event.is_set()):
                recognition_busy_event.clear()
                
            LOG.debug('received openface server response: {}'.format(resp[:40]))
            queue.put(resp)

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

    def detect(self,
                                img_queue,
                                trackers_queue,
                                recognition_queue,
                                openface_ip,
                                openface_port,
                                correct_tracking_event,
                                stop_event):

        recognition_busy_event = multiprocessing.Event()
        recognition_busy_event.clear()
        
        try:
            LOG.info('created')
            detector = dlib.get_frontal_face_detector()
            detection_process_openface_client=AsyncOpenFaceClientProcess(call_back=self.on_receive_openface_server_result, queue=recognition_queue, recognition_busy_event=recognition_busy_event)
            recognition_frame_id=0
            detection_frame_id=0
            while (not stop_event.is_set()):
                # get the last element in the queue
                try:
                    frame=img_queue.get(timeout=1)
                except Queue.Empty:
                    continue
                    
                cnt=0                    
                try:
                    while True:
                        frame = img_queue.get_nowait()
                        cnt+=1                                                
                except Queue.Empty:
                    pass
                    
                rois = detect_faces(frame, detector, upsample_num_times=Config.DLIB_DETECTOR_UPSAMPLE_TIMES, adjust_threshold=Config.DLIB_DETECTOR_ADJUST_THRESHOLD)
                if (len(rois)>0):
                    if (not recognition_busy_event.is_set() ):
                        recognition_busy_event.set()                                            
                        for roi in rois:
                            (x1,y1,x2,y2) = roi
                            face_pixels = frame[y1:y2+1, x1:x2+1]
                            face_string = np_array_to_jpeg_data_url(face_pixels)
                            detection_process_openface_client.addFrameWithID(face_string, 'detect', recognition_frame_id)
                            roi_center=((x1 + x2)/2, (y1+y2)/2)
                            recognition_queue.put(RecognitionRequestUpdate(recognition_frame_id, roi_center))
                            recognition_frame_id+=1
                            LOG.debug('recognition put in-fly requests on queues')
                    else:
                        LOG.debug('skipped sending recognition')

                if Config.DETECT_PROFILE_FACE:
                    profile_face_rois=detect_profile_faces(frame, flip=True)
                    profile_face_start_idx=len(rois)
                    LOG.debug('frontal face roi: {} profile face roi:{}'.format(rois, profile_face_rois))
                    rois.extend(profile_face_rois)
                        
                if WRITE_PICTURE_DEBUG:
                    draw_rois(frame,rois, hint="detect")
                    imwrite_rgb(self.pic_output_path(str(detection_frame_id)+'_detect'), frame)
                    
                detection_frame_id+=1
                # meanshift tracker for catching up
                trackers = create_trackers(frame, rois)
                frame_available = True
                frame_cnt = 0
                while frame_available:
                    try:
                        frame = img_queue.get_nowait()
                        hsv_frame = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
                        for tracker in trackers:
                            tracker.update(hsv_frame, is_hsv=True)
                            
                        rois=[drectangle_to_tuple(tracker.get_position()) for tracker in trackers]
                        if WRITE_PICTURE_DEBUG:                        
                            draw_rois(frame,rois)
                            imwrite_rgb(self.pic_output_path(detection_frame_id), frame)
                        detection_frame_id+=1
                        frame_cnt +=1
                    except Queue.Empty:
                        LOG.debug('catched up {} images'.format(frame_cnt))    
                        frame_available = False
                        faces=[]
                        for idx, tracker in enumerate(trackers):
                            new_roi = tracker.get_position()
                            cur_roi = (int(new_roi.left()),
                                             int(new_roi.top()),
                                             int(new_roi.right()),
                                             int(new_roi.bottom()))
                            name = ""                            
                            if Config.DETECT_PROFILE_FACE:                            
                                if idx >= profile_face_start_idx:
                                    name = PROFILE_FACE
                            face = FaceROI(cur_roi, name=name)
                            faces.append(face)

                        tracker_updates = {'frame':frame, 'faces':faces}
                        trackers_queue.put(tracker_updates)
                        LOG.debug('put detection updates onto the queue # {} faces'.format(len(faces)))
                        correct_tracking_event.set()
            # wake thread up for terminating
            correct_tracking_event.set()
        except Exception as e:
            traceback.print_exc()
            raise e

    def track_faces(self, rgb_img, faces):
        LOG.debug('# faces tracking {} '.format(len(faces)))
        to_be_removed_face = []
        is_low_confidence=False
        
        # cvtColor is an expensive operation
        # only convert frame to hsv frame once for meanshift or camshift
        if not Config.DLIB_TRACKING:
            hsv_img = cv2.cvtColor(rgb_img, cv2.COLOR_RGB2HSV)
            
        if (len(faces) == 0):
            # sleep for 10 ms
            time.sleep(0.005)
        else:
            for idx, face in enumerate(faces):
                tracker = face.tracker
                if Config.DEBUG:
                    start = time.time()
                (x1,y1,x2,y2)=face.roi
                if isinstance(tracker, meanshiftTracker) or isinstance(tracker, camshiftTracker):
                    tracker.update(hsv_img, is_hsv=True)
                else:
                    # dlib
                    guess = tracker.get_position()
                    conf=tracker.update(rgb_img, tracker.get_position())
                    if face.name != PROFILE_FACE and conf < TRACKER_CONFIDENCE_THRESHOLD:
                        LOG.debug('frontal tracker conf too low {}'.format(conf))     
                        is_low_confidence=True
                    
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
                    if Config.RETURN_FACE_DATA:
                        face.data = np.copy(rgb_img[y1:y2+1, x1:x2+1])
                faces = [face for face in faces if face not in to_be_removed_face]
        return faces, is_low_confidence

    def add_profile_faces_blur(self, bgr_img, blur_list):
        profile_faces=detect_profile_faces(bgr_img, flip=True)
        for (x1,y1,x2,y2) in profile_faces:
            LOG.debug('detect profile faces: {} {} {} {}'.format(x1,y1,x2,y2))
            profile_face=FaceROI( (int(x1), int(y1), int(x2), int(y2)), name='profile_face')
            try:
                profile_face_json = profile_face.get_json(send_data=Config.RETURN_FACE_DATA)
                blur_list.append(profile_face_json)
            except ValueError:
                pass
        
    def swap_face(self,rgb_img, bgr_img=None):
        if bgr_img == None:
            bgr_img = cv2.cvtColor(rgb_img, cv2.COLOR_RGB2BGR)
            
        if self.training:
            LOG.debug('main-process stopped openface training!')            
            self.training=False
            self.openface_client.setTraining(False)

        height, self.image_width, _=rgb_img.shape
        LOG.debug('received image. {}x{}'.format(self.image_width, height))

        # track existing faces
        self.faces_lock.acquire()                            
        self.faces, tracker_fail =self.track_faces(rgb_img, self.faces)
        self.faces_lock.release()        
        
        face_snippets = []
        for face in self.faces:
            try:
                face_json = face.get_json(send_data=Config.RETURN_FACE_DATA)
                face_snippets.append(face_json)
            except ValueError:
                pass

        if self.frame_id % 10 == 0 or tracker_fail:
            self.need_detection=True

        if self.need_detection:
            # make sure blurry images is not sent for detection
            if is_clear(bgr_img, threshold=Config.IMAGE_CLEAR_THRESHOLD):
                self.need_detection=False
                self.img_queue.put(rgb_img)
            else:
                LOG.debug('image {} too blurry. not running detection'.format(self.frame_id))
            
        LOG.debug('# faces returned: {}'.format(len(self.faces)))

        if WRITE_PICTURE_DEBUG:
            rois=[]
            for faceROI_json in face_snippets:
                faceROI_dict = json.loads(faceROI_json)
                x1 = faceROI_dict['roi_x1']
                y1 = faceROI_dict['roi_y1']
                x2 = faceROI_dict['roi_x2']
                y2 = faceROI_dict['roi_y2']
                rois.append( (x1,y1,x2,y2) )
            draw_rois(rgb_img,rois)
            imwrite_rgb(self.pic_output_path(str(self.frame_id)+'_track'), rgb_img)

        self.frame_id +=1
        
        return face_snippets

    def addPerson(self, name):
        return self.openface_client.addPerson(name)

    # frame is a numpy array
    def train(self, rgb_frame, name):
        # change training to true
        if self.training == False:
            self.training = True
            self.training_cnt = 0
            self.openface_client.setTraining(True)

        # detect the largest face
        rois = detect_faces(rgb_frame, self.detector, largest_only=True)

        # only the largest face counts
        if (len(rois) > 1):
            LOG.info("more than 1 faces detected in training frame. abandon frame")
            return self.training_cnt, None

        if (len(rois) == 0):
            LOG.debug("No faces detected in training frame. abandon frame")
            return self.training_cnt, None

        LOG.info("training-adding frame: detected 1 face")            

        if 1 == len(rois) :
            (x1,y1,x2,y2) = rois[0]
            face_pixels = np.copy(rgb_frame[y1:y2+1, x1:x2+1]) 

        face = FaceROI(rois[0], data=face_pixels, name="training")            
        face_string = np_array_to_jpeg_data_url(face_pixels)
        
        resp = self.openface_client.addFrame(face_string, name)
        resp = json.loads(resp)
        success = resp['success']
        if success:
            self.training_cnt +=1

        return self.training_cnt, face.get_json()
