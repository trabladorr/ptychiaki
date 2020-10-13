package com.trurdilin.tichu.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.core.Card;
import com.trurdilin.tichu.core.Game;
import com.trurdilin.tichu.core.Move;
import com.trurdilin.tichu.core.Move.InvalidMoveException;
import com.trurdilin.tichu.core.MoveType;
import com.trurdilin.tichu.core.Player;
import com.trurdilin.tichu.core.Round;
import com.trurdilin.tichu.core.Card.Special;
import com.trurdilin.tichu.core.Round.Phase;
import com.trurdilin.tichu.core.Round.TichuState;
import com.trurdilin.tichu.net.Message.CompoundMessage;
import com.trurdilin.tichu.net.MessageCreator;
import com.trurdilin.tichu.net.MessageReceiver;
import com.trurdilin.tichu.net.MessageSender;
import com.trurdilin.tichu.net.NetTools;
import com.trurdilin.tichu.ui.MultipleCardView.CardViewListener;
import com.trurdilin.tichu.ui.WifiStateReceiver.WifiStateHandler;

public class GameActivity extends Activity implements WifiStateHandler{
	
	private MessageReceiver l = null;
	private String userName;
	private String hostToConnect;
	private GameMessageHandler messageHandler;
	boolean isHost; 
	private List<String> connectedPlayers = new CopyOnWriteArrayList<String>();
	
	private Map<String,RelativeLayout> playerContainers = new HashMap<String, RelativeLayout>();
	private Map<String,TextView> playerNameViews = new HashMap<String, TextView>();
	private Map<String,TextView> playerCardViews = new HashMap<String, TextView>();
	
	private MultipleCardView handView;
	private MultipleCardView moveView;
	private MultipleCardView lastMoveView;
	
	private Button tichuButton;
	private Button moveButton;
	
	private CardViewListener handViewListener = new CardViewListener() {
		@Override
		public void cardSelected(Card card) {
			if (round != null && phase != null && phase.equals(Phase.MainGame)){
				handView.removeCard(card);
				if (card.special.equals(Special.PHOENIX)){
					for (Card tmpCard: moveView.getCards())
						if (tmpCard.special == null)
							card.value = tmpCard.value + Double.valueOf(0.5).floatValue();
				}
				moveView.addCard(card);
				if (Move.isValid(moveView.getCards(), round.getLastMove()) != null)
					moveButton.setEnabled(true);
				else
					moveButton.setEnabled(false);
			}
		}
	};
	
	private CardViewListener moveViewListener = new CardViewListener() {
		@Override
		public void cardSelected(Card card) {
			if (round != null && phase != null && phase.equals(Phase.MainGame)){
				moveView.removeCard(card);
				if (card.special.equals(Special.PHOENIX)){
					card = new Card(null, null, Special.PHOENIX, false);
				}
				handView.addCard(card);
				if (Move.isValid(moveView.getCards(), round.getLastMove()) != null) 
					moveButton.setEnabled(true);
				else
					moveButton.setEnabled(false);
			}
		}
	};
	
	private OnClickListener tichuButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			if (round == null || phase == null)
				return;
			if (phase.equals(Phase.GrandTichu) && round.getTichuState()[connectedPlayers.indexOf(userName)].equals(TichuState.None)){
				if (isHost){
					try {
						round.grandeDecision(connectedPlayers.indexOf(userName), true, true);
						for (String player: connectedPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.grandeDecision(player, userName, true, true));
					} 
					catch (Exception e) {
						returnToStartActivity(e);
						return;
					}
				}
				else
					MessageSender.sendMessage(MessageCreator.grandeDecision(hostToConnect, userName, true, true));
				tichuButton.setClickable(false);
				moveButton.setClickable(false);
			}
			else if ((phase.equals(Phase.GiftGiving) || phase.equals(Phase.MainGame)) && round.getTichuState()[connectedPlayers.indexOf(userName)].equals(TichuState.None)){
				if (isHost){
					try {
						round.declareTichu(connectedPlayers.indexOf(userName), true);
						for (String player: connectedPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.tichuDecision(player, userName, true));
					} 
					catch (Exception e) {
						returnToStartActivity(e);
						return;
					}
				}
				else
					MessageSender.sendMessage(MessageCreator.tichuDecision(hostToConnect, userName, true));
				tichuButton.setClickable(false);
			}
		}
	};
	
	private OnClickListener moveButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			if (round == null || phase == null)
				return;
			if (phase.equals(Phase.GrandTichu) && round.getTichuState()[connectedPlayers.indexOf(userName)].equals(TichuState.None)){
				if (isHost){
					try {
						round.grandeDecision(connectedPlayers.indexOf(userName), false, true);
						for (String player: connectedPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.grandeDecision(player, userName, false, true));
					} 
					catch (Exception e) {
						returnToStartActivity(e);
						return;
					}
				}
				else
					MessageSender.sendMessage(MessageCreator.grandeDecision(hostToConnect, userName, false, true));
				tichuButton.setClickable(false);
				moveButton.setClickable(false);
			}
			else if (phase.equals(Phase.MainGame) && Move.isValid(moveView.getCards(), round.getLastMove()) != null ){
				Move move;
				try {
					move = Move.create(moveView.getCards(), round.getLastMove());
				} 
				catch (InvalidMoveException e1) {
					return;
				}
				if (!(round.getCurrentPlayer() == connectedPlayers.indexOf(userName) || move.getType().equals(MoveType.QuadBomb) || move.getType().equals(MoveType.SequenceBomb)))
					return;
					
				if (isHost){
					try {
						round.performMove(move, connectedPlayers.indexOf(userName));
						for (String player: connectedPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.playMove(player, userName, moveView.getCards()));
					} 
					catch (Exception e) {
						returnToStartActivity(e);
						return;
					}
					
					moveView.removeCards();
					moveButton.setEnabled(false);
				}
				else
					MessageSender.sendMessage(MessageCreator.playMove(hostToConnect, userName, moveView.getCards()));
			}
				
		}
	};
	
	private Game game = null;
	private Round round = null;
	private Phase phase = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.game_activity);
		
		SharedPreferences pref = getSharedPreferences(ConfigActivity.PREF_NAME, 0);
		userName = pref.getString(ConfigActivity.PREF_USER_NAME, "");
		
		Intent startingIntent = getIntent();
		
		isHost = startingIntent.getBooleanExtra(StartActivity.INTENT_MODE_HOST, true);
		CharSequence[] tmp = startingIntent.getCharSequenceArrayExtra(StartActivity.INTENT_PLAYERS);
		for (CharSequence seq : tmp)
			connectedPlayers.add(seq.toString());
		
		handView = (MultipleCardView)findViewById(R.id.game_player_hand);
		handView.setCardListener(handViewListener);
		moveView = (MultipleCardView)findViewById(R.id.game_player_move_preparation);
		moveView.setCardListener(moveViewListener);
		lastMoveView = (MultipleCardView)findViewById(R.id.game_last_move);
		lastMoveView.setClickable(false);
		
		tichuButton = (Button)findViewById(R.id.game_button_tichu);
		tichuButton.setOnClickListener(tichuButtonListener);
		moveButton = (Button)findViewById(R.id.game_button_move);
		moveButton.setOnClickListener(moveButtonListener);		
		
		findViewById(R.id.game_player_container).setBackgroundColor(getResources().getColor(R.color.game_background));
		
		if (!isHost)
			hostToConnect = connectedPlayers.get(0);
		
		int index = connectedPlayers.indexOf(userName);
		for (int i=index+1; i<index+4; i++){
			playerContainers.put(connectedPlayers.get(i%4), (RelativeLayout)findViewById(getResources().getIdentifier("game_player_container_"+(i-index), "id", getPackageName())));
			playerNameViews.put(connectedPlayers.get(i%4), (TextView)findViewById(getResources().getIdentifier("game_player_"+(i-index)+"_name", "id", getPackageName())));
			playerCardViews.put(connectedPlayers.get(i%4), (TextView)findViewById(getResources().getIdentifier("game_player_"+(i-index)+"_cards", "id", getPackageName())));
		}
		playerContainers.put(userName, (RelativeLayout)findViewById(R.id.game_my_container));
		
		for (final String name: playerContainers.keySet()){
			if (name.equals(userName))
				continue;
			playerContainers.get(name).setClickable(true);
			playerContainers.get(name).setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View arg0) {
					if (round == null || phase == null || phase.equals(Phase.GiftGiving))
						return;
					
					Card card = handView.getSelectedCard();
					if (card == null)
						return;
					
					if (isHost){
						for (String player: connectedPlayers)
							if (!player.equals(userName))
								MessageSender.sendMessage(MessageCreator.giveGift(player, userName, name, new Card(null, null, null, true)));
						try {
							round.giveGift(card, connectedPlayers.indexOf(userName), connectedPlayers.indexOf(name));
						} 
						catch (Exception e) {
							returnToStartActivity(e);
							return;
						}
						updateUi(MessageCreator.giveGift(null, null, null, new Card(null, null, null, true)));
					}
					else{
						MessageSender.sendMessage(MessageCreator.giveGift(hostToConnect, userName, name, card));
					}
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.empty , menu);
		return true;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (!NetTools.wifiConnected(this)){
			returnToStartActivity(new Exception("Wifi Disabled"));
			return;
		}
		
		
		WifiStateReceiver.register(this, this);
		
		if (game == null){
			game = Game.create(1, isHost, connectedPlayers.indexOf(userName));
			messageHandler = new GameMessageHandler(game, this, isHost, userName, connectedPlayers);
			l = MessageReceiver.bindInstance(messageHandler);
			if (isHost){
				try {
					game.playerReady(connectedPlayers.indexOf(userName));
				} 
				catch (Exception e) {
					returnToStartActivity(e);
					return;
				}
			}
			else
				MessageSender.sendMessage(MessageCreator.playerReady(hostToConnect, null));
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		WifiStateReceiver.unregister();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();


		l.stopListen();
		MessageSender.stopSending();
	}
	
	@Override
	public void onBackPressed() {
		showExitDialog();
	}

	public void returnToStartActivity(Exception e){
		finish();
		if (e != null)
			Toast.makeText(GameActivity.this, getString(R.string.game_toast_game_crashed)+":"+Log.getStackTraceString(e), Toast.LENGTH_SHORT).show();
		else
			Toast.makeText(GameActivity.this, getString(R.string.game_toast_game_cancelled), Toast.LENGTH_SHORT).show();
		Intent startIntent = new Intent(getApplicationContext(), StartActivity.class);
		startIntent.setAction("android.intent.action.MAIN");
		startActivity(startIntent);
	}
	
	public void updateUi(CompoundMessage m){
		round = game.getCurrentRound();
		if (round == null)
			return;
		phase = round.getGamePhase();
		
		for (Player player: round.getPlayerList()){
			if (player.id == connectedPlayers.indexOf(userName))
				continue;
			
			playerContainers.get(connectedPlayers.get(player.id)).setBackgroundColor(getResources().getColor(R.color.game_background));
		}
		
		if (phase.equals(Phase.MainGame))
			playerContainers.get(connectedPlayers.get(round.getCurrentPlayer())).setBackgroundColor(getResources().getColor(R.color.game_background_active));
		
		for (String player: playerNameViews.keySet()){
			String playerText = player;
			TichuState playerTichuState = round.getTichuState()[connectedPlayers.indexOf(player)];
			if (!playerTichuState.equals(TichuState.None))
				playerText += "(" + playerTichuState.name() + ")";
			playerNameViews.get(player).setText(playerText);
			
			int playerCards = round.getPlayerList().get(connectedPlayers.indexOf(player)).getHand().size();
			int playerCardsWon = round.getPlayerList().get(connectedPlayers.indexOf(player)).getWon().size();
			playerCardViews.get(player).setText(playerCards+" Card"+(playerCards == 1?"s":"")+", "+playerCardsWon+" Won");
		}
				
	}

	@Override
	public void wifiEnabled() {
		
	}

	@Override
	public void wifiDisabled() {
		returnToStartActivity(new Exception("Wifi Disabled"));
	}
	
	public void showGiftDialog(Map<String,Card> giftCards){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(getString(R.string.alert_get_gifts_name));
		alert.setMessage(getString(R.string.alert_get_gifts_message));

		TextView textView = new TextView(GameActivity.this);
		String text = "";
		for (String player: giftCards.keySet())
			text += player+":"+giftCards.get(player).toString();
		textView.setText(text);
		
		
		alert.setNeutralButton(getString(R.string.alert_get_gifts_button_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});

		alert.show();
	}
	
	public void showExitDialog(){
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(getString(R.string.alert_exit_game_name));
		alert.setMessage(getString(R.string.alert_exit_game_message));

		alert.setPositiveButton(getString(R.string.alert_exit_game_button_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				if (isHost){
					for (String player: connectedPlayers)
						if (!player.equals(userName))
							MessageSender.sendMessage(MessageCreator.exitGame(player));
					returnToStartActivity(null);
				}
				else{
					MessageSender.sendMessage(MessageCreator.exitGame(hostToConnect));
				}
			}
		});

		alert.setNegativeButton(getString(R.string.alert_exit_game_button_exit), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});

		alert.show();
	}

}
