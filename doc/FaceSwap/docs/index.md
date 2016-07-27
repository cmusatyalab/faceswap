# FaceSwap Documentation

FaceSwap is used to demonstrate the critical role cloudlets play in shortening end-to-end latency for computation offloading mobile applications. 

FaceSwap demo consists of a front-end Android client and a back-end server performing.

## FaceSwap Android Client

FaceSwap Android client is available on Google Play:

[![Get it on Google Play](img/google-play-badge-small.png)](https://play.google.com/store/apps/details?id=edu.cmu.cs.faceswap)

## FaceSwap Server

### Cloudlet Setup

The FaceSwap server is wrapped into a virtual machine disk image. You can download it [here](https://storage.cmusatyalab.org/faceswap/faceswap-server-release.qcow). The best way to construct a faceswap server is to simply create and boot a virtual machine using provided faceswap server disk image.

### Cloud Setup

FaceSwap Android server disk image is available on Amazon EC2. The AMI name is "FaceSwap-server-release". The AMI ID in EC2 Oregon is ami-31c43351.
The source code is available [here](https://github.com/cmusatyalab/faceswap).

## Hardware Requirements

The recommended FaceSwap server should have 4 core and 8GM RAM. Similar EC2 instances are m4.xlarge and m4.large.
