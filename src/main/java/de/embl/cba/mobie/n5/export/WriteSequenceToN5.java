/*-
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2020 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.embl.cba.mobie.n5.export;

import bdv.export.*;
import bdv.export.ExportScalePyramid.AfterEachPlane;
import bdv.export.ExportScalePyramid.LoopbackHeuristic;
import bdv.img.cache.SimpleCacheArrayLoader;
import bdv.img.n5.N5ImageLoader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Cast;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static bdv.img.n5.BdvN5Format.*;
import static net.imglib2.cache.img.ReadOnlyCachedCellImgOptions.options;

/**
 * @author Tobias Pietzsch
 * @author John Bogovic
 */
public class WriteSequenceToN5
{
	private static final String MULTI_SCALE_KEY = "multiScale";
	private static final String RESOLUTION_KEY = "resolution";

	/**
	 * Create a n5 group containing image data from all views and all
	 * timepoints in a chunked, mipmaped representation.
	 *
	 * @param seq
	 *            description of the sequence to be stored as hdf5. (The
	 *            {@link AbstractSequenceDescription} contains the number of
	 *            setups and timepoints as well as an {@link BasicImgLoader}
	 *            that provides the image data, Registration information is not
	 *            needed here, that will go into the accompanying xml).
	 * @param perSetupMipmapInfo
	 *            this maps from setup {@link BasicViewSetup#getId() id} to
	 *            {@link ExportMipmapInfo} for that setup. The
	 *            {@link ExportMipmapInfo} contains for each mipmap level, the
	 *            subsampling factors and subdivision block sizes.
	 * @param compression
	 *            n5 compression scheme.
	 * @param n5File
	 *            n5 root.
	 * @param loopbackHeuristic
	 *            heuristic to decide whether to create each resolution level by
	 *            reading pixels from the original image or by reading back a
	 *            finer resolution level already written to the hdf5. may be
	 *            null (in this case always use the original image).
	 * @param afterEachPlane
	 *            this is called after each "plane of chunks" is written, giving
	 *            the opportunity to clear caches, etc.
	 * @param numCellCreatorThreads
	 *            The number of threads that will be instantiated to generate
	 *            cell data. Must be at least 1. (In addition the cell creator
	 *            threads there is one writer thread that saves the generated
	 *            data to HDF5.)
	 * @param progressWriter
	 *            completion ratio and status output will be directed here.
	 */
	public static void writeN5File(
			final AbstractSequenceDescription< ?, ?, ? > seq,
			final Map< Integer, ExportMipmapInfo > perSetupMipmapInfo,
			final Compression compression,
			final File n5File,
			final LoopbackHeuristic loopbackHeuristic,
			final AfterEachPlane afterEachPlane,
			final int numCellCreatorThreads,
			ProgressWriter progressWriter ) throws IOException
	{
		if ( progressWriter == null )
			progressWriter = new ProgressWriterNull();
		progressWriter.setProgress( 0 );

		final BasicImgLoader imgLoader = seq.getImgLoader();

		for ( final BasicViewSetup setup : seq.getViewSetupsOrdered() )
		{
			final Object type = imgLoader.getSetupImgLoader( setup.getId() ).getImageType();
			if ( !( type instanceof RealType &&
					type instanceof NativeType &&
					N5Utils.dataType( Cast.unchecked( type ) ) != null ) )
				throw new IllegalArgumentException( "Unsupported pixel type: " + type.getClass().getSimpleName() );
		}

		final List< Integer > timepointIds = seq.getTimePoints().getTimePointsOrdered().stream()
				.map( TimePoint::getId )
				.collect( Collectors.toList() );
		final List< Integer > setupIds = seq.getViewSetupsOrdered().stream()
				.map( BasicViewSetup::getId )
				.collect( Collectors.toList() );

		N5Writer n5 = new N5FSWriter( n5File.getAbsolutePath() );

		// write Mipmap descriptions
		for ( final int setupId : setupIds )
		{
			final String pathName = getPathName( setupId );
			final int[][] downsamplingFactors = perSetupMipmapInfo.get( setupId ).getExportResolutions();
			final DataType dataType = N5Utils.dataType( Cast.unchecked( imgLoader.getSetupImgLoader( setupId ).getImageType() ) );
			n5.createGroup( pathName );
			n5.setAttribute( pathName, DOWNSAMPLING_FACTORS_KEY, downsamplingFactors );
			n5.setAttribute( pathName, DATA_TYPE_KEY, dataType );
		}


		// calculate number of tasks for progressWriter
		int numTasks = 0; // first task is for writing mipmap descriptions etc...
		for ( final int timepointIdSequence : timepointIds )
			for ( final int setupIdSequence : setupIds )
				if ( seq.getViewDescriptions().get( new ViewId( timepointIdSequence, setupIdSequence ) ).isPresent() )
					numTasks++;
		int numCompletedTasks = 0;

		final ExecutorService executorService = Executors.newFixedThreadPool( numCellCreatorThreads );
		try
		{
			// write image data for all views
			final int numTimepoints = timepointIds.size();
			int timepointIndex = 0;
			for ( final int timepointId : timepointIds )
			{
				progressWriter.out().printf( "proccessing timepoint %d / %d\n", ++timepointIndex, numTimepoints );

				// assemble the viewsetups that are present in this timepoint
				final ArrayList< Integer > setupsTimePoint = new ArrayList<>();
				for ( final int setupId : setupIds )
					if ( seq.getViewDescriptions().get( new ViewId( timepointId, setupId ) ).isPresent() )
						setupsTimePoint.add( setupId );

				final int numSetups = setupsTimePoint.size();
				int setupIndex = 0;
				for ( final int setupId : setupsTimePoint )
				{
					progressWriter.out().printf( "proccessing setup %d / %d\n", ++setupIndex, numSetups );

					final ExportMipmapInfo mipmapInfo = perSetupMipmapInfo.get( setupId );
					final double startCompletionRatio = ( double ) numCompletedTasks++ / numTasks;
					final double endCompletionRatio = ( double ) numCompletedTasks / numTasks;
					final ProgressWriter subProgressWriter = new SubTaskProgressWriter( progressWriter, startCompletionRatio, endCompletionRatio );
					writeScalePyramid(
							n5, compression,
							imgLoader, setupId, timepointId, mipmapInfo,
							executorService, numCellCreatorThreads,
							loopbackHeuristic, afterEachPlane, subProgressWriter );


					// additional attributes for paintera compatibility
					final String pathName = getPathName( setupId, timepointId );
					n5.createGroup( pathName );
					n5.setAttribute( pathName, MULTI_SCALE_KEY, true );
					final VoxelDimensions voxelSize = seq.getViewSetups().get( setupId ).getVoxelSize();
					if ( voxelSize != null )
					{
						final double[] resolution = new double[ voxelSize.numDimensions() ];
						voxelSize.dimensions( resolution );
						n5.setAttribute( pathName, RESOLUTION_KEY, resolution );
					}
					final int[][] downsamplingFactors = perSetupMipmapInfo.get( setupId ).getExportResolutions();
					for( int l = 0; l < downsamplingFactors.length; ++l )
						n5.setAttribute( getPathName( setupId, timepointId, l ), DOWNSAMPLING_FACTORS_KEY, downsamplingFactors[ l ] );
				}
			}
		}
		finally
		{
			executorService.shutdown();
		}

		progressWriter.setProgress( 1.0 );
	}

	static < T extends RealType< T > & NativeType< T > > void writeScalePyramid(
			final N5Writer n5,
			final Compression compression,
			final BasicImgLoader imgLoader,
			final int setupId,
			final int timepointId,
			final ExportMipmapInfo mipmapInfo,
			final ExecutorService executorService,
			final int numThreads,
			final LoopbackHeuristic loopbackHeuristic,
			final AfterEachPlane afterEachPlane,
			ProgressWriter progressWriter ) throws IOException
	{
		final BasicSetupImgLoader< T > setupImgLoader = Cast.unchecked( imgLoader.getSetupImgLoader( setupId ) );
		final RandomAccessibleInterval< T > img = setupImgLoader.getImage( timepointId );
		final T type = setupImgLoader.getImageType();
		final N5DatasetIO< T > io = new N5DatasetIO<>( n5, compression, setupId, timepointId, type );
		ExportScalePyramid.writeScalePyramid(
				img, type, mipmapInfo, io,
				executorService, numThreads,
				loopbackHeuristic, afterEachPlane, progressWriter );
	}

	static class N5Dataset
	{
		final String pathName;
		final DatasetAttributes attributes;

		public N5Dataset( final String pathName, final DatasetAttributes attributes )
		{
			this.pathName = pathName;
			this.attributes = attributes;
		}
	}

	static class N5DatasetIO< T extends RealType< T > & NativeType< T > > implements ExportScalePyramid.DatasetIO< N5Dataset, T >
	{
		private final N5Writer n5;
		private final Compression compression;
		private final int setupId;
		private final int timepointId;
		private final DataType dataType;
		private final T type;
		private final Function< ExportScalePyramid.Block< T >, DataBlock< ? > > getDataBlock;

		public N5DatasetIO( final N5Writer n5, final Compression compression, final int setupId, final int timepointId, final T type )
		{
			this.n5 = n5;
			this.compression = compression;
			this.setupId = setupId;
			this.timepointId = timepointId;
			this.dataType = N5Utils.dataType( type );
			this.type = type;

			switch ( dataType )
			{
			case UINT8:
				getDataBlock = b -> new ByteArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case UINT16:
				getDataBlock = b -> new ShortArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case UINT32:
				getDataBlock = b -> new IntArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case UINT64:
				getDataBlock = b -> new LongArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case INT8:
				getDataBlock = b -> new ByteArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case INT16:
				getDataBlock = b -> new ShortArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case INT32:
				getDataBlock = b -> new IntArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case INT64:
				getDataBlock = b -> new LongArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case FLOAT32:
				getDataBlock = b -> new FloatArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			case FLOAT64:
				getDataBlock = b -> new DoubleArrayDataBlock( b.getSize(), b.getGridPosition(), Cast.unchecked( b.getData().getStorageArray() ) );
				break;
			default:
				throw new IllegalArgumentException();
			}
		}

		@Override
		public N5Dataset createDataset( final int level, final long[] dimensions, final int[] blockSize ) throws IOException
		{
			final String pathName = getPathName( setupId, timepointId, level );
			n5.createDataset( pathName, dimensions, blockSize, dataType, compression );
			final DatasetAttributes attributes = n5.getDatasetAttributes( pathName );
			return new N5Dataset( pathName, attributes );
		}

		@Override
		public void writeBlock( final N5Dataset dataset, final ExportScalePyramid.Block< T > dataBlock ) throws IOException
		{
			n5.writeBlock( dataset.pathName, dataset.attributes, getDataBlock.apply( dataBlock ) );
		}

		@Override
		public void flush( final N5Dataset dataset )
		{}

		@Override
		public RandomAccessibleInterval< T > getImage( final int level ) throws IOException
		{
			final String pathName = getPathName( setupId, timepointId, level );
			final DatasetAttributes attributes = n5.getDatasetAttributes( pathName );
			final long[] dimensions = attributes.getDimensions();
			final int[] cellDimensions = attributes.getBlockSize();
			final CellGrid grid = new CellGrid( dimensions, cellDimensions );
			final SimpleCacheArrayLoader< ? > cacheArrayLoader = N5ImageLoader.createCacheArrayLoader( n5, pathName );
			return new ReadOnlyCachedCellImgFactory().createWithCacheLoader(
					dimensions, type,
					key -> {
						final int n = grid.numDimensions();
						final long[] cellMin = new long[ n ];
						final int[] cellDims = new int[ n ];
						final long[] cellGridPosition = new long[ n ];
						grid.getCellDimensions( key, cellMin, cellDims );
						grid.getCellGridPositionFlat( key, cellGridPosition );
						return new Cell<>( cellDims, cellMin, cacheArrayLoader.loadArray( cellGridPosition ) );
					},
					options().cellDimensions( cellDimensions ) );
		}
	}
}
