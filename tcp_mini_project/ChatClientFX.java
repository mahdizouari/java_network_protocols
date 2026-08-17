package tcp_mini_project;

// Pour applications JavaFX (base pour fenêtre et démarrage d'une appli graphique Java)
import javafx.application.Application; // (Permet de créer l'application JavaFX)
// Pour exécuter du code dans le thread graphique/FX (nécessaire pour modifier l'interface depuis un thread secondaire)
import javafx.application.Platform; // (Permet d'exécuter des instructions dans le thread graphique)
// Pour alignement, positionnement graphique
import javafx.geometry.Pos; // (Définit l'alignement des éléments graphiques dans les conteneurs JavaFX)
// Pour créer et manipuler la scène principale de l'appli (fenêtre)
import javafx.scene.Scene; // (Scène principale de l'application JavaFX)
// Pour les widgets standards (bouton, texte, etc.)
import javafx.scene.control.*; // (TextField, Button, TextInputDialog, Label, etc.)
// Pour afficher des images (et manipuler)
import javafx.scene.image.Image; // (Pour créer des objets Image à partir de fichiers ou url)
import javafx.scene.image.ImageView; // (Afficher les objets Image dans l'interface)
// Pour mettre en page les composants dans la fenêtre
import javafx.scene.layout.*; // (VBox, HBox, StackPane, ScrollPane, etc.)
// Pour prendre en charge les fichiers audio/vidéo (.mp4, .mp3, ...)
import javafx.scene.media.Media; // (Pour manipuler un média audio/vidéo)
import javafx.scene.media.MediaPlayer; // (Lecture des médias)
import javafx.scene.media.MediaView; // (Affichage vidéo dans l'interface)
// Pour la sélection de fichiers sur disque via l'interface graphique
import javafx.stage.FileChooser; // (Ouvre une boîte de dialogue pour sélectionner un fichier)
import javafx.stage.Stage; // (Correspond à la "fenêtre principale" JavaFX de l'appli)
 
// Communication côté réseau / lecture-écriture de fichiers et flux binaires
import java.io.*; // (BufferedReader, BufferedWriter, DataInputStream, DataOutputStream, InputStream, OutputStream, File, FileInputStream, FileOutputStream, etc)
import java.net.Socket; // (Socket TCP client réseau permettant de joindre le serveur)
// import java.util.Arrays; est inutile ici, mais sert à manipuler des tableaux en général
 
public class ChatClientFX extends Application { // Dérive de Application (JavaFX)
 
	// === Variables pour la communication réseau ===
	private Socket socket; // Socket réseau TCP (import java.net.Socket) pour la connexion serveur
	private BufferedReader textInput; // Lit du texte reçu depuis le serveur (import java.io.BufferedReader)
	private BufferedWriter textOutput; // Envoie du texte vers le serveur (import java.io.BufferedWriter)
	private DataInputStream dataInput; // Pour recevoir des données binaires (fichiers) (import java.io.DataInputStream)
	private DataOutputStream dataOutput; // Pour envoyer des données binaires (import java.io.DataOutputStream)
	private String myHandle = ""; // Sera du style @nom#1, assigné par le serveur
 
	// === Composants graphiques (JavaFX) ===
	private VBox chatBox; // Zone verticale où on affiche les messages (VBox = conteneur vertical d'éléments)
	private TextField messageField, nameField; // Champs de saisie du nom et du message
	private Button connectButton, sendButton, disconnectButton, sendFileButton; // Boutons principaux
	private boolean isDisconnected = false; // True après déconnexion
	private String username; // Nom de l'utilisateur (local)
 
	// Méthode d'entrée de l'application graphique (appelée automatiquement)
	@Override
	public void start(Stage primaryStage) { // (Stage : fenêtre principale JavaFX)
    	// Mise en page : zone de chat, champs, boutons, etc.
    	chatBox = new VBox(8); // (VBox : conteneur vertical, "box", JavaFX)
    	chatBox.setStyle("-fx-padding: 10; -fx-background-color: #f0f2f5;");
    	ScrollPane scrollPane = new ScrollPane(chatBox); // Ajoute le scroll automatique
    	scrollPane.setFitToWidth(true);
    	scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
 
    	messageField = new TextField(); // Saisie de message
    	messageField.setPromptText("Type your message...");
    	messageField.setPrefWidth(400);
 
    	nameField = new TextField(); // Saisie du nom utilisateur
    	nameField.setPromptText("Enter your name");
 
    	connectButton = new Button("Connect"); // Bouton pour se connecter au serveur
    	sendButton = new Button("Send");   	// Envoyer un message texte
    	sendButton.setDisable(true);       	// Désactivé tant qu'on n'est pas connecté
    	sendButton.setStyle("-fx-background-color: #1877f2; -fx-text-fill: white;");
 
    	disconnectButton = new Button("Disconnect"); // Bouton de déconnexion
    	disconnectButton.setDisable(true);
 
    	sendFileButton = new Button("Send File");	// Bouton pour envoyer un fichier
    	sendFileButton.setDisable(true);
    	sendFileButton.setStyle("-fx-background-color: #42b72a; -fx-text-fill: white;");
 
    	// Organisation horizontale des champs dans la fenêtre
    	HBox nameBox = new HBox(10, new Label("Name:"), nameField, connectButton, disconnectButton, sendFileButton);
    	nameBox.setAlignment(Pos.CENTER_LEFT);
    	HBox messageBox = new HBox(10, messageField, sendButton);
    	messageBox.setAlignment(Pos.CENTER_LEFT);
 
    	// Grande zone principale de l'interface
    	VBox layout = new VBox(10, scrollPane, nameBox, messageBox);
    	layout.setStyle("-fx-padding: 15; -fx-background-color: #e9ecef;");
    	layout.setPrefSize(780, 680);
 
    	Scene scene = new Scene(layout); // La scène contient tout l'UI
    	primaryStage.setScene(scene);
    	primaryStage.setTitle("WhatsApp-like Chat - Full Video & Image Support"); // Titre de la fenêtre
    	primaryStage.setOnCloseRequest(e -> handleDisconnect()); // Quitter = déconnexion automatique
    	primaryStage.show();
 
    	chatBox.heightProperty().addListener(o -> scrollPane.setVvalue(1.0)); // Scroll auto à la fin
 
    	// Actions des boutons/champs
    	connectButton.setOnAction(e -> connectToServer());
    	sendButton.setOnAction(e -> sendMessage());
    	disconnectButton.setOnAction(e -> handleDisconnect());
    	sendFileButton.setOnAction(e -> sendFile());
    	messageField.setOnAction(e -> sendMessage());
	}
 
	// === Gestion de la connexion réseau et des flux ===
	private void connectToServer() {
    	username = nameField.getText().trim();
    	if (username.isEmpty()) {
        	showAlert("Please enter your name!");
        	return;
    	}
    	new File("downloads").mkdirs(); // Crée (si besoin) le répertoire de destination des fichiers
 
    	try {
        	socket = new Socket("localhost", 1234); // Connexion TCP (import java.net.Socket)
        	resetStreams(); // Création de TOUS les flux associés à cette socket
 
        	textOutput.write(username + "\n");
        	textOutput.flush();
 
        	// Lorsqu'on est connecté, on rend tous les boutons/accessibles
        	connectButton.setDisable(true);
        	nameField.setEditable(false);
        	sendButton.setDisable(false);
        	disconnectButton.setDisable(false);
        	sendFileButton.setDisable(false);
 
        	// Thread secondaire pour recevoir en continu les messages du serveur (ne pas geler l’interface)
        	new Thread(() -> {
            	try {
                	String line;
                	while ((line = textInput.readLine()) != null && !isDisconnected) {
                    	String currentLine = line; // Pour donner au lambda/Fx thread une variable figée
 
                    	// Détection (protocole) du handle attribué
                    	if (currentLine.startsWith("Welcome, your handle is: @")) {
                        	myHandle = currentLine.substring("Welcome, your handle is: @".length()).trim();
                        	Platform.runLater(() -> addMessage("Your handle: @" + myHandle));
                        	continue;
                    	}
                    	// Détection du début de transfert de fichier (protocole spécifique)
                    	if (currentLine.startsWith("FILE_HEADER:")) {
                        	handleIncomingFile(currentLine); // Va lire le fichier binaire
                        	resetStreams(); // Toujours réinitialiser les flux après fichier
                        	continue;
                    	}
                    	// Tous les autres messages sont affichés
                    	Platform.runLater(() -> addMessage(currentLine));
                	}
            	} catch (Exception e) {
                	if (!isDisconnected) Platform.runLater(this::handleDisconnect);
            	}
        	}).start();
 
    	} catch (IOException e) {
        	showAlert("Cannot connect: " + e.getMessage());
    	}
	}
 
	// === Remet en place tous les flux associés à la socket (important après chaque transfert de fichier) ===
	private void resetStreams() {
    	try {
        	textInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));	// Pour recevoir le texte
        	textOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())); // Pour envoyer le texte
        	dataInput = new DataInputStream(socket.getInputStream());   // Pour recevoir les fichiers binaires
        	dataOutput = new DataOutputStream(socket.getOutputStream());// Pour envoyer les fichiers binaires
    	} catch (IOException e) {
        	e.printStackTrace();
    	}
	}
 
	// === ENVOI d'un SIMPLE message texte au serveur ===
	private void sendMessage() {
    	String msg = messageField.getText().trim();
    	if (!msg.isEmpty() && textOutput != null) {
        	try {
            	textOutput.write(msg + "\n");
            	textOutput.flush();
            	messageField.clear();
        	} catch (IOException e) {
            	showAlert("Failed to send message");
        	}
    	}
	}
 
	// === Sélection graphique + ENVOI de fichier ===
	private void sendFile() {
    	FileChooser chooser = new FileChooser();
    	File file = chooser.showOpenDialog(null); // Ouvre explorateur de fichiers
    	if (file == null) return;
 
    	TextInputDialog dialog = new TextInputDialog("all");
    	dialog.setHeaderText("Send to whom?");
    	dialog.setContentText("Use 'all' or '@name#1 @name#2' ...");
    	dialog.showAndWait().ifPresent(recipients -> {
        	try {
            	String recList = recipients.trim().replaceAll("\\s*,\\s*", ",");
            	if (recList.isEmpty()) recList = "all";
            	final String finalRecList = recList; // Pour la lambda
 
            	String header = "FILE_HEADER:" + myHandle + ":" + file.getName() + ":" + file.length() + ":" + finalRecList;
            	textOutput.write(header + "\n");
            	textOutput.flush();
 
            	// Ecriture du fichier octet par octet (import java.io.FileInputStream, DataOutputStream)
            	try (FileInputStream fis = new FileInputStream(file)) {
                	byte[] buffer = new byte[8192];
                	int bytesRead;
                	while ((bytesRead = fis.read(buffer)) != -1) {
                    	dataOutput.write(buffer, 0, bytesRead);
                	}
            	}
            	dataOutput.flush();
            	resetStreams(); // Très important !
            	Platform.runLater(() -> addMessage("✅ Sent: " + file.getName() + " to " + finalRecList));
        	} catch (Exception e) {
            	Platform.runLater(() -> showAlert("Send failed: " + e.getMessage()));
        	}
    	});
	}
 
	// === RECOIT et affiche/stocke le fichier transmis par le serveur ===
	private void handleIncomingFile(String header) {
    	try {
        	String[] p = header.split(":", 5);
        	if (p.length < 5) return;
        	String sender = p[1], fileName = p[2];
        	long size = Long.parseLong(p[3]);
        	String recipients = p[4];
 
        	// Vérifie si ce fichier m'est adressé
        	boolean forMe = recipients.equalsIgnoreCase("all") || recipients.contains(myHandle.replace("@", ""));
        	if (!forMe) return;
 
        	File saved = new File("downloads/" + System.currentTimeMillis() + "_" + fileName);
        	saved.getParentFile().mkdirs();
 
        	try (FileOutputStream fos = new FileOutputStream(saved)) {
            	byte[] buf = new byte[8192];
            	long rem = size;
            	while (rem > 0) {
                	int r = dataInput.read(buf, 0, (int) Math.min(buf.length, rem));
                	if (r == -1) break;
                	fos.write(buf, 0, r);
                	rem -= r;
            	}
        	}
 
        	String ext = fileName.toLowerCase();
        	Platform.runLater(() -> displayFile(sender, fileName, saved, ext, size));
    	} catch (Exception e) {
        	e.printStackTrace();
    	}
	}
 
	// === Affiche le média reçu selon son type (image/vidéo/audio/lien fichier) ===
	private void displayFile(String sender, String fileName, File saved, String ext, long size) {
    	Label info = new Label("@" + sender + " sent " + fileName);
    	info.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a73e8; -fx-padding: 5 0 5 0;");
 
    	if (ext.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|tiff)")) {
        	try {
            	Image img = new Image(saved.toURI().toString());
            	ImageView iv = new ImageView(img);
            	iv.setFitWidth(450);
            	iv.setPreserveRatio(true);
            	iv.setOnMouseClicked(e -> showFullScreenImage(img));
            	chatBox.getChildren().addAll(info, iv);
        	} catch (Exception ex) {
            	chatBox.getChildren().addAll(info, new Label("Image failed to load"));
        	}
    	} else if (ext.matches(".*\\.(mp4|avi|mkv|mov|wmv|flv|webm|mpg|mpeg|3gp)")) {
        	ImageView thumb = createVideoThumbnail(saved);
        	Label playBtn = new Label("▶ Play");
        	playBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-font-size: 24; -fx-padding: 20 40; -fx-background-radius: 50;");
        	StackPane videoPane = new StackPane(thumb, playBtn);
        	StackPane.setAlignment(playBtn, Pos.CENTER);
        	videoPane.setOnMouseClicked(e -> openVideoPlayer(saved, fileName));
        	videoPane.setStyle("-fx-background-color: #333;");
        	chatBox.getChildren().addAll(info, new Label(fileName), videoPane);
    	} else if (ext.matches(".*\\.(mp3|wav|ogg|m4a|aac|flac)")) {
        	Button playBtn = new Button("▶ Play " + fileName);
        	playBtn.setStyle("-fx-background-color: #5f27cd; -fx-text-fill: white; -fx-padding: 10 20;");
        	MediaPlayer mp = new MediaPlayer(new Media(saved.toURI().toString()));
        	playBtn.setOnAction(e -> {
            	if (mp.getStatus() == MediaPlayer.Status.PLAYING) {
                	mp.pause();
                	playBtn.setText("▶ Play");
            	} else {
                	mp.play();
                	playBtn.setText("⏸ Pause");
            	}
        	});
        	mp.setOnEndOfMedia(() -> Platform.runLater(() -> playBtn.setText("▶ Replay")));
        	chatBox.getChildren().addAll(info, playBtn);
    	} else {
        	Hyperlink link = new Hyperlink("💾 " + fileName + " (" + formatSize(size) + ")");
        	link.setStyle("-fx-font-weight: bold; -fx-text-fill: #42b72a;");
        	link.setOnAction(e -> saveFileAs(saved, fileName));
        	chatBox.getChildren().addAll(info, link);
    	}
	}
 
	// Affiche une vignette vidéo de remplacement (lien vers image "VIDEO" par défaut)
	private ImageView createVideoThumbnail(File videoFile) {
    	ImageView placeholder = new ImageView();
    	placeholder.setImage(new Image("https://via.placeholder.com/500x300/333/fff?text=VIDEO"));
    	placeholder.setFitWidth(500);
    	placeholder.setPreserveRatio(true);
    	return placeholder;
	}
 
	// Fenêtre pop-up pour la lecture vidéo (MediaPlayer/MediaView)
	private void openVideoPlayer(File file, String title) {
    	try {
        	Stage stage = new Stage();
        	MediaPlayer player = new MediaPlayer(new Media(file.toURI().toString()));
        	MediaView view = new MediaView(player);
        	view.setPreserveRatio(true);
 
        	Button playPause = new Button("⏸ Pause");
        	playPause.setOnAction(e -> {
            	if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                	player.pause();
                	playPause.setText("▶ Play");
            	} else {
                	player.play();
                	playPause.setText("⏸ Pause");
            	}
        	});
 
        	VBox box = new VBox(10, view, playPause);
        	box.setStyle("-fx-background-color: black; -fx-padding: 20;");
        	view.fitWidthProperty().bind(stage.widthProperty().subtract(40));
 
        	stage.setScene(new Scene(box, 1100, 700));
        	stage.setTitle("Playing: " + title);
        	stage.show();
        	player.play();
    	} catch (Exception e) {
        	showAlert("Video player error: " + e.getMessage());
    	}
	}
 
	// Ajoute un message texte graphique dans le VBox d'affichage discussion
	private void addMessage(String text) {
    	Label lbl = new Label(text);
    	lbl.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 18; -fx-max-width: 500;");
    	lbl.setWrapText(true);
    	chatBox.getChildren().add(lbl);
	}
 
	// Ouvre une image plein écran lors du clic
	private void showFullScreenImage(Image img) {
    	Stage s = new Stage();
    	ImageView iv = new ImageView(img);
    	iv.setPreserveRatio(true);
    	ScrollPane scroll = new ScrollPane(iv);
    	s.setScene(new Scene(scroll));
    	s.setMaximized(true);
    	s.show();
    	iv.setOnMouseClicked(e -> s.close());
	}
 
	// Sauvegarde le fichier reçu lors d'un clic sur le lien
	private void saveFileAs(File src, String name) {
    	FileChooser fc = new FileChooser();
    	fc.setInitialFileName(name);
    	File dest = fc.showSaveDialog(null);
    	if (dest != null) {
        	try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            	byte[] b = new byte[8192];
            	int len;
            	while ((len = in.read(b)) > 0) out.write(b, 0, len);
            	showAlert("Saved: " + dest.getName());
        	} catch (Exception ex) {
            	showAlert("Error: " + ex.getMessage());
        	}
    	}
	}
 
	// Retourne une taille lisible par un humain (octets, Ko, Mo, Go)
	private String formatSize(long b) {
    	if (b < 1024) return b + " B";
    	if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
    	if (b < 1024 * 1024 * 1024) return String.format("%.1f MB", b / (1024.0 * 1024));
    	return String.format("%.1f GB", b / (1024.0 * 1024 * 1024));
	}
 
	// Gère la déconnexion et désactive l'interface
	private void handleDisconnect() {
    	if (isDisconnected) return;
    	isDisconnected = true;
    	try {
        	if (textOutput != null) textOutput.write("/quit\n");
        	if (socket != null) socket.close();
    	} catch (Exception ignored) {}
    	Platform.runLater(() -> {
        	addMessage("Disconnected.");
        	connectButton.setDisable(false);
        	nameField.setEditable(true);
        	sendButton.setDisable(true);
        	disconnectButton.setDisable(true);
        	sendFileButton.setDisable(true);
    	});
	}
 
	// Fenêtre d'information JavaFX
	private void showAlert(String msg) {
    	Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait());
	}
 
	// Point d'entrée lorsqu'on lance ChatClientFX (JavaFX)
	public static void main(String[] args) {
    	launch(args); // Lance l'application JavaFX


}}