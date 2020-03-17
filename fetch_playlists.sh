#!/bin/sh

RES_PATH="src/main/res/raw"

# Make sure raw directory exists
mkdir $RES_PATH

# Check if json files are already there
COUNT=`ls -l $RES_PATH/tvos*.json | wc -l`

# Skip download if files already exist
if [ $COUNT != 3 ]
then
    wget http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/entries.json -O $RES_PATH/tvos10.json
    wget https://sylvan.apple.com/Aerials/2x/entries.json -O $RES_PATH/tvos11.json --no-check-certificate
    wget https://sylvan.apple.com/Aerials/resources.tar -qO - --no-check-certificate | tar -xOf - entries.json > $RES_PATH/tvos12.json
    wget https://sylvan.apple.com/Aerials/resources-13.tar -qO - --no-check-certificate | tar -xOf - entries.json > $RES_PATH/tvos13.json
fi

