package leamanlab.tau;

import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.real.FloatType;

public class PhaseDifferenceAndAmplitude {
	private Img<FloatType> phaseDiffCos = null;
	private Img<FloatType> phaseDiffSin = null;
	private Img<FloatType> amplitude = null;
	
	public PhaseDifferenceAndAmplitude() {  // constructor
		return;
	}
	
	public Img<FloatType> getAmplitude() {
		return this.amplitude;
	}
	
	public Img<FloatType> getPhaseDiffCos() {
		return this.phaseDiffCos;
	}
	
	public Img<FloatType> getPhaseDiffSin() {
		return this.phaseDiffSin;
	}
	
	public void compute(Img<FloatType> currReal, Img<FloatType> currX, Img<FloatType> currY, 
			Img<FloatType> prevReal, Img<FloatType> prevX, Img<FloatType> prevY, OpService ops) {
		Img<FloatType> qConjProdReal = ops.create().img(currReal);
		Img<FloatType> qConjProdX = ops.create().img(currReal);
		Img<FloatType> qConjProdY = ops.create().img(currReal);
		
		// qConjProdReal = currReal*prevReal + currX*prevX + currX.*prevX
		LoopBuilder.setImages(currReal, prevReal, currX, prevX, qConjProdReal).multiThreaded().forEachPixel(
				(cr, pr, cx, px, o) -> o.setReal(
						cr.getRealFloat() * pr.getRealFloat() + cx.getRealFloat() * px.getRealFloat()));
		LoopBuilder.setImages(currY, prevY, qConjProdReal).forEachPixel(
				(cy, py, o) -> o.setReal(
						cy.getRealFloat() * py.getRealFloat() + o.getRealFloat()));
		
		// qConjProdX = -currReal*prevX + prevReal*currX
		LoopBuilder.setImages(currReal, prevX, prevReal, currX, qConjProdX).multiThreaded().forEachPixel(
				(cr, px, pr, cx, o) -> o.setReal(
						-cr.getRealFloat() * px.getRealFloat() + cx.getRealFloat() * pr.getRealFloat()));
		
		// qConjProdY = -currReal*prevY + prevReal*currY
		LoopBuilder.setImages(currReal, prevY, prevReal, currY, qConjProdY).multiThreaded().forEachPixel(
				(cr, py, pr, cy, o) -> o.setReal(
						-cr.getRealFloat() * py.getRealFloat() + cy.getRealFloat() * pr.getRealFloat()));
		
		Img<FloatType> qConjProdAmplitude = ops.create().img(currReal);
		Img<FloatType> phaseDifference = ops.create().img(currReal);
		Img<FloatType> cosOrientation = ops.create().img(currReal);
		Img<FloatType> sinOrientation = ops.create().img(currReal);
		
		// qConjProdAmplitude = sqrt(qConjProdReal^2 + qConjProdX^2 + qConjProdY^2) 
		LoopBuilder.setImages(qConjProdReal, qConjProdX, qConjProdY, qConjProdAmplitude).multiThreaded().forEachPixel(
				(pr, px, py, o) -> o.setReal(
						Math.sqrt(Math.pow(pr.getRealFloat(), 2) + Math.pow(px.getRealFloat(), 2) + Math.pow(py.getRealFloat(), 2))));
		
		// phaseDifference = acos(qConjProdReal / (qConjProdAmplitude + epsilon))
		LoopBuilder.setImages(qConjProdReal, qConjProdAmplitude, phaseDifference).multiThreaded().forEachPixel(
				(pr, pa, o) -> o.setReal(
						Math.acos(pr.getRealFloat() / (pa.getRealFloat() + 0.000001f))));

		// cosOrientation = qConjProdX / sqrt(qConjProdX^2 + qConjProdY^2 + epsilon)
		LoopBuilder.setImages(qConjProdX, qConjProdY, cosOrientation).multiThreaded().forEachPixel(
				(px, py, o) -> o.setReal(
						px.getRealFloat() / Math.sqrt(Math.pow(px.getRealFloat(), 2) + Math.pow(py.getRealFloat(), 2) + 0.000001f)));
		
		// sinOrientation = qConjProdY / sqrt(qConjProdX^2 + qConjProdY^2 + epsilon)
		LoopBuilder.setImages(qConjProdX, qConjProdY, sinOrientation).multiThreaded().forEachPixel(
				(px, py, o) -> o.setReal(
						py.getRealFloat() / Math.sqrt(Math.pow(px.getRealFloat(), 2) + Math.pow(py.getRealFloat(), 2) + 0.000001f)));
		
		this.phaseDiffCos = ops.create().img(currReal);
		this.phaseDiffSin = ops.create().img(currReal);
		this.amplitude = ops.create().img(currReal);
		
		// phaseDiffCos = phaseDifference * cosOrientation
		LoopBuilder.setImages(phaseDifference, cosOrientation, this.phaseDiffCos).multiThreaded().forEachPixel(
				(pd, co, o) -> o.setReal(pd.getRealFloat() * co.getRealFloat()));
		
		// phaseDiffSin = phaseDifference * sinOrientation
		LoopBuilder.setImages(phaseDifference, sinOrientation, this.phaseDiffSin).multiThreaded().forEachPixel(
				(pd, so, o) -> o.setReal(pd.getRealFloat() * so.getRealFloat()));
		
		// amplitude = sqrt(qConjProdAmplitude)
		LoopBuilder.setImages(qConjProdAmplitude, this.amplitude).multiThreaded().forEachPixel(
				(pa, o) -> o.setReal(Math.sqrt(pa.getRealFloat())));
	}
}
