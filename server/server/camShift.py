#!/usr/bin/env python

'''
Camshift tracker
================

'''

import numpy as np
import cv2
import dlib
import vision
import sys
import time
import pdb

class Tracker(object):
    def __init__(self):
        self.stale = 0

    def get_stale(self):
        return self.stale

    def inc_stale(self):
        self.stale+=1

    def dec_stale(self):
        self.stale-=1

    def clr_stale(self):
        self.stale=0

# obsolete need double-check correctness
class camshiftTracker(Tracker):
    def __init__(self):
        super(self.__class__, self).__init__()
        self.selection = None
        self.hist = None
        self.track_window=None
        self.track_box = None

    def start_track(self, frame, droi):
        self.selection=vision.drectangle_to_tuple(droi)
        hsv = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
        mask = cv2.inRange(hsv, np.array((0., 60., 32.)), np.array((180., 255., 255.)))
        x0, y0, x1, y1 = self.selection
        self.track_window = (x0, y0, x1-x0, y1-y0)
        hsv_roi = hsv[y0:y1, x0:x1]
        mask_roi = mask[y0:y1, x0:x1]
        hist = cv2.calcHist( [hsv_roi], [0], mask_roi, [16], [0, 180] )
        cv2.normalize(hist, hist, 0, 255, cv2.NORM_MINMAX)
        self.hist = hist.reshape(-1)
        
    def update(self, frame, is_hsv=False, suggested_roi=None):
        try:
            if not is_hsv:
                hsv = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
            else:
                hsv = frame
            mask = cv2.inRange(hsv, np.array((0., 60., 32.)), np.array((180., 255., 255.)))        
            prob = cv2.calcBackProject([hsv], [0], self.hist, [0, 180], 1)
            prob &= mask
            term_crit = ( cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 10, 1 )
            retval, self.track_window = cv2.CamShift(prob, self.track_window, term_crit)
        except cv2.error as e:
            sys.stdout.write('cv2 error in tracking')
            self.track_window=(0,0,0,0)

    def get_position(self):
        track_rect = self.track_window
        roi_x1, roi_y1 = track_rect[0], track_rect[1]
        roi_x2 = track_rect[0] + track_rect[2]
        roi_y2 = track_rect[1] + track_rect[3]        
        ret = dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2)
        return ret

class meanshiftTracker(Tracker):
    def __init__(self):
        super(self.__class__, self).__init__()        
        self.hist = None
        self.track_window=None
        self.track_box = None

    def start_track(self, frame, droi):
        # calculate mask
        hsv = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
        mask = cv2.inRange(hsv, np.array((0., 60., 32.)), np.array((180., 255., 255.)))
        (x0, y0, x1, y1)=vision.drectangle_to_tuple(droi)
        self.track_window = (x0, y0, x1-x0, y1-y0)
        hsv_roi=hsv[y0:y1, x0:x1]
        mask_roi = mask[y0:y1, x0:x1]
        self.hist = cv2.calcHist( [hsv_roi], [0], mask_roi, [16], [0, 180] )
        cv2.normalize(self.hist, self.hist, 0, 255, cv2.NORM_MINMAX)
        self.hist = self.hist.reshape(-1)
        
    def update(self, frame, is_hsv=False, suggested_roi=None):
        try:
            if not is_hsv:
                hsv = cv2.cvtColor(frame, cv2.COLOR_RGB2HSV)
            else:
                hsv = frame
            mask = cv2.inRange(hsv, np.array((0., 60., 32.)), np.array((180., 255., 255.)))      
            prob = cv2.calcBackProject([hsv], [0], self.hist, [0, 180], 1)
            prob &= mask
            term_crit = ( cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 10, 1 )            
            retval, self.track_window = cv2.meanShift(prob, self.track_window, term_crit)
        except cv2.error as e:
            sys.stdout.write('cv2 error in tracking')
            self.track_window=(0,0,0,0)

    def get_position(self):
        track_rect = self.track_window
        roi_x1, roi_y1 = track_rect[0], track_rect[1]
        roi_x2 = track_rect[0] + track_rect[2] -1
        roi_y2 = track_rect[1] + track_rect[3] -1     
        ret = dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2)
        return ret
        

class App(object):
    def __init__(self, video_src, bx=None):
#        self.cam = video.create_capture(video_src)
        self.cam = cv2.VideoCapture(video_src)        
        ret, self.frame = self.cam.read()

#        self.tracker = camshiftTracker()
        self.tracker = meanshiftTracker()

        cv2.namedWindow(self.tracker.__class__.__name__)
        cv2.setMouseCallback(self.tracker.__class__.__name__, self.onmouse)
        self.show_backproj = False

        if bx:
            self.tracking_state = 1
            self.selection = bx
        else:
            self.tracking_state = 0        
            self.selection = None
        

    def onmouse(self, event, x, y, flags, param):
        x, y = np.int16([x, y]) # BUG
        if event == cv2.EVENT_LBUTTONDOWN:
            self.drag_start = (x, y)
            self.tracking_state = 0
            return
        if self.drag_start:
            if flags & cv2.EVENT_FLAG_LBUTTON:
                h, w = self.frame.shape[:2]
                xo, yo = self.drag_start
                x0, y0 = np.maximum(0, np.minimum([xo, yo], [x, y]))
                x1, y1 = np.minimum([w, h], np.maximum([xo, yo], [x, y]))
                self.selection = None
                if x1-x0 > 0 and y1-y0 > 0:
                    self.selection = (x0, y0, x1, y1)
            else:
                self.drag_start = None
                if self.selection is not None:
                    self.tracking_state = 1

    def show_hist(self):
        bin_count = self.hist.shape[0]
        bin_w = 24
        img = np.zeros((256, bin_count*bin_w, 3), np.uint8)
        for i in xrange(bin_count):
            h = int(self.hist[i])
            cv2.rectangle(img, (i*bin_w+2, 255), ((i+1)*bin_w-2, 255-h), (int(180.0*i/bin_count), 255, 255), -1)
        img = cv2.cvtColor(img, cv2.COLOR_HSV2BGR)
        cv2.imshow('hist', img)

    def run(self):
        while True:
            ret, self.frame = self.cam.read()
            vis = self.frame.copy()
            self.frame=cv2.cvtColor(self.frame, cv2.COLOR_BGR2RGB)
            if self.selection:
                print('selection: {}'.format(self.selection))
                x0, y0, x1, y1 = self.selection                
                self.tracker.start_track(self.frame,
                                         dlib.rectangle(int(x0),int(y0),int(x1),int(y1)))
                print('track_window: {}'.format(self.tracker.track_window))        
                
                vis_roi = vis[y0:y1, x0:x1]
                cv2.bitwise_not(vis_roi, vis_roi)
                
            if self.tracking_state == 1:
                self.selection = None
                self.tracker.update(self.frame)
                track_box = self.tracker.get_position()

                pt1 = (track_box.left(), track_box.top())
                pt2 = (track_box.right(), track_box.bottom())
                print(self.tracker.track_window)                
                cv2.rectangle(vis, pt1, pt2, (0, 255, 0), 2)
#            cv2.imshow(self.tracker.__class__.__name__, vis)

            ch = 0xFF & cv2.waitKey(5)
            if ch == 27:
                break
            if ch == ord('b'):
                self.show_backproj = not self.show_backproj
        cv2.destroyAllWindows()


if __name__ == '__main__':
    import sys
    try:
        video_src = sys.argv[1]
    except:
        video_src = 0
#    print(__doc__)
#    r,h,c,w = 250,90,400,125  # simply hardcoded the values
#    selection = (c,r,c+w-1,r+h-1)
    App(video_src, bx=(288, 98, 516, 350) ).run()    
        
