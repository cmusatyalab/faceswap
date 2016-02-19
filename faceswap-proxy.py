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
#from scipy.ndimage import imread
import cProfile, pstats, StringIO

DEBUG = False
transformer = FaceTransformation()
prev_timestamp = time.time()*1000

class AppDataProtocol(object):
    TYPE_add_person = "add_person"
    TYPE_train = "train"    
    TYPE_detect = "detect"
    TYPE_img = "image"        
    TYPE_get_state = "get_state"
    TYPE_load_state = "load_state"
    TYPE_reset = "reset"                

# bad idea to transfer image back using json
class DummyVideoApp(AppProxyThread):

    def gen_response(self, response_type, value):
        msg = {
            'type': response_type,
            'value': value,
            'time': int(time.time()*1000)
            }
        return json.dumps(msg)
        
    
    def process(self, image):
        # pr = cProfile.Profile()
        # pr.enable()

        # preprocessing techqniues : resize?
#        image = cv2.resize(nxt_face, dim, interpolation = cv2.INTER_AREA)

        face_snippets_list = transformer.swap_face(image)
        face_snippets_string = {}
        face_snippets_string['num'] = len(face_snippets_list)
#        print 'length of resposne to android: {}'.format(len(face_snippets_list))
        for idx, face_snippet in enumerate(face_snippets_list):
            face_snippets_string[str(idx)] = face_snippet

        result = json.dumps(face_snippets_string)
        return result
        
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


#        print result


    def handle(self, header, data):
        # pr = cProfile.Profile()
        # pr.enable()

        
        global prev_timestamp
        global transformer
        global DEBUG
        
        # locking to make sure tracker update thread is not interrupting
        transformer.tracking_thread_idle_event.clear()
        
        # PERFORM Cognitive Assistant Processing
        # header is a dict
        sys.stdout.write("processing: ")
        sys.stdout.write("%s\n" % header)

        if DEBUG:
            cur_timestamp = time.time()*1000
            interval = cur_timestamp - prev_timestamp
            sys.stdout.write("packet interval: %d\n"%interval)
            start = time.time()
        
        header_dict = header

        if 'reset' in header_dict:
            reset = header_dict['reset']
#            pdb.set_trace()            
            print 'reset openface state'            
            if reset:
#                transformer.terminate()
#                time.sleep(2)
#                transformer = FaceTransformation()
                transformer.openface_client.reset()                
                resp=self.gen_response(AppDataProtocol.TYPE_reset, True)
                transformer.training=False
                return resp

        if 'get_state' in header_dict:
            get_state = header_dict['get_state']
            print 'get openface state'            
            if get_state:
                state_string = transformer.openface_client.getState()
                resp=self.gen_response(AppDataProtocol.TYPE_get_state, state_string)

#                print 'send out response {}'.format(resp[:10])
#                sys.stdout.flush()
                return resp

        if 'load_state' in header_dict:
            state_string = header_dict['load_state']
            print 'loading openface state'            
            transformer.openface_client.setState(state_string)
            resp=self.gen_response(AppDataProtocol.TYPE_load_state, True)
            return resp
                
        if 'add_person' in header_dict:
            print 'adding person'
            name = header_dict['add_person']
            if isinstance(name, basestring):
                transformer.addPerson(name)
                transformer.training_cnt = 0                
                print 'training_cnt :{}'.format(transformer.training_cnt)
            else:
                raise TypeError('unsupported type for name of a person')
            resp=self.gen_response(AppDataProtocol.TYPE_add_person, name)
            
        elif 'face_table' in header_dict:
            face_table_string = header_dict['face_table']
            print face_table_string
            face_table = json.loads(face_table_string)
            transformer.face_table=face_table
            for from_person, to_person in face_table.iteritems():
                print 'mapping:'
                print '{0} <-- {1}'.format(from_person, to_person)
            sys.stdout.flush()

        training = False
        if 'training' in header_dict:
            training=True
            name=header_dict['training']

        # operate on client data
        image_raw = Image.open(io.BytesIO(data))
        image = np.array(image_raw)


        if training:
            cnt, face_json = transformer.train(image, name)
            if face_json is not None:
                msg = {
                    'num': 1,
                    'cnt': cnt,
                    '0': face_json
                }
                msg = json.dumps(msg)
            else:
                # time is a random number to avoid token leak
                msg = {
                    'num': 0,
                    'cnt': cnt,
                }
            resp= self.gen_response(AppDataProtocol.TYPE_train, msg)
        else:
            # swap faces
            snippets = self.process(image)
            resp= self.gen_response(AppDataProtocol.TYPE_detect, snippets)

        if DEBUG:
            end = time.time()
            print('total processing time: {}'.format((end-start)*1000))
            prev_timestamp = time.time()*1000

        transformer.tracking_thread_idle_event.set()

        # pr.disable()
        # s = StringIO.StringIO()
        # sortby = 'cumulative'
        # ps = pstats.Stats(pr, stream=s).sort_stats(sortby)
        # ps.print_stats()
        # print s.getvalue()
        
        return resp

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
    global transformer
    
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

