package de.embl.cba.mobie2.view.additionalviews;

import de.embl.cba.mobie.ui.MoBIESettings;
import de.embl.cba.mobie2.MoBIE2;
import de.embl.cba.mobie2.serialize.AdditionalViewsJsonParser;
import de.embl.cba.mobie2.ui.UserInterfaceHelper;
import de.embl.cba.mobie2.view.View;

import java.io.IOException;
import java.util.Map;

import static de.embl.cba.tables.FileUtils.selectPathFromProjectOrFileSystem;

public class AdditionalViewsLoader {

    private MoBIE2 moBIE2;
    private MoBIESettings settings;

    public AdditionalViewsLoader ( MoBIE2 moBIE2 ) {
        this.moBIE2 = moBIE2;
        this.settings = moBIE2.getSettings();
    }

    public void loadAdditionalViewsDialog() {
        try {
            String additionalViewsDirectory = moBIE2.getDatasetPath("misc", "views" );
            String selectedFilePath = selectPathFromProjectOrFileSystem( additionalViewsDirectory, "View" );
            // to match to the existing view selection panels, we enable the cross platform look and feel
            UserInterfaceHelper.resetCrossPlatformSwingLookAndFeel();

            if (selectedFilePath != null) {
                Map< String, View> views = new AdditionalViewsJsonParser().getViews( selectedFilePath ).views;
                moBIE2.getUserInterface().addViews( views );
            }

            UserInterfaceHelper.resetSystemSwingLookAndFeel();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}