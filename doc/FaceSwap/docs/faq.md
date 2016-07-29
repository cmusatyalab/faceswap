#### 1. I'm getting "Illegal instruction (core dumped)" in my log file

It's likely that your cpu doesn't support AVX instruction. dlib uses AVX to make face detection faster. To check if AVX instruction is available on your machine, use

           grep avx /proc/cpuinfo

To resolve this problem:

##### Method 1

Use an AVX enabled machine.

##### Method 2

If you are using the pre-packaged image, uninstall dlib first using

             sudo rm /usr/local/lib/python2.7/dist-packages/dlib.so
             
Then install dlib using pip:

             sudo pip install dlib

#### 2. I'm getting following ferror when using server/start_demo.sh

>         "<urlopen error [Errno 111] Connection refused>"

>         "failed to register UCOMM to control"

To resolve this problem:

Edit line 29 "sleep 5" in server/start_demo.sh to a sleep longer time, for example, "sleep 15". 

Changing the sleep time to a larger value gives one of gabriel server more time to boot up. The original sleep time may not be enough if you are running the server on a slow virtual machine.
