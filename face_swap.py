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

def create_tracker(frame, roi):
    tracker = dlib.correlation_tracker()
    (roi_x1, roi_y1, roi_x2, roi_y2) = roi
    print 'workerpool: start tracking! # face: {}'.format(roi)
    tracker.start_track(frame,
                        dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2))
    return tracker

def drectangle_to_tuple(drectangle):
    cur_roi = (int(drectangle.left()),
                     int(drectangle.top()),
                     int(drectangle.right()),
                     int(drectangle.bottom()))
    return cur_roi
    
def create_and_track(tracker_input):
    print 'workerpool: create_and_track! process id: {}'.format(os.getpid())
    sys.stdout.flush()
    
    prev_frame = tracker_input.prev_frame
    prev_roi = tracker_input.prev_roi
    frame = tracker_input.frame    
    tracker=create_tracker(prev_frame, prev_roi)
    tracker.update(frame)
    new_roi = drectangle_to_tuple(tracker.get_position())
    return new_roi

def x_square(x):
    print 'worker pool test called'
    return x**2

    
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
        
        self.rois=[]
        self.cnt=0
        self.detector = dlib.get_frontal_face_detector()
        self.trackers=[]
        self.trackers_lock=threading.Lock()
        self.img_queue = multiprocessing.Queue()
        self.trackers_queue = multiprocessing.Queue()
        self.last_frame=None
        self.worker_pool=multiprocessing.Pool()
        self.detection_process = multiprocessing.Process(target = self.detection_update_thread, name='DetectionProcess', args=(self.img_queue, self.trackers_queue, ) )
        self.detection_process.start()

    def terminate(self):
        self.logger.debug('detection thread terminate!')

    def detection_update_thread(self, img_queue, trackers_queue):
        self.logger.info('update thread created')
        while True:
            frame = img_queue.get()
            rois = self.detect_faces(frame, self.detector)
            trackers = self.create_trackers(frame, rois)
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
                    self.logger.debug('detection thread# trackers {}'.format(len(trackers)))
                    cur_poses = []
                    for tracker in trackers:
                        new_roi = tracker.get_position()
                        cur_roi = (int(new_roi.left()),
                                         int(new_roi.top()),
                                         int(new_roi.right()),
                                         int(new_roi.bottom()))
                        cur_poses.append(cur_roi)
                    if (len(cur_poses)>0):
                        self.logger.debug('update trackers!')
                        tracker_updates = {'frame':frame, 'rois':cur_poses}
                        trackers_queue.put(tracker_updates)

                    
    def update_trackers(self, trackers, frame):
        for idx, tracker in enumerate(trackers):
            tracker.update(frame)
            
    def create_trackers(self, frame, rois):
        self.logger.debug("detection_process:")
        trackers = []
        for roi in rois:
            tracker = create_tracker(frame,roi)
            trackers.append(tracker)
        return trackers

    
    def track_faces(self, frame):
        if DEBUG:
            start = time.time()
        
        trackers_input = []
        for roi in self.rois:
            trackers_input.append(TrackerInitializer(self.last_frame, roi, frame) )
        self.rois = self.worker_pool.map(create_and_track, trackers_input)
        self.logger.debug('map result: {}'.format(self.rois))

        if DEBUG:
            end = time.time()
            self.logger.debug('trackers run: {}'.format((end-start)*1000))
            
        self.rois, _ = self.get_large_faces(self.rois, [])
        roi_face_pairs=self.shuffle_roi(self.rois, frame)
        return roi_face_pairs

        # serialized version
        # for idx, tracker in enumerate(self.trackers):
        #     if DEBUG:
        #         start = time.time()
        #     tracker.update(frame)
        #     new_roi = tracker.get_position()
        #     if DEBUG:
        #         end = time.time()
        #         self.logger.debug('main-thread tracker run: {}'.format(end-start))
        #     (x1,y1,x2,y2) = (int(new_roi.left()),
        #                   int(new_roi.top()),
        #                   int(new_roi.right()),
        #                   int(new_roi.bottom()))
            
        #     self.rois.append((x1,y1,x2,y2))
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
        if DEBUG:
            start = time.time()
        
        self.logger.debug('main-process received frame!')                                
        # forward img to DetectionProcess
        self.img_queue.put(frame)
        try:
            tracker_updates = self.trackers_queue.get_nowait()
            rois = tracker_updates['rois']
            tracker_frame = tracker_updates['frame']
            self.rois = rois
            self.last_frame = tracker_frame
#            self.trackers=self.create_trackers(frame,rois)
            self.logger.debug('main-process Updated rois and frame based on detection!')
        except Queue.Empty:
            self.logger.debug('main-process no trackers update')            

        results=self.track_faces(frame)            
        self.last_frame = frame

        if DEBUG:
            end = time.time()
            self.logger.debug('total processing time: {}'.format((end-start)*1000))
        
        return results

# #        pdb.set_trace()
#         roi_face_pairs=[]
#         if (self.cnt % (RATIO_DETECT_TRACK+1) ==0):
#             roi_face_pairs = self.detect_swap_face(frame)
#             # rois are reset in detect_swap_face
#             self.trackers=[]
#             # initialize trackers
#             if ( len(self.rois) > 0):
#                 for roi in self.rois:
#                     tracker = dlib.correlation_tracker()
#                     (roi_x1, roi_y1, roi_x2, roi_y2) = roi
#                     print 'start tracking! # face: {}'.format(len(self.rois))
#                     tracker.start_track(frame, dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2))
#                     self.trackers.append(tracker)
                    
#         else:
#             # use tracking to shuffle
#             trackers_to_remove=[]
#             if ( len(self.trackers) > 0):
# #                pdb.set_trace()
#                 for idx, tracker in enumerate(self.trackers):
#                     start = time.time()                    
#                     tracker.update(frame)
#                     new_roi = tracker.get_position()
#                     end = time.time()
#                     print 'tracker run: {}'.format(end-start)
# #                    print 'roi: {}'.format(new_roi)
#                     x1,y1,x2,y2 = int(new_roi.left()), int(new_roi.top()), int(new_roi.right()), int(new_roi.bottom())
#                     self.rois[idx]= (x1,y1,x2,y2)

#                 # remove failed trackers
#                 small_face_idx=self.find_small_face_idx(self.rois)
#                 # remove small faces
#                 if ( len(small_face_idx) > 0):
#                     large_rois = [ self.rois[i] for i in xrange(len(self.rois)) if i not in set(small_face_idx) ]
#                     self.rois=large_rois
#                     large_rois_trackers = [ self.trackers[i] for i in xrange(len(self.trackers)) if i not in set(small_face_idx) ]
#                     self.trackers = large_rois_trackers
                

#                 roi_face_pairs=self.shuffle_roi(self.rois, frame)
                
#                 # for roi in self.rois:
#                 #     (x1,y1,x2,y2) = roi
#                 #     cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0))
                    
#         self.cnt+=1
#         return roi_face_pairs


    # shuffle rois
    def shuffle_roi(self, rois, frame):
        roi_face_pairs = []
        faces = [np.copy(frame[y1:y2+1, x1:x2+1]) for (x1,y1,x2,y2) in rois]                 
        for idx, _ in enumerate(faces):
            display_idx = (idx+1) % len(faces)
            (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]
            nxt_face = faces[display_idx]
            cur_face = frame[roi_y1:roi_y2+1, roi_x1:roi_x2+1]
            dim = (cur_face.shape[1],cur_face.shape[0])
            
#            print 'roi: {}'.format(rois[idx])                
#            print 'dimension: {}'.format(dim)
            nxt_dim = (nxt_face.shape[1],nxt_face.shape[0])
#            print 'nxt face dimension: {}'.format(nxt_dim)

            try:
                nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
            except:
                print 'error: face resize failed!'
                pdb.set_trace()
# modifiying frames directly                
#            frame[roi_y1:roi_y2+1, roi_x1:roi_x2+1] = nxt_face_resized
# instead of modifying frames returns these face snippets back
            roi_face = (rois[idx], nxt_face_resized)
            roi_face_pairs.append(roi_face)

#        return frame
        return roi_face_pairs


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
        
    def detect_faces(self, frame, detector):
        if DEBUG:
            start = time.time()
        dets = detector(frame, 1)
        if DEBUG:
            end = time.time()
            self.logger.debug('detector run: {}'.format((end-start)*1000))
        
        dets = map(lambda d: (int(d.left()), int(d.top()), int(d.right()), int(d.bottom())), dets)
        rois=self.rm_small_face(dets)
        self.logger.debug('# detected : {}'.format(len(rois)))        
        rois=sorted(rois)
        return rois

        
    def detect_swap_face(self, frame):
        # The 1 in the second argument indicates that we should upsample the image
        # 1 time.  This will make everything bigger and allow us to detect more
        # faces.
        start = time.time()
#        dets, scores, idx = detector.run(frame, 1)
        dets = self.detector(frame, 1)
        end = time.time()
        print 'detector run: {}'.format((end-start)*1000)
        
        # changed dets format here
        dets = map(lambda d: (int(d.left()), int(d.top()), int(d.right()), int(d.bottom())), dets)
        rois=self.rm_small_face(dets)
        rois=sorted(rois)
        
        roi_face_pairs=[]
        if (len(rois) > 0):
            # swap faces:
#            faces=[]
            roi_face_pairs= self.shuffle_roi(rois, frame)
            
            # for i,d in enumerate(rois):
            #     (x1,y1,x2,y2) = d
            #     cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0))


            # # for cropping roi, y is given first
            # # http://stackoverflow.com/questions/15589517/how-to-crop-an-image-in-opencv-using-python
            # # manipulate frames
            # for idx, _ in enumerate(faces):
            #     display_idx = (idx+1) % len(faces)
            #     (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]
            #     nxt_face = faces[display_idx]
            #     cur_face = frame[roi_y1:roi_y2+1, roi_x1:roi_x2+1]
            #     dim = (cur_face.shape[1],cur_face.shape[0])

            #     print 'roi: {}'.format(rois[idx])                
            #     print 'dimension: {}'.format(dim)
            #     nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
            #     frame[roi_y1:roi_y2+1, roi_x1:roi_x2+1] = nxt_face_resized

        self.rois =rois
            
        return roi_face_pairs
