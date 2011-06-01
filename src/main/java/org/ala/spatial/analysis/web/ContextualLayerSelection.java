/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.ContextualLayerListComposer;
import au.org.emii.portal.util.LayerUtilities;
import net.sf.json.JSONObject;
import org.ala.spatial.util.CommonData;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Button;
import org.zkoss.zul.Window;

/**
 *
 * @author angus
 */
public class ContextualLayerSelection extends AreaToolComposer  {

    Button btnNext;
    ContextualLayersAutoComplete autoCompleteLayers;
    String treeName, treePath, treeMetadata;
    int treeSubType;

    @Override
    public void afterCompose() {
        super.afterCompose();
        
    }

    public void onClick$btnNext(Event event) {
         if(treeName != null) {
            getMapComposer().addWMSLayer(treeName,
                            treePath,
                            (float) 0.75, treeMetadata, treeSubType);

            getMapComposer().updateUserLogMapLayer("env - tree - add", /*joLayer.getString("uid")+*/"|"+treeName);


        }


      

      
        Window window = (Window) Executions.createComponents("WEB-INF/zul/AreaMapPolygon.zul", this.getParent(), Executions.getCurrent().getArg());
        window.doOverlapped();
        String script = getMapComposer().getOpenLayersJavascript().addFeatureSelectionTool();
        getMapComposer().getOpenLayersJavascript().execute(getMapComposer().getOpenLayersJavascript().iFrameReferences + script);
      
        this.detach();
    }

     public void onChange$autoCompleteLayers(Event event) {
        treeName = null;
        //btnOk.setDisabled(true);

        ContextualLayerListComposer llc = (ContextualLayerListComposer) getFellow("layerTree").getFellow("contextuallayerswindow");

        if (autoCompleteLayers.getItemCount() > 0 && autoCompleteLayers.getSelectedItem() != null) {
            JSONObject jo = (JSONObject) autoCompleteLayers.getSelectedItem().getValue();
            String metadata = "";

            metadata = CommonData.satServer + "/alaspatial/layers/" + jo.getString("uid");

            setLayer(jo.getString("displayname"), jo.getString("displaypath"), metadata,
                    jo.getString("type").equalsIgnoreCase("environmental")?LayerUtilities.GRID:LayerUtilities.CONTEXTUAL);
        } else {
            JSONObject joLayer = JSONObject.fromObject(llc.tree.getSelectedItem().getTreerow().getAttribute("lyr"));
            if (!joLayer.getString("type").contentEquals("class")) {

                String metadata = CommonData.satServer + "/alaspatial/layers/" + joLayer.getString("uid");

                setLayer(joLayer.getString("displayname"), joLayer.getString("displaypath"), metadata,
                        joLayer.getString("type").equalsIgnoreCase("environmental")?LayerUtilities.GRID:LayerUtilities.CONTEXTUAL);
            } else {
                String classAttribute = joLayer.getString("classname");
                String classValue = joLayer.getString("displayname");
                String layer = joLayer.getString("layername");
                String displaypath = joLayer.getString("displaypath") + "&cql_filter=(" + classAttribute + "='" + classValue + "');include";
                //Filtered requests don't work on
                displaypath = displaypath.replace("gwc/service/", "");
                // Messagebox.show(displaypath);
                String metadata = CommonData.satServer + "/alaspatial/layers/" + joLayer.getString("uid");

                setLayer(layer + " - " + classValue, displaypath, metadata,
                        joLayer.getString("type").equalsIgnoreCase("environmental")?LayerUtilities.GRID:LayerUtilities.CONTEXTUAL);
            }

            //close parent if it is 'addlayerwindow'
            try {
                getRoot().getFellow("addlayerwindow").detach();
            } catch (Exception e) {}
        }
    }

    public void setLayer(String name, String displaypath, String metadata, int subType) {
        treeName = name;
        treePath = displaypath;
        treeMetadata = metadata;
        treeSubType = subType;

        //fill autocomplete text
        autoCompleteLayers.setText(name);

        //clear selection on tree
        ContextualLayerListComposer llc = (ContextualLayerListComposer) getFellow("layerTree").getFellow("contextuallayerswindow");
        llc.tree.clearSelection();

       // btnOk.setDisabled(false);
    }
}