package MuiltiCastMiniProjet;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.file.Files;
import java.nio.file.Path;

public class Client extends Application {

    // Adresse et port du groupe multicast
    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 12345;

    // Dossier de sauvegarde des fichiers reçus (créé sur le bureau)
    private static final Path DOSSIER_RECEPTION = Path.of(System.getProperty("user.home"), "Desktop", "fichier_multicast");

    static {
        try {
            // Création automatique du dossier si non existant
            Files.createDirectories(DOSSIER_RECEPTION);
        } catch (IOException e) {
            System.err.println("Impossible de créer le dossier : " + DOSSIER_RECEPTION);
        }
    }

    private VBox console;
    private ScrollPane scrollPane;

    // Buffers utilisés pour reconstruire les vidéos et PDF reçus en plusieurs paquets
    private byte[] videoBuffer = new byte[0];
    private byte[] pdfBuffer = new byte[0];

    @Override
    public void start(Stage stage) {

        // Titre de l’interface
        Label title = new Label("Client Multicast • Fichiers sauvegardés sur le Bureau/fichier_multicast");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1565c0;");

        console = new VBox(10);
        console.setPadding(new Insets(10));

        scrollPane = new ScrollPane(console);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(700);

        VBox root = new VBox(10, title, scrollPane);
        root.setPadding(new Insets(15));

        stage.setScene(new Scene(root, 800, 800));
        stage.setTitle("Client Multicast - Réception");
        stage.show();

        // Lancement du thread d’écoute multicast
        startListening();
    }

    private void startListening() {
        new Thread(() -> {
            try {
                // Socket multicast permettant de recevoir sur le port
                MulticastSocket socket = new MulticastSocket(PORT);

                // Rejoindre le groupe multicast
                InetAddress group = InetAddress.getByName(GROUP);
                socket.joinGroup(group);

                displayText("En écoute sur " + GROUP + ":" + PORT + "\nFichiers reçus → Bureau/fichier_multicast");

                // Boucle infinie d’écoute
                while (true) {
                    byte[] buffer = new byte[65507]; // Taille max UDP
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // Réception d’un paquet UDP
                    socket.receive(packet);

                    // Extraction des données
                    byte[] data = packet.getData();
                    int length = packet.getLength();

                    // Analyse du type de fichier
                    if (isImage(data)) {
                        displayImageInConsole(data, length);
                    } else if (isVideoChunk(data)) {
                        handleVideoChunk(data, length);
                    } else if (isPdfChunk(data)) {
                        handlePdfChunk(data, length);
                    } else {
                        // Message texte
                        displayText(new String(data, 0, length, "UTF-8"));
                    }
                }
            } catch (Exception e) {
                displayText("Erreur réseau : " + e.getMessage());
            }
        }).start();
    }

    // Affichage d’un message texte dans la console GUI
    private void displayText(String msg) {
        Platform.runLater(() -> {
            Label lbl = new Label(msg);
            lbl.setStyle("-fx-font-size: 14px; -fx-padding: 5;");
            console.getChildren().add(lbl);
            scrollToBottom();
        });
    }

    // Affichage direct d’une image reçue
    private void displayImageInConsole(byte[] data, int length) {
        Platform.runLater(() -> {
            try {
                Image img = new Image(new ByteArrayInputStream(data, 0, length));
                ImageView iv = new ImageView(img);
                iv.setFitWidth(500);
                iv.setPreserveRatio(true);
                console.getChildren().add(iv);
                scrollToBottom();
            } catch (Exception e) {
                displayText("Erreur affichage image");
            }
        });
    }

    // ========== VIDÉO ==========

    // Traitement des paquets vidéo (chunk par chunk)
    private void handleVideoChunk(byte[] chunk, int length) {

        byte flag = chunk[0]; // flag = 0 (milieu) ou 1 (fin)
        byte[] part = new byte[length - 1]; // extraction des données
        System.arraycopy(chunk, 1, part, 0, length - 1);

        // Ajout au buffer vidéo
        byte[] newBuf = new byte[videoBuffer.length + part.length];
        System.arraycopy(videoBuffer, 0, newBuf, 0, videoBuffer.length);
        System.arraycopy(part, 0, newBuf, videoBuffer.length, part.length);
        videoBuffer = newBuf;

        // Si flag=1 → fin de vidéo
        if (flag == 1) {
            saveAndShowVideo(videoBuffer);
            videoBuffer = new byte[0];
        }
    }

    // Sauvegarde + lecture de la vidéo
    private void saveAndShowVideo(byte[] data) {
        Platform.runLater(() -> {
            try {
                // Nom unique
                String nom = "video_" + System.currentTimeMillis() + ".mp4";
                Path chemin = DOSSIER_RECEPTION.resolve(nom);
                Files.write(chemin, data);

                // Lecture vidéo dans JavaFX
                Media media = new Media(chemin.toUri().toString());
                MediaPlayer player = new MediaPlayer(media);
                MediaView mv = new MediaView(player);
                mv.setFitWidth(600);
                mv.setPreserveRatio(true);

                Label info = new Label("Vidéo reçue → " + nom);
                info.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");

                console.getChildren().addAll(info, mv);
                scrollToBottom();
                player.setOnReady(player::play);

            } catch (Exception e) {
                displayText("Erreur vidéo : " + e.getMessage());
            }
        });
    }

    // ========== PDF ==========

    // Vérifie si le paquet correspond à un PDF
    private boolean isPdfChunk(byte[] d) {
        return d.length > 2 && d[0] == 0x02 && (d[1] == 0 || d[1] == 1);
    }

    // Reconstruction d’un PDF chunk par chunk
    private void handlePdfChunk(byte[] chunk, int length) {

        byte endFlag = chunk[1]; // fin=1
        byte[] part = new byte[length - 2];
        System.arraycopy(chunk, 2, part, 0, length - 2);

        byte[] newBuf = new byte[pdfBuffer.length + part.length];
        System.arraycopy(pdfBuffer, 0, newBuf, 0, pdfBuffer.length);
        System.arraycopy(part, 0, newBuf, pdfBuffer.length, part.length);
        pdfBuffer = newBuf;

        if (endFlag == 1) {
            saveAndShowPdf(pdfBuffer);
            pdfBuffer = new byte[0];
        }
    }

    // Sauvegarde + affichage PDF
    private void saveAndShowPdf(byte[] data) {
        Platform.runLater(() -> {
            try {
                String nom = "document_" + System.currentTimeMillis() + ".pdf";
                Path chemin = DOSSIER_RECEPTION.resolve(nom);
                Files.write(chemin, data);

                WebView webView = new WebView();
                webView.setPrefSize(700, 900);
                webView.getEngine().load(chemin.toUri().toString());

                Label titre = new Label("PDF reçu → " + nom);
                titre.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #d32f2f;");

                VBox box = new VBox(8, titre, webView);
                box.setStyle("-fx-border-color: #ff5722; -fx-border-width: 2; -fx-padding: 10; -fx-background-color: #fff3e0;");
                console.getChildren().add(box);
                scrollToBottom();

            } catch (Exception e) {
                displayText("Erreur PDF : " + e.getMessage());
            }
        });
    }

    private void scrollToBottom() {
        scrollPane.setVvalue(1.0);
    }

    // Vérifie si le contenu reçu correspond à une image (JPG/PNG/GIF)
    private boolean isImage(byte[] d) {
        if (d.length < 4) return false;
        return (d[0] == (byte)0xFF && d[1] == (byte)0xD8) || // JPG
               (d[0] == (byte)0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') || // PNG
               (d[0] == 'G' && d[1] == 'I' && d[2] == 'F'); // GIF
    }

    // Vérifie si c’est un fragment vidéo (flag 0 ou 1)
    private boolean isVideoChunk(byte[] d) {
        return d.length > 1 && (d[0] == 0 || d[0] == 1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
