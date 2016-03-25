#! /usr/bin/python
import asyncore
import pdb

import select

import sys

import multiprocessing
from websocket import create_connection, WebSocketException
import json
from PIL import Image
import base64
import numpy as np
import io, StringIO
import MyUtils
import threading
import time
import os
import logging
from NetworkProtocol import *

# receive is called synchronously
# all return result directly goes to recv and returned
class OpenFaceClient(object):
    def __init__(self, server_ip=u"ws://localhost", server_port=9000):
        self.logger=MyUtils.getLogger(__name__)
        self.logger.setLevel(logging.DEBUG)
        server_ip_port = server_ip + ':' +str(server_port)
        self.ws=create_connection(server_ip_port)

        # self.receive_thread = None
        # self.receive_thread_running = None
        #
        # self.async=async
        # if (self.async):
        #     self.receive_thread = threading.Thread(target=self.onReceive, name='receive_thread', args=(async_callback,))
        #     self.receive_thread_running = threading.Event()
        #     self.receive_thread_running.set()
        #     self.receive_thread.start()

#        if not self.async:
    def recv(self):
        try:
            resp = self.ws.recv()
            self.logger.debug('server said: {}'.format(resp[:]))
            return resp
        except WebSocketException as e:
            self.logger.debug("web socket error: {0}".format(e))

        
    def addPerson(self,person):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_add_person,
            'val': person
        }
        msg = json.dumps(msg)
        self.ws.send(msg)

    def setTraining(self,training_on):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_set_training,
            'val': training_on
        }
        msg = json.dumps(msg)
        self.ws.send(msg)

    def reset(self):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_set_state,
            'images': {},
            'people': [],
            'training': False
        }
        msg = json.dumps(msg)
        self.ws.send(msg)
        
    def setState(self, state_string):
        # state_json = json.loads(state_string)
        # msg = {
        #     'type': 'ALL_STATE',
        #     'images': images,
        #     'people': people,
        #     'training': training_on
        # }
        # msg = json.dumps(msg)
        self.ws.send(state_string)
        

    def getState(self):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_get_state
        }
        msg = json.dumps(msg)
        self.ws.send(msg)
        return self.recv()        

    def getPeople(self):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_get_people
        }
        out = json.dumps(msg)
        self.ws.send(out)
        return self.recv()        
        
    # current processing frame
    def addFrame(self, data_url, name):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_frame,
            'dataURL': data_url,
            'name': name
        }
        msg = json.dumps(msg)
        self.ws.send(msg)
        return self.recv()

    def removePerson(self,name):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_remove_person,
            'val': name
        }
        msg = json.dumps(msg)
        self.ws.send(msg)
        # resp = self.recv()
        # resp = json.loads(resp)
        # return resp['success']
        return self.recv()
        
    def terminate(self):
        self.ws.close()

    def isTraining(self):
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_get_training,
        }
        msg = json.dumps(msg)
        self.ws.send(msg)
        # resp = self.recv()
        # resp = json.loads(resp)
        # if resp['type'] != 'IS_TRAINING':
        #     raise ValueError
        # return resp['training']
        return self.recv()

class AsyncOpenFaceClientProcess(OpenFaceClient):
    def __init__(self, server_ip=u"ws://localhost", server_port=9000,
                 call_back=None,
                 queue=None,
                 busy_event=None   ):
        super(AsyncOpenFaceClientProcess, self).__init__(server_ip, server_port)
        self.call_back_fun = call_back
        self.shared_queue=queue
        self.receive_process = multiprocessing.Process(target=self.async_on_receive,
                                                name='receive_thread',
                                                args=(self.ws.sock,
                                                      call_back,
                                                      queue,
                                                      busy_event,
                                                  ))
        self.receive_process_running = multiprocessing.Event()
        self.receive_process_running.set()
        self.receive_process.start()

    def async_on_receive(self,
                         sock,
                         call_back,
                         queue,
                         busy_event):
        input = [sock]
        while True:
            inputready,outputready,exceptready = select.select(input,[],[])
            for s in inputready:
                if s == self.ws.sock:
                    try:
                        resp = self.ws.recv()
#                        self.logger.debug('server said: {}'.format(resp[:50]))
                        if call_back is not None:
                            call_back(resp, queue=queue, busy_event=busy_event)
                    except WebSocketException as e:
                        self.logger.debug("web socket error: {0}".format(e))

    # add id for sequencing
    def addFrameWithID(self, data_url, name, frame_id):
#        self.logger.debug('before send out request openfaceClient')
        msg = {
            'type': FaceRecognitionServerProtocol.TYPE_frame,
            'dataURL': data_url,
            'name': name,
            'id': frame_id
        }
        msg = json.dumps(msg)
        self.ws.send(msg)
        # sys.stdout.write('successful sent frame msg: {}'.format(msg))
        # sys.stdout.flush()
        
#        self.logger.debug('after send out request openfaceClient')
        return self.recv()

    def recv(self):
        pass

    def terminate(self):
        if None != self.receive_process_running:
            self.receive_process_running.clear()
        self.ws.close()

class AsyncOpenFaceClientThread(OpenFaceClient):
    def __init__(self, server_ip=u"ws://localhost", server_port=9000,
                 call_back=None):
        super(AsyncOpenFaceClientThread, self).__init__(server_ip, server_port)
        self.call_back_fun = call_back
        self.receive_thread = threading.Thread(target=self.async_on_receive,
                                               name='receive_thread',
                                               args=(call_back,))
        self.receive_thread_running = threading.Event()
        self.receive_thread_running.set()
        self.receive_thread.start()

    def async_on_receive(self, call_back):
        input = [self.ws.sock]
        while True:
            inputready,outputready,exceptready = select.select(input,[],[])
            for s in inputready:
                if s == self.ws.sock:
                    try:
                        resp = self.ws.recv()
                        self.logger.debug('server said: {}'.format(resp[:50]))
                        if call_back:
                            call_back(resp)
                    except WebSocketException as e:
                        self.logger.debug("web socket error: {0}".format(e))

    def recv(self):
        pass

    def terminate(self):
        if None != self.receive_thread_running:
            self.receive_thread_running.clear()
        self.ws.close()

if __name__ == '__main__':
#    client = OpenFaceClient()
    client = AsyncOpenFaceClientProcess()
#    pdb.set_trace()
    base_dir = '/home/junjuew/gabriel/gabriel/bin/img/'
    people_dir = ['Hu_Jintao', 'Jeb_Bush']
    test_dir = ['test']
    client.getState()

    for people in people_dir:
        client.addPerson(people)

    client.setTraining(True)
    for (idx, pdir) in enumerate(people_dir):
        cur_dir = base_dir+pdir
        for (dirpath, dirnames, filenames) in os.walk(cur_dir):
            for filename in filenames:
                print 'adding file: {}'.format(filename)
                image = Image.open(dirpath + '/' +filename)
                image_output = StringIO.StringIO()
                image.save(image_output, 'JPEG')

                jpeg_image = image_output.getvalue()
                face_string = base64.b64encode(jpeg_image)

                face_string = "data:image/jpeg;base64," + face_string
                client.addFrame(face_string, people_dir[idx])

    client.setTraining(False)

    for pdir in test_dir:
        cur_dir = base_dir+pdir
        for (dirpath, dirnames, filenames) in os.walk(cur_dir):
            for filename in filenames:
                print 'testing file: {}'.format(filename)
                image = Image.open(dirpath + '/' +filename)
                image_output = StringIO.StringIO()
                image.save(image_output, 'JPEG')

                jpeg_image = image_output.getvalue()
                face_string = base64.b64encode(jpeg_image)

                face_string = "data:image/jpeg;base64," + face_string
                client.addFrame(face_string, 'test')

    state=client.getState()
    print state
#    print client.setState(state)
    time.sleep(100)
    print 'waked up'
    client.terminate()
    
    # print "Sent"
    # print "Receiving..."
    # result =  ws.recv()
    # print "Received '%s'" % result
    # ws.close()

    # log.startLogging(sys.stdout)
    # client = OpenFaceClient(server_ip)


    # def getTSNE(self, people):
    #     msg = {
    #         'type': 'REQ_TSNE',
    #         'people': people
    #     }
    #     msg = json.dumps(msg)
    #     self.ws.send(msg)
    #     return self.recv()


    # def setState(self,images, people, training_on):
    #     msg = {
    #         'type': 'ALL_STATE',
    #         'images': images,
    #         'people': people,
    #         'training': training_on
    #     }
    #     msg = json.dumps(msg)
    #     self.ws.send(msg)


    # def onReceive(self, call_back):
    #     while (self.receive_thread_running.isSet()):
    #         try:
    #             resp = self.ws.recv()
    #             self.logger.debug('server said: {}'.format(resp))
    #             if call_back:
    #                 call_back(resp)
    #         except WebSocketException as e:
    #             self.logger.debug("web socket error: {0}".format(e))
