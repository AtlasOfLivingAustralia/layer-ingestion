#!/bin/bash
# Used in the expert distributions load process. See http://code.google.com/p/alageospatialportal/wiki/ExpertDistributionsLoadProcess.

first_shp_file_extracted=0

for file in ./CAAB*/*
do
if [[ ${file} =~ \.shp ]]
then
	if [[ $first_shp_file_extracted -eq 0 ]]
	then
		shp2pgsql -c -I -s 4326 ${file} new_distributionshapes
		let first_shp_file_extracted=1
	else
 		shp2pgsql -a -s 4326 ${file} new_distributionshapes
	fi
fi
done		
