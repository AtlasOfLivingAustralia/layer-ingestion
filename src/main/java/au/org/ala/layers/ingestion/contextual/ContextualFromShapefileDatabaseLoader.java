package au.org.ala.layers.ingestion.contextual;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import au.org.ala.layers.ingestion.IngestionUtils;

public class ContextualFromShapefileDatabaseLoader {

    private static final String GID_COLUMN_NAME = "gid";
    private static final String ALA_NAME_COLUMN_NAME = "ala_name";
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 12) {
            System.out.println("Usage: layerId layerName layerDescription fieldsSid fieldsSname fieldsSdesc shapeFile dbUsername dbPassword dbJdbcUrl dbHost dbName [derivedColumnsFile]");
            System.exit(1);
        }

        int layerId = Integer.parseInt(args[0]);
        String layerName = args[1];
        String layerDescription = args[2];
        String fieldsSid = args[3];
        String fieldsSname = args[4];
        String fieldsSdesc = args[5];
        File shapeFile = new File(args[6]);
        String dbUsername = args[7];
        String dbPassword = args[8];
        String dbJdbcUrl = args[9];
        String dbHost = args[10];
        String dbName = args[11];

        File derivedColumnsFile = null;
        if (args.length >= 13) {
            derivedColumnsFile = new File(args[12]);
        }

        try {
            boolean success = create(layerId, layerName, layerDescription, fieldsSid, fieldsSname, fieldsSdesc, shapeFile, dbUsername, dbPassword, dbJdbcUrl, dbHost, dbName, derivedColumnsFile);
            if (success) {
                System.exit(0);
            } else {
                System.exit(1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static boolean create(int layerId, String layerName, String layerDescription, String fieldsSid, String fieldsSname, String fieldsSdesc, File shapeFile, String dbUsername,
            String dbPassword, String dbJdbcUrl, String dbHost, String dbName, File derivedColumnsFile) throws Exception {

        boolean reExportShapeFile = false;

        if (!shapeFile.exists()) {
            throw new IllegalArgumentException("File " + shapeFile.getAbsolutePath() + " does not exist");
        }

        if (derivedColumnsFile != null && !derivedColumnsFile.exists()) {
            throw new IllegalArgumentException("Derived columns mapping file " + derivedColumnsFile.getAbsolutePath() + " does not exist");
        }

        // connect to the database
        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
        conn.setAutoCommit(false);

        try {
            double[] extents = getExtents(shapeFile, layerName);
            double minLatitude = extents[0];
            double maxLatitude = extents[1];
            double minLongitude = extents[2];
            double maxLongitude = extents[3];

            importShapeFile(layerId, shapeFile, conn);

            if (derivedColumnsFile != null) {
                // Create an additional column for each derived column defined
                // on the derived columns mapping file
                List<DerivedColumnDefinition> columnDefs = readDerivedColumnsMappingFile(derivedColumnsFile);
                for (DerivedColumnDefinition columnDef : columnDefs) {
                    createDerivedDatabaseColumn(conn, layerId, columnDef.getSourceColumnName(), columnDef.getDerivedColumnName(), columnDef.getValueMap());
                }

                reExportShapeFile = true;
            } else {
                // examine number of rows in new table for shape. If there is
                // only
                // one row (single polygon), then ignore the passed in
                // fieldsSid,
                // fieldsSname and fieldsSdec
                // parameters and use the layer description for the
                // fieldsSid/fieldsSname/object name
                System.out.println("Checking row count of shape geometry table");
                ResultSet rs = conn.prepareStatement(String.format("SELECT COUNT(*) from \"%s\"", layerId)).executeQuery();
                rs.next();

                int numRows;
                String numRowsAsString = rs.getString(1);
                if (numRowsAsString != null) {
                    numRows = Integer.parseInt(numRowsAsString);
                    if (numRows == 1) {
                        System.out.println("Shape geometry table has only 1 row - creating ala_name column for use with fields and objects");
                        fieldsSid = ALA_NAME_COLUMN_NAME;
                        fieldsSname = ALA_NAME_COLUMN_NAME;
                        fieldsSdesc = null;

                        // map the gid (which must be 1, as there is only 1 row
                        // in the table) to the layer description (in a column
                        // called ala_name)
                        Map<String, String> valueMap = new HashMap<String, String>();
                        valueMap.put("1", layerDescription);

                        createDerivedDatabaseColumn(conn, layerId, GID_COLUMN_NAME, ALA_NAME_COLUMN_NAME, valueMap);
                        reExportShapeFile = true;
                    }
                }
            }

            // insert to layers table
            String displayPath = MessageFormat.format(IngestionUtils.GEOSERVER_QUERY_TEMPLATE, layerName);
            System.out.println("Creating layers table entry...");
            PreparedStatement createLayersStatement = IngestionUtils.createLayersInsertForContextual(conn, layerId, layerDescription, shapeFile.getParentFile().getAbsolutePath(), layerName, displayPath, minLatitude,
                    minLongitude, maxLatitude, maxLongitude);
            createLayersStatement.execute();

            // insert to fields table
            System.out.println("Creating fields table entry...");
            String fieldId = IngestionUtils.CONTEXTUAL_FIELD_PREFIX + Integer.toString(layerId);
            PreparedStatement createFieldsStatement = IngestionUtils.createFieldsInsert(conn, layerId, layerName, layerDescription, fieldId, IngestionUtils.CONTEXTUAL_REGULAR_FIELD_TYPE, fieldsSid, fieldsSname, fieldsSdesc, true, true, true, true, false, false, true, true);
            createFieldsStatement.execute();

            conn.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            conn.rollback();
            return false;
        }

        // Shapefile must be reexported after database changes have been
        // committed. Otherwise the table that we attempt to export will not
        // exist.
        if (reExportShapeFile) {
            // Overrwrite existing shape file with data from the
            // modified postgis table. This need to be done because
            // for the purposes of
            // analysis, the data in the database and the shapefile
            // must be identical
            try {
                reExportShapeFile(shapeFile, layerId, dbHost, dbName, dbUsername, dbPassword);
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }

        return true;
    }

    /**
     * use ogrinfo to determine layer extents
     * 
     * @param shapeFile
     * @param layerName
     * @return
     * @throws Exception
     */
    private static double[] getExtents(File shapeFile, String layerName) throws Exception {
        double[] extents = new double[4];

        System.out.println("Getting extents...");
        double minLatitude;
        double maxLatitude;
        double minLongitude;
        double maxLongitude;
        Process procOgrinfo = Runtime.getRuntime().exec(new String[] { "ogrinfo", "-so", shapeFile.getAbsolutePath(), layerName });

        // read output straight away otherwise process may hang
        String ogrinfoOutput = IOUtils.toString(procOgrinfo.getInputStream());

        // process should have already terminated at this point - just do
        // this to get the return code
        int ogrinfoReturnVal = procOgrinfo.waitFor();

        if (ogrinfoReturnVal != 0) {
            String ogrinfoErrorOutput = IOUtils.toString(procOgrinfo.getErrorStream());
            throw new RuntimeException("ogrinfo failed: " + ogrinfoErrorOutput);
        }

        // Use regular expression matching to pull the extent values from
        // the output for ogrinfo
        Pattern p = Pattern.compile("^Extent: \\((.+), (.+)\\) \\- \\((.+), (.+)\\)$", Pattern.MULTILINE);
        Matcher m = p.matcher(ogrinfoOutput);
        if (m.find()) {
            if (m.groupCount() == 4) {
                minLongitude = Double.parseDouble(m.group(1));
                minLatitude = Double.parseDouble(m.group(2));
                maxLongitude = Double.parseDouble(m.group(3));
                maxLatitude = Double.parseDouble(m.group(4));
            } else {
                throw new RuntimeException("error reading extents from ogrinfo: " + ogrinfoOutput);
            }
        } else {
            throw new RuntimeException("error reading extents from ogrinfo: " + ogrinfoOutput);
        }

        extents[0] = minLatitude;
        extents[1] = maxLatitude;
        extents[2] = minLongitude;
        extents[3] = maxLongitude;

        return extents;
    }

    /**
     * Use shp2pgsql to convert the shapefile to a database table.
     */
    private static void importShapeFile(int layerId, File shapeFile, Connection conn) throws Exception {
        // drop old table if exists
        System.out.println("Dropping old geometry table if present");
        conn.prepareStatement(String.format("DROP TABLE IF EXISTS \"%s\"", layerId)).execute();

        // use shp2pgsql to convert shape file into sql for insertion in the
        // database
        System.out.println("Converting shape file for insertion in database...");
        Process procShp2Pgsql = Runtime.getRuntime().exec(new String[] { "shp2pgsql", "-I", "-s", "4326", shapeFile.getAbsolutePath(), Integer.toString(layerId) });

        String shp2pgsqlOutput = IOUtils.toString(procShp2Pgsql.getInputStream());

        // shp2pgsql wraps the sql in BEGIN..COMMIT. Remove these, as we
        // want all our database operations to be part of the one
        // transaction
        shp2pgsqlOutput = shp2pgsqlOutput.replace("BEGIN;", "");
        shp2pgsqlOutput = shp2pgsqlOutput.replace("COMMIT;", "");

        // process should have already terminated at this point - just do
        // this to get the return code
        int shp2pgsqlReturnVal = procShp2Pgsql.waitFor();

        if (shp2pgsqlReturnVal != 0) {
            String shp2pgsqlErrorOutput = IOUtils.toString(procShp2Pgsql.getErrorStream());
            throw new RuntimeException("shp2pgsql failed: " + shp2pgsqlErrorOutput);
        }

        System.out.println("Writing shape file to database...");
        conn.prepareStatement(shp2pgsqlOutput).execute();
    }

    /**
     * Used to write a modified postgis table back to a shapefile.
     * 
     * @param shapeFile
     * @param layerId
     * @param dbHost
     * @param dbName
     * @param dbUsername
     * @param dbPassword
     * @throws Exception
     */
    private static void reExportShapeFile(File shapeFile, int layerId, String dbHost, String dbName, String dbUsername, String dbPassword) throws Exception {
        System.out.println("Using pgsql2shp to re-export shapefile that was modified using postgis");
        Process procPgsql2shp = Runtime.getRuntime().exec(
                new String[] { "pgsql2shp", "-f", shapeFile.getAbsolutePath(), "-h", dbHost, "-u", dbUsername, "-P", dbPassword, dbName, Integer.toString(layerId) });

        int pgsql2shpReturnVal = procPgsql2shp.waitFor();

        if (pgsql2shpReturnVal != 0) {
            String shp2pgsqlErrorOutput = IOUtils.toString(procPgsql2shp.getErrorStream());
            throw new RuntimeException("pgsql2shp failed: " + shp2pgsqlErrorOutput);
        }
    }

    private static void createDerivedDatabaseColumn(Connection conn, int layerId, String sourceColumnName, String derivedColumnName, Map<String, String> valueMap) throws Exception {

        DatabaseMetaData dbMetadata = conn.getMetaData();
        ResultSet columnTypeRs = dbMetadata.getColumns(null, null, Integer.toString(layerId), sourceColumnName);
        columnTypeRs.next();
        int sourceColumnType = columnTypeRs.getInt(5);

        // create a new column in the shape geometry table with the
        // layer name
        PreparedStatement alaNameColumnCreationStatement = conn.prepareStatement(String.format("ALTER TABLE \"%s\" ADD COLUMN %s text;", layerId, derivedColumnName));
        alaNameColumnCreationStatement.execute();

        for (String sourceColumnValue : valueMap.keySet()) {
            String derivedColumnValue = valueMap.get(sourceColumnValue);

            // write the layer name to the newly created column
            PreparedStatement alaNameInsertionStatement = conn.prepareStatement(String.format("UPDATE \"%s\" SET %s = ? where %s = ?;", layerId, derivedColumnName, sourceColumnName));

            alaNameInsertionStatement.setString(1, derivedColumnValue);
            alaNameInsertionStatement.setObject(2, sourceColumnValue, sourceColumnType);

            alaNameInsertionStatement.execute();
        }
    }

    private static List<DerivedColumnDefinition> readDerivedColumnsMappingFile(File derivedColumnsFile) throws Exception {
        ArrayList<DerivedColumnDefinition> columnDefList = new ArrayList<DerivedColumnDefinition>();

        String jsonString = FileUtils.readFileToString(derivedColumnsFile);

        Object parsedJson = new JSONParser().parse(jsonString);
        try {
            JSONArray array = (JSONArray) parsedJson;
            for (Object oColumnDef : array) {
                JSONObject columnDef = (JSONObject) oColumnDef;
                String sourceColumnName = (String) columnDef.get("sourceColumn");
                String derivedColumnName = (String) columnDef.get("derivedColumn");
                Map<String, String> valueMap = (Map<String, String>) columnDef.get("valueMap");

                columnDefList.add(new DerivedColumnDefinition(sourceColumnName, derivedColumnName, valueMap));
            }
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException("Invalid format for JSON in derived columns mapping file");
        }

        return columnDefList;
    }

    private static class DerivedColumnDefinition {
        private String _sourceColumnName;
        private String _derivedColumnName;
        private Map<String, String> _valueMap;

        public DerivedColumnDefinition(String sourceColumnName, String derivedColumnName, Map<String, String> valueMap) {
            _sourceColumnName = sourceColumnName;
            _derivedColumnName = derivedColumnName;
            _valueMap = valueMap;
        }

        public String getSourceColumnName() {
            return _sourceColumnName;
        }

        public String getDerivedColumnName() {
            return _derivedColumnName;
        }

        public Map<String, String> getValueMap() {
            return _valueMap;
        }
    }

}
