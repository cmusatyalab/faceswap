#! /usr/bin/env python
import Queue
import StringIO
import base64
import json
import logging
import multiprocessing
import sys
import threading
import time
from operator import itemgetter

import cv2
import dlib
import numpy as np
from PIL import Image

from camShift import camshiftTracker, meanshiftTracker
from NetworkProtocol import *
from openfaceClient import OpenFaceClient, AsyncOpenFaceClientProcess

MIN_WIDTH_THRESHOLD=3
MIN_HEIGHT_THRESHOLD=3
DETECT_TRACK_RATIO = 10

DEBUG = True

class TrackerInitializer(object):
    def __init__(self, prev_frame, prev_roi, frame):
        self.prev_frame=prev_frame
        self.prev_roi = prev_roi
        self.frame = frame
    
def create_dlib_tracker(frame, roi):
    tracker = dlib.correlation_tracker()
    (roi_x1, roi_y1, roi_x2, roi_y2) = roi
    tracker.start_track(frame,
                        dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2))
    return tracker

def create_tracker(frame, roi):
#    tracker = camShift.camshiftTracker()
    # using mean shift for now
    tracker = meanshiftTracker()
    (roi_x1, roi_y1, roi_x2, roi_y2) = roi
    tracker.start_track(frame,
                        dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2))
    return tracker

def create_trackers(frame, rois, dlib=False):
    trackers = []
    for roi in rois:
        if (dlib):
            tracker = create_dlib_tracker(frame,roi)            
        else:
            tracker = create_tracker(frame,roi)
        trackers.append(tracker)
    return trackers
    
def drectangle_to_tuple(drectangle):
    cur_roi = (int(drectangle.left()),
                     int(drectangle.top()),
                     int(drectangle.right()),
                     int(drectangle.bottom()))
    return cur_roi

def euclidean_distance_square(roi1, roi2):
    result = abs(roi1[0] - roi2[0])**2 + abs(roi1[1] - roi2[1])**2
    return result

def np_array_to_jpeg_string(frame):
    face_img = Image.fromarray(frame)                        
    sio = StringIO.StringIO()
    face_img.save(sio, 'JPEG')
    jpeg_img = sio.getvalue()
    face_string = base64.b64encode(jpeg_img)
    return face_string

def np_array_to_string(frame):
    face_string = base64.b64encode(frame)
    return face_string
    
class FaceROI(object):
    def __init__(self, roi, data=None, name=None, tracker=None):
        self.roi = roi
        self.data = data
        self.name = name
        self.tracker = tracker
        self.swap_tmp_data=None

    def get_json(self, send_data=False):
        (roi_x1, roi_y1, roi_x2, roi_y2) = self.roi        
        msg = {
            'roi_x1':roi_x1,
            'roi_y1':roi_y1,
            'roi_x2':roi_x2,
            'roi_y2':roi_y2,
            'name':self.name
            }
        if send_data:
            msg['data'] = np_array_to_jpeg_string(self.data)
        return json.dumps(msg)

    # return the center location of the face
    def get_location(self):
        (roi_x1, roi_y1, roi_x2, roi_y2) = self.roi
        return ((roi_x1 + roi_x2)/2, (roi_y1+roi_y2)/2)

class RecognitionRequestUpdate(object):
    def __init__(self, recognition_frame_id, location):
        self.recognition_frame_id = recognition_frame_id
        self.location=location

class FaceTransformation(object):
    def __init__(self):
        logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s %(name)-12s %(levelname)-8s %(thread)d %(message)s',
                    filename='faceswap-proxy.log',
                    filemode='w+'                            
        )
        formatter = logging.Formatter('%(asctime)-15s %(levelname)-8s %(processName)s %(message)s')   
        self.logger=logging.getLogger(__name__)
#        self.logger.setLevel(logging.INFO)
        self.logger.setLevel(logging.INFO)        
        ch = logging.StreamHandler(sys.stdout)
        ch.setFormatter(formatter)
        self.logger.addHandler(ch)
        
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
        self.server_ip = u"ws://128.2.211.75"
        self.server_port = 9000
        # changed to two openface_client
        # 1 sync for blocking response
        # another for non-blocking response in detection_process
        self.openface_client = OpenFaceClient(self.server_ip, self.server_port)
        resp = self.openface_client.isTraining()
        self.logger.info('resp: {}'.format(resp))
        self.training = json.loads(resp)['val']
        self.logger.info('openface is training?{}'.format(self.training))

        mpl = multiprocessing.log_to_stderr()
        mpl.setLevel(logging.INFO)        
        
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
            target = self.detection_update_thread,
            name='DetectionProcess',
            args=(self.img_queue,
                  self.trackers_queue,
                  self.recognition_queue,
                  self.server_ip,
                  self.server_port,
                  self.correct_tracking_event,
                  self.detection_process_stop_event,))
        self.detection_process.start()

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
                self.logger.debug('bg-thread getting detection updates')
                try:
                    tracker_updates = self.trackers_queue.get(timeout=1)
                    faces = tracker_updates['faces']
                    tracker_frame = tracker_updates['frame']
                    for face in faces:
                        nearest_face = self.find_nearest_face(face, self.faces)
                        if (nearest_face):
                            face.name = nearest_face.name
                        tracker = create_tracker(tracker_frame, face.roi)
                        face.tracker = tracker

                    self.logger.debug('bg-thread updating faces from detection process!')
                    self.faces_lock.acquire()
                    self.faces = faces
                    self.faces_lock.release()
                    self.correct_tracking_event.clear()
                except Queue.Empty:
                    self.logger.info('bg-thread updating faces queue empty!')
            else:
                try:
                    update = self.recognition_queue.get_nowait()
                    if (isinstance(update, RecognitionRequestUpdate)):
                        in_fly_recognition_info[update.recognition_frame_id] = update.location
                    else:
#                        self.logger.info('main process received recognition resp {}'.format(update))
                        recognition_resp = json.loads(update)
                        if (recognition_resp['type'] == FaceRecognitionServerProtocol.TYPE_frame_resp
                            and recognition_resp['success']):
                            frame_id = recognition_resp['id']
                            if (frame_id in in_fly_recognition_info):
                                # TODO: add in min distance requirement
                                self.faces_lock.acquire()
                                nearest_face = self.find_nearest_face(in_fly_recognition_info.pop(frame_id),
                                                                      self.faces)
                                if (nearest_face):
                                    nearest_face.name = recognition_resp['name']
                                self.faces_lock.release()
                                self.logger.debug('main process received recognition name {}'
                                    .format(recognition_resp['name']))
                            else:
                                self.logger.error('received response but no frame info about the request')
                        else:
                            self.logger.error('received response is not frame_resp or frame_resp success is false')
                except Queue.Empty:
                    pass


    def terminate(self):
        self.detection_process_stop_event.set()
        self.sync_thread_stop_event.set()
        self.detection_process.join()
        self.logger.info('detection process shutdown!')        
        self.sync_faces_thread.join()
        self.logger.info('sync faces thread shutdown!')                
        self.openface_client.terminate()        
        self.logger.debug('transformer terminate!')

    def np_array_to_jpeg_data_url(self, frame):
        face_string = np_array_to_jpeg_string(frame)
        face_string = "data:image/jpeg;base64," + face_string
        return face_string
        
    def recognize_faces(self, openface_client, frame, rois):
        names =[]
        for roi in rois:
            (x1,y1,x2,y2) = roi
            face_pixels = np.copy(frame[y1:y2+1, x1:x2+1]) 
            face_string = self.np_array_to_jpeg_data_url(face_pixels)
            # has to use the same client as mainprocess
            resp = openface_client.addFrame(face_string, 'detect')
            self.logger.debug('server response: {}'.format(resp))            
            resp_dict = json.loads(resp)
            name = resp_dict['name']
            self.logger.debug('recognize: {}'.format(name))
            names.append(name)
        return names


# while (frame_cnt < 10):
#     try:                
#         frame = img_queue.get(timeout=1)
#         self.update_trackers(trackers, frame)
#         frame_cnt +=1
#     except Queue.Empty:
#         pass
# faces=[]
# for idx, tracker in enumerate(trackers):
#     new_roi = tracker.get_position()
#     cur_roi = (int(new_roi.left()),
#                      int(new_roi.top()),
#                      int(new_roi.right()),
#                      int(new_roi.bottom()))
#     name = names[idx]                        
#     self.logger.debug('recognized faces {0} {1}'.format(idx, name))
#     face = FaceROI(cur_roi, name=name)
#     faces.append(face)
# if (len(faces)>0):
#     tracker_updates = {'frame':frame, 'faces':faces}
#     trackers_queue.put(tracker_updates)
#     sync_face_event.set()

    def find_nearest_face(self, src, nearby_faces):
        distances = []
        # find the closest face object
        for face in nearby_faces:
            face_center = face.get_location()
            if (isinstance(src, FaceROI)):
                src_center = src.get_location()
            else:
                src_center=src
            distance = euclidean_distance_square(face_center, src_center)
            distances.append(distance)
        if distances:
            (face_idx, _) = min(enumerate(distances), key=itemgetter(1))
            return nearby_faces[face_idx]
        else:
            return None

    # TODO: to be finished!!!
    # called in another recognize listener process (listener)
    def on_receive_openface_server_result(self, resp, queue=None):
        # parse the resp
        resp_json=json.loads(resp)
        if (resp_json['type']== FaceRecognitionServerProtocol.TYPE_frame_resp):
            if (self.training):
                self.logger.error('training is using async openface response')
                return
            else:
                self.logger.debug('server response: {}'.format(resp))
                queue.put(resp)

                # resp_dict = json.loads(resp)
                # success=resp_dict['success']
                # if success:
                #     name = resp_dict['name']
                #     self.logger.debug('recognized: {}'.format(name))
                #     # notify main process
                #     if queue:



    def send_face_recognition_requests(self, openface_client, frame, rois, frame_id):
        for roi in rois:
            (x1,y1,x2,y2) = roi
            # TODO: really need copy here?
            face_pixels = np.copy(frame[y1:y2+1, x1:x2+1])
            face_string = self.np_array_to_jpeg_data_url(face_pixels)
            # has to use the same client as mainprocess
            openface_client.addFrameWithID(face_string, 'detect', frame_id)
            frame_id+=1
        return frame_id

    # send recognition request
    def detection_update_thread(self,
                                img_queue,
                                trackers_queue,
                                recognition_queue,
                                openface_ip,
                                openface_port,
                                sync_face_event,
                                stop_event):
        self.logger.info('created')
        detector = dlib.get_frontal_face_detector()
        detection_process_openface_client=AsyncOpenFaceClientProcess(call_back=self.on_receive_openface_server_result,
                                                                     queue=recognition_queue)
        # create face recognition thread to be in background
#        face_recognition_thread = threading.Thread(target=self.bg_face_recognition, name='bg_recognition_thread')

        recognition_frame_id=0
        while (not stop_event.is_set()):
            try:
                frame = img_queue.get(timeout=1)
            except Queue.Empty:                
                continue
            rois = self.detect_faces(frame, detector)
            for roi in rois:
                (x1,y1,x2,y2) = roi
                # TODO: really need copy here?
                face_pixels = np.copy(frame[y1:y2+1, x1:x2+1])
                face_string = self.np_array_to_jpeg_data_url(face_pixels)
                # has to use the same client as mainprocess
                self.logger.debug('before sending out recognition request')
                detection_process_openface_client.addFrameWithID(face_string, 'detect', recognition_frame_id)
                roi_center=((x1 + x2)/2, (y1+y2)/2)
                self.logger.debug('after sending out recognition request')
                recognition_queue.put(RecognitionRequestUpdate(recognition_frame_id, roi_center))
                recognition_frame_id+=1
                self.logger.debug('after putting updates on queues')

            # recognition_frame_id=self.send_face_recognition_requests(
            #     detection_process_openface_client,
            #     frame,
            #     rois,
            #     recognition_frame_id)
#            names = self.recognize_faces(None, frame, rois)
#            self.logger.info('detect and recognized {}'.format(names))
            # dlib tracker cannot catch up with the image detected
#            trackers = create_trackers(frame, rois, dlib=True)
            trackers = create_trackers(frame, rois)
            frame_available = True
            frame_cnt = 0

            while frame_available:
                try:
                    frame = img_queue.get_nowait()
                    self.update_trackers(trackers, frame)
                    self.logger.debug('catched up # {}'.format(frame_cnt))
                    frame_cnt +=1
                except Queue.Empty:
                    self.logger.debug('all image catched up! # images {}'.format(frame_cnt))    
                    frame_available = False
                    faces=[]
                    for idx, tracker in enumerate(trackers):
                        new_roi = tracker.get_position()
                        cur_roi = (int(new_roi.left()),
                                         int(new_roi.top()),
                                         int(new_roi.right()),
                                         int(new_roi.bottom()))
#                        name = names[idx]
                        name = ""
#                        self.logger.info('recognized faces {0} {1}'.format(idx, name))
                        face = FaceROI(cur_roi, name=name)
                        faces.append(face)

                    tracker_updates = {'frame':frame, 'faces':faces}
                    trackers_queue.put(tracker_updates)
                    sync_face_event.set()

        # wake thread up for terminating
        sync_face_event.set()
                    
    def update_trackers(self, trackers, frame):
        for idx, tracker in enumerate(trackers):
            tracker.update(frame)
            
    def track_faces(self, frame, faces):
        self.logger.debug('# faces tracking {} '.format(len(faces)))
        to_be_removed_face = []
        grey_frame = frame
        # cvtColor is an expensive operation 
        grey_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
        if (len(faces) == 0):
            # sleep for 10 ms
            time.sleep(0.005)
        else:
            for idx, face in enumerate(faces):
                tracker = face.tracker
                
                if DEBUG:
                    start = time.time()

                (x1,y1,x2,y2)=face.roi
                tracker.update(grey_frame, converted=True)
                new_roi = tracker.get_position()

                if DEBUG:
                    end = time.time()
                    self.logger.debug('tracker run: {}'.format((end-start)*1000))

                (x1,y1,x2,y2) = (int(new_roi.left()),
                              int(new_roi.top()),
                              int(new_roi.right()),
                              int(new_roi.bottom()))
                face.roi = (x1,y1,x2,y2)
                if (self.is_small_face(face.roi)):
                    to_be_removed_face.append(face)
                else:
#                    face.data = np.copy(frame[y1:y2+1, x1:x2+1])
                    face.data = frame[y1:y2+1, x1:x2+1]
                faces = [face for face in faces if face not in to_be_removed_face]
        return faces
        
    def get_large_faces(self, rois, trackers):
        # find small faces
        small_face_idx=self.find_small_face_idx(rois)
        large_rois = rois
        large_rois_trackers = trackers
        if ( len(small_face_idx) > 0):
            large_rois = [ rois[i] for i in xrange(len(rois)) if i not in set(small_face_idx) ]
            large_rois_trackers = [ trackers[i] for i in xrange(len(trackers)) if i not in set(small_face_idx) ]
        return large_rois, large_rois_trackers
        
    def swap_face(self,frame):
        # change training to true
        if self.training:
            self.logger.debug('main-process stopped openface training!')            
            self.training=False
            self.openface_client.setTraining(False)

        # forward img to DetectionProcess
        # preprocessing to grey scale can reduce run time for detection process only handle greyscale
        # openface need rgb images
        grey_frame = frame
#        grey_frame = cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY)

        faces=self.track_faces(frame, self.faces)
        self.faces_lock.acquire()                    
        self.faces=faces
        self.faces_lock.release()        
        return_faces = self.faces
        
        face_snippets = []
        for face in return_faces:
            try:
                face_json = face.get_json()
                face_snippets.append(face_json)
            except ValueError:
                pass

        self.img_queue.put(grey_frame)                
        self.logger.debug('# faces in image: {}'.format(len(self.faces)))
        return face_snippets

    def get_FaceROI_from_string(self, faces, name):
        for face in faces:
            if face.name == name:
                return face
        return None
    
    # shuffle faces
    def shuffle_roi(self, faces, face_table):
        to_people = []
        for idx, face in enumerate(faces):
            if face.name in face_table:
                (roi_x1, roi_y1, roi_x2, roi_y2) = face.roi
                to_person = self.get_FaceROI_from_string(faces, face_table[face.name])
                if (None == to_person):
                    continue
                to_people.append(to_person)
                nxt_face = to_person.data
                cur_face = face.data
                dim = (cur_face.shape[1],cur_face.shape[0])
                try:
                    nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
                except:
                    print 'error: face resize failed!'
                face.swap_tmp_data=nxt_face_resized

        to_people = set(to_people)
        faces = [face for face in faces if face not in to_people]        
#        faces = [face in faces if face in face_table and face not in to_people]
#        faces.extend([ face in faces if face in face_table and face in to_people])
        for face in faces:
            if None != face.swap_tmp_data:
                face.data = face.swap_tmp_data
        return faces


    def is_small_face(self, roi):
        (x1,y1,x2,y2) = roi
        # has negative number
        if ( x1<0 or y1<0 or x2<0 or y2<0):
            return True
        
        # region too small
        if ( abs(x2-x1) < MIN_WIDTH_THRESHOLD or abs(y2-y1) < MIN_HEIGHT_THRESHOLD):
            return True

        
        
    def find_small_face_idx(self, dets):
        idx=[]
        for i, d in enumerate(dets):
            if ( self.is_small_face(d) ):
                idx.append(i)
        return idx

    def rm_small_face(self, dets):
        filtered_dets=[]
        for i, d in enumerate(dets):
            if not self.is_small_face(d):
                filtered_dets.append(d)
        return filtered_dets
        
    def detect_faces(self, frame, detector, largest_only=False):
        if DEBUG:
            start = time.time()
        # upsampling will take a lot of time
        dets = detector(frame)
        if DEBUG:
            end = time.time()
            self.logger.debug('detector run: {}'.format((end-start)*1000))

        if largest_only:
            if (len(dets) > 0):
                max_det = max(dets, key=lambda rect: rect.width() * rect.height())
                dets = [max_det]
            
        dets=map(lambda d: (int(d.left()), int(d.top()), int(d.right()), int(d.bottom())), dets)
        rois=self.rm_small_face(dets)
        
        self.logger.debug('# detected : {}'.format(len(rois)))        
        rois=sorted(rois)
        return rois

        

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
        rois = self.detect_faces(frame, self.detector, largest_only=True)

        # only the largest face counts
        if (len(rois) > 1):
            self.logger.info("more than 1 faces detected in training frame. abandon frame")
            return self.training_cnt, None

        if (len(rois) == 0):
            self.logger.info("No faces detected in training frame. abandon frame")
            return self.training_cnt, None

        self.logger.debug("training: sucesss - detected 1 face. add frame")            

        if 1 == len(rois) :
            (x1,y1,x2,y2) = rois[0]
            face_pixels = np.copy(frame[y1:y2+1, x1:x2+1]) 

        face = FaceROI(rois[0], data=face_pixels, name="training")            
        face_string = self.np_array_to_jpeg_data_url(face_pixels)
        
        # face_img = Image.fromarray(face)                        
        # sio = StringIO.StringIO()
        # face_img.save(sio, 'JPEG')
        # jpeg_img = sio.getvalue()

        # if DEBUG:
        #     face_img.save('training.jpg')            
        
        # face_string = base64.b64encode(jpeg_img)
        # face_string = "data:image/jpeg;base64," + face_string
            
        resp = self.openface_client.addFrame(face_string, name)
        resp = json.loads(resp)
        success = resp['success']
        if success:
            self.training_cnt +=1

        return self.training_cnt, face.get_json()

