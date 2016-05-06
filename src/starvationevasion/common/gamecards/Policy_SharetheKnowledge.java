package starvationevasion.common.gamecards;

import java.util.EnumSet;

import starvationevasion.common.EnumRegion;

/**
 * Title: {@value #TITLE}<br><br>
 * Game Text: {@value #TEXT}<br><br>
 *
 * Votes Required: Automatic<br><br>
 *
 * Model Effects: The fact that such cards as this exist, implies that every player's
 * hands must, in theory, be able to be viewed during any part of the game.
 */

public class Policy_SharetheKnowledge extends GameCard
{

  public static final String TITLE = "Share the Knowledge";

  public static final String TEXT = 
      "You may look at target US Region's hand and " +
      "you may play one card from their hand.  Pay that region " +
      "5 million dollars";
  
  public static final EnumSet<EnumGameState> PLAY_STATES = //when the card can be used
      EnumSet.of(EnumGameState.PLANNING_STATE);

  /**
   * {@inheritDoc}
   */
  @Override
  public String getTitle() {return TITLE;}

  /**
   * {@inheritDoc}
   */
  @Override
  public String getGameText() {return TEXT;}
  
  /**
   * {@inheritDoc}
   */
  @Override
  public int actionPointCost() {return 3;}
  
  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<EnumGameState> getUsableStates()
  {
    return PLAY_STATES;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumRegion[] getValidTargetRegions()
  {
    return EnumRegion.US_REGIONS;
  }
}
