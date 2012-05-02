#!/bin/bash

echo Preparing contextual layers for analysis
java -Xmx10G -DANALYSIS_RESOLUTIONS=0.5 -cp layer-ingestion-1.0-SNAPSHOT-jar-with-dependencies.jar org.ala.layers.util.AnalysisLayerUtil auto shapes
#echo Generating tabulation for contextual layers
