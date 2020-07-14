#! /usr/bin/env python
import sys

with open(sys.argv[1],'r') as f:
    lines = f.read().splitlines()
    total_offload_time=0
    total_server_time=0
    num=0
    for line in lines:
        if 'GabrielClientActivity' in line:
            text=line.split('offload-time:')[1]
            offload_time=int(text.split(';server-time:')[0])
            server_time=int(text.split(';server-time:')[1])
            total_offload_time+=offload_time
            total_server_time+=server_time
            num+=1

    avg_offload=float(total_offload_time)/num
    avg_server=float(total_server_time)/num
    print 'offload:{} server:{}'.format(avg_offload, avg_server)
