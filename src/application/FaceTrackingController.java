package application;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.videoio.VideoCapture;

import arduino.ArduinoCommunicator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import sphinx.VoiceCommandController;

/**
 * This class controls the <code>FaceTrackingGUI.fxml</code> file.
 * <p>
 * <h3>The tasks this class performs are:</h3>
 * <ul>
 * <li>Handling button presses</li>
 * <li>Updating the central ImageView</li>
 * <li>Playing sounds</li>
 * <li>Detecting and highlighting faces using a connected webcam</li>
 * <li>Determining coordinates of faces for use in face tracking</li>
 * </ul>
 * 
 * @author Michael Huyler
 *
 */
public class FaceTrackingController {

	@FXML
	private Button cameraButton;
	@FXML
	private TextField x;
	@FXML
	private TextField y;
	@FXML
	private ComboBox<String> soundComboBox;
	@FXML
	private Button playButton;
	@FXML
	private ProgressBar progressBar;
	@FXML
	private Slider volumeSlider;
	@FXML
	private ImageView currentFrame;
	@FXML
	private HBox coordBox;
	@FXML
	private ToggleButton noFace;
	@FXML
	private ToggleButton haarFace;
	@FXML
	private ToggleButton lbpFace;
	@FXML
	private Label coordLabel;
	@FXML
	private ImageView minimize;
	@FXML
	private ImageView maximize;
	@FXML
	private ImageView close;
	@FXML
	private ToolBar titleBar;
	@FXML
	private TextArea logBox;
	@FXML
	private Label serialPort;

	// The stage
	private static Stage stage;
	// Stage offsets
	private double xOffset = 0, yOffset = 0;
	// The path to the sound effect .wav files
	private final String soundPath = ClassLoader.getSystemClassLoader().getResource(".").getPath() + "sounds";
	// Which camera to use: 0 = built-in webcam, 1 = 1st external webcam
	protected static int cameraID = 1;
	// To keep track of whether the camera is on or off
	private boolean cameraActive = false;
	// The connection to the webcam
	private VideoCapture capture;
	// Responsible for grabbing frames
	private ScheduledExecutorService timer;
	// Time in millis between each frame grab
	private final int interval = 33;
	// [-80.0, 6.0206]
	@SuppressWarnings("unused")
	private final float maxVol = 6.0206f, minVol = -80.0f, range = 86.0206f;
	// This cascade classifier is trained to detect faces
	private CascadeClassifier faceCascade;
	// The paths to the classifiers
	private final String haarClassifierPath = ClassLoader.getSystemClassLoader().getResource(".").getPath().substring(1)
			+ "haarcascades/haarcascade_frontalface_alt.xml";
	private final String lbpClassifierPath = ClassLoader.getSystemClassLoader().getResource(".").getPath().substring(1)
			+ "lbpcascades/lbpcascade_frontalface.xml";
	// Used in calculating how big a face needs to be to be detected
	private int absoluteFaceSize;
	// The minimum portion of the frame a face must take up to be detected
	private float facePortion = 0.2f;
	// Face Detection types
	private final int OFF = 0, HAAR = 1, LBP = 2;
	// The sound clip
	private Clip clip;
	// Used to update the progress bar
	private Timer progressUpdateTimer;
	// Used to decide if face detection should occur
	private boolean doFaceDetect = false;
	// Responsible for handling all voice commands
	private VoiceCommandController vcc;
	// The voice command thread
	private Thread voiceCommands;
	// Command constants
	private final String COMMAND_HAAR = "COMMAND SEARCH ONE";
	private final String COMMAND_LBP = "COMMAND SEARCH TWO";
	private final String COMMAND_RETIRE = "COMMAND RETIRE";
	private final String COMMAND_ACTIVATE = "COMMAND ACTIVATE";
	private final String COMMAND_SHUTDOWN = "COMMAND SHUTDOWN";
	private final String COMMAND_AUTOPILOT = "COMMAND TOGGLE AUTOPILOT";

	// Sounds for when the Turret finds someone
	final String activeSounds = "turret_active_";
	final int activeSoundsInt = 8;
	// 'Autopilot' ambient search noises
	final String autoSearchSounds = "turret_autosearch_";
	final int autoSearchSoundsInt = 6;
	// Sounds for when the Turret begins shooting
	final String deploySounds = "turret_deploy_";
	final int deploySoundsInt = 6;
	// Sounds for when the Turret is no longer operable
	final String disabledSounds = "turret_disabled_";
	final int disabledSoundsInt = 8;
	// Sounds for when the Turret fires
	final String fireSounds = "turret_fire_4x_0";
	final int fireSoundsInt = 3;
	// Sounds for when the Turret stops searching
	final String retireSounds = "turret_retire_";
	final int retireSoundsInt = 7;
	// Sounds for when the Turret loses a target
	final String searchSounds = "turret_search_";
	final int searchSoundsInt = 4;

	// Booleans used to keep track of searching information
	private boolean autopilot = false;
	private boolean found = false, previous = false;
	// Used to interface with the Arduino
	private ArduinoCommunicator arduino;
	// The Serial Port the Arduino is connected to
	private final String PORT = "COM1";
	// Previous coordinates
	private double prevX = -1, prevY = -1;
	private final int TOLERANCE = 5;

	/**
	 * Changes which type of face detection should be used (if any).
	 * 
	 * @param e
	 *            The ActionEvent triggered by a button press.
	 */
	@FXML
	protected void toggleFaceDetection(ActionEvent e) {
		System.out.println("INFO: TOGGLING FACE TRACKING PROCESSES.");
		String source = ((ToggleButton) e.getSource()).getId();
		if (source.equals("noFace")) {
			this.processCommand(this.COMMAND_RETIRE);
		} else if (source.equals("haarFace")) {
			this.processCommand(this.COMMAND_HAAR);
		} else if (source.equals("lbpFace")) {
			this.processCommand(this.COMMAND_LBP);
		}
	}

	private void toggleFaceDetection(int type) {
		boolean wasRecording = cameraActive;
		if (wasRecording)
			stopAcquisition();

		switch (type) {
		case OFF:
			doFaceDetect = false;
			noFace.setSelected(true);
			haarFace.setSelected(false);
			lbpFace.setSelected(false);
			this.coordBox.setDisable(true);
			this.coordLabel.setDisable(true);
			this.x.setText("--");
			this.y.setText("--");
			break;
		case HAAR:
			doFaceDetect = true;
			noFace.setSelected(false);
			haarFace.setSelected(true);
			lbpFace.setSelected(false);
			this.coordBox.setDisable(false);
			this.coordLabel.setDisable(false);
			this.faceCascade.load(haarClassifierPath);
			break;
		case LBP:
			doFaceDetect = true;
			noFace.setSelected(false);
			haarFace.setSelected(false);
			lbpFace.setSelected(true);
			this.coordBox.setDisable(false);
			this.coordLabel.setDisable(false);
			this.faceCascade.load(lbpClassifierPath);
			break;
		}
		if (wasRecording)
			startCamera();
	}

	/**
	 * Toggles the camera on and off and begins scheduling of the frame grabber
	 * timer.
	 */
	@FXML
	protected void startCamera() {
		if (!this.cameraActive) {
			System.out.println("INFO: ATTEMPTING TO CONNECT TO CAMERA.");
			this.capture.open(cameraID);
			if (this.capture.isOpened()) {
				this.cameraActive = true;
				Runnable frameGrabber = new Runnable() {
					@Override
					public void run() {
						Mat frame = grabFrame();
						if (frame.empty())
							return;
						Image imageToShow = Utils.mat2Image(frame);
						updateImageView(currentFrame, imageToShow);
					}
				};
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, interval, TimeUnit.MILLISECONDS);
				this.cameraButton.setText("Stop Camera");
			} else {
				System.err.println("ERROR: COULD NOT CONNECT TO CAMERA.");
			}
		} else {
			this.stopAcquisition();
		}
	}

	/**
	 * Responsible for grabbing frames from the video capture.
	 * 
	 * @return The current frame the camera sees.
	 */
	private Mat grabFrame() {
		Mat frame = new Mat();
		if (this.capture.isOpened()) {
			this.capture.read(frame);
			if (!frame.empty() && doFaceDetect) {
				this.detectAndDisplay(frame);
			}
		}
		return frame;
	}

	/**
	 * Draws a grid over the screen
	 * 
	 */
	private void overlayFrame(Mat frame) {
		int spacing = 10;
		int width = frame.cols() / spacing;
		int height = frame.rows() / spacing;

		for (int x = 0; x < width - 1; x++) {
			Imgproc.line(frame, new Point(x * spacing, 0), new Point(x * spacing, height * spacing),
					new Scalar(64, 64, 64, 64));
		}
		for (int y = 0; y < height - 1; y++) {
			Imgproc.line(frame, new Point(0, y * spacing), new Point(width * spacing, y * spacing),
					new Scalar(64, 64, 64, 64));
		}
	}

	/**
	 * Detects faces in a frame and highlights them.
	 * 
	 * @param frame
	 *            The <code>Mat</code> from which faces are extracted.
	 */
	private void detectAndDisplay(Mat frame) {
		// Get variables ready for face detection
		MatOfRect faces = new MatOfRect();
		Mat grayFrame = new Mat();

		// Color to gray scale
		Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
		// make face detecting easier
		Imgproc.equalizeHist(grayFrame, grayFrame);

		// Compute the face size needed for detection
		if (this.absoluteFaceSize == 0) {
			int height = grayFrame.rows();
			if (Math.round(facePortion * height) > 0) {
				this.absoluteFaceSize = Math.round(facePortion * height);
			}
		}

		// Find all faces in the frame
		this.faceCascade.detectMultiScale(grayFrame, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
				new Size(this.absoluteFaceSize, this.absoluteFaceSize), new Size());

		// Store the rectangle outlines of each face into this array
		final Rect[] facesArray = faces.toArray();

		// Draw a rectangle around each face
		for (int i = 0; i < facesArray.length; i++) {
			Point tl = new Point(10 * ((int) facesArray[i].tl().x / 10), 10 * ((int) facesArray[i].tl().y / 10));
			Point br = new Point(10 * ((int) facesArray[i].br().x / 10), 10 * ((int) facesArray[i].br().y / 10));
			Imgproc.rectangle(frame, tl, br, new Scalar(0, 0, 255, 255), 2);
		}

		// Store the coordinates of the center of the first detected face (if
		// any) in two text fields
		for (int i = 0; i < facesArray.length; i++) {
			Rect face = facesArray[i];

			Point tl = face.tl();
			Point br = face.br();

			double xPos = tl.x + ((br.x - tl.x) / 2);
			double yPos = tl.y + ((br.y - tl.y) / 2);

			this.x.setText("" + (int) xPos / 10);
			this.y.setText("" + (int) yPos / 10);

			// Focus on the center of the face
			Imgproc.circle(frame, new Point(xPos, yPos), 1, new Scalar(0, 0, 255, 255), 2);

			// Send new coordinates to the Arduino
			if (Math.abs(xPos - prevX) > TOLERANCE || Math.abs(yPos - prevY) > TOLERANCE) {
				this.arduino.sendCoordinates(xPos, yPos);
				prevX = xPos;
				prevY = yPos;
			}
		}

		if (facesArray.length < 1 && (prevX != -1 || prevY != -1)) {
			prevX = -1;
			prevY = -1;
			this.arduino.sendCoordinates(-1, -1);
		}

		// If there has been a long enough delay between clips, play another
		// clip if a target was found/lost
		if (this.autopilot && this.clip == null || !this.clip.isActive()) {
			this.previous = this.found;
			this.found = facesArray.length > 0;
			if (this.found != this.previous) {
				System.out.println("INFO: FACE " + (this.found ? "DETECTED." : "LOST."));
				if (this.found)
					this.playClip(this.activeSounds + (new Random().nextInt(this.activeSoundsInt) + 1));
				else
					this.playClip(this.searchSounds + (new Random().nextInt(this.searchSoundsInt) + 1));
			}
		}
		this.overlayFrame(frame);
	}

	/**
	 * Updates a value on the correct thread.
	 * 
	 * @param view
	 *            The ImageView which needs to be updated.
	 * @param image
	 *            The Image that should be shown on the ImageView.
	 */
	private void updateImageView(ImageView view, Image image) {
		Utils.onFXThread(view.imageProperty(), image);
	}

	/**
	 * Handles playing .wav files when the play button is pressed
	 */
	@FXML
	protected void playSound() {
		String sound = soundComboBox.getValue();
		playClip(sound);
	}

	private void playClip(String sound) {
		// If there is already something playing, stop it first
		if (this.clip != null && this.clip.isActive()) {
			this.clip.stop();
			if (this.progressUpdateTimer != null)
				this.progressUpdateTimer.cancel();
		}

		if (sound != null)
			try {
				// Load the sound file
				this.clip = AudioSystem.getClip();
				InputStream is = new BufferedInputStream(
						ClassLoader.getSystemClassLoader().getResourceAsStream("sounds/" + sound + ".wav"));
				AudioInputStream inputStream = AudioSystem.getAudioInputStream(is);
				this.clip.open(inputStream);

				// Set volume level
				try {
					FloatControl gainControl = (FloatControl) this.clip.getControl(FloatControl.Type.MASTER_GAIN);
					gainControl.setValue(((float) (this.volumeSlider.getValue() / 100.0f) * this.range) + this.minVol);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}

				// Length of the sound file
				final long frames = inputStream.getFrameLength();

				// This will update the progress bar to reflect the sound file
				this.progressUpdateTimer = new Timer();
				this.progressUpdateTimer.scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						long pos = clip.getLongFramePosition();
						progressBar.setProgress((pos * 100.0f / frames) / 100.0d);
					}
				}, 0, 10);

				// Stop the timer when the clip has finished playing
				this.clip.addLineListener(new LineListener() {
					@Override
					public void update(LineEvent e) {
						if (e.getType() == LineEvent.Type.STOP) {
							progressUpdateTimer.cancel();
							progressBar.setProgress(0.0d);
						}
					}
				});

				System.out.println("INFO: PLAYING " + sound.toUpperCase() + ".");

				// Finally, start the actual sound
				this.clip.start();
			} catch (Exception e) {
				System.err.println("FAILED TO PLAY CLIP");
				e.printStackTrace();
			}
	}

	/**
	 * Processes voice commands
	 */
	public void processCommand(String command) {
		switch (command) {
		case COMMAND_AUTOPILOT:
			this.autopilot = !this.autopilot;
			System.out.println("INFO: TOGGLING AUTOPILOT " + (this.autopilot ? "ON." : "OFF."));
			break;
		case COMMAND_ACTIVATE:
			System.out.println("INFO: ACTIVATING.");
			playClip(this.autoSearchSounds + (new Random().nextInt(this.autoSearchSoundsInt) + 1));

			if (!this.capture.isOpened())
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						startCamera();
					}
				});
			break;
		case COMMAND_SHUTDOWN:
			System.out.println("INFO: SHUTTING DOWN.");
			playClip(this.disabledSounds + (new Random().nextInt(this.disabledSoundsInt) + 1));

			if (this.capture.isOpened())
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						stopAcquisition();
					}
				});
			break;
		case COMMAND_HAAR:
			System.out.println("INFO: BEGINNING FACE TRACKING PROCESSES.");
			playClip(this.autoSearchSounds + (new Random().nextInt(this.autoSearchSoundsInt) + 1));

			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					toggleFaceDetection(HAAR);
				}
			});
			break;
		case COMMAND_LBP:
			System.out.println("INFO: BEGINNING FACE TRACKING PROCESSES.");
			playClip(this.autoSearchSounds + (new Random().nextInt(this.autoSearchSoundsInt) + 1));

			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					toggleFaceDetection(LBP);
				}
			});
			break;
		case COMMAND_RETIRE:
			System.out.println("INFO: HALTING FACE TRACKING PROCESSES.");
			playClip(this.retireSounds + (new Random().nextInt(this.retireSoundsInt) + 1));

			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					toggleFaceDetection(OFF);
				}
			});
			break;
		default:
			break;
		}
	}

	/**
	 * Handles initialization of the scene. Only called once when the program
	 * starts up.
	 */
	protected void init(Stage primaryStage) {
		// Call the ClassLoader
		FaceTrackingController.class.getClassLoader();

		// Initialize variables
		stage = primaryStage;
		this.capture = new VideoCapture();
		this.faceCascade = new CascadeClassifier();
		this.absoluteFaceSize = 0;
		this.vcc = new VoiceCommandController();
		this.vcc.addCommandListener(this);
		this.titleBar.setOnMousePressed(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				xOffset = FaceTrackingController.stage.getX() - event.getScreenX();
				yOffset = FaceTrackingController.stage.getY() - event.getScreenY();
			}
		});
		this.titleBar.setOnMouseDragged(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				FaceTrackingController.stage.setX(event.getScreenX() + xOffset);
				FaceTrackingController.stage.setY(event.getScreenY() + yOffset);
			}
		});
		Timer logUpdater = new Timer();
		final int characters = logBox.getPrefRowCount() * logBox.getPrefColumnCount();
		logUpdater.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (Main.streamLog != null) {
					String log = Main.streamLog.toString();

					if (log.length() > characters) {
						log = log.substring(characters);
					}

					if (!logBox.getText().equals(log)) {
						logBox.setText(log);
						logBox.setScrollTop(Double.MAX_VALUE);
					}
				}
			}
		}, 0, 100);
		this.arduino = new ArduinoCommunicator(); // PORT
		try {
			if (this.arduino.connect(PORT)) {
				this.serialPort.setText("\u3014TURRET_0\u3015 ACTIVE ON \u00abCOM1\u00bb");
			} else {
				this.serialPort.setText("\u3014TURRET_0\u3015 NOT ACTIVE");
			}
		} catch (Exception e) {
			this.serialPort.setText("\u3014TURRET_0\u3015 NOT ACTIVE");
			e.printStackTrace();
		}

		// Set the Capture Frame to the Aperture logo, or a black screen
		try {
			this.updateImageView(this.currentFrame,
					new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/aperture_science.jpg")));
		} catch (Exception e) {
			System.err.println("FAILED TO FIND CORRECT BACKGROUND IMAGE");
			e.printStackTrace();
			Mat blackFrame = new Mat(480, 640, CvType.CV_8U);
			blackFrame.setTo(new Scalar(0, 0, 0, 255));
			this.updateImageView(this.currentFrame, Utils.mat2Image(blackFrame));
		}

		// Setup buttons
		try {
			// Title bar buttons
			this.close.setImage(new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/close.png")));
			this.maximize
					.setImage(new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/maximize.png")));
			this.minimize
					.setImage(new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/minimize.png")));
			// Play Button
			ImageView graphic = new ImageView(
					new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/play.png")));
			graphic.setFitWidth(20);
			graphic.setPreserveRatio(true);
			playButton.setGraphic(graphic);
			playButton.setText("");
		} catch (Exception e) {
			playButton.setText("||>");
		}

		// Fill combo box
		final String path = "sounds/";
		final File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		if (jarFile.isFile()) { // Jar File
			try {
				final JarFile jar = new JarFile(jarFile);
				final Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					final String name = entries.nextElement().getName();
					if (name.startsWith(path) && !name.equals(path))
						this.soundComboBox.getItems().add(name.substring(path.length(), name.indexOf(".")));
				}
				jar.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else { // IDE
			File file = new File(this.soundPath);
			for (File sound : file.listFiles()) {
				this.soundComboBox.getItems().add(sound.getName().substring(0, sound.getName().indexOf(".")));
			}
		}

		// Set recommended volume
		this.volumeSlider.setValue(85.0f);

		// Start voice command recognizing on a separate thread (infinite loop)
		voiceCommands = new Thread(new Runnable() {
			@Override
			public void run() {
				vcc.init();
				vcc.start();
			}
		});
		voiceCommands.start();
	}

	/**
	 * Closes the stream to the connected webcam.
	 */
	protected void stopAcquisition() {
		System.out.println("INFO: STOPPING CAMERA.");
		if (this.timer != null && !this.timer.isShutdown()) {
			try {
				this.timer.shutdown();
				this.timer.awaitTermination(interval, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				System.err.println("FAILED TO STOP CAMERA TIMER");
				e.printStackTrace();
			}
		}
		if (this.capture.isOpened()) {
			this.capture.release();
		}

		try {
			this.updateImageView(this.currentFrame,
					new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/aperture_science.jpg")));
		} catch (Exception e) {
			System.err.println("FAILED TO FIND CORRECT BACKGROUND IMAGE");
			e.printStackTrace();
			Mat blackFrame = new Mat(480, 640, CvType.CV_8U);
			blackFrame.setTo(new Scalar(0, 0, 0, 255));
			this.updateImageView(this.currentFrame, Utils.mat2Image(blackFrame));
		}

		this.cameraActive = false;
		this.cameraButton.setText("Start Camera");
	}

	/**
	 * Called when the program is exiting
	 */
	@FXML
	protected void onStop() {
		stopAcquisition();
		if (this.clip != null && this.clip.isActive()) {
			this.clip.stop();
			if (progressUpdateTimer != null)
				progressUpdateTimer.cancel();
		}
		vcc.stop();
		// TODO Get Thread.interrupt() to work.
		System.exit(0);
	}

	/**
	 * Called when the Minimize button is pressed.
	 */
	@FXML
	protected void onMinimize() {
		FaceTrackingController.stage.setIconified(true);
	}

	/**
	 * Called when the Maximize/Restore button is pressed
	 */
	@FXML
	protected void onMaximize() {
		FaceTrackingController.stage.setMaximized(!FaceTrackingController.stage.isMaximized());
	}
}
