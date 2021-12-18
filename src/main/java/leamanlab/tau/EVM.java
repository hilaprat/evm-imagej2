package leamanlab.tau;

import java.util.ArrayList;

import org.scijava.log.LogService;

import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import uk.me.berndporr.iirj.Biquad;
import net.imagej.ops.OpService;

public class EVM {
	public static Img<FloatType> runEVM(Img<FloatType> vid, final float ampFactor, final double centerFreq, 
			final double width, final float sampleFreq, OpService ops, LogService log) {
		Img<FloatType> ampVideo = ops.create().img(vid);
		int frameNum = 0;
		int level;

		Biquad butterCoeffs = utils.calcButterCoeffs(centerFreq, width, sampleFreq);
		double B[] = { butterCoeffs.getB0(), butterCoeffs.getB1(), butterCoeffs.getB2() };
		double A[] = { butterCoeffs.getA0(), butterCoeffs.getA1(), butterCoeffs.getA2() };

		IntervalView<FloatType> prevFrame = Views.hyperSlice(vid, 2, frameNum);
		IntervalView<FloatType> ampFrame = Views.hyperSlice(ampVideo, 2, frameNum);
		Img<FloatType> currSlice = ops.create()
				.img(new FinalDimensions(new long[] { vid.dimension(0), vid.dimension(1) }), new FloatType());
		LoopBuilder.setImages(prevFrame, ampFrame).multiThreaded().forEachPixel((s, t) -> t.set(s));
		LoopBuilder.setImages(prevFrame, currSlice).multiThreaded().forEachPixel((s, t) -> t.set(s));

		rieszPyramid prevPyr = new rieszPyramid();
		prevPyr.computePyramid(currSlice, ops);
		ArrayList<Img<FloatType>> prevPyramid = prevPyr.getPyramid();

		int numLevels = prevPyramid.size() - 1;
		ArrayList<Img<FloatType>> phaseCos = rieszPyramid.initZeros(numLevels, prevPyramid, ops);
		ArrayList<Img<FloatType>> phaseSin = rieszPyramid.initZeros(numLevels, prevPyramid, ops);
		ArrayList<Img<FloatType>> register0Cos = rieszPyramid.initZeros(numLevels, prevPyramid, ops);
		ArrayList<Img<FloatType>> register1Cos = rieszPyramid.initZeros(numLevels, prevPyramid, ops);
		ArrayList<Img<FloatType>> register0Sin = rieszPyramid.initZeros(numLevels, prevPyramid, ops);
		ArrayList<Img<FloatType>> register1Sin = rieszPyramid.initZeros(numLevels, prevPyramid, ops);
		ArrayList<Img<FloatType>> motionMagnifiedPyramid = rieszPyramid.initZeros(prevPyramid.size(), prevPyramid, ops);

		IntervalView<FloatType> currFrame;
		for (frameNum = 1; frameNum < vid.dimension(2); frameNum++) {
			log.info(String.format("frame: %d/%d", frameNum, vid.dimension(2)));

			// get current frame
			currFrame = Views.hyperSlice(vid, 2, frameNum);
			currSlice = ops.create().img(new FinalDimensions(new long[] { vid.dimension(0), vid.dimension(1) }),
					new FloatType());
			LoopBuilder.setImages(currFrame, currSlice).multiThreaded().forEachPixel((s, t) -> t.set(s));

			// compute current frame laplace and riesz pyramid
			rieszPyramid currPyr = new rieszPyramid();
			currPyr.computePyramid(currSlice, ops);
			
			for (level = 0; level < numLevels; level++) {
				// ompute quaternionic phase difference between current Riesz pyramid
				PhaseDifferenceAndAmplitude pdaa = new PhaseDifferenceAndAmplitude();
				pdaa.compute(currPyr.getPyramid().get(level), currPyr.getRieszX().get(level),
						currPyr.getRieszY().get(level), prevPyr.getPyramid().get(level), prevPyr.getRieszX().get(level),
						prevPyr.getRieszY().get(level), ops);

				// Adds the quaternionic phase difference to the current value of the
				// quaternionic phase
				// Computing the current value of the phase in this way is equivalent to phase
				// unwrapping
				// phaseCos[level] = phaseCos[level] + phaseDiffCos
				// phaseSin[level] = phaseSin[level] + phaseDiffSin
				Img<FloatType> currPhaseCos = phaseCos.get(level);
				Img<FloatType> currPhaseSin = phaseSin.get(level);
				LoopBuilder.setImages(currPhaseCos, pdaa.getPhaseDiffCos())
						.forEachPixel((cpc, pdc) -> cpc.setReal(pdc.getRealFloat() + cpc.getRealFloat()));
				LoopBuilder.setImages(currPhaseSin, pdaa.getPhaseDiffSin())
						.forEachPixel((cps, pds) -> cps.setReal(cps.getRealFloat() + pds.getRealFloat()));

				// Temporally filter the quaternionic phase using current value and stored
				// information
				IIRTemporalFilter iirtCos = new IIRTemporalFilter(register0Cos.get(level), register1Cos.get(level));
				IIRTemporalFilter iirtSin = new IIRTemporalFilter(register0Sin.get(level), register1Sin.get(level));
				iirtCos.compute(B, A, currPhaseCos, ops);
				iirtSin.compute(B, A, currPhaseSin, ops);

				// Spatial blur the temporally filtered quaternionic phase signals
				// his is not an optional step. In addition to denoising, it smoothes out errors
				// made during the various approximations
				Img<FloatType> phaseFilteredCos = utils.amplitudeWeightedBlur(iirtCos.getPhaseFiltered(),
						pdaa.getAmplitude(), ops);
				Img<FloatType> phaseFilteredSin = utils.amplitudeWeightedBlur(iirtSin.getPhaseFiltered(),
						pdaa.getAmplitude(), ops);

				// The motion magnified pyramid is computed by phase shifting the input pyramid
				// by the spatio-temporally filtered quaternionic phase and taking the real part
				Img<FloatType> phaseMagnifiedFilteredCos = ops.create().img(phaseFilteredCos);
				Img<FloatType> phaseMagnifiedFilteredSin = ops.create().img(phaseFilteredSin);
				LoopBuilder.setImages(phaseFilteredCos, phaseMagnifiedFilteredCos)
						.multiThreaded().forEachPixel((s, t) -> t.setReal(ampFactor * s.getRealFloat()));
				LoopBuilder.setImages(phaseFilteredSin, phaseMagnifiedFilteredSin)
						.multiThreaded().forEachPixel((s, t) -> t.setReal(ampFactor * s.getRealFloat()));

				Img<FloatType> currMagnified = utils.phaseShiftCoefficientRealPart(currPyr.getPyramid().get(level),
						currPyr.getRieszX().get(level), currPyr.getRieszY().get(level), phaseMagnifiedFilteredCos,
						phaseMagnifiedFilteredSin, ops);
				motionMagnifiedPyramid.set(level, currMagnified);
			}
			motionMagnifiedPyramid.set(numLevels, currPyr.getPyramid().get(numLevels).copy());

			ampFrame = Views.hyperSlice(ampVideo, 2, frameNum);
			Img <FloatType> reconstructed = rieszPyramid.reconstructPyramid(motionMagnifiedPyramid, ops);
			LoopBuilder.setImages(reconstructed, ampFrame).multiThreaded().forEachPixel((s, t) -> t.set(s));

			prevPyr = currPyr;
			System.gc();
		}
		
		return ampVideo;
	}
}
