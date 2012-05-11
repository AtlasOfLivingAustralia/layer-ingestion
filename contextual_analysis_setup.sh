#!/bin/bash
export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT:./lib/*"

echo Preparing contextual layers for analysis \
&& java -Xmx10G -DANALYSIS_RESOLUTIONS=0.5 -cp "${JAVA_CLASSPATH}" org.ala.layers.util.AnalysisLayerUtil auto shapes
&& echo Generating tabulation for contextual layers

