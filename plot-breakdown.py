#!/usr/bin/env python2

import os
import sys
import matplotlib
import matplotlib.pyplot as plt
import pdb
import numpy as np

from optparse import OptionParser

def parse_data(foldername, filename):
    """Read data from the file and generate CDF data
    """
    ## get the client data and server data synced in terms of frame id
    client_file = os.path.join(foldername, "client-" + filename)
    server_file = os.path.join(foldername, "server-" + filename)
    with open(client_file, "r") as f:
        client_lines = f.read().split("\n")
    if (len(client_lines) <= 1):
        x_items = y_items = \
        c_start = c_compress = c_response= c_done = \
        s_control_recv = s_app_recv = s_app_symbolic = \
        s_app_sent = s_ucomm_recv = s_ucomm_sent = \
        s_control_sent = [0]
        return (x_items, y_items), (c_start, c_compress, c_response, c_done, s_control_recv, s_app_recv, s_app_symbolic, s_app_sent, s_ucomm_recv, s_ucomm_sent, s_control_sent)

    sync_line = client_lines[1]
    client_lines = client_lines[3:]
    with open(server_file, "r") as f:
        server_lines = f.read().split("\n")
    server_lines = server_lines[1:]

    if len(server_lines) > len(client_lines):
        server_lines = server_lines[:-1]

    ## sync time on client and server
    sync_splits = sync_line.strip().split('\t')
    client_offset = float(sync_splits[1]) - (float(sync_splits[0]) + float(sync_splits[2])) / 2
    #print "offset: %d" % client_offset

    ## combine server and client data and get different lists
    c_start = []
    c_compress = []
    c_response = []
    c_done = []
    s_control_recv = []
    s_app_recv = []
    s_app_symbolic = []
    s_app_sent = []
    s_ucomm_recv = []
    s_ucomm_sent = []
    s_control_sent = []
    for line_idx in xrange(len(client_lines)):
        client_line = client_lines[line_idx]
        if not client_line.strip(): # the last line may contain nothing
            break
        server_line = server_lines[line_idx]
        status = client_line.split("\t")[6]
        if status != "success":
            continue
        c_start.append(float(client_line.split("\t")[2]) + client_offset)
        c_compress.append(float(client_line.split("\t")[3]) + client_offset)
        c_response.append(float(client_line.split("\t")[4]) + client_offset)
        c_done.append(float(client_line.split("\t")[5]) + client_offset)
        s_control_recv.append(float(server_line.split("\t")[1]) * 1000)
        s_app_recv.append(float(server_line.split("\t")[2]) * 1000)
        s_app_symbolic.append(float(server_line.split("\t")[3]) * 1000)
        s_app_sent.append(float(server_line.split("\t")[4]) * 1000)
        s_ucomm_recv.append(float(server_line.split("\t")[5]) * 1000)
        s_ucomm_sent.append(float(server_line.split("\t")[6]) * 1000)
        s_control_sent.append(float(server_line.split("\t")[7]) * 1000)

    result_list = list()
    for idx in xrange(len(c_start)):
        latency = c_response[idx] - c_start[idx]
        result_list.append(latency)

    result_list.sort()
    counter = len(result_list)
    min = result_list[0]
    max = result_list[-1]
    med = result_list[int(counter*0.5)]
    percentile_10 = result_list[int(counter*0.1)]
    percentile_90 = result_list[int(counter*0.9)]

    #print "min\t0.1\tmedian\t0.9\tmax"
    #print "%f\t%f\t%f\t%f\t%f" % (min, percentile_10, med, percentile_90, max)

    # plot
    x_items = list()
    y_items = list()
    for index, item in enumerate(result_list):
        percent = float(index+1)/counter
        x_items.append(item)
        y_items.append(percent)

    # A work-around for a bug - GPU machine and the proxy server are not synchronized
    if "cooking" in filename: #Cooking GPU
        #print "cooking-gpu"
        s_app_symbolic = s_app_sent

    return (x_items, y_items), (c_start, c_compress, c_response, c_done, s_control_recv, s_app_recv, s_app_symbolic, s_app_sent, s_ucomm_recv, s_ucomm_sent, s_control_sent)

def plot_breakdown():
    fig, ax = plt.subplots()

    # Figure parameters - default
    font_size = 24
    figure_width = 5
    figure_height = 5
    xticks_rotation = 0
    width = 0.5

    font = {'family' : 'Times New Roman',
            'weight' : 'normal',
            'size'   : font_size}
    matplotlib.rc('font', **font)
    fig = matplotlib.pyplot.gcf()
    fig.set_size_inches(figure_width, figure_height)   # figure size

    labels=['cloudlet', 'cloud']
    N = len(labels)
    #print labels
    #print t_compresses, t_ups, t_syms, t_tasks, t_downs, t_guides
    ind = np.arange(N)    # the x locations for the groups

    #colors = ['grey', 'r', 'gold', 'c', 'b']
    colors = ['brown', 'limegreen', 'gold', 'w', 'royalblue']
#    colors = ['limegreen', 'gold', 'w', 'royalblue']

    # cloudlet, cloud
    t_network=[60.52, 128.88]
    t_server_computation=[14.23, 11.95]
    data = [t_network, t_server_computation]
    print data
    p = [None] * len(data)
    current_height = np.array([0]*len(data))
#    pdb.set_trace()
    for i in xrange(len(data)):
        p[i] = plt.bar(ind, data[i], width, color=colors[i], bottom = current_height)
        current_height += data[i]

    plt.xlim([-0.25, N - 1 + 0.25 + width])
    plt.ylabel('Time(ms)')
    plt.xticks((ind + width/2.), labels, size=20)

    legend = plt.legend((p[1][0], p[0][0], ),
                (
                 'Server Computation',
                 'Network',                    
                ),
                prop={'size': 20},
                bbox_to_anchor=(1.8, 1),
                loc = 'upper right',
                #mode="expand",
                ncol = 1,
                labelspacing = 1,
                borderaxespad=0,
                handletextpad = 0.3,
                handlelength = 1,
                handleheight=4)
    frame = legend.get_frame()
    frame.set_edgecolor("none")
    plt.savefig('img/breakdown.png', bbox_inches='tight')

def process_command_line(argv):
    """
    `argv` is a list of arguments, or `None` for ``sys.argv[1:]``.
    """
    if argv is None:
        argv = sys.argv[1:]
    parser = OptionParser()
    parser.add_option("-a", "--apps", help="pingpong/pool")
    parser.add_option("-i", "--inputfolder",
            help="Input folder.", default="./data")
    parser.add_option("-o", "--outputfolder",
            help="Output folder.", default="./plots")
    parser.add_option("-x", "--max_x", type=float, default=1000)
    parser.add_option("-l", "--legend",
            action="store_true", default=False)
    parser.add_option("-r", "--resolution",
            action="store_true", default=False)
    parser.add_option("-t", "--lte",
            action="store_true", default=False)
    parser.add_option("-e", "--per",
            action="store_true", default=False)
    parser.add_option("-p", "--phone",
            action="store_true", default=False)

    options, args = parser.parse_args()
    if args:
        parser.error('program takes no command-line arguments; '
                     '"%s" ignored.' % (args,))
    return options, args

def main(argv=None):
    '''
    Create bar plots for time breakdown
    '''
    options, args = process_command_line(argv)

    apps = options.apps.split("/")
    if not options.per:
        apps.sort()
    foldername = options.inputfolder
    outputfolder = options.outputfolder
    filename_prefix = "-".join(apps)
    if options.phone:
        filename_prefix = filename_prefix + "-Glass-Vs-Phone"
    elif options.resolution:
        filename_prefix = filename_prefix + "-resolution"
    elif options.lte:
        filename_prefix = filename_prefix + "-LTE"
    elif options.per:
        filename_prefix = filename_prefix + "-percentage"
    elif options.legend:
        filename_prefix = 'legend'
    output_filename = "%s/%s-breakdown.pdf" \
            % (outputfolder, filename_prefix)
    print "Plotting %s ..." % output_filename

    input_files = {}
    if options.phone:
        app = apps[0]
        input_files[app + '-1-Glass'] = ["cloudlet-" + app + ".txt"]
        input_files[app + '-2-Phone'] = ["cloudlet-" + app + "-phone.txt"]
        #input_files[app + '-1-Glass'] = ["cloudlet-" + app + ".txt"]
        #input_files[app + '-2-Phone'] = ["cloudlet-" + app + "-phone.txt"]
        #input_files[app + '-3-Phone5GHz'] = \
        #        ["cloudlet-" + app + "-phone-5G.txt"]
    elif options.resolution:
        app = apps[0]
        input_files[app + '-0-180p'] = ["cloudlet-" + app + "_180p.txt"]
        input_files[app + '-1-360p'] = ["cloudlet-" + app + ".txt"]
        input_files[app + '-2-720p'] = ["cloudlet-" + app + "_720p.txt"]
        input_files[app + '-3-1080p'] = ["cloudlet-" + app + "_1080p.txt"]
    elif options.lte:
        app = apps[0]
        input_files[app + '-1-WiFi'] = ["cloudlet-" + app + ".txt"]
        input_files[app + '-2-LTE'] = ["cloudlet-" + app + "-LTE.txt"]
    elif options.per:
        for i,app in enumerate(apps):
            input_files[str(i)+'-' + app + '-0-Cloudlet'] = ["cloudlet-" + app + ".txt"]
            if i < len(apps) - 1:
                input_files[str(i)+'-' + app + '-1-Empty'] = ["empty.txt"]
    else:
        for i,app in enumerate(apps):
            input_files[app + '-1-Cloudlet'] = ["cloudlet-" + app + ".txt"]
            input_files[app + '-2-East'] = ["east-" + app + ".txt"]
            input_files[app + '-3-West'] = ["west-" + app + ".txt"]
            input_files[app + '-4-EU'] = ["eu-" + app + ".txt"]
            #input_files[app + '-1-Cloudlet-all'] = \
            #        ["cloudlet-" + app + ".txt")]
            #input_files[app + '-2-East-all'] = ["east-" + app + ".txt"]
            #input_files[app + '-3-West-all'] = ["west-" + app + ".txt"]
            #input_files[app + '-4-EU-all'] = ["eu-" + app + ".txt"]
            if i < len(apps) - 1:
                input_files[app + '-5-'] = ["empty.txt"]
                #input_files[app + '-5--all'] = ["empty.txt"]

    bar_plot_data = {}


    # for filename, label, line_style in input_files:
    for label, filenames in sorted(input_files.items()):
        mean_data_all = []
        for filename in filenames:
            cdf_data, mean_data = parse_data(foldername, filename)
            mean_data_all.append(mean_data)
        bar_plot_data[label] = mean_data_all

    plot_breakdown(bar_plot_data,
            output_filename,
            apps, options)

if __name__ == "__main__":
    plot_breakdown()
#    status = main()
#    sys.exit(status)
