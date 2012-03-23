package au.org.ala.layers.ingestion;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // gdalwarp System.out.println("Running gdalwarp");'
        File hdrFile = new File(rawDataDir, "hdr.adf");
        if (!hdrFile.exists()) {
            throw new RuntimeException("Could not find hdr.adf in " + rawDataDirPath);
        }

        File bilFile = new File(layerProcessDir, layerName + ".bil");

        Process procGdalWarp = Runtime.getRuntime().exec(new String[] { "gdalwarp", "-of", "EHdr", "-ot", "Float32", "-dstnodata", "-9999", hdrFile.getAbsolutePath(), bilFile.getAbsolutePath() });

        int gdalWarpReturnVal = procGdalWarp.waitFor();

        if (gdalWarpReturnVal != 0) {
            String gdalWarpErrorOutput = IOUtils.toString(procGdalWarp.getErrorStream());
            throw new RuntimeException("gdalwarp failed: " + gdalWarpErrorOutput);
        }

        // bil2diva
        System.out.println("Running Bil2diva");
        Bil2diva.main(new String[] { layerProcessDir.getAbsolutePath() + File.separator + layerName, divaDir.getAbsolutePath() + File.separator + layerName, units });

        // GridLegend System.out.println("Running GridLegend");
        GridLegend.main(new String[] { divaDir.getAbsolutePath() + File.separator + layerName, legendDir.getAbsolutePath() + File.separator + layerName });

        // gdal_translate
        System.out.println("Running gdal_translate");
        File geotiffFile = new File(geotiffDir, layerName + ".tif");
        Process procGdalTranslate = Runtime.getRuntime().exec(new String[] { "gdal_translate", "-of", "GTiff", bilFile.getAbsolutePath(), geotiffFile.getAbsolutePath() });

        int gdalTranslateReturnVal = procGdalTranslate.waitFor();

        if (gdalTranslateReturnVal != 0) {
            String gdalTranslateErrorOutput = IOUtils.toString(procGdalTranslate.getErrorStream());
            throw new RuntimeException("gdal_translate failed: " + gdalTranslateErrorOutput);
        }

        // gdal info to get extents, min/max
        System.out.println("Running gdalinfo");
        Process procGdalInfo = Runtime.getRuntime().exec(new String[] { "gdalinfo", hdrFile.getAbsolutePath() });
        String gdalInfoOutput = IOUtils.toString(procGdalInfo.getInputStream());

        // process should have already terminated at this point - just do
        // this to get the return code
        int gdalInfoReturnVal = procGdalInfo.waitFor();

        if (gdalInfoReturnVal != 0) {
            String gdalInfoErrorOutput = IOUtils.toString(procGdalInfo.getErrorStream());
            throw new RuntimeException("gdalinfo failed: " + gdalInfoErrorOutput);
        }

        double minValue;
        double maxValue;
        double minLatitude;
        double maxLatitude;
        double minLongitude;
        double maxLongitude;

        // Use regular expression matching to pull the min and max values from
        // the output of gdalinfo
        Pattern p1 = Pattern.compile("Min=(.+) Max=(.+)$", Pattern.MULTILINE);
        Matcher m1 = p1.matcher(gdalInfoOutput);
        if (m1.find()) {
            if (m1.groupCount() == 2) {
                minValue = Double.parseDouble(m1.group(1));
                maxValue = Double.parseDouble(m1.group(2));

            } else {
                throw new RuntimeException("error reading min and max from gdalinfo: " + gdalInfoOutput);
            }
        } else {
            throw new RuntimeException("error reading extents from gdalinfo: " + gdalInfoOutput);
        }

        // Determine min/max latitude and longitude from gdalinfo output
        double[] upperLeftCornerCoords = extractCornerCoordinates(gdalInfoOutput, "^Upper Left\\s+\\(\\s+(.+),\\s+(.+)\\) .+$");
        double[] lowerLeftCornerCoords = extractCornerCoordinates(gdalInfoOutput, "^Lower Left\\s+\\(\\s+(.+),\\s+(.+)\\) .+$");
        double[] upperRightCornerCoords = extractCornerCoordinates(gdalInfoOutput, "^Upper Right\\s+\\(\\s+(.+),\\s+(.+)\\) .+$");
        double[] lowerRightCornerCoords = extractCornerCoordinates(gdalInfoOutput, "^Lower Right\\s+\\(\\s+(.+),\\s+(.+)\\) .+$");

        double[] minMaxValues = determineMinMaxLatitudeLongitude(upperLeftCornerCoords, lowerLeftCornerCoords, upperRightCornerCoords, lowerRightCornerCoords);
        minLatitude = minMaxValues[0];
        maxLatitude = minMaxValues[1];
        minLongitude = minMaxValues[2];
        maxLongitude = minMaxValues[3];

        System.out.println(minValue);
        System.out.println(maxValue);

        System.out.println(minLatitude);
        System.out.println(maxLatitude);
        System.out.println(minLongitude);
        System.out.println(maxLongitude);

        /*
         * // create layers table entry
         * System.out.println("Creating layers table entry");
         * 
         * // create fields table entry
         * System.out.println("Creating fields table entry");
         * 
         * // create layer in geoserver
         * System.out.println("Creating layer in geoserver");
         * 
         * // create style in geoserver (refer to SLD)
         * System.out.println("Creating style in geoserver");
         * 
         * // upload sld file
         * System.out.println("Uploading sld file to geoserver");
         * 
         * // create default style in geoserver
         * System.out.println("Creating default style in geoserver");
         */
    }

    private static double[] extractCornerCoordinates(String gdalInfoOutput, String pattern) {
        double[] retArray = new double[2];
        double latitude;
        double longitude;

        Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
        Matcher m = p.matcher(gdalInfoOutput);
        if (m.find()) {
            if (m.groupCount() == 2) {
                longitude = Double.parseDouble(m.group(1));
                latitude = Double.parseDouble(m.group(2));

            } else {
                throw new RuntimeException("error reading corner coordinates from gdalinfo: " + gdalInfoOutput);
            }
        } else {
            throw new RuntimeException("error reading corner coordinates from gdalinfo: " + gdalInfoOutput);
        }

        retArray[0] = latitude;
        retArray[1] = longitude;

        return retArray;
    }

    private static double[] determineMinMaxLatitudeLongitude(double[] upperLeftCornerCoords, double[] lowerLeftCornerCoords, double[] upperRightCornerCoords, double[] lowerRightCornerCoords) {
        double[] retArray = new double[4];

        List<Double> latitudeValues = new ArrayList<Double>();
        latitudeValues.add(upperLeftCornerCoords[0]);
        latitudeValues.add(lowerLeftCornerCoords[0]);
        latitudeValues.add(upperRightCornerCoords[0]);
        latitudeValues.add(lowerRightCornerCoords[0]);

        double minLatitude = Collections.min(latitudeValues);
        double maxLatitude = Collections.max(latitudeValues);

        List<Double> longitudeValues = new ArrayList<Double>();
        longitudeValues.add(upperLeftCornerCoords[1]);
        longitudeValues.add(lowerLeftCornerCoords[1]);
        longitudeValues.add(upperRightCornerCoords[1]);
        longitudeValues.add(lowerRightCornerCoords[1]);

        double minLongitude = Collections.min(longitudeValues);
        double maxLongitude = Collections.max(longitudeValues);

        retArray[0] = minLatitude;
        retArray[1] = maxLatitude;
        retArray[2] = minLongitude;
        retArray[3] = maxLongitude;

        return retArray;
    }

}
