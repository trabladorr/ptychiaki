package com.trurdilin.tichu.core;

import java.util.List;

import static com.trurdilin.tichu.core.Card.Special.*;

public enum MoveType{
	//Before a move is checked to be valid, cards are required to be sorted, and Phoenix must have value when used in a combo
	//Phoenix played as a single Card over previous Card will be assigned proper value
	SingleCard {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (cards.size() != 1)
				return false;
			Card card = cards.get(0);
			if (card.special == DOG)
				return false;
			
			if (previousCards == null)
				return true;
			else{
				Card previousCard = previousCards.get(0);
				if (previousCard.special == null){
					if (card.special == null){
						if (previousCard.value < card.value)
							return true;
						else
							return false;
					}
					else{
						if (card.special == MAHJONG)
							return false;
						else if (card.special == PHOENIX){
							card.value = previousCard.value + (float)0.5;
							return true;
							}
						else
							return true;  //card is DRAGON
					}
						
				}
				else{
					if (card.special == null){
						if (previousCard.special == MAHJONG)
							return true;
						else if (previousCard.special == PHOENIX && previousCard.value < card.value)
							return true;
						else 
							return false;  //previous was DRAGON
					}
					else{
						if  (previousCard.special == MAHJONG)
							return true;
						else if (previousCard.special == PHOENIX && card.special == DRAGON)
							return true;
						else 
							return false;
					}
						
				}
					
			}
				
		}
	},
	Pair {
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (cards.size() != 2)
				return false;
			
			for (Card card: cards)
				if (card.special != null && card.special != PHOENIX)
					return false;
			
			Card card1 = cards.get(0);
			Card card2 = cards.get(1);
			float currentValue = 0; 			
			
			if (card1.special == PHOENIX)
				currentValue = card2.value;
			else if (card2.special == PHOENIX)
				currentValue = card1.value;
			
			else   //den uparxoun special cards dhladh
				if (card1.rank != card2.rank ) //prepei na exoun to idio rank
					return false;
				
			if (previousCards == null)
				return true;
			else{
				Card previousCard = previousCards.get(0);  //afou paizoume me zevgaria arkei mia apo tis 2 prohgoumenes gia na doume to rank ths
				
				if (previousCard.value < currentValue)  //arkei o elegxos gia mia apo tis 2 kathws exoume elenksei an exoun idio rank
					return true;
				else
					return false;				
			}
				
		}
	},  //<----------------AAAAAAAAAA eimaste mesa se enum toshn wra aaaaaaaaaaaaaaaaaaaaaa:O:O:O
	SequenceOfPairs {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
		
			if ((cards.size()%2 != 0) || (cards.size()<4))
				return false;         //artios arithmos kartwn kai apo 4 kai panw kathws oi 2 thewrountai pair
			
			Card card1;
			Card card2;	
			float previousPairRank = 0;
			float currentRank = 0;

			for (Card card: cards)
				if (card.special != null && card.special != PHOENIX)
					return false;
			
			for(int i=0; i<cards.size(); i+=2) { //TO BE CHECKED BOREI NA PAIZEI POZERO JAVA TROPOS KALUTEROS
				//!!! THEWRW PWS H LIST cards einai taksinomhmenh an den einai 
				// here be taksinomhsh afksousa
				
				card1 = cards.get(i);
				card2 = cards.get(i+1);
				
				if (! ((card1.special == PHOENIX) || (card2.special ==PHOENIX))) //an den einai PHOENIX mia apo tis duo
					if (card1.rank != card2.rank) //prepei na exoun to idio rank
						return false;
				else{  
					if (card1.special == PHOENIX)
						currentRank = card2.value;
					else if (card2.special == PHOENIX)
						currentRank = card1.value;
				}
				
				if ((previousPairRank !=0 ) && (currentRank != previousPairRank+1))  // prepei na einai se sunexh rank ta pair
					return false;
				else
					previousPairRank=currentRank;
			}




			if (previousCards == null)
				return true;
			
			else if (previousCards.get(previousCards.size()-1).value < currentRank)
				return true;
			return false;
		}
	},
	Trio {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {

			if (cards.size() != 3)
				return false;
			
			for (Card card: cards)
				if (card.special != null && card.special != PHOENIX)
					return false;

			float currentValue = cards.get(0).value; 			

			if ((currentValue != cards.get(1).value) || (currentValue != cards.get(2).value ))
					return false;


			if (previousCards == null)
				return true;
			
			return currentValue > previousCards.get(0).value;
		}
	},
	FullHouse {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (cards.size() != 5)
				return false;
			
			for (Card card: cards)
				if (card.special != null && card.special != PHOENIX)
					return false;
			
			float currentValue = cards.get(2).value;
			if (!((cards.get(0).value == currentValue && cards.get(1).value == currentValue && cards.get(3).value != currentValue && cards.get(3).value == cards.get(4).value) ||
				(cards.get(3).value == currentValue && cards.get(4).value == currentValue && cards.get(0).value != currentValue && cards.get(0).value == cards.get(1).value)))
				return false;
			
			if (previousCards == null)
				return true;
			
			return currentValue > previousCards.get(2).value;
		}
	},
	Sequence {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (cards.size() < 5)
				return false;
			
			float value = 0;
			for (Card card: cards){
				if (card.special != null && card.special != PHOENIX)
					return false;
				if (value>0 && card.value != value + 1)
					return false;
				value = card.value;
			}
			
			if (previousCards == null)
				return true;
			
			if (previousCards.size() != cards.size())
				return false;
			
			return value > previousCards.get(previousCards.size()-1).value;	
		}
	},
	
	QuadBomb {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (cards.size() != 4)
				return false;
			
			for (Card card: cards)
				if (card.special != null)
					return false;

			float currentValue = cards.get(0).value; 			

			if ((currentValue != cards.get(1).value) || (currentValue != cards.get(2).value ) || (currentValue != cards.get(2).value ))
					return false;

			if (previousCards == null)
				return true;
			
			if (SequenceBomb.valid(previousCards, null))
				return false;
			else if (QuadBomb.valid(previousCards, null))
				return currentValue > previousCards.get(0).value;
			else
				return true;
		}
	},
	
	SequenceBomb {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (cards.size() < 5)
				return false;
			
			float value = 0;
			Card.Suit suit = cards.get(0).suit;
			for (Card card: cards){
				if (card.special != null)
					return false;
				if (value>0 && card.value != value + 1)
					return false;
				if (card.suit != suit)
					return false;
				value = card.value;
			}
			
			if (previousCards == null)
				return true;
			
			if (SequenceBomb.valid(previousCards, null)){
				if (previousCards.size() < cards.size())
					return true;
				return value > previousCards.get(previousCards.size()-1).value;	
			}
			else
				return true;
		}
	},
	Dog {
		@Override
		public boolean valid(List<Card> cards, List<Card> previousCards) {
			if (previousCards != null)
				return false;
			if (cards.size() != 1)
				return false;
			if (cards.get(0).special != Card.Special.DOG)
				return false;
			return true;
		}
	};
	
	public abstract boolean valid(List<Card> cards, List<Card> previousCards);
}
