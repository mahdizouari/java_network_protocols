package udp_mini_projet;

// Déclaration du package de ton projet Java

// =============== IMPORTS ===============

// Réseau : communication UDP
import java.net.*; // Pour DatagramSocket, DatagramPacket, InetAddress (nécessaires pour l'envoi/réception UDP)

// JavaFX : interface graphique
import javafx.application.Application; // (JavaFX) Point d’entrée de l’application graphique
import javafx.application.Platform;    // (JavaFX) Accès aux threads JavaFX depuis threads externes (ex: Platform.runLater)
import javafx.geometry.Insets;         // (JavaFX) Pour les marges dans les layouts
import javafx.geometry.Pos;            // (JavaFX) Pour gérer l’alignement dans les layouts

import javafx.scene.Scene;             // (JavaFX) Fenêtre, gestion de la scène graphique
import javafx.scene.control.*;         // (JavaFX) Boutons, labels, textfields etc.
import javafx.scene.image.Image;       // (JavaFX) Pour afficher et manipuler des images/gifs
import javafx.scene.image.ImageView;   // (JavaFX) Affichage d’images (et gifs) dans l’UI
import javafx.scene.layout.*;          // (JavaFX) Différents layouts graphiques (VBox, HBox, BorderPane…)

import javafx.stage.Stage;              // (JavaFX) Fenêtre principale ou supplémentaire
import javafx.stage.FileChooser;        // (JavaFX) Boîte de dialogue pour choisir un fichier à envoyer

// Structures de données utilitaires
import java.util.*;      // List, Map, Set, HashMap etc. (nécessaire partout dans le code pour stocker données dynamiques)
import java.io.File;     // Représentation de fichiers du système (pour images/GIF)
import java.nio.file.Files;     // Pour lire le contenu d’un fichier en bytes
import java.util.Base64; // Encoder/décoder en Base64 (pour envoyer images/GIFs en texte UDP)

// ========== CLASSE PRINCIPALE ==========

public class ClientUDP_Multi extends Application { // (hérite Application) Application graphique JavaFX

    // ===================== Variables : Sockets et configuration =====================
    private DatagramSocket socket; // (java.net) Pour envoyer/réceptionner datagrammes UDP
    private InetAddress serverAddress; // (java.net) Adresse IP serveur UDP
    private int serverPort = 9876; // Port par défaut du serveur UDP
    private String clientName; // Nom choisi par l'utilisateur

    // ===================== Variables : UI Principale =====================
    private VBox messagesArea; // (JavaFX) Zone verticale pour l’affichage des messages
    private TextField messageField; // (JavaFX) Champ texte pour entrer son message
    private Label nameLabel; // (JavaFX) Affiche le nom défini
    private TextField nameField; // (JavaFX) Saisie du nom
    private Button setNameButton; // (JavaFX) Bouton pour valider le nom
    private Button selectClientsButton; // (JavaFX) Bouton pour ouvrir le chat de groupe
    private Button privateChatButton;   // (JavaFX) Bouton pour ouvrir le chat privé

    private VBox clientsVBox = new VBox(5); // (JavaFX) Affiche la liste des clients connectés
    private Map<String, CheckBox> clientCheckBoxes = new HashMap<>(); // (Java) Associe chaque client à sa case dans la liste

    // ===================== Variables : Fenêtres secondaires =====================
    // Historique et gestion des fenêtres privées (1 par client)
    private Map<String, Stage> privateChats = new HashMap<>(); // (Java) Stocke fenêtre privée par client
    private Map<String, VBox> privateVBoxes = new HashMap<>(); // (Java) Zone messages privée par client
    private Map<String, List<Object>> privateHistory = new HashMap<>(); // (Java) Historique privé par client

    // Historique et gestion des fenêtres de groupe
    private Map<String, Stage> groupChats = new HashMap<>(); // (Java) Fenêtre par groupe de destinataires
    private Map<String, VBox> groupVBoxes = new HashMap<>(); // (Java) Zone messages groupée par groupe
    private Map<String, List<Object>> groupHistory = new HashMap<>(); // (Java) Historique pour chaque groupe

    // Clés (Set) pour éviter d’afficher deux fois le même message/image/GIF
    private Set<String> messageKeys = new HashSet<>();

    // ================ Méthode start : Point d’entrée JavaFX ==================
    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Création du socket UDP pour communication (DatagramSocket)
        socket = new DatagramSocket(); // (java.net)
        socket.setSoTimeout(2000);     // (java.net) Timeout pour reception
        serverAddress = InetAddress.getByName("127.0.0.1"); // Adresse serveur (local)

        // 2. Création de la fenêtre principale avec BorderPane
        BorderPane root = new BorderPane();

        // 3. Zone affichage des messages
        messagesArea = new VBox(5); // (JavaFX) zone pour messages
        messagesArea.setPadding(new Insets(8)); // marges
        ScrollPane scrollPane = new ScrollPane(messagesArea); // pour gérer le scroll des messages
        scrollPane.setFitToWidth(true); // messages prennent largeur
        root.setCenter(scrollPane); // position au centre

        // 4. Zone entrée message (en bas)
        HBox inputBox = new HBox(10); // boîte horizontale
        inputBox.setPadding(new Insets(10));
        inputBox.setAlignment(Pos.CENTER_LEFT);
        messageField = new TextField();
        messageField.setPromptText("Entrez votre message ici...");
        Button sendButton = new Button("Envoyer");
        inputBox.getChildren().addAll(messageField, sendButton);
        root.setBottom(inputBox);

        // 5. Zone nom utilisateur (en haut)
        nameField = new TextField();
        nameField.setPromptText("Entrez votre nom");
        setNameButton = new Button("Définir nom");
        HBox nameBox = new HBox(10, nameField, setNameButton);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.setPadding(new Insets(10));
        root.setTop(nameBox);

        // 6. Affichage nom, boutons gestion de discussions (à droite)
        nameLabel = new Label();
        nameLabel.setStyle("-fx-background-color: lightblue; -fx-padding: 5px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        selectClientsButton = new Button("Envoyer au groupe");
        privateChatButton = new Button("Discussion privée");

        // Liste des clients reconnectés
        VBox rightBox = new VBox(10, nameLabel, new Label("Liste des clients"),
                privateChatButton, selectClientsButton, clientsVBox);
        rightBox.setPadding(new Insets(10));
        rightBox.setAlignment(Pos.TOP_CENTER);
        root.setRight(rightBox);

        // 7. Création et affichage de la scène
        Scene scene = new Scene(root, 800, 500);
        primaryStage.setTitle("Client UDP JavaFX (Images & GIFs)"); // titre fenêtre
        primaryStage.setScene(scene);
        primaryStage.show();

        // =========== Actions des boutons =======================

        // Bouton pour définir le nom utilisateur
        setNameButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                clientName = name; // sauvegarde nom
                nameLabel.setText("👤 " + clientName); // affichage côté droit
                addMessage(messagesArea, "✅ Nom défini : " + clientName, null);
                nameField.setDisable(true);
                setNameButton.setDisable(true);
            }
        });

        // Bouton pour envoyer message principal
        sendButton.setOnAction(e -> {
            String message = messageField.getText().trim();
            if (!message.isEmpty() && clientName != null) {
                sendUDPMessage(clientName + ": " + message); // envoi UDP
                addMessage(messagesArea, clientName + ": " + message, null); // ajout à l’UI
                messageField.clear();
            } else if (clientName == null) {
                addMessage(messagesArea, "⚠️ Veuillez définir un nom avant d'envoyer un message.", null);
            }
        });

        // Bouton pour ouvrir une discussion privée
        privateChatButton.setOnAction(e -> {
            List<String> selectedClients = getSelectedClients(); // liste des cases cochées
            if (selectedClients.size() == 1) {
                String selectedClientFullId = selectedClients.get(0); // identifiant complet nom##port
                openPrivateChat(selectedClientFullId);
            } else if (selectedClients.isEmpty()) {
                addMessage(messagesArea, "⚠️ Veuillez sélectionner un client pour la discussion privée.", null);
            } else {
                addMessage(messagesArea, "⚠️ Veuillez sélectionner un seul client pour la discussion privée.", null);
            }
        });

        // Bouton pour ouvrir une discussion de groupe
        selectClientsButton.setOnAction(e -> {
            List<String> selectedClients = getSelectedClients(); // tous cochés (identifiant complet)
            Set<String> uniqueClients = new LinkedHashSet<>(selectedClients);
            if (uniqueClients.size() < 2) {
                addMessage(messagesArea, "⚠️ Veuillez sélectionner au moins deux clients pour le groupe.", null);
                return;
            }
            openGroupChat(new ArrayList<>(uniqueClients));
        });

        // Démarrage du Thread de réception des messages UDP
        new Thread(this::receiveMessages).start();
    }

    // =============== Fonctions utilitaires UI & données ==============

    // Retourne la liste des clients cochés pour discuter (identifiant complet)
    private List<String> getSelectedClients() {
        List<String> selectedClients = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : clientCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedClients.add(entry.getKey()); // clé = nom##port
            }
        }
        return selectedClients;
    }

    // Met à jour la liste des clients (reçue du serveur)
    private void updateClientsList(String[] clientsArray) {a
        Platform.runLater(() -> {
            clientsVBox.getChildren().clear();
            clientCheckBoxes.clear();
            String myFullId = getClientFullIdentifier();
            for (String client : clientsArray) {
                if (client != null && !client.equals(myFullId)) { // ne s’ajoute pas soi-même
                    CheckBox cb = new CheckBox(client);
                    clientCheckBoxes.put(client, cb);
                    clientsVBox.getChildren().add(cb);
                }
            }
        });
    }

    // Retourne l’identifiant complet du client local (nom##port)
    private String getClientFullIdentifier() {
        if (clientName != null && socket != null) {
            return clientName + "##" + socket.getLocalPort();
        }
        return clientName != null ? clientName : "Unknown";
    }

    // Génère une clé unique pour chaque message/image/GIF pour éviter doublons d’affichage
    private String getMessageKey(String msg, Image img) {
        if (img != null) {
            return "IMG:" + (msg != null ? msg : "") + ":" + img.getUrl() + ":" + img.getWidth() + "x" + img.getHeight();
        } else {
            return msg != null ? msg : "null";
        }
    }

    // Ajoute le message ou l’image dans la zone d’affichage si non déjà affiché (évite doublon)
    private void addMessage(VBox vbox, String msg, Image img) {
        String key = getMessageKey(msg, img);
        if (messageKeys.contains(key)) return; // évite doublon
        messageKeys.add(key);

        if (msg != null) {
            Label txt = new Label(msg);
            txt.setWrapText(true);
            vbox.getChildren().add(txt);
        }
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(250);
            iv.setPreserveRatio(true);
            vbox.getChildren().add(iv);
        }
        if (vbox.getParent() instanceof ScrollPane)
            ((ScrollPane) vbox.getParent()).setVvalue(1.0);
    }

    // =================== Fonctions pour chats privés et groupes =======================

    // Ouvre/affiche la fenêtre du chat de groupe (destinataires sont identifiants complets)
    private void openGroupChat(List<String> selectedClientsFullId) {
        messageKeys.clear();
        String groupKey = String.join(",", selectedClientsFullId);
        String title = "Chat groupe: " + String.join(", ", selectedClientsFullId);

        Stage groupStage = groupChats.get(groupKey);
        VBox vbox;

        if (groupStage == null) {
            groupStage = new Stage();
            groupStage.setTitle(title);

            vbox = new VBox(5);
            vbox.setPadding(new Insets(8));
            ScrollPane scrollPane = new ScrollPane(vbox);
            scrollPane.setFitToWidth(true);

            TextField chatField = new TextField();
            chatField.setPromptText("Message au groupe...");
            Button sendBtn = new Button("Envoyer");
            Button imageBtn = new Button("📷 Image");
            Button gifBtn = new Button("GIF animé");

            HBox inputBox = new HBox(10, chatField, sendBtn, imageBtn, gifBtn);
            inputBox.setPadding(new Insets(5));
            inputBox.setAlignment(Pos.CENTER_LEFT);

            VBox main = new VBox(10, scrollPane, inputBox);
            main.setPadding(new Insets(10));

            List<Object> history = groupHistory.computeIfAbsent(groupKey, k -> new ArrayList<>());
            for (Object m : history) {
                if (m instanceof String)
                    addMessage(vbox, (String)m, null);
                else if (m instanceof Image)
                    addMessage(vbox, null, (Image)m);
            }

            groupChats.put(groupKey, groupStage);

            // Envoi message texte au groupe
            sendBtn.setOnAction(e -> {
                String msg = chatField.getText().trim();
                if (!msg.isEmpty() && clientName != null) {
                    Set<String> uniqDest = new LinkedHashSet<>(selectedClientsFullId);
                    for (String destFullId : uniqDest) {
                        sendUDPMessage(clientName + "->" + destFullId + ":" + msg);
                    }
                    groupHistory.get(groupKey).add("🔒 Moi → " + String.join(", ", uniqDest) + " : " + msg);
                    addMessage(vbox, "🔒 Moi → " + String.join(", ", uniqDest) + " : " + msg, null);
                    chatField.clear();
                }
            });

            // Envoi d’image au groupe
            imageBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Choisir une image");
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
                );
                File file = fc.showOpenDialog(groupChats.get(groupKey));
                if (file != null && clientName != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        Set<String> uniqDest = new LinkedHashSet<>(selectedClientsFullId);
                        for (String destFullId : uniqDest) {
                            String imgMsg = "IMG:" + destFullId + ":" + file.getName() + ":" + base64;
                            sendUDPMessage(clientName + "->" + destFullId + ":" + imgMsg);
                        }
                        Image image = new Image(new java.io.ByteArrayInputStream(bytes));
                        groupHistory.get(groupKey).add("📷 Moi → " + String.join(", ", uniqDest) + " : " + file.getName());
                        groupHistory.get(groupKey).add(image);
                        addMessage(vbox, "📷 Moi → " + String.join(", ", uniqDest) + " : " + file.getName(), image);
                    } catch (Exception ex) {
                        addMessage(vbox, "❌ Erreur envoi image : " + ex.getMessage(), null);
                    }
                }
            });

            // Envoi GIF animé au groupe
            gifBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Choisir un GIF animé");
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("GIF animé", "*.gif")
                );
                File file = fc.showOpenDialog(groupChats.get(groupKey));
                if (file != null && clientName != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        Set<String> uniqDest = new LinkedHashSet<>(selectedClientsFullId);
                        for (String destFullId : uniqDest) {
                            String gifMsg = "GIF:" + destFullId + ":" + file.getName() + ":" + base64;
                            sendUDPMessage(clientName + "->" + destFullId + ":" + gifMsg);
                        }
                        Image image = new Image(new java.io.ByteArrayInputStream(bytes));
                        groupHistory.get(groupKey).add("🎞️ Moi → " + String.join(", ", uniqDest) + " : " + file.getName());
                        groupHistory.get(groupKey).add(image);
                        addMessage(vbox, "🎞️ Moi → " + String.join(", ", uniqDest) + " : " + file.getName(), image);
                    } catch (Exception ex) {
                        addMessage(vbox, "❌ Erreur envoi GIF : " + ex.getMessage(), null);
                    }
                }
            });

            groupStage.setScene(new Scene(main, 450, 350));
            groupVBoxes.put(groupKey, vbox);

            groupStage.setOnCloseRequest(e -> {
                groupChats.remove(groupKey);
                groupVBoxes.remove(groupKey);
            });
        } else {
            vbox = groupVBoxes.get(groupKey);
            messageKeys.clear();
            vbox.getChildren().clear();
            for (Object m : groupHistory.get(groupKey)) {
                if (m instanceof String)
                    addMessage(vbox, (String)m, null);
                else if (m instanceof Image)
                    addMessage(vbox, null, (Image)m);
            }
        }

        groupStage.show();
        groupStage.toFront();
        deselectAllCheckboxes();
    }

    // Ouvre la discussion privée avec client sélectionné (identifiant complet nom##port)
    private void openPrivateChat(String clientFullId) {
        messageKeys.clear();
        Stage chatStage = privateChats.get(clientFullId);
        VBox vbox;

        if (chatStage == null) {
            chatStage = new Stage();
            chatStage.setTitle("Chat privé avec " + clientFullId);

            vbox = new VBox(5);
            vbox.setPadding(new Insets(8));
            ScrollPane scrollPane = new ScrollPane(vbox);
            scrollPane.setFitToWidth(true);

            TextField chatField = new TextField();
            chatField.setPromptText("Message privé...");
            Button sendBtn = new Button("Envoyer");
            Button imageBtn = new Button("📷 Image");
            Button gifBtn = new Button("GIF animé");

            HBox inputBox = new HBox(10, chatField, sendBtn, imageBtn, gifBtn);
            inputBox.setPadding(new Insets(5));
            inputBox.setAlignment(Pos.CENTER_LEFT);

            VBox main = new VBox(10, scrollPane, inputBox);
            main.setPadding(new Insets(10));

            List<Object> history = privateHistory.computeIfAbsent(clientFullId, k -> new ArrayList<>());
            for (Object m : history) {
                if (m instanceof String)
                    addMessage(vbox, (String)m, null);
                else if (m instanceof Image)
                    addMessage(vbox, null, (Image)m);
            }

            privateChats.put(clientFullId, chatStage);

            // Envoi message texte privé
            sendBtn.setOnAction(e -> {
                String msg = chatField.getText().trim();
                if (!msg.isEmpty() && clientName != null) {
                    sendUDPMessage(clientName + "->" + clientFullId + ":" + msg);
                    privateHistory.get(clientFullId).add("🔒 Moi → " + clientFullId + " : " + msg);
                    addMessage(vbox, "🔒 Moi → " + clientFullId + " : " + msg, null);
                    chatField.clear();
                }
            });

            // Envoi image privée
            imageBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Choisir une image");
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
                );
                File file = fc.showOpenDialog(privateChats.get(clientFullId));
                if (file != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        String imgMsg = "IMG:" + clientFullId + ":" + file.getName() + ":" + base64;
                        sendUDPMessage(clientName + "->" + clientFullId + ":" + imgMsg);
                        Image image = new Image(new java.io.ByteArrayInputStream(bytes));
                        privateHistory.get(clientFullId).add("📷 Moi → " + clientFullId + " : " + file.getName());
                        privateHistory.get(clientFullId).add(image);
                        addMessage(vbox, "📷 Moi → " + clientFullId + " : " + file.getName(), image);
                    } catch (Exception ex) {
                        addMessage(vbox, "❌ Erreur envoi image : " + ex.getMessage(), null);
                    }
                }
            });

            // Envoi GIF animé privé
            gifBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Choisir un GIF animé");
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("GIF animé", "*.gif")
                );
                File file = fc.showOpenDialog(privateChats.get(clientFullId));
                if (file != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        String gifMsg = "GIF:" + clientFullId + ":" + file.getName() + ":" + base64;
                        sendUDPMessage(clientName + "->" + clientFullId + ":" + gifMsg);
                        Image image = new Image(new java.io.ByteArrayInputStream(bytes));
                        privateHistory.get(clientFullId).add("🎞️ Moi → " + clientFullId + " : " + file.getName());
                        privateHistory.get(clientFullId).add(image);
                        addMessage(vbox, "🎞️ Moi → " + clientFullId + " : " + file.getName(), image);
                    } catch (Exception ex) {
                        addMessage(vbox, "❌ Erreur envoi GIF : " + ex.getMessage(), null);
                    }
                }
            });

            chatStage.setScene(new Scene(main, 400, 300));
            privateVBoxes.put(clientFullId, vbox);

            chatStage.setOnCloseRequest(e -> {
                privateChats.remove(clientFullId);
                privateVBoxes.remove(clientFullId);
            });
        } else {
            vbox = privateVBoxes.get(clientFullId);
            messageKeys.clear();
            vbox.getChildren().clear();
            for (Object m : privateHistory.get(clientFullId)) {
                if (m instanceof String)
                    addMessage(vbox, (String)m, null);
                else if (m instanceof Image)
                    addMessage(vbox, null, (Image)m);
            }
        }

        chatStage.show();
        chatStage.toFront();
        deselectAllCheckboxes();
    }

    // Décoche tous les clients après chat ouvert
    private void deselectAllCheckboxes() {
        for (CheckBox cb : clientCheckBoxes.values()) {
            cb.setSelected(false);
        }
    }

    // =================== Réception des messages UDP ========================
    private void receiveMessages() {
        while (!socket.isClosed()) {
            try {
                byte[] buffer = new byte[65507]; // tampon pour recevoir le paquet UDP (limite UDP)
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // réception UDP (DatagramSocket)
                String msg = new String(packet.getData(), 0, packet.getLength());

                Platform.runLater(() -> {
                    if (msg.startsWith("CLIENTS:")) {
                        String clientsStr = msg.substring(8);
                        String[] clientsArray = clientsStr.split(",");
                        updateClientsList(clientsArray);
                        return;
                    }
                    if (msg.startsWith("🔒 ")) {
                        if (msg.contains("IMG:")) {
                            handlePrivateImage(msg); // Gestion image privée
                        } else if (msg.contains("GIF:")) {
                            handlePrivateGif(msg); // Gestion GIF privé
                        } else {
                            handlePrivateMessage(msg); // Message privé texte
                        }
                        return;
                    }
                    addMessage(messagesArea, msg, null); // message public
                });
            } catch (SocketTimeoutException e) {} // ignore timeouts
            catch (Exception e) {
                Platform.runLater(() -> addMessage(messagesArea, "❌ Erreur réception : " + e.getMessage(), null));
            }
        }
    }

    // =================== Gestion affichage des messages reçus privés ========================

    // Message privé texte reçu
    private void handlePrivateMessage(String msg) {
        if (msg.contains("→ Vous")) {
            int arrowIndex = msg.indexOf("→ Vous");
            if (arrowIndex > 0) {
                String sender = msg.substring(2, arrowIndex).trim();
                String senderFullId = sender; // Identifiant complet
                List<Object> history = privateHistory.computeIfAbsent(senderFullId, k -> new ArrayList<>());
                history.add(msg);

                VBox vbox = privateVBoxes.get(senderFullId);
                if (vbox == null) {
                    openPrivateChat(senderFullId);
                } else {
                    addMessage(vbox, msg, null); 
                }
            }
        }
    }

    // Message privé image reçu
    private void handlePrivateImage(String msg) {
        try {
            int arrowIndex = msg.indexOf("→ Vous");
            if (arrowIndex <= 0) return;
            String sender = msg.substring(2, arrowIndex).trim();
            String senderFullId = sender;
            int idxImg = msg.indexOf("IMG:", arrowIndex);
            if (idxImg < 0) return;
            String imgPart = msg.substring(idxImg + 4).trim();
            String[] p = imgPart.split(":", 3);
            if (p.length < 3) return;
            String fileName = p[1];
            String base64 = p[2];
            byte[] bytes = Base64.getDecoder().decode(base64);
            Image image = new Image(new java.io.ByteArrayInputStream(bytes));
            List<Object> history = privateHistory.computeIfAbsent(senderFullId, k -> new ArrayList<>());
            history.add("📷 " + senderFullId + " → Vous : " + fileName);
            history.add(image);

            VBox vbox = privateVBoxes.get(senderFullId);
            if (vbox == null) {
                openPrivateChat(senderFullId);
            } else {
                addMessage(vbox, "📷 " + senderFullId + " → Vous : " + fileName, image);
            }
        } catch (Exception ex) {
            addMessage(messagesArea, "❌ Erreur réception image : " + ex.getMessage(), null);
        }
    }

    // Message privé GIF reçu
    private void handlePrivateGif(String msg) {
        try {
            int arrowIndex = msg.indexOf("→ Vous");
            if (arrowIndex <= 0) return;
            String sender = msg.substring(2, arrowIndex).trim();
            String senderFullId = sender;
            int idxGif = msg.indexOf("GIF:", arrowIndex);
            if (idxGif < 0) return;
            String gifPart = msg.substring(idxGif + 4).trim();
            String[] p = gifPart.split(":", 3);
            if (p.length < 3) return;
            String fileName = p[1];
            String base64 = p[2];
            byte[] bytes = Base64.getDecoder().decode(base64);
            Image image = new Image(new java.io.ByteArrayInputStream(bytes));
            List<Object> history = privateHistory.computeIfAbsent(senderFullId, k -> new ArrayList<>());
            history.add("🎞️ " + senderFullId + " → Vous : " + fileName);
            history.add(image);

            VBox vbox = privateVBoxes.get(senderFullId);
            if (vbox == null) {
                openPrivateChat(senderFullId);
            } else {
                addMessage(vbox, "🎞️ " + senderFullId + " → Vous : " + fileName, image);
            }
        } catch (Exception ex) {
            addMessage(messagesArea, "❌ Erreur réception GIF : " + ex.getMessage(), null);
        }
    }

    // ============== ENVOI DES MESSAGES UDP vers serveur ===============
    private void sendUDPMessage(String message) {
        new Thread(() -> {
            try {
                byte[] sendData = message.getBytes(); // message transformé en bytes
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort); // paquet UDP à envoyer
                socket.send(sendPacket); // envoi UDP (DatagramSocket)
            } catch (Exception ex) {
                Platform.runLater(() -> addMessage(messagesArea, "❌ Erreur UDP : " + ex.getMessage(), null));
            }
        }).start();
    }

    // =========== Arrêt et fermeture des sockets à la fermeture ============
    @Override
    public void stop() throws Exception {
        if (socket != null && !socket.isClosed()) socket.close();
        super.stop();
    }

    // =========== Lancement principal de l'application JavaFX ==============
    public static void main(String[] args) {
        launch(args); // démarre l’application JavaFX (Application)
    }
}


