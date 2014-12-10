#!/bin/bash
# Loads an environmental layer from raw .bil data
# NOTE: The following 5 variables need to be modified for each new layer
export LAYER_ID=989
export LAYER_SHORT_NAME="gbr_gbr100" 
export LAYER_DISPLAY_NAME="Great Barrier Reef 100m DEM"
export UNITS="m"
export RAW_BIL_FILE="/data/ala/data/layers/raw/gbr_gbr100/gbr_gbr100.bil"

export DBUSERNAME="postgres"
export DBPASSWORD="password"
export DBJDBCURL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"

export GEOSERVERBASEURL="http://localhost:8082/geoserver"
export GEOSERVERUSERNAME="admin"
export GEOSERVERPASSWORD="password" 

export PROCESS_DIR="/data/ala/data/layers/process"
export DIVA_DIR="/data/ala/data/layers/ready/diva"
export LEGEND_DIR="/data/ala/data/layers/test"
export GEOTIFF_DIR="/data/ala/data/layers/ready/geotiff"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

echo "create process directory" \
&& mkdir -p "${PROCESS_DIR}/${LAYER_SHORT_NAME}" \
&& echo "reproject bil file to WGS 84 and copy to process dir" \
&& gdalwarp -r cubicspline -of EHdr -ot Float32 -t_srs EPSG:4326 "${RAW_BIL_FILE}" "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}.bil" \
&& echo "convert bil to diva" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.util.Bil2diva "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}" "${DIVA_DIR}/${LAYER_SHORT_NAME}" "${UNITS}" \
&& echo "generate sld legend file" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.legend.GridLegend "${DIVA_DIR}/${LAYER_SHORT_NAME}" "${LEGEND_DIR}/${LAYER_SHORT_NAME}" \
&& echo "convert bil to geotiff" \
&& gdal_translate -of GTiff "${PROCESS_DIR}/${LAYER_SHORT_NAME}/${LAYER_SHORT_NAME}.bil" "${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif" \
&& echo "Creating layer and fields table entries for layer" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.environmental.EnvironmentalDatabaseLoader "${LAYER_ID}" "${LAYER_SHORT_NAME}" "${LAYER_DISPLAY_NAME}" "${UNITS}" "${DIVA_DIR}/${LAYER_SHORT_NAME}.grd" "${DBUSERNAME}" "${DBPASSWORD}" "${DBJDBCURL}" \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.GeotiffGeoserverLoader "${LAYER_SHORT_NAME}" "${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif" "${LEGEND_DIR}/${LAYER_SHORT_NAME}.sld" "${GEOSERVERBASEURL}" "${GEOSERVERUSERNAME}" "${GEOSERVERPASSWORD}"

