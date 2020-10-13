package com.trurdilin.tichu.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import android.view.View;
import android.widget.Button;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.core.Card;
import com.trurdilin.tichu.core.Game;
import com.trurdilin.tichu.core.Move;
import com.trurdilin.tichu.core.Player;
import com.trurdilin.tichu.core.Round;
import com.trurdilin.tichu.core.Round.Phase;
import com.trurdilin.tichu.core.Round.TichuState;
import com.trurdilin.tichu.net.Message;
import com.trurdilin.tichu.net.Message.CompoundMessage;
import com.trurdilin.tichu.net.Message.XMLNode;
import com.trurdilin.tichu.net.MessageCreator;
import com.trurdilin.tichu.net.MessageReceiver.MessageHandler;
import com.trurdilin.tichu.net.MessageSender;

public class GameMessageHandler implements MessageHandler{
	private final Game game;
	private Round round = null;
	private Phase phase = null;
	private final GameActivity activity;
	private final boolean isHost;
	private final String userName;
	private final List<String> connectedPlayers;
	private final List<String> readyPlayers = new CopyOnWriteArrayList<String>();
	
	public GameMessageHandler(Game game, GameActivity activity, boolean isHost, String userName, List<String> connectedPlayers){
		this.game = game;
		this.activity = activity;
		this.isHost = isHost;
		this.userName = userName;
		this.connectedPlayers = new CopyOnWriteArrayList<String>(connectedPlayers);
		if (isHost)
			readyPlayers.add(userName);
	}
	
	@Override
	public void handleMessage(final CompoundMessage m) {
		activity.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				if (round != null)
					phase = round.getGamePhase();
				
				//Toast.makeText(getBaseContext(), m.message.toString(), Toast.LENGTH_SHORT).show();
				//Log.d(this.getClass().getSimpleName(),"DBG:Received message:\n"+m.message.toString());
				
				if (m.message.getMessageType().equals(Message.TYPE_READY)){
					if (phase != null || readyPlayers.size() == 4)
						return;
					if (isHost){
						readyPlayers.add(m.username);
						
						for (String player: readyPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.playerReady(player, readyPlayers.toArray(new String[readyPlayers.size()])));
						
						if (readyPlayers.size() == 4){
							round = game.getCurrentRound();
							phase = round.getGamePhase();
							for (Player player: round.getPlayerList()){
								if (player.id != connectedPlayers.indexOf(userName)){
									MessageSender.sendMessage(MessageCreator.dealCards(connectedPlayers.get(player.id), phase, player.getHand()));
								}
							}
						}
					}
					else{
						for (XMLNode node: m.message.getSubNodesByName(Message.FIELD_PLAYER)){
							if (!readyPlayers.contains(node.getText())){
								readyPlayers.add(node.getText());
								try {
									game.playerReady(connectedPlayers.indexOf(node.getText()));
								} 
								catch (Exception e) {
									activity.returnToStartActivity(e);
									return;
								}
							}
						}
						if (readyPlayers.size() == 4)
							round = game.getCurrentRound();
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_DEAL_CARDS)){
					if (isHost || !(phase.equals(Phase.GrandTichu) || phase.equals(Phase.GiftGiving)))
						return;
					
					List<Card> cards = m.message.getCards();
					List<Card> existingCards = round.getPlayerList().get(connectedPlayers.indexOf(userName)).getHand();
					if (existingCards != null)
						cards.removeAll(existingCards);
					
					try {
						round.dealCards(phase, cards);
					}
					catch (Exception e) {
						activity.returnToStartActivity(e);
						return;
					}
					
					if (phase.equals(Phase.GrandTichu)){
						((Button)activity.findViewById(R.id.game_button_tichu)).setVisibility(View.VISIBLE);
						((Button)activity.findViewById(R.id.game_button_tichu)).setText(R.string.game_button_grande);
						((Button)activity.findViewById(R.id.game_button_move)).setVisibility(View.VISIBLE);
						((Button)activity.findViewById(R.id.game_button_move)).setText(R.string.game_button_no_grande);
					}
					else if (phase.equals(Phase.GiftGiving)){
						if (round.getTichuState()[connectedPlayers.indexOf(userName)].equals(TichuState.None)){
							((Button)activity.findViewById(R.id.game_button_tichu)).setVisibility(View.VISIBLE);
							((Button)activity.findViewById(R.id.game_button_tichu)).setText(R.string.game_button_tichu);
							((Button)activity.findViewById(R.id.game_button_tichu)).setClickable(true);
						}
						else
							((Button)activity.findViewById(R.id.game_button_tichu)).setVisibility(View.GONE);
						((Button)activity.findViewById(R.id.game_button_move)).setVisibility(View.GONE);
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_RECEIVE_GIFTS)){
					if (isHost || !phase.equals(Phase.MainGame))
						return;
					
					Map<String,Card> giftCards = new HashMap<String,Card>();
					List<Card> gifts = new ArrayList<Card>();
					for (XMLNode node: m.message.getSubNodesByName(Message.FIELD_PLAYER)){
						giftCards.put(node.getText(), node.getSubNodeByName(Message.FIELD_CARD).toCard());
						gifts.add(giftCards.get(node.getText()));
					}
					try {
						round.receiveGifts(gifts);
						activity.showGiftDialog(giftCards);						
					}
					catch (Exception e) {
						activity.returnToStartActivity(e);
						return;
					}
					
					((Button)activity.findViewById(R.id.game_button_move)).setVisibility(View.VISIBLE);
					((Button)activity.findViewById(R.id.game_button_move)).setClickable(true);
				}
				else if (m.message.getMessageType().equals(Message.TYPE_MAHJONG_PLAYER)){
					if (isHost || !phase.equals(Phase.MainGame))
						return;
					
					try {
						round.playerWithMahJong(connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText()));
					}
					catch (Exception e) {
						activity.returnToStartActivity(e);
						return;
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_GRANDE)){
					if (!phase.equals(Phase.GrandTichu))
						return;
					
					if (isHost){
						try {
							round.grandeDecision(connectedPlayers.indexOf(m.username), m.message.getSubNodeByName(Message.FIELD_GRANDE).getTextAsBoolean(), m.message.getSubNodeByName(Message.FIELD_FORCE).getTextAsBoolean());
						} 
						catch (Exception e) {
							return;
						}
						for (String player: readyPlayers){
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.grandeDecision(player, m.username, m.message.getSubNodeByName(Message.FIELD_GRANDE).getTextAsBoolean(), m.message.getSubNodeByName(Message.FIELD_FORCE).getTextAsBoolean()));
						}
						
						phase = round.getGamePhase();
						if (phase.equals(Phase.GiftGiving)){
							for (Player player: round.getPlayerList()){
								if (player.id != connectedPlayers.indexOf(userName)){
									MessageSender.sendMessage(MessageCreator.dealCards(connectedPlayers.get(player.id), phase, player.getHand()));
								}
							}
						}
					}
					else{
						try {
							round.grandeDecision(connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText()), m.message.getSubNodeByName(Message.FIELD_GRANDE).getTextAsBoolean(), m.message.getSubNodeByName(Message.FIELD_FORCE).getTextAsBoolean());
						} 
						catch (Exception e) {
							activity.returnToStartActivity(e);
							return;
						}
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_GIVE_GIFT)){
					if (!phase.equals(Phase.GiftGiving))
						return;
					
					if (isHost){
						try {
							round.giveGift(m.message.getCards().get(0), connectedPlayers.indexOf(m.username), connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_RCV_PLAYER).getText()));
						} 
						catch (Exception e) {
							return;
						}
						for (String player: readyPlayers){
							if (!player.equals(userName))
								if (player.equals(m.username))
									MessageSender.sendMessage(MessageCreator.giveGift(player, m.username, m.message.getSubNodeByName(Message.FIELD_RCV_PLAYER).getText(), m.message.getCards().get(0)));
								else
									MessageSender.sendMessage(MessageCreator.giveGift(player, m.username, m.message.getSubNodeByName(Message.FIELD_RCV_PLAYER).getText(), new Card(null, null, null, true)));
						}
						
						phase = round.getGamePhase();
						if (phase.equals(Phase.MainGame)){
							for (Player player: round.getPlayerList()){
								if (player.id != connectedPlayers.indexOf(userName)){
									Map<Integer,Card> giftCards = player.getGift();
									Map<String,Card> giftCardsModded = new HashMap<String,Card>();
									for (Integer id: giftCards.keySet())
										giftCardsModded.put(connectedPlayers.get(id), giftCards.get(id));
									MessageSender.sendMessage(MessageCreator.receiveGifts(connectedPlayers.get(player.id), giftCardsModded));
									MessageSender.sendMessage(MessageCreator.mahjongPlayer(connectedPlayers.get(player.id), connectedPlayers.get(round.getMahjongPlayer())));
								}
							}
							Map<Integer,Card> giftCards = round.getPlayerList().get(connectedPlayers.indexOf(userName)).getGift();
							Map<String,Card> giftCardsModded = new HashMap<String,Card>();
							for (Integer id: giftCards.keySet())
								giftCardsModded.put(connectedPlayers.get(id), giftCards.get(id));
							activity.showGiftDialog(giftCardsModded);
						}
					}
					else{
						try {
							if (m.message.getSubNodeByName(Message.FIELD_PLAYER).getText().equals(userName))
								((MultipleCardView)activity.findViewById(R.id.game_last_move)).removeCard(m.message.getCards().get(0));
							round.giveGift(m.message.getCards().get(0), connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText()), connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_RCV_PLAYER).getText()));
							
						} 
						catch (Exception e) {
							activity.returnToStartActivity(e);
							return;
						}
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_MOVE)){
					if (!phase.equals(Phase.MainGame))
						return;
					
					List<Card> cards = m.message.getCards();
					
					if (isHost){
						try {
							Move move = round.createMove(connectedPlayers.indexOf(m.username), cards);
							round.performMove(move, connectedPlayers.indexOf(m.username));
						} 
						catch (Exception e) {
							return;
						}
						for (String player: readyPlayers){
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.playMove(player, m.username, cards));
						}
						((MultipleCardView)activity.findViewById(R.id.game_last_move)).setCards(cards);
						phase = round.getGamePhase();
						if (phase.equals(Phase.Scoring)){
							//TODO: endgame
						}
					}
					else{
						try {
							Move move = round.createMove(connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText()), cards);
							round.performMove(move, connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText()));
							
							if (m.message.getSubNodeByName(Message.FIELD_PLAYER).getText().equals(userName)){
								((Button)activity.findViewById(R.id.game_button_tichu)).setVisibility(View.GONE);
								((MultipleCardView)activity.findViewById(R.id.game_player_move_preparation)).removeCards();
							}
						} 
						catch (Exception e) {
							activity.returnToStartActivity(e);
							return;
						}
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_DRAGON)){
					
				}
				else if (m.message.getMessageType().equals(Message.TYPE_MAHJONG)){
					
				}
				else if (m.message.getMessageType().equals(Message.TYPE_TICHU)){
					if (!(phase.equals(Phase.GiftGiving) || phase.equals(Phase.MainGame)))
						return;
					
					if (isHost){
						try {
							round.declareTichu(connectedPlayers.indexOf(m.username), m.message.getSubNodeByName(Message.FIELD_FORCE).getTextAsBoolean());
						} 
						catch (Exception e) {
							return;
						}
						for (String player: readyPlayers){
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.tichuDecision(player, m.username, m.message.getSubNodeByName(Message.FIELD_FORCE).getTextAsBoolean()));
						}
					}
					else{
						try {
							round.declareTichu(connectedPlayers.indexOf(m.message.getSubNodeByName(Message.FIELD_PLAYER).getText()), m.message.getSubNodeByName(Message.FIELD_FORCE).getTextAsBoolean());
							
							if (m.message.getSubNodeByName(Message.FIELD_PLAYER).getText().equals(userName))
								((Button)activity.findViewById(R.id.game_button_tichu)).setVisibility(View.GONE);
						} 
						catch (Exception e) {
							activity.returnToStartActivity(e);
							return;
						}
					}
				}
				else if (m.message.getMessageType().equals(Message.TYPE_EXIT_GAME)){
					if (isHost){
						for (String player: connectedPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.exitGame(player));
						activity.returnToStartActivity(null);
					}
					else{
						activity.returnToStartActivity(null);
					}
				}
				
				activity.updateUi(m);
			}
		});
	}
}