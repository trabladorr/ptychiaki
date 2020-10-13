package com.trurdilin.tichu.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.spongycastle.operator.OperatorCreationException;

import android.content.Context;
import android.os.AsyncTask;

import com.trurdilin.tichu.net.Message.CompoundMessage;
import com.trurdilin.tichu.net.rsa.CertificateManager;


public class MessageReceiver implements Runnable{
	
	private static SSLServerSocketFactory sslserverSocketFactory;
	private static MessageReceiver receiver = null;

	private Thread t = null;
	private boolean running = false;
	private int port = 0;
	private MessageHandler handler;
	private final String name;

	private static final Object handlerLock = new Object();
	private static final Object listenerLock = new Object();
	private static final ReadWriteLock runningLock = new ReentrantReadWriteLock();
	private static final Object portLock = new Object();
	private static final Object threadLock = new Object();
	
	public static void init(SecureRandom random, String name, Context context)  throws IOException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, CertificateException, OperatorCreationException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException{
		synchronized(listenerLock){
			if (receiver == null || !receiver.name.equals(name))
				receiver = new MessageReceiver(random, name, context);
		}
	}
	
	public static MessageReceiver bindInstance(MessageHandler handler){
		MessageReceiver ret;
		synchronized(listenerLock){
			if (receiver == null)
				return null;
			ret = receiver;
		}
		synchronized(handlerLock){
			ret.handler = handler;
		}
		return ret;
	}
	
	private MessageReceiver(SecureRandom random, String name, Context context) throws IOException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, CertificateException, OperatorCreationException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException{
		this.name = name;
		
		//force connection to adhere to TLSv1.2, load keystore

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
		kmf.init(CertificateManager.getMyKeyStore(name, context), CertificateManager.password.toCharArray());
		KeyManager[] keyManagers = kmf.getKeyManagers();
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		
		//allow self-signed certificates
		TrustManager[] trustManagers = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers(){
                    return null;
                }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType){
                }
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType){
                }
            }
        };
		
		sslContext.init(keyManagers, trustManagers, random);
		
		sslserverSocketFactory = (SSLServerSocketFactory) sslContext.getServerSocketFactory();
		
		
	}
	
	public boolean isRunning(){//when app is in background
		synchronized(runningLock){
			return running;
		}	
	}
	
	public void startListen(){
		runningLock.writeLock().lock();
		running = true;
		runningLock.writeLock().unlock();
		
		synchronized(threadLock){
			if (t == null){
				t = new Thread(this);
				t.start();
			}
		}
		
	}
	
	public void stopListen(){//when app is in background
		runningLock.writeLock().lock();
		running = false;
		runningLock.writeLock().unlock();
		
		synchronized(threadLock){
			t = null;
		}
	}
	
	public int getPort(){
		synchronized(portLock){
			return port;
		}	
	}
	
	public String getName(){
		return name;
	}

	//@Override
	public void run() {
		
		SSLServerSocket sslServerSocket;
		try {
			sslServerSocket = (SSLServerSocket) sslserverSocketFactory.createServerSocket(NetSettings.chosenPort);
			sslServerSocket.setSoTimeout(NetSettings.serverSocketSoTimeout);
		} catch (Exception e) {
			synchronized(listenerLock){
				receiver = null;
			}
			return;
		}
		synchronized(portLock){
			port = sslServerSocket.getLocalPort();
		}
		
		sslServerSocket.setEnabledProtocols(NetSettings.selectedProtocols);
		sslServerSocket.setEnabledCipherSuites(NetSettings.selectedCiphersuites);
		
		sslServerSocket.setNeedClientAuth(true);
		while(true){
			boolean isRunning;
		
			runningLock.readLock().lock();
			isRunning = running;
			runningLock.readLock().unlock();
			
			if (!isRunning)
				break;
				
			try {
				SSLSocket socket = (SSLSocket)sslServerSocket.accept();
				MessageHandler handlerTmp;
				synchronized (handlerLock) {
					handlerTmp = this.handler;
				}
				SocketListener.handleSocket(socket, handlerTmp);
			}
			catch (IOException e) {
				if (NetSettings.debug && !(e instanceof SocketTimeoutException))
					e.printStackTrace();//TODO: report that failed connection?
			}
		}
		try {
			sslServerSocket.close();
		} 
		catch (IOException e) {
		}
	}
	
	//unencrypted message reception, for test purposes
	//@Override
	public void rund() {

		ServerSocket serverSocket;
		try {
			serverSocket = (ServerSocket) new ServerSocket(0);
			serverSocket.setSoTimeout(NetSettings.serverSocketSoTimeout);
		} catch (Exception e) {
			synchronized(listenerLock){
				receiver = null;
			}
			return;
		}

		synchronized(portLock){
			port = serverSocket.getLocalPort();
		}
		
		while(true){
			boolean isRunning;
			
			runningLock.readLock().lock();
			isRunning = running;
			runningLock.readLock().unlock();
			
			if (!isRunning)
				break;
			
			try {

				Socket socket = (Socket)serverSocket.accept();
				MessageHandler handlerTmp;
				synchronized (handlerLock) {
					handlerTmp = this.handler;
				}
				SocketListener.handleSocket(socket, handlerTmp);
			}
			catch (Exception e) {
				if (NetSettings.debug)
					e.printStackTrace();//TODO: report that failed connection?
			}
		}
	}
	
	//Separate thread for authenticating remote user and receiving a message from the socket
	private static class SocketListener implements Runnable{

		private MessageHandler handler;
		private Socket socket;
		
		private static void handleSocket(Socket socket, MessageHandler handler){
			SocketListener tmp = new SocketListener(socket, handler);
			AsyncTask.execute(tmp);
		}
		
		private SocketListener(Socket socket, MessageHandler handler){
			this.socket = socket;
			this.handler = handler;
		}
		
		@Override
		public void run() {
			try {
				Certificate cert = null;
				if (socket instanceof SSLSocket)
					cert = CertificateManager.extractCertificate((SSLSocket)socket);
				
				Message m = new Message(socket.getInputStream());
				
				if (CertificateManager.verifyCertificate(cert))
					handler.handleMessage(new CompoundMessage(m, CertificateManager.extractUsername(cert), cert));
			}
			catch (Exception e) {
				if (NetSettings.debug)
					e.printStackTrace();//TODO: report exception in message handling?
			}
		}
		
	}
	
	public static class ListenerException extends Exception{
		private static final long serialVersionUID = -1669624982635420230L;

		public ListenerException(String string) {
			super(string);
		}
	}
	
	public static interface MessageHandler{
		public void handleMessage(CompoundMessage m);
	}
}
