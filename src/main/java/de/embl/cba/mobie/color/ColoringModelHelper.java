package de.embl.cba.mobie.color;

import de.embl.cba.mobie.display.SegmentationSourceDisplay;
import de.embl.cba.tables.color.ColoringModel;
import de.embl.cba.tables.color.ColumnColoringModelCreator;
import de.embl.cba.tables.tablerow.TableRowImageSegment;

public class ColoringModelHelper
{
	public static void configureMoBIEColoringModel( SegmentationSourceDisplay segmentationDisplay )
	{
		if ( segmentationDisplay.getColorByColumn() != null )
		{
			final ColumnColoringModelCreator< TableRowImageSegment > modelCreator = new ColumnColoringModelCreator( segmentationDisplay.tableRows );

			final ColoringModel< TableRowImageSegment > coloringModel;

			String coloringLut = getColoringLut( segmentationDisplay );

			if ( segmentationDisplay.getValueLimits() != null ) {
				coloringModel = modelCreator.createColoringModel(segmentationDisplay.getColorByColumn(), coloringLut, segmentationDisplay.getValueLimits()[0], segmentationDisplay.getValueLimits()[1]);
			} else {
				coloringModel = modelCreator.createColoringModel(segmentationDisplay.getColorByColumn(), coloringLut, null, null );
			}

			segmentationDisplay.coloringModel = new MoBIEColoringModel( coloringModel );
		}
		else
		{
			segmentationDisplay.coloringModel = new MoBIEColoringModel<>( segmentationDisplay.getLut() );
		}
	}

	private static String getColoringLut( SegmentationSourceDisplay segmentationDisplay )
	{
		return segmentationDisplay.getLut() ;
	}
}
