package com.trurdilin.tichu.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.trurdilin.tichu.R;
import com.trurdilin.tichu.core.Card;
import com.trurdilin.tichu.core.Card.Suit;

public class MultipleCardView extends HorizontalScrollView{
	private List<Card> cards = new ArrayList<Card>();
	private Map<TextView, Card> cardMap = new HashMap<TextView, Card>();
	private TextView cardSelectedView = null;
	private final LinearLayout cardContainer;
	private final Context context;
	private CardViewListener listener;
	
	public MultipleCardView(Context context) {
		super(context);
		cardContainer = new LinearLayout(context);
		this.context = context;
		initSettings();
	}
	
	public MultipleCardView(Context context, AttributeSet attrs) {
		super(context, attrs);
		cardContainer = new LinearLayout(context);
		this.context = context;
		initSettings();
	}
	
	public MultipleCardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		cardContainer = new LinearLayout(context);
		this.context = context;
		initSettings();
	}
	
	private void initSettings(){
		cardContainer.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		this.addView(cardContainer);
	}

	public void addCard(Card card){
		cards.add(card);
				
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		@SuppressLint("InflateParams")
		TextView cardView = (TextView)inflater.inflate(R.layout.game_activity_card_entry, null);
		
		cardView.setTag(card.toString());
		cardView.setText(card.toString());
		cardView.setTextColor(getColour(card));
		cardView.setOnClickListener(new CardOnClickListener());
		int cardWidth = Double.valueOf(context.getResources().getDimension(R.dimen.card_width)).intValue();
		cardView.setLayoutParams(new FrameLayout.LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT));
		
		cardContainer.addView(cardView);
		
		cardMap.put(cardView, card);
		
		if (cards.size() > 1)
			Collections.sort(cards, new Card.DefaultCardComparator());
	}
	
	public void removeCard(Card card){
		cards.remove(card);
		TextView cardView = (TextView)cardContainer.findViewWithTag(card.toString());
		
		if (cardSelectedView != null && cardSelectedView.equals(cardView))
			cardSelectedView = null;	
		
		cardMap.remove(cardView);
		cardContainer.removeView(cardView);
		
		if (cards.size() > 1)
			Collections.sort(cards, new Card.DefaultCardComparator());
	}
	
	public void removeCards(){
		List<Card> tmpCards = new ArrayList<Card>(cards);
		
		for (Card card: tmpCards){
			removeCard(card);
		}
	}
	
	public void addCards(List<Card> cards){
		for (Card card: cards)
			addCard(card);
	}
	
	public void setCards(List<Card> cards){
		for (Card card: this.cards)
			removeCard(card);
		addCards(cards);
	}
	
	private static int getColour(Card card){
		if (card.suit != null){
			if (card.suit == Suit.BLACK)
				return Color.BLACK;
			else if (card.suit == Suit.RED)
				return Color.RED;
			else if (card.suit == Suit.BLUE)
				return Color.BLUE;
			else if (card.suit == Suit.GREEN)
				return Color.GREEN;
		}
		return Color.BLACK;
	}
	
	public List<Card> getCards(){
		return new ArrayList<Card>(cards);
	}
	
	public void setCardListener(final CardViewListener listener){
		this.listener = listener;
	}
	
	private class CardOnClickListener implements View.OnClickListener{

		@Override
		public void onClick(View v) {
			if (listener != null && v instanceof TextView){
				TextView tv = (TextView) v;
				
				if (cardSelectedView != null){
					if (cardSelectedView.equals(tv)){
						tv.setBackgroundResource(R.drawable.card);
						cardSelectedView = null;
					}
					else{
						cardSelectedView.setBackgroundResource(R.drawable.card);
						cardSelectedView = tv;
						cardSelectedView.setBackgroundResource(R.drawable.card_selected);
					}
				}
				else{
					cardSelectedView = tv;
					cardSelectedView.setBackgroundResource(R.drawable.card_selected);
				}
				
				listener.cardSelected(cardMap.get(tv));
			}
		}
	}

	public Card getSelectedCard(){
		if (cardSelectedView != null)
			return cardMap.get(cardSelectedView);
		return null;
	}
	
	public static interface CardViewListener{
		public void cardSelected(Card card);
	}
}
