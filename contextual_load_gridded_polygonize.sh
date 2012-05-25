#!/bin/bash
# Loads an contextual layer from raw .adf data, polygonzing using gdal_polygonize
# NOTE: The following 8 variables need to be modified for each new layer
export ADF_HEADER_FILE="/data/ala/data/layers/raw/ga_bath_rocky/hdr.adf"
export LAYER_ID="988"
export LAYER_NAME="meow_ecos"
export LAYER_DESCRIPTION="Marine Ecoregions of the World"
export FIELDS_SID=""
export FIELDS_SNAME=""
export FIELDS_SDESCRIPTION=""
export DERIVED_COLUMNS_FILE=""
export LEGEND_FILE=""

export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"
export DB_HOST="ala-devmaps-db.vm.csiro.au"
export DB_NAME="layersdb"
export DB_JDBC_URL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"

export GEOSERVER_BASE_URL="http://localhost:8082/geoserver"
export GEOSERVER_USERNAME="admin"
export GEOSERVER_PASSWORD="at1as0f0z"

export PROCESS_DIR="/data/ala/data/layers/process"
export LEGEND_DIR="/data/ala/data/layers/test"
export GEOTIFF_DIR="/data/ala/data/layers/ready/geotiff"

export SHAPEFILE="${PROCESS_DIR}/${LAYER_NAME}/${LAYER_NAME}.shp"
export REPROJECTEDSHAPEFILE="/data/ala/data/layers/ready/shape/${LAYER_NAME}.shp"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar"

echo "Copy legend file to legend directory" \
&& cp ${LEGEND_FILE} "${LEGEND_DIR}/${LAYER_NAME}.sld" \
&& echo "create process directory" \ 
&& mkdir -p "${PROCESS_DIR}/${LAYER_NAME}" \
&& echo "Polygonizing gridded data" \
&& gdal_polygonize ${ADF_HEADER_FILE} -f "ESRI Shapefile" "${SHAPEFILE}" \
&& echo "Reprojecting shapefile to WGS 84" \
&& ogr2ogr -t_srs EPSG:4326  "${REPROJECTEDSHAPEFILE}" "${SHAPEFILE}" \
&& echo "Creating layer and fields table entries for layer, converting shapefile to database table" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualFromShapefileDatabaseLoader "${LAYER_ID}" "${LAYER_NAME}" "${LAYER_DESCRIPTION}" "${FIELDS_SID}" "${FIELDS_SNAME}" "${FIELDS_SDESCRIPTION}" "${REPROJECTEDSHAPEFILE}" "${DB_USERNAME}" "${DB_PASSWORD}" "${DB_JDBC_URL}" "${DB_HOST}" "${DB_NAME}" "${DERIVED_COLUMNS_FILE}" \
&& echo "Create objects from layer" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualObjectCreator "${LAYER_ID}" "${DB_USERNAME}" "${DB_PASSWORD}" "${DB_JDBC_URL}" \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.PostgisTableGeoserverLoader "${GEOSERVER_BASE_URL}" "${GEOSERVER_USERNAME}" "${GEOSERVER_PASSWORD}" "${LAYER_ID}" "${LAYER_NAME}" "${LAYER_DESCRIPTION}" "${LEGEND_DIR}/${LAYER_NAME}.sld"
