#! /bin/bash

. /home/junjuew/torch/install/bin/torch-activate
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
gabriel-ucomm &
sleep 8
$DIR/openface-server/start_server.sh &
sleep 8
if pgrep -f "gabriel-ucomm" > /dev/null
then
    if [[ $debug -eq 1 ]];
    then
        echo 'debug mode...'
        $DIR/faceswap-proxy.py 2>&1 | tee $DIR/faceswap.log
    else
        $DIR/faceswap-proxy.py
    fi
else
    $DIR/kill_demo.sh
    echo "launch failed"
fi

