package au.org.ala.layers.ingestion;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates layer thumbnails, either the full set or for a given set of layers
 * 
 * Usage: 
 * 
 *   java ThumbnailGenerator [-f] [-e] [-v] [-o output path] [layer name 1]...[layer name N]
 *   
 *   -f: Generate the full set of thumbnails overwriting the existing set. 
 *   -e: Enabled layers only. Works only with the -f flag. 
 *   -v: Verbose output. 
 *   -o: output path. If none provided, then current directory is assumed 
 *   -dbJdbcUrl: JDBC url. If not supplied, defaults to "jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb";
 *   -dbUsername: database username, if not supplied, defaults to "postgres"
 *   -dbPassword: database password, if not supplied, defaults to "postgres"
 *   -geoserverLocation: base url for geoserver. If not supplied, defaults to "http://spatial-dev.ala.org.au/geoserver". Must
 *      not end with a '/';
 * 
 * Example Usage: 
 * 
 *   Generate thumbnails for all layers from db
 *     java ThumbnailGenerator -f -o /data/output/layerthumbs/ 
 * 
 *   Generate thumbnails for enabled layers from db
 *     java ThumbnailGenerator -f -e -o /data/output/layerthumbs/ 
 * 
 *   Generate thumbnails for specified layers 
 *     java ThumbnailGenerator -o /data/output/layerthumbs/ lyr1 lyr2 
 * 
 * @author ajay
 */
public class ThumbnailGenerator {
    
    private static void doFullDump(String dbJdbcUrl, String dbUsername, String dbPassword, String geoserverLocation, boolean enabledOnly, File outputDir) {
        try {
            Class.forName("org.postgresql.Driver");
            Properties props = new Properties();
            props.setProperty("user", dbUsername);
            props.setProperty("password", dbPassword);
            Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
            conn.setAutoCommit(false);
            
            String sql = "SELECT name FROM layers";
            if (enabledOnly) {
                sql = "SELECT name FROM layers WHERE enabled = true";
            }
            
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                generateThumbnail(geoserverLocation, rs.getString(1), outputDir);
            }

        } catch (Exception e) {
            System.err.println("Unable to generate a full set of the layer thumbnails");
            e.printStackTrace(System.err);
        }
    }
    
    private static void generateThumbnail(String geoserverLocation, String layerName, File outputDir) {
        try {
            
            System.out.println(" > " + layerName);
            String thumburl = geoserverLocation + "/wms/reflect?layers=ALA:"+layerName+"&width=200&height=200";
            
            URL url = new URL(thumburl);
            
            InputStream in = new BufferedInputStream(url.openStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n = 0;
            while (-1 != (n = in.read(buf))) {
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            
            FileOutputStream fos = new FileOutputStream(outputDir.getAbsoluteFile() + "/ALA:" + layerName + ".jpg");
            fos.write(out.toByteArray());
            fos.close();
        } catch (IOException ex) {
            Logger.getLogger(ThumbnailGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    private static void printUsage() {
        printUsage("");        
    }
    
    private static void printUsage(String message) {
        System.out.println(message);
        System.out.println("\nUsage: \n\tjava ThumbnailGenerator [-f] [-e] [-v] [-o output path] [layer name 1]...[layer name N]");
    }
    
    public static void main(String[] args) {
        
        String output_dir = System.getProperty("user.dir");
        boolean doFullSet = false;
        boolean doEnabledSet = false;
        boolean doVerbose = false;
        ArrayList<String> layers = new ArrayList<String>(); 
        
        String dbJdbcUrl = "jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb";
        String dbUsername = "postgres";
        String dbPassword = "postgres";
        String geoserverLocation = "http://spatial-dev.ala.org.au/geoserver";
        
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        for (int i=0; i<args.length; i++) {
            String s = args[i];
            if (s.equalsIgnoreCase("-o")) {
                output_dir = args[i+1];
                i++;
            } else if (s.equalsIgnoreCase("-dbJdbcUrl")) {
                dbJdbcUrl = args[i+1];
                i++;
            } else if (s.equalsIgnoreCase("-dbUsername")) {
                dbUsername = args[i+1];
                i++;
            } else if (s.equalsIgnoreCase("-dbPassword")) {
                dbPassword = args[i+1];
                i++;
            } else if (s.equalsIgnoreCase("-geoserverLocation")) {
                geoserverLocation = args[i+1];
                i++;
            } else if (s.equalsIgnoreCase("-f")) {
                doFullSet = true;
            } else if (s.equalsIgnoreCase("-e")) {
                doEnabledSet = true;
            } else if (s.equalsIgnoreCase("-v")) {
                doVerbose = true;
            } else {
                layers.add(s);
            }
        }
        
        if (doEnabledSet && !doFullSet) {
            printUsage("Enabled set (-e) flag only works with the Full set (-f) flag");
            System.exit(1);
        }
        
        File outputDir = new File(output_dir);
        System.out.println("Output dir: " + outputDir.getAbsolutePath() + ": " + ((outputDir.exists())? "exists":"will be created" ));
        outputDir.mkdirs(); 
        
        if (doFullSet) {
            if (!doEnabledSet) {
                System.out.println("* Generate thumbnails for all layers:");
            } else {
                System.out.println("* Generate thumbnails for enabled layers only:");
            }
            doFullDump(dbJdbcUrl, dbUsername, dbPassword, geoserverLocation, doEnabledSet, outputDir);
        } else if (!doFullSet && layers.size() > 0) {
            System.out.println("* Generating thumbails for the following layer(s):");
            Iterator<String> it = layers.iterator();
            while (it.hasNext()) {
                generateThumbnail(geoserverLocation, it.next(), outputDir);
            }
        } else {
            String message = "";
            message += "No layers provided.\n";
            message += "Please provide either a list of layers to generate the thumbnail for\n";
            message += "or the -f option to generate a full set";
            printUsage(message);
            System.exit(1);
        }
    }
}
