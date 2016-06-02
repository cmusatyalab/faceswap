#!/usr/bin/env python

import os
import sys
import matplotlib
import matplotlib.pyplot as plt
import pdb

def parse_mar(filename):
    """Read data from the file and generate CDF data
    """
    lines = open(filename, "r").read().split("\n")
    result_list = list()
    for line in lines:
        if not line.strip():
            continue
        response_time = line.split(",")[-1]
        response_time = float(response_time)
        result_list.append(response_time)

    result_list.sort()
    counter = len(result_list)
    min = result_list[0]
    max = result_list[-1]
    med = result_list[int(counter*0.5)]
    percentile_10 = result_list[int(counter*0.1)]
    percentile_90 = result_list[int(counter*0.9)]

    print "min\t0.1\tmedian\t0.9\tmax"
    print "%f\t%f\t%f\t%f\t%f" % (min, percentile_10, med, percentile_90, max)

    # plot
    x_items = list()
    y_items = list()
    for index, item in enumerate(result_list):
        percent = float(index+1)/counter
        x_items.append(item)
        y_items.append(percent)
    return x_items, y_items


def plotting(parsed_data):
    # figure plot
    fig, ax = plt.subplots()
    font = {'family' : 'Times New Roman',
            'weight' : 'normal',
            'size'   : 20}
    matplotlib.rc('font', **font)
    fig = matplotlib.pyplot.gcf()
    fig.gca().grid(True)

    fig.set_size_inches(10,5)   # figure size
    linewidth = 1               # line width
    plt.xlabel('(ms)')
    plt.ylabel('CDF')
    plt.title('Offload Latency Comparision')
    plt.xlim([0, 250])         # x-axis range

    for (label, line_style, x_list, y_list) in parsed_data:
        # if line_style.endswith("--"):
        #     plt.plot(x_list, y_list, line_style, label=label, linewidth=linewidth, dashes=(20,5))
        # else:
        #     if line_style.endswith("-."):
        #         plt.plot(x_list, y_list, line_style, label=label, linewidth=linewidth, dashes=(10,3,3,3))
        #     else:
        plt.plot(x_list, y_list, '-', color=line_style, label=label, linewidth=linewidth)
    plt.legend(loc='lower right', prop={'size':13})   # legend style
    plt.savefig('img/CDF.png', bbox_inches='tight')


if __name__ == "__main__":
    # [(filename, labelname, line drawing format), ..]
    # for line format, see at http://matplotlib.org/api/lines_api.html
    # input_files = [
    #      ("latency-lte-cloudlet-no-ping.txt", "LTE cloudlet without ping", "b-"),
    #      ("latency-lte-cloudlet-ping-1ms.txt", "LTE cloudlet with ping per 1ms", "g-"),
    #      ("latency-lte-cloud-no-ping.txt", "LTE cloud without ping", "m-"),
    #      ("latency-lte-cloud-ping-1ms.txt", "LTE cloud with ping per 1ms", "k-"),                                
    #      ("latency-wifi-cloudlet-no-ping.txt", "Wifi cloudlet without ping", "r-"),
    #      ("latency-wifi-cloudlet-ping-1ms.txt", "Wifi cloudlet with ping per 1ms", "c-"),
    #      ("latency-wifi-cloud-no-ping.txt", "Wifi cloud without ping", "y-"),
    #      ("latency-wifi-cloud-ping-1ms.txt", "Wifi cloud with ping per 1ms", "k-"),                
    # ]

    input_files = [
         ("cloudlet-latency.txt", "Cloudlet Latency", "black"),
         ("cloud-latency.txt", "Cloud (Amazon EC2-Oregon) Latency", "red"),
#         ("latency-lte-cloud-no-ping.txt", "LTE cloud without ping", "violet"),
#         ("latency-lte-cloud-ping-1ms.txt", "LTE cloud with ping per 1ms", "purple"),                                
#         ("latency-wifi-cloudlet-no-ping.txt", "Wifi cloudlet without ping", "limegreen"),
#         ("latency-wifi-cloudlet-ping-1ms.txt", "Wifi cloudlet with ping per 1ms", "green"),
#         ("latency-wifi-cloud-no-ping.txt", "Wifi cloud without ping", "gold"),
#         ("latency-wifi-cloud-ping-1ms.txt", "Wifi cloud with ping per 1ms", "darkorange"),                
    ]
    
    parsed_data = []
    for filename, label, line_style in input_files:
        x_list, y_list = parse_mar(filename)
        parsed_data.append((label, line_style, x_list, y_list))

    plotting(parsed_data)


