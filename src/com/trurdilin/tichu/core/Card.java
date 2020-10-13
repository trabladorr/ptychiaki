package com.trurdilin.tichu.core;

import java.util.Comparator;
import java.util.List;

public class Card implements Comparable<Card>{
	
	public enum Rank {
		TWO (2),
		THREE (3),
		FOUR (4),
		FIVE (5),
		SIX (6),
		SEVEN (7),
		EIGHT (8),
		NINE (9),
		TEN (10),
		JACK (11),
		QUEEN (12),
		KING (13),
		ACE (14);
		
		public final int value;
		
		Rank(int value){
			this.value = value;
		}
	}
	
	public enum Suit {
		BLACK, 	//Clubs
		RED,	//Hearts
		BLUE,	//Diamonds
		GREEN	//Clubs
	}
	
	public enum Special {
		DOG,
		MAHJONG,
		PHOENIX,
		DRAGON,
	}
	
	public final Suit suit;
	public final Rank rank;
	public final Special special;
	public final boolean unknown;
	public float value;


	
	public Card (Suit suit, Rank rank, Special special, boolean unknown) {
		if (unknown){
			this.unknown = true;
			this.suit = null;
			this.rank = null;
			this.special = null;
			this.value = (float)0.0;

			return;
		}
		
		this.unknown = false;
		if (special == null){
			this.suit = suit;
			this.rank = rank;
			this.special = null;
			this.value = rank.value;
		}
		else{
			this.suit = null;
			this.rank = null;
			this.special = special;
			if (special == Special.MAHJONG)
				this.value = 1;
			else if (special == Special.DRAGON)
				this.value = 15;
			else
				this.value = 0;
		}
	}

	

	@Override
	//For sorting cards in a move
	public int compareTo(Card o){
		float ret = this.value - o.value;
		if (ret < 0)
			return -1;
		else if (ret > 0)
			return 1;
		else
			return 0;
	}

	@Override
	public boolean equals(Object o){
		Card that;
		if (o instanceof Card)
			that = (Card)o;
		else
			return false;
		
		if (this.unknown && that.unknown)
				return true;
		
		if (this.special != null && that.special != null)
				return this.special == that.special;
		
		if (this.suit != null && that.suit != null)
				return this.suit == that.suit && this.rank == that.rank;
		
		return false;
	}
	
	//For checking if cards in a move are sorted
	public static boolean isSorted(List<Card> cards){
		Card prev = null;
		for (Card card: cards){
			if (prev!=null && prev.compareTo(card)>0)
				return false;
			prev = card;
		}
		return true;
	}
	
	@Override
	public String toString(){
		String ret = "";
		
		if (unknown)
			ret = "\u2605";
		
		if	(special != null){
			if (special == Special.DOG)
				ret = "Dog";
			else if (special == Special.DRAGON)
				ret = "Drg";
			else if (special == Special.MAHJONG)
				ret = "Mhj";
			else if (special == Special.PHOENIX)
				ret = "Phx";
		}
		
		else if (suit != null){

			if (rank.value < 11)
				ret += rank.value;
			else if (rank == Rank.JACK)
				ret = "J";
			else if (rank == Rank.QUEEN)
				ret = "Q";
			else if (rank == Rank.KING)
				ret = "K";
			else if (rank == Rank.ACE)
				ret = "A";
			
			if (suit == Suit.BLACK)
				ret += "\u2660";
			else if (suit == Suit.RED)
				ret += "\u2665";
			else if (suit == Suit.BLUE)
				ret += "\u2666";
			else if (suit == Suit.GREEN)
				ret += "\u2663";
		}
		
		return ret;
		
	}
	
	//For sorting card held in hand
		public static class DefaultCardComparator implements Comparator<Card>{

			@Override
			public int compare(Card first, Card second) {
				if (first.special == null && second.special == null){
					if (first.rank.value == second.rank.value)
						return first.suit.ordinal() - second.suit.ordinal();
					else return first.rank.value - second.rank.value;
				}
				else if (first.special != null && second.special == null){
					if (first.special == Special.DOG || first.special == Special.MAHJONG)
						return -1;
					else
						return 1;
				}
				else if (first.special == null && second.special != null){
					if (first.special == Special.DOG || first.special == Special.MAHJONG)
						return 1;
					else
						return -1;
				}
				else{
					return first.special.ordinal() - second.special.ordinal();
				}
				
			}
			
		}
		
		public static class UnknownComparisonException extends Exception{//TODO: Needed?
			private static final long serialVersionUID = 5660393220963409069L;
		}
}
