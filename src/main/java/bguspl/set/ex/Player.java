package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.Random;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // the dealer of the table 
    public Dealer dealer;

    // each player will hold list of the slots he put the tokens on 
    private BlockingQueue<Integer> playerTokens;

    // each player have a queue of the last 3 actions 
    private BlockingQueue<Integer> playerActions;

    private boolean inpenalty; 

    private boolean inpoint; 

    private int freeze;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.terminate=false;
        this.dealer= dealer;// we add this line 
        this.playerTokens= new ArrayBlockingQueue<>(3);// we add this line
        this.playerActions= new ArrayBlockingQueue<>(3);// we add this line
        this.inpenalty = false; 
        this.inpoint = false; 
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human){
            createArtificialIntelligence();
        }

        while (!terminate) {
            // TODO implement main player loop
            while(!playerActions.isEmpty()){
                if(!inpenalty && !inpoint){
                    Integer slot= playerActions.poll();
                    if(!human){
                          synchronized(aiThread){
                            aiThread.notifyAll();
                        }
                    }
                    if (!(dealer.playerSets.contains(this)) && dealer.isTimeStart && table.slotToCard[slot]!= null){
                        if(table.removeToken(this.id, slot)){
                            removeTokenFromPlayerSlots(slot);
                        }
                        else{
                            addTokentoPlayerSlots(slot);
                        }
                    }
                }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if(!inpenalty && !inpoint){
                    Random random= new Random();
                    int randomSlot= random.nextInt(12);
                    keyPressed(randomSlot);
                    if(playerActions.size()==3){
                        synchronized(aiThread){
                        try {
                            aiThread.wait(500);
                            }
                        catch (InterruptedException e) {}
                        }
                    }
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    public void startPlayerThread() {
        Thread playerThread = new Thread(this);
        playerThread.start();
    }


    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        this.terminate=true;
    }

    public Queue<Integer> getPlayerTokens(){
        return playerTokens;
    }

    public int getId(){
        return id;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
            if(!inpenalty && !inpoint){
                try{
                playerActions.put(slot);
                }
                catch (InterruptedException e) {}
            }
    }
    


    public void removeTokenFromPlayerSlots(int slot){
            Iterator<Integer> iterator = playerTokens.iterator();
            while (iterator.hasNext()) {
                Integer currentElement = iterator.next();
                if (currentElement==slot) {
                    iterator.remove();
                    break; 
                }
            }
        }


    public void addTokentoPlayerSlots(int slot){
            if (playerTokens.size()<3){
                table.placeToken(this.id, slot);
                playerTokens.add(slot);
                table.addPlayerToTokenList(this, slot);
                String s="the player  "+this.id+" tokens are ";
                    Iterator it= playerTokens.iterator();
                    while(it.hasNext()){
                        s= s+" ,"+ it.next();
                    }
                    System.out.println(s);
                if (playerTokens.size()==3){
                    dealer.playerSets.offer(this);
                    synchronized(dealer.lockDealer){
                        try {
                            dealer.lockDealer.notifyAll();
                            }
                         catch (IllegalMonitorStateException e) {}
                    }
                    synchronized(dealer.playerSets){
                        try{
                            while(dealer.playerSets.contains(this)){
                                dealer.playerSets.wait();
                            }
                        }
                        catch(InterruptedException e){}
                    }
                    updateFreeze();
                    inpoint = false;
                    inpenalty = false;
            }
        }
    }
    
    private void updateFreeze(){
        try {
               for (long i = freeze; i > 0; i--) {
                   // Update the countdown display
                      env.ui.setFreeze(this.id, i * 1000); // Update freeze time in milliseconds
                      Thread.sleep(1000); // Sleep for 1 second 
              }
           }
           catch (InterruptedException e) {}
           env.ui.setFreeze(this.id,0);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        // the player need to wait one second
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        freeze= ((int)(env.config.pointFreezeMillis))/1000;
    }
 

    /**
     * Penalize a player and perform other related actions.
     */
   
    public void penalty() {
        // TODO implement
        freeze= ((int)(env.config.penaltyFreezeMillis))/1000;
    }
    

         
    public int score() {
        return score;
    }

    public void resetTokens(){
        playerTokens.clear();
    }


    public void removeTokens(){
        Iterator<Integer> it= playerTokens.iterator();
        while (it.hasNext()) {
           int token= it.next();
           table.removeToken(this.id, token);
           playerTokens.remove(token);
        }
    }

    public void setinpenalty(){
        this.inpenalty = true; 
   }

    public void setinpoint(){
        this.inpoint = true; 
  }


}
