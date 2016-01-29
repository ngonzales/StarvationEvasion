package starvationevasion.client.Aegislash.GUI.DraftLayout.hand;

import starvationevasion.client.Aegislash.GUI.DraftLayout.DeckNode;
import starvationevasion.client.Aegislash.GUI.DraftLayout.DraftedCards;
import starvationevasion.client.Aegislash.GUI.DraftLayout.map.Map;
import starvationevasion.client.Aegislash.GUI.GUI;

import javafx.scene.layout.GridPane;
import starvationevasion.common.EnumPolicy;

import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

/**
 * Hand is the starvationevasion.client.Aegislash.GUI element responsible for allowing the user to interact with the current cards in their hand
 * It will display the hards in the user's hand
 * On mouseover, the cards will become enlarged
 * On mouseclick, the player will attempt to play the card
 * Maybe on rightmouseclick, the player can set card variables
 *
 */
public class Hand extends GridPane
{
  Stage primaryStage;
  GUI gui;
  ArrayList<ClientPolicyCard> elements;
  Stack<ClientPolicyCard> usedCards;
  ArrayList<ClientPolicyCard> discardPile;
  ArrayList<ClientPolicyCard> cardsPlayed;
  DraftedCards draftedCards;
  DeckNode deckNode;
  public EnumPolicy[] hand;
  boolean playedSupportCard=false;
  boolean discardedSingleCard=false;
  boolean selectingCard=false;
  int numberOfActionsUsed=0;
  ArrayList<ClientPolicyCard> selectedCards=new ArrayList<>();
  public Hand(GUI gui, Stage stage)
  {
    this.gui = gui;
    primaryStage = gui.getPrimaryStage();
  }

  /**
   * Sets the hand with an Array of EnumPolicies
   * @param hand
   */
  public void setHand(EnumPolicy[] hand)
  {
    this.hand = hand;
    updateHand();
  }
  public EnumPolicy[] getHand()
  {
    return hand;
  }
  public void setSelectingCard(boolean bool){selectingCard=bool;}
  public int getNumberOfActionsUsed(){return numberOfActionsUsed;}
  public ArrayList<ClientPolicyCard> getDraftedCards()
  {
    return cardsPlayed;
  }
  public ArrayList<ClientPolicyCard> getDiscardCards(){return discardPile;}
  private void updateHand()
  {
    elements = new ArrayList<>();
    usedCards=new Stack<>();
    discardPile=new ArrayList<>();
    cardsPlayed=new ArrayList<>();
    for (int i = 0; i <hand.length ; i++)
    {
      ClientPolicyCard clientPolicyCard=new ClientPolicyCard(gui.client.getAssignedRegion(),hand[i],gui);
      clientPolicyCard.setHandIndex(i);
      elements.add(clientPolicyCard);
      add(clientPolicyCard,i,0);
    }
    setListeners();
  }

  private void setListeners()
  {
    for(final ClientPolicyCard card : elements)
    {
      card.setOnMouseEntered(event -> {
        if(!card.getIsFlipped())
        {
          if(!card.getDrafted()&&!card.isDiscarded())
          {
            if(numberOfActionsUsed>=2)
            {
              card.setDraftButton(true);
            }
            if(card.isSupportCard()&&playedSupportCard) card.setDraftButton(true);
            if(discardedSingleCard) card.setDiscardButton(true);
            card.setDetailedCard();
            card.toFront();
          }else
          {
            card.setDetailedDraftedCard();
            card.toFront();
          }
        }
      });
      card.setOnMouseExited(event -> {
        if(!card.getDrafted()&&!card.isDiscarded()&&!card.getIsFlipped())
        {
          card.setIsFlipped(false);
          card.setBasicCard();
        }
        else if(!card.getIsFlipped()) card.setDraftedCard();
      });
      card.setOnMouseClicked(event -> {
        if(selectingCard&&selectedCards.size()<3||card.isSelected())
        {
          selectCard(card);
        }
        else if (!card.getIsFlipped()&&!card.getDrafted()&&!card.isDiscarded())
        {
          for(ClientPolicyCard clientPolicyCard:elements)
          {
            if(!card.equals(clientPolicyCard)) clientPolicyCard.setBasicCard();
            clientPolicyCard.setIsFlipped(false);
            gui.setSelectingProduct(false);
            gui.setSelectingRegion(false);
          }
          card.flipCardOver();
          if(card.needsFood()) gui.setSelectingProduct(true);
          if(card.needsRegion() && Map.currentlySelectedRegion.isPresent());
        }
        else if(!card.getDrafted()&&!card.isDiscarded()) card.setDetailedCard();
      });
      //Checks for when player clicks drafted card
      card.getDraftButton().setOnMouseClicked(event -> {
        draftCard(card);
      });
      card.getDiscardButton().setOnMouseClicked(event -> {
        discardCard(card);
      });

    }
  }

  /**
   * Puts a card in the Draft zone
   * @param card the card you're drafting
   */
  public void draftCard(ClientPolicyCard card)
  {
    if (numberOfActionsUsed < 2)
    {
      if(card.needsFood()&&gui.getDraftLayout().getProductBar().getSelectedElement()!=null||!card.needsFood())
      {
        if((card.needsRegion()&& Map.currentlySelectedRegion.isPresent())||!card.needsRegion())
        {
          numberOfActionsUsed++;
          draftedCards = gui.getDraftLayout().getDraftedCards();
          usedCards.add(card);
          cardsPlayed.add(card);
          card.setDrafted(true);
          card.setIsFlipped(false);
          draftedCards.addCard(card);
          elements.remove(card);
          gui.setSelectingProduct(false);
          gui.getDraftLayout().unselectSelectedProduct();
          if (card.isSupportCard()) playedSupportCard = true;
          if (numberOfActionsUsed == 2) gui.getDraftLayout().getActionButtons().setDisableOnBigDiscardButton(true);
          card.setSelectedFood(gui.getDraftLayout().getProductBar().getSelectedElement());

          if(!gui.client.isAI&&Map.currentlySelectedRegion.isPresent())card.setSelectedRegion(Map.currentlySelectedRegion.get());
        }
      }
    }
  }

  /**
   * Removes card from hand and adds it to the local discard
   * @param card card to be discarded
   */
  public void discardCard(ClientPolicyCard card)
  {
    if (!discardedSingleCard)
    {
      deckNode = gui.getDraftLayout().getDeckNode();
      discardPile.add(card);
      usedCards.add(card);
      card.setDiscarded(true);
      card.setIsFlipped(false);
      deckNode.discardCard(card);
      elements.remove(card);
      discardedSingleCard = true;
    }
  }
  private void selectCard(ClientPolicyCard clientPolicyCard)
  {
    clientPolicyCard.selectCard();
    if(clientPolicyCard.isSelected())
    {
      selectedCards.add(clientPolicyCard);
    } else
    {
      selectedCards.remove(clientPolicyCard);
    }
  }

  /**
   * removes selected cards from the hand
   */
  public void discardSelected()
  {
    deckNode = gui.getDraftLayout().getDeckNode();
    if(selectedCards.size()>0)
    {
      numberOfActionsUsed++;
    }
    for(ClientPolicyCard card:selectedCards)
    {
      discardPile.add(card);
      card.setDiscarded(true);
      card.setIsFlipped(false);
      deckNode.discardCard(card);
      elements.remove(card);
    }
    selectedCards.clear();
  }

  /**
   * Undo's last move. Used by ActionButton
   */
  public void undo()
  {
    if(usedCards!=null&&usedCards.size()!=0)
    {
      ClientPolicyCard undoCard=usedCards.pop();
      undoCard.setBasicCard();
      undoCard.setDrafted(false);
      undoCard.setDiscarded(false);
      undoCard.setIsFlipped(false);
      elements.add(undoCard);
      add(undoCard,undoCard.getHandIndex(),0);

      if(undoCard.isSupportCard())
      {
        playedSupportCard=false;
      }
      if(cardsPlayed.contains(undoCard))
      {
        numberOfActionsUsed--;
        gui.getDraftLayout().getActionButtons().setDisableOnBigDiscardButton(false);
        draftedCards.removeCard(undoCard);
        cardsPlayed.remove(undoCard);

        for(ClientPolicyCard card: elements)
        {
          card.setDraftButton(false);
        }
      }
      if(discardPile.contains(undoCard))
      { for(ClientPolicyCard card: elements)
        {
        card.setDiscardButton(false);
        }
        undoCard.setDiscarded(false);
        discardedSingleCard=false;
        discardPile.remove(undoCard);
        deckNode.undoDiscard(undoCard);
      }
    }
  }

  /**
   * Plays a random card
   * Called by AI
   */
  public void playRandomCard()
  {
    ArrayList<ClientPolicyCard> validCards=new ArrayList<>();
    for(ClientPolicyCard card:elements)
    {
      if(isLegal(card))
      {
        card.setRandomValues();
        validCards.add(card);
      }
    }
    if(validCards.size()!=0)
    {
      Collections.shuffle(validCards);
      draftCard(validCards.get(0));
      gui.client.draftCard(validCards.get(0).getPolicyCard());
    }
  }
  private boolean isLegal(ClientPolicyCard card)
  {
    if(card.isSupportCard()&&playedSupportCard) return false;
    if(numberOfActionsUsed>=2) return false;
    return true;
  }


}
