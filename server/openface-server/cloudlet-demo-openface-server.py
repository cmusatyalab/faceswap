#!/usr/bin/env python2
#
# Copyright 2015-2016 Carnegie Mellon University
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import sys

import time

fileDir = os.path.dirname(os.path.realpath(__file__))
#sys.path.append(os.path.join(fileDir, "..", ".."))
import pdb
import txaio
txaio.use_twisted()

from autobahn.twisted.websocket import WebSocketServerProtocol, \
    WebSocketServerFactory
from twisted.python import log
from twisted.internet import reactor

import argparse
import cv2
import dlib
import imagehash
import json
from PIL import Image
import numpy as np
import os, shutil
import StringIO
import urllib
import base64

from sklearn.decomposition import PCA
from sklearn.grid_search import GridSearchCV
from sklearn.manifold import TSNE
from sklearn.svm import SVC

import matplotlib as mpl
mpl.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.cm as cm
from NetworkProtocol import *
from threading import Lock
import openface
DEBUG = False
STORE_IMG_DEBUG = False
EXPERIMENT = 'e3'
test_idx =0
if STORE_IMG_DEBUG:
    test_dir = EXPERIMENT+ '/test'
    if os.path.exists(test_dir):
        shutil.rmtree(test_dir)
    os.makedirs(test_dir)
    rep_file_name = EXPERIMENT+ '/reps.txt'
    rep_file = open(rep_file_name, 'w+')
    
#modelDir = os.path.join(fileDir, '..', '..', 'models')
modelDir = os.path.join(fileDir, 'models')
dlibModelDir = os.path.join(modelDir, 'dlib')
openfaceModelDir = os.path.join(modelDir, 'openface')

parser = argparse.ArgumentParser()
parser.add_argument('--dlibFacePredictor', type=str, help="Path to dlib's face predictor.",
                    default=os.path.join(dlibModelDir, "shape_predictor_68_face_landmarks.dat"))
parser.add_argument('--networkModel', type=str, help="Path to Torch network model.",
                    default=os.path.join(openfaceModelDir, 'nn4.small2.v1.t7'))
parser.add_argument('--imgDim', type=int,
                    help="Default image dimension.", default=96)
parser.add_argument('--cuda', type=bool, default=False)
parser.add_argument('--unknown', type=bool, default=False,
                    help='Try to predict unknown people')
parser.add_argument('--port', type=int, default=9000,
                    help='WebSocket Port')

args = parser.parse_args()

align = openface.AlignDlib(args.dlibFacePredictor)
net = openface.TorchNeuralNet(args.networkModel, imgDim=args.imgDim,
                              cuda=args.cuda)


class Face:

    def __init__(self, rep, identity):
        self.rep = rep
        self.identity = identity

    def __repr__(self):
        return "{{id: {}, rep[0:3]: {}}}".format(
            str(self.identity),
            self.rep[0:3]
        )


images = {}
training = False
people = []
svm = None
svm_lock = Lock()

#TODO: non debug mode is not correct right now..
class OpenFaceServerProtocol(WebSocketServerProtocol):
    
    def __init__(self):
        if DEBUG:
            global images
            global people
            global svm
            global training
            global svm_lock
            if len(people) > 0:
                self.trainSVM()                
        else:
            self.images = {}
            self.training = False
            people = []
            self.svm = None
        if args.unknown:
            self.unknownImgs = np.load("./unknown.npy")

    def onConnect(self, request):
        global images
        global people
        global svm
        global training
        global svm_lock

        print("Client connecting: {0}".format(request.peer))
        training = False
        print("server state: {0}".format(people))
        
    def onOpen(self):
        print("WebSocket connection open.")

    def onMessage(self, payload, isBinary):
        global images
        global people
        global svm
        global training
        global svm_lock

        raw = payload.decode('utf-8')
        msg = json.loads(raw)
        if DEBUG:
            print("Received {} message of length {}.".format(
                msg['type'], len(raw)))
        if msg['type'] == FaceRecognitionServerProtocol.TYPE_set_state:
            print("loading state... {}".format(msg['people']))
            self.loadState(msg['images'],
                           msg['people'])
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_get_state:
            msg=self.getState()
            self.sendMessage(json.dumps(msg))
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_get_people:
            msg=self.getPeople()
            self.sendMessage(json.dumps(msg))
        elif msg['type'] == "NULL":
            self.sendMessage('{"type": "NULL"}')
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_frame:
            resp=self.processFrame(msg['dataURL'], msg['name'])
            if 'id' in msg:
                resp['id']=msg['id']
            self.sendMessage(json.dumps(resp))
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_set_training:
            training = msg['val']
            if not training:
                self.trainSVM()
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_add_person:
            name = msg['val'].encode('ascii', 'ignore')
            if name not in people:
                people.append(name)
            else:
                print('warning: duplicate name')
            print(people)
            
            if STORE_IMG_DEBUG:
                person_dir = EXPERIMENT+ '/' + name
                if os.path.exists(person_dir):
                    shutil.rmtree(person_dir)
                os.makedirs(person_dir)                
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_remove_person:
            name = msg['val'].encode('ascii', 'ignore')
            print 'remove {} from {}'.format(name, people)
            # remove identities from images
            try:
                remove_identity = people.index(name)
                # maintain the correspondence between identity and people name string
                images ={h:face for h,face in images.iteritems() if face.identity != remove_identity}
                for h,face in images.iteritems():
                    if face.identity > remove_identity:
                        face.identity=face.identity-1
                people.remove(name)
                print 'succesfully removed {}'.format(name)
                self.trainSVM()
                msg={
                    'type':FaceRecognitionServerProtocol.TYPE_remove_person_resp,
                    'val':True
                }
                print 'before send message'
                self.sendMessage(json.dumps(msg))
            except ValueError:
                print('error: Name to be removed is not found')
                msg={
                    'type':FaceRecognitionServerProtocol.TYPE_remove_person_resp,
                    'val':False
                }
                self.sendMessage(json.dumps(msg))
            print(people)
        elif msg['type'] == FaceRecognitionServerProtocol.TYPE_get_training:
            resp = {
                'type':FaceRecognitionServerProtocol.TYPE_get_training_resp,
                'val': training
            }
            self.sendMessage(json.dumps(resp))
        else:
            print("Warning: Unknown message type: {}".format(msg['type']))

    def onClose(self, wasClean, code, reason):
        print("WebSocket connection closed: {0}".format(reason))

    def loadState(self, jsImages, jsPeople):
        global images
        global people
        global svm
        global training
        global svm_lock

        images = {}
        people =[]
        training = False

        for jsImage in jsImages:
            h = jsImage['hash'].encode('ascii', 'ignore')
            rep_bytes=base64.b64decode(jsImage['representation'])
            face_rep = np.fromstring(rep_bytes, dtype=np.float64)
            images[h] = Face(face_rep,
                                  jsImage['identity'])
        for jsPerson in jsPeople:
            people.append(jsPerson.encode('ascii', 'ignore'))

        self.trainSVM()

    def getState(self):
        global images
        global people
        global svm
        global training
        global svm_lock
        
        # format it such that json can serialize
        images_serializable=[]
        for h, face in images.iteritems():
#            print str(face)
#            print type(face.rep[0])
            # use tostring for numpy backward compatibility
            # alias for tobytes
            rep_bytes= face.rep.tostring()
            rep_string = base64.b64encode(rep_bytes)            
            images_serializable.append({
                'hash':h,
                'representation':rep_string,
                'identity':face.identity
                })
        msg ={
            'type': FaceRecognitionServerProtocol.TYPE_get_state_resp,
            'images': images_serializable,
            'people': people,
            'val':True
        }
        return msg


    def getPeople(self):
        global images
        global people
        global svm
        global training
        global svm_lock

        self.remove_people_with_insufficient_images()
        # format it such that json can serialize
        msg ={
            'type':FaceRecognitionServerProtocol.TYPE_get_people_resp,
            'people': people
        }
        return msg


    def remove_people_with_insufficient_images(self):
        global images
        global people
        no_image_people=[]
        to_be_removed_images=[]
        for (idx,person) in enumerate(people):
            person_images_hashes=[h for h,face in images.iteritems() if face.identity == idx]
            print '{} has {} images'.format(person,len(person_images_hashes))
            if len(person_images_hashes) < 6:
                print 'removing {}'.format(person)
                no_image_people.append(idx)
                to_be_removed_images.extend(person_images_hashes)

        for hash in to_be_removed_images:
            del images[hash]
        people = [person for (idx,person) in enumerate(people) if idx not in no_image_people]
        
        
    def getData(self):
        global images
        global people
        global svm
        global training
        global svm_lock

        self.remove_people_with_insufficient_images()
        X = []
        y = []
        for img in images.values():
            X.append(img.rep)
            y.append(img.identity)

        numIdentities = len(set(y + [-1])) - 1
        if numIdentities == 0:
            return None

        if args.unknown:
            numUnknown = y.count(-1)
            numIdentified = len(y) - numUnknown
            numUnknownAdd = (numIdentified / numIdentities) - numUnknown
            if numUnknownAdd > 0:
                print("+ Augmenting with {} unknown images.".format(numUnknownAdd))
                for rep in self.unknownImgs[:numUnknownAdd]:
                    # print(rep)
                    X.append(rep)
                    y.append(-1)

        X = np.vstack(X)
        y = np.array(y)
        return (X, y)

    def sendTSNE(self, people):
        d = self.getData()
        if d is None:
            return
        else:
            (X, y) = d

        X_pca = PCA(n_components=50).fit_transform(X, X)
        tsne = TSNE(n_components=2, init='random', random_state=0)
        X_r = tsne.fit_transform(X_pca)

        yVals = list(np.unique(y))
        colors = cm.rainbow(np.linspace(0, 1, len(yVals)))

        # print(yVals)

        plt.figure()
        for c, i in zip(colors, yVals):
            name = "Unknown" if i == -1 else people[i]
            plt.scatter(X_r[y == i, 0], X_r[y == i, 1], c=c, label=name)
            plt.legend()

        imgdata = StringIO.StringIO()
        plt.savefig(imgdata, format='png')
        imgdata.seek(0)

        content = 'data:image/png;base64,' + \
                  urllib.quote(base64.b64encode(imgdata.buf))
        msg = {
            "type": "TSNE_DATA",
            "content": content
        }
        self.sendMessage(json.dumps(msg))

    def trainSVM(self):
        global images
        global people
        global svm
        global training
        global svm_lock

        print("+ Training SVM on {} labeled images.".format(len(images)))
#        print("+ labeled images {}".format(images))

        # clean images first

        d = self.getData()
        if d is None:
            svm = None
            return
        else:
            (X, y) = d
#            numIdentities = len(set(y + [-1]))
            numIdentities = len(set(y))            
            if numIdentities <= 1:
                return

            param_grid = [
                {'C': [1, 10, 100, 1000],
                 'kernel': ['linear']},
                {'C': [1, 10, 100, 1000],
                 'gamma': [0.001, 0.0001],
                 'kernel': ['rbf']}
            ]
            svm_lock.acquire()
            svm = GridSearchCV(SVC(C=1), param_grid, cv=5).fit(X, y)
            svm_lock.release()
            print("successfully trained svm {}".format(svm))                    

    def processFrame(self, dataURL, name):
        global images
        global people
        global svm
        global training
        global svm_lock

        head = "data:image/jpeg;base64,"
        if (not dataURL.startswith(head)):
            print('received wrong dataURL. not a jpeg image')
            msg={
                'type':FaceRecognitionServerProtocol.TYPE_frame_resp,
                'success':False
            }
            return msg
        imgdata = base64.b64decode(dataURL[len(head):])
        imgF = StringIO.StringIO()
        imgF.write(imgdata)
        imgF.seek(0)
        img = Image.open(imgF)
        (img_width, img_height) = img.size                

        buf = np.asarray(img)
        rgbFrame = np.copy(buf)
        
        # every image passed in assume it's a face image already
        # therefore the detected bounding box is just the whole image it self
        bb = dlib.rectangle(left=0, top=0, right=img_width-1, bottom=img_height-1)
        resp=None

        # not providing bb will make openface to detect face again...
        # double detection here!
        alignedFace = align.align(args.imgDim, rgbFrame, bb=bb,
                                  landmarkIndices=openface.AlignDlib.OUTER_EYES_AND_NOSE)

        if alignedFace is None:
            msg = {
                "type": FaceRecognitionServerProtocol.TYPE_frame_resp,
                "success": False
            }
            print('no face found. skip')
            return  msg

        aligned_img= Image.fromarray(alignedFace)
        phash = str(imagehash.phash(aligned_img))
        try:
            identity = people.index(name)
        except ValueError:
            identity = -1
        if phash in images:
            identity = images[phash].identity
            if training:
                msg = {
                    "type": FaceRecognitionServerProtocol.TYPE_frame_resp,
                    "success": False
                }
                resp=msg
        else:
            if DEBUG:
                start =time.time()

            rep = net.forward(alignedFace)

            if DEBUG:
                print('net forward time: {} ms'.format((time.time()-start)*1000))
            if STORE_IMG_DEBUG:
                global rep_file
                print >> rep_file, str(phash)+':\n'
                print >> rep_file, str(rep)+'\n'
                rep_file.flush()

            if training:
                images[phash] = Face(rep, identity)
                msg = {
                    "type": FaceRecognitionServerProtocol.TYPE_frame_resp,
                    "identity": identity,
                    "success": True
                }
                resp=msg
                if STORE_IMG_DEBUG:
                    output_file = EXPERIMENT + '/' +str(name) +'/'+ phash + '.jpg'
                    img.save(output_file)
                    output_file_aligned = EXPERIMENT + '/' +str(name) +'/'+ phash + '_aligned.jpg'
                    aligned_img.save(output_file_aligned)
            else:
#                print 'detecting. known people: {}'.format(people) 
                if len(people) == 0:
                    identity = -1
                elif len(people) == 1:
                    identity = 0
                elif svm:
                    svm_lock.acquire()
                    identity = svm.predict(rep)[0]
                    svm_lock.release()
                else:
                    print "No SVM trained"
                    identity = -1

        if not training:
            if identity == -1:
                if len(people) == 1:
                    name = people[0]
                else:
                    print "svm detect result unknown!"
                    name = ""
            else:
                name = people[identity]

            msg = {
                "type": FaceRecognitionServerProtocol.TYPE_frame_resp,
                'success':True,
                "name": name
            }
            resp=msg

            global test_idx
            print "svm result {0}: {1}".format(test_idx, name)
            if STORE_IMG_DEBUG:
                output_file = str(EXPERIMENT+'/test') +'/'+ str(test_idx) + '.jpg'
                img.save(output_file)
                test_idx +=1
        return resp

                # bl = (bb.left(), bb.bottom())
                # tr = (bb.right(), bb.top())
                # cv2.rectangle(annotatedFrame, bl, tr, color=(153, 255, 204),
                #               thickness=3)
                
                # for p in openface.AlignDlib.OUTER_EYES_AND_NOSE:
                #     cv2.circle(annotatedFrame, center=landmarks[p], radius=3,
                #                color=(102, 204, 255), thickness=-1)
                    
                    
                # cv2.putText(annotatedFrame, name, (bb.left(), bb.top() - 10),
                #             cv2.FONT_HERSHEY_SIMPLEX, fontScale=0.75,
                #             color=(152, 255, 204), thickness=2)

        # no need to send frames back
        # need to send boundingbox and name/id back
        
        # if not self.training:
        #     msg = {
        #         "type": "IDENTITIES",
        #         "identities": identities
        #     }
        #     self.sendMessage(json.dumps(msg))

        #     plt.figure()
        #     plt.imshow(annotatedFrame)
        #     plt.xticks([])
        #     plt.yticks([])

        #     imgdata = StringIO.StringIO()
        #     plt.savefig(imgdata, format='png')
        #     imgdata.seek(0)
        #     content = 'data:image/png;base64,' + \
        #         urllib.quote(base64.b64encode(imgdata.buf))
        #     msg = {
        #         "type": "ANNOTATED",
        #         "content": content
        #     }
        #     plt.close()
        #     self.sendMessage(json.dumps(msg))

if __name__ == '__main__':
    log.startLogging(sys.stdout)

    # factory = WebSocketServerFactory("ws://localhost:{}".format(args.port),
    #                                  debug=False)
    
    factory = WebSocketServerFactory()
    
    factory.protocol = OpenFaceServerProtocol

    reactor.listenTCP(args.port, factory)
    reactor.run()


        # buf = np.fliplr(np.asarray(img))
        # rgbFrame = np.zeros((img_height, img_width, 3), dtype=np.uint8)
        # rgbFrame[:, :, 0] = buf[:, :, 2]
        # rgbFrame[:, :, 1] = buf[:, :, 1]
        # rgbFrame[:, :, 2] = buf[:, :, 0]

#        if STORE_IMG_DEBUG:
#            img.save('test.jpg')
#            cv2.imwrite('cv_test.jpg', rgbFrame)            
#            cv2.imshow('frame', rgbFrame)
#            if cv2.waitKey(1) & 0xFF == ord('q'):
#                return

        # bbs = align.getAllFaceBoundingBoxes(rgbFrame)
#        bb = align.getLargestFaceBoundingBox(rgbFrame)

    
        # print(len(bbs))
        # landmarks = align.findLandmarks(rgbFrame, bb)

        # alignedFace = align.align(args.imgDim, rgbFrame, bb,
        #                           landmarks=landmarks,
        #                           landmarkIndices=openface.AlignDlib.OUTER_EYES_AND_NOSE)

