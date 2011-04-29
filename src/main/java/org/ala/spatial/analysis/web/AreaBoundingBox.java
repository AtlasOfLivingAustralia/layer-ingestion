package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.MapComposer;
import au.org.emii.portal.composer.UtilityComposer;
import au.org.emii.portal.menu.MapLayer;
import java.util.Map;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Button;
import org.zkoss.zul.Textbox;

/**
 *
 * @author Adam
 */
public class AreaBoundingBox extends AreaToolComposer {

    private String boxGeom;
    private Textbox displayGeom;
    //String layerName;
    Textbox txtLayerName;
    Button btnNext;
    Button btnClear;

    @Override
    public void afterCompose() {
        super.afterCompose();
        txtLayerName.setValue(getMapComposer().getNextAreaLayerName("My Area"));
    }

    public void onClick$btnNext(Event event) {
        //reapply layer name
        getMapComposer().getMapLayer(layerName).setDisplayName(txtLayerName.getValue());
        getMapComposer().redrawLayersList();

        this.detach();
    }

    public void onClick$btnClear(Event event) {
        MapComposer mc = getThisMapComposer();
        if (layerName != null && mc.getMapLayer(layerName) != null) {
            mc.removeLayer(layerName);
        }
        String script = mc.getOpenLayersJavascript().addBoxDrawingTool();
        mc.getOpenLayersJavascript().execute(mc.getOpenLayersJavascript().iFrameReferences + script);
        displayGeom.setValue("");
        btnNext.setDisabled(true);
        btnClear.setDisabled(true);
    }

    public void onClick$btnCancel(Event event) {
        MapComposer mc = getThisMapComposer();
        if (layerName != null && mc.getMapLayer(layerName) != null) {
            mc.removeLayer(layerName);
        }
        this.detach();
    }

    public void onBoxGeom(Event event) {
        boxGeom = (String) event.getData();
        //setInstructions(null, null);
        try {

            if (boxGeom.contains("NaN NaN")) {
                displayGeom.setValue("");
            } else {
                displayGeom.setValue(boxGeom);
            }
            // updateComboBoxText();
            updateSpeciesList(false); // true


            //get the current MapComposer instance
            MapComposer mc = getThisMapComposer();

            //add feature to the map as a new layer
            //mc.removeLayer("Area Selection");
            //mc.deactiveLayer(mc.getMapLayer("Area Selection"), true,true);
            layerName = (mc.getMapLayer(txtLayerName.getValue()) == null) ? txtLayerName.getValue() : mc.getNextAreaLayerName(txtLayerName.getValue());
            MapLayer mapLayer = mc.addWKTLayer(boxGeom, layerName, txtLayerName.getValue());

            btnNext.setDisabled(false);
            btnClear.setDisabled(false);
        } catch (Exception e) {//FIXME
        }

    }

    /**
     * updates species list analysis tab with refreshCount
     */
    void updateSpeciesList(boolean populateSpeciesList) {
        try {
            FilteringResultsWCController win =
                    (FilteringResultsWCController) getMapComposer().getFellow("leftMenuAnalysis").getFellow("analysiswindow").getFellow("sf").getFellow("selectionwindow").getFellow("speciesListForm").getFellow("popup_results");
            //if (!populateSpeciesList) {
            win.refreshCount();
            //} else {
            //    win.onClick$refreshButton2();
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // updateAreaLabel();
    }

    /**
     * Gets the main pages controller so we can add a
     * drawing tool to the map
     * @return MapComposer = map controller class
     */
    private MapComposer getThisMapComposer() {

        MapComposer mapComposer = null;
        Page page = getPage();
        mapComposer = (MapComposer) page.getFellow("mapPortalPage");

        return mapComposer;
    }
}