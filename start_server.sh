#! /bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
$DIR/cloudlet-demo-openface-server.py 2>&1 | tee openface-server.log
