#! /usr/bin/env python
import sys

with open(sys.argv[1],'r') as f:
    lines = f.read().splitlines()
    total_offload_time=0
    total_server_time=0
    num=0
    offload_times=[]
    for line in lines:
        if 'GabrielClientActivity' in line:
            text=line.split('offload-time:')[1]
            offload_time=int(text.split(';server-time:')[0])
            server_time=int(text.split(';server-time:')[1])
            offload_times.append(offload_time)

with open(sys.argv[2],'w') as f:
    for time in offload_times:
        f.write('{}\n'.format(time))
