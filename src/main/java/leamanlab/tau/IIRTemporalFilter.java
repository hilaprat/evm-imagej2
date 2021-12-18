package leamanlab.tau;

import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.real.FloatType;

public class IIRTemporalFilter {
	private Img<FloatType> phaseFiltered = null;
	private Img<FloatType> register0;
	private Img<FloatType> register1;
	
	public IIRTemporalFilter(Img<FloatType> register0, Img<FloatType> register1) {  // constructor
		this.register0 = register0;
		this.register1 = register1;
	}
	
	public Img<FloatType> getPhaseFiltered() {
		return this.phaseFiltered;
	}
	
	public Img<FloatType> getRegister0() {
		return this.register0;
	}
	
	public Img<FloatType> getRegister1() {
		return this.register1;
	}
	
	public void compute(double[] B, double[] A, Img<FloatType> phase, OpService ops) {
		this.phaseFiltered = ops.create().img(phase);
		
		// phaseFiltered = B[0] * phase + register0
		LoopBuilder.setImages(phase, this.register0, this.phaseFiltered).multiThreaded().forEachPixel(
				(p, r, o) -> o.setReal(B[0] * p.getRealFloat() + r.getRealFloat()));
		
		// register0 = B[1] * phase + register1 - A[1] * phaseFiltered
		LoopBuilder.setImages(phase, this.register1, this.phaseFiltered, this.register0).multiThreaded().forEachPixel(
				(p, r1, pf, o) -> o.setReal(B[1] * p.getRealFloat() + r1.getRealFloat() - pf.getRealFloat() * A[1]));
		
		// register1 = B[2] * phase - A[2] * phaseFiltered
		LoopBuilder.setImages(phase, this.phaseFiltered, this.register1).multiThreaded().forEachPixel(
				(p, pf, o) -> o.setReal(B[2] * p.getRealFloat() + - pf.getRealFloat() * A[2]));
	}
}
