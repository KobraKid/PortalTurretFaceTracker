package application;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Random;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

public class Main extends Application {

	static PrintStream stream;
	static ByteArrayOutputStream streamLog = new ByteArrayOutputStream();
	// Controls where logs are displayed
	// True = logs are shown in program
	// False = logs are shown in console
	private static boolean LOGGING = true, ERROR_LOGGING = false;

	private final String[] titles = { "BAKING CAKES", "RESETTING PORTALS", "PLACING CUBES", "TESTING GIANT BUTTONS",
			"CALIBRATING TURRETS", "PROGRAMMING GLaDOS", "GATHERING TEST SUBJECTS", "APERTURE SCIENCE",
			"APERTURE SCIENCE LABORATORIES COMPUTER-AIDED ENRICHMENT CENTER" };

	private final static String LOG_FLAG = "NAME\n\t-log : Log normal program function\nOPTIONS\n\tT, t : Display detailed (non-error related) logs\n\n\tF, f : Do not display logs";

	private final static String ERR_FLAG = "NAME\n\t-err : Log program warnings and errors\nOPTIONS\n\tT, t : Always display warnings and errors\n\n\tF, f : Do not display warnings and errors";

	private final static String CAM_FLAG = "NAME\n\t-cam : Indicate the camera to be used\nOPTIONS\n\t[0..9] : The index of the desired camera. Typically, 0 is the built-in webcam (if one exists)";

	private final static String HELP_FLAG = "NAME\n\t-help : Displays information about a flag\nOPTIONS\n\t[flag name] : The desired flag";

	private final static String LIST_FLAG = "NAME\n\t-list : Lists all available flags";

	private final static String list = "log\terr\ncam\thelp\nlist";

	@Override
	public void start(Stage primaryStage) {
		// Start up the GUI
		try {
			// Loading the stage
			FXMLLoader loader = new FXMLLoader(getClass().getResource("FaceTrackingGUI.fxml"));
			VBox root = (VBox) loader.load();
			Scene scene = new Scene(root, 928, 603);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

			// Styling the stage
			primaryStage.setTitle(
					String.format("TURRET OPERATION | %s...", titles[new Random().nextInt(titles.length - 1)]));
			primaryStage.getIcons().add(
					new Image(ClassLoader.getSystemClassLoader().getResourceAsStream("images/aperture_science.png")));
			primaryStage.setScene(scene);
			primaryStage.initStyle(StageStyle.UNDECORATED);
			primaryStage.show();

			FaceTrackingController controller = loader.getController();
			// If the camera is still in use when the program closes, release it
			primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
				@Override
				public void handle(WindowEvent arg0) {
					controller.onStop();
				}
			});
			// Initialize the controller
			controller.init(primaryStage);
		} catch (Exception e) {
			System.err.println("FAILED TO INITIALIZE THE STAGE");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		int arglen = args.length;
		if (arglen == 1 && args[0].equals("-list")) {
			System.out.println(list);
			System.exit(0);
		} else if (arglen > 0 && arglen < 7 && arglen % 2 == 0) {
			for (int i = 0; i < arglen; i += 2) {
				if (args[i].equals("-help")) {
					switch (args[i + 1]) {
					case "log":
						System.out.println(LOG_FLAG);
						break;
					case "err":
						System.out.println(ERR_FLAG);
						break;
					case "cam":
						System.out.println(CAM_FLAG);
						break;
					case "list":
						System.out.println(LIST_FLAG);
						break;
					case "help":
						System.out.println(HELP_FLAG);
						break;
					default:
						System.err.println("INVALID FLAG\n");
						System.out.println(HELP_FLAG);
						break;
					}
					System.exit(0);
				} else if (args[i].equals("list")) {
					System.err.println("INVALID FLAG\n");
					System.out.println(LIST_FLAG);
					System.exit(3);
				} else if (args[i].equals("-log"))
					if (args[i + 1].equalsIgnoreCase("t"))
						LOGGING = true;
					else if (args[i + 1].equalsIgnoreCase("f"))
						LOGGING = false;
					else {
						System.err.println("INVALID FLAG\n");
						System.out.println(LOG_FLAG);
						System.exit(1);
					}
				else if (args[i].equals("-err"))
					if (args[i + 1].equalsIgnoreCase("t"))
						ERROR_LOGGING = true;
					else if (args[i + 1].equalsIgnoreCase("f"))
						ERROR_LOGGING = false;
					else {
						System.err.println("INVALID FLAG\n");
						System.out.println(ERR_FLAG);
						System.exit(2);
					}
				else if (args[i].equals("-cam"))
					try {
						int cam = Integer.parseInt(args[i + 1]);
						FaceTrackingController.cameraID = cam;
					} catch (NumberFormatException e) {
						System.err.println("INVALID FLAG\n");
						System.out.println(CAM_FLAG);
					}
			}
		} else if (arglen != 0) {
			System.out.println("NO ARGS OR WRONG NUMBER OF ARGS PASSED");
		}

		// Load the OpenCV Native Library
		// System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		Main.class.getClassLoader();
		System.load(ClassLoader.getSystemClassLoader().getResource(".").getPath() + Core.NATIVE_LIBRARY_NAME + ".dll");

		stream = new PrintStream(streamLog);
		if (LOGGING)
			System.setOut(stream);
		if (ERROR_LOGGING)
			System.setErr(stream);
		launch(args);
	}
}
