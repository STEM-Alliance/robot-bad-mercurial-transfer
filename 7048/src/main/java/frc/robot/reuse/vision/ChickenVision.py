#!/usr/bin/env python3
# ----------------------------------------------------------------------------
# Copyright (c) 2018 FIRST. All Rights Reserved.
# Open Source Software - may be modified and shared by FRC teams. The code
# must be accompanied by the FIRST BSD license file in the root directory of
# the project.

# My 2019 license: use it as much as you want. Crediting is recommended because it lets me know that I am being useful.
# Credit to Screaming Chickens 3997

# This is meant to be used in conjuction with WPILib Raspberry Pi image: https://github.com/wpilibsuite/FRCVision-pi-gen
# ----------------------------------------------------------------------------

import json
import time
import sys
from threading import Thread

from cscore import CameraServer, VideoSource
from networktables import NetworkTablesInstance
import cv2
import numpy as np
from networktables import NetworkTables
import math
########### SET RESOLUTION TO 256x144 !!!! ############


# import the necessary packages
import datetime


# Class to examine Frames per second of camera stream. Currently not used.
class FPS:
    def __init__(self):
        # store the start time, end time, and total number of frames
        # that were examined between the start and end intervals
        self._start = None
        self._end = None
        self._numFrames = 0

    def start(self):
        # start the timer
        self._start = datetime.datetime.now()
        return self

    def stop(self):
        # stop the timer
        self._end = datetime.datetime.now()

    def update(self):
        # increment the total number of frames examined during the
        # start and end intervals
        self._numFrames += 1

    def elapsed(self):
        # return the total number of seconds between the start and
        # end interval
        return (self._end - self._start).total_seconds()

    def fps(self):
        # compute the (approximate) frames per second
        return self._numFrames / self.elapsed()


# class that runs separate thread for showing video,
class VideoShow:
    """
    Class that continuously shows a frame using a dedicated thread.
    """

    def __init__(self, imgWidth, imgHeight, cameraServer, frame=None):
        try:
            self.outputStream = cameraServer.getServer(name="stream")
        except:
            self.outputStream = cameraServer.putVideo("stream", imgWidth, imgHeight)

        self.frame = frame
        self.stopped = False

    def start(self):
        Thread(target=self.show, args=()).start()
        return self

    def show(self):
        while not self.stopped:
            self.outputStream.putFrame(self.frame)

    def stop(self):
        self.stopped = True

    def notifyError(self, error):
        self.outputStream.notifyError(error)


# Class that runs a separate thread for reading  camera server also controlling exposure.
class WebcamVideoStream:
    def __init__(self, camera, cameraServer, frameWidth, frameHeight, name="WebcamVideoStream"):
        # initialize the video camera stream and read the first frame
        # from the stream

        # Automatically sets exposure to 0 to track tape
        self.webcam = camera
        self.webcam.setExposureManual(0)
        # Some booleans so that we don't keep setting exposure over and over to the same value
        self.autoExpose = False
        self.prevValue = self.autoExpose
        # Make a blank image to write on
        self.img = np.zeros(shape=(frameWidth, frameHeight, 3), dtype=np.uint8)
        # Gets the video
        self.stream = cameraServer.getVideo(camera=camera)
        (self.timestamp, self.img) = self.stream.grabFrame(self.img)

        # initialize the thread name
        self.name = name

        # initialize the variable used to indicate if the thread should
        # be stopped
        self.stopped = False

    def start(self):
        # start the thread to read frames from the video stream
        t = Thread(target=self.update, name=self.name, args=())
        t.daemon = True
        t.start()
        return self

    def update(self):
        # keep looping infinitely until the thread is stopped
        while True:
            # if the thread indicator variable is set, stop the thread
            if self.stopped:
                return
            # Boolean logic we don't keep setting exposure over and over to the same value
            if self.autoExpose:
                if (self.autoExpose != self.prevValue):
                    self.prevValue = self.autoExpose
                    self.webcam.setExposureAuto()
            else:
                if (self.autoExpose != self.prevValue):
                    self.prevValue = self.autoExpose
                    self.webcam.setExposureManual(0)
            # gets the image and timestamp from cameraserver
            (self.timestamp, self.img) = self.stream.grabFrame(self.img)

    def read(self):
        # return the frame most recently read
        return self.timestamp, self.img

    def stop(self):
        # indicate that the thread should be stopped
        self.stopped = True

    def getError(self):
        return self.stream.getError()


###################### PROCESSING OPENCV ################################

# Angles in radians

# image size ratioed to 16:9
image_width = 256
image_height = 144

# Lifecam 3000 from datasheet
# Datasheet: https://dl2jx7zfbtwvr.cloudfront.net/specsheets/WEBC1010.pdf
diagonalView = math.radians(68.5)

# 16:9 aspect ratio
horizontalAspect = 16
verticalAspect = 9

# Reasons for using diagonal aspect is to calculate horizontal field of view.
diagonalAspect = math.hypot(horizontalAspect, verticalAspect)
# Calculations: http://vrguy.blogspot.com/2013/04/converting-diagonal-field-of-view-and.html
horizontalView = math.atan(math.tan(diagonalView / 2) * (horizontalAspect / diagonalAspect)) * 2
verticalView = math.atan(math.tan(diagonalView / 2) * (verticalAspect / diagonalAspect)) * 2

# Focal Length calculations: https://docs.google.com/presentation/d/1ediRsI-oR3-kwawFJZ34_ZTlQS2SDBLjZasjzZ-eXbQ/pub?start=false&loop=false&slide=id.g12c083cffa_0_165
H_FOCAL_LENGTH = image_width / (2 * math.tan((horizontalView / 2)))
V_FOCAL_LENGTH = image_height / (2 * math.tan((verticalView / 2)))
# blurs have to be odd
green_blur = 2

# define range of green of retroreflective tape in HSV
lower_green = np.array([40.69886550628882, 225.0, 41.58786943490556])
upper_green = np.array([77.31838629921143, 255.0, 201.05557789981572])


# Flip image if camera mounted upside down
def flipImage(frame):
    return cv2.flip(frame, -1)


# Blurs frame
def blurImg(frame, blur_radius):
    img = frame.copy()
    blur = cv2.blur(img, (blur_radius, blur_radius))
    return blur


# Masks the video based on a range of hsv colors
# Takes in a frame, range of color, and a blurred frame, returns a masked frame
def threshold_video(lower_color, upper_color, blur):
    # Convert BGR to HSV
    hsv = cv2.cvtColor(blur, cv2.COLOR_BGR2HSV)

    # hold the HSV image to get only red colors
    mask = cv2.inRange(hsv, lower_color, upper_color)

    # Returns the masked imageBlurs video to smooth out image

    return mask


# Finds the tape targets from the masked image and displays them on original stream + network tales
def findTargets(frame, mask):
    # Finds contours
    _, contours, _ = cv2.findContours(mask, cv2.RETR_TREE, cv2.CHAIN_APPROX_TC89_KCOS)
    # Take each frame
    # Gets the shape of video
    screenHeight, screenWidth, _ = frame.shape
    # Gets center of height and width
    centerX = (screenWidth / 2) - .5
    centerY = (screenHeight / 2) - .5
    # Copies frame and stores it in image
    image = frame.copy()
    # Processes the contours, takes in (contours, output_image, (centerOfImage)
    if len(contours) != 0:
        image = findTape(contours, image, centerX, centerY)
    # Shows the contours overlayed on the original video
    return image

# Draws Contours and finds center and yaw of orange ball
# centerX is center x coordinate of image
# centerY is center y coordinate of image
def findTape(contours, image, centerX, centerY):
    screenHeight, screenWidth, channels = image.shape;
    # Seen vision targets (correct angle, adjacent to each other)
    tape = []

    if len(contours) > 0:
        # Sort contours by area size (biggest to smallest)
        cntsSorted = sorted(contours, key=lambda x: cv2.contourArea(x), reverse=True)

        biggestTape = []
        for cnt in cntsSorted:
            x, y, w, h = cv2.boundingRect(cnt)
            aspect_ratio = float(w) / h
            # Get moments of contour; mainly for centroid
            M = cv2.moments(cnt)
            # Get convex hull (bounding polygon on contour)
            hull = cv2.convexHull(cnt)
            # Calculate Contour area
            cntArea = cv2.contourArea(cnt)
            # Filters contours based off of size
            if (checkContours(cntArea, aspect_ratio)):
                ### MOSTLY DRAWING CODE, BUT CALCULATES IMPORTANT INFO ###
                # Gets the centeroids of contour
                if M["m00"] != 0:
                    cx = int(M["m10"] / M["m00"])
                    cy = int(M["m01"] / M["m00"])
                else:
                    cx, cy = 0, 0
                if (len(biggestTape) < 3):

                    ##### DRAWS CONTOUR######
                    # Gets rotated bounding rectangle of contour
                    rect = cv2.minAreaRect(cnt)
                    # Creates box around that rectangle
                    box = cv2.boxPoints(rect)
                    # Not exactly sure
                    box = np.int0(box)
                    # Draws rotated rectangle
                    cv2.drawContours(image, [box], 0, (23, 184, 80), 3)

                    # Draws a vertical white line passing through center of contour
                    cv2.line(image, (cx, screenHeight), (cx, 0), (255, 255, 255))
                    # Draws the contours
                    cv2.drawContours(image, [cnt], 0, (23, 184, 80), 1)

                    # Makes bounding rectangle of contour
                    rx, ry, rw, rh = cv2.boundingRect(cnt)

                    # Draws countour of bounding rectangle and enclosing circle in green
                    cv2.rectangle(image, (rx, ry), (rx + rw, ry + rh), (23, 184, 80), 1)

                    # Appends important info to array
                    if not biggestTape:
                        biggestTape.append([cx, cy, cnt])
                    elif [cx, cy, cnt] not in biggestTape:
                        biggestTape.append([cx, cy, cnt])

        # Check if there are tape seen
        if (len(biggestTape) > 0):
            # pushes that it sees tape to network tables
            networkTable.putBoolean("tapeDetected", True)

            # Sorts targets based on x coords to break any angle tie
            biggestTape.sort(key=lambda x: math.fabs(x[0]))
            biggestTape.sort(key=lambda y: math.fabs(y[0]))

            closestTape = min(biggestTape, key=lambda x: (math.fabs(x[0] - centerX)))


            xCoord = closestTape[0]
            finalTarget = calculateYaw(xCoord, centerX, H_FOCAL_LENGTH)

            print("Yaw: " + str(finalTarget))
            # Puts the yaw on screen
            # Draws yaw of target + line where center of target is
            cv2.putText(image, "Yaw: " + str(finalTarget), (40, 40), cv2.FONT_HERSHEY_COMPLEX, .6,
                        (255, 255, 255))
            cv2.line(image, (xCoord, screenHeight), (xCoord, 0), (255, 0, 0), 2)
            currentAngleError = finalTarget

            # pushes tape angle to network tables
            networkTable.putNumber("tapeYaw", currentAngleError)

        else:
            # pushes that it doesn't see cargo to network tables
            networkTable.putBoolean("tapeDetected", False)

        cv2.line(image, (round(centerX), screenHeight), (round(centerX), 0), (255, 255, 255), 2)


        return image

# Checks if tape contours are worthy based off of contour area and (not currently) hull area
def checkContours(cntSize, hullSize):
    return cntSize > (image_width / 12)

def calculateDistance(heightOfCamera, heightOfTarget, pitch):
    if ( pitch != 0):
        heightOfTargetFromCamera = heightOfTarget - heightOfCamera
        distance = math.fabs(heightOfTargetFromCamera / math.tan(math.radians(pitch)))
    else:
        distance = 0
    # Uses trig and pitch to find distance to target
    '''
    d = distance
    h = height between camera and target
    a = angle = pitch

    tan a = h/d (opposite over adjacent)

    d = h / tan a

                         .
                        /|
                       / |
                      /  |h
                     /a  |
              camera -----
                       d
    '''

    return distance

# Uses trig and focal length of camera to find yaw.
# Link to further explanation: https://docs.google.com/presentation/d/1ediRsI-oR3-kwawFJZ34_ZTlQS2SDBLjZasjzZ-eXbQ/pub?start=false&loop=false&slide=id.g12c083cffa_0_298
def calculateYaw(pixelX, centerX, hFocalLength):
    yaw = math.degrees(math.atan((pixelX - centerX) / hFocalLength))
    return round(yaw)


# Link to further explanation: https://docs.google.com/presentation/d/1ediRsI-oR3-kwawFJZ34_ZTlQS2SDBLjZasjzZ-eXbQ/pub?start=false&loop=false&slide=id.g12c083cffa_0_298
def calculatePitch(pixelY, centerY, vFocalLength):
    pitch = math.degrees(math.atan((pixelY - centerY) / vFocalLength))
    # Just stopped working have to do this:
    pitch *= -1
    return round(pitch)

#################### FRC VISION PI Image Specific #############
configFile = "/boot/frc.json"


class CameraConfig: pass


team = None
server = False
cameraConfigs = []

"""Report parse error."""


def parseError(str):
    print("config error in '" + configFile + "': " + str, file=sys.stderr)


"""Read single camera configuration."""


def readCameraConfig(config):
    cam = CameraConfig()

    # name
    try:
        cam.name = config["name"]
    except KeyError:
        parseError("could not read camera name")
        return False

    # path
    try:
        cam.path = config["path"]
    except KeyError:
        parseError("camera '{}': could not read path".format(cam.name))
        return False

    cam.config = config

    cameraConfigs.append(cam)
    return True


"""Read configuration file."""


def readConfig():
    global team
    global server

    # parse file
    try:
        with open(configFile, "rt") as f:
            j = json.load(f)
    except OSError as err:
        print("could not open '{}': {}".format(configFile, err), file=sys.stderr)
        return False

    # top level must be an object
    if not isinstance(j, dict):
        parseError("must be JSON object")
        return False

    # team number
    try:
        team = j["team"]
    except KeyError:
        parseError("could not read team number")
        return False

    # ntmode (optional)
    if "ntmode" in j:
        str = j["ntmode"]
        if str.lower() == "client":
            server = False
        elif str.lower() == "server":
            server = True
        else:
            parseError("could not understand ntmode value '{}'".format(str))

    # cameras
    try:
        cameras = j["cameras"]
    except KeyError:
        parseError("could not read cameras")
        return False
    for camera in cameras:
        if not readCameraConfig(camera):
            return False

    return True


"""Start running the camera."""


def startCamera(config):
    print("Starting camera '{}' on {}".format(config.name, config.path))
    cs = CameraServer.getInstance()
    camera = cs.startAutomaticCapture(name=config.name, path=config.path)
    camera.setConfigJson(json.dumps(config.config))

    return camera


def configCamera(camera, config):
    camera.setConfigJson(json.dumps(config.config))


if __name__ == "__main__":
    if len(sys.argv) >= 2:
        configFile = sys.argv[1]
    # read configuration
    if not readConfig():
        sys.exit(1)

    # start NetworkTables
    ntinst = NetworkTablesInstance.getDefault()
    # Name of network table - this is how it communicates with robot. IMPORTANT
    networkTable = NetworkTables.getTable('ChickenVision')

    if server:
        print("Setting up NetworkTables server")
        ntinst.startServer()
    else:
        print("Setting up NetworkTables client for team {}".format(team))
        ntinst.startClientTeam(team)

    networkTable.putBoolean("Tape", True)
    networkTable.putBoolean("Driver", False)
    networkTable.putBoolean("tapeDetected", False)
    networkTable.putNumber("tapeYaw", 0)

    # start cameras
    cameras = []
    for cameraConfig in cameraConfigs:
        cameraCapture = startCamera(cameraConfig)
        cameras.append(cameraCapture)
    # Get the first camera

    cameraIndex = 0
    newCameraIndex = -1
    networkTable.putNumber("CameraIndex", cameraIndex)

    # (optional) Setup a CvSource. This will send images back to the Dashboard
    # Allocating new images is very expensive, always try to preallocate
    img = np.zeros(shape=(image_height, image_width, 3), dtype=np.uint8)

    cameraServer = CameraServer.getInstance()
    # Start thread outputing stream
    streamViewer = VideoShow(image_width, image_height, cameraServer, frame=img).start()

    while True:
        cameraChange = False
        print("[INFO] camera: {:d}".format(cameraIndex))
        webcam = cameras[cameraIndex]
        # Start thread reading camera
        cap = WebcamVideoStream(webcam, cameraServer, image_width, image_height).start()

        # cap.autoExpose=True;
        tape = False
        fps = FPS().start()

        # TOTAL_FRAMES = 200;
        # loop forever
        while not cameraChange:
            # Tell the CvSink to grab a frame from the camera and put it
            # in the source image.  If there is an error notify the output.
            timestamp, img = cap.read()

            # Uncomment if camera is mounted upside down
            # frame = flipImage(img)
            # Comment out if camera is mounted upside down
            frame = img

            if timestamp == 0:
                # Send the output the error.
                streamViewer.notifyError(cap.getError())
                # skip the rest of the current iteration
                continue

            # figure out which camera to use
            if networkTable.getBoolean("Driver", False):
                state = 0
                networkTable.putBoolean("Tape", False)
            elif networkTable.getBoolean("Tape", False):
                state = 1
                networkTable.putBoolean("Driver", False)

            # Checks if you just want camera for driver (No processing), False by default
            if state == 0:
                cap.autoExpose = True
                processed = frame

            else:
                # Checks if you just want camera for Tape processing , False by default
                # Lowers exposure to 0
                cap.autoExpose = False
                boxBlur = blurImg(frame, green_blur)
                threshold = threshold_video(lower_green, upper_green, boxBlur)
                processed = findTargets(frame, threshold)

            # Puts timestamp of camera on netowrk tables
            networkTable.putNumber("VideoTimestamp", timestamp)
            streamViewer.frame = processed

            # update the FPS counter
            fps.update()
            # Flushes camera values to reduce latency
            ntinst.flush()

            newCameraIndex = int(networkTable.getNumber("CameraIndex", cameraIndex))
            if newCameraIndex < len(cameras) and newCameraIndex >= 0 and cameraIndex != newCameraIndex:
                configCamera(cameras[cameraIndex], cameraConfigs[cameraIndex])
                cameraIndex = newCameraIndex
                cameraChange = True
                cap.stop()

        # Doesn't do anything at the moment. You can easily get this working by indenting these three lines
        # and setting while loop to: while fps._numFrames < TOTAL_FRAMES
        fps.stop()
        print("[INFO] elasped time: {:.2f}".format(fps.elapsed()))
        print("[INFO] approx. FPS: {:.2f}".format(fps.fps()))