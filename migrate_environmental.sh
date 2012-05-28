#!/bin/bash
export SSH_USERNAME=fle13g
export LAYER_ID=990
export LAYER_SHORT_NAME=alwc4

export DEV_SERVER=ala-devmaps.vm.csiro.au
export DIVA_DIR="/data/ala/data/layers/ready/diva"
export GEOTIFF_DIR="/data/ala/data/layers/ready/geotiff"

export DEV_DB_JDBC_URL="jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb"
export DEV_DB_USERNAME=postgres
export DEV_DB_PASSWORD=password
export PROD_DB_JDBC_URL="jdbc:postgresql://ala-maps-db.vic.csiro.au:5432/layersdb"
export PROD_DB_USERNAME=postgres
export PROD_DB_PASSWORD=password
export PROD_DB_HOST="ala-maps-db.vic.csiro.au"
export PROD_DB_NAME="layersdb"


export GEOSERVERBASEURL="http://localhost:8082/geoserver"
export GEOSERVERUSERNAME="admin"
export GEOSERVERPASSWORD="password"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

echo "Copy diva files from dev server" \
&& scp ${SSH_USERNAME}@${DEV_SERVER}:${DIVA_DIR}/${LAYER_SHORT_NAME}.* ${DIVA_DIR} \
&& echo "Change mode of copied shapefile" \
&& chmod 777 ${DIVA_DIR}/${LAYER_SHORT_NAME}.* \
&& echo "Change ownership of copied shapefile" \
&& chown tomcat:10 ${DIVA_DIR}/${LAYER_SHORT_NAME}.* \
&& echo "Copy geotiff files from dev server" \
&& scp ${SSH_USERNAME}@${DEV_SERVER}:${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif ${GEOTIFF_DIR} \
&& echo "Change mode of copied geotiff" \
&& chmod 777 ${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif \
&& echo "Change ownership of copied geotiff" \
&& chown tomcat:10 ${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif \
&& echo "copy database entries from dev database" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.MigrateLayerDatabaseEntries "${LAYER_ID}" "${DEV_DB_JDBC_URL}" "${DEV_DB_USERNAME}" "${DEV_DB_PASSWORD}" "${PROD_DB_JDBC_URL}" "${PROD_DB_USERNAME}" "${PROD_DB_PASSWORD}" \
&& echo "copy sld legend file from dev geoserver to current directory" \
&& wget http://spatial-dev.ala.org.au/geoserver/rest/styles/${LAYER_SHORT_NAME}_style.sld \
&& echo "Load layer into geoserver" \
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" au.org.ala.layers.ingestion.GeotiffGeoserverLoader "${LAYER_SHORT_NAME}" "${GEOTIFF_DIR}/${LAYER_SHORT_NAME}.tif" "./${LAYER_SHORT_NAME}_style.sld" "${GEOSERVERBASEURL}" "${GEOSERVERUSERNAME}" "${GEOSERVERPASSWORD}" \
&& echo "delete temporary sld file" \
&& rm "./${LAYER_SHORT_NAME}_style.sld"

# Layer thumbnails



