package tcp_mini_project;

//Pour gérer les entrées/sorties de fichiers, flux texte et binaire, PrintWriter/FileInputStream, etc.
import java.io.*; // (nécessaire pour BufferedReader, PrintWriter, File, FileInputStream, FileOutputStream, DataInputStream, etc.)
//Pour gérer les sockets réseau TCP/IP client/serveur
import java.net.*; // (Socket, ServerSocket, communication réseau)
//Pour lire l'entrée utilisateur sur la console
import java.util.Scanner; // (Scanner pour lire l'entrée texte côté utilisateur)

public class Client {

	// Attribut qui contiendra l'identifiant unique envoyé par le serveur (ex: yassine#1)
	private static String handle = null;

	// C'est ici que tout commence : point d'entrée du client (fonction spéciale en Java)
	public static void main(String[] args) {
 	// La connexion au serveur ainsi que la fermeture auto des ressources se fait ici
 	try (Socket socket = new Socket("localhost", 1234)) { // (Socket : permet de se connecter à l'adresse et port du serveur)
     	System.out.println("Connected to server!");

     	// Permet de recevoir les fichiers en binaire/byte par byte (DataInputStream : pour lire efficacement des octets)
     	DataInputStream dataInput = new DataInputStream(socket.getInputStream());

     	// Sert à lire ce que l'utilisateur tape dans la console (Scanner : lecture de l'entrée standard, System.in)
     	Scanner scanner = new Scanner(System.in);

     	// Dès le début, demande le nom utilisateur puis l'envoie (texte) au serveur
     	System.out.print("Enter your name: ");
     	String username = scanner.nextLine();
     	// On utilise PrintWriter pour envoyer le nom (println ajoute le saut de ligne)
     	new PrintWriter(socket.getOutputStream(), true).println(username);

     	// Thread séparé : toujours à l'écoute des messages entrants du serveur (non bloquant pour l'utilisateur)
     	Thread receiver = new Thread(() -> {
         	try {
             	// BufferedReader permet de lire les lignes de texte efficacement (ligne par ligne)
             	BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             	String line;
             	// Boucle infinie tant que le serveur envoie des lignes à ce client
             	while ((line = reader.readLine()) != null) {
                 	// Détection du handle/ID attribué par le serveur
                 	if (line.startsWith("Welcome, your handle is: @")) {
                     	handle = line.substring("Welcome, your handle is: @".length()).trim();
                     	System.out.println("[Your handle: @" + handle + "]");
                     	continue;
                 	}
                 	// Début de la réception d'un fichier (nouvelle commande/entête spéciale)
                 	if (line.startsWith("FILE_HEADER:")) {
                     	handleIncomingFile(line, dataInput); // (voir la méthode juste en-dessous)
                     	continue;
                 	}
                 	// Tous les autres messages texte sont affichés tels quels
                 	System.out.println(line);

                 	// Gestion spéciale de la liste des utilisateurs connectés (affiche tout ce qui vient après === sous forme de liste)
                 	if (line.startsWith("=== Connected users ===")) {
                     	while ((line = reader.readLine()) != null) {
                         	if (line.startsWith("=======================")) break; // Fin de la liste
                         	System.out.println(line);
                     	}
                     	System.out.print("> ");
                 	}
             	}
         	} catch (Exception e) {
             	System.out.println("Disconnected.");
         	}
     	});
     	// Permet que le thread ne bloque jamais la fermeture du programme principal
     	receiver.setDaemon(true);
     	receiver.start();

     	// Boucle principale pour envoyer des commandes
     	String input;
     	while (true) {
         	System.out.print("> ");
         	input = scanner.nextLine(); // Toujours prêt à recevoir une commande utilisateur

         	// Quitter proprement (/quit ferme la connexion client/serveur)
         	if (input.equalsIgnoreCase("/quit")) {
             	try {
                 	new PrintWriter(socket.getOutputStream(), true).println("/quit"); // Avertit le serveur
                 	socket.close();
             	} catch (IOException e) {}
             	System.out.println("Disconnected. Bye!");
             	System.exit(0); // Arrête le programme
         	}

         	// Afficher les utilisateurs connectés
         	if (input.equalsIgnoreCase("/list")) {
             	new PrintWriter(socket.getOutputStream(), true).println("/list");
         	// Commande pour envoyer un fichier à un ou plusieurs utilisateurs
         	} else if (input.startsWith("/file ")) {
             	String path = input.substring(6).trim();
             	System.out.print("Send to (comma-separated @name#id or 'all'): ");
             	String recipients = scanner.nextLine().trim();
             	sendFile(path, recipients, socket.getOutputStream()); // (voir fonction juste en-dessous)
         	} else {
             	// Message texte : envoie simplement la ligne au serveur
             	new PrintWriter(socket.getOutputStream(), true).println(input);
         	}
     	}
 	} catch (Exception e) {
     	System.out.println("Cannot connect to server.");
 	}
	}

	// Fonction qui gère entièrement l'envoi de fichiers binaires
	// filePath : chemin du fichier sur disque, recipients : à qui envoyer, out : flux d'envoi (OutputStream)
	private static void sendFile(String filePath, String recipients, OutputStream out) throws IOException {
 	File file = new File(filePath); // (File : gérer le nom et le chemin du fichier)
 	if (!file.exists()) {
     	System.out.println("File not found: " + filePath);
     	return;
 	}

 	PrintWriter writer = new PrintWriter(out, true); // (PrintWriter : écrire l'entête textuelle facilement)
 	String senderHandle = (handle != null) ? handle : "Unknown";
 	String header = "FILE_HEADER:" + senderHandle + ":" + file.getName() + ":" + file.length() + ":" + recipients;
 	writer.println(header); // Envoie l'entête du fichier au serveur

 	// Transmission du fichier octet par octet (FileInputStream/OutputStream)
 	try (FileInputStream fis = new FileInputStream(file)) {
     	byte[] buffer = new byte[8192];
     	int bytes;
     	while ((bytes = fis.read(buffer)) != -1) {
         	out.write(buffer, 0, bytes);
     	}
     	out.flush();
 	}
 	System.out.println("File sent: " + file.getName());
	}

	// Fonction qui s'occupe de recevoir le fichier complet après réception d'un FILE_HEADER:
	// header : ligne d'en-tête reçue, dis : (DataInputStream) réception des données binaires
	private static void handleIncomingFile(String header, DataInputStream dis) throws IOException {
 	String[] parts = header.split(":", 5); // Sépare pour extraire qui envoie, quel nom, quelle taille etc.
 	String sender = parts[1];
 	String fileName = parts[2];
 	long size = Long.parseLong(parts[3]);

 	new File("downloads").mkdirs(); // Crée le dossier de destination si inexistant
 	File savedFile = new File("downloads/" + System.currentTimeMillis() + "_" + fileName);

 	// Lecture stricte du flux binaire (DataInputStream fournit read(byte[]...) pour garder la structure du fichier)
 	try (FileOutputStream fos = new FileOutputStream(savedFile)) {
     	byte[] buffer = new byte[8192];
     	long remaining = size;
     	while (remaining > 0) {
         	int read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
         	if (read == -1) break; // Fin anormale
         	fos.write(buffer, 0, read);
         	remaining -= read;
     	}
 	}

 	System.out.println("\nFile received from @" + sender + ": " + fileName);
 	System.out.println("Saved as: " + savedFile.getName());
 	if (fileName.matches(".*\\.(jpg|png|gif|mp4|mp3).*")) {
     	System.out.println("Open the 'downloads' folder to view/play it!");
 	}
 	System.out.print("> ");
	}
}



