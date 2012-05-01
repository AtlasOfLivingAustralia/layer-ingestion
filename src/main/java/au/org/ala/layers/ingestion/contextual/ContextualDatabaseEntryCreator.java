package au.org.ala.layers.ingestion.contextual;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

public class ContextualDatabaseEntryCreator {

    private static final String ALA_NAME_COLUMN_NAME = "ala_name";
    private static final String GEOSERVER_QUERY_TEMPLATE = "<COMMON_GEOSERVER_URL>/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:{0}&format=image/png&styles=";

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 12) {
            System.out.println("Usage: layerId layerName layerDescription fieldsSid fieldsSname fieldsSdesc shapeFile dbUsername dbPassword dbJdbcUrl dbHost dbName");
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

        try {
            boolean success = create(layerId, layerName, layerDescription, fieldsSid, fieldsSname, fieldsSdesc, shapeFile, dbUsername, dbPassword, dbJdbcUrl, dbHost, dbName);
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
            String dbPassword, String dbJdbcUrl, String dbHost, String dbName) throws Exception {
        
        if (!shapeFile.exists()) {
            throw new IllegalArgumentException("File " + shapeFile.getAbsolutePath() + " does not exist");
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
            // use ogrinfo to determine layer extents
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

            System.out.println("Checking row count of shape geometry table");
            // examine number of rows in new table for shape. If there is only
            // one row (single polygon), then ignore the passed in fieldsSid,
            // fieldsSname and fieldsSdec
            // parameters and use the layer description for the
            // fieldsSid/fieldsSname/object name
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

                    // create a new column in the shape geometry table with the
                    // layer name
                    PreparedStatement alaNameColumnCreationStatement = conn.prepareStatement(String.format("ALTER TABLE \"%s\" ADD COLUMN %s text;", layerId, ALA_NAME_COLUMN_NAME));
                    alaNameColumnCreationStatement.execute();

                    // write the layer name to the newly created column
                    PreparedStatement alaNameInsertionStatement = conn.prepareStatement(String.format("UPDATE \"%s\" SET %s = '%s' where true;", layerId, ALA_NAME_COLUMN_NAME, layerDescription));
                    alaNameInsertionStatement.execute();

                    // need to run pgsql2shp to re-export the shape file here.
                    // The data in the database table and the shapefile
                    // need to match each other
                    // TODO run pgsql2shp here.
                    System.out.println("Using pgsql2shp to re-export shapefile that was modified using postgis");
                    Process procPgsql2shp = Runtime.getRuntime().exec(
                            new String[] { "pgsql2shp", "-f", shapeFile.getAbsolutePath(), "-h", dbHost, "-u", dbUsername, "-P", dbPassword, dbName, Integer.toString(layerId) });

                    int pgsql2shpReturnVal = procPgsql2shp.waitFor();

                    if (pgsql2shpReturnVal != 0) {
                        String shp2pgsqlErrorOutput = IOUtils.toString(procShp2Pgsql.getErrorStream());
                        throw new RuntimeException("pgsql2shp failed: " + shp2pgsqlErrorOutput);
                    }
                }
            }

            // insert to layers table
            String displayPath = MessageFormat.format(GEOSERVER_QUERY_TEMPLATE, layerName);
            System.out.println("Creating layers table entry...");
            PreparedStatement createLayersStatement = createLayersInsert(conn, layerId, layerDescription, shapeFile.getParentFile().getAbsolutePath(), layerName, displayPath, minLatitude,
                    minLongitude, maxLatitude, maxLongitude);
            createLayersStatement.execute();

            // insert to fields table
            System.out.println("Creating fields table entry...");
            PreparedStatement createFieldsStatement = createFieldsInsert(conn, layerId, layerName, layerDescription, fieldsSid, fieldsSname, fieldsSdesc);
            createFieldsStatement.execute();

            conn.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            conn.rollback();
            return false;
        }

        return true;
    }

    private static PreparedStatement createLayersInsert(Connection conn, int layerId, String description, String path, String name, String displayPath, double minLatitude, double minLongitude,
            double maxLatitude, double maxLongitude) throws SQLException {
        PreparedStatement stLayersInsert = conn
                .prepareStatement("INSERT INTO layers (id, name, description, type, path, displayPath, minlatitude, minlongitude, maxlatitude, maxlongitude, enabled, displayname, uid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stLayersInsert.setInt(1, layerId);
        stLayersInsert.setString(2, name);
        stLayersInsert.setString(3, description);
        stLayersInsert.setString(4, "Contextual");
        stLayersInsert.setString(5, path);
        stLayersInsert.setString(6, displayPath);
        stLayersInsert.setDouble(7, minLatitude);
        stLayersInsert.setDouble(8, minLongitude);
        stLayersInsert.setDouble(9, maxLatitude);
        stLayersInsert.setDouble(10, maxLongitude);
        stLayersInsert.setBoolean(11, true);
        stLayersInsert.setString(12, description);
        stLayersInsert.setString(13, Integer.toString(layerId));
        return stLayersInsert;
    }

    private static PreparedStatement createFieldsInsert(Connection conn, int layerId, String name, String description, String sid, String sname, String sdesc) throws SQLException {
        // TOOD slightly different statement if sdesc is null...

        PreparedStatement stFieldsInsert = conn
                .prepareStatement("INSERT INTO fields (name, id, \"desc\", type, spid, sid, sname, sdesc, indb, enabled, last_update, namesearch, defaultlayer, \"intersect\", layerbranch)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stFieldsInsert.setString(1, name);
        stFieldsInsert.setString(2, "cl" + Integer.toString(layerId));
        stFieldsInsert.setString(3, description);
        stFieldsInsert.setString(4, "c");
        stFieldsInsert.setString(5, Integer.toString(layerId));
        stFieldsInsert.setString(6, sid);
        stFieldsInsert.setString(7, sname);

        if (sdesc == null) {
            stFieldsInsert.setNull(8, Types.VARCHAR);
        } else {
            stFieldsInsert.setString(8, sdesc);
        }

        stFieldsInsert.setBoolean(9, true);
        stFieldsInsert.setBoolean(10, true);
        stFieldsInsert.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
        stFieldsInsert.setBoolean(12, true);
        stFieldsInsert.setBoolean(13, false);
        stFieldsInsert.setBoolean(14, false);
        stFieldsInsert.setBoolean(15, false);

        return stFieldsInsert;
    }

}
