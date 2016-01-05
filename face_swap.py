import numpy as np
import cv2
import dlib
import sys
import pdb
import numpy as np
import time

MIN_WIDTH_THRESHOLD=3
MIN_HEIGHT_THRESHOLD=3

class FaceTransformation():

    def __init__(self):
        self.rois=[]
        self.cnt=0
        self.detector = dlib.get_frontal_face_detector()
        self.trackers=[]
        
    def swap_face(self,frame):
#        pdb.set_trace()
        if (self.cnt % 10 ==0):
            frame = self.detect_swap_face(frame)
            # rois are reset in detect_swap_face
            self.trackers=[]
            # initialize trackers
            if ( len(self.rois) > 0):
                for roi in self.rois:
                    tracker = dlib.correlation_tracker()
                    (roi_x1, roi_y1, roi_x2, roi_y2) = roi
                    print 'start tracking! # face: {}'.format(len(self.rois))
                    tracker.start_track(frame, dlib.rectangle(roi_x1, roi_y1, roi_x2, roi_y2))
                    self.trackers.append(tracker)
                    
        else:
            # use tracking to shuffle
            trackers_to_remove=[]
            if ( len(self.trackers) > 0):
#                pdb.set_trace()
                for idx, tracker in enumerate(self.trackers):
                    start = time.time()                    
                    tracker.update(frame)
                    new_roi = tracker.get_position()
                    end = time.time()
                    print 'tracker run: {}'.format(end-start)

                    x1,y1,x2,y2 = int(new_roi.left()), int(new_roi.top()), int(new_roi.right()), int(new_roi.bottom())
                    self.rois[idx]= (x1,y1,x2,y2)

                # remove failed trackers
                small_face_idx=self.find_small_face_idx(self.rois)
                if ( len(small_face_idx) > 0):
                    for del_idx in small_face_idx:
                        del self.trackers[del_idx]
                        del self.rois[del_idx]
                
                for roi in self.rois:
                    (x1,y1,x2,y2) = roi
                    cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0))

                frame=self.shuffle_roi(self.rois, frame)
                    
        self.cnt+=1
        return frame
        
    # shuffle rois
    def shuffle_roi(self, rois, frame):
        faces = [np.copy(frame[y1:y2+1, x1:x2+1]) for (x1,y1,x2,y2) in rois]                 
        for idx, _ in enumerate(faces):
            display_idx = (idx+1) % len(faces)
            (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]
            nxt_face = faces[display_idx]
            cur_face = frame[roi_y1:roi_y2+1, roi_x1:roi_x2+1]
            dim = (cur_face.shape[1],cur_face.shape[0])
            
            print 'roi: {}'.format(rois[idx])                
            print 'dimension: {}'.format(dim)
            nxt_dim = (nxt_face.shape[1],nxt_face.shape[0])
            print 'nxt face dimension: {}'.format(nxt_dim)

            try:
                nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
            except:
                pdb.set_trace()
            frame[roi_y1:roi_y2+1, roi_x1:roi_x2+1] = nxt_face_resized
        return frame


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
        
        
    def detect_swap_face(self, frame):
        # The 1 in the second argument indicates that we should upsample the image
        # 1 time.  This will make everything bigger and allow us to detect more
        # faces.
        start = time.time()
#        dets, scores, idx = detector.run(frame, 1)
        dets = self.detector(frame, 1)
        end = time.time()
        print 'detector run: {}'.format(end-start)
        
        # filtered_dets=[]
        # for i, d in enumerate(dets):
        #     x1,y1,x2,y2 = int(d.left()), int(d.top()), int(d.right()), int(d.bottom())
        #     if ( abs(x2-x1) >= MIN_WIDTH_THRESHOLD and abs(y2-y1) >=MIN_HEIGHT_THRESHOLD):
        #         filtered_dets.append(d)
        #         cv2.rectangle(frame, (x1+1, y1+1), (x2+1, y2+1), (255,0,0))
        # dets=filtered_dets

        # changed dets format here
        dets = map(lambda d: (int(d.left()), int(d.top()), int(d.right()), int(d.bottom())), dets)
        rois=self.rm_small_face(dets)
        rois=sorted(rois)
        if (len(rois) > 0):
            # swap faces:
#            faces=[]

            for i,d in enumerate(rois):
                # fixed size
#                x1,y1,x2,y2 =int(d.left()), int(d.top()), int(d.right()), int(d.bottom())
                (x1,y1,x2,y2) = d
                cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0))
#                faces.append(np.copy(frame[y1:y2+1, x1:x2+1]))

            self.shuffle_roi(rois, frame)
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
            
        return frame
