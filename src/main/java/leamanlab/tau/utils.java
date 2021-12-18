package leamanlab.tau;

import org.scijava.log.LogService;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.Axis;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import uk.me.berndporr.iirj.Biquad;
import uk.me.berndporr.iirj.Butterworth;

public class utils <T extends RealType<T> & NativeType<T>> {

	static Biquad calcButterCoeffs(double centerFreq, double widthFreq, double samplingFreq) {
		int order = 1;
		
		Butterworth butterworth = new Butterworth();
		butterworth.bandPass(order, samplingFreq, centerFreq, widthFreq);
		return butterworth.getBiquad(0);
	}
	
	static Img <FloatType> amplitudeWeightedBlur(Img <FloatType> temporallyFilteredPhase, Img <FloatType> amplitude, OpService ops) {
		float epsilon = 0.000001f;
		Img <FloatType> result = ops.create().img(amplitude);
		Img <FloatType> denominator = ops.create().img(amplitude);
		Img <FloatType> numerator = ops.create().img(amplitude);

		double[] kernel = Gauss3.halfkernel(2.0, 2, true);
		double[][] kernels = {kernel, kernel};
		Kernel1D[] gaussKernel = Kernel1D.symmetric( kernels );

		SeparableKernelConvolution.convolution( gaussKernel ).process( 
				Views.extendBorder( amplitude ), denominator );
		
		LoopBuilder.setImages(temporallyFilteredPhase, amplitude, result).multiThreaded().forEachPixel(
				(tpf, a, o) -> o.setReal(tpf.getRealFloat() * a.getRealFloat()));
		SeparableKernelConvolution.convolution( gaussKernel ).process( 
				Views.extendBorder( result ), numerator );
		
		LoopBuilder.setImages(numerator, denominator, result).multiThreaded().forEachPixel(
				(tpf, a, o) -> o.setReal(tpf.getRealFloat() / (a.getRealFloat() + epsilon)));
		
		return result;
	}
	
	static Img<FloatType> phaseShiftCoefficientRealPart(
			Img <FloatType> rieszReal, Img <FloatType> rieszX, Img <FloatType> rieszY, Img <FloatType> phaseCos, Img <FloatType> phaseSin, OpService ops) {
		float epsilon = 0.000001f;
		Img <FloatType> result = ops.create().img(rieszReal);
		
		Img <FloatType> phaseMagnitudeAndTemp = ops.create().img(rieszReal);
		LoopBuilder.setImages(phaseCos, phaseSin, phaseMagnitudeAndTemp).multiThreaded().forEachPixel(
				(pc, ps, o) -> o.setReal(Math.sqrt(Math.pow(pc.getRealFloat(), 2) + Math.pow(ps.getRealFloat(), 2)) + epsilon));
		
		Img <FloatType> expPhaseReal = ops.create().img(rieszReal);
		Img <FloatType> expPhaseX = ops.create().img(rieszReal);
		Img <FloatType> expPhaseY = ops.create().img(rieszReal);
		LoopBuilder.setImages(phaseMagnitudeAndTemp, expPhaseReal).multiThreaded().forEachPixel(
				(pm, o) -> o.setReal(Math.cos(pm.getRealFloat())));
		LoopBuilder.setImages(phaseCos, phaseMagnitudeAndTemp, expPhaseX).multiThreaded().forEachPixel(
				(pc, pm, o) -> o.setReal(pc.getRealFloat() / pm.getRealFloat() * Math.sin(pm.getRealFloat())));
		LoopBuilder.setImages(phaseSin, phaseMagnitudeAndTemp, expPhaseY).multiThreaded().forEachPixel(
				(ps, pm, o) -> o.setReal(ps.getRealFloat() / pm.getRealFloat() * Math.sin(pm.getRealFloat())));
		
		LoopBuilder.setImages(expPhaseReal, rieszReal, expPhaseX, rieszX, phaseMagnitudeAndTemp).multiThreaded().forEachPixel(
				(epr, rr, epx, rx, tmp) -> tmp.setReal(epr.getRealFloat() * rr.getRealFloat() - epx.getRealFloat() * rx.getRealFloat()));
		LoopBuilder.setImages(phaseMagnitudeAndTemp, expPhaseY, rieszY, result).multiThreaded().forEachPixel(
				(tmp, epy, ry, o) -> o.setReal(tmp.getRealFloat() - epy.getRealFloat() * ry.getRealFloat()));
		
		return result;
	}

	private static Img<FloatType> convertToGray(final Img<FloatType> imp, OpService ops, LogService log) {
		log.info("Converting video to gray");
		final ImgFactory< FloatType > imgFactory = imp.factory();
		final int[] dims = new int[] {(int)imp.dimension(0), (int)imp.dimension(1), (int)imp.dimension(3)};
		Img< FloatType > grayImg = imgFactory.create( dims );

		IntervalView<FloatType> redVid = Views.hyperSlice(imp, 2, 0);
		IntervalView<FloatType> greenVid = Views.hyperSlice(imp, 2, 1);
		IntervalView<FloatType> blueVid = Views.hyperSlice(imp, 2, 2);
		LoopBuilder.setImages(redVid, greenVid, blueVid, grayImg).multiThreaded().forEachPixel(
				(r, g, b, o) -> o.set((int)((r.get() + g.get() + b.get())/3)));

		log.info("Done converting video to gray");
		return grayImg;
	}

	public static Img<FloatType> preProcessImg(final Img<FloatType> img, OpService ops, LogService log) {
		Img<FloatType> grayImg = null;
		if (img.dimension(2) == 3) {
			grayImg =  convertToGray(img, ops, log);
		}
		else {
			grayImg = img;
		}

		FloatType min = grayImg.firstElement().createVariable();
		FloatType max = grayImg.firstElement().createVariable();
		ComputeMinMax.computeMinMax(grayImg, min, max);
		if (max.getRealFloat() > 1) {
			log.info("normalizing image between 0-1");
			Img<FloatType> res = ops.create().img(grayImg);
			LoopBuilder.setImages(grayImg, res).multiThreaded().forEachPixel(
					(x, y) -> y.setReal(x.getRealFloat() / max.getRealFloat()));
			log.info("Done normalizing image between 0-1");
			return res;
		}
		
		return grayImg;
	}

	public static ImgPlus<UnsignedByteType> postProcessImg(Img <FloatType> img, boolean normalize, OpService ops, LogService log) {
		log.info("post process EVM video");

		if (normalize) {
			// normalize values between 0-1
			LoopBuilder.setImages(img).forEachPixel((s) -> {
				if (s.getRealFloat() < 0) s.setReal(0);
				else if ((s.getRealFloat() > 1)) s.setReal(1);
			});
		}

		Img<UnsignedByteType> uByteVid = ops.create().img(img, new UnsignedByteType());
		LoopBuilder.setImages(img, uByteVid).multiThreaded().forEachPixel(
				(x, y) -> y.set((int)(x.getRealFloat()*255f)));
		ImgPlus<UnsignedByteType> out = ImgPlus.wrap(uByteVid);

		// set axis to be time axis
		final Axis ax = out.axis(2);
		if ( ax instanceof CalibratedAxis ) {
            final AxisType axType = ((CalibratedAxis)ax).type();
            if (axType.toString() == Axes.unknown().toString() || axType.toString() == Axes.CHANNEL.toString()) {
            	((CalibratedAxis)ax).setType(Axes.TIME);
            }
		}

		log.info("Done post process EVM video");
		return out;
	}
}
