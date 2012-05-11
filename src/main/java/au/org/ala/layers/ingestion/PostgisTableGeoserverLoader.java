package au.org.ala.layers.ingestion;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * Used to create a layer in geoserver from a postgis table, in turn created
 * from a shapefile. This tool is used for all contextual layers aside from
 * those processed using the GridClassBuilder.
 * 
 * @author ChrisF
 * 
 */
public class PostgisTableGeoserverLoader {

    public static final String RELATIVE_URL_FOR_LAYER_CREATION = "/rest/workspaces/ALA/datastores/LayersDB/featuretypes";

    public static final String TEMPLATE_RELATIVE_URL_FOR_BORDER_SETTING = "/rest/layers/ALA:%s";

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.out.println("Usage: geoserverBaseUrl geoserverUsername geoserverPassword layerId layerName layerDescription [sldFile]");
            System.exit(1);
        }

        String geoserverBaseUrl = args[0];
        String geoserverUsername = args[1];
        String geoserverPassword = args[2];
        int layerId = Integer.parseInt(args[3]);
        String layerName = args[4];
        String layerDescription = args[5];

        File sldFile = null;
        if (args.length >= 7) {
            sldFile = new File(args[6]);
        }

        try {
            boolean success = load(geoserverBaseUrl, geoserverUsername, geoserverPassword, layerId, layerName, layerDescription, sldFile);
            if (!success) {
                System.exit(1);
            } else {
                System.exit(0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static boolean load(String geoserverBaseUrl, String geoserverUsername, String geoserverPassword, int layerId, String layerName, String layerDescription, File sldFile) throws Exception {
        // Create layer in geoserver
        System.out.println("Creating layer in geoserver...");
        DefaultHttpClient httpClient = new DefaultHttpClient();
        httpClient.getCredentialsProvider().setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(geoserverUsername, geoserverPassword));
        HttpPost post = new HttpPost(geoserverBaseUrl + RELATIVE_URL_FOR_LAYER_CREATION);
        post.setHeader("Content-type", "text/xml");
        post.setEntity(new StringEntity(String.format("<featureType><name>%s</name><nativeName>%s</nativeName><title>%s</title></featureType>", layerName, layerId, layerDescription)));

        HttpResponse response = httpClient.execute(post);

        if (response.getStatusLine().getStatusCode() != 201) {
            throw new RuntimeException("Error creating layer in geoserver: " + response.toString());
        }

        EntityUtils.consume(response.getEntity());

        // If no sld style file supplied, set geoserver layer as having a generic border
        if (sldFile == null) {
            HttpPut put = new HttpPut(String.format(geoserverBaseUrl + TEMPLATE_RELATIVE_URL_FOR_BORDER_SETTING, layerName));
            put.setHeader("Content-type", "text/xml");
            put.setEntity(new StringEntity("<layer><defaultStyle><name>generic_border</name></defaultStyle><enabled>true</enabled></layer>"));

            HttpResponse response2 = httpClient.execute(put);

            if (response2.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Error setting layer border in geoserver: " + response2.toString());
            }

            EntityUtils.consume(response2.getEntity());
        } else {
            // create style in geoserver (refer to SLD)
            System.out.println("Creating style in geoserver");
            HttpPost createStylePost = new HttpPost(geoserverBaseUrl + "/rest/styles");
            createStylePost.setHeader("Content-type", "text/xml");
            createStylePost.setEntity(new StringEntity(String.format("<style><name>%s_style</name><filename>%s.sld</filename></style>", layerName, layerName)));

            HttpResponse createStyleResponse = httpClient.execute(createStylePost);

            if (createStyleResponse.getStatusLine().getStatusCode() != 201) {
                throw new RuntimeException("Error creating style in geoserver: " + createStyleResponse.toString());
            }

            EntityUtils.consume(createStyleResponse.getEntity());

            // upload sld
            System.out.println("Uploading sld file to geoserver");
            String sldData = FileUtils.readFileToString(sldFile);

            HttpPut uploadSldPut = new HttpPut(String.format(geoserverBaseUrl + "/rest/styles/%s_style", layerName));
            uploadSldPut.setHeader("Content-type", "application/vnd.ogc.sld+xml");
            uploadSldPut.setEntity(new StringEntity(sldData));

            HttpResponse uploadSldResponse = httpClient.execute(uploadSldPut);

            if (uploadSldResponse.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Error uploading sld file geoserver: " + uploadSldResponse.toString());
            }

            EntityUtils.consume(uploadSldResponse.getEntity());

            // set default style in geoserver
            System.out.println("Setting default style in geoserver");
            HttpPut setDefaultStylePut = new HttpPut(String.format(geoserverBaseUrl + "/rest/layers/ALA:%s", layerName));
            setDefaultStylePut.setHeader("Content-type", "text/xml");
            setDefaultStylePut.setEntity(new StringEntity(String.format("<layer><enabled>true</enabled><defaultStyle><name>%s_style</name></defaultStyle></layer>", layerName)));

            HttpResponse setDefaultStyleResponse = httpClient.execute(setDefaultStylePut);

            if (setDefaultStyleResponse.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Setting default style in geoserver: " + setDefaultStyleResponse.toString());
            }

            EntityUtils.consume(setDefaultStyleResponse.getEntity());
        }

        return true;
    }

}
