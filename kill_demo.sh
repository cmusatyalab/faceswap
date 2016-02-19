#! /bin/bash

echo -e "killing gabriel..."
gabriel=( "faceswap-proxy.py" "gabriel-upnp" "gabriel_upnp_server" "gabriel_REST_server" "gabriel-ucomm" "gabriel-control" "start_demo" )
for i in "${gabriel[@]}"
do
    echo $i
    PID=`ps -eaf | grep ${i} | grep -v grep | awk '{print $2}'`
    if [[ "" !=  "$PID" ]]; then
        echo "killing $PID"
        kill -9 $PID
    fi
done
echo -e "All killed"
