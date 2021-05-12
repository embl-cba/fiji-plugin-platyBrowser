package de.embl.cba.mobie2.bdv;

import bdv.util.BdvHandle;
import de.embl.cba.mobie2.color.RandomColorSeedChangerCommand;
import de.embl.cba.mobie2.segment.BdvSegmentSelector;
import de.embl.cba.mobie2.view.ViewerManager;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.SerializableBdvOptions;
import sc.fiji.bdvpg.behaviour.SourceAndConverterContextMenuClickBehaviour;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.function.Supplier;

public class SliceViewer implements Supplier< BdvHandle >
{
	public static final String UNDO_SEGMENT_SELECTIONS = "Undo segment selections [ Ctrl Shift N ]";
	public static final String CHANGE_RANDOM_COLOR_SEED = "Change random color seed";
	private final SourceAndConverterBdvDisplayService displayService;
	private BdvHandle bdvHandle;
	private final boolean is2D;
	private final ViewerManager viewerManager;
	private final int timepoints;

	private SourceAndConverterContextMenuClickBehaviour contextMenu;
	private final SourceAndConverterService sacService;

	public SliceViewer( boolean is2D, ViewerManager viewerManager, int timepoints )
	{
		this.is2D = is2D;
		this.viewerManager = viewerManager;
		this.timepoints = timepoints;

		sacService = ( SourceAndConverterService ) SourceAndConverterServices.getSourceAndConverterService();
		displayService = SourceAndConverterServices.getBdvDisplayService();

		bdvHandle = createBdv( timepoints );
		displayService.registerBdvHandle( bdvHandle );

		installContextMenuAndKeyboardShortCuts();
	}

	@Override
	public BdvHandle get()
	{
		if ( bdvHandle == null )
		{
			bdvHandle = createBdv( timepoints );
			displayService.registerBdvHandle( bdvHandle );
		}
		return bdvHandle;
	}

	private void installContextMenuAndKeyboardShortCuts( )
	{
		final BdvSegmentSelector segmentBdvSelector = new BdvSegmentSelector( bdvHandle, is2D, () -> viewerManager.getSegmentationDisplays() );

		final Set< String > actionsKeys = sacService.getActionsKeys();

		sacService.registerAction( UNDO_SEGMENT_SELECTIONS, sourceAndConverters -> {
			// TODO: Maybe only do this for the sacs at the mouse position
			segmentBdvSelector.clearSelection();
		} );

		sacService.registerAction( CHANGE_RANDOM_COLOR_SEED, sourceAndConverters -> {
			// TODO: Maybe only do this for the sacs at the mouse position
			RandomColorSeedChangerCommand.incrementRandomColorSeed( sourceAndConverters );
		} );

		final String[] actions = {
				//sacService.getCommandName( ScreenShot.class ),
				sacService.getCommandName( BdvLocationLogger.class ),
				//sacService.getCommandName( SourceAndConverterBlendingModeChangerCommand.class ),
				sacService.getCommandName( RandomColorSeedChangerCommand.class ),
				UNDO_SEGMENT_SELECTIONS };

		contextMenu = new SourceAndConverterContextMenuClickBehaviour( bdvHandle, new SourcesAtMousePositionSupplier( bdvHandle, is2D ), actions );

		Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.behaviour( contextMenu, "Context menu", "button3", "shift P");
		behaviours.install( bdvHandle.getTriggerbindings(), "MoBIE" );

		behaviours.behaviour(
				( ClickBehaviour ) ( x, y ) ->
						new Thread( () -> segmentBdvSelector.run() ).start(),
				"Toggle selection", "ctrl button1" ) ;

		behaviours.behaviour(
				( ClickBehaviour ) ( x, y ) ->
						new Thread( () ->
						{
							segmentBdvSelector.clearSelection();
						}).start(),
				"Clear selection", "ctrl shift N" ) ;
	}

	public SourceAndConverterService getSacService()
	{
		return sacService;
	}

	public SourceAndConverterContextMenuClickBehaviour getContextMenu()
	{
		return contextMenu;
	}

	private BdvHandle createBdv( int numTimepoints )
	{
		// create Bdv
		final SerializableBdvOptions bdvOptions = new SerializableBdvOptions();
		bdvOptions.numTimePoints = numTimepoints;
		bdvOptions.is2D = is2D;
		final DefaultBdvSupplier bdvSupplier = new DefaultBdvSupplier( bdvOptions );
		final BdvHandle bdvHandle = bdvSupplier.get();
		return bdvHandle;
	}

	public BdvHandle getBdvHandle()
	{
		return bdvHandle;
	}

	public Window getWindow()
	{
		return SwingUtilities.getWindowAncestor( bdvHandle.getViewerPanel() );
	}
}
