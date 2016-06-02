#! /bin/bash

USER=$(whoami)
echo "sourcing torch: "
echo "/home/${USER}/torch/install/bin/torch-activate"
source /home/${USER}/torch/install/bin/torch-activate
# turn debug on by default to log output 
debug=0
while getopts "d" opt; do
    case "$opt" in
    d)  debug=1
        ;;
    esac
done

# need to pull models down if they doesn't exist yet
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

openface_model_dir=$DIR/openface-server/models
dlib_face_model=$openface_model_dir/dlib/shape_predictor_68_face_landmarks.dat
openface_model=$openface_model_dir/openface/nn4.small2.v1.t7
if [ ! -f $dlib_face_model ] || [ ! -f $openface_model ]; then
    echo -e "start downloading models"
    $openface_model_dir/get-models.sh
fi

echo -e "launching FaceSwap server at dir $DIR"
gabriel-control &
sleep 5
# specify localhost and port to make sure we are connecting to the correct gabriel control server
gabriel-ucomm -s 127.0.0.1:8021 &
sleep 8
$DIR/openface-server/cloudlet-demo-openface-server.py 2>&1 &
#$DIR/openface-server/start_server.sh &
sleep 10

for trial in $(seq 1 5);
do
    if ! pgrep -f "openface_server.lua" > /dev/null;
    then
	echo 'checking openface server status:'
	echo $trial
	echo 'openface server has not finished starting. wait for another 20 seconds...'
	sleep 20
    else
	break
    fi
done
    
if pgrep -f "gabriel-ucomm" > /dev/null
then
    if [[ $debug -eq 1 ]];
    then
        echo 'debug mode...'
        $DIR/faceswap-proxy.py -s 127.0.0.1:8021 2>&1
    else
        $DIR/faceswap-proxy.py -s 127.0.0.1:8021 2>&1
    fi
else
    $DIR/kill_demo.sh
    echo "launch failed"
fi

