package com.trurdilin.tichu.core;

import java.util.ArrayList;
import java.util.List;

public class Trick {
	
	private MoveType type = null;
	private List<Move> moves = new ArrayList<Move>();
	
	public Trick(Trick that){
		for (Move move:that.moves)
			moves.add(new Move(move));
		type = that.type;
	}
	
	private Trick(){
	}
	
	public static Trick newTrick(Move move) throws InvalidMoveException{
		Trick ret = new Trick();
		ret.addMove(move);
		return ret;
	}
	
	//Last move Played
	public Move lastPlayed(){
		if (moves.isEmpty())
			return null;
		return Move.copy(moves.get(moves.size()-1));
	}
	
	public void addMove(Move move) throws InvalidMoveException{
		//TODO: remove redundant check if too heavy
		type = move.getType();
		if (type == null || type != Move.isValid(move.getCards(), lastPlayed()))
			throw new InvalidMoveException();
		moves.add(move);
	}
	
	//returns all cards played in this trick
	public List<Card> removeTrickCards(){
		List<Card> cards = new ArrayList<Card>();
		for (Move move: moves)
			cards.addAll(move.getCards());
		moves = new ArrayList<Move>();
		return cards;
	}
	
	//get the type of a Trick
	public MoveType getType(){
		return this.type;
	}
	
	public List<Move> getMoves(){
		List<Move> ret = new ArrayList<Move>();
		for (Move move:moves)
			ret.add(0,new Move(move));
		return ret;
	}

	public static class InvalidMoveException extends Exception{

		private static final long serialVersionUID = 773538448098573402L;

	}
}
