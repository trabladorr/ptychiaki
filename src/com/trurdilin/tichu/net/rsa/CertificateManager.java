package com.trurdilin.tichu.net.rsa;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v1CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.util.encoders.Base64;

import android.content.Context;

public class CertificateManager {
	private static KeyStore myCertificates;
	private static KeyStore remoteAuthenticatedCertificates;
	private static KeyStore remoteSessionCertificates;
	
	public static final String password = "verySecurePassword1234!@#$xXx";//unnecessary security
	public static final String myCertFilename = "TichuMyCetificateFile";
	public static final String authCertFilename = "TichuAuthCetificateFile";
	public static final String privateKeyEntry = "Tichu PrivateKey";
	public static final String certificateEntry = "Tichu Cetificate";
	
	public static final String rsaSignatureAlgorithm = "SHA512withRSA";
	public static final String ecdsaSignatureAlgorithm = "SHA512withECDSA";
	
	private static final ReadWriteLock remoteCertificateLock = new ReentrantReadWriteLock();
	private static final ReadWriteLock localCertificateLock = new ReentrantReadWriteLock();
	
	static {
		//set spongycastle as default provider
		Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
	}
	
	private static KeyPair createEcdsaKeypair(String name, Context context, SecureRandom sr) throws NoSuchAlgorithmException, OperatorCreationException, KeyStoreException, IOException, CertificateException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException{

		final String ecCurve = "brainpoolp384t1";
		
		ECGenParameterSpec ecGenSpec = new ECGenParameterSpec(ecCurve);
		
		//Create Certificate key pair
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "SC");
        keyGen.initialize(ecGenSpec, sr);
        return keyGen.generateKeyPair();
 	}
	
	private static KeyPair createRsaKeypair(String name, Context context, SecureRandom sr) throws NoSuchAlgorithmException, OperatorCreationException, KeyStoreException, IOException, CertificateException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException{

		final int keypairSize = 2048;
		
		//Create Certificate key pair
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "SC");
        keyGen.initialize(keypairSize);
        return keyGen.generateKeyPair();
	}
	
	private static void createMyCertificate(String name, Context context, String signatureAlgorithm) throws NoSuchAlgorithmException, OperatorCreationException, KeyStoreException, IOException, CertificateException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException{

		SecureRandom sr = new SecureRandom();
		KeyPair keyPair = null;
		if (signatureAlgorithm.equals(rsaSignatureAlgorithm))
			keyPair = createRsaKeypair(name, context, sr);
		else if (signatureAlgorithm.equals(ecdsaSignatureAlgorithm))
			keyPair = createEcdsaKeypair(name, context, sr);
        PrivateKey privKey = keyPair.getPrivate();
        PublicKey pubKey = keyPair.getPublic();
        
        //Set Certificate Details
		Calendar startDate = Calendar.getInstance();
		Calendar expiryDate = Calendar.getInstance();
		startDate.add(Calendar.DAY_OF_YEAR, -1);
		expiryDate.add(Calendar.DAY_OF_YEAR, 1000);
		BigInteger serialNumber = BigInteger.valueOf(1);
		X500Name cnName = new X500Name("CN="+name);
		
		
		//Create Certificate
		X509v1CertificateBuilder certGen = new JcaX509v1CertificateBuilder(cnName,
				serialNumber,
				startDate.getTime(),
				expiryDate.getTime(),
				cnName,
				pubKey);
		
		//Create signer
		ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).setProvider("SC").build(privKey); 
		
		//Self-sign
		X509CertificateHolder certHolder = certGen.build(signer);
		//Convert to X509 certificate
		X509Certificate cert = new JcaX509CertificateConverter().setProvider("SC").getCertificate(certHolder);

		//generate keystore
		myCertificates = KeyStore.getInstance("PKCS12");
		myCertificates.load(null);
		
		PrivateKeyEntry privEntry = new KeyStore.PrivateKeyEntry(privKey, new X509Certificate[]{cert});
		myCertificates.setEntry(privateKeyEntry, privEntry, null);
		myCertificates.setEntry(certificateEntry, new KeyStore.TrustedCertificateEntry(cert), null);	

	}

	
	private static void saveKeyStore(Context context, String filename, KeyStore keystore) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		FileOutputStream outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
		keystore.store(outputStream, password.toCharArray());
		outputStream.close();
	}
	
	private static KeyStore loadKeyStore(Context context, String filename) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		KeyStore keystore = KeyStore.getInstance("PKCS12");
		
		if (Arrays.asList(context.fileList()).contains(filename)){
			FileInputStream inputStream = context.openFileInput(filename);
			keystore.load(inputStream, password.toCharArray());
			inputStream.close();
		}
		else {
			keystore.load(null);
		}
		return keystore;
	}
	
	public static KeyStore getMyKeyStore(String name, Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, OperatorCreationException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException{
		localCertificateLock.writeLock().lock();
		
		boolean create = true;
		if (Arrays.asList(context.fileList()).contains(myCertFilename)){
			myCertificates = loadKeyStore(context, myCertFilename);
			if ( myCertificates.containsAlias(certificateEntry) && 
					((X509Certificate)((KeyStore.TrustedCertificateEntry) myCertificates.getEntry(certificateEntry, null)).getTrustedCertificate()).getSubjectDN().getName().replace("CN=", "").equals(name))
				create = false;
		}
		if (create){
			createMyCertificate(name, context, rsaSignatureAlgorithm);
			saveKeyStore(context, myCertFilename, myCertificates);
		}
		
		localCertificateLock.writeLock().unlock();
		
		return myCertificates;
	}

	public static void addAuthenticatedCertificate(Certificate cert, Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		remoteCertificateLock.writeLock().lock();
		
		if (remoteAuthenticatedCertificates == null)
			remoteAuthenticatedCertificates = loadKeyStore(context, authCertFilename);

		remoteAuthenticatedCertificates.setEntry(extractUsername(cert), new KeyStore.TrustedCertificateEntry(cert), null);
		saveKeyStore(context, authCertFilename, remoteAuthenticatedCertificates);
		
		remoteCertificateLock.writeLock().unlock();
	}
	
	public static void removeAuthenticatedCertificate(String username, Context context) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		remoteCertificateLock.writeLock().lock();
		
		if (remoteAuthenticatedCertificates == null)
			remoteAuthenticatedCertificates = loadKeyStore(context, authCertFilename);

		if (remoteAuthenticatedCertificates.containsAlias(username)){
			remoteAuthenticatedCertificates.deleteEntry(username);
			saveKeyStore(context, authCertFilename, remoteAuthenticatedCertificates);
		}
		
		remoteCertificateLock.writeLock().unlock();
	}
	
	public static void addSessionCertificate(Certificate cert) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		remoteCertificateLock.writeLock().lock();
		
		if (remoteSessionCertificates == null){
			remoteSessionCertificates = KeyStore.getInstance("PKCS12");
			remoteSessionCertificates.load(null);
		}
		
		remoteSessionCertificates.setEntry(extractUsername(cert), new KeyStore.TrustedCertificateEntry(cert), null);
		
		remoteCertificateLock.writeLock().unlock();
	}
	
	public static void removeSessionCertificate(String username) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException{
		remoteCertificateLock.writeLock().lock();
		
		if (remoteSessionCertificates == null){
			remoteSessionCertificates = KeyStore.getInstance("PKCS12");
			remoteSessionCertificates.load(null);
		}
		
		if (remoteSessionCertificates.containsAlias(username))
			remoteSessionCertificates.deleteEntry(username);
		
		remoteCertificateLock.writeLock().unlock();
	}
	
	public static boolean isAuthenticated(Context context, Certificate cert) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException, CertificateException, IOException{
		boolean ret = false;
		
		remoteCertificateLock.readLock().lock();

		if (remoteAuthenticatedCertificates == null){
			remoteCertificateLock.readLock().unlock();
			remoteCertificateLock.writeLock().lock();
			
			remoteAuthenticatedCertificates = loadKeyStore(context, authCertFilename);
			
			remoteCertificateLock.writeLock().unlock();
			remoteCertificateLock.readLock().lock();
		}
		
		if (remoteAuthenticatedCertificates.containsAlias(extractUsername(cert)))
			ret = ((KeyStore.TrustedCertificateEntry) remoteAuthenticatedCertificates.getEntry(extractUsername(cert), null)).getTrustedCertificate().equals(cert);
		
		remoteCertificateLock.readLock().unlock();
		
		return ret;
	}
	
	public static List<String> getAuthenticated(Context context) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException, CertificateException, IOException{
		List<String> ret = null;
		
		remoteCertificateLock.readLock().lock();
		
		if (remoteAuthenticatedCertificates == null){
			remoteCertificateLock.readLock().unlock();
			remoteCertificateLock.writeLock().lock();
			
			remoteAuthenticatedCertificates = loadKeyStore(context, authCertFilename);
			
			remoteCertificateLock.writeLock().unlock();
			remoteCertificateLock.readLock().lock();
		}
		
		ret = Collections.list(remoteAuthenticatedCertificates.aliases());
		
		remoteCertificateLock.readLock().unlock();
		
		
		return ret;
	}
	
	public static boolean verifyCertificate(Certificate cert) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException, CertificateException, IOException{
		remoteCertificateLock.readLock().lock();
		
		if (remoteAuthenticatedCertificates != null && remoteAuthenticatedCertificates.containsAlias(extractUsername(cert))){
			boolean ret = ((KeyStore.TrustedCertificateEntry) remoteAuthenticatedCertificates.getEntry(extractUsername(cert), null)).getTrustedCertificate().equals(cert);
			remoteCertificateLock.readLock().unlock();
			return ret;
		}
		if (remoteSessionCertificates != null && remoteSessionCertificates.containsAlias(extractUsername(cert))){
			boolean ret = ((KeyStore.TrustedCertificateEntry) remoteSessionCertificates.getEntry(extractUsername(cert), null)).getTrustedCertificate().equals(cert);
			remoteCertificateLock.readLock().unlock();
			return ret;
		}
		
		remoteCertificateLock.readLock().unlock();
		
		addSessionCertificate(cert);
		return true;
	}

	
	public static Certificate extractCertificate(SSLSocket socket) throws SSLPeerUnverifiedException{
		SSLSession session = socket.getSession();
		Certificate servercerts[] = session.getPeerCertificates();
		return servercerts[0];
	}
	
	public static String extractUsername(Certificate cert){
		if (cert instanceof X509Certificate)
			return ((X509Certificate)cert).getSubjectDN().getName().replace("CN=", "");
		return null;
	}
	
	private static String certificateHmacSHA256(Certificate cert){
		String ret = null;
		try {			
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secret_key = new SecretKeySpec(password.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secret_key);
			
			ret = Base64.toBase64String(sha256_HMAC.doFinal(cert.getEncoded()));
		}
		catch (Exception e){
		}
		return ret;
	}
	
	public static String authenticationString(Certificate remoteCert, boolean isHost){
		String local = null;
		String remote = null;
		
		localCertificateLock.readLock().lock();
		
		try {
			if (myCertificates != null && myCertificates.containsAlias(certificateEntry))
				local = certificateHmacSHA256(((KeyStore.TrustedCertificateEntry) myCertificates.getEntry(certificateEntry, null)).getTrustedCertificate());
		}
		catch (Exception e) {
		}
		
		localCertificateLock.readLock().unlock();
		
		remoteCertificateLock.readLock().lock();
		
		try {
			remote = certificateHmacSHA256(remoteCert);
		}
		catch (Exception e) {
		}
		
		remoteCertificateLock.readLock().unlock();
		
		if (local == null || remote == null)
			return null;
		else if (isHost)
			return local+"\n"+remote;
		else
			return remote+"\n"+local;
	}
}
