package com.trurdilin.tichu.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.trurdilin.tichu.core.Card.Rank;
import com.trurdilin.tichu.core.Card.Special;
import com.trurdilin.tichu.core.Card.Suit;


//TODO: only operates on server side
public class Deck {
	private List<Card> available = new ArrayList<Card>();
	
	private Random r;
	boolean dummy = false;
	
	private Deck(long seed,boolean dummy){
		if (!dummy){
			if (seed == 0)
				r = new Random();
			else
				r = new Random(seed);
			for (Suit suit : Suit.values())
	            for (Rank rank : Rank.values())
	            	available.add(new Card(suit, rank, null, false));
	        for (Special special : Special.values())
	        	available.add(new Card(null, null, special, false));
		}
		else{
			dummy = true;
			r = null;
			for (int i=0;i<56;i++)
				available.add(new Card(null,null,null,true));
		}		
	}
	
	// Usage: Deck test = Deck.createWithSeed(seed);
	public static Deck createWithSeed(long seed){
		return new Deck(seed,false);
	}
	
	// Usage: Deck test = Deck.create();
	public static Deck create(){
		return new Deck(0,false);
	}
	
	public static Deck createDummy(){
		return new Deck(0,true);
	}
	
	//Draw random card from Deck
	public Card drawRandomCard() throws OverdrawingException{
		if (available.isEmpty())
			throw new OverdrawingException();
		if (!dummy)
			return available.remove(r.nextInt(available.size()));
		return available.remove(0);
	}
	
	//Draw number random cards from Deck
	public List<Card> drawCards(int number) throws OverdrawingException{
		List<Card> ret = new ArrayList<Card>();
		for (int i=0; i<number; ++i)
			ret.add(drawRandomCard());
		return ret;
	}
	
	
	
	public class OverdrawingException extends Exception{
		private static final long serialVersionUID = -802907646808453027L;
	}
}
