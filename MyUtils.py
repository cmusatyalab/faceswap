#!/usr/bin/env python

import logging
import sys
from operator import itemgetter
from itertools import groupby
import pdb

def getLogger(name):
    logger = logging.getLogger(name)
    handler = logging.StreamHandler(sys.stdout)
    logger.addHandler(handler)
    logger.setLevel(logging.DEBUG)
    return logger


# TODO: move to myutil
# get range of a list
# input : [1,2,4,5, 6,7]
# output: ["1...2", "4...7")]
def getContinuousRange(sorted_list):
    # get the range of python frame indexes
    ranges = []
    for key, group in groupby(enumerate(sorted_list), lambda (i,x):i-x):
        chunk = map(itemgetter(1), group)
        ranges.append('{0}...{1}'.format(chunk[0], chunk[-1]))
    return ranges


gmail_user="cardboardtest123@gmail.com"
gmail_pwd="cardboardtest321"
def send_email(recipient, subject, body):
    import smtplib
    FROM = gmail_user
    TO = recipient if type(recipient) is list else [recipient]
    SUBJECT = subject
    TEXT = body

    # Prepare actual message
    message = """\From: %s\nTo: %s\nSubject: %s\n\n%s
    """ % (FROM, ", ".join(TO), SUBJECT, TEXT)
    try:
        server_ssl = smtplib.SMTP_SSL("smtp.gmail.com", 465)
        server_ssl.ehlo() # optional, called by login()
        server_ssl.login(gmail_user, gmail_pwd)  
        # ssl server doesn't support or need tls, so don't call server_ssl.starttls() 
        server_ssl.sendmail(FROM, TO, message)
        server_ssl.close()
        print 'successfully sent the mail'

        # server = smtplib.SMTP("smtp.gmail.com", 587)
        # server.ehlo()
        # server.starttls()
        # server.login(gmail_user, gmail_pwd)
        # server.sendmail(FROM, TO, message)
        # server.close()
        # print 'successfully sent the mail'
    except:
        print "failed to send mail"


def drectangle_to_tuple(drectangle):
    cur_roi = (int(drectangle.left()),
                     int(drectangle.top()),
                     int(drectangle.right()),
                     int(drectangle.bottom()))
    return cur_roi
