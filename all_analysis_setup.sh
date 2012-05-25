#!/bin/bash
export PATH_TO_RECORDS_CSV="/data/ala/data/layers/process/density/_records.csv"

export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar"
export READY_DIR="/data/ala/data/layers/ready"
export DIVA_DIR="/data/ala/data/layers/ready/diva"
export DIVA_CACHE_DIR="/data/ala/data/layers/ready/diva_cache"

export DB_JDBC_URL="jdbc:postgresql://ala-maps-db.vic.csiro.au:5432/layersdb"
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"

echo "backup diva cache directory" \
&& tar cvzf "${DIVA_CACHE_DIR}" "${READY_DIR}/diva_cache.tgz" \ 
&& echo "deleting contents of diva_cache directory" \
&& rm ${DIVA_CACHE_DIR}/* \
&& echo "running GridCacheBuilder" \
&& java -Xmx14G -cp "${JAVA_CLASSPATH}" org.ala.layers.grid.GridCacheBuilder "${DIVA_DIR}" "${DIVA_CACHE_DIR}" \
&& echo "Preparing environmental layers for analysis" \
&& java -Xmx14G -DANALYSIS_RESOLUTIONS=0.5,0.01,0.0025 -cp "${JAVA_CLASSPATH}" org.ala.layers.util.AnalysisLayerUtil auto grids \
&& echo "Calculating environmental layer distance values" \
&& java -Xmx14G -cp "${JAVA_CLASSPATH}" org.ala.spatial.analysis.index.LayerDistanceIndex "${DIVA_DIR}" "${DIVA_CACHE_DIR}" \
&& echo "Preparing contextual layers for analysis" \
&& java -Xmx14G -DANALYSIS_RESOLUTIONS=0.5 -cp "${JAVA_CLASSPATH}" org.ala.layers.util.AnalysisLayerUtil auto shapes \
&& echo "Generating tabulation for contextual layers" \
&& java -Xmx14G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 1 \
&& -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 3 \
&& -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 5 "${PATH_TO_RECORDS_CSV}" \
&& -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 6 "${PATH_TO_RECORDS_CSV}" \
&& -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 4

