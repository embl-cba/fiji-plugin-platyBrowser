package de.embl.cba.platynereis;

import bdv.img.imaris.Imaris;
import bdv.spimdata.SpimDataMinimal;
import bdv.util.*;
import bdv.viewer.Interpolation;
import de.embl.cba.platynereis.ui.LegendPanel;
import de.embl.cba.platynereis.ui.MainFrame;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Plugin(type = Command.class, menuPath = "Plugins>Registration>EMBL>Platynereis", initializer = "init")
public class MainCommand extends DynamicCommand implements Interactive
{

    @Parameter
    public LogService logService;

    Bdv bdv;

    public Map< String, PlatynereisDataSource > dataSources;
    String emRawDataName;
    AffineTransform3D emRawDataTransform;
    LegendPanel legend;


    public void init()
    {
        System.setProperty( "apple.laf.useScreenMenuBar", "true" );

        String dir = IJ.getDirectory( "Please choose Platynereis directory" );

        File[] files = new File( dir ).listFiles();
        Arrays.sort( files );

        dataSources = Collections.synchronizedMap( new LinkedHashMap() );

        initDataSources( files );

        loadProSPrDataSourcesInSeparateThread();

        initBdvWithEmRawData();

        MainFrame mainFrame = new MainFrame( bdv, this );

        legend = mainFrame.getLegendPanel();

    }


    public String getEmRawDataName()
    {
        return emRawDataName;
    }


    private void loadProSPrDataSourcesInSeparateThread( )
    {
        (new Thread(new Runnable(){
            public void run(){
                loadProSPrDataSources( );
            }
        })).start();
    }

    private void addOverlay()
    {
        //bdv.getViewer().addTransformListener( lo );
        //bdv.getViewer().getDisplay().addOverlayRenderer( lo );
        //bdv.getViewerFrame().setVisible( true );
        //bdv.getViewer().requestRepaint();
        //https://github.com/PreibischLab/BigStitcher/blob/master/src/main/java/net/preibisch/stitcher/gui/overlay/LinkOverlay.java
    }

    public void run()
    {

    }

    private void print( String text )
    {
        Utils.log( text );
    }


    public void addSourceToBdv( String name )
    {
        PlatynereisDataSource source = dataSources.get( name );

        if ( source.bdvSource == null )
        {
            switch ( Constants.BDV_XML_SUFFIX ) // TODO: makes no sense...
            {
                case ".tif":
                    addSourceFromTiffFile( name );
                    break;
                case ".xml":
                    if ( source.spimData == null )
                    {
                        source.spimData = Utils.openSpimData( source.file );
                    }
                    showSourceInBdv( name );
                    break;
                default:
                    logService.error( "Unsupported format: " + Constants.BDV_XML_SUFFIX );
            }
        }

        source.bdvSource.setActive( true );
        source.isActive = true;
        source.bdvSource.setColor( asArgbType( source.color ) );
        source.name = name;

        legend.addSource( source );
    }

    public void hideDataSource( String dataSourceName )
    {
        if ( dataSources.get( dataSourceName ).bdvSource != null )
        {
            dataSources.get( dataSourceName ).bdvSource.setActive( false );
            dataSources.get( dataSourceName ).isActive = false;
        }
    }


    public void setDataSourceColor( String sourceName, Color color )
    {
        dataSources.get( sourceName ).bdvSource.setColor( asArgbType( color ) );
        dataSources.get( sourceName ).color = color;
    }


    public void setBrightness( String sourceName )
    {
        GenericDialog gd = new GenericDialog( "LUT max value" );
        gd.addNumericField( "LUT max value: ", dataSources.get( sourceName ).maxLutValue, 0 );
        gd.showDialog();
        if ( gd.wasCanceled() ) return;

        int max = ( int ) gd.getNextNumber();

        dataSources.get( sourceName ).bdvSource.setDisplayRange( 0.0, max );
        dataSources.get( sourceName ).maxLutValue = max;
    }


    private void showSourceInBdv( String dataSourceName )
    {
        PlatynereisDataSource source = dataSources.get( dataSourceName );

        if ( source.isSpimDataMinimal )
        {
            setName( dataSourceName, source );

            source.bdvSource = BdvFunctions.show( source.spimDataMinimal, BdvOptions.options().addTo( bdv ) ).get( 0 );
            source.bdvSource.setColor( asArgbType( source.color ) );
            source.bdvSource.setDisplayRange( 0.0, source.maxLutValue );

            bdv = source.bdvSource.getBdvHandle();
        }
        else
        {
            setName( dataSourceName, source );

            source.bdvSource = BdvFunctions.show( source.spimData, BdvOptions.options().addTo( bdv ) ).get( 0 );

            source.bdvSource.setColor( asArgbType( source.color ) );
            source.bdvSource.setDisplayRange( 0.0, source.maxLutValue );

            bdv = source.bdvSource.getBdvHandle();
        }

    }

    private void setName( String name, PlatynereisDataSource source )
    {
        if ( source.spimData != null )
        {
            source.spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getChannel().setName( name );
        }
    }

    private void addSourceFromTiffFile( String gene )
    {
        ImagePlus imp = IJ.openImage( dataSources.get( gene ).file.toString() );
        Img img = ImageJFunctions.wrap( imp );

        AffineTransform3D prosprScaling = new AffineTransform3D();
        prosprScaling.scale( Constants.PROSPR_SCALING_IN_MICROMETER );

        final BdvSource source = BdvFunctions.show( img, gene, Bdv.options().addTo( bdv ).sourceTransform( prosprScaling ) );
        source.setColor( asArgbType( Constants.DEFAULT_GENE_COLOR ) );
        dataSources.get( gene ).color = Constants.DEFAULT_GENE_COLOR;
        dataSources.get( gene ).bdvSource = source;

    }

    private ARGBType asArgbType( Color color )
    {
        return new ARGBType( ARGBType.rgba( color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() ) );
    }

    private void initBdvWithEmRawData(  )
    {
        showSourceInBdv( emRawDataName );

        bdv.getBdvHandle().getViewerPanel().setInterpolation( Interpolation.NLINEAR );

        //bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( new AffineTransform3D() );
    }

    private SpimDataMinimal openImaris( File file, double[] calibration )
    {
        SpimDataMinimal spimDataMinimal;

        try
        {
            spimDataMinimal = Imaris.openIms( file.toString() );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            return null;
        }

        setScale( calibration, spimDataMinimal );

        return spimDataMinimal;

    }

    private void setScale( double[] calibration, SpimDataMinimal spimDataMinimal )
    {
        final AffineTransform3D affineTransform3D = new AffineTransform3D();
        final Scale scale = new Scale( calibration );
        affineTransform3D.preConcatenate( scale );
        final ViewTransformAffine calibrationTransform = new ViewTransformAffine( "calibration", affineTransform3D );
        spimDataMinimal.getViewRegistrations().getViewRegistration( 0,0 ).identity();
        spimDataMinimal.getViewRegistrations().getViewRegistration( 0,0 ).preconcatenateTransform( calibrationTransform );

        final FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions( "micrometer", calibration );
        BasicViewSetup basicViewSetup = new BasicViewSetup(  0, "view", null, voxelDimensions );
        spimDataMinimal.getSequenceDescription().getViewSetupsOrdered().set( 0, basicViewSetup);
    }

    private void initDataSources( File[] files )
    {
        for ( File file : files )
        {
            if ( file.getName().endsWith( Constants.BDV_XML_SUFFIX ) || file.getName().endsWith( Constants.IMARIS_SUFFIX )  )
            {
                String dataSourceName = getDataSourceName( file );

                PlatynereisDataSource source = new PlatynereisDataSource();
                dataSources.put( dataSourceName, source );
                source.file = file;
                source.maxLutValue = 255;

                if ( file.getName().contains( Constants.EM_RAW_FILE_ID ) || file.getName().contains( Constants.EM_SEGMENTED_FILE_ID ) )
                {
                    if ( file.getName().endsWith( Constants.BDV_XML_SUFFIX ) )
                    {
                        source.spimData = Utils.openSpimData( file );
                    }
                    else if ( file.getName().contains( Constants.IMARIS_SUFFIX ) )
                    {
                        double[] calibration = new double[] { 0.01, 0.01, 0.025 };
                        source.spimDataMinimal = openImaris( file, calibration );
                        source.isSpimDataMinimal = true;
                    }

                    if ( file.getName().contains( Constants.EM_RAW_FILE_DEFAULT_ID ) )
                    {
                        emRawDataName = dataSourceName;
                        ProSPrRegistration.setEmSimilarityTransform( source );
                        source.name = Constants.EM_RAW_FILE_DEFAULT_ID;
                    }

                    if ( file.getName().contains( Constants.EM_RAW_FILE_ID )  )
                    {
                        source.color = Constants.DEFAULT_EM_RAW_COLOR;
                    }

                    if ( file.getName().contains( Constants.EM_SEGMENTED_FILE_ID ) )
                    {
                        source.color = Constants.DEFAULT_EM_SEGMENTATION_COLOR;
                    }
                }
                else // gene
                {
                    source.color = Constants.DEFAULT_GENE_COLOR;
                }
            }
        }
    }


    private void loadProSPrDataSources( )
    {
        Set< String > names = dataSources.keySet();

        for (  String name : names )
        {
            PlatynereisDataSource source = dataSources.get( name );

            if ( source.file.getName().contains( Constants.EM_FILE_ID ) ) continue;

            if ( source.file.getName().endsWith( Constants.BDV_XML_SUFFIX ) )
            {
                source.spimData = Utils.openSpimData( source.file );
            }
        }
    }


    private String getDataSourceName( File file )
    {
        String dataSourceName = null;

        if ( file.getName().endsWith( Constants.BDV_XML_SUFFIX ) )
		{
			dataSourceName = file.getName().replaceAll( Constants.BDV_XML_SUFFIX, "" );
		}
		else if ( file.getName().endsWith( Constants.IMARIS_SUFFIX ) )
		{
			dataSourceName = file.getName().replaceAll( Constants.IMARIS_SUFFIX, "" );
		}

        return dataSourceName;
    }


    public static void main( String... args )
    {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run( MainCommand.class, true );
    }


    public void toggleVisibility( String dataSourceName )
    {
        boolean isActive = dataSources.get( dataSourceName ).isActive;
        dataSources.get( dataSourceName ).isActive = !isActive ;
        dataSources.get( dataSourceName ).bdvSource.setActive( !isActive );
    }
}