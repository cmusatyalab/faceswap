#! /bin/bash

# turn debug on by default to log output 
debug=1
while getopts "d" opt; do
    case "$opt" in
    d)  debug=1
        ;;
    esac
done

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
echo -e "launching FaceSwap server at dir $DIR"
gabriel-control &
sleep 5
gabriel-ucomm &
sleep 8
$DIR/openface-server/start_server.sh &
sleep 8
if pgrep -f "gabriel-ucomm" > /dev/null
then
    if [[ $debug -eq 1 ]];
    then
        echo 'debug mode...'
        $DIR/faceswap-proxy.py 2>&1 | tee faceswap.log
    else
        $DIR/faceswap-proxy.py
    fi
else
    $DIR/kill_demo.sh
    echo "launch failed"
fi

