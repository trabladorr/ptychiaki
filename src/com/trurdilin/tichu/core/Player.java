package com.trurdilin.tichu.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.trurdilin.tichu.core.Card.DefaultCardComparator;
import com.trurdilin.tichu.core.Card.Rank;
import com.trurdilin.tichu.core.Card.Special;


public class Player {

	private List<Card> hand = new ArrayList<Card>();
	private List<Card> won = new ArrayList<Card>();
	private Map<Integer,Card> gifts = new HashMap<Integer,Card>();
	private final static Comparator<Card> cardSorter = new DefaultCardComparator();
	public final int id;
	public final boolean dummy;
	
	public Player(Player that){
		this.hand = new ArrayList<Card>(that.hand);
		this.won = new ArrayList<Card>(that.won);
		this.gifts = new HashMap<Integer,Card>(that.gifts);
		this.id = that.id;
		this.dummy = that.dummy;
	}
	
	private Player(int id,boolean dummy){
		this.id = id;
		this.dummy = dummy;
	}
	
	public static Player create(int id){
		return new Player(id,false);
	}
	
	public static Player createDummy(int id){
		return new Player(id,true);
	}
	
	
	
	//reset a Player's hand and won cards, for next round
	public void reset(){//TODO: Needed? not used
		hand = new ArrayList<Card>();
		won = new ArrayList<Card>();
		gifts = new HashMap<Integer,Card>();
	}
	
	
	
	//Play a Move
	public void playMove(Move move) throws ImpossibleMoveException{
		//TODO: Active player gives a known gift to dummy player, card may get removed out of proper move containing it
		//... /care?
		if (!hand.containsAll(move.getCards()))
			throw new ImpossibleMoveException();
		if (dummy)
			for (int i=0; i<move.getCards().size(); i++)
				hand.remove(0);
		else{
			hand.removeAll(move.getCards());
			Collections.sort(hand,cardSorter);//TODO:Needed?
		}
	}
	
	//Force a Player to draw Cards from Deck provided
	public void drawCards(List<Card> cards){
		hand.addAll(cards);
		Collections.sort(hand,cardSorter);
	}
	
	//Give a player a won Trick
	public void winCards(List<Card> cards){
		won.addAll(cards);
	}
	
	//Give a card To another Player
	public void giveCard(Card card, Player that) throws ImpossibleMoveException{
		if (this.dummy && that.dummy)
			throw new ImpossibleMoveException();
		if (!hand.contains(card))
			throw new ImpossibleMoveException();
		
		if (this.dummy){//if a dummy player gives a card to the active player
			that.gifts.put(that.id, card);
			this.hand.remove(0);
		}
		else//if the active player gives a card to a dummy one, or if this is the server core (all players are fully known)
			that.gifts.put(that.id,this.hand.remove(this.hand.indexOf(card)));
	}
	
	public void giveUnknownCard(Player that) throws ImpossibleMoveException{
		if (!this.dummy)
			throw new ImpossibleMoveException();
		if (that.dummy)
			that.gifts.put(that.id,this.hand.remove(0));
		else
			this.hand.remove(0);
	}
	
	//Pick up gifts
	public void receiveGifts(List<Card> cards){
		if (cards == null){
			for (int id:gifts.keySet()){
				hand.add(gifts.get(id));
			}
		}
		else
			hand.addAll(cards);
		
	}
	
	//last Player gets his hand stolen by the other team
	public void stealHand(Player that){
		this.won.addAll(that.hand);
		that.hand = new ArrayList<Card>();
	}
	
	//last Player gets his won stolen by the player who finished first
	public void stealWon(Player that){
		this.won.addAll(that.won);
		that.won = new ArrayList<Card>();
	}
	
	//check if a player Can fulfill a wish
	public boolean canPlayWish(Card.Rank wish,Move previousMove){
		boolean hasWish = false;
		for (Card card: hand)
			if (card.rank == wish){
				hasWish = true;
				break;
			}	
		if (!hasWish)
			return false;
		
		if (previousMove.getType() == MoveType.SingleCard){//If player can play wish as single card in current Trick
			if (previousMove.getCards().get(0).value < wish.value)
				return true;
		}
		else if (previousMove.getType() == MoveType.Sequence){//If player can play a sequence with wish card in current Trick
			if (Move.containsSequenceWithWish(hand, previousMove, wish))
				return true;
		}
		
		if (Move.containsBombWithWish(hand, previousMove, wish))//If player can play a bomb with wish inside
			return true;
		
		return false;
	}
	
	public boolean hasMahJong(){
		for (Card card: hand)
			if (card.special == Special.MAHJONG)
				return true;
			else if (card.unknown)
				return true;
		return false;
	}
	
	public int getWonScore(){
		int score = 0;
		for (Card card: won){
			if (card.rank == Rank.FIVE)
				score += 5;
			else if (card.rank == Rank.TEN || card.rank == Rank.KING)
				score += 10;
			else if (card.special == Special.DRAGON)
				score += 25;
			else if (card.special == Special.PHOENIX)
				score -= 25;
		}
		return score;
	}
	
	public boolean hasCardsLeft(){
		return !hand.isEmpty();
	}
	
	
	//Accessors - get methods
	
	//Get a Player's won cards
	public List<Card> getWon(){
		return new ArrayList<Card>(this.won);
	}
	
	//Get a Player's hand cards
	public List<Card> getHand(){
		return new ArrayList<Card>(this.hand);
	}

	//Get a Player's gift cards
	public Map<Integer,Card> getGift(){
		return new HashMap<Integer,Card>(gifts);
	}
	
	public static class ImpossibleMoveException extends Exception{
		private static final long serialVersionUID = 7385058558788482264L;
	}
}
