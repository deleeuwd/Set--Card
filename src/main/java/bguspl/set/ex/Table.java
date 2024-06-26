package bguspl.set.ex;

import bguspl.set.Env;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * + none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    // we add an array of lists that will kept the id of the player that placed the token on the card in the given slot
    protected List<Player>[] tokenLists;

    private Object [] objectSlot ;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokenLists= new ArrayList[env.config.tableSize];
        this.objectSlot = new ArrayList [env.config.tableSize];
        for (int i = 0; i < tokenLists.length; i++) {
            tokenLists[i] = new ArrayList<>();
            objectSlot[i] =  new ArrayList<>();
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        synchronized (objectSlot[slot]){
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
            System.out.println("Daniel add card the slot- " +slot+" is locked");
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot);

        // TODO implement
    }
    System.out.println("Daniel add card the slot- " +slot+" is umlocked");
}

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void  removeCard(int slot) {
        synchronized (objectSlot[slot]){
            System.out.println("Daniel remove card the slot- " +slot+" is locked");
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
            Integer card = slotToCard[slot];
            if (card != null) {
                Iterator<Player> it=tokenLists[slot].iterator();
                while(it.hasNext()){
                    Player player= it.next();
                    player.getPlayerTokens().remove(slot);
                }
                slotToCard[slot]= null;
                cardToSlot[card]= null;
                tokenLists[slot].clear();
                env.ui.removeTokens(slot);
                env.ui.removeCard(slot);
        }
    }
    System.out.println("Daniel remove card the slot- " +slot+" is umlocked");
    // TODO implement
    }


    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public synchronized void placeToken(int player, int slot) {
        synchronized (objectSlot[slot]){
            System.out.println("Daniel place token the slot- " +slot+" is locked by "+ player);
            if(slotToCard[slot]!=null){
            env.ui.placeToken(player, slot);
        // TODO implement
    }
    }
    System.out.println("Daniel place token the slot- " +slot+" is umlocked  by "+ player);
}

    public void addPlayerToTokenList(Player player, int slot){
        tokenLists[slot].add(player);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public synchronized boolean removeToken(int player, int slot) {
        synchronized (objectSlot[slot]){
            System.out.println("Daniel remove token the slot- " +slot+" is locked  by "+ player);
            // TODO implement
            Iterator<Player> it= tokenLists[slot].iterator();
            while (it.hasNext()) {
            Player removePlayer=it.next();
            if(removePlayer.id==player){
                tokenLists[slot].remove(removePlayer);
                env.ui.removeToken(player, slot);
                System.out.println("Daniel the slot- " +slot+" is umlocked");
                return true;
              }
            }
            System.out.println("Daniel remove token the slot- " +slot+" is umlocked by "+ player);
            return false;
        }
    }

    public void removeAllTokens(){
        for(int i=0; i<tokenLists.length; i++){
            tokenLists[i].clear();
        }
    }


}


