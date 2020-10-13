package com.trurdilin.tichu.core;

import com.trurdilin.tichu.core.Deck.OverdrawingException;
import com.trurdilin.tichu.core.Round.MaliciousMoveException;

public class Game {

	private int winningScore;
	private int teamScore[] = {0,0};
	private Round currentRound;
	private boolean playerReady[] = {false,false,false,false};
	private boolean isServer;
	private int activePlayerId;
	private int startingPlayerId = -1;
	private boolean gameOver = false;
	private boolean awaitingScores = false;
	
	private Game(int winningScore, boolean isServer,int activePlayerId){
		this.winningScore = winningScore;
		this.isServer = isServer;
		this.activePlayerId = activePlayerId;
	}
	
	public static Game create(int winningScore, boolean isServer, int activePlayerId){
		return new Game(winningScore,isServer,activePlayerId);
	}
	
	//public methods to alter game state
	
	//called by each player to signify he is ready for the round to start
	public void playerReady(int playerId) throws Round.MaliciousMoveException, OverdrawingException{
		if (awaitingScores)
			throw new Round.MaliciousMoveException("playerReady called while still awaiting scores to be updated.");
		if (currentRound != null)
			throw new Round.MaliciousMoveException("playerReady called while Round in progress.");
		if (gameOver)
			throw new Round.MaliciousMoveException("playerReady called when game is over.");
		if (playerId<0 || playerId>0)
			throw new Round.MaliciousMoveException("Invalid playerId provided: "+playerId+".");
		playerReady[playerId] = true;
		for (boolean ready: playerReady)
			if (!ready)
				break;
		startingPlayerId=(startingPlayerId+1)%4;
		currentRound = new Round(this, isServer, activePlayerId, startingPlayerId);
	}
	
	//package-private methods, to be used by Round
	
	//called by Round, to declare round is over
	void roundOver() throws MaliciousMoveException{
		if (awaitingScores)
			throw new Round.MaliciousMoveException("roundOver called while still awaiting scores to be updated.");
		if (gameOver)
			throw new Round.MaliciousMoveException("roundOver called when game is over.");
		for (int i=0;i<4;i++)
			playerReady[i] = false;
		currentRound = null;
		awaitingScores = true;
	}
	
	//called by Round
	void updateScores(int firstTeam, int secondTeam) throws MaliciousMoveException{
		if (!awaitingScores)
			throw new Round.MaliciousMoveException("updateScores called while not awaiting scores to be updated.");
		if (gameOver)
			throw new Round.MaliciousMoveException("updateScores called when game is over.");
		teamScore[0] += firstTeam;
		teamScore[1] += secondTeam;
		if ((firstTeam > secondTeam && firstTeam >= winningScore)||(secondTeam > firstTeam && secondTeam >= winningScore))
			gameOver = true;
		awaitingScores = false;
			
	}
	
	//public get methods
	
	public boolean gameOver(){
		return this.gameOver;
	}
	
	//get Winning team, 0 for first team (players 0,2) and 1 for second team (players 1,3)
	public int getWinningTeam() throws MaliciousMoveException{
		if (awaitingScores)
			throw new Round.MaliciousMoveException("getWinningTeam called while still awaiting scores to be updated.");
		if (!gameOver)
			throw new Round.MaliciousMoveException("getWinningTeam called when game is not over.");
		if (teamScore[0]>teamScore[1])
			return 0;
		else
			return 1;
	}
	
	public int[] getTeamScore(){
		return teamScore.clone();
	}
	
	//access to currentRound, returns null if no round in progress
	public Round getCurrentRound(){
		return this.currentRound;
	}
}
