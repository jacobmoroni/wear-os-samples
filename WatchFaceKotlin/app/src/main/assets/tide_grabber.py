#!/usr/bin/env python3
"""
This class pulls the tide info from tidesandcurrents.noaa.gov
"""
import sys
import subprocess
import time


class TideGrabber:
    """
    Class to parse out command line args and pull correct tide data to the correct place
    """

    def __init__(self):
        self.year = 0
        self.station_id = 0
        self.file_name = 0
        self.parseArgs()

    def parseArgs(self):
        """
        Parse out sys.argv and set member variables
        """
        if len(sys.argv) == 3:
            self.station_id = sys.argv[1]
            self.year = sys.argv[2]
            self.file_name = f"tides_{self.station_id}_{self.year}.txt"
            self.pullTide()
            self.checkResult()
        elif len(sys.argv) == 2:
            if sys.argv[1] != "auto":
                self.printHelp()
        else:
            self.printHelp()

    def printHelp(self):
        """
        Prints a helper string to show how to use the function
        """
        print("To run this script: python3 tide_grabber.py <Station ID> <year> or python3 tide_grabber.py auto")
        print("If 'auto' argument passed in, it will automatically load designated tides")
        print("Station ID can be found by visiting https://tidesandcurrents.noaa.gov/map/index.html?type=TidePredictions&region=, then selecting the desired location")
        print("Years available are the current year +/- 2 years")
        print("Example for Balboa Pier, Newport Beach 2023: python3 tide_grabber.py 9410583 2023")

    def pullTide(self):
        """
        Pulls the tide info and saves it to the correct location
        """
        command_string = f"wget -O {self.file_name} https://tidesandcurrents.noaa.gov/cgi-bin/predictiondownload.cgi\?\&stnid\={self.station_id}\&threshold\=\&thresholdDirection\=greaterThan\&bdate\={self.year}\&timezone\=GMT\&datum\=MLLW\&clock\=24hour\&type\=txt\&annual\=true"
        self.runcmd(command_string, True)

    def runcmd(self, cmd, verbose=False):
        """
        Runs a command using suprocess.Popen
        """

        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            # text=True,
            shell=True
        )
        std_out, std_err = process.communicate()
        if verbose:
            print(std_out.strip(), std_err)

    def checkResult(self):
        """
        Checks the results of the file download
        """
        with open(self.file_name, "r", encoding="utf8") as file:
            lines = file.readlines()
            if len(lines) == 0:
                print("ERROR: File is empty check your input")
            else:
                print(f"Download Successful for:\n{lines[3]}\n{lines[5]}\n{lines[13]}")
            file.close()


if __name__ == "__main__":
    tg = TideGrabber()
    if len(sys.argv) == 2 and sys.argv[1] == "auto":
        tide_spots = ["9410583", # Newport Beach, CA
                      "TWC0419", # Oceanside, CA
                      "9410230", # La Jolla, CA
                      "8512354", # Long Island, NY
                      "8533071", # Seaside Heights, NJ
                      "8652226", # Outer Banks, NC
                      "8721649"] # Cocoa Beach, FL
        current_year = time.gmtime().tm_year
        for spot in tide_spots:
            for year in range(current_year, current_year + 5):
                tg.station_id = spot
                tg.year = year
                tg.file_name = f"tides_{tg.station_id}_{tg.year}.txt"
                # print(tg.file_name)
                tg.pullTide()
                tg.checkResult()
