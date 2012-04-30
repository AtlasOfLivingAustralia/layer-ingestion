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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class ContextualImport {
    
    private static final String ALA_NAME_COLUMN_NAME = "ala_name";
    private static final String GEOSERVER_QUERY_TEMPLATE = "<COMMON_GEOSERVER_URL>/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:{0}&format=image/png&styles=";

    // NOTE - layerName should match name of shape file (without the .shp on the
    // end)
    public static void main(String[] args) throws Exception {
        if (args.length < 11) {
            System.out.println("USAGE: ContextualImport shapeFilePath reprojectedShapeFileDestination dbJdbcUrl dbUsername dbPassword geoserverUsername geoserverPassword layerName layerDescription fieldsSid fieldsSname [fieldsSdesc]");
            return;
        }
        
        System.out.println("Beginning contextual load");

        String shapeFilePath = args[0];
        String reprojectedShapeFileDestination = args[1];
        String dbJdbcUrl = args[2];
        String dbUsername = args[3];
        String dbPassword = args[4];
        String geoserverUsername = args[5];
        String geoserverPassword = args[6];
        String layerName = args[7];
        String layerDescription = args[8];
        String fieldsSid = args[9];
        String fieldsSname = args[10];

        String fieldsSdesc = null;
        if (args.length >= 12) {
            fieldsSdesc = args[11];
        }

        System.out.println("Checking supplied paths for shape file and destination directory");
        File shapeFile = new File(shapeFilePath);
        if (!shapeFile.exists() || !shapeFile.isFile()) {
            throw new RuntimeException("Shape file " + shapeFilePath + " does not exist or is a directory");
        }

        File reprojectedShapeFileDestinationDir = new File(reprojectedShapeFileDestination);
        if (!reprojectedShapeFileDestinationDir.exists() || !reprojectedShapeFileDestinationDir.isDirectory()) {
            throw new RuntimeException("Directory " + reprojectedShapeFileDestinationDir + " does not exist or is not a directory");
        }

        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
        conn.setAutoCommit(false);

        try {
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

            // use ogr2ogr to reproject the shape file to WGS 84
            System.out.println("Reprojecting to WGS 84...");
            File reprojectedShapeFile = new File(reprojectedShapeFileDestinationDir, shapeFile.getName());

            Process procOgr2Ogr = Runtime.getRuntime().exec(new String[] {"ogr2ogr", "-t_srs", "EPSG:4326", reprojectedShapeFile.getAbsolutePath(), shapeFile.getAbsolutePath()});
            int ogr2ogrReturnVal = procOgr2Ogr.waitFor();
            if (ogr2ogrReturnVal != 0) {
                String ogr2ogrErrorOutput = IOUtils.toString(procOgr2Ogr.getErrorStream());
                throw new RuntimeException("ogr2ogr failed: " + ogr2ogrErrorOutput);
            }

            // use ogrinfo to get extents
            System.out.println("Getting extents...");
            double minLatitude;
            double maxLatitude;
            double minLongitude;
            double maxLongitude;
            Process procOgrinfo = Runtime.getRuntime().exec(new String[] {"ogrinfo", "-so", reprojectedShapeFile.getAbsolutePath(), layerName});

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
            
            //drop old table if exists
            System.out.println("Dropping old geometry table if present");
            conn.prepareStatement(String.format("DROP TABLE IF EXISTS \"%s\"", id)).execute();
            
            // use shp2pgsql to convert shape file into sql for insertion in the database
            System.out.println("Converting shape file for insertion in database...");
            Process procShp2Pgsql = Runtime.getRuntime().exec(new String[] {"shp2pgsql", "-I", "-s", "4326", shapeFile.getAbsolutePath(), Integer.toString(id)});

            String shp2pgsqlOutput = IOUtils.toString(procShp2Pgsql.getInputStream());
            
            // shp2pgsql wraps the sql in BEGIN..COMMIT. Remove these, as we want all our database operations to be part of the one
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
            // examine number of rows in new table for shape. If there is only one row (single polygon), then ignore the passed in fieldsSid, fieldsSname and fieldsSdec
            // parameters and use the layer description for the fieldsSid/fieldsSname/object name
            rs = st.executeQuery(String.format("SELECT COUNT(*) from \"%s\"", id));
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
                    
                    // create a new column in the shape geometry table with the layer name
                    PreparedStatement alaNameColumnCreationStatement = conn.prepareStatement(String.format("ALTER TABLE \"%s\" ADD COLUMN %s text;", id, ALA_NAME_COLUMN_NAME));
                    alaNameColumnCreationStatement.execute();
                    
                    // write the layer name to the newly created column
                    PreparedStatement alaNameInsertionStatement = conn.prepareStatement(String.format("UPDATE \"%s\" SET %s = '%s' where true;", id, ALA_NAME_COLUMN_NAME, layerDescription));
                    alaNameInsertionStatement.execute();
                    
                    // need to run pgsql2shp to re-export the shape file here. The data in the database table and the shapefile
                    //need to match each other
                    //TODO run pgsqlshp here.
                }
            }
            

            // insert to layers table
            String displayPath = MessageFormat.format(GEOSERVER_QUERY_TEMPLATE, layerName);
            System.out.println("Creating layers table entry...");
            PreparedStatement createLayersStatement = createLayersInsert(conn, id, layerDescription, reprojectedShapeFileDestinationDir.getAbsolutePath(), layerName, displayPath, minLatitude, minLongitude, maxLatitude, maxLongitude);
            createLayersStatement.execute();

            // insert to fields table
            System.out.println("Creating fields table entry...");
            PreparedStatement createFieldsStatement = createFieldsInsert(conn, id, layerName, layerDescription, fieldsSid, fieldsSname, fieldsSdesc);
            createFieldsStatement.execute();

            // insert to objects table
            System.out.println("Creating objects table entries...");
            PreparedStatement createObjectsStatement = createObjectsInsert(conn, id, fieldsSid, fieldsSname, fieldsSdesc);
            createObjectsStatement.execute();

            // generate object names
            System.out.println("Generating object names...");
            PreparedStatement createObjectNamesStatement = createObjectNameGenerationStatement(conn);
            createObjectNamesStatement.execute();

            // generate object bboxes and areas
            System.out.println("Generating object bounding boxes and areas...");
            PreparedStatement createBBoxesAndAreaStatement = createGenerateObjectsBBoxAndAreaStatement(conn);
            createBBoxesAndAreaStatement.execute();
            
            // database updates complete so commit the transaction
            conn.commit();
            
            // Create layer in geoserver
            System.out.println("Creating layer in geoserver...");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            httpClient.getCredentialsProvider().setCredentials(new AuthScope("localhost", 8082), new UsernamePasswordCredentials(geoserverUsername, geoserverPassword));
            HttpPost post = new HttpPost("http://localhost:8082/geoserver/rest/workspaces/ALA/datastores/LayersDB/featuretypes");
            post.setHeader("Content-type", "text/xml");
            post.setEntity(new StringEntity(String.format("<featureType><name>%s</name><nativeName>%s</nativeName><title>%s</title></featureType>", layerName, id, layerDescription)));
            
            HttpResponse response = httpClient.execute(post);
            
            if (response.getStatusLine().getStatusCode() != 201) {
                throw new RuntimeException("Error creating layer in geoserver: " + response.toString());
            }
            
            EntityUtils.consume(response.getEntity());
            
            // Set geoserver layer as having a generic border
            HttpPut put = new HttpPut(String.format("http://localhost:8082/geoserver/rest/layers/ALA:%s", layerName));
            put.setHeader("Content-type", "text/xml");
            put.setEntity(new StringEntity("<layer><defaultStyle><name>generic_border</name></defaultStyle><enabled>true</enabled></layer>"));
            
            HttpResponse response2 = httpClient.execute(put);
            
            if (response2.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Error setting layer border in geoserver: " + response2.toString());
            }
            
            EntityUtils.consume(response2.getEntity());
            
            //TODO set extents in geoserver (is this possible with the REST api?)
            //TODO geoserver call to generate GWC tiles.

            //Run the object stats generator tool to do further operations on bboxes and areas
            //System.out.println("Running ObjectStatsGenerator tool...");
            //ObjectsStatsGenerator.main(new String[] { "10", dbJdbcUrl, dbUsername, dbPassword });

        } catch (Exception ex) {
            ex.printStackTrace();
            conn.rollback();
        }
        
        
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

    private static PreparedStatement createObjectsInsert(Connection conn, int layerId, String fieldsSid, String fieldsSname, String fieldsSdesc) throws SQLException {
        // Unfortunately table and column names can't be substituted with PreparedStatements, so we have to hardcode them
        PreparedStatement stLayersInsert = conn.prepareStatement(MessageFormat.format("INSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch)"
                + " SELECT nextval(''objects_id_seq''::regclass), {0}, MAX({1}), MAX({2}), ''{3}'', ST_UNION(the_geom), TRUE FROM \"{4}\" GROUP BY {5}",
                fieldsSid, fieldsSname, fieldsSdesc == null ? "NULL" : fieldsSdesc, "cl" + Integer.toString(layerId), layerId, fieldsSid));
        return stLayersInsert;
    }

    private static PreparedStatement createObjectNameGenerationStatement(Connection conn) throws SQLException {
        PreparedStatement objectNameGenerationStatement = conn.prepareStatement("INSERT INTO obj_names (name)"
                + "  SELECT lower(objects.name) FROM fields, objects"
                + "  LEFT OUTER JOIN obj_names ON lower(objects.name)=obj_names.name"
                + "  WHERE obj_names.name IS NULL" + "  AND fields.namesearch = true"
                + " AND fields.id = objects.fid"
                + " GROUP BY lower(objects.name);"
                + "  UPDATE objects SET name_id=obj_names.id FROM obj_names WHERE name_id IS NULL AND lower(objects.name)=obj_names.name;");

        return objectNameGenerationStatement;
    }

    private static PreparedStatement createGenerateObjectsBBoxAndAreaStatement(Connection conn) throws SQLException {
        PreparedStatement generateBBoxAndAreaStatement = conn.prepareStatement("update objects set bbox = ST_AsText(Box2D(the_geom)) where bbox is null; "
                + "update objects set area_km=0 where st_geometrytype(the_geom) = 'ST_Point';");

        return generateBBoxAndAreaStatement;
    }

}
