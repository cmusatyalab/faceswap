#! /bin/bash

echo -e "killing gabriel..."
# openface start_server process leaves dangling child...
gabriel=( "start_demo" "openface-server/start_server.sh" "faceswap-proxy.py" "gabriel-upnp" "gabriel_upnp_server" "gabriel_REST_server" "gabriel-ucomm" "gabriel-control" "cloudlet-demo-openface-server")
#gabriel=( "start_demo")
for i in "${gabriel[@]}"
do
    echo $i
    PID=`ps -eaf | grep ${i} | grep -v grep | awk '{print $2}'`
    if [[ "" !=  "$PID" ]]; then 
        while read -r line; do
            echo "killing $line"
            # kill process group
            pkill -KILL -g $line
            kill -KILL $line
        done <<< "$PID"
    fi
done
echo -e "All killed"

#       echo "interrupting $PPID"
       # kill -INT -$PID
       # sleep 3 &&
