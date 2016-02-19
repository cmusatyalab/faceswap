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
fileDir = os.path.dirname(os.path.realpath(__file__))
sys.path.append(os.path.join(fileDir, "..", ".."))
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

import openface
DEBUG = True
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
    
modelDir = os.path.join(fileDir, '..', '..', 'models')
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
        return "{{id: {}, rep[0:10]: {}}}".format(
            str(self.identity),
            self.rep[0:2]
        )


images = {}
training = False
people = []
svm = None
        
class OpenFaceServerProtocol(WebSocketServerProtocol):

    def __init__(self):
        if DEBUG:
            global images
            global training
            global people
            global svm
            self.images = images
            self.training = training
            self.people = people
            if len(people) > 0:
                self.trainSVM()                
        else:
            self.images = {}
            self.training = False
            self.people = []
            self.svm = None
        if args.unknown:
            self.unknownImgs = np.load("./unknown.npy")

    def onConnect(self, request):
        print("Client connecting: {0}".format(request.peer))
        self.training = False
        print("server state: {0}".format(self.people))
        
    def onOpen(self):
        print("WebSocket connection open.")

    def onMessage(self, payload, isBinary):
        raw = payload.decode('utf8')
        msg = json.loads(raw)
        print("Received {} message of length {}.".format(
            msg['type'], len(raw)))
        if msg['type'] == "ALL_STATE":
            self.loadState(msg['images'], msg['training'], msg['people'])
        elif msg['type'] == "GET_STATE":
            self.getState()
        elif msg['type'] == "NULL":
            self.sendMessage('{"type": "NULL"}')
        elif msg['type'] == "FRAME":
            self.processFrame(msg['dataURL'], msg['name'])
#            self.sendMessage('{"type": "PROCESSED"}')
        elif msg['type'] == "TRAINING":
            self.training = msg['val']
            if not self.training:
                self.trainSVM()
        elif msg['type'] == "ADD_PERSON":
            name = msg['val'].encode('ascii', 'ignore')
            if name not in self.people:
                self.people.append(name)
            else:
                print('warning: duplicate name')
            print(self.people)
            
            if STORE_IMG_DEBUG:
                person_dir = EXPERIMENT+ '/' + name
                if os.path.exists(person_dir):
                    shutil.rmtree(person_dir)
                os.makedirs(person_dir)                
            
        elif msg['type'] == "UPDATE_IDENTITY":
            h = msg['hash'].encode('ascii', 'ignore')
            if h in self.images:
                self.images[h].identity = msg['idx']
                if not self.training:
                    self.trainSVM()
            else:
                print("Image not found.")
        elif msg['type'] == "REMOVE_IMAGE":
            h = msg['hash'].encode('ascii', 'ignore')
            if h in self.images:
                del self.images[h]
                if not self.training:
                    self.trainSVM()
            else:
                print("Image not found.")
        elif msg['type'] == 'REQ_TSNE':
            self.sendTSNE(msg['people'])
        elif msg['type'] == 'GET_TRAINING':
            resp = {
                'type':'IS_TRAINING',
                "training": self.training
            }
            self.sendMessage(json.dumps(resp))
        else:
            print("Warning: Unknown message type: {}".format(msg['type']))

    def onClose(self, wasClean, code, reason):
        print("WebSocket connection closed: {0}".format(reason))

    def loadState(self, jsImages, training, jsPeople):
        self.images = {}
        self.people =[]
        self.training = training

        for jsImage in jsImages:
            h = jsImage['hash'].encode('ascii', 'ignore')
            self.images[h] = Face(np.array(jsImage['representation']),
                                  jsImage['identity'])

        for jsPerson in jsPeople:
            self.people.append(jsPerson.encode('ascii', 'ignore'))

        if not training:
            self.trainSVM()

    def getState(self):
        # format it such that json can serialize
        images_serializable=[]
        for h, face in self.images.iteritems():
            images_serializable.append({
                'hash':h,
                'representation':list(face.rep),
                'identity':face.identity
                })
        msg ={
            'type': 'ALL_STATE',
            'images': images_serializable,
            'people': self.people,
            'training': self.training
        }
        self.sendMessage(json.dumps(msg))

    def getData(self):
        X = []
        y = []
        for img in self.images.values():
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
        print("+ Training SVM on {} labeled images.".format(len(self.images)))
        print("+ labeled images {}".format(self.images))        
        d = self.getData()
        if d is None:
            self.svm = None
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
            self.svm = GridSearchCV(SVC(C=1), param_grid, cv=5).fit(X, y)

    def processFrame(self, dataURL, name):
        head = "data:image/jpeg;base64,"
        assert(dataURL.startswith(head))
        imgdata = base64.b64decode(dataURL[len(head):])
        imgF = StringIO.StringIO()
        imgF.write(imgdata)
        imgF.seek(0)
        img = Image.open(imgF)
        (img_width, img_height) = img.size                

        buf = np.asarray(img)
        rgbFrame = np.copy(buf)
        
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

        identities = []
        # bbs = align.getAllFaceBoundingBoxes(rgbFrame)
#        bb = align.getLargestFaceBoundingBox(rgbFrame)

        # every image passed in assume it's a face image already
        # therefore the detected bounding box is just the whole image it self

        bb = dlib.rectangle(left=0, top=0, right=img_width-1, bottom=img_height-1)
        
        bbs = [bb] if bb is not None else []
        for bb in bbs:
            # print(len(bbs))
            # landmarks = align.findLandmarks(rgbFrame, bb)
            
            # alignedFace = align.align(args.imgDim, rgbFrame, bb,
            #                           landmarks=landmarks,
            #                           landmarkIndices=openface.AlignDlib.OUTER_EYES_AND_NOSE)

            # TODO: not providing bb will make openface to detect face again...
            # double detection here!
            alignedFace = align.align(args.imgDim, rgbFrame, bb=bb,
                                      landmarkIndices=openface.AlignDlib.OUTER_EYES_AND_NOSE)
            
            if alignedFace is None:
                if self.training:
                    msg = {
                        "type": "NEW_IMAGE",
                        "success": False
                    }
                    self.sendMessage(json.dumps(msg))
                print 'no face found. skip'
                continue
                
            aligned_img= Image.fromarray(alignedFace)
            phash = str(imagehash.phash(aligned_img))
            try:
                identity = self.people.index(name)
            except ValueError:
                identity = -1
                
            if phash in self.images:
                identity = self.images[phash].identity
                if self.training:
                    msg = {
                        "type": "NEW_IMAGE",
                        "success": False
                    }
                    self.sendMessage(json.dumps(msg))
            else:
                rep = net.forward(alignedFace)
                
                if STORE_IMG_DEBUG:
                    global rep_file
                    print >> rep_file, str(phash)+':\n'
                    print >> rep_file, str(rep)+'\n'
                    rep_file.flush()
                    
                if self.training:
                    self.images[phash] = Face(rep, identity)
                    msg = {
                        "type": "NEW_IMAGE",
                        "identity": identity,
                        "success": True                        
                    }
                    self.sendMessage(json.dumps(msg))
                    if STORE_IMG_DEBUG:
                        output_file = EXPERIMENT + '/' +str(name) +'/'+ phash + '.jpg'
                        img.save(output_file)
                        output_file_aligned = EXPERIMENT + '/' +str(name) +'/'+ phash + '_aligned.jpg'
                        aligned_img.save(output_file_aligned)                        
                        
                else:
                    print "detecting"
                    if len(self.people) == 0:
                        identity = -1
                    elif len(self.people) == 1:
                        identity = 0
                    elif self.svm:
                        identity = self.svm.predict(rep)[0]
                        print "svm result: {}".format(identity)
                    else:
                        print "hhh"
                        identity = -1
                    if identity not in identities:
                        identities.append(identity)

            if not self.training:
                if identity == -1:
                    if len(self.people) == 1:
                        name = self.people[0]
                    else:
                        name = "Unknown"
                else:
                    name = self.people[identity]

                msg = {
                    "type": "name",
#                    "identities": identities
                    "name": name                    
                }
                self.sendMessage(json.dumps(msg))
                global test_idx
                print "svm result {0}: {1}".format(test_idx, name)
                if STORE_IMG_DEBUG:
                    output_file = str(EXPERIMENT+'/test') +'/'+ str(test_idx) + '.jpg'
                    img.save(output_file)
                    test_idx +=1
                
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
