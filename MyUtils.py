#!/usr/bin/env python

import logging
import sys
from operator import itemgetter
from itertools import groupby
import pdb
import os
import shutil

def getLogger(name):
    logger = logging.getLogger(name)
    handler = logging.StreamHandler(sys.stdout)
    logger.addHandler(handler)
    logger.setLevel(logging.DEBUG)
    return logger

def remove_dir(dir_name):
    shutil.rmtree(dir_name, ignore_errors=True)    

def create_dir(dir_name):
    if '~' in dir_name:
        directory = os.path.expanduser(dir_name)
    if not os.path.exists(dir_name):
        print 'create dir: ' + dir_name
        os.makedirs(dir_name)

def writeListToFile(m_list, file_path):
    with open(file_path, 'w') as output:
        for item in m_list:
            output.write('{}\n'.format(item))
        output.flush()

def zipdir(dirPath=None, zipFilePath=None, includeDirInZip=True):
    """Create a zip archive from a directory.

    Note that this function is designed to put files in the zip archive with
    either no parent directory or just one parent directory, so it will trim any
    leading directories in the filesystem paths and not include them inside the
    zip archive paths. This is generally the case when you want to just take a
    directory and make it into a zip file that can be extracted in different
    locations.

    Keyword arguments:

    dirPath -- string path to the directory to archive. This is the only
    required argument. It can be absolute or relative, but only one or zero
    leading directories will be included in the zip archive.

    zipFilePath -- string path to the output zip file. This can be an absolute
    or relative path. If the zip file already exists, it will be updated. If
    not, it will be created. If you want to replace it from scratch, delete it
    prior to calling this function. (default is computed as dirPath + ".zip")

    includeDirInZip -- boolean indicating whether the top level directory should
    be included in the archive or omitted. (default True)

"""
    if not zipFilePath:
        zipFilePath = dirPath + ".zip"
    if not os.path.isdir(dirPath):
        raise OSError("dirPath argument must point to a directory. "
            "'%s' does not." % dirPath)
    parentDir, dirToZip = os.path.split(dirPath)
    #Little nested function to prepare the proper archive path
    def trimPath(path):
        archivePath = path.replace(parentDir, "", 1)
        if parentDir:
            archivePath = archivePath.replace(os.path.sep, "", 1)
        if not includeDirInZip:
            archivePath = archivePath.replace(dirToZip + os.path.sep, "", 1)
        return os.path.normcase(archivePath)

    outFile = zipfile.ZipFile(zipFilePath, "w",
        compression=zipfile.ZIP_DEFLATED)
    for (archiveDirPath, dirNames, fileNames) in os.walk(dirPath):
        for fileName in fileNames:
            filePath = os.path.join(archiveDirPath, fileName)
            outFile.write(filePath, trimPath(filePath))
        #Make sure we get empty directories as well
        if not fileNames and not dirNames:
            zipInfo = zipfile.ZipInfo(trimPath(archiveDirPath) + "/")
            #some web sites suggest doing
            #zipInfo.external_attr = 16
            #or
            #zipInfo.external_attr = 48
            #Here to allow for inserting an empty directory.  Still TBD/TODO.
            outFile.writestr(zipInfo, "")
    outFile.close()



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

class Tee(object):
    def __init__(self, name, mode):
        self.file = open(name, mode)
        self.stdout = sys.stdout
        sys.stdout = self

    def __del__(self):
        sys.stdout = self.stdout
        self.file.close()

    def write(self, data):
        self.file.write(data)
        self.stdout.write(data)

    def flush(self):
        self.file.flush()
        self.stdout.flush()
    
    
