package udp_mini_projet;

//================== IMPORTS AVEC EXPLICATION ==================
import java.net.*;           // Tout ce qui est UDP : DatagramSocket (socket UDP), DatagramPacket (paquet UDP), InetSocketAddress (adresse: IP+port)
import java.util.*;          // Collections Java : HashMap, ArrayList, Set, Map, List… servent à stocker clients, historiques, etc.
import javafx.application.Application; // Pour créer une appli JavaFX
import javafx.application.Platform;    // Pour manipuler l'UI JavaFX depuis les threads secondaires (avec runLater)
import javafx.collections.*;           // Pour l’ObservableList utilisé en ListView (liste des clients dynamiques)
import javafx.geometry.Insets;         // Pour gérer les marges dans les layouts JavaFX (VBox, etc)
import javafx.scene.Scene;             // Pour gérer la fenêtre principale JavaFX
import javafx.scene.control.*;         // Tous les contrôles UI JavaFX (Label, Button, ListView…)
import javafx.scene.image.Image;       // Pour manipuler et afficher des images/gifs reçus ou envoyés
import javafx.scene.image.ImageView;   // Pour montrer l'image/gif dans ta UI
import javafx.scene.layout.*;          // Les layouts (VBox, HBox, BorderPane, etc) pour organiser graphiquement
import javafx.stage.Stage;             // Fenêtre principale JavaFX
import java.io.ByteArrayInputStream;   // Pour lire des images en mémoire depuis byte[]
import java.util.Base64;               // Encodage/décodage en Base64, utilisé pour transporter images/GIF via UDP
//=================== CLASSE PRINCIPALE SERVEUR JAVA =====================
public class ServeurUDP_Multi extends Application {
// =========== Variables UDP et état serveur ===========
private DatagramSocket socket; // (java.net) Le socket UDP qui attend les messages des clients
private int serverPort = 9876; // Port écouté par le serveur (doit correspondre au client)
// Map des clients connectés (clé = identifiant complet nom##port, valeur = adresse IP+port)
private Map<String, InetSocketAddress> clients = new HashMap<>();
// Historique des messages/images/gifs pour chaque client (clé = identifiant complet)
private Map<String, List<Object>> clientHistories = new HashMap<>();
// Liste observable des clients (JavaFX) pour affichage dynamique dans la UI serveur
private ObservableList<String> clientsList = FXCollections.observableArrayList();
// =========== Variables UI JavaFX ===========
private ListView<String> clientListView; // Liste affichée des clients
private VBox messagesArea;               // Zone de messages principale (non utilisé ici)
private Map<String, VBox> clientVBoxes = new HashMap<>(); // Un VBox par client pour son historique graphique
private VBox logVBox;                    // Zone historique/journal du serveur (affiche tous messages, images reçues)
private ScrollPane scrollPane;           // Pour faire défiler la zone messages
private volatile boolean running = true; // Contrôle du thread d'écoute UDP
// ============= Point d'entrée Application JavaFX =============
@Override
public void start(Stage primaryStage) {
    BorderPane root = new BorderPane();
    // UI : liste des clients à gauche
    clientListView = new ListView<>(clientsList);
    clientListView.setPrefWidth(200);
    root.setLeft(clientListView);
    // UI : zone centrale de messages
    messagesArea = new VBox(5);
    messagesArea.setPadding(new Insets(8));
    scrollPane = new ScrollPane(messagesArea);
    scrollPane.setFitToWidth(true);
    root.setCenter(scrollPane);
    // UI : Journal serveur, en bas
    logVBox = new VBox(5);
    logVBox.setPadding(new Insets(8));
    ScrollPane logScroll = new ScrollPane(logVBox);
    logScroll.setPrefHeight(140);
    logScroll.setFitToWidth(true);
    // UI : fusion centre + journal
    VBox centerBox = new VBox(10, scrollPane, new Label("🖥️ Journal du serveur (texte ET images) :"), logScroll);
    centerBox.setPadding(new Insets(10));
    root.setCenter(centerBox);
    // Fenêtre principale
    Scene scene = new Scene(root, 950, 600);
    primaryStage.setTitle("Serveur UDP Multi-Clients (Journal images + GIF)");
    primaryStage.setScene(scene);
    primaryStage.show();
    // Sélection d’un client dans la UI pour voir historique individuel
    clientListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
        if (newVal != null) {
            actualiseHistoriqueVisuel(newVal); // Affiche l'historique du client sélectionné
        }
    });
    // Démarre le thread serveur UDP (écoute des paquets)
    new Thread(this::runServer).start();
}
// ========== Méthode pour afficher un message dans le VBox correspondant ==========
private void addMsg(VBox vbox, String msg, Image img) {
    ObservableList<javafx.scene.Node> children = vbox.getChildren();
    if (msg != null) {
        Label l = new Label(msg);
        l.setWrapText(true);
        vbox.getChildren().add(l);
    }
    if (img != null) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(250);
        iv.setPreserveRatio(true);
        vbox.getChildren().add(iv);
    }
}
// =========== Ajoute à la zone journal/server log ============
private void addLog(String msg, Image img) {
    Platform.runLater(() -> {
        if (msg != null) {
            Label l = new Label(msg);
            l.setWrapText(true);
            logVBox.getChildren().add(l);
        }
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(160);
            iv.setPreserveRatio(true);
            logVBox.getChildren().add(iv);
        }
        if (logVBox.getParent() instanceof ScrollPane)
            ((ScrollPane) logVBox.getParent()).setVvalue(1.0);
    });
}
// =========== Affiche l’historique graphique du client sélectionné ===========
private void actualiseHistoriqueVisuel(String selected) {
    VBox vb = clientVBoxes.computeIfAbsent(selected, k -> new VBox(5));
    vb.getChildren().clear();
    List<Object> historyList = clientHistories.getOrDefault(selected, new ArrayList<>());
    for (Object m : historyList) {
        if (m instanceof String)
            addMsg(vb, (String)m, null);
        else if (m instanceof Image)
            addMsg(vb, null, (Image)m);
    }
    scrollPane.setContent(vb);
}
// ================== Thread principale d'écoute UDP =====================
private void runServer() {
    try {
        socket = new DatagramSocket(serverPort, InetAddress.getByName("0.0.0.0")); // socket serveur UDP sur port 9876
        socket.setSoTimeout(2000); // timeout réception
        addLog("✅ Serveur démarré sur le port " + serverPort, null);
        while (running) {
            try {
                byte[] receiveData = new byte[65507];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                handleMessage(receivePacket);
            } catch (SocketTimeoutException e) { continue; }
            catch (Exception e) { if (running) addLog("❌ Erreur : " + e.getMessage(), null); }
        }
    } catch (Exception e) {
        addLog("❌ Exception serveur : " + e.getMessage(), null);
    } finally {
        if (socket != null && !socket.isClosed()) socket.close();
        addLog("🛑 Serveur arrêté.", null);
    }
}
// =================== Routage et historique des messages ===================
private void handleMessage(DatagramPacket receivePacket) throws Exception {
    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
    InetAddress clientAddr = receivePacket.getAddress();
    int clientPort = receivePacket.getPort();
    InetSocketAddress endpoint = new InetSocketAddress(clientAddr, clientPort);
    String[] parts = message.split(":", 2);
    String senderInfo = parts.length > 0 ? parts[0].trim() : "Inconnu";
    String clientMsg = parts.length > 1 ? parts[1].trim() : "";
    String senderFullId;
    if (senderInfo.contains("->")) senderFullId = senderInfo.split("->")[0].trim();
    else senderFullId = senderInfo;
    // =========== Gestion du routage (privé ou groupe) ===========
    if (senderInfo.contains("->")) { // Message à un ou plusieurs destinataires
        String[] names = senderInfo.split("->", 2);
        String destStr = names[1].trim();
        String[] destIds = destStr.split(",");
        Set<String> uniqueDestFullIds = new HashSet<>();
        for (String s : destIds) uniqueDestFullIds.add(s.trim());
        List<String> sentTo = new ArrayList<>();
        String displayRecips = String.join(", ", uniqueDestFullIds);
        // => 1. Envoi à chaque destinataire
        for (String destFullId : uniqueDestFullIds) {
            InetSocketAddress destAddr = clients.get(destFullId);
            if (destAddr != null) {
                String privateMsg = "🔒 " + senderFullId + " → Vous : " + clientMsg;
                socket.send(new DatagramPacket(privateMsg.getBytes(), privateMsg.getBytes().length, destAddr.getAddress(), destAddr.getPort()));
                sentTo.add(destFullId);
                clientHistories.computeIfAbsent(destFullId, k -> new ArrayList<>());
                List<Object> historyList = clientHistories.get(destFullId);
                // HISTORIQUE (pas de double-check des keys/doublons ici)
                if (clientMsg.startsWith("IMG:")) {
                    Image image = msgToImage(clientMsg);
                    String fileName = getImageFileName(clientMsg);
                    historyList.add("📷 " + senderFullId + " → Vous : " + fileName);
                    historyList.add(image);
                } else if (clientMsg.startsWith("GIF:")) {
                    Image image = msgToImage(clientMsg);
                    String fileName = getImageFileName(clientMsg);
                    historyList.add("🎞️ " + senderFullId + " → Vous : " + fileName);
                    historyList.add(image);
                } else {
                    historyList.add(senderFullId + " → Vous : " + clientMsg);
                }
                Platform.runLater(() -> actualiseHistoriqueVisuel(destFullId));
            }
        }
        // => 2. Historique pour l'expéditeur
        String senderUniqueId = senderInfo + "##" + clientPort;
        clientHistories.computeIfAbsent(senderUniqueId, k -> new ArrayList<>());
        List<Object> historyList = clientHistories.get(senderUniqueId);
        if (clientMsg.startsWith("IMG:")) {
            Image image = msgToImage(clientMsg);
            String fileName = getImageFileName(clientMsg);
            historyList.add("📷 " + senderFullId + " → " + displayRecips + " : " + fileName);
            historyList.add(image);
        } else if (clientMsg.startsWith("GIF:")) {
            Image image = msgToImage(clientMsg);
            String fileName = getImageFileName(clientMsg);
            historyList.add("🎞️ " + senderFullId + " → " + displayRecips + " : " + fileName);
            historyList.add(image);
        } else {
            historyList.add(senderFullId + " → " + displayRecips + " : " + clientMsg);
        }
        Platform.runLater(() -> actualiseHistoriqueVisuel(senderUniqueId));
        // => 3. Journal serveur
        if (clientMsg.startsWith("IMG:")) {
            Image image = msgToImage(clientMsg);
            String fileName = getImageFileName(clientMsg);
            addLog("📷 " + senderFullId + " → " + displayRecips + " : " + fileName, image);
        } else if (clientMsg.startsWith("GIF:")) {
            Image image = msgToImage(clientMsg);
            String fileName = getImageFileName(clientMsg);
            addLog("🎞️ " + senderFullId + " → " + displayRecips + " : " + fileName, image);
        } else {
            addLog(senderFullId + " → " + displayRecips + " : " + clientMsg, null);
        }
        // => 4. Confirmation à l'expéditeur
        String confirm = "✅ Message envoyé à " + String.join(", ", sentTo);
        socket.send(new DatagramPacket(confirm.getBytes(), confirm.getBytes().length, clientAddr, clientPort));
    } else { // Message public ou enregistrement du client
        String clientUniqueId = senderInfo + "##" + clientPort;
        clients.putIfAbsent(clientUniqueId, endpoint);
        clientHistories.putIfAbsent(clientUniqueId, new ArrayList<>());
        List<Object> historyList = clientHistories.get(clientUniqueId);
        // HISTORIQUE pour client local
        if (clientMsg.startsWith("IMG:")) {
            Image image = msgToImage(clientMsg);
            String fileName = getImageFileName(clientMsg);
            historyList.add("📷 " + senderFullId + " : " + fileName);
            historyList.add(image);
            addLog("📷 " + senderFullId + " : " + fileName, image);
        } else if (clientMsg.startsWith("GIF:")) {
            Image image = msgToImage(clientMsg);
            String fileName = getImageFileName(clientMsg);
            historyList.add("🎞️ " + senderFullId + " : " + fileName);
            historyList.add(image);
            addLog("🎞️ " + senderFullId + " : " + fileName, image);
        } else {
            historyList.add(senderFullId + " : " + clientMsg);
            addLog(senderFullId + " : " + clientMsg, null);
        }
        Platform.runLater(() -> actualiseHistoriqueVisuel(clientUniqueId));
        Platform.runLater(() -> {
            if (!clientsList.contains(clientUniqueId)) clientsList.add(clientUniqueId);
        });
        // Réponse d’acquittement au client sender
        String response = "📨 Serveur OK : " + clientMsg;
        socket.send(new DatagramPacket(response.getBytes(), response.getBytes().length, clientAddr, clientPort));
        // Envoie la liste des clients à TOUS (pour mise à jour UI côté client)
        sendClientsList();
    }
}
// =============== UTILITAIRES POUR IMAGES ET GIFS ================
private Image msgToImage(String imgMsg) {
    try {
        String[] p = imgMsg.split(":", 4);
        if (p.length < 4) return null;
        String base64 = p[3];
        byte[] bytes = Base64.getDecoder().decode(base64);
        return new Image(new ByteArrayInputStream(bytes));
    } catch (Exception e) {
        addLog("⚠️ Erreur décodage image: " + e.getMessage(), null);
        return null;
    }
}
private String getImageFileName(String imgMsg) {
    String[] p = imgMsg.split(":", 4);
    return (p.length > 2) ? p[2] : "";
}
private void sendClientsList() {
    for (Map.Entry<String, InetSocketAddress> entry : clients.entrySet()) {
        String currentId = entry.getKey();
        InetSocketAddress addr = entry.getValue();
        StringBuilder listStr = new StringBuilder("CLIENTS:");
        boolean first = true;
        for (String key : clients.keySet()) {
            if (!key.equals(currentId)) {
                if (!first) listStr.append(",");
                listStr.append(key);
                first = false;
            }
        }
        byte[] data = listStr.toString().getBytes();
        try {
            socket.send(new DatagramPacket(data, data.length, addr.getAddress(), addr.getPort()));
        } catch (Exception e) {
            addLog("⚠️ Erreur envoi liste clients : " + e.getMessage(), null);
        }
    }
}
@Override
public void stop() {
    running = false;
    if (socket != null && !socket.isClosed()) socket.close();
    addLog("🛑 Serveur arrêté manuellement.", null);
}
public static void main(String[] args) {
    launch(args); // Lancement de l’application graphique JavaFX
}
}

