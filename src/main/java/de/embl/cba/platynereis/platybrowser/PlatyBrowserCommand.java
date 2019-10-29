package de.embl.cba.platynereis.platybrowser;

import de.embl.cba.platynereis.utils.ui.VersionsDialog;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;


@Plugin(type = Command.class, menuPath = "Plugins>EMBL>Explore>Platynereis Atlas" )
public class PlatyBrowserCommand implements Command
{
	@Parameter ( label = "Image data", style = "directory" )
	public File imagesLocation = new File("/Volumes/arendt/EM_6dpf_segmentation/platy-browser-data/data");

	@Parameter ( label = "Table data" )
	public String tablesLocation = "https://git.embl.de/tischer/platy-browser-tables/raw/master/data";

	@Override
	public void run()
	{
		final String version = new VersionsDialog().showDialog( tablesLocation + "/versions.json" );

		new PlatyBrowser(
				version,
				imagesLocation.toString(),
				tablesLocation );
	}

	public static void main(final String... args)
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		ij.command().run( PlatyBrowserCommand.class, true );
	}
}
