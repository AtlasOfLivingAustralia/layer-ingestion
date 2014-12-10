#!/bin/bash
# Loads a contextual layer from raw .adf data, using the GridClassesBuilder to generate class data which is
# read from disk
# NOTE: The following 5 variables need to be modified for each new layer
export LAYER_ID=2000
export LAYER_SHORT_NAME=geo_feature_gridded
export LAYER_DISPLAY_NAME=Geomorphology_of_australian_seafloor
export ADF_HEADER_FILE=C:/xxx/geo_feature/hdr.adf
export CLASSES_MAPPING_FILE=C:/xxx/geo_feature/geo_feature.txt

export DB_USERNAME=postgres
export DB_PASSWORD=password
export DB_JDBC_URL=jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb

export GEOSERVER_BASE_URL="http://localhost:8082/geoserver"
export GEOSERVER_USERNAME=admin
export GEOSERVER_PASSWORD=password

export PROCESS_DIR="/data/ala/data/layers/process"
export SHAPE_DIVA_DIR="/data/ala/data/layers/ready/shape_diva"
export GEOTIFF_DIR="/data/ala/data/layers/ready/geotiff"

export JAVA_CLASSPATH=./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*

echo "create process directory" \
&& mkdir -p "${PROCESS_DIR}/${LAYER_SHORT_NAME}" \
&& echo "convert adf to bil, reprojecting to WGS 84" \
&& gdalwarp -r cubicspline -of EHdr -ot Float32 -t_srs EPSG:4326 "${ADF_HEADER_FILE}" "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}.bil" \
&& echo "convert bil to diva" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.util.Bil2diva "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}" "${SHAPE_DIVA_DIR}/${LAYER_SHORT_NAME}" "${UNITS}" \
&& echo "Copy classes mapping file to shape_diva directory" \
&& cp ${CLASSES_MAPPING_FILE} ${SHAPE_DIVA_DIR}/${LAYER_SHORT_NAME}.txt \
&& echo "Run GridClassBuilder tool" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.grid.GridClassBuilder "${SHAPE_DIVA_DIR}/${LAYER_SHORT_NAME}" \
&& echo "Convert diva for polygons into bil" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.util.Diva2bil "${SHAPE_DIVA_DIR}/${LAYER_SHORT_NAME}/polygons" "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}" \
&& echo "Convert polygons bil into geotiff" \
&& gdal_translate -of GTiff "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}.bil" "${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif" \
&& echo "Creating layer and fields table entries for layer" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.contextual.ContextualFromGridDatabaseLoader "${LAYER_ID}" "${LAYER_SHORT_NAME}" "${LAYER_DISPLAY_NAME}" "${SHAPE_DIVA_DIR}/${LAYER_SHORT_NAME}/polygons.grd" "${DB_USERNAME}" "${DB_PASSWORD}" "${DB_JDBC_URL}" \
&& echo "Load layer into geoserver" \
java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.GeotiffGeoserverLoader "${LAYER_SHORT_NAME}" "${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif" "${SHAPE_DIVA_DIR}/${LAYER_SHORT_NAME}/polygons.sld" "${GEOSERVER_BASE_URL}" "${GEOSERVER_USERNAME}" "${GEOSERVER_PASSWORD}"