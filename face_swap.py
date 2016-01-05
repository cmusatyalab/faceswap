import numpy as np
import cv2
import dlib
import sys
import pdb
import numpy as np
import time

MIN_WIDTH_THRESHOLD=10
MIN_HEIGHT_THRESHOLD=10

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
                    
                    if (new_roi.is_empty()):
                        # lost obj
                        trackers_to_remove.append(idx)
                    else:
                        x1,y1,x2,y2 = int(new_roi.left()), int(new_roi.top()), int(new_roi.right()), int(new_roi.bottom())
                        self.rois[idx]= (x1,y1,x2,y2)
                        cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0), 1)
                        
                frame=self.shuffle_roi(self.rois, frame)
                        
            if ( len(trackers_to_remove) > 0):
                for del_idx in trackers_to_remove:
                    del self.rois[idx]
                    del self.trackers[idx]
                    
        self.cnt+=1
        
        return frame
        
    # shuffle rois
    def shuffle_roi(self, rois, frame):
        # for cropping roi, y is given first
        # http://stackoverflow.com/questions/15589517/how-to-crop-an-image-in-opencv-using-python
        # manipulate frames
        faces = [np.copy(frame[y1:y2, x1:x2]) for (x1,y1,x2,y2) in rois]                 
        for idx, _ in enumerate(faces):
            display_idx = (idx+1) % len(faces)
            (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]
            nxt_face = faces[display_idx]
            cur_face = frame[roi_y1:roi_y2, roi_x1:roi_x2]
            dim = (cur_face.shape[1],cur_face.shape[0])

            nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
            frame[roi_y1:roi_y2, roi_x1:roi_x2] = nxt_face_resized
        return frame

            
    def detect_swap_face(self, frame):
        # The 1 in the second argument indicates that we should upsample the image
        # 1 time.  This will make everything bigger and allow us to detect more
        # faces.
        start = time.time()
#        dets, scores, idx = detector.run(frame, 1)
        dets = self.detector(frame, 1)
        end = time.time()
        print 'detector run: {}'.format(end-start)
        
        filtered_dets=[]
        for i, d in enumerate(dets):
            # print("Detection {}, score: {}, face_type:{}".format(
            #     d, scores[i], idx[i]))
            x1,y1,x2,y2 = int(d.left()), int(d.top()), int(d.right()), int(d.bottom())
            if ( abs(x2-x1) >= MIN_WIDTH_THRESHOLD and abs(y2-y1) >=MIN_HEIGHT_THRESHOLD):
                filtered_dets.append(d)
                cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0), 1)
                
        dets=filtered_dets

        rois=[]                
        if (len(dets) > 0):
            # swap faces:
            faces=[]

            for i,d in enumerate(dets):
                # fixed size
                x1,y1,x2,y2 =int(d.left()), int(d.top()), int(d.right()), int(d.bottom())
                faces.append(np.copy(frame[y1:y2, x1:x2]))
                rois.append((x1,y1,x2,y2))

            # for cropping roi, y is given first
            # http://stackoverflow.com/questions/15589517/how-to-crop-an-image-in-opencv-using-python
            # manipulate frames
            for idx, _ in enumerate(faces):
                display_idx = (idx+1) % len(faces)
                (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]
                nxt_face = faces[display_idx]
                cur_face = frame[roi_y1:roi_y2, roi_x1:roi_x2]
                dim = (cur_face.shape[1],cur_face.shape[0])

                nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
                frame[roi_y1:roi_y2, roi_x1:roi_x2] = nxt_face_resized

        self.rois =rois
            
        return frame
