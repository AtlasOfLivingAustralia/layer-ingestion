#!/bin/bash
# Loads an environmental layer from raw .adf data
# NOTE: The following 5 variables need to be modified for each new layer
export LAYER_ID=111
export LAYER_NAME="ga_bath_rocky" 
export LAYER_DESCRIPTION=
export UNITS="%" 
export ADF_HEADER_FILE="/data/ala/data/layers/raw/ga_bath_rocky/hdr.adf"

export DBUSERNAME="postgres"
export DBPASSWORD="postgres"
export DBJDBCURL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"

export GEOSERVERBASEURL="http://localhost:8082/geoserver"
export GEOSERVERUSERNAME="admin"
export GEOSERVERPASSWORD="at1as0f0z" 

export PROCESS_DIR="/data/ala/data/layers/process"
export DIVA_DIR="/data/ala/data/layers/ready/diva"
export LEGEND_DIR="/data/ala/data/layers/test"
export GEOTIFF_DIR="/data/ala/data/layers/ready/geotiff"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

echo "create process directory" 
&& mkdir -p "${PROCESS_DIR}/${LAYER_NAME}" \
&& echo "convert adf to bil, reprojecting to WGS 84" \
&& gdalwarp -of EHdr -ot Float32 -t_srs EPSG:4326 "${ADF_HEADER_FILE}" "${PROCESS_DIR}/${LAYER_NAME}/${LAYER_NAME}.bil" \
&& echo "convert bil to diva" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.util.Bil2diva "${PROCESS_DIR}/${LAYER_NAME}/${LAYER_NAME}" "${DIVA_DIR}/${LAYER_NAME}" "${UNITS}" \
&& echo "generate sld legend file" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.legend.GridLegend "${DIVA_DIR}/${LAYER_NAME}" "${LEGEND_DIR}/${LAYER_NAME}" \
&& echo "convert bil to geotiff" \
&& gdal_translate -of GTiff "${PROCESS_DIR}/${LAYER_NAME}/${LAYER_NAME}.bil" "${GEOTIFF_DIR}/${LAYER_NAME}.tif" \
&& echo "Creating layer and fields table entries for layer" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.environmental.EnvironmentalDatabaseLoader "${LAYER_ID}" "${LAYER_NAME}" "${LAYER_DESCRIPTION}" "${UNITS}" "${DIVA_DIR}/${LAYER_NAME}.grd" "${DBUSERNAME}" "${DBPASSWORD}" "${DBJDBCURL}" \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.GeotiffGeoserverLoader "${LAYER_NAME}" "${GEOTIFF_DIR}/${LAYER_NAME}.tif" "${LEGEND_DIR}/${LAYER_NAME}.sld" "${GEOSERVERBASEURL}" "${GEOSERVERUSERNAME}" "${GEOSERVERPASSWORD}"

