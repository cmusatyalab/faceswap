import numpy as np
import cv2
import dlib
import sys
import pdb
import numpy as np

MIN_WIDTH_THRESHOLD=10
MIN_HEIGHT_THRESHOLD=10

class FaceTransformation():
    def swap_face(self, frame):
        # The 1 in the second argument indicates that we should upsample the image
        # 1 time.  This will make everything bigger and allow us to detect more
        # faces.
        detector = dlib.get_frontal_face_detector()        
        dets, scores, idx = detector.run(frame, 1)
        filtered_dets=[]
        for i, d in enumerate(dets):
            # print("Detection {}, score: {}, face_type:{}".format(
            #     d, scores[i], idx[i]))
            x1,y1,x2,y2 =d.left(), d.top(), d.right(), d.bottom()
            if ( abs(x2-x1) >= MIN_WIDTH_THRESHOLD and abs(y2-y1) >=MIN_HEIGHT_THRESHOLD):
                filtered_dets.append(d)
                cv2.rectangle(frame, (x1, y1), (x2, y2), (255,0,0), 2)

        dets=filtered_dets
        if (len(dets) > 1):
            # swap faces:

            faces=[]
            rois=[]        
            for i,d in enumerate(dets):
                # fixed size
                x1,y1,x2,y2 =d.left(), d.top(), d.right(), d.bottom()
                faces.append(np.copy(frame[y1:y2, x1:x2]))
                rois.append((x1,y1,x2,y2))

            # for cropping roi, y is given first
            # http://stackoverflow.com/questions/15589517/how-to-crop-an-image-in-opencv-using-python
            for idx, face in enumerate(faces):
                (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]

            # manipulate frames
            for idx, _ in enumerate(faces):
                display_idx = (idx+1) % len(faces)
                (roi_x1, roi_y1, roi_x2, roi_y2) = rois[idx]
                nxt_face = faces[display_idx]
                cur_face = frame[roi_y1:roi_y2, roi_x1:roi_x2]
                dim = (cur_face.shape[1],cur_face.shape[0])

                nxt_face_resized = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)
                frame[roi_y1:roi_y2, roi_x1:roi_x2] = nxt_face_resized
                
        return frame
