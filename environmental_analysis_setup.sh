#!/bin/bash
export JAVA_CLASSPATH="./layer-ingestion-1.0-SNAPSHOT.jar"
export READY_DIR="/data/ala/data/layers/ready"
export DIVA_DIR="/data/ala/data/layers/ready/diva"
export DIVA_CACHE_DIR="/data/ala/data/layers/ready/diva_cache"

echo "backup diva cache directory" \
&& tar cvzf "${DIVA_CACHE_DIR}" "${READY_DIR}/diva_cache.tgz" \ 
&& echo "deleting contents of diva_cache directory" \
&& rm "${DIVA_CACHE_DIR}/*" \
&& echo "running GridCacheBuilder" \
&& java -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.layers.grid.GridCacheBuilder "${DIVA_DIR}" "${DIVA_CACHE_DIR}" \
&& echo "Preparing environmental layers for analysis" \
&& java -Xmx20G -DANALYSIS_RESOLUTIONS=0.5,0.01 -cp "${JAVA_CLASSPATH}" org.ala.layers.util.AnalysisLayerUtil auto grids \
&& echo "Calculating layer distance values" \
&& java -Xmx20G -cp "${JAVA_CLASSPATH}" org.ala.spatial.analysis.index.LayerDistanceIndex "${DIVA_DIR}" "${DIVA_CACHE_DIR}

