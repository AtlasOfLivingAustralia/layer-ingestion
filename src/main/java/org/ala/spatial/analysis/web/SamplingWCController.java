package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.MapComposer;
import au.org.emii.portal.composer.UtilityComposer;
import au.org.emii.portal.settings.SettingsSupplementary;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.ala.spatial.util.CommonData;
import org.ala.spatial.util.LayersUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.zkoss.zhtml.Messagebox;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zhtml.Filedownload;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Popup;
import org.zkoss.zul.Row;
import org.zkoss.zul.Tabbox;

/**
 *
 * @author ajay
 */
public class SamplingWCController extends UtilityComposer {
    private SpeciesAutoComplete sac;
    Tabbox tabboxsampling;
   // private Listbox lbenvlayers;
    private Popup p;
    private Html h;
    private List layers;
    //private Checkbox useArea;
    private Map layerdata;
    private String selectedLayer;
    private MapComposer mc;
    private String satServer = "";
    private SettingsSupplementary settingsSupplementary = null;
    private String user_polygon = "";
    private String[] groupLabels = null;
    //String previousArea = "";
    LayersUtil layersUtil;
    private String pid;
    private String species;
    private String taxon; 

    EnvironmentalList lbListLayers;

    @Override
    public void afterCompose() {
        super.afterCompose();

        try{
            mc = getThisMapComposer();
            if (settingsSupplementary != null) {
                satServer = settingsSupplementary.getValue(CommonData.SAT_URL);
            }

            layersUtil = new LayersUtil(mc, satServer);

            layers = new Vector();
            layerdata = new Hashtable<String, String[]>();

            lbListLayers.init(mc, satServer, false);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void onClick$btnClearSelection(Event event){
        lbListLayers.clearSelection();
    }

    private void showLayerExtents(Object o) {
        Listcell lc = (Listcell) o;

        selectedLayer = (String) lc.getLabel();
        selectedLayer = selectedLayer.trim();
        String slist = "";
        try {
            selectedLayer = selectedLayer.replaceAll(" ", "_");
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(satServer + "/alaspatial/ws/spatial/settings/layer/" + selectedLayer + "/extents"); // testurl
            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            slist = get.getResponseBodyAsString();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        h.setContent(slist);

        p.open(lc);
    }

    private void showLayerExtentsLabel(Object o) {
        Label l = (Label) o;

        selectedLayer = (String) l.getValue();
        selectedLayer = selectedLayer.trim();
        String slist = "";
        try {
            selectedLayer = selectedLayer.replaceAll(" ", "_");
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(satServer + "/alaspatial/ws/spatial/settings/layer/" + selectedLayer + "/extents");
            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            slist = get.getResponseBodyAsString();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        h.setContent(slist);

        p.open(l);
    }

    public void onChange$sac(Event event) {
        loadSpeciesOnMap();
    }

    public void onClick$btnDownloadMetadata(Event event) {
        try {

            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(satServer + "/alaspatial/ws/spatial/settings/layer/" + selectedLayer + "/extents"); // testurl
            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            String slist = get.getResponseBodyAsString();


            Filedownload.save(slist, "text/plain", selectedLayer + "_metadata.txt");
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private boolean isEnvironmental(String layername) {
        layername = layername.trim();
        String[] layernames = CommonData.getEnvironmentalLayers();
        if (layernames != null) {
            for (int i = 0; i < layernames.length; i++) {
                if (layernames[i].trim().equals(layername)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isContextual(String layername) {
        layername = layername.trim();
        String[] layernames = CommonData.getContextualLayers();
        if (layernames != null) {
            for (int i = 0; i < layernames.length; i++) {
                if (layernames[i].trim().equals(layername)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onDoInit(Event event) throws Exception {
        runsampling();
    }

    public void produce() {
        onClick$btnPreview(null);
    }

    public void onClick$btnPreview(Event event) {
        try{onDoInit(null);}catch(Exception e){}
    }

    /**
     * Iterate thru' the layer lifst setup in the @doAfterCompose method
     * and setup the listbox
     */
    private String[] setupEnvironmentalLayers() {
        String[] aslist = null;
        try {
            aslist = layersUtil.getEnvironmentalLayers();


            if (aslist.length > 0) {
                layers.addAll(Arrays.asList(aslist));
                layerdata.put("Environmental", aslist);
            }

        } catch (Exception e) {
            System.out.println("error setting up env list");
            e.printStackTrace(System.out);
        }

        return aslist;
    }

    /**
     * Iterate thru' the layer list setup in the @doAfterCompose method
     * and setup the listbox
     */
    private String[] setupContextualLayers() {
        String[] aslist = null;
        try {
            aslist = layersUtil.getContextualLayers();

            if (aslist.length > 0) {
                layers.addAll(Arrays.asList(aslist));
                layerdata.put("Contextual", aslist);

                for (int k = 0; k < aslist.length; k++) {
                    System.out.println(aslist[k] + ", ");
                }
            }

        } catch (Exception e) {
            System.out.println("error setting up env list");
            e.printStackTrace(System.out);
        }

        return aslist;
    }

    public void runsampling() {


        try {

            String taxon = cleanTaxon();
            if (taxon == null || taxon.equals("")) {
                Messagebox.show("Please select a species in step 1.", "ALA Spatial Toolkit", Messagebox.OK, Messagebox.EXCLAMATION);
                //highlight step 1                
                tabboxsampling.setSelectedIndex(0);
                return;
            }

            SamplingResultsWCController window = (SamplingResultsWCController) Executions.createComponents("WEB-INF/zul/AnalysisSamplingResults.zul", this, null);
            window.parent = this;

            StringBuffer sbenvsel = new StringBuffer();

            String [] selectedLayers = lbListLayers.getSelectedLayers();
            if (selectedLayers.length > 0) {
                int i = 0;
                for(i=0;i<selectedLayers.length;i++){
                    if (selectedLayers[i] == null) {
                        // seems to be null, shouldn't be, but let's ignore it
                        continue;
                    }
                    sbenvsel.append(selectedLayers[i]);
                    if (i < selectedLayers.length-1) {
                        sbenvsel.append(":");
                    }
                }
                if (i == 0) {
                    sbenvsel.append("none");
                }
            } else {
                sbenvsel.append("none");
            }

            StringBuffer sbProcessUrl = new StringBuffer();
            sbProcessUrl.append(satServer + "/alaspatial/ws/sampling/process/preview?");
            sbProcessUrl.append("taxonid=" + URLEncoder.encode(taxon.replace(".", "__"), "UTF-8"));
            sbProcessUrl.append("&envlist=" + URLEncoder.encode(sbenvsel.toString(), "UTF-8"));
            if (true) { //an area always exists; useArea.isChecked()) {
                user_polygon = mc.getSelectionArea();
            } else {
                user_polygon = "";
            }
            System.out.println("user_polygon: " + user_polygon);
            String area = "none";
            if (user_polygon.length() > 0) {
                //sbProcessUrl.append("&area=" + URLEncoder.encode(user_polygon, "UTF-8"));
                area = user_polygon;
            } else {
                //sbProcessUrl.append("&area=" + URLEncoder.encode("none", "UTF-8"));
            }

            //String testurl = satServer + "/alaspatial/ws/sampling/test";

            HttpClient client = new HttpClient();
//            GetMethod get = new GetMethod(sbProcessUrl.toString()); // testurl
            PostMethod get = new PostMethod(sbProcessUrl.toString());
            get.addParameter("area", area);
            //get.addRequestHeader("Accept", "application/json, text/javascript, */*");
            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            String slist = get.getResponseBodyAsString();

            System.out.println("Got response from SamplingWSController: " + result + "\n" + slist);

            //error condition, for example, when no combobox item is selected
            if (result != 200) {
                mc.showMessage("no records available");
                window.detach();
                return;
            }

            String[] aslist = slist.split(";");
            System.out.println("Result count: " + aslist.length);
            int count = 0;
            for (int i = 0; i < aslist.length; i++) {
                String[] rec = aslist[i].split("~");
                if (rec.length > 0) {
                    count++;
                }
            }
            count--; //don't include header in count

            if (slist.trim().length() == 0 || count == 0) {
                mc.showMessage("No records available for selected criteria.");

                window.detach();
                return;
            }

            //don't count header

            window.doModal();

            if (count == 1) {
                window.samplingresultslabel.setValue("preview: 1 record");
            } else {
                window.samplingresultslabel.setValue("preview: " + count + " records");
            }


            // load into the results popup
            int j;

            /* map of top row, contextual columns data lists for value lookups */
            Map contextualLists = new Hashtable<Integer, String[]>();

            // add rows
            String[] top_row = null;
            for (int i = 0; i < aslist.length; i++) {
                if (i == 0) {
                    top_row = aslist[i].split("~");

                    for (int k = 0; k < top_row.length; k++) {
                        if (isContextual(top_row[k])) {
                            try {
                                String layername = top_row[k].trim().replaceAll(" ", "_");
                                client = new HttpClient();
                                GetMethod getmethod = new GetMethod(satServer + "/alaspatial/ws/spatial/settings/layer/" + layername + "/extents"); // testurl
                                getmethod.addRequestHeader("Accept", "text/plain");

                                result = client.executeMethod(getmethod);
                                String[] salist = getmethod.getResponseBodyAsString().split("<br>");
                                contextualLists.put(new Integer(k), salist);

                                System.out.println("# records=" + salist.length);
                            } catch (Exception e) {
                                e.printStackTrace(System.out);
                            }
                        }
                    }
                }
                String[] rec = aslist[i].split("~");

                System.out.println("Column Count: " + rec.length);
                //System.out.println()

                Row r = new Row();
                r.setParent(window.results_rows);
                // set the value
                for (int k = 0; k < rec.length && k < top_row.length; k++) {
                    Label label = new Label(rec[k]);
                    label.setParent(r);

                    /* onclick event for popup content update */
                    boolean iscontextual = isContextual(top_row[k]);
                    boolean isenvironmental = isEnvironmental(top_row[k]);

                    if (iscontextual || isenvironmental) {
                        if (i == 0) {
                            label.addEventListener("onClick", new EventListener() {

                                public void onEvent(Event event) throws Exception {
                                    showLayerExtentsLabel(event.getTarget());
                                }
                            });
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception calling sampling.preview:");
            e.printStackTrace(System.out);
        }
    }

    public void downloadWithProgressBar(Event event) {
        try {

            String taxon = cleanTaxon();

            StringBuffer sbenvsel = new StringBuffer();

             String [] selectedLayers = lbListLayers.getSelectedLayers();
            if (selectedLayers.length > 0) {
                int i = 0;
                for(i=0;i<selectedLayers.length;i++){
                    if (selectedLayers[i] == null) {
                        // seems to be null, shouldn't be, but let's ignore it
                        continue;
                    }/* else if (ArrayUtils.contains(groupLabels, selectedLayers[i])) {
                        // this must be the group header, let's ignore it
                        continue;
                    }*/

                    sbenvsel.append(selectedLayers[i]);
                    if (i < selectedLayers.length-1) {
                        sbenvsel.append(":");
                    }
                }
                if (i == 0) {
                    sbenvsel.append("none");
                }
            } else {
                sbenvsel.append("none");
            }

            StringBuffer sbProcessUrl = new StringBuffer();
            sbProcessUrl.append(satServer + "/alaspatial/ws/sampling/processq/download?");
            sbProcessUrl.append("taxonid=" + URLEncoder.encode(taxon, "UTF-8"));
            sbProcessUrl.append("&envlist=" + URLEncoder.encode(sbenvsel.toString(), "UTF-8"));
            if (true) { //an area always exists; useArea.isChecked()) {
                user_polygon = mc.getSelectionArea();
            } else {
                user_polygon = "";
            }
            System.out.println("user_polygon: " + user_polygon);
            String area;
            if (user_polygon.length() > 0) {
                //sbProcessUrl.append("&area=" + URLEncoder.encode(user_polygon, "UTF-8"));
                area = user_polygon;
            } else {
                //sbProcessUrl.append("&area=" + URLEncoder.encode("none", "UTF-8"));
                area = "none";
            }

            //String testurl = satServer + "/alaspatial/ws/sampling/test";

            HttpClient client = new HttpClient();
            //GetMethod get = new GetMethod(sbProcessUrl.toString()); // testurl
            PostMethod get = new PostMethod(sbProcessUrl.toString());
            get.addParameter("area", area);

            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            pid = get.getResponseBodyAsString();

            System.out.println("Got response from SamplingWSController: \n" + pid);

            SamplingProgressWCController window = (SamplingProgressWCController) Executions.createComponents("WEB-INF/zul/AnalysisSamplingProgress.zul", this, null);
            window.parent = this;
            window.start(pid);
            window.doModal();
            mc.updateUserLogAnalysis("Sampling", "Species: " + taxon + ";area: " + area, sbenvsel.toString(), sbProcessUrl.toString(), pid, "Sampling starting file download for pid: " + pid);
        } catch (Exception e) {
            System.out.println("Exception calling sampling.download:");
            e.printStackTrace(System.out);
        }
    }

    public void download(Event event) {
        try {

            String taxon = cleanTaxon();

            StringBuffer sbenvsel = new StringBuffer();

             String [] selectedLayers = lbListLayers.getSelectedLayers();
            if (selectedLayers.length > 0) {
                int i = 0;
                for(i=0;i<selectedLayers.length;i++){
                    if (selectedLayers[i] == null) {
                        // seems to be null, shouldn't be, but let's ignore it
                        continue;
                    }/* else if (ArrayUtils.contains(groupLabels, selectedLayers[i])) {
                        // this must be the group header, let's ignore it
                        continue;
                    }*/

                    sbenvsel.append(selectedLayers[i]);
                    if (i < selectedLayers.length-1) {
                        sbenvsel.append(":");
                    }
                }
                if (i == 0) {
                    sbenvsel.append("none");
                }
            } else {
                sbenvsel.append("none");
            }

            StringBuffer sbProcessUrl = new StringBuffer();
            sbProcessUrl.append(satServer + "/alaspatial/ws/sampling/process/download?");
            sbProcessUrl.append("taxonid=" + URLEncoder.encode(taxon, "UTF-8"));
            sbProcessUrl.append("&envlist=" + URLEncoder.encode(sbenvsel.toString(), "UTF-8"));
            if (true) { //an area always exists; useArea.isChecked()) {
                user_polygon = mc.getSelectionArea();
            } else {
                user_polygon = "";
            }
            System.out.println("user_polygon: " + user_polygon);
            String area;
            if (user_polygon.length() > 0) {
                //sbProcessUrl.append("&area=" + URLEncoder.encode(user_polygon, "UTF-8"));
                area = user_polygon;
            } else {
                //sbProcessUrl.append("&area=" + URLEncoder.encode("none", "UTF-8"));
                area = "none";
            }

            //String testurl = satServer + "/alaspatial/ws/sampling/test";

            HttpClient client = new HttpClient();
            //GetMethod get = new GetMethod(sbProcessUrl.toString()); // testurl
            PostMethod get = new PostMethod(sbProcessUrl.toString());
            get.addParameter("area", area);

            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            String slist = get.getResponseBodyAsString();

            System.out.println("Got response from SamplingWSController: \n" + slist);

            if (slist.equalsIgnoreCase("")) {
                Messagebox.show("Unable to download sample file. Please try again", "ALA Spatial Analysis Toolkit - Sampling", Messagebox.OK, Messagebox.ERROR);
            } else {
                System.out.println("Sending file to user: " + satServer + "/alaspatial" + slist);

                URL url = new URL(satServer + "/alaspatial" + slist);
                Filedownload.save(url.openStream(), "application/zip",url.getFile());
                mc.updateUserLogAnalysis("Sampling", "species: " + taxon + "; area: " + area, sbenvsel.toString(), satServer + "/alaspatial" + slist, pid, "Sampling results for species: " + taxon);
            }

        } catch (Exception e) {
            System.out.println("Exception calling sampling.download:");
            e.printStackTrace(System.out);
        }
    }

    public void downloadSampling(Event event) {
        try {
            if (pid == null || pid.equalsIgnoreCase("")) {
                Messagebox.show("Unable to download sample file. Please try again", "ALA Spatial Analysis Toolkit - Sampling", Messagebox.OK, Messagebox.ERROR);
            } else {
                //get job inputs/outputs
                String pth = "";
                try {
                    for (String s : getJob("inputs").split(";")) {
                        if (s.startsWith("output_path")) {
                            pth = s.split(":")[1];
                            pth = "";
                            String[] sp = s.split(":");
                            for (int i = 1; i < sp.length; i++) {
                                pth += sp[i];
                                if (i < sp.length - 1) {
                                    pth += ":";
                                }
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Sending file to user: " + satServer + "/alaspatial" + pth);
                URL url = new URL(satServer + "/alaspatial" + pth);
                Filedownload.save(url.openStream(), "application/zip",url.getFile());
                
                mc.updateUserLogAnalysis("Sampling", "", "", url.getFile(), pid, "Sampling download file for pid: " + pid + " from " + satServer + "/alaspatial" + pth);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    String getJob(String type) {
        try {
            StringBuffer sbProcessUrl = new StringBuffer();
            sbProcessUrl.append(satServer + "/alaspatial/ws/jobs/").append(type).append("?pid=").append(pid);

            System.out.println(sbProcessUrl.toString());
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(sbProcessUrl.toString());

            get.addRequestHeader("Accept", "text/plain");

            int result = client.executeMethod(get);
            String slist = get.getResponseBodyAsString();
            System.out.println(slist);
            return slist;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public void onClick$btnDownload(Event event) {
        download(null);
    }

    /**
     * Gets the main pages controller so we can add a
     * layer to the map
     * @return MapComposer = map controller class
     */
    private MapComposer getThisMapComposer() {

        MapComposer mapComposer = null;
        //Page page = maxentWindow.getPage();
        Page page = getPage();
        mapComposer = (MapComposer) page.getFellow("mapPortalPage");

        return mapComposer;
    }

    private void loadSpeciesOnMap() {

        // check if the species name is not valid
        // this might happen as we are automatically mapping
        // species without the user pressing a button
        if (sac.getSelectedItem() == null) {
            return;
        }

        cleanTaxon();
        String taxon = sac.getValue();

        String rank = "";
        /*
        String spVal = sac.getSelectedItem().getDescription();
        if (spVal.trim().startsWith("Scientific name")) {
            //myci.setValue(spVal[1].trim().substring(spVal[1].trim().indexOf(":")).trim());
            taxon = spVal.trim().substring(spVal.trim().indexOf(":") + 1, spVal.trim().indexOf("-")).trim();
            rank = "common name";
            //   mc.mapSpeciesByName(taxon, sac.getValue());
        } else {
            rank = StringUtils.substringBefore(spVal, " ").toLowerCase();
            //mc.mapSpeciesByName(taxon);
            //   mc.mapSpeciesByNameRank(taxon, rank, null);
        }
        taxon = taxon.substring(0, 1).toUpperCase() + taxon.substring(1);
        //mc.mapSpeciesByName(taxon);
        mc.mapSpeciesByLsid((String) (sac.getSelectedItem().getAnnotatedProperties().get(0)), taxon);
         *
         */
        String spVal = sac.getSelectedItem().getDescription();
        if (spVal.trim().contains(": ")) {
            taxon = spVal.trim().substring(spVal.trim().indexOf(":") + 1, spVal.trim().indexOf("-")).trim() + " (" + taxon + ")";
            rank = spVal.trim().substring(0, spVal.trim().indexOf(":")); //"species";

            if (rank.equalsIgnoreCase("scientific name")) {
                rank = "taxon";
            }
        } else {
            rank = StringUtils.substringBefore(spVal, " ").toLowerCase();
        }
        System.out.println("mapping rank and species: " + rank + " - " + taxon);
        mc.mapSpeciesByLsid((String) (sac.getSelectedItem().getAnnotatedProperties().get(0)), taxon, rank);

    }

    /**
     * get rid of the common name if present
     * 2 conditions here
     *  1. either species is automatically filled in from the Layer selector
     *     and is a common name and is in format Scientific name (Common name)
     *
     *  2. or user has searched for a common name from the analysis tab itself
     *     in which case we need to grab the scientific name for analysis
     *
     *  * condition 1 should also parse the proper taxon if its a genus, for eg
     *
     * @param taxon
     * @return
     */
    private String cleanTaxon() {
        String taxon = null;

        if(sac.getSelectedItem() == null && sac.getValue() != null){
            String taxValue = sac.getValue();
            if (taxValue.contains(" (")) {
                taxValue = StringUtils.substringBefore(taxValue, " (");
            }
            sac.refresh(taxValue);
        }

        //make the sac.getValue() a selected value if it appears in the list
        // - fix for common names entered but not selected
        if (sac.getSelectedItem() == null) {
            List list = sac.getItems();
            for (int i = 0; i < list.size(); i++) {
                Comboitem ci = (Comboitem) list.get(i);
                if (ci.getLabel().equalsIgnoreCase(sac.getValue())) {
                    System.out.println("cleanTaxon: set selected item");
                    sac.setSelectedItem(ci);
                    break;
                }
            }
        }
        /*
        if (StringUtils.isNotBlank(taxon)) {

        // check for condition 1
        System.out.println("Checking for cond.1: " + taxon);
        if (taxon.contains(" (")) {
        taxon = StringUtils.substringBefore(taxon, " (");
        }
        System.out.println("After checking for cond.1: " + taxon);

        // check for condition 2
        if (sac.getSelectedItem() != null) {
        String spVal = sac.getSelectedItem().getDescription();
        System.out.println("Checking for cond.2: " + taxon + " -- " + spVal);
        if (spVal.trim().startsWith("Scientific name")) {
        //myci.setValue(spVal[1].trim().substring(spVal[1].trim().indexOf(":")).trim());
        taxon = spVal.trim().substring(spVal.trim().indexOf(":") + 1, spVal.trim().indexOf("-")).trim();
        }
        System.out.println("After checking for cond.2: " + taxon);
        }

        }*/

        if (sac.getSelectedItem() != null && sac.getSelectedItem().getAnnotatedProperties() != null) {
            taxon = (String) sac.getSelectedItem().getAnnotatedProperties().get(0);
        }

        return taxon;
    }

    /**
     * populate sampling screen with values from active layers and area tab
     */
    public void callPullFromActiveLayers() {
        //get top species and list of env/ctx layers
        //String species = layersUtil.getFirstSpeciesLayer();
        String speciesandlsid = layersUtil.getFirstSpeciesLsidLayer();
        String species = null; 
        String lsid = null;
        if (StringUtils.isNotBlank(speciesandlsid)) {
            species = speciesandlsid.split(",")[0];
            lsid = speciesandlsid.split(",")[1];
        }
        if (StringUtils.isNotBlank(lsid)) {
            taxon = lsid;
        }
        String[] layers = layersUtil.getActiveEnvCtxLayers();


        /* set species from layer selector */
        if (species != null) {
            String tmpSpecies = species;
            if (species.contains(" (")) {
                tmpSpecies = StringUtils.substringBefore(species, " (");
            }
            sac.setValue(tmpSpecies);
            sac.refresh(tmpSpecies);
        }

        /* set as selected each envctx layer found */
        if (layers != null) {
            lbListLayers.selectLayers(layers);
        }

        lbListLayers.updateDistances();
    }
}
