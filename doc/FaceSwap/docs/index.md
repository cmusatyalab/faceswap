# FaceSwap Documentation

FaceSwap is used to demonstrate the critical role cloudlets play in shortening end-to-end latency for computation offloading mobile applications. 

FaceSwap demo consists of a front-end Android client and a back-end server performing.

## Demo Video
[![Demo Video](http://img.youtube.com/vi/YSC-04jxS90/0.jpg)](http://www.youtube.com/watch?v=YSC-04jxS90)

## FaceSwap Android Client

FaceSwap Android client is available on Google Play:

[![Get it on Google Play](img/google-play-badge-small.png)](https://play.google.com/store/apps/details?id=edu.cmu.cs.faceswap)

You can also directly download the apk [here](https://github.com/cmusatyalab/faceswap/blob/master/apk/app-release.apk?raw=true). To install this apk directly through adb, follow this [guide](https://developer.vuforia.com/library/articles/Solution/How-To-install-an-APK-using-ADB).

## FaceSwap Server Setup

### Use Pre-Packaged Images

#### Cloudlet Setup

The FaceSwap server is wrapped into a qcow2 virtual machine disk image.

* If your virtual machine has AVX instruction support, use [this image](https://storage.cmusatyalab.org/faceswap/faceswap-server-release.qcow). 
* If your virtual machine doesn't have AVX instruction support, use [this image](https://storage.cmusatyalab.org/faceswap/faceswap-server-release-core2duo.qcow). 

Intel microarchitecture SandyBridge and newer microarchitectures have support for AVX. If you are not sure, run following command in your vm

      cat /proc/cpuinfo | grep avx

If you see outputs with highlighted avx, your machine has AVX support. Otherwise, it doesn't.

##### Method 1 OpenStack:

1. Import the qcow2 image into a running OpenStack following this [guide](http://docs.openstack.org/user-guide/dashboard_manage_images.html).
2. Launch that instance. FaceSwap server will automatically launch at start-up time.
5. After the instance has fully booted up, connect FaceSwap client to it for use. (see [User Guide](https://cmusatyalab.github.io/faceswap/user-guide/))

##### Method 2 KVM/QEMU

You can also directly create a virtual machine from the qcow2 image using KVM/QEMU. 

1. For ubuntu, you can install KVM/QEMU following [here](http://www.howtogeek.com/117635/how-to-install-kvm-and-create-virtual-machines-on-ubuntu/). 
2. Modify correct domain XML file below to point to the downloaded path of your image. For instance, if the downloaded faceswap-server-release.qcow is at /home/junjuew/faceswap-server-release.qcow. Then the xml file should be modified into:

          ...
          <disk type='file' device='disk'>
           <driver name='qemu' type='qcow2'/>
           <source file='/home/junjuew/faceswap-setup/faceswap-server-release.qcow'/>
           <target dev='vda' bus='virtio'/>
          </disk>
          ...

     * for host with AVX: [faceswap-server.xml](https://raw.githubusercontent.com/cmusatyalab/faceswap/master/server/faceswap-server.xml)
     * for host without AVX: [faceswap-server-core2duo.xml](https://raw.githubusercontent.com/cmusatyalab/faceswap/master/server/faceswap-server-core2duo.xml) 

3. Launch the virtual machine:

        sudo virsh create faceswap-server.xml    

4. The cloudlet image takes a bit longer to be fully booted up and initialized. You can monitor whether the virtual machine has fully booted up by checking whether you've arrived at log-in shell through virt-manager console.
5. Connect FaceSwap client to it for use. (see [User Guide](https://cmusatyalab.github.io/faceswap/user-guide/)). 

#### Cloud Setup

1. Find FaceSwap Android server disk image on Amazon EC2 Oregon/Ireland. The AMI ID in EC2 Oregon is **ami-31c43351**. The AMI ID in EC2 Ireland is **ami-b0abc7c3**. The AMI name is **FaceSwap-server-release**. 
2. Create an instance from the AMI. The recommended EC2 instance types are m4.large, m4.xlarge, and more powerful ones.
3. Configure security group associated with that instance to open port **9098, 9101** for inbound TCP traffic. 
4. Launch that instance. FaceSwap server will automatically launch at start-up time.
5. After the instance has fully booted up, connect FaceSwap client to it for use. (see [User Guide](https://cmusatyalab.github.io/faceswap/user-guide/))

#### Access Disk Image Content

If you want to customize the content of the image, the default username:password is **faceswap-admin:faceswap-admin**. You're advised to change the password as soon as you gain access.

The log file of FaceSwap is at /var/log/FaceSwap.log

### By Hand

These instructions are based on ubuntu 14.04. 

1. Install OpenFace and its dependency by hand. See [guide](https://cmusatyalab.github.io/openface/setup/)

2. Download this Gabriel [release](https://github.com/cmusatyalab/gabriel/archive/mobisys2016submission.zip). Install its dependency and gabriel:

            sudo apt-get install -y gcc python-dev default-jre python-pip pssh python-psutil &&
            sudo pip install \
                    Flask==0.9 \
                    Flask-RESTful \
                    Jinja2==2.8 \
                    MarkupSafe==0.23 \
                    pycrypto \
                    six \
                    Werkzeug==0.11.10 &&
            wget https://github.com/cmusatyalab/gabriel/archive/mobisys2016submission.zip &&
            sudo apt-get install -y unzip &&
            unzip mobisys2016submission.zip &&
            cd gabriel-mobisys2016submission &&
            sudo python setup.py install            
   
        
3. Download FaceSwap [source code](https://github.com/cmusatyalab/faceswap/archive/v1.0.zip). Install its dependency and start it by invoking server/start_demo

            mkdir faceswap &&
            cd faceswap &&
            wget https://github.com/cmusatyalab/faceswap/archive/v1.0.zip &&
            unzip v1.0.zip &&
            cd ./faceswap-1.0/server/ &&
            sudo pip install \
              websocket-client==0.35.0 \
              autobahn==0.10.4 \
              imagehash==1.0 \
              twisted==15.2.1 \
              scipy==0.14 \
              scikit-learn==0.17 \
              protobuf==2.5 &&
            ./start_demo
If you didn't install Torch at ~/torch, please edit server/start_demo.sh to source torch-activate at your installed location at line 6.

4. To stop demo use server/kill_demo.sh
           

## Source Code
The source code is available [here](https://github.com/cmusatyalab/faceswap/releases/tag/v1.0).

## Hardware Requirements
The recommended FaceSwap server should have 4 core and 8GM RAM. 

