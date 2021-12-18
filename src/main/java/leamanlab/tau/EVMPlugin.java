/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package leamanlab.tau;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ItemIO;
import org.scijava.log.LogService;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * A plugin for running Eulerian Video Magnification
 *
 * @author Hila Prat
 */
@Plugin(type = Command.class, headless = true, menuPath = "Plugins>Video Magnification>EVM")
public class EVMPlugin<T extends RealType<T> & NativeType<T>> implements Command {
	@Parameter
	private OpService ops;

	@Parameter
    private UIService ui;

	@Parameter
	private LogService log;

	@Parameter(label="Input video to amplify")
    private Dataset inputVideo;

	@Parameter(label="Video sample rate [frames/sec]", required=true)
	private float sampleFreq;

	@Parameter(label="Dominant frequency to amplify", required=true)
	private float dominantFreq;

	@Parameter(label="Amplification Factor", required=true)
	private float ampFactor = 10;

	@Parameter(label="Should crop output values between 0-255", required=true)
	private boolean normalize = true;

	@Parameter(label="Amplified Video", type=ItemIO.OUTPUT)
	private ImgPlus<UnsignedByteType> amplifiedVideo;


	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads an
	 * image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) throws Exception {
		// start ImageJ
		final ImageJ ij = new ImageJ();
		ij.launch(args);
		
		File importVideo = ij.ui().chooseFile(null, "open");
		final String imagePath = importVideo.getAbsolutePath();
		
		Dataset image = ij.scifio().datasetIO().open(imagePath);
		ij.ui().show(image);

        // invoke the plugin
        ij.command().run(EVMPlugin.class, true);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void run() {
		final Img<FloatType> image = ops.convert().float32((Img<T>)inputVideo.getImgPlus().getImg());
		Img <FloatType> img = utils.preProcessImg(image, ops, log);

		Img <FloatType> ampVideo = EVM.runEVM(img, ampFactor, dominantFreq, 0.1 * dominantFreq, sampleFreq, ops, log);

		amplifiedVideo = utils.postProcessImg(ampVideo, normalize, ops, log);
		log.info("EVM DONE");
	}
}
