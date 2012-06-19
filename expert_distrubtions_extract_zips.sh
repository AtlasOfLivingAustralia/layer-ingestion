#!/bin/bash
# Used in the expert distributions load process. See http://code.google.com/p/alageospatialportal/wiki/ExpertDistributionsLoadProcess.

for file in ./*
do 
if [[ ${file}  =~ \./(.+)\.zip ]]
then
	mkdir ${BASH_REMATCH[1]}
	unzip ${file} -d ${BASH_REMATCH[1]}
	echo $?
fi
done
