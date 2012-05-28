#!/bin/bash
export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar:./lib/*"

echo "Preparing contextual layers for analysis" \
&& java -Xmx10G -DANALYSIS_RESOLUTIONS=0.5 -cp "${JAVA_CLASSPATH}" org.ala.layers.util.AnalysisLayerUtil auto shapes \
&& echo "Generating tabulation for contextual layers"
&& java -Xmx10G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 1 \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 3 \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 5 "${PATH_TO_RECORDS_CSV}" \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 6 "${PATH_TO_RECORDS_CSV}" \
&& -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.tabulation.TabulationGenerator "${DB_JDBC_URL}" "${DB_USERNAME}" "${DB_PASSWORD}" 4

