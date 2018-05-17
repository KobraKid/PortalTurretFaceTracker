package arduino;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

/**
 * The class is used to interface with the Arduino. The Arduino has a sketch
 * loaded onto it where it listens to the Serial Port for input and writes to
 * the Serial Port for feedback. This class handles writing to the port, and
 * reading from it.
 * 
 * @author Michael
 *
 */
public class ArduinoCommunicator {

	private final static int BAUD_RATE = 9600;
	private InputStream in;
	private OutputStream out;

	private static SerialReader reader;
	private static SerialWriter writer;

	public ArduinoCommunicator() { // String port
//		try {
//			connect(port);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	/**
	 * The x and y coordinates within the webcam's frame, formatted such that
	 * the Arduino simply has to move its servos to the coordinates in order to
	 * be centered on a face.
	 */
	public void sendCoordinates(double xPos, double yPos) {
		writer.write((int) xPos / 10, (int) yPos / 10);
	}

	/**
	 * Open a connection with the Arduino.
	 * 
	 * @param port
	 *            The name of the Serial Port the Arduino is on
	 * @return True if the connection is a success, otherwise false
	 * @throws Exception
	 */
	public boolean connect(String port) throws Exception {
		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(port);
		if (portIdentifier.isCurrentlyOwned()) {
			System.err.println("ERROR: PORT IS CURRENTLY IN USE.");
			return false;
		} else {
			CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);
			if (commPort instanceof SerialPort) {
				SerialPort serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(BAUD_RATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
						SerialPort.PARITY_NONE);

				this.in = serialPort.getInputStream();
				this.out = serialPort.getOutputStream();

				reader = new SerialReader(this.in);
				writer = new SerialWriter(this.out);

				(new Thread(reader)).start();
				(new Thread(writer)).start();

			} else {
				System.err.println("ERROR: NOT A SERIAL PORT");
				return false;
			}
		}
		return true;
	}

	/**
	 * Read from the Serial Port
	 * 
	 * @author Michael
	 *
	 */
	public static class SerialReader implements Runnable {
		InputStream in;

		public SerialReader(InputStream in) {
			this.in = in;
		}

		public void run() {
			byte[] buffer = new byte[1024];
			int len = -1;
			try {
				while (true) {
					while ((len = this.in.read(buffer)) > -1) {
						String read = new String(buffer, 0, len);
						if (read.trim().length() > 0)
							System.out.print(read);
					}
					System.out.println();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Write to the Serial Port
	 * 
	 * @author Michael
	 *
	 */
	public static class SerialWriter implements Runnable {
		OutputStream out;

		public SerialWriter(OutputStream out) {
			this.out = out;
		}

		/**
		 * The method actually used to write the coordinates to the Serial port
		 */
		public void write(int x, int y) {
			try {
				System.out.println("WRITE: " + String.format("X%dY%d", x, y));
				this.out.write(String.format("X%dY%d", x, y).getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Required as a Runnable
		 */
		public void run() {
		}
	}

}
