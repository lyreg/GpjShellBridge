package gpjshellbridge;


import java.io.IOException;

final public class TransceiveData {
	public static final byte NFC_CHANNEL = 0x01;
	public static final byte SOCKET_CHANNEL = 0x11;
	public static final byte SOFT_CHANNEL = 0x21;
	public static final byte DEVICE_CHANNEL = 0x31;
	
	private final static short defaultTo = 2000;
	private byte[][] sndBuf = null;
	private short to=defaultTo;  //default
	private byte[]  responses = null;
	private byte[][] recBuf = null;
	private byte channel = NFC_CHANNEL;

	public TransceiveData()
	{
	}

	public TransceiveData(byte channel)
	{
		if(channel==SOCKET_CHANNEL || channel==SOFT_CHANNEL || channel==DEVICE_CHANNEL)
			this.channel = channel;
	}

	void clear()
	{
		sndBuf = null;
		responses = null;
	}

    public void setTimeout(short to)
	{
		this.to = to;
	}

	public void packCardReset(boolean resp)
	{
		
		if(resp)
		{
			if(responses==null)
				responses = new byte[0];
			byte[] tmp = new byte[responses.length+1];
			short i;
			for(i=0;i<responses.length;i++)
				tmp[i] = responses[i];
			tmp[i] = 0x00;
			responses = tmp;
		}
		
		if(sndBuf==null)
			sndBuf = new byte[0][0];
		byte[][] tmp = new byte[sndBuf.length+1][];
		short i;
		for(i=0;i<sndBuf.length;i++)
		{
			tmp[i] = new byte[sndBuf[i].length];
			for(short k=0;k<sndBuf[i].length;k++)
				tmp[i][k] = sndBuf[i][k];
		}
		tmp[i] = new byte[4];
		if(resp)
			tmp[i][0]= 0x00;
		else
			tmp[i][0] = (byte)0x80;
		tmp[i][1] = 0x00;
		tmp[i][2] = 0x00;
		tmp[i][3] = 0x00;
		sndBuf = tmp;
	}
		
	public void packApdu(byte[] apdu, boolean resp)
	{
		if(apdu==null)
			return;
		
		if(resp)
		{
			if(responses==null)
				responses = new byte[0];
			byte[] tmp = new byte[responses.length+1];
			short i;
			for(i=0;i<responses.length;i++)
				tmp[i] = responses[i];
			tmp[i] = channel;
			responses = tmp;
		}
		
		if(sndBuf==null)
			sndBuf = new byte[0][0];
		byte[][] tmp = new byte[sndBuf.length+1][];
		short i;
		for(i=0;i<sndBuf.length;i++)
		{
			tmp[i] = new byte[sndBuf[i].length];
			System.arraycopy(sndBuf[i], 0, tmp[i], 0, sndBuf[i].length);
		}
		if(apdu.length>0xffff)
		{
			tmp[tmp.length-1] = new byte[apdu.length+5];
			if(resp)
				tmp[tmp.length-1][0]= channel;
			else
				tmp[tmp.length-1][0] = (byte)(0x80|channel);
			tmp[tmp.length-1][0]|=0x40;
			tmp[tmp.length-1][1] = 0x00;
			tmp[tmp.length-1][2] = (byte)((short)(apdu.length/(256*256))&0xff);
			tmp[tmp.length-1][3] = (byte)((short)((apdu.length%(256*256))/256)&0xff);
			tmp[tmp.length-1][4] = (byte)((short)(apdu.length%256)&0xff);
			System.arraycopy(apdu, 0, tmp[tmp.length-1], 5, apdu.length);
		}
		else
		{
			tmp[tmp.length-1] = new byte[apdu.length+4];
			if(resp)
				tmp[tmp.length-1][0]= channel;
			else
				tmp[tmp.length-1][0] = (byte)(0x80|channel);
			tmp[tmp.length-1][1] = 0x00;
			tmp[tmp.length-1][2] = (byte)((short)(apdu.length/256)&0xff);
			tmp[tmp.length-1][3] = (byte)((short)(apdu.length%256)&0xff);
			System.arraycopy(apdu, 0, tmp[tmp.length-1], 4, apdu.length);
		}
		sndBuf = tmp;
	}

	public byte[] getNextResponse()
	{
		if(recBuf==null || recBuf.length<1)
			return null;
		byte[] tmp2 = recBuf[0];
		//shift recBuf
		byte[][] tmp = new byte[recBuf.length-1][];
		for(short i=0;i<tmp.length;i++)
		{
			tmp[i] = new byte[recBuf[i+1].length];
			System.arraycopy(recBuf[i+1], 0, tmp[i], 0, recBuf[i+1].length);
		}
		recBuf=tmp;
		return tmp2;
	}

	//master implemented
    short getTimeout() {return to;}
    
	byte[][] getData()
	{
		recBuf = null;
		return sndBuf;
	}

    short getResponseNumber() 
    {
    	if(responses!=null && responses.length>0)
    		return (short)responses.length;
    	return 0;
    }
    
	void setResponse(byte[] in) throws IOException
	{
		if(in!=null && responses==null)
		{
			clear();
			throw new IOException("INVALID_DATA");
		}
		if(responses!=null && in==null)
		{
			clear();
			throw new IOException("INVALID_DATA");
		}
		if(responses==null || responses.length<1)
			throw new IOException("INVALID_DATA");
		if((in.length>=4 && (in[0]&0x40)!=0x40) || (in.length>=5 && (in[0]&0x40)==0x40))
		{
			if((responses[0]&0xBF)!=(in[0]&0xBF))
			{
				clear();
				throw new IOException("INVALID_DATA");
			}
			//shift responses
			byte[] tmp2 = new byte[responses.length-1];
			for(short i=0;i<tmp2.length;i++)
				tmp2[i] = responses[i+1];
			responses = tmp2;
			int len = 0;
			int offset = 0;
			if((in[0]&0x40)!=0x40)
			{
				offset = 4;
				len = (int)(in[2]&0xff)*256+(int)(in[3]&0xff);
			}
			else
			{
				offset = 5;
				len = (int)(in[2]&0xff)*256*256+(int)(in[3]&0xff)*256+(short)(in[4]&0xff);				
			}
			if(len==(int)(in.length-offset))
			{
				//move responses to respBuf
				if(recBuf==null)
					recBuf = new byte[0][0];
				byte[][] tmp = new byte[recBuf.length+1][];
				short i;
				for(i=0;i<recBuf.length;i++)
				{
					tmp[i] = new byte[recBuf[i].length];
					System.arraycopy(recBuf[i], 0, tmp[i], 0, recBuf[i].length);
				}
				tmp[i] = new byte[len];
				System.arraycopy(in,offset,tmp[i],0,len);
				recBuf = tmp;
			}
			else
			{
				clear();
				throw new IOException("INVALID_DATA");
			}
		}
		else
		{
			clear();
			throw new IOException("INVALID_DATA");
		}
		return;
	}
}
