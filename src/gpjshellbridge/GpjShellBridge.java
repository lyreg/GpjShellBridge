package gpjshellbridge;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;

public class GpjShellBridge {

	private static String byteArrayToString(byte[] ba)
	{
	    String s = "";
	    for (short i = 0; i < ba.length; i++)
	    	s += String.format("%02x", ba[i]&0xff);
	    return s;
	}

    public static void main(String[] args) {
		
    	boolean jcShell = true;
    	String iface = "NFC";
    	String ck = null;
    	String cs = null;
    	String at = null;
    	String ts = null;
    	int port = 0;
    	String script = null;
    	
		for(short i=0;i<args.length;i++)
		{
			switch(args[i])
			{
			case "--help":
			case "-h":
				/*java -jar STBridge.jar -ck L5cx5gHqvaNIKUMAbIm0I5zLEfTXJucMGxrLrvaT -cs WSoq3n7Nwm4wmaLCfsAL3jaPBcxMg6MuyeDHZpHa -at ngEKtsdOyGCnSkhiOINjtm5UQubd3I665npZwYdH -ts A2cVVFbtAwbGQncKvBNBcOPIZLdM8jHktV8vR2sD -p 2000 -i T0 -s /var/www/matrix/nordics/paypass_trans.jcsh

				the params to STBridge are as follows:
				-ck  the issuer consumer key
				-cs  the issuer consumer secret
				-at  the card access token
				-ts  the card access token secret
				-p the port number to run the local service on
				-i  the interface to attach to the card (T0 or NFC)
				-s the .jcsh script to run
				-noshell  (do this if you would like to use your own Shell tool to connect to the cloud through JCRemoteTermial)*/

	    		System.out.println("Usage:");
	    		System.out.println("java -jar STBridge.jar -ck consumer_key -cs consumer_secreet -at access_token -ts access_secret -p port_number [-i NFC or T0] [-s path/to/script/ending/in/.jcsh] [-noshell]");
	    		System.out.println("-ck  the issuer consumer key");
	    		System.out.println("-cs  the issuer consumer secret");
	    		System.out.println("-at  the card access token");
	    		System.out.println("-ts  the card access token secret");
	    		System.out.println("-p the port number to run the local service on (0 value picks an open port)");
	    		System.out.println("-i  the interface to attach to the card (T0 or NFC)");
	    		System.out.println("-s the .jcsh script to run");
	    		System.out.println("-noshell  (do this if you would like to use your own Shell tool to connect to the cloud through JCRemoteTermial)");
	    		System.exit(-1);
				return;
			case "-p":
			case "--port":
				try {
					i++;
					if(i<args.length)
						port=Integer.valueOf(args[i]).intValue();
				} catch(NumberFormatException e) {
					System.exit(-1);
					return;
				}
				break;
			case "-i":
			case "--interface":
				i++;
				if(i<args.length)
					iface = args[i];
				break;
			case "-ck":
			case "--consumer_key":
				i++;
				if(i<args.length)
					ck = args[i];
				break;
			case "-cs":
			case "--consumer_secret":
				i++;
				if(i<args.length)
					cs = args[i];
				break;
			case "-at":
			case "--access_token":
				i++;
				if(i<args.length)
					at = args[i];
				break;
			case "-ts":
			case "--token_secret":
				i++;
				if(i<args.length)
					ts = args[i];
				break;
			case "-ns":
			case "--noshell":
				jcShell = false;
				break;
			case "-s":
			case "--script":
				i++;
				if(i<args.length)
					script = args[i];
				break;
			default:
				break;
			}
		}

		//arg check
    	if(port<0)
    	{
    		System.out.println("-port must be greater than 0");
    		System.exit(-1);
			return;
    	}
       	if(ts==null)
    	{
    		System.out.println("-ts token secret must be assigned");
    		System.exit(-1);
			return;
    	}
       	if(cs==null)
    	{
    		System.out.println("-cs consumer secret must be assigned");
    		System.exit(-1);
			return;
    	}
       	if(ck==null)
    	{
    		System.out.println("-ck consumer key must be assigned");
    		System.exit(-1);
			return;
    	}
       	if(at==null)
    	{
    		System.out.println("-at access token must be assigned");
    		System.exit(-1);
			return;
    	}

    	OAuthConsumer consumer = new CommonsHttpOAuthConsumer(ck, cs);
		consumer.setTokenWithSecret(at, ts);

		RemoteCardConnection connection = null;
		try{
			connection = new RemoteCardConnection(consumer);
		} catch(IOException e){
    		System.out.println(e.getMessage());
    		System.exit(-1);
			return;
		}
		
	    System.out.println("Connecting to SimplyTapp card...");
	    try {
			connection.connect();
			System.out.println("Connected");
		} catch (IOException e) {
    		System.out.println(e.getMessage());
    		System.exit(-1);
			return;
		}

		ServerSocket server = null;
		try {
			server = new ServerSocket(port);
			if(port==0)
				port = server.getLocalPort();
		} catch (IOException e) {
			System.out.println("problem opening socket server");
			System.exit(-1);
			return;
		}
		System.out.println("Running on port "+port);

		if(!jcShell)
			System.out.println("run '/term \"Remote|localhost:"+port+"\"' in STShell");
		else
		{
			System.out.println("Shell is automatically connecting to port "+port);
			final int localPort = port;
			final String localScript = script;
	 	    new Thread(new Runnable() {
				Process proc = null;
	
				@Override
				public void run() {
					ProcessBuilder pb=null;
					if(localScript!=null)
					{
						String[] command = {
	                            "java",
	                            "-classpath", System.getProperty("java.class.path"),
	                            "net.sourceforge.gpj.cardservices.GlobalPlatformService",
	                            "-cmd",
	                            "-t",
	                            "Remote|localhost:" + localPort,
	                            "-s",
	                            localScript
	                    };
						pb = new ProcessBuilder(command);
					}
					else
					{
						String[] command = {
	                            "java",
	                            "-classpath", System.getProperty("java.class.path"),
	                            "net.sourceforge.gpj.cardservices.GlobalPlatformService",
	                            "-cmd",
	                            "-t",
	                            "Remote|localhost:" + localPort
	                    };
						pb = new ProcessBuilder(command);
					}
		            
					try {
						proc = pb.start();
					} catch (IOException e) {
					}
					final BufferedReader gpjBr = new BufferedReader(new InputStreamReader(proc.getInputStream()));
					Thread r = new Thread(new Runnable(){
						@Override
						public void run() {
							int c;
							boolean stop = false;
							while(true)
							{
								try {
									proc.exitValue();
									break;
								} catch (IllegalThreadStateException e){
								}
								try {
									Thread.sleep(1);
								} catch (InterruptedException e1) {
									stop = true;
								}
								try {
									while(gpjBr.ready() && (c=gpjBr.read())>-1)
									{
										System.out.printf("%c",c);
									}
									if(stop)
									{
										System.out.flush();
										break;
									}
								} catch (IOException e) {
								}
							}

						}});
					r.start();
					
					BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
					BufferedOutputStream output = new BufferedOutputStream(proc.getOutputStream());
					PrintStream printStream = new PrintStream(output);
					boolean exit = false;
					while(true)
					{
						try {
							proc.exitValue();
							r.interrupt();
							System.out.println("Quiting");
							break;
						} catch (IllegalThreadStateException e){
						}
						try {
							String input;
							if((input=br.readLine())!=null){
								if(input.startsWith("#"))
									continue;
								printStream.println(input);
								printStream.flush();
								if(input.equals("exit"))
								{
									exit = true;
									break;
								}
							}
							else
							{
								exit = true;
								printStream.println("exit");
								printStream.flush();
								break;
							}
						} catch (IOException e) {
							r.interrupt();
							break;
						}
					}
					while(r.isAlive())
					{
						try {
							Thread.sleep(1);
						} catch (InterruptedException e1) {
						}
					}
					printStream.close();
					if(exit)
						System.exit(0);

				}}).start();
		}
		
		try {
			Socket clientSocket = server.accept();
			clientSocket.setTcpNoDelay(true);
		    clientSocket.setReceiveBufferSize(128000);
     	    InputStream is = clientSocket.getInputStream();
     	    OutputStream os = clientSocket.getOutputStream();
     	    
     	    while(true)
     	    {
     	    	int len = 0;
				int r;
				int a=0;
				int b=0;
				r = is.read();
    			if(r==-1)
    				throw new IOException();
    			a = r;
    			if((a&0x40)!=0x40)
    			{
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			b = r;
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			len = (int)(0xFF00&(r<<8));
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			len |= r;
    			}
    			else
    			{
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			b = r;
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			len = (int)(0xFF0000&(r<<16));
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			len += (int)(0xFF00&(r<<8));
	    			r = is.read();
	    			if(r==-1)
	    				throw new IOException();
	    			len |= r;
    			}
    			a=(a&0xBF);
    			byte[] data = new byte[len]; 
    			for(int i=0;i<len;i++)
    			{
    				r = is.read();
    				if(r==-1)
    					throw new IOException();
    				data[i] = (byte)r;
        		}
    			
    			if(a==0x00)
    			{
    				if(!jcShell)
    					System.out.println("=> Card Reset");
    				TransceiveData tData = null;
    				if(!iface.equals("T0"))
    					tData = new TransceiveData(TransceiveData.NFC_CHANNEL);
    				else
    					tData = new TransceiveData(TransceiveData.DEVICE_CHANNEL);
	    			tData.setTimeout((short)15000);
	    			tData.packCardReset(true);
	    			connection.transceive(tData);
	    			data = tData.getNextResponse();
	    			if(data==null)
	    			{
	    				if(!jcShell)
	    					System.out.println("Remote Comm Error");
	    	    		throw new IOException();
	    			}
    				if(!jcShell)
    					System.out.println("<= "+byteArrayToString(data));
	    		}
    			else if(a==0x01)
    			{
    				if(!jcShell)
    					System.out.println("=> "+byteArrayToString(data));
    				TransceiveData tData = null;
    				if(!iface.equals("T0"))
    					tData = new TransceiveData(TransceiveData.NFC_CHANNEL);
    				else
    					tData = new TransceiveData(TransceiveData.DEVICE_CHANNEL);
	    			tData.setTimeout((short)15000);
	    			tData.packApdu(data,true);
	    			connection.transceive(tData);
	    			data = tData.getNextResponse();
	    			if(data==null)
	    			{
	    				if(!jcShell)
	    					System.out.println("Remote Comm Error");
	    	    		throw new IOException();
	    			}
    				if(!jcShell)
    					System.out.println("<= "+byteArrayToString(data));
    			}
    			else
    			{
    				data = new byte[0];
    				if(!jcShell)
    					System.out.println("unknown packet");
    			}
    			if(data.length>0xffff)
    			{
	    			os.write(a|0x40);
	    			os.write(b);
	    			os.write((byte)(data.length>>16));
	    			os.write((byte)(data.length>>8));
	    			os.write((byte)(data.length&0xFF));
    			}
    			else
    			{
	    			os.write(a);
	    			os.write(b);
	    			os.write((byte)(data.length>>8));
	    			os.write((byte)(data.length&0xFF));    				
    			}
    			for(int i=0;i<data.length;i++)
    				os.write(data[i]);    			
     	    }
		} catch (Exception e) {
			if(!jcShell)
				System.out.println("Oops, socket error");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
			}
			System.exit(-1);
		}
		try {
			server.close();
		} catch (IOException e1) {
		}
    }
}
