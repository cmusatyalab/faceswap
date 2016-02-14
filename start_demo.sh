#! /bin/bash

debug=0
while getopts "d" opt; do
    case "$opt" in
    d)  debug=1
        ;;
    esac
done

echo -e $gabriel_home
gabriel_control="${gabriel_home}/bin"
cd $gabriel_control &&
./gabriel-control &
sleep 3 &&
./gabriel-ucomm &
sleep 8 &&
if pgrep -f "gabriel-ucomm" > /dev/null
then
    if [[ $debug -eq 1 ]];
    then
        echo 'debug mode...'
        ./faceswap-proxy.py 2>&1 | tee faceswap.log
    else
        ./faceswap-proxy.py
    fi
else
    ./kill_demo.sh
    echo "launch failed"
fi

