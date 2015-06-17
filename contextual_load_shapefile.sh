#!/bin/bash
# Loads a contextual layer using from a shape file (.shp)
# NOTE: The following 9 variables need to be modified for each new layer
export SHAPEFILE="/data/ala/data/layers/raw/meow_ecos/meow_ecos.shp"
export LAYERID="988"
export LAYER_SHORT_NAME="meow_ecos"
export LAYER_DISPLAY_NAME="Marine Ecoregions of the World"
export FIELDSSID=""
export FIELDSSNAME=""
export FIELDSSDESCRIPTION=""
export NAME_SEARCH="true"
export INTERSECT="false"

export DBUSERNAME="postgres"
export DBPASSWORD="postgres"
export DBHOST="ala-devmaps-db.vm.csiro.au"
export DBNAME="layersdb"
export DBJDBCURL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"

export GEOSERVERBASEURL="http://localhost:8082/geoserver"
export GEOSERVERUSERNAME="admin"
export GEOSERVERPASSWORD="password"

export BASEURL="http://spatial-dev.ala.org.au/"

export REPROJECTEDSHAPEFILE="/data/ala/data/layers/ready/shape/${LAYER_SHORT_NAME}.shp"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

echo "Reprojecting shapefile to WGS 84" \
&& ogr2ogr -t_srs EPSG:4326  "${REPROJECTEDSHAPEFILE}" "${SHAPEFILE}" \
&& echo "Creating layer and fields table entries for layer, converting shapefile to database table" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualFromShapefileDatabaseLoader "${LAYERID}" "${LAYER_SHORT_NAME}" "${LAYER_DISPLAY_NAME}" "${FIELDSSID}" "${FIELDSSNAME}" "${FIELDSSDESCRIPTION}" "${NAME_SEARCH}" "${INTERSECT}" "${REPROJECTEDSHAPEFILE}" "${DBUSERNAME}" "${DBPASSWORD}" "${DBJDBCURL}" "${DBHOST}" "${DBNAME}" \
&& echo "Create objects from layer" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualObjectCreator "${LAYERID}" "${DBUSERNAME}" "${DBPASSWORD}" "${DBJDBCURL}" \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.PostgisTableGeoserverLoader "${GEOSERVERBASEURL}" "${GEOSERVERUSERNAME}" "${GEOSERVERPASSWORD}" "${LAYERID}" "${LAYER_SHORT_NAME}" "${LAYER_DISPLAY_NAME}" \
&& echo "Create coloured legend (sld) for geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualLegend "${BASEURL}" "cl${LAYERID}" "/data/ala/data/layers/test/" "${GEOSERVERUSERNAME}" "${GEOSERVERPASSWORD}"