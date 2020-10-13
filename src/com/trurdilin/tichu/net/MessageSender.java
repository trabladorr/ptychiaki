package com.trurdilin.tichu.net;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.jmdns.ServiceInfo;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.spongycastle.operator.OperatorCreationException;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;
import android.os.AsyncTask;
import android.util.Log;

import com.trurdilin.tichu.net.Message.CompoundMessage;
import com.trurdilin.tichu.net.rsa.CertificateManager;

public class MessageSender{
	
	private static SSLSessionCache sslSessionCache;
	private static SSLCertificateSocketFactory sslSocketFactory; //Assuming sslSocketFactory is thread safe //TODO: but is it?
	private static String name = null;

	private static final Map<String, InetSocketAddress> addresses = new ConcurrentHashMap<String, InetSocketAddress>();
	private static final Map<String, ConcurrentLinkedQueue<Message>> messageQueues = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>>();
	private static final Map<String, Thread> senderThreads = new ConcurrentHashMap<String, Thread>();

	private static boolean running = false;

	private static final Object initLock = new Object();
	private static final ReadWriteLock socketFactoryLock = new ReentrantReadWriteLock();
	private static final ReadWriteLock runningLock = new ReentrantReadWriteLock();


	public static void init (Context context, String name) throws UnrecoverableKeyException, InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, OperatorCreationException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException, IOException{
		synchronized(initLock){
			if (MessageSender.name == null || !MessageSender.name.equals(name)){
				MessageSender.name = name;
				
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
				kmf.init(CertificateManager.getMyKeyStore(name, context), CertificateManager.password.toCharArray());
				KeyManager[] keyManagers = kmf.getKeyManagers();
				
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
				
				socketFactoryLock.writeLock().lock();
				sslSessionCache = new SSLSessionCache(context);
				sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0, sslSessionCache);
				sslSocketFactory.setKeyManagers(keyManagers);
				sslSocketFactory.setTrustManagers(trustManagers);
				socketFactoryLock.writeLock().unlock();
			}
		}
	}
	
	public static void sendMessage(final CompoundMessage m){
		AsyncTask.execute(new Runnable(){
		
			@Override
			public void run(){
				
				if (!messageQueues.containsKey(m.username))
					messageQueues.put(m.username, new ConcurrentLinkedQueue<Message>());
				messageQueues.get(m.username).add(m.message);
				
				if (!addresses.containsKey(m.username)){
					Log.e(MessageSender.class.getSimpleName(),"Sending message to recipient whose address is not registered");
					return;
				}
				
				if (!senderThreads.containsKey(m.username)){
					Thread thread = new Thread(new TargetMessageSender(m.username));
					if (!senderThreads.containsKey(m.username)){
						senderThreads.put(m.username, thread);
						thread.start();
					}
				}
			}
		});
	}
	
	public static void setAddress(String userName, InetSocketAddress address){
		addresses.put(userName, address);
	}
	
	public static void setAddress(String userName, ServiceInfo address){
		addresses.put(userName, new InetSocketAddress(address.getInet4Addresses()[0], address.getPort()));
	}
	
	public static boolean isRunning(){//when app is in background
		boolean ret;
		
		runningLock.readLock().lock();
		ret = running;
		runningLock.readLock().unlock();
		
		return ret;
	}
	
	public static void startSending(){
		runningLock.writeLock().lock();
		running = true;
		runningLock.writeLock().unlock();
		
	}
	
	public static void stopSending(){//when app is in background
		runningLock.writeLock().lock();
		running = false;
		runningLock.writeLock().unlock();
	}
	
	private static class TargetMessageSender implements Runnable{
		private SSLSocket sslSocket = null;
		private final String username; 
		
		private TargetMessageSender(String username){
			this.username = username;
		}
		
		@Override
		public void run() {

			while(true){
				boolean isRunning;
			
				runningLock.readLock().lock();
				isRunning = running;
				runningLock.readLock().unlock();
				
				if (!isRunning)
					break;
				
				try {

					Message m = messageQueues.get(username).peek();
					
					if (m == null){
						try{
							Thread.sleep((NetSettings.sleepTime));
						}
						catch(Exception e){
						}
						continue;
					}
						
					try {
						InetSocketAddress address = addresses.get(username);
						
						socketFactoryLock.readLock().lock();
						sslSocket = (SSLSocket) sslSocketFactory.createSocket(address.getAddress(), address.getPort());	
						socketFactoryLock.readLock().unlock();
						
						sslSocket.setEnabledProtocols(NetSettings.selectedProtocols);
						sslSocket.setEnabledCipherSuites(NetSettings.selectedCiphersuites);
					}
					catch (Exception e) {
						senderThreads.remove(username);
						if (NetSettings.debug)
							Log.e(MessageSender.class.getSimpleName(),"SSLSocket init failed: "+Log.getStackTraceString(e));
						return;
					}
					
					if (!CertificateManager.verifyCertificate(CertificateManager.extractCertificate(sslSocket)))
						throw new Exception("Certificate doesn't match previous one, malicious activity suspected.");
				
					OutputStream out = sslSocket.getOutputStream();
					m.send(out);
					out.flush();
					
					sslSocket.close();
					
					messageQueues.get(username).remove();
				} 
				catch (Exception e) {
					if (NetSettings.debug)
						Log.e(MessageSender.class.getSimpleName(),"Mesage sending failed: "+Log.getStackTraceString(e));
				}
			}
			
			try {
				sslSocket.close();
			} 
			catch (IOException e) {
			}
			
			senderThreads.remove(username);
		}
		
	}

}
