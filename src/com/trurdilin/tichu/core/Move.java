package com.trurdilin.tichu.core;

import static com.trurdilin.tichu.core.Card.Special.PHOENIX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.trurdilin.tichu.core.Card.Special;


public class Move {
	
	private MoveType type;	
	private List<Card> cards = new ArrayList<Card>();		
	
	public Move(Move move){
		this.cards = new ArrayList<Card>(move.cards);
		this.type = move.type;
	}
	
	private Move(List<Card> cards, MoveType type){
		this.cards = cards;
		this.type = type;
	}
	
	//checks if a list of cards would constitute a valid move over the previousMove, returning it's MoveType
	//null returned means invalid Move (either wrong cards for a move, or not beating previous Move)
	//MoveType isValid functions expect cards to be sorted, and Phoenix to have a value if in cards
	public static MoveType isValid(List<Card> cards,Move previousMove){
		if (previousMove == null){
			for (MoveType type: MoveType.values())
				if (type.valid(cards, null))
					return type;
			}
		else{// if cards are to be played on top of previous Move
			if (previousMove.type.valid(cards, previousMove.cards))//if cards are of the same MoveType and they beat the previous, it's a valid Move
				return previousMove.type;
		
			if (previousMove.type != MoveType.QuadBomb && previousMove.type != MoveType.SequenceBomb){//if no bombs involved previously
				if (MoveType.QuadBomb.valid(cards, null))
					return MoveType.QuadBomb;
				else if (MoveType.SequenceBomb.valid(cards, null))//bombs beat all normal cards
					return MoveType.SequenceBomb;
			}
			else if (previousMove.type == MoveType.QuadBomb){
				if (MoveType.SequenceBomb.valid(cards, null))//SequenceBombs beat QuadBombs
					return MoveType.SequenceBomb;
			}
		}	
		return null;
	}
	
	//call copy constructor staticalylylyllliiiiyyyy
		public static Move copy(Move move){
				return new Move(move);
		}
	
	//check if list of cards are a valid move, return that Move
	public static Move create(List<Card> cards,Move previousMove) throws InvalidMoveException{
		
		MoveType type = isValid(cards,previousMove);
		if (type != null)
			return new Move(cards,type);
		else
			throw new InvalidMoveException();
	}
	
	
	//use this to add any Card to a List destined to be a Move
	public static List<Card> addToFutureMove(List<Card> cards, Card card) throws InvalidMoveCreationException{
		if (cards == null)
			cards = new ArrayList<Card>();
		if (card.special == PHOENIX)
			throw new InvalidMoveCreationException();
		cards.add(card);
		Collections.sort(cards);
		return cards;
	}
	
	//use to add a Phoenix card to a List destined to be a Move
	public static List<Card> addPhoenixToFutureMove(List<Card> cards, Card card,int index) throws InvalidMoveCreationException,WrongPhoenixPositionException{
		
		if (cards == null)
			cards = new ArrayList<Card>();
		if (card.special != PHOENIX)
			throw new InvalidMoveCreationException();
		cards.add(index, card);
		if (!assignPhoenixValue(cards))
			throw new WrongPhoenixPositionException();
		return cards;
	}
	
	//use this to remove any Card from a List destined to be a Move, returns null if last card removed
	public static List<Card> removeFromFutureMove(List<Card> cards, Card card) throws InvalidMoveCreationException{
		if (cards == null || !cards.contains(card))
			throw new InvalidMoveCreationException();
		cards.remove(card);
		if (cards.isEmpty())
			return null;
		return cards;
	}

	//will attempt to assign value to a phoenix card, return true if successful assignment, false if not(false also removes phoenix from list)
	//if it's played as single card, returns success and assigns 1.5 (actual value depends on previous card played, will be assigned at movetype eval)
	//phoenix card should be tested to exist in cards before call
	private static boolean assignPhoenixValue(List<Card> cards){
		//TODO: Mass Debug
		boolean ret = true;
		for (Card card: cards)
			if (card.special == PHOENIX){
				
				if (cards.size() == 1){
					card.value = (float)1.5;
					break;
				}
					
				//first detect Sequence MoveType
				boolean differentValues = true;
				float tmpVal = 0;
				for (Card tmp: cards)
					if (tmp.value == tmpVal)
						differentValues = false;
				
				//find values of next and previous card
				float prevVal = 0;
				if (cards.indexOf(card)>0)
					prevVal = cards.get(cards.indexOf(card)-1).value;
				float nextVal = 0;
				if (cards.indexOf(card)<cards.size()-1)
					nextVal = cards.get(cards.indexOf(card)+1).value;
				
				//
				if (differentValues){
					if (prevVal > 0)
						card.value = prevVal+1;
					else //more than two cards at this point, if no previous card, there must be a next card
						card.value = nextVal-1;
				}
				else{
					if (prevVal > 0 && nextVal > 0 && prevVal != nextVal){
						cards.remove(card);
						ret = false;
					}
					if (prevVal > 0)
						card.value = prevVal;
					else //more than two cards at this point, if no previous card, there must be a next card
						card.value = nextVal;
				}
				break;
			}
		return ret;
		
	}
	
	//check if move contains MahJong
	public boolean containsMahJong(){
		for (Card card: cards)
			if (card.special == Card.Special.MAHJONG)
				return true;
		return false;
	}
	
	//check if list of cards contains Sequence with Wish
	//considers Cards sorted with Card.DefaultCardComparator
	public static boolean containsSequenceWithWish(List<Card> cards, Move previousMove, Card.Rank wish){
		//TODO: Mass Debug
		boolean hasWish = false;
		boolean containsPhoenix = false;
		for (Card card: cards){
			if (card.rank == wish)
				hasWish = true;
			else if (card.special == Special.PHOENIX)
				containsPhoenix = true;
		}
		if (!hasWish)
			return false;
		
		
		if (cards.size() < previousMove.getCards().size())
			return false;
		
		boolean containsWish = false;
		int sequence = 1;
		float prevValue = 0;
		boolean phoenixUsed = false; 
		boolean firstSpecialsOver = false;//Dog and MahJong set at start of deck
		
		
		for (Card card: cards){
			if (card.special != null){
				if (firstSpecialsOver)
					break;
				else
					continue;
			}
			else
				firstSpecialsOver = true;
			
			
			
			if (card.value == prevValue)
				continue;
			else if (card.value == prevValue + 1)
				sequence ++;
			else if (card.value == prevValue + 2 && containsPhoenix){
				if (!phoenixUsed){
					sequence += 2;
					phoenixUsed = true;
				}
				else{
					if (prevValue != wish.value)
						containsWish = false;
					sequence = 3;
				}
			}
			else{//(card.value > prevValue + 2), or (card.value == prevValue + 2 and no phoenix)
				containsWish = false;
				phoenixUsed = false;
				sequence = 1;
			}
			
			if (card.value == wish.value)
				containsWish = true;
			prevValue = card.value;
			
			//Check if a valid sequence has been formed
			if (containsWish){
				if (sequence == previousMove.getCards().size())
					return true;
				else if (containsPhoenix && !phoenixUsed && sequence == previousMove.getCards().size() - 1)
					return true;
			}
		}
		
		return false;
	}
	//check if list of cards contains Bomb with wish
	//considers Cards sorted with Card.DefaultCardComparator
	public static boolean containsBombWithWish(List<Card> cards, Move previousMove, Card.Rank wish){
		//TODO: Mass Debug
		boolean hasWish = false;

		float prevValue = 0;
		float number = 1;
		for (Card card: cards){//check if cards contain a wish, and if he has a QuadBomb of this wish
			if (card.rank == wish){
				hasWish = true;
				if (prevValue == card.value)
					number ++;
				if(number == 4)
					return true;
			}
			prevValue = card.value;
		}
		if (!hasWish)
			return false;
		
		if (cards.size() < previousMove.getCards().size())
			return false;
		
		boolean containsWish[] = {false,false,false,false};
		int sequences[] = {1,1,1,1};
		float prevValues[] = {0,0,0,0};
		boolean firstSpecialsOver = false;//Dog and MahJong set at start of deck
		
		for (Card card: cards){
			if (card.special != null){
				if (firstSpecialsOver)
					break;
				else
					continue;
			}
			else
				firstSpecialsOver = true;

			if (card.value == prevValues[card.suit.ordinal()] + 1)
				sequences[card.suit.ordinal()] ++;
			else{//(card.value > prevValue[] + 1
				containsWish[card.suit.ordinal()] = false;
				sequences[card.suit.ordinal()] = 1;
			}
			
			if (card.value == wish.value)
				containsWish[card.suit.ordinal()] = true;
			prevValues[card.suit.ordinal()] = card.value;
			
			//Check if a valid sequence has been formed
			if (containsWish[card.suit.ordinal()]){
				if (sequences[card.suit.ordinal()] == previousMove.getCards().size())
					return true;
			}
		}
		return false;
	}
	
	
	//check if move contains Wish
	public boolean containsWish(Card.Rank wish){
		for (Card card: cards)
			if (card.rank == wish)
				return true;
		return false;
	}
	
	//check if move is a bomb
	public boolean isBomb(){
		if (type == MoveType.QuadBomb || type == MoveType.SequenceBomb)
			return true;
		return false;
	}
	
	//get the cards contained in a Move
	public List<Card> getCards(){
		return new ArrayList<Card>(this.cards);
	}
	
	//get the type of a Move
	public MoveType getType(){
		return this.type;
	}
	
	public static class InvalidMoveException extends Exception{
		private static final long serialVersionUID = 7702693501096465574L;
	}
	
	public static class InvalidMoveCreationException extends Exception{
		private static final long serialVersionUID = -2639987425589094938L;
	}
	
	public static class WrongPhoenixPositionException extends Exception{
		private static final long serialVersionUID = -6441418817146696007L;
	}
	
	
}
