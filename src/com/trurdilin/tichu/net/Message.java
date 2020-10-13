package com.trurdilin.tichu.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import com.trurdilin.tichu.core.Card;
import com.trurdilin.tichu.core.Card.Rank;
import com.trurdilin.tichu.core.Card.Special;
import com.trurdilin.tichu.core.Card.Suit;
import com.trurdilin.tichu.core.Round.Phase;

public class Message {

	public static final String TYPE_JOIN_REQUEST = "join";
	public static final String TYPE_LEAVE_REQUEST = "leave";
	public static final String TYPE_JOIN_ACCEPTED = "joinAccepted"; 
	public static final String TYPE_JOIN_REJECTED = "joinRejected"; 
	public static final String TYPE_AUTH = "authenticate";
	public static final String TYPE_LEAVE_ACCEPTED = "leaveAccepted"; 
	public static final String TYPE_READY = "playerReady"; 
	public static final String TYPE_DEAL_CARDS = "dealCards";
	public static final String TYPE_RECEIVE_GIFTS = "receiveGifts";
	public static final String TYPE_MAHJONG_PLAYER = "playerWithMahjong";
	public static final String TYPE_GRANDE = "grandeDecision";
	public static final String TYPE_GIVE_GIFT = "giveGiftCard";
	public static final String TYPE_MOVE = "playMove";
	public static final String TYPE_DRAGON = "dragonDecision";
	public static final String TYPE_MAHJONG = "mahjongDecision";
	public static final String TYPE_TICHU = "tichuDecision";
	public static final String TYPE_EXIT_GAME = "exitGame";

	public static final String FIELD_IP = "Ip";
	public static final String FIELD_PORT = "Port"; 
	public static final String FIELD_PLAYER = "Player";
	public static final String FIELD_RCV_PLAYER = "ReceivingPlayer";  

	public static final String FIELD_PHASE = "Phase";
	public static final String FIELD_GRANDE = "Grande";
	public static final String FIELD_TICHU = "Tichu";
	public static final String FIELD_FORCE = "Force";
	public static final String FIELD_PLAYER_ID = "PlayerId"; 
	public static final String FIELD_CARD = "Card"; 
	public static final String FIELD_SPECIAL = "Special";
	public static final String FIELD_SUIT = "Suit"; 
	public static final String FIELD_RANK = "Rank"; 
	public static final String FIELD_UNKNOWN = "Unknown"; 
	
	private List<XMLNode> subNodes = new ArrayList<XMLNode>();
	private boolean ready = false;
	
	//construct a message by receiving it from an inputstream
	public Message(InputStream is) throws XmlPullParserException, MessageException, IOException {
		
		//initialize XML Pull Parser
    	XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(is, "UTF-8");
		
        //Document starts
      	if(xpp.getEventType() != XmlPullParser.START_DOCUMENT) 
        	throw new MessageException("Message does not start with XmlPullParser.START_DOCUMENT event");
        
      	//parse opening tags
        int eventType;
		do {
			eventType = xpp.next();
			if(eventType == XmlPullParser.START_TAG) {
				subNodes.add(new XMLNode(xpp));
			}
		}
		while(eventType != XmlPullParser.END_DOCUMENT);
		
		ready = true;
	}

	
	//construct a message for sending
	public Message(List<XMLNode> subNodes){
		if (subNodes != null)
			this.subNodes.addAll(subNodes);
	}
	
	
	public Message(){
	}
	
	
	//send a message
	public synchronized void send(OutputStream os) throws IllegalArgumentException, IllegalStateException, IOException, XmlPullParserException{
		
		ready = true;
		XmlSerializer serializer;
    	XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        serializer = factory.newSerializer();
        serializer.setOutput(os, "UTF-8");
        serializer.startDocument(null, Boolean.valueOf(true)); //????? TODO: ???????

        for (XMLNode node : subNodes)
        	node.addToMessage(serializer);
        
		serializer.endDocument();
		new PrintWriter(os).println(" ");
	}
	
	@Override
	public String toString(){
		String ret = "";
		for (XMLNode node:subNodes)
			ret+=node.toString(0);
		return ret;
	}
	
	public List<XMLNode> getSubNodes(){
		return new ArrayList<XMLNode>(subNodes);
	}
	
	public XMLNode getSubNodeByName(String type){
		for (XMLNode node:subNodes){
			XMLNode tmp = node.getSubNodeByName(type);
			if (tmp != null)
				return tmp;
		}
		return null;
	}
	
	public List<XMLNode> getSubNodesByName(String type){
		List<XMLNode> ret = new ArrayList<Message.XMLNode>();
		
		for (XMLNode node:subNodes){
			List<XMLNode> tmp = node.getSubNodesByName(type); 
			if (tmp != null)
				ret.addAll(tmp);
		}
		if (ret.isEmpty())
			return null;
		else
			return ret;
	}
	
	public Message addSubNode(XMLNode node) throws MessageException{
		if (ready)
			throw new MessageException("Ready Message modification attempted");
		subNodes.add(node);
		return this;
	}
	
	public Message addSubNode(String name, String text) throws MessageException{
		return this.addSubNode(new XMLNode(name, text));
	}
	
	public void finalize(){
		ready = true;
	}
	
	public String getMessageType(){
		return subNodes.get(0).getName();
	}
	
	//Functions to help convert a Java Object into an XMLNode tree and vice versa
	
	//Converts a list of cards to a list of XMLNodes
	public static List<XMLNode> cardsToXMLNode(List<Card> cards){
	       
		List<XMLNode> ret = new ArrayList<XMLNode>();
		
	    for (Card card : cards)
	        ret.add(cardToXMLNode(card));
	    
	    return ret;  
	}	
	
	//converts Card to XMLNode
	public static XMLNode cardToXMLNode(Card card){
	       
        XMLNode node = new XMLNode(FIELD_CARD, null);
        

        if (card.unknown)
        	node.addSubNode(new XMLNode(FIELD_UNKNOWN,null));
        else{
	        if (card.special != null)
	        	node.addSubNode(new XMLNode(FIELD_SPECIAL,card.special.toString()));
	        
	        if (card.suit != null)
	        	node.addSubNode(new XMLNode(FIELD_SUIT,card.suit.toString()));
	        
	        if (card.rank != null)
	        	node.addSubNode(new XMLNode(FIELD_RANK,card.rank.toString()));
	    }

	    return node;  
	}
	
	public List<Card> getCards(){
		List<XMLNode> cardNodes = getSubNodesByName(FIELD_CARD);
		if (cardNodes == null)
			return null;
		
		try{
			return XMLNodesToCards(cardNodes);
		}
		catch(Exception e){
		}
		return null;
	}
	
	//converts a list of XMLNodes to a list of cards
	public static List<Card> XMLNodesToCards(List<XMLNode> nodes) throws MessageException{
		List<Card> ret = new ArrayList<Card>();
		
		for (XMLNode node : nodes){
			if (node.getName().equals(FIELD_CARD))
				ret.add(XMLNodeToCard(node));
		}
		
		return ret;
	}
	
	
	//convert an XMLNode to a Card
	public static Card XMLNodeToCard(XMLNode node) throws MessageException{
		if (!node.getName().equals(FIELD_CARD))
			throw new MessageException("XMLNodeToCard called with a node not describing a card (name="+node.getName()+")");
		
	    Special tmpSpecial = null;
	    Rank tmpRank = null;
	    Suit tmpSuit = null;
	    boolean unknown = false;
		
		for (XMLNode n : node.getSubNodes()){
			if (n.getName().equals(FIELD_UNKNOWN))
				unknown = true;
			else if (n.getName().equals(FIELD_SPECIAL))
			    tmpSpecial = Special.valueOf(n.getText());
			else if (n.getName().equals(FIELD_SUIT))
				tmpSuit = Suit.valueOf(n.getText());
			else if (n.getName().equals(FIELD_RANK))
					tmpRank = Rank.valueOf(n.getText());
			else
				throw new MessageException("XMLNodeToCard's card node contains wrong XMLNode (name="+n.getName()+")");
		}
		
		return new Card(tmpSuit, tmpRank, tmpSpecial, unknown);
	}
	
	
	public static class XMLNode{

		private List<XMLNode> subNodes = new ArrayList<XMLNode>();
		private String name;
		private String text = null;
		
		//Constructs an XMLNode tree recursively from a message's XMLPullParser
		public XMLNode(XmlPullParser xpp) throws XmlPullParserException, IOException{
			
			this.name = xpp.getName();
			
			int eventType;
			do {
				eventType = xpp.next();
				if(eventType == XmlPullParser.START_TAG)
					subNodes.add(new XMLNode(xpp));
				else if (eventType == XmlPullParser.TEXT)
					text = xpp.getText();
			}
			while(eventType != XmlPullParser.END_TAG);
		}
		
		
		public XMLNode(String name, String text, List<XMLNode> subNodes){
			
			this.name = name;
			this.text = text;
			if (subNodes != null)
				this.subNodes.addAll(subNodes);
		}
		
		
		public XMLNode(String name, String text){
			
			this.name = name;
			this.text = text;
		}
		
		
		public XMLNode addSubNode(XMLNode node){
			subNodes.add(node);
			return this;
		}
		
		public XMLNode addSubNode(String name, String text){
			return this.addSubNode(new XMLNode(name, text));
		}
		
		public XMLNode addSubNodes(List<XMLNode> nodes){
			subNodes.addAll(nodes);
			return this;
		}
		
		public XMLNode getSubNodeByName(String type){
			if (name.equals(type))
				return this;
			for (XMLNode node:subNodes){
				XMLNode tmp = node.getSubNodeByName(type);
				if (tmp != null && tmp.name.equals(type))
					return tmp;
			}
			return null;
		}
		
		public List<XMLNode> getSubNodesByName(String type){
			List<XMLNode> ret = new ArrayList<XMLNode>();
			if (name.equals(type))
				ret.add(this);
			
			for (XMLNode node:subNodes){
				List<XMLNode> tmp = node.getSubNodesByName(type); 
				if (tmp != null)
					ret.addAll(tmp);
			}
			if (ret.isEmpty())
				return null;
			else
				return ret;
		}
		
		
		
		//adds XMLNode to the message's serializer recursively
		public void addToMessage(XmlSerializer serializer) throws IllegalArgumentException, IllegalStateException, IOException{
	        serializer.startTag(null, name);
	        if (text != null)
	        	serializer.text(text);
	        for (XMLNode node : subNodes)
	        	node.addToMessage(serializer);
	        serializer.endTag(null, name);  
		}
		
		public String toString(int tabs){
			String ret = "";
			for (int i=0;i<tabs;i++)
				ret += "\t";
			ret += "<"+name+">\n";
			if (text != null){
				for (int i=0;i<tabs;i++)
					ret += "\t";
				ret+= text+"\n";
			}
			for (XMLNode node:subNodes)
				ret+=node.toString(tabs+1);
			for (int i=0;i<tabs;i++)
				ret += "\t";
			ret += "</"+name+">\n";
			return ret;
		}
		
		//get accessors
		public List<XMLNode> getSubNodes(){
			return new ArrayList<XMLNode>(subNodes);
		}
		
		public String getName(){
			return name;
		}
		
		public String getText(){
			return text;
		}
		
		public Integer getTextAsInt(){
			try{
				return Integer.parseInt(text);
			}
			catch(Exception e){
			}
			return null;
		}
		
		public Boolean getTextAsBoolean(){
			try{
				return Boolean.parseBoolean(text);
			}
			catch(Exception e){
			}
			return null;
		}
		
		public Phase getTextAsPhase(){
			try{
				return Phase.valueOf(text);
			}
			catch(Exception e){
			}
			return null;
		}
		
		public Rank getTextAsRank(){
			try{
				return Rank.valueOf(text);
			}
			catch(Exception e){
			}
			return null;
		}
		
		public Card toCard(){
			if (this.name != FIELD_CARD)
				return null;
			try{
				return XMLNodeToCard(this);
			}
			catch(Exception e){
			}
			return null;
		}
	}
	
	public static class MessageException extends Exception{

		private static final long serialVersionUID = -1126814582698762701L;

		public MessageException(String str){
    		super(str);
    	}
    }
	
	public static class CompoundMessage{
		
		public final Message message;
		public final String username;
		public final Certificate cert;
		
		public CompoundMessage(Message message, String username, Certificate cert){
			this.message = message;
			this.username = username;
			this.cert = cert;
		}
	}
}
