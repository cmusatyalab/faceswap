#!/usr/bin/env bash
set -e

######################################################################
# This script installs required dependencies for FaceSwap Application
######################################################################
{
install_openblas() {
    # Get and build OpenBlas (Torch is much better with a decent Blas)
    cd /tmp/
    rm -rf OpenBLAS
    git clone https://github.com/xianyi/OpenBLAS.git
    cd OpenBLAS
    if [ $(getconf _NPROCESSORS_ONLN) == 1 ]; then
        make NO_AFFINITY=1 USE_OPENMP=0 USE_THREAD=0
    else
        make NO_AFFINITY=1 USE_OPENMP=1
    fi
    RET=$?;
    if [ $RET -ne 0 ]; then
        echo "Error. OpenBLAS could not be compiled";
        exit $RET;
    fi
    sudo make install
    RET=$?;
    if [ $RET -ne 0 ]; then
        echo "Error. OpenBLAS could not be installed";
        exit $RET;
    fi
}

install_opencv(){
    # opencv 
    apt-get -y autoremove libopencv-dev python-opencv
    apt-get -y install build-essential
    apt-get -y install cmake git libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev unzip
    apt-get -y install python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev

    cd $DIR && \
        mkdir -p opencv-src && \
        cd opencv-src && \
        curl -L \
        https://github.com/Itseez/opencv/archive/3.1.0.zip \
        -o opencv.3.1.0.zip && \
        unzip opencv.3.1.0.zip && \
        cd opencv-3.1.0 && \
        mkdir -p build && \
        cd build && \
        cmake -D CMAKE_BUILD_TYPE=Release -D CMAKE_INSTALL_PREFIX=/usr/local -DBUILD_EXAMPLES=ON .. && \
        make -j8
    make install
}

install_dlib(){
    DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    sudo apt-get -y install cmake libboost-all-dev python-skimage
    cd $DIR
    mkdir -p dlib-src
    cd dlib-src
    curl -L http://dlib.net/files/dlib-18.18.tar.bz2 \
        -o dlib.tar.bz2
    tar -xf dlib.tar.bz2
    cd dlib-18.18/python_examples
    mkdir -p build
    cd build
    cmake ../../tools/python -DUSE_AVX_INSTRUCTIONS=ON 2>&1 | tee make.log
    cmake --build . --config Release 2>&1 | tee build.log
    cd ..
    cp dlib.so /usr/local/lib/python2.7/dist-packages
}

clear
echo "============================================"
echo "Gabriel Server Install Script"
echo "============================================"

THIS_DIR=$(cd $(dirname $0); pwd)
PREFIX=${PREFIX:-"${THIS_DIR}/install"}
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update
sudo apt-get install -y build-essential gcc g++ curl \
    cmake libreadline-dev git-core 
sudo apt-get install -y libopenblas-dev liblapack-dev 
sudo apt-get install python-numpy
install_dlib || true
}
