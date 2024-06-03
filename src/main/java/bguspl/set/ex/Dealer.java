package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    // an array that holds the slots of the current set we are about to check 
    public int[] set;

    // queue that holds the players who have sets 
    public Queue<Player> playerSets;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    // true if new set is found otherewise false
    private boolean setFound;

    public Object lockDealer;

    public boolean isTimeStart;



    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.terminate=false;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        set= new int[3];
        for (int i=0; i<this.set.length; i++){
            this.set[i]=-1;
        }
        playerSets= new LinkedList<>();
        setFound= false;
        lockDealer = new Object();
        isTimeStart= false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            player.startPlayerThread();
        }
        while (!shouldFinish()) {
            isTimeStart=false;
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            isTimeStart=true;
            removeAllCardsFromTable();
            removeAllTokens();
            for(int i=0; i<set.length;i++){
                set[i]=-1;
            }
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime= System.currentTimeMillis()+env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            if(setFound){
                isTimeStart= false;
                updateTimerDisplay(true);
                isTimeStart= true;
                reshuffleTime= System.currentTimeMillis()+env.config.turnTimeoutMillis;
                setFound=false;
            }
            else{
                updateTimerDisplay(false);
                isTimeStart= true;
            }
           removeCardsFromTable();
           placeCardsOnTable();
        }
 }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate= true;
        // continue implement the closing of the game 
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }
    
    // helper function for keyPressed in Player class 
    public boolean checkIfLegalSet(Queue<Integer> playerTokens){
        synchronized(lockDealer){
            int [] cards= new int [3];
            int count=0;
            Iterator<Integer> iterator = playerTokens.iterator();
            while (iterator.hasNext()) {
                int slot= iterator.next();
                this.set[count]=slot;
                cards[count]=table.slotToCard[slot];
                count++;
            }
            if(count!=0){
                return env.util.testSet(cards);
            }
            else{
                return false;
            } 
    }
}

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        // the cards shold be removed : one of the players found a set and then we remove it
        while(!playerSets.isEmpty()){
            Player player= playerSets.poll();
            boolean cardIsNull= false;
            Iterator<Integer> it= player.getPlayerTokens().iterator();
            while (it.hasNext()){
                Integer playerslot=it.next();
                if(table.slotToCard[playerslot]==null){
                    cardIsNull=true;
                }
            }
            if(!cardIsNull){
            boolean legalSet= checkIfLegalSet(player.getPlayerTokens());
            if(legalSet){
                for(int i=0;i<this.set.length ;i++){
                    if(this.set[i] != -1 && table.slotToCard[this.set[i]] != null){
                        Iterator<Player> iterator= table.tokenLists[this.set[i]].iterator();
                        while(iterator.hasNext()){
                            Player pl= iterator.next();
                            if(playerSets.contains(pl)){
                                playerSets.remove(pl);
                            }
                        }
                        table.removeCard(this.set[i]);
                    }         
                }  
                player.setinpoint();
                player.point();
                player.resetTokens();
                setFound= true;
                synchronized(playerSets){
                    try{
                        playerSets.notifyAll();
                    }
                    catch(IllegalMonitorStateException e){}
                }
            }
            else{
                player.setinpenalty();  
                player.penalty();
                synchronized(playerSets){
                    try{
                        playerSets.notifyAll();
                    }
                    catch(IllegalMonitorStateException e){}
                }

            }
        }
        } 
        
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        if(deck.size()!=0){
            Collections.shuffle(deck);// shuffeling the deck 
            int i=0;
            while(i<table.slotToCard.length ){
                if(table.slotToCard[i]==null){
                    table.placeCard(deck.remove(0), i); // place in the table randome cards from the deck
                }
                i++;
             }
        }
    
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(lockDealer){
        try {
            lockDealer.wait(1000);
            }
         catch (InterruptedException e) {}
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // Calculate the remaining time until reshuffleTime
        long timeLeft = reshuffleTime - System.currentTimeMillis();
    
        if (reset) {
            // Reset the countdown timer to 60 seconds
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        } else {
            // Update the countdown timer if necessary
            if (timeLeft <= 0) {
                // If timeLeft is negative or zero, set countdown to 0 and display warning
                env.ui.setCountdown(0, true);
            } else {
                // Update the countdown timer with the remaining time
                env.ui.setCountdown(timeLeft, timeLeft <= env.config.turnTimeoutWarningMillis);
        }
    }
}

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for (int slot=0;slot<table.slotToCard.length;slot++){
            Integer card = table.slotToCard[slot];
            if (card != null) {
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        List<Integer> winners = new ArrayList<>(); // an arrray that holds the winners
        int maxScore=Integer.MIN_VALUE;
        for(int i=0; i<players.length; i++){
            if(players[i].score()>maxScore){
                maxScore= players[i].score();
                winners.clear(); // Clear previous winners if any
                winners.add(players[i].id); // Add the current player as the new winner
            }
            else{
                if (players[i].score()==maxScore){
                    winners.add(players[i].id); 
                }
            }
        }
        int[] winnersArray = winners.stream().mapToInt(Integer::intValue).toArray(); // turn the list into array with all the winners id
        env.ui.announceWinner(winnersArray); 
    }

    public void removeAllTokens(){
        env.ui.removeTokens();
        for(int i=0;  i<players.length; i++){
            players[i].resetTokens();
        }
        table.removeAllTokens();
    }
}
