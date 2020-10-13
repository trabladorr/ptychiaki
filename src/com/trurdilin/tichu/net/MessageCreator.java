package com.trurdilin.tichu.net;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.trurdilin.tichu.core.Card;
import com.trurdilin.tichu.core.Card.Rank;
import com.trurdilin.tichu.core.Round.Phase;
import com.trurdilin.tichu.net.Message.CompoundMessage;
import com.trurdilin.tichu.net.Message.MessageException;
import com.trurdilin.tichu.net.Message.XMLNode;

public class MessageCreator {

	public static CompoundMessage joinGame(String recipient, InetSocketAddress address) {
		Message m = new Message();
		try {
			m.addSubNode(Message.TYPE_JOIN_REQUEST, null)
				.addSubNode(Message.FIELD_IP, address.getAddress().getHostAddress())
				.addSubNode(Message.FIELD_PORT, ""+address.getPort())
				.finalize();
		}
		catch (MessageException e) {
		}
		m.finalize();
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage leaveGame(String recipient) {
		Message m = new Message();
		try {
			m.addSubNode(Message.TYPE_LEAVE_REQUEST, null).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage authRequest(String recipient) {
		
		Message m = new Message();
		try {
			m.addSubNode(new XMLNode(Message.TYPE_AUTH, null)).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage joinAccepted(String recipient, String[] players) {

		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_JOIN_ACCEPTED, null);
			for (String player:players)
				node.addSubNode(Message.FIELD_PLAYER, player);
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage joinRejected(String recipient) {

		Message m = new Message();
		try {
			m.addSubNode(new XMLNode(Message.TYPE_JOIN_REJECTED, null)).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage leaveAccepted(String recipient, String player) {

		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_LEAVE_ACCEPTED, null);
			node.addSubNode(Message.FIELD_PLAYER, player);
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage playerReady(String recipient, String[] players) {

		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_READY, null);
			for (String player:players)
				node.addSubNode(Message.FIELD_PLAYER, player);
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage dealCards(String recipient, Phase phase, List<Card> cards){
		
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_DEAL_CARDS, null);
			node.addSubNode(Message.FIELD_PHASE, phase.toString());
			
			for (XMLNode subnode: Message.cardsToXMLNode(cards))
				node.addSubNode(subnode);
				
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage receiveGifts(String recipient, Map<String, Card> playerCards){
		
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_RECEIVE_GIFTS, null);
			
			for (String player: playerCards.keySet()){
				XMLNode subnode = new XMLNode(Message.FIELD_PLAYER, null);
				subnode.addSubNode(Message.cardToXMLNode(playerCards.get(player)));
			}
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage mahjongPlayer(String recipient, String player){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_MAHJONG_PLAYER, null);
			node.addSubNode(Message.FIELD_PLAYER, player);
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage grandeDecision(String recipient, String player, Boolean grande, Boolean force){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_GRANDE, null);
			node.addSubNode(Message.FIELD_PLAYER, player);
			node.addSubNode(Message.FIELD_GRANDE, grande.toString());
			node.addSubNode(Message.FIELD_FORCE, force.toString());
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage giveGift(String recipient, String giver, String receiver, Card card){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_GIVE_GIFT, null);
			node.addSubNode(Message.FIELD_PLAYER, giver);
			node.addSubNode(Message.FIELD_RCV_PLAYER, receiver);
			node.addSubNode(Message.cardToXMLNode(card));
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage playMove(String recipient, String player, List<Card> cards){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_MOVE, null);
			node.addSubNode(Message.FIELD_PLAYER, player);
			for (XMLNode subnode: Message.cardsToXMLNode(cards))
				node.addSubNode(subnode);
			
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage dragonDecision(String recipient, String decider, String receiver){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_DRAGON, null);
			node.addSubNode(Message.FIELD_PLAYER, decider);
			node.addSubNode(Message.FIELD_RCV_PLAYER, receiver);
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage mahjongWish(String recipient, String decider, Rank rank){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_MAHJONG, null);
			node.addSubNode(Message.FIELD_PLAYER, decider);
			node.addSubNode(Message.FIELD_RANK, rank.toString());
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage tichuDecision(String recipient, String player, Boolean force){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_TICHU, null);
			node.addSubNode(Message.FIELD_PLAYER, player);
			node.addSubNode(Message.FIELD_FORCE, force.toString());
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
	
	public static CompoundMessage exitGame(String recipient){
		Message m = new Message();
		try {
			XMLNode node = new XMLNode(Message.TYPE_EXIT_GAME, null);
			m.addSubNode(node).finalize();
		}
		catch (MessageException e) {
		}
		
		return new CompoundMessage(m, recipient, null);
	}
}