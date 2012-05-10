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
 * Used to create a layer in geoserver from a geotiff. This is used to load all
 * environmental layers into geoserver, as well as any Contextual layers
 * processed using the GridClassBuilder tool.
 * 
 * @author ChrisF
 * 
 */
public class GeotiffGeoserverLoader {
    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("layerName geotiffFile sldFile geoserverBaseUrl geoserverUsername geoserverPassword");
            System.exit(1); // Abnormal termination
        }

        String layerName = args[0];
        File geotiffFile = new File(args[1]);
        File sldFile = new File(args[2]);
        String geoserverBaseUrl = args[3];
        String geoserverUsername = args[4];
        String geoserverPassword = args[5];

        try {
            load(layerName, geotiffFile, sldFile, geoserverBaseUrl, geoserverUsername, geoserverPassword);
        } catch (Exception ex) {
            ex.printStackTrace();
            // Abnormal termination
            System.exit(1);
        }
    }

    public static void load(String layerName, File geotiffFile, File sldFile, String geoserverBaseUrl, String geoserverUsername, String geoserverPassword) throws Exception {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        httpClient.getCredentialsProvider().setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(geoserverUsername, geoserverPassword));

        // Create layer in geoserver
        System.out.println("Creating layer in geoserver...");
        HttpPut createLayerPut = new HttpPut(String.format(geoserverBaseUrl + "/rest/workspaces/ALA/coveragestores/%s/external.geotiff", layerName));
        createLayerPut.setHeader("Content-type", "text/plain");
        createLayerPut.setEntity(new StringEntity(geotiffFile.toURI().toURL().toString()));

        HttpResponse createLayerResponse = httpClient.execute(createLayerPut);

        if (createLayerResponse.getStatusLine().getStatusCode() != 201) {
            throw new RuntimeException("Error creating layer in geoserver: " + createLayerResponse.toString());
        }

        EntityUtils.consume(createLayerResponse.getEntity());

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
}
