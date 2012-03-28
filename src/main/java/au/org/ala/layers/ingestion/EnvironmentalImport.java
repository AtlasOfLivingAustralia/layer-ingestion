package au.org.ala.layers.ingestion;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ala.layers.legend.GridLegend;
import org.ala.layers.util.Bil2diva;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class EnvironmentalImport {

    public static void main(String[] args) throws Exception {
        String layerName = args[0];
        String layerDescription = args[1];
        String units = args[2];
        String rawDataDirPath = args[3];
        String processDirPath = args[4];
        String divaDirPath = args[5];
        String legendDirPath = args[6];
        String geotiffDirPath = args[7];
        String dbJdbcUrl = args[8];
        String dbUsername = args[9];
        String dbPassword = args[10];
        String geoserverUsername = args[11];
        String geoserverPassword = args[12];
        String geoserverQueryTemplate = args[13];

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

        System.out.println("Beginning environmetal load");
        
        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
        conn.setAutoCommit(false);
        
        try {
        // create process directory
        File layerProcessDir = new File(processDir, layerName);
        layerProcessDir.mkdir();

        // running gdalwarp
        System.out.println("Running gdalwarp");
        File hdrFile = new File(rawDataDir, "hdr.adf");
        if (!hdrFile.exists()) {
            throw new RuntimeException("Could not find hdr.adf in " + rawDataDirPath);
        }

        File bilFile = new File(layerProcessDir, layerName + ".bil");

        Process procGdalWarp = Runtime.getRuntime().exec(new String[] { "gdalwarp", "-of", "EHdr", "-ot", "Float32", "-r", "cubicspline", hdrFile.getAbsolutePath(), bilFile.getAbsolutePath() });

        int gdalWarpReturnVal = procGdalWarp.waitFor();

        if (gdalWarpReturnVal != 0) {
            String gdalWarpErrorOutput = IOUtils.toString(procGdalWarp.getErrorStream());
            throw new RuntimeException("gdalwarp failed: " + gdalWarpErrorOutput);
        }

        // bil2diva
        System.out.println("Running Bil2diva");
        boolean bil2DivaSuccess = Bil2diva.bil2diva(layerProcessDir.getAbsolutePath() + File.separator + layerName, divaDir.getAbsolutePath() + File.separator + layerName, units);
        if (!bil2DivaSuccess) {
            throw new RuntimeException("Bil2diva Failed");
        }

        System.out.println("Running GridLegend");
        boolean gridLegendSuccess = GridLegend.generateGridLegend(divaDir.getAbsolutePath() + File.separator + layerName, legendDir.getAbsolutePath() + File.separator + layerName, 1, false);
        if (!gridLegendSuccess) {
            throw new RuntimeException("GridLegend Failed");
        }

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

        // Get ID to use for layer
        System.out.println("Generating ID for new layer...");
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT MAX(id) from layers");
        rs.next();

        int id = 1;
        String idAsString = rs.getString(1);
        if (idAsString != null) {
            id = Integer.parseInt(idAsString);
            id++;
        }

        // insert to layers table
        String displayPath = MessageFormat.format(geoserverQueryTemplate, layerName);
        System.out.println("Creating layers table entry...");
        PreparedStatement createLayersStatement = createLayersInsert(conn, id, layerDescription, divaDir.getAbsolutePath(), layerName, displayPath, minLatitude, minLongitude, maxLatitude, maxLongitude, minValue, maxValue, units);
        createLayersStatement.execute();

        // insert to fields table
        System.out.println("Creating fields table entry...");
        PreparedStatement createFieldsStatement = createFieldsInsert(conn, id, layerName, layerDescription);
        createFieldsStatement.execute();

        // ========== CONFIGURE LAYER IN GEOSERVER ===================
        
        DefaultHttpClient httpClient = new DefaultHttpClient();
        httpClient.getCredentialsProvider().setCredentials(new AuthScope("localhost", 8082), new UsernamePasswordCredentials(geoserverUsername, geoserverPassword));
        
        // Create layer in geoserver
        System.out.println("Creating layer in geoserver...");
        HttpPut createLayerPut = new HttpPut(String.format("http://localhost:8082/geoserver/rest/workspaces/ALA/coveragestores/%s/external.geotiff", layerName));
        createLayerPut.setHeader("Content-type", "text/plain");
        createLayerPut.setEntity(new StringEntity(geotiffFile.toURI().toURL().toString()));
        
        HttpResponse createLayerResponse = httpClient.execute(createLayerPut);
        
        if (createLayerResponse.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Error creating layer in geoserver: " + createLayerResponse.toString());
        }
        
        EntityUtils.consume(createLayerResponse.getEntity());
        
        // create style in geoserver (refer to SLD)
        System.out.println("Creating style in geoserver");        
        HttpPost createStylePost = new HttpPost("http://localhost:8082/geoserver/rest/styles");
        createStylePost.setHeader("Content-type", "text/xml");
        createStylePost.setEntity(new StringEntity(String.format("<style><name>%s_style</name><filename>%s.sld</filename></style>", layerName, layerName)));
        
        HttpResponse createStyleResponse = httpClient.execute(createLayerPut);
        
        if (createStyleResponse.getStatusLine().getStatusCode() != 201) {
            throw new RuntimeException("Error creating layer in geoserver: " + createStyleResponse.toString());
        }
        
        EntityUtils.consume(createStyleResponse.getEntity());
        
        // upload sld
        System.out.println("Uploading sld file to geoserver");
        File sldFile = new File(legendDir, layerName + ".sld");
        String sldData = FileUtils.readFileToString(sldFile);
        
        HttpPut uploadSldPut = new HttpPut(String.format("http://localhost:8082/geoserver/rest/styles/%s_style", layerName));
        uploadSldPut.setHeader("Content-type", "application/vnd.ogc.sld+xml");
        uploadSldPut.setEntity(new StringEntity(sldData));
        
        HttpResponse uploadSldResponse = httpClient.execute(uploadSldPut);
        
        if (uploadSldResponse.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Error creating layer in geoserver: " + uploadSldResponse.toString());
        }
        
        EntityUtils.consume(uploadSldResponse.getEntity());        
        
        // set default style in geoserver
        System.out.println("Setting default style in geoserver");
        HttpPut setDefaultStylePut = new HttpPut(String.format("http://localhost:8082/geoserver/rest/layers/ALA:%s", layerName));
        setDefaultStylePut.setHeader("Content-type", "text/xml");
        setDefaultStylePut.setEntity(new StringEntity(String.format("<layer><enabled>true</enabled><defaultStyle><name>%s_style</name></defaultStyle></layer>", layerName)));
        
        HttpResponse setDefaultStyleResponse = httpClient.execute(createLayerPut);
        
        if (setDefaultStyleResponse.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Error creating layer in geoserver: " + setDefaultStyleResponse.toString());
        }
        
        EntityUtils.consume(setDefaultStyleResponse.getEntity());  
               
        conn.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            conn.rollback();
        }
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

    private static PreparedStatement createLayersInsert(Connection conn, int layerId, String description, String path, String name, String displayPath, double minLatitude, double minLongitude,
            double maxLatitude, double maxLongitude, double valueMin, double valueMax, String units) throws SQLException {
        PreparedStatement stLayersInsert = conn
                .prepareStatement("INSERT INTO layers (id, name, description, type, path, displayPath, minlatitude, minlongitude, maxlatitude, maxlongitude, enabled, displayname, environmentalvaluemin, environmentalvaluemax, environmentalvalueunits, uid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stLayersInsert.setInt(1, layerId);
        stLayersInsert.setString(2, name);
        stLayersInsert.setString(3, description);
        stLayersInsert.setString(4, "Environmental");
        stLayersInsert.setString(5, path);
        stLayersInsert.setString(6, displayPath);
        stLayersInsert.setDouble(7, minLatitude);
        stLayersInsert.setDouble(8, minLongitude);
        stLayersInsert.setDouble(9, maxLatitude);
        stLayersInsert.setDouble(10, maxLongitude);
        stLayersInsert.setBoolean(11, true);
        stLayersInsert.setString(12, description);
        stLayersInsert.setDouble(13, valueMin);
        stLayersInsert.setDouble(14, valueMax);
        stLayersInsert.setString(15, units);
        stLayersInsert.setInt(16, layerId);
        return stLayersInsert;
    }

    private static PreparedStatement createFieldsInsert(Connection conn, int layerId, String name, String description) throws SQLException {
        // TOOD slightly different statement if sdesc is null...

        PreparedStatement stFieldsInsert = conn
                .prepareStatement("INSERT INTO fields (name, id, \"desc\", type, spid, sid, sname, sdesc, indb, enabled, last_update, namesearch, defaultlayer, \"intersect\", layerbranch, analysis)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stFieldsInsert.setString(1, name);
        stFieldsInsert.setString(2, "el" + Integer.toString(layerId));
        stFieldsInsert.setString(3, description);
        stFieldsInsert.setString(4, "e");
        stFieldsInsert.setString(5, Integer.toString(layerId));
        stFieldsInsert.setNull(6, Types.VARCHAR);
        stFieldsInsert.setNull(7, Types.VARCHAR);
        stFieldsInsert.setNull(8, Types.VARCHAR);
        stFieldsInsert.setBoolean(9, true);
        stFieldsInsert.setBoolean(10, true);
        stFieldsInsert.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
        stFieldsInsert.setBoolean(12, true);
        stFieldsInsert.setBoolean(13, false);
        stFieldsInsert.setBoolean(14, false);
        stFieldsInsert.setBoolean(15, false);
        stFieldsInsert.setBoolean(16, true);

        return stFieldsInsert;
    }

}
