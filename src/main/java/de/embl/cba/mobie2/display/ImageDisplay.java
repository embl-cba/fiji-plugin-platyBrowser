package de.embl.cba.mobie2.display;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import de.embl.cba.mobie2.bdv.ImageSliceView;
import de.embl.cba.mobie2.color.opacity.AdjustableOpacityColorConverter;
import net.imglib2.display.ColorConverter;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.ArrayList;

public class ImageDisplay extends Display
{
	// Serialization
	private String color;
	private double[] contrastLimits;
	//private BlendingMode blendingMode;
	private boolean showImagesIn3d;

	// Runtime
	public transient ImageSliceView imageSliceView;
	private final SourceAndConverterBdvDisplayService displayService;

	public String getColor()
	{
		return color;
	}

	public double[] getContrastLimits()
	{
		return contrastLimits;
	}

//	public BlendingMode getBlendingMode()
//	{
//		return blendingMode;
//	}

	/**
	 * Create a serializable copy
	 *
	 * @param imageDisplay
	 */
	public ImageDisplay( ImageDisplay imageDisplay )
	{
		this.name = imageDisplay.name;
		this.displayService = SourceAndConverterServices.getBdvDisplayService();
		this.sources = new ArrayList<>();
		for ( SourceAndConverter< ? > sourceAndConverter : imageDisplay.sourceAndConverters )
		{
			sources.add( sourceAndConverter.getSpimSource().getName() );
		}

		final SourceAndConverter< ? > sourceAndConverter = imageDisplay.sourceAndConverters.get( 0 );
		final ConverterSetup converterSetup = displayService.getConverterSetup( sourceAndConverter );

		if( sourceAndConverter.getConverter() instanceof AdjustableOpacityColorConverter )
		{
			this.opacity = ( ( AdjustableOpacityColorConverter ) sourceAndConverter.getConverter() ).getOpacity();
		}

		if ( sourceAndConverter.getConverter() instanceof ColorConverter)
		{
			// needs to be of form r=(\\d+),g=(\\d+),b=(\\d+),a=(\\d+)"
			String colorString = ( ( ColorConverter ) sourceAndConverter.getConverter() ).getColor().toString();
			colorString.replaceAll("[()]", "");
			this.color = colorString;
		}

		double[] contrastLimits = new double[2];
		contrastLimits[0] = converterSetup.getDisplayRangeMin();
		contrastLimits[1] = converterSetup.getDisplayRangeMax();
		this.contrastLimits = contrastLimits;

//		this.blendingMode = (BlendingMode) SourceAndConverterServices.getSourceAndConverterService().getMetadata( sourceAndConverter, BLENDING_MODE );

		// TODO - show images in 3d (currently not supported in viewer)
	}
}
