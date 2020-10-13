package com.trurdilin.tichu.core;

import java.util.ArrayList;
import java.util.List;

import com.trurdilin.tichu.core.Card.Special;
import com.trurdilin.tichu.core.Deck.OverdrawingException;
import com.trurdilin.tichu.core.Player.ImpossibleMoveException;
import com.trurdilin.tichu.core.Round.InvalidGiftException;
import com.trurdilin.tichu.core.Trick.InvalidMoveException;


public class Round {

	private Phase phase;
	private Deck deck = null;
	private Game game;
	private List<Player> players = new ArrayList<Player>();
	private int currentPlayer = -1;
	private int activePlayer;
	private int activePlayers = 4;
	private int passes = 0;
	private boolean isServer;
	private boolean awaitingDragonDecision = false;
	private boolean awaitingMahJongWish = false;
	private Trick currentTrick = null;
	private Card.Rank wish = null;
	private TichuState tichus[] = {TichuState.None,TichuState.None,TichuState.None,TichuState.None};
	private int finishedFirst = -1;
	private int cardsLeft[] = {0,0,0,0};
	private boolean readyPhaseOne[] = {false,false,false,false};
	private boolean cardsDealtPhaseOne = false;
	private boolean cardsDealtPhaseTwo = false;
	private boolean giftsTakenPhaseThree = false;
	private Gifts gifts;
	
	private final Object stateLock = new Object();
	
	public Round(Game game,boolean isServer,int activePlayerId,int startingPlayer) throws OverdrawingException{
		this.game = game;

		this.isServer = isServer;
		this.activePlayer = activePlayerId;
		for (int i=0;i<4;i++){
			if (isServer || i == activePlayer)
				players.add(Player.create(i));
			else
				players.add(Player.createDummy(i));
		}
		
		if (isServer)
			this.deck = Deck.create();
		else
			this.deck = Deck.createDummy();
		gifts = Gifts.create();
		
		//start of first phase
		phaseOne();
	}
	
	//first dealing of cards, option of Grande
	private void phaseOne() throws OverdrawingException{
		synchronized(stateLock){
			phase = Phase.GrandTichu;
			if (isServer){
				for (Player player: players){
					player.drawCards(deck.drawCards(8));
					cardsLeft[player.id]+=8;
				}
				cardsDealtPhaseOne = true;
			}
		}
	}
	
	private void phaseTwo() throws OverdrawingException{
		synchronized(stateLock){
			phase = Phase.GiftGiving;
			if (isServer){
				for (Player player: players){
					player.drawCards(deck.drawCards(6));
					cardsLeft[player.id]+=6;
				}
				cardsDealtPhaseTwo = true;
			}
		}
	}
	
	private void phaseThree(){
		synchronized(stateLock){
			phase = Phase.MainGame;
			if (isServer){
				for (Player player: players){
					player.receiveGifts(null);
					cardsLeft[player.id] += 3;
					if (player.hasMahJong())
						currentPlayer = player.id;
				}
				giftsTakenPhaseThree = true;
			}
		}
	}
	
	private void phaseFour() throws MaliciousMoveException{

		synchronized(stateLock){
			phase = Phase.Scoring;
			if (awaitingDragonDecision)
				throw new MaliciousMoveException("phaseFour called while dragon decision pending.");
			
			game.roundOver();
			
			int teamScores[] = {0,0};
			if (activePlayers == 2){//One-Two
				teamScores[finishedFirst%2] = 200;
				teamScores[(finishedFirst+1)%2] = 200;
			}
			else{
				for (Player player: players)
					if (cardsLeft[player.id] != 0){
						players.get(finishedFirst).stealWon(player);
						players.get((player.id+1)%2).stealHand(player);
						break;
					}
				for (Player player: players)
					teamScores[player.id%2] = player.getWonScore();
			}
				
			for (Player player: players){
				if (tichus[player.id] == TichuState.Tichu){
					if (player.id == finishedFirst)
						teamScores[player.id%2] += 100;
					else
						teamScores[player.id%2] -= 100;
				}
				else if (tichus[player.id] == TichuState.Grande){
					if (player.id == finishedFirst)
						teamScores[player.id%2] += 200;
					else
						teamScores[player.id%2] -= 100;
				}
			}
	
			game.updateScores(teamScores[0], teamScores[1]);
		}
	}
	
	//necessary actions for client core to operate
	
	//Phase One/Two action:
	//Server deals cards to players
	public void dealCards(Phase phase,List<Card> cards) throws OverdrawingException, MaliciousMoveException{

		synchronized(stateLock){
			if (isServer)
				throw new MaliciousMoveException("Core in Server mode, Client mode specific method dealCards called.");
			if ((phase != Phase.GrandTichu && phase != Phase.GiftGiving) || phase.ordinal() != this.phase.ordinal())
				throw new MaliciousMoveException("dealCards called out of proper phase (phase="+phase.name()+")");
			if (phase == Phase.GrandTichu && cards.size() != 8)
				throw new MaliciousMoveException("dealCards called in GrandTichu phase with "+cards.size()+" cards.");
			if (phase == Phase.GiftGiving && cards.size() != 6)
				throw new MaliciousMoveException("dealCards called in GiftGiving phase with "+cards.size()+" cards.");
	
				
			for (Player player: players){
				if (player.id == activePlayer){
					player.drawCards(cards);
					cardsLeft[player.id] += cards.size();
					deck.drawCards(cards.size());
				}
				else{
					player.drawCards(deck.drawCards(cards.size()));
					cardsLeft[player.id] += cards.size();
				}
			}
			if (phase == Phase.GrandTichu)
				cardsDealtPhaseOne = true;
			else if (phase == Phase.GiftGiving)
				cardsDealtPhaseTwo = true;
			}
	}
	
	//Phase Three action:
	//Server gives gift cards to all players
	public void receiveGifts(List<Card> cards) throws MaliciousMoveException{

		synchronized(stateLock){
			if (isServer)
				throw new MaliciousMoveException("Core in Server mode, Client mode specific method receiveGifts called.");
			if (this.phase != Phase.MainGame)
				throw new MaliciousMoveException("receiveGifts called out of proper phase (phase="+this.phase.name()+")");
			for (Player player: players){
				if (player.id == activePlayer){
					player.receiveGifts(cards);
				}
				else
					player.receiveGifts(null);
				cardsLeft[player.id] += 3;
			}
			giftsTakenPhaseThree = true;
		}
	}
	
	//Phase Three action:
	//Server informs us which player has MahJong
	public void playerWithMahJong(int playerId) throws MaliciousMoveException{
		synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (isServer)
				throw new MaliciousMoveException("Core in Server mode, Client mode specific method playerWithMahJong called.");
			if (this.phase != Phase.MainGame)
				throw new MaliciousMoveException("playerWithMahJong called out of proper phase (phase="+this.phase.name()+")");
			currentPlayer = playerId;
			if (playerId == activePlayer && !players.get(playerId).hasMahJong())
				throw new MaliciousMoveException("Server specified wrong player to possess MahJong.");
		}
	}
	
	//player or timeout Actions modifying game state
	
	//Phase One action:
	//each Player or his timeout timer calls this to declare or not Grande, when all have acted, move to Phase Two
	//force boolean should be false, except when a player insists (through proper handling of InvalidTichuException by the GUI)
	// to declare Grande even though his ally has also declare Grande
	public void grandeDecision(int playerId, boolean grande, boolean force) throws MaliciousMoveException, InvalidTichuException, OverdrawingException{

			synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (phase != Phase.GrandTichu)
				throw new MaliciousMoveException("grandeDecision called out of proper phase (phase="+this.phase.name()+")");
			if (readyPhaseOne[playerId])
				throw new MaliciousMoveException("Player "+playerId+" called grandeDecision multiple times.");
			readyPhaseOne[playerId] = true;
			if (grande)
				if (tichus[(playerId+2)%4] != TichuState.Grande && !force)//if a player's companion has declared Grande, he shall not pass
					throw new InvalidTichuException();
				else
					tichus[playerId]=TichuState.Grande;
			
			for (int i=0;i<4;i++)
				if (!readyPhaseOne[playerId])
					return;
			if (!cardsDealtPhaseOne)
				return;
			
			phaseTwo();
	
			}
	}
	
	//Phase Two Action:
	//each Player or his timeout timer calls this to give a Card to another player, when all cards given, move to Phase Three
	public void giveGift(Card card, int playerId, int recvrId) throws MaliciousMoveException, InvalidGiftException{

		synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (recvrId<0 || recvrId>0)
				throw new Round.MaliciousMoveException("Invalid recvrId provided: "+recvrId+".");
			if (phase != Phase.GiftGiving)
				throw new MaliciousMoveException("giveGift called out of proper phase (phase="+this.phase.name()+")");
	
			// if Player.giveCard throws an ImpossibleMoveException, turn it into a MaliciousMoveException
			try{
				if (playerId == activePlayer || recvrId == activePlayer)
					players.get(playerId).giveCard(card, players.get(recvrId));
				else
					players.get(playerId).giveUnknownCard(players.get(recvrId));
			}
			catch (ImpossibleMoveException e){
				throw new MaliciousMoveException("Player.giveCard threw ImpossibleMoveException.");
			}
			cardsLeft[playerId] -= 1;
			gifts.addGift(playerId, recvrId);
			
			if (gifts.giftsOver() && cardsDealtPhaseTwo)
				phaseThree();
		}
	}
		
	//Phase Three Action:
	//each Player or his timeout timer calls this to perform a move (including pass)
	public void performMove(Move move, int playerId) throws InvalidMoveException, ImpossibleStateException, MaliciousMoveException, ImpossibleMoveException{

		synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (phase != Phase.MainGame)
				throw new MaliciousMoveException("performMove called out of proper phase (phase="+this.phase.name()+")");
			if (!giftsTakenPhaseThree)
				throw new MaliciousMoveException("performMove called before gifts were picked up by all Players.");
			if (awaitingDragonDecision)
				throw new MaliciousMoveException("performMove called while awaiting Dragon player to decide where to give it.");
			if (awaitingMahJongWish)
				throw new MaliciousMoveException("performMove called while awaiting MahJong player to decide his wish.");
			
			
			
			if (currentPlayer == playerId){
				if (wish != null && players.get(currentPlayer).canPlayWish(wish,currentTrick.lastPlayed()) && !move.isBomb() && !move.containsWish(wish))
					throw new MaliciousMoveException("performMove called for a move not containing active wish, while player can fulfill it.");
				
				if (currentTrick == null){//Start a trick
					currentTrick = Trick.newTrick(move);	
					if (normalMove(move, playerId)){//normalMove returns true if game finished with this Move
						if (!awaitingDragonDecision)
							phaseFour();
						return;
					}
					if (move.getType() == MoveType.Dog){//Dog played ends trick and skips one player spot
						players.get(currentPlayer).winCards(currentTrick.removeTrickCards());
						currentTrick = null;
						currentPlayer = (currentPlayer+1)%4;
					}
					if (move.getType() == MoveType.SingleCard || move.getType() == MoveType.Sequence)
						if (move.containsMahJong()){
							awaitingMahJongWish = true;
							return;
						}
					currentPlayer = nextPlayer();
				}
				else if (move == null){ //Pass
					passes ++;
					currentPlayer = nextPlayer();
					if (passes == activePlayers-1){
						if (currentTrick.lastPlayed().getType() == MoveType.SingleCard && currentTrick.lastPlayed().getCards().get(0).special == Special.DRAGON){
							awaitingDragonDecision = true;
							return;
						}
						players.get(currentPlayer).winCards(currentTrick.removeTrickCards());
						currentTrick = null;
						passes = 0;
					}
				}
				else{//Normal move
					currentTrick.addMove(move);
					if (normalMove(move, playerId)){
						if (!awaitingDragonDecision)
							phaseFour();
					}
				}
				return;
			}
			
			if (currentPlayer != playerId){
				if (move.isBomb()){//a player plays a Bomb out of turn
					if (currentTrick == null)
						throw new MaliciousMoveException("Bomb played out of turn with no trick open on table.");
					currentTrick.addMove(move);
					if (normalMove(move, playerId)){
						if (!awaitingDragonDecision)
							phaseFour();
						return;
					}
					currentPlayer = playerId;
					currentPlayer = nextPlayer();
					passes = 0;
				}
				else//a players plays a move different than a bomb out of turn
					throw new MaliciousMoveException("Player "+playerId+" played a non-bomb out of turn.");
			}
		}
	}

	//Phase Three Action:
	//Dragon Player or his timeout timer calls this to declare who gets the Dragon won trick
	public void dragonDecision(int playerId,int recvrId) throws MaliciousMoveException, ImpossibleStateException{

		synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (recvrId<0 || recvrId>0)
				throw new Round.MaliciousMoveException("Invalid recvrId provided: "+recvrId+".");
			if (!awaitingDragonDecision)
				throw new MaliciousMoveException("dragonDecision called when not waiting for a dragon decision");
			if (playerId != currentPlayer)
				throw new MaliciousMoveException("dragonDecision called by player "+playerId+", when it's "+currentPlayer+"'s decision.");	
			if (playerId == recvrId || (playerId+2)%4 == recvrId)
				throw new MaliciousMoveException("dragonDecision called to give it to the wrong team.");
			players.get(playerId).winCards(currentTrick.removeTrickCards());
			currentTrick = null;
			passes = 0;
			awaitingDragonDecision = false;
			if (currentPlayer == nextPlayer() || checkFinished())
				phaseFour();
		}
	}
	

	//Phase Three Action:
	//Player or his timeout timer calls this to declare a MahJong wish (null for none)
	public void mahJongWish(int playerId,Card.Rank playerWish) throws MaliciousMoveException, ImpossibleStateException{

		synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (!awaitingMahJongWish)
				throw new MaliciousMoveException("mahJongWish called when not waiting for a MahJong wish");
			if (playerId != currentPlayer)
				throw new MaliciousMoveException("mahJongWish called by player "+playerId+", when it's "+currentPlayer+"'s wish.");
			wish = playerWish;
			currentPlayer = nextPlayer();
			awaitingMahJongWish = false;
		}
	}
	

	//Phase Two/Three Action:
	//Player calls this to declare Tichu
	//force boolean should be false, except when a player insists (through proper handling of InvalidTichuException by the GUI)
	// to declare Tichu even though his ally has also declare Tichu/Grande
	public void declareTichu(int playerId, boolean force) throws MaliciousMoveException, InvalidTichuException{

		synchronized(stateLock){
			if (playerId<0 || playerId>0)
				throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
			if (phase != Phase.GiftGiving && phase != Phase.MainGame)
				throw new MaliciousMoveException("declareTichu called out of proper phase (phase="+this.phase.name()+")");
			if (phase == Phase.MainGame && cardsLeft[playerId] != 14)
				throw new MaliciousMoveException("Player "+playerId+" declared Tichu with "+cardsLeft[playerId]+" cards.");
			if (tichus[playerId] != TichuState.None)
				throw new MaliciousMoveException("Player "+playerId+" declared Tichu with TichuState="+tichus[playerId].name()+".");
			if (tichus[(playerId+2)%4] != TichuState.None && !force)
				throw new InvalidTichuException();
			tichus[playerId] = TichuState.Tichu;
		}
	}
	
	//Private helper methods
	
	//Get next player to play
	private int nextPlayer() throws ImpossibleStateException{

		synchronized(stateLock){
			int next = currentPlayer;
			for (int i=0;i<3;i++){
				next = (next+1)%4;
				if (!players.get(next).getHand().isEmpty())
					return next;
			}
			throw new ImpossibleStateException();	
		}
	}
	
	//Every move contains these actions, returns true if game ended with this move
	private boolean normalMove(Move move,int playerId) throws ImpossibleStateException, ImpossibleMoveException{

		synchronized(stateLock){
			if (wish != null && move != null && move.containsWish(wish))//wish over
				wish = null;
			
			players.get(playerId).playMove(move);
			cardsLeft[playerId] -= move.getCards().size();
			
			//Game Ended
			if (currentPlayer == nextPlayer() || checkFinished()){//if game has ended, return true
				if (currentTrick.lastPlayed().getType() == MoveType.SingleCard && currentTrick.lastPlayed().getCards().get(0).special == Special.DRAGON)//if Dragon stack ends game
					awaitingDragonDecision = true;
				else
					players.get(currentPlayer).winCards(currentTrick.removeTrickCards());
				return true;
			}
			passes = 0;
			return false;
		}
	}
	
	//finds player who finished first, returns true if game ended with one-two combo
	private boolean checkFinished(){

		synchronized(stateLock){
			if (cardsLeft[currentPlayer] == 0){
				if (finishedFirst == -1){
					finishedFirst = currentPlayer;
				}
				activePlayers --;
				//One-Two
				if (cardsLeft[(currentPlayer+2)%4] == 0 && cardsLeft[(currentPlayer+1)%4] != 0 && cardsLeft[(currentPlayer+3)%4] != 0 ){
					return true;
				}
			}
			return false;
		}
	}
	
	//Method to find if a potential move is valid, based on previous trick moves, MahJong Wish, and current turn
	//return the Move if it is fully valid, null otherwise 
	public Move createMove(int playerId, List<Card> cards){

		synchronized(stateLock){
			if (phase != Phase.MainGame)
				return null;
			Move move;
			try{
				if (currentTrick == null)
					move = Move.create(cards,null);
				else
					move = Move.create(cards, currentTrick.lastPlayed());
			}
			catch (com.trurdilin.tichu.core.Move.InvalidMoveException e){
				return null;
			}
			if (playerId == currentPlayer || move.isBomb()){
				if (wish != null)
					if (players.get(currentPlayer).canPlayWish(wish,currentTrick.lastPlayed()) && !move.isBomb() && !move.containsWish(wish))
						return null;
				return move;
			}
			else
				return null;
		}
	}
	
	//Get Methods to view round state
	
	public boolean[] getGiftState(int playerId){
		synchronized(stateLock){
			return gifts.playersGifts(playerId);
		}
	}
	
	public Phase getGamePhase(){
		synchronized(stateLock){
			return phase;
		}
	}
	
	//returns last move played, null if none
	public Move getLastMove(){
		synchronized(stateLock){
			if (currentTrick == null)
				return null;
			return currentTrick.lastPlayed();
		}
	}
	
	public boolean[] getReadyPhaseOne(){
		synchronized(stateLock){
			return readyPhaseOne.clone();
		}
	}
	
	public TichuState[] getTichuState(){
		synchronized(stateLock){
			return tichus.clone();
		}
	}
	
	public int[] getCardsLeft(){
		synchronized(stateLock){
			return cardsLeft.clone();
		}
	}
	
	public boolean getAwaitingDragon(){
	    synchronized(stateLock){   	
	    	return awaitingDragonDecision;
	    }
	}

	public boolean getAwaitingMahjong(){
		synchronized(stateLock){
			return awaitingMahJongWish;
		}
	}

	public int getActivePlayer(){
		synchronized(stateLock){
			return activePlayer;
		}
	}

	public int getCurrentPlayer(){
		synchronized(stateLock){
			return currentPlayer;
		}
	}

	public Card.Rank getWish(){
		synchronized(stateLock){
			return wish;
		}
	}

	public List<Player> getPlayerList(){
		synchronized(stateLock){
			List<Player> ret = new ArrayList<Player>(); 
			for(Player p: players)
				ret.add(0,new Player(p));
			return ret;
		}
	}
	
	public int getMahjongPlayer(){
		synchronized(stateLock){
			for(Player p: players)
				if (p.hasMahJong())
					return p.id;
			return -1;
		}
	}
	//Exceptions
	
	public static class InvalidTichuException extends Exception{
		private static final long serialVersionUID = -827923100170295621L;
	}
	
	public static class MaliciousMoveException extends Exception{
		public MaliciousMoveException(String string) {
			super(string);
		}
		private static final long serialVersionUID = -1976463429181760279L;
	}
	
	public static class InvalidGiftException extends Exception{
		private static final long serialVersionUID = 8433307879765126491L;
	}
	
	public static class ImpossibleStateException extends Exception{
		private static final long serialVersionUID = 1649854223344139980L;
	}
	
	public enum Phase{
		GrandTichu,
		GiftGiving,
		MainGame,
		Scoring
	}

	public enum TichuState{
		None,
		Tichu,
		Grande
	}
}



class Gifts {
	boolean gifts[][] = {{false,false,false,false},{false,false,false,false},{false,false,false,false},{false,false,false,false}};
	
	private Gifts(){
	}
	
	public static Gifts create(){
		return new Gifts();
	}
	
	public void addGift(int giverId, int recvrId) throws InvalidGiftException{
		if (gifts[giverId][recvrId] != false || giverId == recvrId)
			throw new Round.InvalidGiftException();
		gifts[giverId][recvrId] = true;
	}
	
	public boolean giftsOver(){
		for (int i=0;i<4;i++)
			for (int j=0;j<4;j++){
				if (i == j)
					continue;
				if (gifts[i][j] == false)
					return false;
			}
		return true;
	}
	
	public boolean[] playersGifts(int playerId){
		return gifts[playerId].clone();
	}
	
}