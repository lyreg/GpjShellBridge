package gpjshellbridge;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

final class RemoteCardConnection {
    private final static char SUCCESS = '0';
	private final static char BAD_CREDENTIAL = '1';
    private final static char BAD_DEVICE = '2';
	private final static char NO_CARD = '3';
	private final static char SOCKET_ERR = '4';
	private final static char NO_PIN = '5';
	private final static char BAD_PIN = '6';
    private final static String authHttpHost = GpjShellBridge.service;

    private SSLSocket clientSocket = null;
    private InputStream is = null;
    private OutputStream os = null;
	private Thread tPing = null;
	private boolean transceiving = false;
	private boolean pinging = false;
	private boolean writing = false;
	private OAuthConsumer consumer = null;

	private byte[] kL = null;
	private byte[] kR = null;

	RemoteCardConnection(OAuthConsumer consumer) throws IOException
	{
		if(consumer==null)
			throw new IOException("BAD_CONSUMER");
		this.consumer = consumer;
	}
	
	//this is ALWAYS run in a thread, so notify the virtualCardInteface when errors occur
	private RemoteConnectionData auth() throws IOException
	{
		String str = "";
    	try
    	{   
    		HttpClient hc = new DefaultHttpClient();
    		String url = authHttpHost;
    		HttpPost post = new HttpPost(url);
     	   
    		try {
    			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        		JSONObject query = new JSONObject();
        		query.put("command","Transact");
				if(query!=null)
	     	    	nameValuePairs.add(new BasicNameValuePair("DATA", query.toString()));
	     	    post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	     	} catch (JSONException e1) {
			}
    		
    		consumer.sign(post);
    	    
    	    HttpResponse rp = hc.execute(post);
    		if(rp.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
    		{
    			str = EntityUtils.toString(rp.getEntity());
    		}
    		else
    		{
            	throw new IOException();
    		}
    	}catch(UnsupportedEncodingException e){
        	throw new IOException("BAD_PARAM");
    	}catch(IOException e){
        	throw new IOException("BAD_CONNECTION");
    	}catch(OAuthMessageSignerException e){
        	throw new IOException("BAD_CONSUMER");
		}catch(OAuthCommunicationException e){
        	throw new IOException("BAD_CONSUMER");
		}catch(OAuthExpectationFailedException e){
        	throw new IOException("BAD_CONSUMER");
		}
		String status=null;
		JSONObject data=null;
    	try{
	    	data = new JSONObject(str);
	    	status = data.getString("status");
    	} catch (JSONException e){
        	throw new IOException();
    	}
    	if(status==null || status.length()!=1)
    	{
        	throw new IOException();
    	}
    	byte ch = (byte)status.toCharArray()[0];
    	if(ch==BAD_CREDENTIAL)
    	{
        	throw new IOException("BAD_CREDENTIAL");
    	}
    	else if(ch==BAD_DEVICE)
    	{
        	throw new IOException("BAD_DEVICE");
    	}
    	else if(ch==NO_PIN)
    	{
        	throw new IOException("NO_PIN");
    	}
    	else if(ch==BAD_PIN)
    	{
        	throw new IOException("NO_PIN");
    	}
    	else if(ch==NO_CARD)
    	{
        	throw new IOException("NO_CARD");
    	}
    	else if(ch==SOCKET_ERR)
    	{
        	throw new IOException("SOCKET_ERR");
    	}
    	else if(ch!=SUCCESS)
    	{
        	throw new IOException();
    	}

    	JSONObject details=null;
    	try{
        	details = (JSONObject) data.get("data");
    	} catch (JSONException e){
        	throw new IOException();
    	}
    	String ip=null;
    	try{
    		ip = (String)details.get("ip");
    	} catch (JSONException e){
           	throw new IOException("NO_CARD");
    	}
    	String sslPortStr=null;
    	try{
    		sslPortStr = (String)details.get("ssl_port");
    	} catch (JSONException e){
           	throw new IOException("NO_CARD");
    	}
    	int sslPort;
		try {
			sslPort = Integer.parseInt(sslPortStr);
		} catch (NumberFormatException e) {
	       	throw new IOException("NO_CARD");
		}
    	String auth=null;
    	try{
    		auth = (String)details.get("auth");
    	} catch (JSONException e){
           	throw new IOException("NO_CARD");
    	}
    	if(auth.length()<48)
		{
           	throw new IOException("NO_CARD");
		}
    	return new RemoteConnectionData(auth,ip,sslPort);
	}
	
	void connect() throws IOException
	{
		if(tPing!=null && tPing.isAlive())
			throw new IOException("ALREADY_CONNECTED");
		RemoteConnectionData connectionData = auth();
		if(connectionData==null)
			throw new IOException("NO_CARD");
		try{
            SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            clientSocket = (SSLSocket) sslsocketfactory.createSocket(connectionData.getIp(), connectionData.getPort());
            clientSocket.setTcpNoDelay(true);
	     	//open socket
	     	is = clientSocket.getInputStream();
	     	os = clientSocket.getOutputStream();
	     	byte[] b = new byte[8];
	     	for(short i=0;i<b.length*2;i++)
	     	{
	     		byte ch = (byte)connectionData.getAuth().toCharArray()[i];
	     		if(ch>='A' && ch<='F')
	     			ch-=('A'-10);
	     		else if(ch>='a' && ch<='f')
	     		    ch-=('a'-10);
	     		else if(ch>='0' && ch<='9')
	     		    ch-='0';
   			    if(i%2==0)
   			    	b[i/2] = (byte)((ch<<4)&0xff);
   			    else
   			    	b[i/2] = (byte)(short)(((short)(b[i/2]&0xff)|ch)&0xff);
	     	}

	     	kL = new byte[8];
	     	for(short i=0;i<kL.length*2;i++)
	     	{
	     		byte ch = (byte)connectionData.getAuth().toCharArray()[i+b.length*2];
	     		if(ch>='A' && ch<='F')
	     			ch-=('A'-10);
	     		else if(ch>='a' && ch<='f')
	     			ch-=('a'-10);
	     		else if(ch>='0' && ch<='9')
	     			ch-='0';
   			  	if(i%2==0)
   			  		kL[i/2] = (byte)((ch<<4)&0xff);
   			  	else
   			  		kL[i/2] = (byte)(short)(((short)(kL[i/2]&0xff)|ch)&0xff);
	     	}
	     	kR = new byte[8];
	     	for(short i=0;i<kR.length*2;i++)
	     	{
	     		byte ch = (byte)connectionData.getAuth().toCharArray()[i+b.length*2+kL.length*2];
	     		if(ch>='A' && ch<='F')
	     			ch-=('A'-10);
	     		else if(ch>='a' && ch<='f')
	     			ch-=('a'-10);
	     		else if(ch>='0' && ch<='9')
	     			ch-='0';
   			  	if(i%2==0)
   			  		kR[i/2] = (byte)((ch<<4)&0xff);
   			  	else
   			  		kR[i/2] = (byte)(short)(((short)(kR[i/2]&0xff)|ch)&0xff);
	     	}

	     	//setup encryption/decryption ciphers
	     	IvParameterSpec iv = new IvParameterSpec(new byte[8]);
	     	Cipher ecipherL = Cipher.getInstance("DES/CBC/NoPadding");
	     	Cipher dcipherL = Cipher.getInstance("DES/CBC/NoPadding");
	     	ecipherL.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
	     	dcipherL.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
	     	Cipher ecipherR = Cipher.getInstance("DES/CBC/NoPadding");
	     	Cipher dcipherR = Cipher.getInstance("DES/CBC/NoPadding");
	     	ecipherR.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);
	     	dcipherR.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);

	     	//validate and send back
	     	byte[] out = new byte[b.length];
	     	int j=0;
            for (int i = 0; i < b.length; i++)
            {
          	  	if (b[i] == '\n' || b[i] == '\\')
                {
          	  		byte[] tmp = new byte[out.length+1];
          	  		System.arraycopy(out, 0, tmp, 0, out.length);
          	  		out = tmp;
          	  		out[j++] = '\\';
                }
                out[j++] = b[i];
            }
  	  		byte[] tmp = new byte[out.length+1];
  	  		System.arraycopy(out, 0, tmp, 0, out.length);
  	  		out = tmp;
  	  		out[out.length-1] ='\n';
  	  		
            //send out to socket
            sWrite(out);
            //read from socket for confirmation
            clientSocket.setSoTimeout(15000);  //15 second timeout
            byte[] in = sRead();
            clientSocket.setSoTimeout(0);
	    	  
	    	//verify match
	    	if(in.length>=b.length)
	    	{
	    		short i;
		    	for(i=0;i<b.length;i++)
		    		if(b[i]!=in[i])
		    			break;
		    	if(i==b.length)
		    	{
		    		if(tPing!=null && tPing.isAlive())
		    			throw new IOException();
		    		Runnable r = new RunCardPing();
		    		tPing = new Thread(r);
		    		tPing.start();
		    		return;  //connect successfully, keep alive with ping thread
		    	}
	    	}
		}catch(Exception e){
		}  
		throw new IOException();
	}
	
	void disconnect() throws IOException
	{
		if(tPing==null || !tPing.isAlive())
			throw new IOException("NO_CONNECTION");
    	tPing.interrupt();    		
	}

    private boolean isAlive()
    {
    	if(clientSocket!=null && os!=null && is!=null)
    	{
    		if(clientSocket.isClosed() || clientSocket.isInputShutdown() || clientSocket.isOutputShutdown())
    		{
    			return false;
    		}
    		return true;
    	}
    	return false;
    }

    synchronized private void sWrite(byte[] buffer) throws IOException
	{
		while(writing)
		{
			try {
				Thread.sleep(5);
			} catch (InterruptedException e){
			}
		}
		writing = true;
		try{
			os.write(buffer);
			os.flush();
			writing = false;
		}catch(Exception e){
			writing = false;
			throw new IOException();
		}		
	}

    synchronized private byte[] sRead() throws IOException
	{
        boolean esc=false;
		ByteBuffer pktBuffer = ByteBuffer.allocate(1000);

		int r;
		try{
        	while (true) 
    		{
    			if(pktBuffer.position()==pktBuffer.limit())
    			{
    				ByteBuffer tmp = ByteBuffer.allocate(pktBuffer.position()+1000);
    				tmp.put(pktBuffer.array());
    				pktBuffer = tmp;
    			}
    			
    			r=is.read();
    			if(r==-1)
    				throw new IOException();
    			if((!esc && r=='\n') || pktBuffer.position()>140000)
    				break;
    			
    			//manage escape state
    			if(!esc)
    			{
    				if(r=='\\')
    					esc=true;
    			}
    			else
    				esc=false;
    			
    			if(!esc)
    				pktBuffer.put((byte)(r&0xff));
    		}
		} catch (Exception e){
			throw new IOException();
		}
		byte[] in = new byte[pktBuffer.position()];
		pktBuffer.flip();
		pktBuffer.get(in);

  	  	return in;
	}

    private void close()
    {
   		try{
   			is.close();
    	}catch(Exception e) {
    	}
    	try{
   			os.close();
    	}catch(Exception e) {
    	}
    	try{
   			clientSocket.close();
    	}catch(Exception e) {
    	}
    	is = null;
    	os = null;
    	clientSocket = null;
    }

    private byte[] cardPing() throws IOException
    {
    	byte[] b={(byte)0x0F,0x00,0x00,0x00};

    	if(isAlive())
    	{
    		try
    		{
    	     	IvParameterSpec iv = new IvParameterSpec(new byte[8]);
    	     	Cipher ecipherL = Cipher.getInstance("DES/CBC/NoPadding");
    	     	Cipher dcipherL = Cipher.getInstance("DES/CBC/NoPadding");
    	     	ecipherL.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
    	     	dcipherL.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
    	     	Cipher ecipherR = Cipher.getInstance("DES/CBC/NoPadding");
    	     	Cipher dcipherR = Cipher.getInstance("DES/CBC/NoPadding");
    	     	ecipherR.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);
    	     	dcipherR.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);

    	     	//encrypt ping
    	    	byte[] eb = new byte[8-(b.length%8)+b.length];
    	    	for(int i=0;i<eb.length;i++)
    	    	{
    	    		if(i<b.length)
    	    			eb[i]=b[i];
    	    		else if(i==b.length)
    	    			eb[i]=(byte)0xFF;
    	    		else
    	    			eb[i]=0x00;
    	    	}
    	    	b = eb;
    	    	b = ecipherL.doFinal(b);
    	    	b = dcipherR.doFinal(b);
    	    	b = ecipherL.doFinal(b);

    	     	byte[] out = new byte[b.length];
    	     	int j=0;
                for (short i = 0; i < b.length; i++)
                {
              	  	if (b[i] == '\n' || b[i] == '\\')
                    {
              	  		byte[] tmp = new byte[out.length+1];
              	  		System.arraycopy(out, 0, tmp, 0, out.length);
              	  		out = tmp;
              	  		out[j++] = '\\';
                    }
                    out[j++] = b[i];
                }
      	  		byte[] tmp = new byte[out.length+1];
      	  		System.arraycopy(out, 0, tmp, 0, out.length);
      	  		out = tmp;
      	  		out[out.length-1] ='\n';

	            //send out to socket
	            pinging = true;
	            sWrite(out);
	              
	            //read from socket for confirmation
	            clientSocket.setSoTimeout(15000);  //15 second timeout
	            byte[] in = sRead();
	            pinging = false;
		    	clientSocket.setSoTimeout(0);
		    	
		    	if(in.length>0 && (in.length%8)==0)
		    	{
	    	    	//decrypt ping
			    	eb = dcipherL.doFinal(in);
	    	    	eb = ecipherR.doFinal(eb);
	    	    	eb = dcipherL.doFinal(eb);
	    	    	short i;
	    	    	for(i=(short)eb.length;i>0;i--)
	    	    	{
	    	    		if(eb[i-1]==(byte)0xff)
	    	    			break;
	    	    		if(eb[i-1]!=0x00)
	    	    		{
	    	    			i=0;
	    	    			break;
	    	    		}
	    	    	}
	    	    	if(i>0)
	    	    		i--;
	    	    	in = new byte[i];
	    	    	for(i=0;i<in.length;i++)
	    	    		in[i]=eb[i];
	    	    	
			    	if(in.length>=4 && in[0]==0x0F)
			    	{
			    		short len = (short)(in[2]*256+in[3]);
			    		if(len==(short)(in.length-4))
			    		{
			    			 b = new byte[len];
			    			 for(i=0;i<len;i++)
			    				 b[i]=in[4+i];
			    			 return b;
			    		}
			    	}
		    	}
    		} catch (Exception e) {
	            pinging = false;
    		}
    	}
        pinging = false;
    	throw new IOException();
    }
    
    private class RunCardPing implements Runnable {
    	public void run()
    	{
    		while(true)
    		{
    			if(!transceiving)
    			{
		    		try {
		    			cardPing();
		    		} catch (IOException e){
		    			//ping failed, close the connection
		    			close();
		    			break;
		    		}
    			}
	    		if(Thread.interrupted())
	    		{
	    			close();
	    			break;
	    		}
	    		try {
	    			Thread.sleep(2000);  //wait 2 seconds between network checks
	    		} catch (InterruptedException e) {
	    			close();
	    			break;
	    		}
    		}
    	}
    }
    
	private void transmit(byte[][] buffer) throws IOException
	{
    	if(buffer==null)
    	{
	    	throw new IOException();
    	}
	    	
    	if(tPing!=null && tPing.isAlive())
    	{
    		try
    		{
    	     	IvParameterSpec iv = new IvParameterSpec(new byte[8]);
    	     	Cipher ecipherL = Cipher.getInstance("DES/CBC/NoPadding");
    	     	Cipher dcipherL = Cipher.getInstance("DES/CBC/NoPadding");
    	     	ecipherL.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
    	     	dcipherL.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
    	     	Cipher ecipherR = Cipher.getInstance("DES/CBC/NoPadding");
    	     	Cipher dcipherR = Cipher.getInstance("DES/CBC/NoPadding");
    	     	ecipherR.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);
    	     	dcipherR.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);

    	     	for(short k=0;k<buffer.length;k++)
    			{
	    	    	//encrypt
	    	    	byte[] eb = new byte[8-(buffer[k].length%8)+buffer[k].length];
	    	    	for(int i=0;i<eb.length;i++)
	    	    	{
	    	    		if(i<buffer[k].length)
	    	    			eb[i]=buffer[k][i];
	    	    		else if(i==buffer[k].length)
	    	    			eb[i]=(byte)0xFF;
	    	    		else
	    	    			eb[i]=0x00;
	    	    	}
	    	    	buffer[k] = eb;
	    	    	buffer[k] = ecipherL.doFinal(buffer[k]);
	    	    	buffer[k] = dcipherR.doFinal(buffer[k]);
	    	    	buffer[k] = ecipherL.doFinal(buffer[k]);
	    	    	
	    			//escape outgoing data
	    	     	byte[] out = new byte[buffer[k].length];
	    	     	int j=0;
	                for (int i = 0; i < buffer[k].length; i++)
	                {
	              	  	if (buffer[k][i] == '\n' || buffer[k][i] == '\\')
	                    {
	              	  		byte[] tmp = new byte[out.length+1];
	              	  		System.arraycopy(out, 0, tmp, 0, out.length);
	              	  		out = tmp;
	              	  		out[j++] = '\\';
	                    }
	                    out[j++] = buffer[k][i];
	                }
	      	  		byte[] tmp = new byte[out.length+1];
	      	  		System.arraycopy(out, 0, tmp, 0, out.length);
	      	  		out = tmp;
	      	  		out[out.length-1] ='\n';

		            //send out to socket		              
		            sWrite(out);
    			}
    			return;
    		} catch (Exception e) {
    		}
    	}
    	throw new IOException();
	}
	
	private void receive(TransceiveData transceiveData) throws IOException
	{
    	if(tPing!=null && tPing.isAlive())
    	{
    		try
    		{
    	     	IvParameterSpec iv = new IvParameterSpec(new byte[8]);
    	     	Cipher ecipherL = Cipher.getInstance("DES/CBC/NoPadding");
    	     	Cipher dcipherL = Cipher.getInstance("DES/CBC/NoPadding");
    	     	ecipherL.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
    	     	dcipherL.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kL, "DES"),iv);
    	     	Cipher ecipherR = Cipher.getInstance("DES/CBC/NoPadding");
    	     	Cipher dcipherR = Cipher.getInstance("DES/CBC/NoPadding");
    	     	ecipherR.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);
    	     	dcipherR.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kR, "DES"), iv);

    	     	while(transceiveData.getResponseNumber()>0)
    			{
    				//read from socket for confirmation
	                clientSocket.setSoTimeout(transceiveData.getTimeout()); //set timeout
	                byte[] in = sRead();
	    		    clientSocket.setSoTimeout(0);

	                //load up response to response buffer
	                if(in.length>0 && (in.length%8)==0)
	                {
	      	    	    //decrypt
	  		    	    byte[] eb = dcipherL.doFinal(in);
	      	    	    eb = ecipherR.doFinal(eb);
	      	    	    eb = dcipherL.doFinal(eb);
	      	    	    int i;
	      	    	    for(i=eb.length;i>0;i--)
	      	    	    {
	      	    		    if(eb[i-1]==(byte)0xff)
	      	    			    break;
	      	    		    if(eb[i-1]!=0x00)
	      	    		    {
	      	    			    i=0;
	      	    			    break;
	      	    		    }
	      	    	    }
	      	    	    if(i>0)
	      	    		  i--;
	      	    	    in = new byte[i];
	      	    	    System.arraycopy(eb, 0, in, 0, in.length);
		              
	      	    	    try {
	      	    		    transceiveData.setResponse(in);
	      	    	    } catch (IOException e) {
	      	    	    }
	                }
    			}
    			return;
    		} catch (Exception e) {
    		}
	    }
    	throw new IOException();
	}

	void transceive(TransceiveData transceiveData) throws IOException
    {
		if(transceiving)
			throw new IOException("TRANCEIVE_BUSY");
		transceiving = true;
		//send the data
		try {
			transmit(transceiveData.getData());
		} catch (IOException e){
			if(tPing!=null)
				tPing.interrupt();
			transceiveData.clear();
			transceiving = false;
			throw new IOException("TRANSMISSION_ERROR");
		}
		
		//get responses
		if(transceiveData.getResponseNumber()==0)
		{
			transceiveData.clear();
			transceiving = false;
		}
		else
		{
			//wait for ping to complete before receiving
			while(pinging)
			{
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
			}
			try {
				receive(transceiveData);
				transceiveData.clear();
				transceiving = false;
				return;
			} catch (IOException e){
				if(tPing!=null)
					tPing.interrupt();
				transceiveData.clear();
				transceiving = false;
				throw new IOException("RECEPTION_ERROR");
			}
		}
    }

}
