#! /bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# copy upstart-conf to init
echo 'configuring upstart...'
sudo cp $DIR/upstart-conf/faceswap.conf /etc/init/ &&
sudo service faceswap restart &&
sudo initctl reload-configuration &&

# copy cron job 
echo 'adding cron email reminder...'
sudo cp $DIR/cron-bihourly-email /etc/cron.hourly/ &&
echo 'server setup succeeded.'
