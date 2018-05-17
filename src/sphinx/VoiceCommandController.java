package sphinx;

import java.io.IOException;

import application.FaceTrackingController;
import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.LiveSpeechRecognizer;
import edu.cmu.sphinx.api.SpeechResult;

public class VoiceCommandController {

	FaceTrackingController commandListenerObject;

	private static final String dictionaryVersion = "9927";
	private static final String ACOUSTIC_PATH = "resource:/edu/cmu/sphinx/models/en-us/en-us",
			DIC_PATH = ClassLoader.getSystemClassLoader().getResource("sphinx4/" + dictionaryVersion + ".dic")
					.toString(),
			LM_PATH = ClassLoader.getSystemClassLoader().getResource("sphinx4/" + dictionaryVersion + ".lm").toString();

	private Configuration configuration;
	private LiveSpeechRecognizer recognizer;
	private SpeechResult result;

	private boolean running = false;

	public void init() {
		configuration = new Configuration();
		configuration.setAcousticModelPath(ACOUSTIC_PATH);
		configuration.setDictionaryPath(DIC_PATH);
		configuration.setLanguageModelPath(LM_PATH);

		try {
			recognizer = new LiveSpeechRecognizer(configuration);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void start() {
		running = true;
		recognizer.startRecognition(true);
		while (running && (result = recognizer.getResult()) != null) {
			String hypothesis = result.getHypothesis();
			System.out.println(hypothesis);
			this.commandListenerObject.processCommand(hypothesis);
		}
		recognizer.stopRecognition();
	}

	public void stop() {
		running = false;
	}

	public void addCommandListener(FaceTrackingController listener) {
		this.commandListenerObject = listener;
	}

}
