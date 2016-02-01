import numpy as np
import cv2
import dlib
import sys
import pdb
import numpy as np
import time
import threading
import Queue
import logging
import multiprocessing

import itertools
import os
import time
from PIL import Image, ImageOps
import io, StringIO
import base64
import numpy as np
import json

from openfaceClient import OpenFaceClient
import pdb

MIN_WIDTH_THRESHOLD=3
MIN_HEIGHT_THRESHOLD=3
RATIO_DETECT_TRACK = 10

DEBUG = True

class TrackerInitializer(object):
    def __init__(self, prev_frame, prev_roi, frame):
        self.prev_frame=prev_frame
        self.prev_roi = prev_roi
        self.frame = frame
    
def create_and_track_star(a_b):
    """Convert `f([1,2])` to `f(1,2)` call."""
    return create_and_track(*a_b)

def create_trackers_parallel(tracker_input):
    print "create_trackers. process id : {}".format(os.getpid())
    frame=tracker_input.prev_frame
    roi = tracker_input.prev_roi
    tracker = create_tracker(frame,roi)
    sys.stdout.flush()        
    return tracker
    
def create_tracker(frame, roi):
    tracker = dlib.correlation_tracker()
    (roi_x1, roi_y1, roi_x2, roi_y2) = roi
#    print 'workerpool: start tracking! # face: {}'.format(roi)
    tracker.start_track(frame,
                        dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2))
    return tracker

def create_trackers(frame, rois):
#    self.logger.debug("detection_process:")
    trackers = []
    for roi in rois:
        tracker = create_tracker(frame,roi)
        trackers.append(tracker)
    return trackers
    
def drectangle_to_tuple(drectangle):
    cur_roi = (int(drectangle.left()),
                     int(drectangle.top()),
                     int(drectangle.right()),
                     int(drectangle.bottom()))
    return cur_roi
    
def create_and_track(tracker_input):
    if DEBUG:
        start = time.time()

    prev_frame = tracker_input.prev_frame
    prev_roi = tracker_input.prev_roi
    frame = tracker_input.frame    
    tracker=create_tracker(prev_frame, prev_roi)
    tracker.update(frame)
    new_roi = drectangle_to_tuple(tracker.get_position())
    
    if DEBUG:
        end = time.time()
        print 'workerpool: process id: {} running time: {}'.format(os.getpid(),
                                                                   (end-start)*1000)        

    sys.stdout.flush()
    
    return new_roi

def x_square(x):
    print 'worker pool test called'
    return x**2

    
def np_array_to_jpeg_string(frame):
    face_img = Image.fromarray(frame)                        
    sio = StringIO.StringIO()
    face_img.save(sio, 'JPEG')
    jpeg_img = sio.getvalue()
    face_string = base64.b64encode(jpeg_img)
    return face_string

    
class FaceROI(object):
    def __init__(self, roi, data=None, name=None, tracker=None):
        self.roi = roi
        self.data = data
        self.name = name
        self.tracker = tracker
        self.swap_tmp_data=None

    def get_json(self):
        (roi_x1, roi_y1, roi_x2, roi_y2) = self.roi        
        msg = {
            'roi_x1':roi_x1,
            'roi_y1':roi_y1,
            'roi_x2':roi_x2,
            'roi_y2':roi_y2,
            'name':self.name,
            'data':np_array_to_jpeg_string(self.data)
            }
        return json.dumps(msg)
    
class FaceTransformation(object):

    def __init__(self):
        logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s %(name)-12s %(levelname)-8s %(thread)d %(message)s',
                    filename='faceswap-proxy.log',
                    filemode='w+'                            
        )
        formatter = logging.Formatter('%(asctime)-15s %(levelname)-8s %(processName)s %(message)s')   
        self.logger=logging.getLogger(__name__)
        ch = logging.StreamHandler(sys.stdout)
        ch.setLevel(logging.DEBUG)
        ch.setFormatter(formatter)
        fh = logging.FileHandler('faceswap.log')
        fh.setLevel(logging.DEBUG)
        fh.setFormatter(formatter)
        self.logger.addHandler(ch)
        self.logger.addHandler(fh)        
        
        self.cnt=0
        self.detector = dlib.get_frontal_face_detector()
        self.faces=[]
        self.face_table = {}
        
        self.faces_lock=threading.Lock()
        self.img_queue = multiprocessing.Queue()
        self.trackers_queue = multiprocessing.Queue()
        
        # openface related
        self.training_cnt = 0
        self.server_ip = u"ws://128.2.211.75"
        self.server_port = 9000
        self.openface_client = OpenFaceClient(self.server_ip, self.server_port)
        self.training = self.openface_client.isTraining()
        self.logger.debug('openface is training?{}'.format(self.training))

        mpl = multiprocessing.log_to_stderr()
        mpl.setLevel(logging.INFO)        
        
        self.sync_face_event = multiprocessing.Event()
        self.sync_face_event.clear()
        self.sync_thread_stop_event=threading.Event()
        self.sync_thread_stop_event.clear()
        self.sync_faces_thread = threading.Thread(target=self.sync_faces, name='bgThread', kwargs={'stop_event' : self.sync_thread_stop_event})
        self.sync_faces_thread.start()

        self.detection_process_stop_event = multiprocessing.Event()
        self.detection_process_stop_event.clear()
        self.detection_process = multiprocessing.Process(target = self.detection_update_thread, name='DetectionProcess', args=(self.img_queue, self.trackers_queue, self.server_ip, self.server_port, self.sync_face_event,self.detection_process_stop_event,))
        self.detection_process.start()

    # def reset_openface(self):
    #     self.openface_client.terminate()
    #     self.openface_client = OpenFaceClient(self.server_ip, self.server_port)
    #     self.training = self.openface_client.isTraining()
        
    # background thread that updates self.faces once
    # detection process signaled
    def sync_faces(self, stop_event=None):
        while (not stop_event.is_set()):
            self.sync_face_event.wait(1)
            if (not self.sync_face_event.is_set()):
                continue
#            self.logger.debug('bg-thread getting updates from detection process!')
            try:
                tracker_updates = self.trackers_queue.get(timeout=1)
                faces = tracker_updates['faces']
                tracker_frame = tracker_updates['frame']
                for face in faces:
                    tracker = create_tracker(tracker_frame, face.roi)
                    face.tracker = tracker

                self.logger.debug('bg-thread updating faces from detection process!')
            
                self.faces_lock.acquire()            
                self.faces = faces
                self.faces_lock.release()

                self.sync_face_event.clear()
            except Queue.Empty:
                self.logger.debug('bg-thread updating faces queue empty!')
    
    def terminate(self):
        self.detection_process_stop_event.set()
        self.sync_thread_stop_event.set()
        self.detection_process.join()
        self.logger.debug('detection process shutdown!')        
        self.sync_faces_thread.join()
        self.logger.debug('sync faces thread shutdown!')                
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
            resp = self.openface_client.addFrame(face_string, 'detect')
            self.logger.debug('server response: {}'.format(resp))            
            resp_dict = json.loads(resp)
            name = resp_dict['name']
            self.logger.debug('recognize: {}'.format(name))
            names.append(name)
        return names
        
    def detection_update_thread(self, img_queue, trackers_queue, openface_ip, openface_port, sync_face_event, stop_event):
        self.logger.info('created')
        detector = dlib.get_frontal_face_detector()
#        openface_client = OpenFaceClient(openface_ip, openface_port)
        while (not stop_event.is_set()):
            try:
                frame = img_queue.get(timeout=1)
            except Queue.Empty:                
                continue
            rois = self.detect_faces(frame, detector)
            names = self.recognize_faces(None, frame, rois)
            trackers = create_trackers(frame, rois)
            frame_available = True
            frame_cnt = 0 
            while frame_available:
                try:
                    frame = img_queue.get_nowait()
                    self.update_trackers(trackers, frame)
                    frame_cnt +=1
                except Queue.Empty:
                    self.logger.debug('all image processed! # images {}'.format(frame_cnt))    
                    frame_available = False
#                    self.logger.debug('detection thread# trackers {}'.format(len(trackers)))
                    faces=[]
                    for idx, tracker in enumerate(trackers):
                        new_roi = tracker.get_position()
                        cur_roi = (int(new_roi.left()),
                                         int(new_roi.top()),
                                         int(new_roi.right()),
                                         int(new_roi.bottom()))
                        name = names[idx]                        
                        self.logger.debug('recognized faces {0} {1}'.format(idx, name))
                        face = FaceROI(cur_roi, name=name)
                        faces.append(face)
                    if (len(faces)>0):
#                        self.logger.debug('update trackers!')
                        tracker_updates = {'frame':frame, 'faces':faces}
                        trackers_queue.put(tracker_updates)
                        sync_face_event.set()
        sync_face_event.set()
                    
    def update_trackers(self, trackers, frame):
        for idx, tracker in enumerate(trackers):
            tracker.update(frame)
            
    def track_faces(self, frame, faces):
#        self.rois=[]
        self.logger.debug('main thread tracking. # faces tracking {} '.format(len(faces)))
        to_be_removed_face = []
        for idx, face in enumerate(faces):
            tracker = face.tracker
#        for idx, tracker in enumerate(self.trackers):
            if DEBUG:
                start = time.time()
                
            # preprocessing to grey scale can reduce run time
#            grey_frame = frame
            grey_frame = cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY)
                
            tracker.update(grey_frame)
            new_roi = tracker.get_position()
            if DEBUG:
                end = time.time()
                self.logger.debug('main-thread tracker run: {}'.format((end-start)*1000))
                (x1,y1,x2,y2) = (int(new_roi.left()),
                          int(new_roi.top()),
                          int(new_roi.right()),
                          int(new_roi.bottom()))
            face.roi = (x1,y1,x2,y2)
            if (self.is_small_face(face.roi)):
                to_be_removed_face.append(face)
            else:
                # add in data
                face.data = np.copy(frame[y1:y2+1, x1:x2+1])

        faces = [face for face in faces if face not in to_be_removed_face]
#        self.logger.debug('main thread tracking. # faces returned {} '.format(len(face_snippets)))   
        return faces
        
#        roi_face_pairs=self.shuffle_roi(self.rois, frame)        
#        self.rois, self.trackers = self.get_large_faces(self.rois, self.trackers)

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
        # if DEBUG:
        #     start = time.time()

#        self.logger.debug('main-process received frame!')

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
        self.img_queue.put(grey_frame)
        
        self.faces_lock.acquire()                    
        self.faces=self.track_faces(frame, self.faces)
        self.faces=self.shuffle_roi(self.faces, self.face_table)
        face_snippets = [face.get_json() for face in self.faces]        
        self.faces_lock.release()

        # if DEBUG:
        #     end = time.time()
        #     self.logger.debug('total processing time: {}'.format((end-start)*1000))
        
        return face_snippets

    def get_FaceROI_from_string(self, faces, name):
        for face in faces:
            if face.name == name:
                return face
        return None
    
    # shuffle faces
    def shuffle_roi(self, faces, face_table):
        for idx, face in enumerate(faces):
            if face.name in face_table:
                (roi_x1, roi_y1, roi_x2, roi_y2) = face.roi
                to_person = self.get_FaceROI_from_string(faces, face_table[face.name])
                if (None == to_person):
                    continue
                nxt_face = to_person.data
                cur_face = face.data
                dim = (cur_face.shape[1],cur_face.shape[0])
                try:
                    nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
                except:
                    print 'error: face resize failed!'
                face.swap_tmp_data=nxt_face_resized

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

        face = FaceROI(rois[0], data=face_pixels)            
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
            
        
#     def detect_swap_face(self, frame):
#         # The 1 in the second argument indicates that we should upsample the image
#         # 1 time.  This will make everything bigger and allow us to detect more
#         # faces.
#         start = time.time()
# #        dets, scores, idx = detector.run(frame, 1)
#         dets = self.detector(frame, 1)
#         end = time.time()
#         print 'detector run: {}'.format((end-start)*1000)
        
#         # changed dets format here
#         dets = map(lambda d: (int(d.left()), int(d.top()), int(d.right()), int(d.bottom())), dets)
#         rois=self.rm_small_face(dets)
#         rois=sorted(rois)
        
#         roi_face_pairs=[]
#         if (len(rois) > 0):
#             # swap faces:
# #            faces=[]
#             roi_face_pairs= self.shuffle_roi(rois, frame)
            
#         self.rois =rois
            
#         return roi_face_pairs

