package tcp_mini_project;

//Pour la gestion des Entrées/Sorties : fichiers, réseaux, flux binaires, flux texte, etc.
import java.io.*; // (Obligatoire pour : BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter, DataInputStream, DataOutputStream, IOException, File, etc.)

//Pour la gestion du réseau : sockets réseau côté serveur et client
import java.net.*; // (Obligatoire pour : ServerSocket pour écouter les connexions, Socket pour gérer la connexion de chaque client)

//Pour les structures de données basiques : List, Set, Map, ArrayList, HashSet, TreeSet, etc.
import java.util.*; // (Utilisé ici pour : ArrayList, HashSet, TreeSet, Map, Set, List, etc.)

//Pour avoir une liste d'objets sûrs pour le multithread, modifiable par plusieurs threads sans bug
import java.util.concurrent.CopyOnWriteArrayList; // (Utile ici : liste de clients qui peut être modifiée depuis plusieurs threads en sécurité)

//Pour avoir un dictionnaire/mapping sûr pour le multithread (clé=>valeur), modifiable sans bug entre plusieurs threads
import java.util.concurrent.ConcurrentHashMap; // (Utile ici pour stocker/mettre à jour les id/utilisateurs, etc.)

public class Serveur {

	// Liste de tous les clients connectés (thread safe) (CopyOnWriteArrayList)
	static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
	// Compteur du plus grand ID attribué à chaque nom brut (par exemple : nom = "yassine", valeur = 2 si yassine#2 existe)
	static final Map<String, Integer> nameCount = new ConcurrentHashMap<>();
	// Pour chaque nom brut, stocke les ID "libres"/recyclables (TreeSet car on veut le plus petit dispo)
	static final Map<String, TreeSet<Integer>> freeIds = new ConcurrentHashMap<>();

	public static void main(String[] args) {
 	System.out.println("=== Chat Server Starting on port 1234 ===");
 	// Socket serveur à l'écoute sur le port 1234 (ServerSocket)
 	try (ServerSocket serverSocket = new ServerSocket(1234)) {
     	System.out.println("Server is running. Waiting for clients...\n");
     	// Boucle principale d'attente de connexion client
     	while (true) {
         	// Quand un client arrive, accepte la connexion et crée un gestionnaire dédié (Socket)
         	Socket socket = serverSocket.accept();
         	ClientHandler handler = new ClientHandler(socket);
         	clients.add(handler); // Rajoute ce client à la liste active
         	new Thread(handler).start(); // Lance le thread de gestion du client
     	}
 	} catch (IOException e) {
     	// Gestion d'erreurs (problème d'ouverture/fermeture de sockets)
     	System.err.println("Server crashed: " + e.getMessage());
 	}
	}

	// Diffuse un message à tous les clients connectés
	public static void broadcast(String message) {
 	for (ClientHandler c : clients) c.sendText(message);
 	System.out.println("[SERVER BROADCAST] " + message);
	}

	// Retire un client de la liste, marque son id comme réutilisable, et notifie tout le monde
	public static void removeClient(ClientHandler client) {
 	clients.remove(client);
 	if (client.rawName != null && client.clientNumericId > 0) {
     	// Ajoute l'ID à la liste des IDs "recyclables" pour ce nom
     	freeIds.computeIfAbsent(client.rawName, k -> new TreeSet<>()).add(client.clientNumericId);
 	}
 	String msg = "Left: @" + client.getDisplayName() + " has left the chat.";
 	broadcast(msg);
 	System.out.println("[SERVER] @" + client.getDisplayName() + " disconnected and id #" + client.clientNumericId + " is now reusable.");
	}

	// Attribue un ID le plus petit possible (réutilisable si laissé dispo par un autre utilisateur)
	public static synchronized int assignIdForName(String name) {
 	TreeSet<Integer> freed = freeIds.getOrDefault(name, new TreeSet<>());
 	int id;
 	if (!freed.isEmpty()) {
     	id = freed.pollFirst(); // On recycle le plus petit ID dispo
     	if (freed.isEmpty()) freeIds.remove(name);
     	else freeIds.put(name, freed);
 	} else {
     	id = nameCount.getOrDefault(name, 0) + 1; // Sinon on incrémente
     	nameCount.put(name, id);
 	}
 	return id;
	}
}

//Gère un client connecté, chaque client à son thread dédié
class ClientHandler implements Runnable {
	private final Socket socket; // Socket spécifique à ce client (Socket)
	private BufferedWriter textWriter; // Envoie de texte au client connecté (BufferedWriter)
	private BufferedReader textReader; // Lecture du texte venant du client (BufferedReader)
	private DataOutputStream dataOut;  // Envoie de fichiers binaires (DataOutputStream)
	private DataInputStream dataIn;	// Lecture de fichiers binaires reçus (DataInputStream)
	public String rawName = "Unknown"; // Nom brut (pas #id)
	public int clientNumericId = -1; // Numéro attribué par le serveur

	// Constructeur : initialise tous les flux pour communiquer avec ce client
	public ClientHandler(Socket socket) {
 	this.socket = socket;
 	try {
     	textWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
     	textReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
     	dataOut = new DataOutputStream(socket.getOutputStream());
     	dataIn = new DataInputStream(socket.getInputStream());
 	} catch (IOException e) {
     	closeEverything();
 	}
	}

	// Méthode qui tourne dans le thread associe à ce client
	@Override
	public void run() {
 	try {
     	// Attribution du pseudo + id = handle (ex: yassine#1)
     	String proposedName = textReader.readLine();
     	if (proposedName == null || proposedName.trim().isEmpty()) proposedName = "User";
     	rawName = proposedName.replaceAll("\\s", "");
     	clientNumericId = Serveur.assignIdForName(rawName);

     	sendText("Welcome, your handle is: @" + getDisplayName());
     	printUserList();

     	System.out.println("[SERVER] @" + getDisplayName() + " connected from " + socket.getInetAddress());
     	Serveur.broadcast("Joined: @" + getDisplayName() + " has joined the chat!");

     	// Boucle principale : traite chaque message reçu
     	String line;
     	while ((line = textReader.readLine()) != null) {
         	if (line.equalsIgnoreCase("/quit")) break; // Quitter

         	if (line.equals("/list")) {
             	printUserList(); // Affiche la liste des clients à ce client
         	} else if (line.startsWith("FILE_HEADER:")) {
             	handleFileTransfer(line); // Réception et dispatch d'un fichier
         	} else if (line.startsWith("@")) {
             	// Message privé explicite
             	handlePrivateMessage(line);
         	} else {
             	// Message public : on l'envoie à tous !
             	String genMsg = "@" + getDisplayName() + ": " + line;
             	Serveur.broadcast(genMsg);
             	System.out.println("[SERVER MSG] " + genMsg);
         	}
     	}
 	} catch (Exception e) {
     	System.out.println("[SERVER] @" + getDisplayName() + " disconnected.");
 	} finally {
     	Serveur.removeClient(this); // Nettoie côté serveur
     	closeEverything();
     	System.out.println("[SERVER] @" + getDisplayName() + " cleanup done.");
 	}
	}

	// Traite les transferts de fichiers (header + bytes)
	private void handleFileTransfer(String header) {
 	try {
     	String[] parts = header.split(":", 5);
     	if (parts.length < 5) return;
     	String sender = parts[1];
     	String fileName = parts[2];
     	long fileSize = Long.parseLong(parts[3]);
     	String recipientsList = parts[4];
     	List<ClientHandler> targets = new ArrayList<>();

     	// Décide qui reçoit ce fichier (broadcast ou ciblé)
     	if (recipientsList.equalsIgnoreCase("all")) {
         	targets.addAll(Serveur.clients);
     	} else {
         	Set<String> ids = new HashSet<>();
         	for (String token : recipientsList.split("[,\\s]+")) {
             	String clean = token.trim().replaceAll("@", "");
             	if (!clean.isEmpty()) ids.add(clean);
         	}
         	for (ClientHandler c : Serveur.clients) {
             	if (ids.contains(c.getDisplayName())) targets.add(c);
         	}
     	}
     	if (!recipientsList.equalsIgnoreCase("all")) {
         	targets.remove(this);
     	}

     	System.out.println("[SERVER FILE] @" + getDisplayName() + " sent '" + fileName + "' (" + formatSize(fileSize) + ") to " +
         	(recipientsList.equalsIgnoreCase("all") ? "everyone" : recipientsList));

     	// Envoyer l'en-tête fichier à chacun
     	for (ClientHandler t : targets) t.sendText(header);

     	// Transfère chaque octet du fichier aux destinataires (DataInputStream/DataOutputStream)
     	byte[] buffer = new byte[8192];
     	long remaining = fileSize;
     	while (remaining > 0) {
         	int read = dataIn.read(buffer, 0, (int)Math.min(buffer.length, remaining));
         	if (read == -1) break;
         	for (ClientHandler t : targets) {
             	try {
                 	t.dataOut.write(buffer, 0, read);
                 	t.dataOut.flush();
             	} catch (IOException ignored) {}
         	}
         	remaining -= read;
     	}
     	// On remet à zéro les flux pour éviter les bugs de blocage/lecture alternée texte-binaire
     	dataIn = new DataInputStream(socket.getInputStream());
     	textReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

     	sendText("File sent: " + fileName);
 	} catch (Exception e) {
     	e.printStackTrace();
 	}
	}

	// Messages privés à un ou plusieurs destinataires
	private void handlePrivateMessage(String message) {
 	String[] p = message.split("\\s+", 2);
 	if (p.length < 2) return;
 	String content = p[1];
 	List<String> recipients = Arrays.stream(p[0].split("@"))
         	.filter(s -> !s.isEmpty()).map(String::trim).toList();

 	List<String> notFound = new ArrayList<>();
 	for (String r : recipients) {
     	boolean ok = false;
     	for (ClientHandler c : Serveur.clients) {
         	if (c.getDisplayName().equalsIgnoreCase(r)) {
             	c.sendText("[Private from @" + getDisplayName() + "] " + content);
             	if (c != this) this.sendText("[Private to @" + c.getDisplayName() + "] " + content);
             	System.out.println("[SERVER PRIVATE] @" + getDisplayName() + " -> @" + c.getDisplayName() + ": " + content);
             	ok = true; break;
         	}
     	}
     	if (!ok) notFound.add(r);
 	}
 	if (recipients.size() == 1 && recipients.get(0).equalsIgnoreCase(getDisplayName())) {
     	this.sendText("[Private to @" + getDisplayName() + "] " + content);
     	System.out.println("[SERVER PRIVATE] @" + getDisplayName() + " -> self: " + content);
 	}
 	if (!notFound.isEmpty()) sendText("Not found: @" + String.join(", @", notFound));
	}

	// Envoie un message texte "simple" (une ligne) au client correspondant à ce handler
	public void sendText(String msg) {
 	try {
     	if (textWriter != null) {
         	textWriter.write(msg + "\n");
         	textWriter.flush();
     	}
 	} catch (IOException ignored) {}
	}

	// Renvoie le pseudo + id au format attendy (@nom#id)
	public String getDisplayName() { return rawName + "#" + clientNumericId; }

	// Liste tous les clients connectés à ce client
	private void printUserList() {
 	sendText("=== Connected users ===");
 	for (ClientHandler c : Serveur.clients) sendText(" - @" + c.getDisplayName());
 	sendText("=======================");
 	System.out.println("[SERVER] Sent user list to @" + getDisplayName());
	}

	// Ferme la socket réseau pour terminer proprement la connexion avec ce client
	private void closeEverything() {
 	try { if (socket != null) socket.close(); } catch (IOException ignored) {}
	}

	// Affiche une taille lisible : B, KB, MB, GB selon la taille du fichier envoyé/reçu
	private String formatSize(long b) {
 	if (b < 1024) return b + " B";
 	if (b < 1024*1024) return String.format("%.1f KB", b/1024.0);
 	if (b < 1024*1024*1024) return String.format("%.1f MB", b/(1024.0*1024));
 	return String.format("%.1f GB", b/(1024.0*1024*1024));
	}

}