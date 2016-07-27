#! /usr/bin/env python

from MyUtils import *
import cv2
import dlib
import base64
import numpy as np
import json
from camShift import camshiftTracker, meanshiftTracker

# Tracking
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
#    tracker = camshiftTracker()
    # using mean shift for now
    tracker = meanshiftTracker() # 
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

def update_trackers(trackers, frame, rgb_to_hsv=True):
    if rgb_to_hsv:
        hsv_frame = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
    for idx, tracker in enumerate(trackers):
        tracker.update(hsv_frame, is_hsv=True)
    
# dlib wrappers    
def drectangle_to_tuple(drectangle):
    cur_roi = (int(drectangle.left()),
                     int(drectangle.top()),
                     int(drectangle.right()),
                     int(drectangle.bottom()))
    return cur_roi

# distance    
def euclidean_distance_square(roi1, roi2):
    result = abs(roi1[0] - roi2[0])**2 + abs(roi1[1] - roi2[1])**2
    return result

# np helpers    
def np_array_to_jpeg_string(frame):
    # face_img = Image.fromarray(frame)                        
    # sio = StringIO.StringIO()
    # face_img.save(sio, 'JPEG')
    # jpeg_img = sio.getvalue()
    _, jpeg_img=cv2.imencode('.jpg', frame)
    face_string = base64.b64encode(jpeg_img)
    return face_string

def np_array_to_string(frame):
    frame_bytes = frame.tobytes()
    face_string = base64.b64encode(frame_bytes)
    return face_string

def np_array_to_jpeg_data_url(frame):
    face_string = np_array_to_jpeg_string(frame)
    face_string = "data:image/jpeg;base64," + face_string
    return face_string

# cv2 helpers    
def imwrite_rgb(path, frame):
    frame = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)
    sys.stdout.write('writing img to {}'.format(path))
    sys.stdout.flush()
    cv2.imwrite(path,frame)

def draw_rois(img,rois):
    for roi in rois:
        (x1,y1,x2,y2) = tuple(roi) 
        cv2.rectangle(img, (x1,y1), (x2, y2), (255,0,0))

# face detection
MIN_WIDTH_THRESHOLD=3
MIN_HEIGHT_THRESHOLD=3
    
def is_small_face(roi):
    (x1,y1,x2,y2) = roi
    # has negative number
    if ( x1<0 or y1<0 or x2<0 or y2<0):
        return True
    # region too small
    if ( abs(x2-x1) < MIN_WIDTH_THRESHOLD or abs(y2-y1) < MIN_HEIGHT_THRESHOLD):
        LOG.debug('face too small discard')
        return True
    return False
    
def filter_small_faces(dets):
    filtered_dets=[]
    for i, d in enumerate(dets):
        if not is_small_face(d):
            filtered_dets.append(d)
    return filtered_dets
    
@timeit
def detect_faces(frame, detector, largest_only=False, upsample_num_times=0, adjust_threshold=0.0):
    # upsampling will take a lot of time
    # http://dlib.net/face_detector.py.html
    dets, scores, sub_detector_indices = detector.run(frame, upsample_num_times, adjust_threshold)
    if largest_only:
        if (len(dets) > 0):
            max_det = max(dets, key=lambda rect: rect.width() * rect.height())
            dets = [max_det]

    dets=map(lambda d: (int(d.left()), int(d.top()), int(d.right()), int(d.bottom())), dets)
    rois=filter_small_faces(dets)
    LOG.debug('# detected : {}'.format(len(rois)))        
    rois=sorted(rois)
    return rois

    
class FaceROI(object):
    def __init__(self, roi, data=None, name=None, tracker=None):
        self.roi = roi
        self.data = data
        self.name = name
        self.tracker = tracker
        self.swap_tmp_data=None

    # returned ROI may go out of bounds --> representing failure of tracking
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
            # msg['data'] = np_array_to_jpeg_string(self.data)
            msg['data'] = np_array_to_jpeg_string(self.data)
        return json.dumps(msg)

    # return the center location of the face
    def get_location(self):
        (roi_x1, roi_y1, roi_x2, roi_y2) = self.roi
        return ((roi_x1 + roi_x2)/2, (roi_y1+roi_y2)/2)
