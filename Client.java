
import java.io.*;
import java.net.*;
import java.util.*; 

public class Client {

     int serverPort;
     InetAddress ip=null; 
     Socket s; 
     ObjectOutputStream outputStream ;
     ObjectInputStream inputStream ;
     int peerID;
     int peer_listen_port;
     char FILE_VECTOR[];
     ArrayList<Connection> connectionList;
     ServerSocket listener;
    // To do , create each peers own ServerSocket listener to monitor for incoming peer requests. start a listener thread in main();
    // I used the ServerSocketHandler to handle both client-server and peer-to-peer listeners. You can use a separate class. 90% of the code is repeated.
    // For the individual connections, again you can re-use the Connection class, and add some event handlers to process event codes that will be used to distibguis betwwen peer-to-peer or cleint-server communications, or create a separate class called peerConnection. It is completely your choice.
    public static void main(String args[])
    {
        
        Client client = new Client();
        boolean runClient=true;
        Scanner input = new Scanner(System.in);
        if (args.length==0 || args.length % 2 == 1){
            System.out.println("Parameters Required/Incorrect Format. See usage list");
            System.exit(0);
        }

        client.cmdLineParser(args);

        try
        { 
            if (client.ip==null)
                client.ip = InetAddress.getByName("localhost"); 

            client.s = new Socket(client.ip, client.serverPort); 
            client.outputStream = new ObjectOutputStream(client.s.getOutputStream());
            client.inputStream = new ObjectInputStream(client.s.getInputStream());
            System.out.println("Connected to Server ..." +client.s); 

            Packet p = new Packet();
            p.event_type=0;
            p.sender=client.peerID;
            p.peer_listen_port=client.peer_listen_port;
            p.FILE_VECTOR = client.FILE_VECTOR;

            client.send_packet_to_server(p);

            System.out.println("Packet Sent");
            
            Thread r = new PacketHandler(client);
            r.start();
            
            Thread clientSocketHandler = new ClientSocketHandler(client);
            clientSocketHandler.start();
            
            
            while (runClient){
                
                System.out.println ("Enter query");
                char cmd=input.next().charAt(0);
                switch(cmd)
                {
                    case 'q':
                    System.out.println("Getting ready to quit ..." +client.s); 
                    client.send_quit_to_server();
                    runClient=false;
                    break;

                    case 'f':
                    System.out.println("Enter the file index you want ");
                    int findex = input.nextInt();
                    client.send_req_for_file(findex);
                    break;

                    default:
                    System.out.println("Command not recognized. Try again ");

                }
                //Packet p = (Packet) inputStream.readObject();
                //p.printPacket();
            }
        }
        catch(Exception e){ 
            e.printStackTrace(); 
        }
        input.close();

    }

    void send_packet_to_server(Packet p)
    {
        try
        { 
            outputStream.writeObject(p);
        }
        catch(Exception e){
            System.out.println ("Could not send packet! ");
        }
    }

    void send_quit_to_server()
    {
        Packet p = new Packet();
        p.sender=peerID;
        p.event_type=5;
        p.port_number=peer_listen_port;
        send_packet_to_server(p);
        
    }

    public void cmdLineParser(String args[])
    {
        int i;
        for (i=0;i<args.length;i+=2)
        {
            String option=args[i];
            switch (option)
            {
                case "-c": //config_file
                File file = new File(args[i+1]);
                read_config_from_file(file);
                break;

                case "-i": //my ID
                peerID=Integer.parseInt(args[i+1]);
                break;

                case "-p": // my listen port
                peer_listen_port=Integer.parseInt(args[i+1]);
                break;

                case "-s": //server port
                serverPort=Integer.parseInt(args[i+1]);
                break;

                case "-n":
                try{ip = InetAddress.getByName(args[i+1]);} catch(Exception e){
                System.out.println ("Could not resolve hostname! " +args[i+1]);}
                break;

                default: System.out.println("Unknown flag "+args[i]);

            }

        }
    }

    public void read_config_from_file(File file )
    {
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String opt[]= line.split(" ",2);
                switch(opt[0])
                {
                    case "SERVERPORT": serverPort=Integer.parseInt(opt[1]);break;
                    case "CLIENTID": peerID=Integer.parseInt(opt[1]);break;
                    case "MYPORT": peer_listen_port=Integer.parseInt(opt[1]);break;
                    case "FILE_VECTOR": FILE_VECTOR = opt[1].toCharArray();break;
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public void send_req_for_file(int findex)
    {
        if (FILE_VECTOR[findex] =='1'){
            System.out.println("I already have this file block!");
            return;
        }
        System.out.println(" I don't have this file. Let me contact server...");
        //request file from server
        Packet p = new Packet();
        p.sender=peerID;
        p.event_type=1;
        p.peer_listen_port=peer_listen_port;
        p.req_file_index=findex;
        send_packet_to_server(p);
        //disconnect();

    }

    void disconnect()
    {
        try { 
                outputStream.close();
                inputStream.close();
                s.close();
                System.out.println("Closed Socket");
            }
        catch (Exception e) { System.out.println("Couldn't close socket!");}
    }
}

class PacketHandler extends Thread
{
    Client client;
    private ObjectOutputStream ptpOutputStream;
    private ObjectInputStream ptpInputStream;

    public PacketHandler(Client client)
    {
        this.client=client;
    }

    public void run()
    {
        Packet p;

        while(true){
        try { 
            p = (Packet) client.inputStream.readObject();
            process_packet_from_server(p);
        }
        catch (Exception e) { 
             //e.printStackTrace();
             break;
        }
    }

    }

    void process_packet_from_server(Packet p)
    {
     int e = p.event_type;

     switch (e)
     {
        case 2: //server reply for req. file
        if (p.peerID==-1)
            System.out.println("Server says that no client has file "+p.req_file_index);
        else{
            System.out.println("Server says that peer "+p.peerID+" on listening port "+p.peer_listen_port+" has file "+p.req_file_index);
            PeerToPeerHandler(p.peerIP,p.peer_listen_port,p.req_file_index,p.req_file_index); // TO DO
            }
        break;

        case 6: //server wants to quit. I should too.
            System.out.println("Server wants to quit. I should too! ");
            client.disconnect();
           System.exit(0);

     }

    }
    
    void PeerToPeerHandler(InetAddress remotePeerIP, int remotePortNum, int remotePeerID, int findex)
    {
        // To implement.
    	try {
    		// connect to peer (Anthony Galea)
	        Socket ptpSocket = new Socket(remotePeerIP, remotePortNum); 
	        this.ptpOutputStream = new ObjectOutputStream(ptpSocket.getOutputStream());
	        this.ptpInputStream = new ObjectInputStream(ptpSocket.getInputStream());
            Packet p = new Packet();
            p.event_type=0;
            p.sender=client.peerID;
            p.peer_listen_port=client.peer_listen_port;
            p.FILE_VECTOR = client.FILE_VECTOR;

           send_packet_to_peer(p);

            System.out.println("Packet Sent");
	        System.out.println("Connected to Peer ..." +client.s); 
	        
	        boolean fileReceived = false;
	        // while file not received correctly (Anthony Galea)
	        while (!fileReceived) {
	        	// request_file_from_peer (Anthony Galea)
	            Packet requestFile = new Packet();
	            requestFile.event_type=4;
	            requestFile.sender=client.peerID;
	            requestFile.peer_listen_port=client.peer_listen_port;
	            requestFile.req_file_index=findex;
	            send_packet_to_peer(requestFile);
	            // receive_file_from_peer
	            p = (Packet) this.ptpInputStream.readObject();
	            boolean correct = false;
	            //TODO:add checking here
	            // verify file_hash
	            
	            
	            
	            // if correct, send positve ack, break (Anthony Galea)
	            if (correct) {
	            	Packet posAck = new Packet();
		            posAck.event_type=3;
		            posAck.gotFile = true;
		            posAck.sender=client.peerID;
		            posAck.req_file_index=findex;
		            posAck.isPtp = true;
		            send_packet_to_peer(posAck);
		            break;
	            }else { // if incorrect, send negative ack, loop back (Anthony Galea)
	            	Packet negAck = new Packet();
		            negAck.event_type=3;
		            negAck.gotFile = false;
		            negAck.req_file_index=findex;
		            send_packet_to_peer(negAck);
	            }
	            
	        }
            
	        ptpSocket.close();
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	
        //once, file has been received, send update file request to server (Anthony Galea).
        Packet p = new Packet();
        p.event_type=3;
        p.sender=client.peerID;
        p.req_file_index = findex;
        p.isPtp = false;
        client.send_packet_to_server(p);
        
        
    }
    
    //Anthony Galea - modified send_packet_to_server so that it sends to peer rather than server
    void send_packet_to_peer(Packet p)
    {
        try
        { 
            this.ptpOutputStream.writeObject(p);
        }
        catch(Exception e){
            System.out.println ("Could not send packet! ");
        }
    }



}

//Duplicated ServerSocketHandler so that it can accept and handle PTP requests (Anthony Galea)
class ClientSocketHandler extends Thread
{

    Client c;
    ArrayList<Connection> ptpConnectionList;

    public ClientSocketHandler(Client c){
        this.c=c;
        this.ptpConnectionList=c.connectionList;
    }

    public void run(){
        Socket clientSocket;
        while (true){
            clientSocket = null;
            try{

                clientSocket=c.listener.accept();
                System.out.println("A new peer is connecting.. : " + clientSocket);
                System.out.println("Port : " + clientSocket.getPort());
                System.out.println("IP : " + clientSocket.getInetAddress().toString());
                Connection conn = new Connection(clientSocket, c.connectionList);
                //ptpConnectionList.add(conn);
                conn.start();

            }
            catch (SocketException e){  
                System.out.println("Shutting down peer to peer connections....");
                // send a message to all clients that I want to quit.
                for (Connection c: this.ptpConnectionList)
                {
                    c.send_quit_message();
                    c.closeConnection();
                }
                this.ptpConnectionList.clear();
                break;

            }
            catch (IOException e){  
                e.printStackTrace(); 
            }


        }
    }

}
