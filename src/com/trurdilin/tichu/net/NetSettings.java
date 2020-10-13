package com.trurdilin.tichu.net;

public class NetSettings {
	public static final int chosenPort = 10991;
	public static final int sleepTime = 100;
	public static final int serverSocketSoTimeout = 1000;
	public static final int socketConnectTimeout = 5000;
	public static final boolean debug = true;
	public static final String[] selectedProtocols = {"TLSv1.2"};
	public static final String[] selectedCiphersuites = {"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"};
	//public static final String[] selectedCiphersuites = {"TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA"};
}
