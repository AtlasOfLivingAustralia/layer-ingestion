package au.org.ala.layers.ingestion;

import java.io.File;

import org.ala.layers.util.Bil2diva;
import org.apache.commons.io.IOUtils;


public class EnvironmentalImport {
    
    public static void main(String[] args) throws Exception {
        String layerName = args[0];
        String units = args[1];
        String rawDataDirPath = args[2];
        String processDirPath = args[3];
        String divaDirPath = args[4];
        String legendDirPath = args[5];
        String geotiffDirPath = args[6];
        String dbJdbcUrl = args[7];
        String dbUsername = args[8];
        String dbPassword = args[9];
        String geoserverUsername = args[10];
        String geoserverPassword = args[11];
        String geoserverBaseRestURL = args[12];
        
        // check validity of passed in directory names
        File rawDataDir = new File(rawDataDirPath);
        if (!rawDataDir.exists() || !rawDataDir.isDirectory()) {
            throw new RuntimeException("Supplied raw data directory " + rawDataDirPath + " does not exist or is not a directory");
        }
        
        File processDir = new File(processDirPath);
        if (!processDir.exists() || !processDir.isDirectory()) {
            throw new RuntimeException("Supplied process directory " + processDirPath + " does not exist or is not a directory");
        }
        
        File divaDir = new File(divaDirPath);
        if (!divaDir.exists() || !divaDir.isDirectory()) {
            throw new RuntimeException("Supplied diva directory " + divaDirPath + " does not exist or is not a directory");
        }
        
        File legendDir = new File(legendDirPath);
        if (!legendDir.exists() || !legendDir.isDirectory()) {
            throw new RuntimeException("Supplied legend directory " + legendDirPath + " does not exist or is not a directory");
        }
        
        File geotiffDir = new File(geotiffDirPath);
        if (!geotiffDir.exists() || !geotiffDir.isDirectory()) {
            throw new RuntimeException("Supplied geotiff directory " + geotiffDirPath + " does not exist or is not a directory");
        }
        
        // create process directory
        File layerProcessDir = new File(processDir, layerName);
        layerProcessDir.mkdir();
        
        // gdalwarp
        File hdrFile = new File(rawDataDir, "hdr.adf");
        if (!hdrFile.exists()) {
            throw new RuntimeException("Could not find hdr.adf in " + rawDataDirPath);
        }
        
        File bilFile = new File(layerProcessDir, layerName + ".bil");
        
        Process procGdalWarp = Runtime.getRuntime().exec(new String[] {"gdalwarp", "-of", "EHdr", "-ot", "Float32", "-dstnodata", "-9999",
                hdrFile.getAbsolutePath(), bilFile.getAbsolutePath()});
        
        int gdalWarpReturnVal = procGdalWarp.waitFor();

        if (gdalWarpReturnVal != 0) {
            String gdalWarpErrorOutput = IOUtils.toString(procGdalWarp.getErrorStream());
            throw new RuntimeException("gdalwarp failed: " + gdalWarpErrorOutput);
        }
        
        // bil2diva
        Bil2diva.main(new String[] {layerProcessDir.getAbsolutePath(), divaDir.getAbsolutePath(), units});
        
        // GridLegend
        GridLegend.main(new String[] { divaDir.getAbsolutePath(), legendDir.getAbsolutePath() });
        
        // gdal_translate
        File geotiffFile = new File(geotiffDir, layerName + ".tif");
        Process procGdalTranslate = Runtime.getRuntime().exec(new String[] {"gdal_translate", "-of", "GTiff", bilFile.getAbsolutePath(), geotiffFile.getAbsolutePath()});
        
        int gdalTranslateReturnVal = procGdalTranslate.waitFor();
        
        if (gdalTranslateReturnVal != 0) {
            String gdalTranslateErrorOutput = IOUtils.toString(procGdalWarp.getErrorStream());
            throw new RuntimeException("gdal_translate failed: " + gdalTranslateErrorOutput);
        }
        
        // gdal info to get extents, min/max
        Process procGdalInfo = Runtime.getRuntime().exec(new String[] {"gdalinfo", hdrFile.getAbsolutePath()});
        String gdalInfoOutput = IOUtils.toString(procGdalInfo.getInputStream());
        
        // process should have already terminated at this point - just do
        // this to get the return code
        int gdalInfoReturnVal = procGdalTranslate.waitFor();

        if (gdalInfoReturnVal != 0) {
            String gdalInfoErrorOutput = IOUtils.toString(procGdalInfo.getErrorStream());
            throw new RuntimeException("gdalinfo failed: " + gdalInfoErrorOutput);
        }
        
        // create layers table entry
        
        // create fields table entry
        
        // create layer in geoserver
        
        // create style in geoserver (refer to SLD)
        
        // upload sld file
        
        // create default style in geoserver
    }

}
