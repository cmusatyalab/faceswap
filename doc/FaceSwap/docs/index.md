# FaceSwap Documentation

FaceSwap is used to demonstrate the critical role cloudlets play in shortening end-to-end latency for computation offloading mobile applications. 

FaceSwap demo consists of a front-end Android client and a back-end server performing.

## Demo Video
[![Demo Video](http://img.youtube.com/vi/YSC-04jxS90/0.jpg)](http://www.youtube.com/watch?v=YSC-04jxS90)

## FaceSwap Android Client

FaceSwap Android client is available on Google Play:

[![Get it on Google Play](img/google-play-badge-small.png)](https://play.google.com/store/apps/details?id=edu.cmu.cs.faceswap)

## FaceSwap Server Setup

### Cloudlet Setup

The FaceSwap server is wrapped into a qcow2 virtual machine disk image. You can download it [here](https://storage.cmusatyalab.org/faceswap/faceswap-server-release.qcow). 

#### Method 1 OpenStack:

1. Import the qcow2 image into a running OpenStack following this [guide](http://docs.openstack.org/user-guide/dashboard_manage_images.html).
2. Launch that instance. FaceSwap server will automatically launch at start-up time.
5. After the instance has fully booted up, connect FaceSwap client to it for use. (see [User Guide](https://cmusatyalab.github.io/faceswap/user-guide/))

#### Method 2 KVM/QEMU

You can also directly create a virtual machine from the qcow2 image using KVM/QEMU. 

1. For ubuntu, you can install KVM/QEMU following [here](http://www.howtogeek.com/117635/how-to-install-kvm-and-create-virtual-machines-on-ubuntu/). 
2. Modify this [faceswap-server.xml](https://raw.githubusercontent.com/cmusatyalab/faceswap/master/server/faceswap-server.xml) to point to the downloaded path of your image. For instance, if the downloaded faceswap-server-release.qcow is at /home/junjuew/faceswap-server-release.qcow. Then the xml file should be modified into:

          ...
          <disk type='file' device='disk'>
           <driver name='qemu' type='qcow2'/>
           <source file='/home/junjuew/faceswap-setup/faceswap-server-release.qcow'/>
           <target dev='vda' bus='virtio'/>
          </disk>
          ...

3. Launch the virtual machine:

        sudo virsh create faceswap-server.xml    

4. The cloudlet image takes a bit longer to be fully booted up and initialized. You can monitor whether the virtual machine has fully booted up by checking whether you've arrived at log-in shell through virt-manager console.
5. Connect FaceSwap client to it for use. (see [User Guide](https://cmusatyalab.github.io/faceswap/user-guide/)). 

### Cloud Setup

1. Find FaceSwap Android server disk image on Amazon EC2 Oregon. The AMI ID in EC2 Oregon is **ami-31c43351**. The AMI name is **FaceSwap-server-release**. 
2. Create an instance from the AMI. The recommended EC2 instance types are m4.large, m4.xlarge, and more powerful ones.
3. Configure security group associated with that instance to open port **9098, 9101** for inbound TCP traffic. (If you want to customize the content of the image, though not recommended, you can open port 22 for ssh access. The default username:password is faceswap-admin:faceswap-admin. You're advised to change the password as soon as you open port 22 for debugging.)
4. Launch that instance. FaceSwap server will automatically launch at start-up time.
5. After the instance has fully booted up, connect FaceSwap client to it for use. (see [User Guide](https://cmusatyalab.github.io/faceswap/user-guide/))

## Source Code
The source code is available [here](https://github.com/cmusatyalab/faceswap/releases/tag/v1.0).

## Hardware Requirements
The recommended FaceSwap server should have 4 core and 8GM RAM. 

