package gpjshellbridge;

class RemoteConnectionData {
	private String auth = null;
	private String ip = null;
	private int port = 0;
	
	RemoteConnectionData(String auth,String ip, int port)
	{
		this.auth=auth;
		this.ip = ip;
		this.port = port;
	}
	
	String getAuth(){return auth;}
	String getIp(){return ip;}
	int getPort(){return port;}
}
