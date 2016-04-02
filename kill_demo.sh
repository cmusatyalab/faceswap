#! /bin/bash

echo -e "killing gabriel..."
# openface start_server process leaves dangling child...
gabriel=( "start_demo" "openface-server/start_server.sh" "faceswap-proxy.py" "gabriel-upnp" "gabriel_upnp_server" "gabriel_REST_server" "gabriel-ucomm" "gabriel-control" )
#gabriel=( "start_demo")
for i in "${gabriel[@]}"
do
    echo $i
    PID=`ps -eaf | grep ${i} | grep -v grep | awk '{print $2}'`
    if [[ "" !=  "$PID" ]]; then 
       echo "killing $PID"
       # kill process group
       pkill -KILL -g $PID
#       kill -KILL $PID
    fi
done
echo -e "All killed"

#       echo "interrupting $PPID"
       # kill -INT -$PID
       # sleep 3 &&
