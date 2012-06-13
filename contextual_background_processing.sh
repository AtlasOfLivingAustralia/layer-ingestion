#!/bin/bash
# This script performs background processing on contextual layers.
# The variable below needs to be edited to point to the latest longitude, latitude and lsid dump file:
export PATH_TO_RECORDS_CSV="/data/ala/data/layers/process/density/_records.csv"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

export DB_JDBC_URL="jdbc:postgresql://ala-maps-db.vic.csiro.au:5432/layersdb"
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"

echo "Preparing contextual layers for analysis" \
&& java -Xmx20G -DANALYSIS_RESOLUTIONS=0.5,0.01 -cp "${JAVA_CLASSPATH}" org.ala.layers.util.AnalysisLayerUtil auto shapes \
&& echo "Generating tabulation for contextual layers" \
&& java -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator 4 "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 1 \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator 4 "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 3 \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator 4 "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 5 "${PATH_TO_RECORDS_CSV}" \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator 4 "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 6 "${PATH_TO_RECORDS_CSV}" \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator 4 "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 4
# TODO layer thumbnails