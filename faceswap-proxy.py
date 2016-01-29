#!/usr/bin/env python
#
# Cloudlet Infrastructure for Mobile Computing
#
#   Author: Kiryong Ha <krha@cmu.edu>
#
#   Copyright (C) 2011-2013 Carnegie Mellon University
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

import time
import Queue
import struct
import os
import sys
if os.path.isdir("../gabriel") is True:
    sys.path.insert(0, "..")

from gabriel.proxy.common import AppProxyStreamingClient
from gabriel.proxy.common import AppProxyThread
from gabriel.proxy.common import ResultpublishClient
from gabriel.proxy.common import Protocol_measurement
from gabriel.proxy.common import get_service_list
from gabriel.common.config import ServiceMeta as SERVICE_META
from gabriel.common.config import Const
from face_swap import FaceTransformation

import pdb
from PIL import Image, ImageOps
import io, StringIO
import base64
import numpy as np
import json

import cProfile, pstats, StringIO

DEBUG = True
transformer = FaceTransformation()


class AppDataProtocol(object):
    TYPE_add_person = "add_person"
    TYPE_train = "train"    
    TYPE_detect = "detect"
    TYPE_img = "image"        
    

# bad idea to transfer image back using json
class DummyVideoApp(AppProxyThread):

    def gen_response(self, response_type, value):
        msg = {
            'type': response_type,
            'value': value
            }
        return json.dumps(msg)
        
    
    def process(image):
        # pr = cProfile.Profile()
        # pr.enable()
        # rgb mode

        # preprocessing techqniues : resize?
#        image = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)

#        pdb.set_trace()
        roi_face_pairs = transformer.swap_face(image)
        roi_face_pairs_string = {}
        roi_face_pairs_string['num'] = len(roi_face_pairs)
        idx = 0
        for roi, face in roi_face_pairs:
            processed_img = Image.fromarray(face)
            processed_output = StringIO.StringIO()
            processed_img.save(processed_output, 'JPEG')
            if DEBUG:
                processed_img.save('test.jpg')

            jpeg_image = processed_output.getvalue()
            face_string = base64.b64encode(jpeg_image)
            processed_output.close()            

            (roi_x1, roi_y1, roi_x2, roi_y2) = roi
            roi_face_pairs_string['item_'+str(idx)+'_roi_x1'] = roi_x1
            roi_face_pairs_string['item_'+str(idx)+'_roi_y1'] = roi_y1
            roi_face_pairs_string['item_'+str(idx)+'_roi_x2'] = roi_x2
            roi_face_pairs_string['item_'+str(idx)+'_roi_y2'] = roi_y2
            roi_face_pairs_string['item_'+str(idx)+'_img'] = face_string
            idx+=1
#            roi_face_pairs_string.append((roi, face_string))


        # pr.disable()
        # s = StringIO.StringIO()
        # sortby = 'cumulative'
        # ps = pstats.Stats(pr, stream=s).sort_stats(sortby)
        # ps.print_stats()
        # print s.getvalue()

        # processed_img = Image.fromarray(transformer.swap_face(image))            
        # processed_output = StringIO.StringIO()
        # processed_img.save(processed_output, 'JPEG')
        # if DEBUG:
        #     processed_img.save('test.jpg')

        # jpeg_image = processed_output.getvalue()
        # result = base64.b64encode(jpeg_image)
        # processed_output.close()

        result = json.dumps(roi_face_pairs_string)
#        print result
        return result

    def handle(self, header, data):
        # PERFORM Cognitive Assistant Processing
        # header is a dict
        sys.stdout.write("processing: ")
        sys.stdout.write("%s\n" % header)
        
        header_dict = header

        if 'add_person' in header_dict:
            name = header_dict['add_person']
            if isinstance(name, basestring):
                transformer.addPerson(name)
            else:
                raise TypeError('unsupported type for name of a person')

            return self.gen_response(AppDataProtocol.TYPE_add_person, name)
        
        training = False
        if 'training' in header_dict:
            training=True
            name=header_dict['training']

        # operate on client data
        image_raw = Image.open(io.BytesIO(data))
        image = np.array(image_raw)
            
        if training:
#            pdb.set_trace()
            cnt = transformer.train(image, name)        
            return self.gen_response(AppDataProtocol.TYPE_train, str(cnt))
        else:
            # swap faces
            snippets = self.process(image)
            return self.gen_response(AppDataProtocol.TYPE_detect, snippets)

class DummyAccApp(AppProxyThread):
    def chunks(self, l, n):
        for i in xrange(0, len(l), n):
            yield l[i:i + n]

    def handle(self, header, acc_data):
        ACC_SEGMENT_SIZE = 16# (int, float, float, float)
        for chunk in self.chunks(acc_data, ACC_SEGMENT_SIZE):
            (acc_time, acc_x, acc_y, acc_z) = struct.unpack("!ifff", chunk)
            print "time: %d, acc_x: %f, acc_y: %f, acc_x: %f" % \
                    (acc_time, acc_x, acc_y, acc_z)
        return None


if __name__ == "__main__":
    result_queue = list()

    sys.stdout.write("Discovery Control VM\n")
    service_list = get_service_list(sys.argv)
    video_ip = service_list.get(SERVICE_META.VIDEO_TCP_STREAMING_ADDRESS)
    video_port = service_list.get(SERVICE_META.VIDEO_TCP_STREAMING_PORT)

    
    return_addresses = service_list.get(SERVICE_META.RESULT_RETURN_SERVER_LIST)

    # image receiving thread
    video_frame_queue = Queue.Queue(Const.APP_LEVEL_TOKEN_SIZE)
#    video_frame_queue = Queue.Queue(10)
    print "TOKEN SIZE OF OFFLOADING ENGINE: %d" % Const.APP_LEVEL_TOKEN_SIZE
    video_client = AppProxyStreamingClient((video_ip, video_port), video_frame_queue)
    video_client.start()
    video_client.isDaemon = True
    dummy_video_app = DummyVideoApp(video_frame_queue, result_queue, \
            app_id=Protocol_measurement.APP_DUMMY) # dummy app for image processing
    dummy_video_app.start()
    dummy_video_app.isDaemon = True

    # result pub/sub
    result_pub = ResultpublishClient(return_addresses, result_queue)
    result_pub.start()
    result_pub.isDaemon = True

    try:
        while True:
            time.sleep(1)
    except Exception as e:
        pass
    except KeyboardInterrupt as e:
        sys.stdout.write("user exits\n")
    finally:
        if transformer is not None:
            transformer.terminate()
        if video_client is not None:
            video_client.terminate()
        if dummy_video_app is not None:
            dummy_video_app.terminate()
        result_pub.terminate()

