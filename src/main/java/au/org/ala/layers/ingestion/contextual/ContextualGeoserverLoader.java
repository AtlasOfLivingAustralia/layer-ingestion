package au.org.ala.layers.ingestion.contextual;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class ContextualGeoserverLoader {
    
    public static final String RELATIVE_URL_FOR_LAYER_CREATION = "/rest/workspaces/ALA/datastores/LayersDB/featuretypes";
    
    public static final String TEMPLATE_RELATIVE_URL_FOR_BORDER_SETTING = "/rest/layers/ALA:%s";

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
    }
    
    public static void load(String geoserverBaseUrl, String geoserverUsername, String geoserverPassword, int layerId, String layerName, String layerDescription) throws Exception {
        // Create layer in geoserver
        System.out.println("Creating layer in geoserver...");
        DefaultHttpClient httpClient = new DefaultHttpClient();
        //httpClient.getCredentialsProvider().setCredentials(new AuthScope("localhost", 8082), new UsernamePasswordCredentials(geoserverUsername, geoserverPassword));
        httpClient.getCredentialsProvider().setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(geoserverUsername, geoserverPassword));
        HttpPost post = new HttpPost(geoserverBaseUrl + RELATIVE_URL_FOR_LAYER_CREATION);
        post.setHeader("Content-type", "text/xml");
        post.setEntity(new StringEntity(String.format("<featureType><name>%s</name><nativeName>%s</nativeName><title>%s</title></featureType>", layerName, layerId, layerDescription)));
        
        HttpResponse response = httpClient.execute(post);
        
        if (response.getStatusLine().getStatusCode() != 201) {
            throw new RuntimeException("Error creating layer in geoserver: " + response.toString());
        }
        
        EntityUtils.consume(response.getEntity());
        
        // Set geoserver layer as having a generic border
        HttpPut put = new HttpPut(String.format(geoserverBaseUrl + TEMPLATE_RELATIVE_URL_FOR_BORDER_SETTING, layerName));
        put.setHeader("Content-type", "text/xml");
        put.setEntity(new StringEntity("<layer><defaultStyle><name>generic_border</name></defaultStyle><enabled>true</enabled></layer>"));
        
        HttpResponse response2 = httpClient.execute(put);
        
        if (response2.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Error setting layer border in geoserver: " + response2.toString());
        }
        
        EntityUtils.consume(response2.getEntity());
    }

}
