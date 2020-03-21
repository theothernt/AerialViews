#!/bin/sh

RES_PATH="src/main/res/raw"

# Make sure raw directory exists
mkdir $RES_PATH

# Check if json files are already there
COUNT=`ls -l $RES_PATH/tvos*.json | wc -l`

# Skip download if files already exist
if [ $COUNT != 1 ]
then
    wget https://sylvan.apple.com/Aerials/resources-13.tar -qO - --no-check-certificate | tar -xOf - entries.json > $RES_PATH/tvos13.json
fi

