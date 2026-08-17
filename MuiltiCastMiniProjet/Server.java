package MuiltiCastMiniProjet;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Server extends Application {

    // Adresse et port du groupe multicast
    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 12345;

    private TextArea console;
    private TextField messageField;
    private MulticastSocket socket; // point de communication utilisé pour envoyer et recevoir des données entre deux programmes
    private InetAddress groupAddress;

    @Override
    public void start(Stage stage) { // javafx constructeur =>  

        // Titre de l’interface
        Label title = new Label("Serveur Multicast (Texte • Images • Vidéos • PDF)");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        console = new TextArea(); 
        console.setEditable(false);
        console.setPrefHeight(250);

        messageField = new TextField();
        messageField.setPromptText("Tapez votre message ici...");

        // Boutons d'envoi
        Button sendText = new Button("Envoyer Message");
        sendText.setOnAction(e -> sendTextMessage());

        Button sendImage = new Button("Envoyer Image");
        sendImage.setOnAction(e -> sendImageFile());

        Button sendVideo = new Button("Envoyer Vidéo");
        sendVideo.setOnAction(e -> sendVideoFile());

        Button sendPdf = new Button("Envoyer PDF");
        sendPdf.setOnAction(e -> sendPdfFile());

        HBox buttons = new HBox(15, sendText, sendImage, sendVideo, sendPdf);
        buttons.setPadding(new Insets(10));

        VBox root = new VBox(10, title, console, messageField, buttons);
        root.setPadding(new Insets(15));

        stage.setScene(new Scene(root, 750, 520));
        stage.setTitle("Serveur Multicast - Mini Projet");
        stage.show();

        try {
            // Création du socket multicast
            socket = new MulticastSocket();

            // Adresse du groupe multicast
            groupAddress = InetAddress.getByName(GROUP);

            log("Serveur prêt → " + GROUP + ":" + PORT);
        } catch (Exception e) {
            log("Erreur réseau : " + e.getMessage());
        }
    }

    // ========== ENVOI TEXTE ==========

    private void sendTextMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty()) return;

        try {
            // Conversion du message en bytes
            byte[] data = msg.getBytes("UTF-8");

            // Création du paquet UDP pour multicast
            DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, PORT);

            socket.send(packet);
            log("Texte envoyé : " + msg);
            messageField.clear();
        } catch (IOException e) {
            log("Erreur envoi texte : " + e.getMessage());
        }
    }

    // ========== ENVOI IMAGE ==========

    private void sendImageFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        try {
            // Lecture entière du fichier image
            byte[] data = Files.readAllBytes(file.toPath());

            DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, PORT);
            socket.send(packet);

            log("Image envoyée → " + file.getName());
        } catch (Exception e) {
            log("Erreur envoi image : " + e.getMessage());
        }
    }

    // ========== ENVOI VIDEO ==========

    private void sendVideoFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vidéos MP4", "*.mp4"));
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            int chunkSize = 60000; // Taille d’un fragment
            int offset = 0;
            int chunkCount = 0;

            // Découpage en morceaux
            while (offset < data.length) {
                int len = Math.min(chunkSize, data.length - offset);

                // +1 byte: flag de fin ou non
                byte[] chunk = new byte[len + 1];

                // Copie des données
                System.arraycopy(data, offset, chunk, 1, len);

                // Flag = 1 si dernier morceau
                chunk[0] = (offset + len >= data.length) ? (byte)1 : (byte)0;

                DatagramPacket packet = new DatagramPacket(chunk, chunk.length, groupAddress, PORT);
                socket.send(packet);

                offset += len;
                chunkCount++;
            }
            log("Vidéo envoyée (" + chunkCount + " paquets) → " + file.getName());
        } catch (Exception e) {
            log("Erreur envoi vidéo : " + e.getMessage());
        }
    }

    // ========== ENVOI PDF ==========

    private void sendPdfFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers PDF", "*.pdf"));
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            int chunkSize = 60000;
            int offset = 0;
            int chunkCount = 0;

            while (offset < data.length) {
                int len = Math.min(chunkSize, data.length - offset);

                // +2 bytes : type PDF + flag fin
                byte[] chunk = new byte[len + 2];

                chunk[0] = 0x02; // Type PDF
                chunk[1] = (offset + len >= data.length) ? (byte)1 : (byte)0;

                System.arraycopy(data, offset, chunk, 2, len);

                DatagramPacket packet = new DatagramPacket(chunk, chunk.length, groupAddress, PORT);
                socket.send(packet);

                offset += len;
                chunkCount++;
            }
            log("PDF envoyé (" + chunkCount + " paquets) → " + file.getName());
        } catch (Exception e) {
            log("Erreur envoi PDF : " + e.getMessage());
        }
    }

    // Affiche un message dans la console du serveur
    private void log(String msg) {
        console.appendText(msg + "\n");
    }

    @Override
    public void stop() {
        if (socket != null) socket.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
