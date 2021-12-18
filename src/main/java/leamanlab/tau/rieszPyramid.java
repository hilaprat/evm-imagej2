package leamanlab.tau;

import java.util.ArrayList;

import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.view.Views;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imagej.ops.OpService;


public class rieszPyramid {
	
	private ArrayList <Img<FloatType>> pyramid = null;
	private ArrayList <Img<FloatType>> rieszX = null;
	private ArrayList <Img<FloatType>> rieszY = null;
	
	public rieszPyramid() {  // constructor
		return;
	}
	
	private static int getNumLevels(Img <FloatType> img) {
		int levels = (int) img.dimension(0);
		for (int i=0; i < img.numDimensions(); i++) {
			int curr_levels = (int) Math.floor(Math.log(img.dimension(i)) / Math.log(2));
			if (curr_levels < levels) {
				levels = curr_levels;
			}
		}
		return levels;
	}
	
	private static ArrayList <Img<FloatType>> computeGaussPyramid(Img <FloatType> img, int levels, OpService ops) {
		double[] scales = new double[] {0.5, 0.5};
		Scale scale = new Scale(scales);
		ArrayList <Img<FloatType>> pyramid = new ArrayList<Img<FloatType>>(levels);
		
		pyramid.add(0, img.copy());
		
		for (int level=1; level < levels; level++) {
			Img< FloatType > convolved = ops.create().img(pyramid.get(level-1));
			long[] dims = new long[] {(long)(convolved.dimension(0) * 0.5), (long) (convolved.dimension(1) * 0.5)};
			// View as an infinite image, mirrored at the edges which is ideal for Gaussians
			Gauss3.gauss( 1.0, Views.extendMirrorSingle( pyramid.get(level-1) ), convolved );

			// resize
			RealRandomAccessible< FloatType > extendedAndInterpolated =
					Views.interpolate( Views.extendBorder( convolved ), new NLinearInterpolatorFactory< FloatType >() );
			FinalInterval interval = new FinalInterval(new long[] {0, 0}, new long[] {dims[0] - 1, dims[1] - 1});
			IntervalView <FloatType> levelImgView = Views.interval(RealViews.transform(extendedAndInterpolated, scale), interval);

			// save as Img to pyramid
			FinalDimensions finalDims = new FinalDimensions(dims);
			Img <FloatType> levelImg = ops.create().img(finalDims, new FloatType());
			LoopBuilder.setImages(levelImgView, levelImg).multiThreaded().forEachPixel((s, t) -> t.set(s));
			pyramid.add(level, levelImg);
		}
		return pyramid;
	}

	private static Img<FloatType> expandImage(Img<FloatType> input, OpService ops, long[] targetDims) {
		double[] scales = new double[] {2, 2};
		Scale scale = new Scale(scales);

		// expand image by 2
		RealRandomAccessible< FloatType > extendedAndInterpolated =
				Views.interpolate( Views.extendBorder( input ), new NLinearInterpolatorFactory< FloatType >() );
		FinalInterval interval = new FinalInterval(new long[] {0, 0}, new long[] {targetDims[0] - 1, targetDims[1] - 1});
		IntervalView <FloatType> levelImgView = Views.interval(RealViews.transform(extendedAndInterpolated, scale), interval);

		// run gauss filter on expanded image
		FinalDimensions finalDims = new FinalDimensions(targetDims);
		Img <FloatType> result = ops.create().img(finalDims, new FloatType());
		Gauss3.gauss( 1.0, Views.extendMirrorSingle(levelImgView), result );

		return result;
	}
	
	private static ArrayList<Img<FloatType>> computeLaplacePyr(ArrayList <Img<FloatType>> gaussPyramid, int levels, OpService ops) {
		ArrayList<Img<FloatType>> pyramid = new ArrayList<Img<FloatType>>(levels);
		
		for (int level=0; level < levels-1; level++) {
			Img <FloatType> gaussLevel = gaussPyramid.get(level+1).copy();
			Img <FloatType> gaussNextLevel = gaussPyramid.get(level);
			long[] targetDims = new long[] {gaussNextLevel.dimension(0), gaussNextLevel.dimension(1)};
			Img <FloatType> levelImgView = expandImage(gaussLevel, ops, targetDims);

			Img <FloatType> result = ops.create().img(gaussNextLevel);
			LoopBuilder.setImages(gaussPyramid.get(level), levelImgView, result).multiThreaded().forEachPixel(
					(a, b, s) -> s.setReal(a.getRealFloat() - b.getRealFloat()));
			pyramid.add(level, result);
		}
		
		pyramid.add(levels-1, gaussPyramid.get(levels-1).copy()); // lowest resolution- stays the same as gauss
		
		return pyramid;
	}
	
	public void computePyramid(Img <FloatType> img, OpService ops) {
		int levels = getNumLevels(img);
		ArrayList <Img<FloatType>> gaussPyramid = computeGaussPyramid(img, levels, ops);
		this.pyramid = computeLaplacePyr(gaussPyramid, levels, ops);
		this.rieszX = new ArrayList<Img<FloatType>>(levels);
		this.rieszY = new ArrayList<Img<FloatType>>(levels);
		
		double[][] valuesX = { {1.0, 0.0, -1.0}, {0.0, 0.5, 0.0} };
		double[][] valuesY = { {0.0, 0.5, 0.0}, {1.0, 0.0, -1.0} };
		int[] center = {1, 1};
		
		Kernel1D[] kernelX = Kernel1D.asymmetric( valuesX, center );
		Kernel1D[] kernelY = Kernel1D.asymmetric( valuesY, center );
		
		for (int level=0; level < levels-1; level++) {
			Img<FloatType> resultX =  ops.create().img(pyramid.get(level));
			Img<FloatType> resultY =  ops.create().img(pyramid.get(level));
			SeparableKernelConvolution.convolution( kernelX ).process( Views.extendBorder( pyramid.get(level) ), resultX );
			SeparableKernelConvolution.convolution( kernelY ).process( Views.extendBorder( pyramid.get(level) ), resultY );
			rieszX.add(level, resultX);
			rieszY.add(level, resultY);
		}
	}
	
	public ArrayList <Img<FloatType>> getPyramid() {
		return this.pyramid;
	}
	
	public ArrayList <Img<FloatType>> getRieszX() {
		return this.rieszX;
	}
	
	public ArrayList <Img<FloatType>> getRieszY() {
		return this.rieszY;
	}
	
	public void setPyramid(ArrayList <Img<FloatType>> pyramid) {
		this.pyramid = pyramid;
	}
	
	public void setRieszX(ArrayList <Img<FloatType>> rieszX) {
		this.rieszX = rieszX;
	}
	
	public void setRieszY(ArrayList <Img<FloatType>> rieszY) {
		this.rieszY = rieszY;
	}
	
	public static Img <FloatType> reconstructPyramid(ArrayList <Img<FloatType>> pyramid, OpService ops) {
		int levels = pyramid.size();
		
		Img <FloatType> nextLevel = pyramid.get(levels-1).copy();
		for (int level=levels-1; level > 0; level--) {
			Img <FloatType> next = pyramid.get(level-1);
			long[] targetDims = new long[] {next.dimension(0), next.dimension(1)};
			Img <FloatType> levelImg = expandImage(nextLevel, ops, targetDims);

			nextLevel = ops.create().img(next);
			LoopBuilder.setImages(levelImg, pyramid.get(level-1), nextLevel).multiThreaded().forEachPixel(
					(a, b, s) -> s.setReal(a.getRealFloat() + b.getRealFloat()));
		}
		
		return nextLevel.copy();
	}
	
	public static ArrayList<Img<FloatType>> initZeros(int numLevels, ArrayList <Img<FloatType>> pyramid, OpService ops) {
		ArrayList<Img<FloatType>> result = new ArrayList<Img<FloatType>>(numLevels);
		
		for (int level=0; level < numLevels; level++) {
			Img<FloatType> levelImg = ops.create().img(pyramid.get(level)); // create zeros img
			result.add(level, levelImg);
		}
		return result;
	}

}
