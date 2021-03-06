#!/bin/bash
# Loads an contextual layer from raw .adf data, polygonzing using gdal_polygonize
# NOTE: The following 11 variables need to be modified for each new layer
export ADF_HEADER_FILE="/data/ala/data/layers/raw/ga_bath_rocky/hdr.adf"
export LAYER_ID="988"
export LAYER_SHORT_NAME="meow_ecos"
export LAYER_DISPLAY_NAME="Marine Ecoregions of the World"
export FIELDS_SID=""
export FIELDS_SNAME=""
export FIELDS_SDESCRIPTION=""
export NAME_SEARCH="true"
export INTERSECT="false"
export DERIVED_COLUMNS_FILE=""
export LEGEND_FILE=""

export DB_USERNAME="postgres"
export DB_PASSWORD="password"
export DB_HOST="ala-devmaps-db.vm.csiro.au"
export DB_NAME="layersdb"
export DB_JDBC_URL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"

export GEOSERVER_BASE_URL="http://localhost:8082/geoserver"
export GEOSERVER_USERNAME="admin"
export GEOSERVER_PASSWORD="password"

export PROCESS_DIR="/data/ala/data/layers/process"
export LEGEND_DIR="/data/ala/data/layers/test"
export GEOTIFF_DIR="/data/ala/data/layers/ready/geotiff"

export SHAPEFILE="${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}.shp"
export REPROJECTEDSHAPEFILE="/data/ala/data/layers/ready/shape/${LAYER_SHORT_NAME}.shp"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

echo "Copy legend file to legend directory" \
&& cp ${LEGEND_FILE} "${LEGEND_DIR}/${LAYER_SHORT_NAME}.sld" \
&& echo "create process directory" \ 
&& mkdir -p "${PROCESS_DIR}/${LAYER_SHORT_NAME}" \
&& echo "Polygonizing gridded data" \
&& gdal_polygonize ${ADF_HEADER_FILE} -f "ESRI Shapefile" "${SHAPEFILE}" \
&& echo "Reprojecting shapefile to WGS 84" \
&& ogr2ogr -t_srs EPSG:4326  "${REPROJECTEDSHAPEFILE}" "${SHAPEFILE}" \
&& echo "Creating layer and fields table entries for layer, converting shapefile to database table" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualFromShapefileDatabaseLoader "${LAYER_ID}" "${LAYER_SHORT_NAME}" "${LAYER_DISPLAY_NAME}" "${FIELDS_SID}" "${FIELDS_SNAME}" "${FIELDS_SDESCRIPTION}" "${NAME_SEARCH}" "${INTERSECT}" "${REPROJECTEDSHAPEFILE}" "${DB_USERNAME}" "${DB_PASSWORD}" "${DB_JDBC_URL}" "${DB_HOST}" "${DB_NAME}" "${DERIVED_COLUMNS_FILE}" \
&& echo "Create objects from layer" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualObjectCreator "${LAYER_ID}" "${DB_USERNAME}" "${DB_PASSWORD}" "${DB_JDBC_URL}" \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.PostgisTableGeoserverLoader "${GEOSERVER_BASE_URL}" "${GEOSERVER_USERNAME}" "${GEOSERVER_PASSWORD}" "${LAYER_ID}" "${LAYER_SHORT_NAME}" "${LAYER_DISPLAY_NAME}" "${LEGEND_DIR}/${LAYER_SHORT_NAME}.sld"
