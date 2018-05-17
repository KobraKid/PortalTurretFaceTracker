package application;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.Mat;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/**
 * This class contains various utilities that are useful for image conversions
 * and thread handling.
 * 
 * @author Michael Huyler
 *
 */
public class Utils {

	/**
	 * Convert a {@link Mat} into an {@link Image} for use in an
	 * {@link ImageView}.
	 * 
	 * @param frame
	 *            The <code>Mat</code> to be converted.
	 * @return An <code>Image</code> usable in an <code>ImageView</code>.
	 */
	public static Image mat2Image(Mat frame) {
		try {
			return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
		} catch (Exception e) {
			System.err.println("Couldn't convert image");
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Convert a {@link Mat} into a {@link BufferedImage}.
	 * 
	 * @param original
	 *            The <code>Mat</code> to be converted.
	 * @return A <code>BufferedImage</code> representing the original
	 *         <code>Mat</code>.
	 */
	private static BufferedImage matToBufferedImage(Mat original) {
		BufferedImage image = null;
		int width = original.width(), height = original.height(), channels = original.channels();
		byte[] sourcePixels = new byte[width * height * channels];
		original.get(0, 0, sourcePixels);

		if (original.channels() > 1) {
			image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		} else {
			image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		}

		final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);
		return image;
	}

	/**
	 * Safely updates {@link ObjectProperty} on the correct {@link Thread}.
	 * 
	 * @param property
	 *            The JavaFX property that needs to be updated.
	 * @param value
	 *            The value that the property should be set to.
	 */
	public static <T> void onFXThread(final ObjectProperty<T> property, final T value) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				property.setValue(value);
			}
		});
	}

}
