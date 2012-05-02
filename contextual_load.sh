#!/bin/bash
export SHAPEFILE="/data/ala/data/layers/raw/meow_ecos/meow_ecos.shp"

export LAYERID="988"
export LAYERNAME="meow_ecos"
export LAYERDESCRIPTION="Marine Ecoregions of the World"

export FIELDSSID=""
export FIELDSSNAME=""
export FIELDSSDESCRIPTION=""

export DBUSERNAME="postgres"
export DBPASSWORD="postgres"
export DBHOST="ala-devmaps-db.vm.csiro.au"
export DBNAME="layersdb"
export DBJDBCURL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"

export GEOSERVERBASEURL="http://localhost:8082/geoserver"
export GEOSERVERUSERNAME="admin"
export GEOSERVERPASSWORD="at1as0f0z"

export REPROJECTEDSHAPEFILE="/data/ala/data/layers/ready/shape/${LAYERNAME}.shp"

#echo "Reprojecting shapefile to WGS 84" \
#&& ogr2ogr -t_srs EPSG:4326  "${REPROJECTEDSHAPEFILE}" "${SHAPEFILE}" \
#&& echo "Creating layer and fields table entries for layer, converting shapefile to database table" \
#&& java -Xmx10G -cp ./layer-ingestion-1.0-SNAPSHOT-jar-with-dependencies.jar au.org.ala.layers.ingestion.contextual.ContextualDatabaseEntryCreator "${LAYERID}" "${LAYERNAME}" "${LAYERDESCRIPTION}" "${FIELDSSID}" "${FIELDSSNAME}" "${FIELDSSDESCRIPTION}" "${REPROJECTEDSHAPEFILE}" "${DBUSERNAME}" "${DBPASSWORD}" "${DBJDBCURL}" "${DBHOST}" "${DBNAME}" \
echo "Create objects from layer" \
&& java -Xmx10G -cp ./layer-ingestion-1.0-SNAPSHOT-jar-with-dependencies.jar au.org.ala.layers.ingestion.contextual.ContextualObjectCreator "${LAYERID}" "${DBUSERNAME}" "${DBPASSWORD}" "${DBJDBCURL}" \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp ./layer-ingestion-1.0-SNAPSHOT-jar-with-dependencies.jar au.org.ala.layers.ingestion.contextual.ContextualGeoserverLoader "${GEOSERVERBASEURL}" "${GEOSERVERUSERNAME}" "${GEOSERVERPASSWORD}" "${LAYERID}" "${LAYERNAME}" "${LAYERDESCRIPTION}"
