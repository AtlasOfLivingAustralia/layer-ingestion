/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.org.emii.portal.composer;

import au.org.emii.portal.util.SessionPrint;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Iframe;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

/**
 *
 * @author Adam
 */
public class PrintingComposer extends UtilityComposer {
    Button btnExport;
    Button btnPreview;
    Textbox txtHeader;
    Checkbox chkGrid;
    Combobox cbFormat;
    Combobox cbResolution;

    @Override
    public void afterCompose() {
        super.afterCompose();
        
        cbFormat.setSelectedIndex(0);
        cbResolution.setSelectedIndex(0);
    }

    public void onClick$btnExport(Event event) {
        //header text
        String header = txtHeader.getValue();

        //grid
        double grid = 0; //zero is none
        if(chkGrid.isChecked()) grid = 1;

        //format (pdf, png, jpg)
        String format = cbFormat.getValue();

        //resolution (current == 0, print == 1)
        int resolution = cbResolution.getSelectedIndex();

        getMapComposer().print(header,grid,format,resolution, false);

        this.detach();  //close this window
    }

    public void onClick$btnPreview(Event event) {
        //header text
        String header = txtHeader.getValue();

        //grid
        double grid = 0; //zero is none
        if(chkGrid.isChecked()) grid = 1;

        //format (pdf, png, jpg)
        String format = cbFormat.getValue();

        //resolution (current == 0, print == 1)
        int resolution = cbResolution.getSelectedIndex();

        SessionPrint sp = getMapComposer().print(header,grid,format,resolution, true);

        String previewUrl = sp.getPreviewUrl();

        System.out.println("PREVIEW URL: " + previewUrl);

        //popup another window
        Window w = new Window("Export preview", "normal", false);
        w.setClosable(true);
        w.setSizable(true);
        w.setWidth("640px");
        w.setHeight("480px");
        Iframe iframe = new Iframe();
        iframe.setWidth("100%");
        iframe.setHeight("100%");
        iframe.setSrc(previewUrl);
        iframe.setParent(w);
        w.setParent(this.getParent());
        try {
            w.doModal();
        } catch (InterruptedException ex) {
            Logger.getLogger(PrintingComposer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SuspendNotAllowedException ex) {
            Logger.getLogger(PrintingComposer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
