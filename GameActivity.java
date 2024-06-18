package com.example.alexanddan.arraypoker;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Random;
/*adding a comment to test git*/
public class GameActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference mUserData;
    private String uid;
    final int HAND_SIZE = 5;
    private int pBid, cBid, cInitBid, pBal, cBal, winnersPot, pScore, cScore;
    private long winCount, lossCount, foldCount, totalWinnings, totalLosings;
    private ImageButton mRoll, mReroll, mScore, mFold, mBank;
    private GridLayout playerGrid, cpuGrid;
    private TextView pHand, cHand, scoreArea, pBidArea, cBidArea, pBankArea, cBankArea;
    private boolean isFullHouse, isPairsAndThrees, isStraight, cpuAnte;
    private boolean pFirstRoll = true;
    private boolean rerolled, cRevealHand, shouldCpuFold, isWinner, noWinner = false;
    Bank pBank = new Bank("player", 50);
    Bank cBank = new Bank("opponent", 50);
    private int[] playerDiceArray = {0, 0, 0, 0, 0};
    private int[] cpuDiceArray = {0, 0, 0, 0, 0};
    private int[] cpuPlaceHolder = {0, 0, 0, 0, 0};
    private int[] imageArray = {R.drawable.huh,
            R.drawable.dice_1,
            R.drawable.dice_2,
            R.drawable.dice_3,
            R.drawable.dice_4,
            R.drawable.dice_5,
            R.drawable.dice_6};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        //retrieve saved values from other activities
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            pBid = extras.getInt("playerBid");
            cBid = extras.getInt("cpuBid");
            cInitBid = extras.getInt( "cInitBid");
            pBal = extras.getInt("pSavedBal");
            cBal = extras.getInt("cSavedBal");
            cScore = extras.getInt("cSavedScore");
            playerDiceArray = extras.getIntArray("pHand");
            cpuDiceArray = extras.getIntArray("cHand");
            pFirstRoll = extras.getBoolean("savedFirstRoll");
            rerolled = extras.getBoolean("savedRerolled");
            cpuAnte = extras.getBoolean("savedCpuAnte");
            shouldCpuFold = extras.getBoolean("shouldCpuFold");
            pBank.setBalance(pBal);
            cBank.setBalance(cBal);
        } else {
            pBal = pBank.getBalance();
            cBal = cBank.getBalance();
            pBid = 5;
            cBid = 5;
            cInitBid = 5;
        }

        //get user id and initialize the database reference
        mAuth = FirebaseAuth.getInstance();
        uid = mAuth.getCurrentUser().getUid();
        mUserData = database.getReference().child("users");
        getDatabaseValues();

        //initialize dice grids
        playerGrid = (GridLayout) findViewById(R.id.player_grid);
        playerGrid.setRowCount(1);
        playerGrid.setColumnCount(HAND_SIZE);
        cpuGrid = (GridLayout) findViewById(R.id.cpu_grid);
        cpuGrid.setRowCount(1);
        cpuGrid.setColumnCount(HAND_SIZE);

        for (int i = 0; i < HAND_SIZE; i++) {
            playerGrid.addView(new ImageView(this), i);
            cpuGrid.addView(new ImageView(this), i);
        }

        //initialize textviews
        pHand = (TextView) findViewById(R.id.user_hand);
        cHand = (TextView) findViewById(R.id.cpu_hand);
        scoreArea = (TextView) findViewById(R.id.score_area);
        pBidArea = (TextView) findViewById(R.id.player_bid_area);
        cBidArea = (TextView) findViewById(R.id.cpu_bid_area);
        pBankArea = (TextView) findViewById(R.id.player_bank_area);
        cBankArea = (TextView) findViewById(R.id.cpu_bank_area);

        //display the initial hand
        displayImageArray(true);
        displayImageArray(false);
        updateBankAndBidViews();

        //initiate cpu fold if applicable
        if(shouldCpuFold){
            initiateFold(false);
        }

        //Sequence of events when the Reroll button is pressed
        mReroll = (ImageButton) findViewById(R.id.reroll_button);
        mReroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!pFirstRoll && !rerolled) {
                    finish();
                    Intent i = new Intent(GameActivity.this, RerollActivity.class);
                    saveExtras(i);
                    startActivity(i);
                } else {
                    Toast.makeText(GameActivity.this, "You need a new HAND before you REROLL a Die. You may only REROLL once.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //Sequence of events when the New Hand Button is pressed
        mRoll = (ImageButton) findViewById(R.id.roll_button);
        mRoll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pBid >= 5 && (pFirstRoll)) {
                    clearTextViews();
                    resetScores();
                    resetScoreBools();
                    createHand(playerDiceArray);
                    createHand(cpuDiceArray);
                    displayImageArray(true);
                    displayImageArray(false);
                    pFirstRoll = false;
                    pBid = 5;
                    cBid = 5;
                    updateBankAndBidViews();
                    cFindReroll(cpuDiceArray);
                    identifyHand(cpuDiceArray, false);
                    cpuAnte();
                    cpuAnte = true;
                } else {
                    Toast.makeText(GameActivity.this, "You must REROLL, ANTE UP, or SCORE the match before you start a new hand", Toast.LENGTH_SHORT).show();
                }

            }
        });

        //Sequence of events when the Score Button is pressed
        mScore = (ImageButton) findViewById(R.id.score_button);
        mScore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!pFirstRoll && ((pBid == cBid))) {
                    clearTextViews();
                    identifyHand(playerDiceArray, true);
                    resetScoreBools();
                    cRevealHand = true;
                    cFindReroll(cpuDiceArray);
                    identifyHand(cpuDiceArray, false);
                    displayImageArray(false);
                    compareScores();
                    updateBalance();
                    updateBankAndBidViews();
                    updateDatabase();
                    resetGamePlayBools();
                } else {
                    Toast.makeText(GameActivity.this, "You need a new HAND before revealing the score. Your BID must MATCH the CPU, or be greater", Toast.LENGTH_LONG).show();
                }
            }
        });

        //Sequence of events when the Fold Button is pressed
        mFold = (ImageButton) findViewById(R.id.fold_button);
        mFold.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!pFirstRoll) {
                    initiateFold(true);
                }else{
                    Toast.makeText(GameActivity.this, "You can't FOLD without a HAND!", Toast.LENGTH_SHORT).show();
                }
                }
        });

        //Sequence of events when the Bank button is pressed
        mBank = (ImageButton) findViewById(R.id.bank_button);
        mBank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Start new activity
                if(!pFirstRoll) {
                    finish();
                    Intent i = new Intent(GameActivity.this, BankActivity.class);
                    saveExtras(i);
                    startActivity(i);
                }else{
                    Toast.makeText(GameActivity.this, "You need a new HAND before you can bid.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //Saves the values between activities
    private void saveExtras(Intent i){
        int pSavedBal = pBank.getBalance();
        int cSavedBal = cBank.getBalance();
        i.putExtra("pBalance", pSavedBal);
        i.putExtra("cBalance", cSavedBal);
        i.putExtra("pHand", playerDiceArray);
        i.putExtra("cHand", cpuDiceArray);
        i.putExtra("firstRoll", pFirstRoll);
        i.putExtra("rerolled", rerolled);
        i.putExtra("cpuAnte", cpuAnte);
        i.putExtra("playerBid", pBid);
        i.putExtra("cpuBid", cBid);
        i.putExtra("cInitBid", cInitBid);
        i.putExtra("cSavedScore", cScore);
    }

	//writes updates values to the database at the end of each round
    private void updateDatabase() {
        mUserData.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                    mUserData.child(uid).child("winCount").setValue( winCount);
                    mUserData.child(uid).child("lossCount").setValue( lossCount);
                    mUserData.child(uid).child("foldCount").setValue( foldCount);
                    mUserData.child(uid).child("totalAmountWon").setValue( totalWinnings);
                    mUserData.child(uid).child("totalAmountLost").setValue( totalLosings);
                }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("User", databaseError.getMessage());
            }
        });
    }

    //read user database values into instance variables
    public void getDatabaseValues() {

        mUserData.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChild) {
                UserData user = snapshot.getValue(UserData.class);
                winCount = user.getWinCount();
                lossCount = user.getLossCount();
                foldCount = user.getFoldCount();
                totalWinnings = user.getTotalAmountWon();
                totalLosings = user.getTotalAmountLost();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}

        });
    }

    // Pushes a random number between 1 and 6 to simulate a dice roll
    private int randomGen() {
        Random rand = new Random();
        int num = rand.nextInt(6) + 1;
        return num;
    }

    // Fills a dice array with 5 dice
    private int[] createHand(int[] diceArray) {
        for (int i = 0; i < diceArray.length; i++) {
            diceArray[i] = randomGen();
        }
        return diceArray;
    }

    //clears the values of the textviews
    private void clearTextViews() {
        scoreArea.setText("");
        pHand.setText("");
        cHand.setText("");
    }

    //loads the dice into the grid for either player
    private void displayImageArray(boolean isPlayer) {
        for (int i = 0; i < HAND_SIZE; i++) {
            if (isPlayer) {
                ImageView v = (ImageView) playerGrid.getChildAt(i);
                v.setImageResource(imageArray[playerDiceArray[i]]);
            } else if (!isPlayer && cRevealHand) {
                ImageView v = (ImageView) cpuGrid.getChildAt(i);
                v.setImageResource(imageArray[cpuDiceArray[i]]);
            } else {
                ImageView v = (ImageView) cpuGrid.getChildAt(i);
                v.setImageResource(imageArray[cpuPlaceHolder[i]]);
            }
        }
    }

    //allow the player or cpu to fold when they have a bad hand
    private void initiateFold(Boolean isPlayer) {
       // identifyHand(cpuDiceArray, false);
        if(isPlayer) {
            scoreArea.setText("You folded, CPU wins!");
            isWinner = false;
            lossCount += 1;
            foldCount += 1;
        }else{
            scoreArea.setText("CPU folded, you win!");
            isWinner = true;
            winCount += 1;
        }
        updateBalance();
        updateDatabase();
        resetGamePlayBools();
    }

    //updates the bid and bank text views for the player and cpu
    //with the current value
    private void updateBankAndBidViews(){
        pBidArea.setText("Your current Bid: " + pBid);
        pBankArea.setText("Your current Balance: " + pBank.getBalance());
        cBidArea.setText("CPU's current Bid: " + cBid);
        cBankArea.setText("CPU's current Balance: " + cBank.getBalance());
    }

    //calculate the value for the winners pot
    private int getWinnersPot(int pBid, int cBid){
        winnersPot = pBid + cBid;
        return winnersPot;
    }

    //check who won the round and adjust the banks accordingly
    private void updateBalance(){
        int winnings = getWinnersPot(pBid,cBid);
        if(isWinner && !noWinner){
            pBank.deposit(winnings);
            pBank.withdraw(pBid);
            cBank.withdraw(cBid);
            totalWinnings = totalWinnings + (long)pBid;
            pBankArea.setText("Your current Balance: " + pBank.getBalance());
            cBankArea.setText("CPU's current Balance: " + cBank.getBalance());
        }
        if(!isWinner && !noWinner){
            cBank.deposit(winnings);
            pBank.withdraw(pBid);
            cBank.withdraw(cBid);
            totalLosings = totalLosings + (long)pBid;
            pBankArea.setText("Your current Balance: " + pBank.getBalance());
            cBankArea.setText("CPU's current Balance: " + cBank.getBalance());
        }
        if(noWinner){
            pBankArea.setText("Your current Balance: " + pBank.getBalance());
            cBankArea.setText("CPU's current Balance: " + cBank.getBalance());
        }
        checkWinConditions();
    }

    //check for bankrunpcy to determine the end of the game
    private void checkWinConditions(){
        int pCurBal = pBank.getBalance();
        int cCurBal = cBank.getBalance();

        if(pCurBal < 1 && cCurBal > 0){
            finish();
            Intent i = new Intent(GameActivity.this, DefeatActivity.class);
            startActivity(i);
        }
        if(cCurBal < 1 && pCurBal > 0){
            finish();
            Intent i = new Intent(GameActivity.this, VictoryActivity.class);
            startActivity(i);
        }
    }

    // Once a round is complete, the bools must return to default values
    private void resetScoreBools() {
        isFullHouse = true;
        isPairsAndThrees = true;
        isStraight = true;
    }

    //returns the gameplay bools back to default values
    private void resetGamePlayBools(){
        rerolled = false;
        cRevealHand = false;
        pFirstRoll = true;
        cpuAnte = false;
        isWinner = false;
        noWinner = false;
    }

    // Initializes the score values to zero, called at the beginning of the round
    private void resetScores() {
        pScore = 0;
        cScore = 0;
    }

    // Scores are compared in order to determine a winner of the round
    private void compareScores() {
        if (pScore > cScore) {
            scoreArea.setText(scoreArea.getText() + "You won the round! " + " ");
            isWinner = true;
            winCount += 1;
        } else if (pScore < cScore) {
            scoreArea.setText(scoreArea.getText() + "You lost the round! " + " ");
            isWinner = false;
            lossCount += 1;
        } else {
           tieBreaker();
        }
    }

    //determines which player wins when they have the same hand
    private void tieBreaker() {
        int pHighDie = findHighDie(playerDiceArray);
        int cHighDie = findHighDie(cpuDiceArray);
        int pCount = highDieCount(playerDiceArray, pHighDie);
        int cCount = highDieCount(cpuDiceArray, cHighDie);

        if (pHighDie > cHighDie) {
            scoreArea.setText(scoreArea.getText() + "You won the round!");
            isWinner = true;
            winCount += 1;
        } else if (pHighDie < cHighDie) {
            scoreArea.setText(scoreArea.getText() + "You lost the round!");
            isWinner = false;
            lossCount += 1;
        } else {
            //when player and cpu have the same hand
            if(pCount > cCount) {
                scoreArea.setText(scoreArea.getText() + "You won the round!");
                isWinner = true;
                winCount += 1;
            }else if(pCount < cCount){
                scoreArea.setText(scoreArea.getText() + "You lost the round!");
                isWinner = false;
                lossCount += 1;
            }else {
                scoreArea.setText(scoreArea.getText() + "You tied!");
                noWinner = true;
            }
        }
    }

    // When the player and opponent have the same hand, the highest
    // die must be found and compared in order to determine a winner
    private static int findHighDie(int[] diceArray) {
        int highDie = 0;
        int firstVal = 0;
        int secondVal = 0;
        for (int i = 0; i < diceArray.length; i++) {
            if (i == 0) {
                int count = 0;
                for (int j = i + 1; j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                    }
                }
                if (count > 0) {
                    firstVal = diceArray[i];
                }
            }
            if (i == 1) {
                int count = 0;
                for (int j = i + 1; j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                    }
                }
                if (count > 0 && firstVal != 0) {
                    secondVal = diceArray[i];
                }
                if (count > 0 && firstVal == 0) {
                    firstVal = diceArray[i];
                }

            }
            if (i == 2) {
                int count = 0;
                for (int j = i + 1; j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                    }
                }
                if (count > 0 && firstVal != 0) {
                    secondVal = diceArray[i];
                }
                if (count > 0 && firstVal == 0) {
                    firstVal = diceArray[i];
                }
            }
            if (i == 3) {
                int count = 0;
                for (int j = i + 1; j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                    }
                }
                if (count > 0 && firstVal != 0) {
                    secondVal = diceArray[i];
                }
                if (count > 0 && firstVal == 0) {
                    firstVal = diceArray[i];
                }
            }
        }

        if (firstVal > secondVal) {
            highDie = firstVal;
        }
        if (firstVal < secondVal) {
            highDie = secondVal;
        }
        return highDie;
    }

    //for advanced tiebreaking, only called when the player and cpu
    //have the same hand with the same high die, so who ever has more
    //of the high die (say in a full house) will win the match
    private int highDieCount(int [] diceArray, int diceVal){
        int count = 0;
        for(int i = 0; i < diceArray.length; i++){
            if(diceArray[i] == diceVal){
                count++;
            }
        }
        return count;
    }

    // Check the players hand for any valid dice combinations
    private void identifyHand(int[] diceArray, boolean isPlayer) {
        checkFullHouse(diceArray, isPlayer);
        if (!isFullHouse) {
            checkPairsAndThrees(diceArray, isPlayer);
        }
        if (!isPairsAndThrees) {
            checkStraight(diceArray, isPlayer);
        }
    }

    // identify a possible straight (abcde)
    private void checkStraight(int[] diceArray, boolean isPlayer) {
        int one = 0;
        int two = 0;
        int three = 0;
        int four = 0;
        int five = 0;
        int six = 0;
        for (int i = 0; i < diceArray.length; i++) {
            if (diceArray[i] == 1) {
                one++;
            }
            if (diceArray[i] == 2) {
                two++;
            }
            if (diceArray[i] == 3) {
                three++;
            }
            if (diceArray[i] == 4) {
                four++;
            }
            if (diceArray[i] == 5) {
                five++;
            }
            if (diceArray[i] == 6) {
                six++;
            }
        }

        if ((one == 1 && two == 1 && three == 1 && four == 1 && five == 1)
                || (two == 1 && three == 1 && four == 1 && five == 1 && six == 1)) {
            isStraight = true;
            if (isPlayer) {
                pScore = 6;
            } else {
                cScore = 6;
            }
        } else {
            isStraight = false;
        }
    }

    // identifies if the player has a full house (aabbb)
    private void checkFullHouse(int[] diceArray, boolean isPlayer) {
        int iCountZero = 1;
        int iCountOne = 1;
        int iCountTwo = 1;
        int iCountThree = 1;
        int lastIndexZero = 0;
        int lastIndexOne = 0;
        int lastIndexTwo = 0;
        // int lastIndexThree = 0;
        for (int i = 0; i < diceArray.length; i++) {
            if (i == 0) {
                lastIndexZero = diceArray[i];
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        iCountZero++;
                    }
                }
            }
            if (i == 1) {
                lastIndexOne = diceArray[i];
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if ((diceArray[i] == diceArray[j]) && (diceArray[i] != lastIndexZero)) {
                        iCountOne++;
                    }
                }
            }
            if (i == 2) {
                lastIndexTwo = diceArray[i];
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if ((diceArray[i] == diceArray[j])
                            && ((diceArray[i] != lastIndexZero) && (diceArray[i] != lastIndexOne))) {
                        iCountTwo++;
                    }
                }
            }
            if (i == 3) {
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if ((diceArray[i] == diceArray[j]) && (diceArray[i] != lastIndexZero)
                            && (diceArray[i] != lastIndexOne) && (diceArray[i] != lastIndexTwo)) {
                        iCountThree++;
                    }
                }
            }
        }
        if ((iCountZero == 2 && iCountOne == 3) || (iCountZero == 3 && iCountOne == 2)
                || (iCountZero == 2 && iCountTwo == 3) || (iCountZero == 3 && iCountTwo == 2)
                || (iCountZero == 3 && iCountThree == 2)) {
            isFullHouse = true;
            isPairsAndThrees = false;
            isStraight = false;
            if (isPlayer) {
                pScore = 4;
            } else {
                cScore = 4;
            }
        } else {
            isFullHouse = false;
        }
    }

    // Checks if the player has two or more of one number (aabcd or aaabc)
    // If a number occurs more than once in the dice array it is taken.
    // This method walks through the dice array and compares the index i
    // with the index j looking for matches. If there is a match, the number
    // of matches is counted in order to determine whether the player
    // has a pair, three of a kind, four of a kind etc.
    // pairCheck is true if the player has at least one pair in hand.
    // this is necessary to check for multiple pairs (aabbc, etc)
    private void checkPairsAndThrees(int[] diceArray, boolean isPlayer) {
        boolean pairCheck = false;
        int takenOne = 0;
        int takenTwo = 0;
        int takenThree = 0;
        int takenFour = 0;
        for (int i = 0; i < diceArray.length; i++) {
            if (i == 0) {
                int count = 1;
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                        if (count > 1) {
                            takenOne = diceArray[i];
                        }
                    }
                }
                if (count == 5) {
                    if (isPlayer) {
                        pScore = 7;
                    } else {
                        cScore = 7;
                    }
                }
                if (count == 4) {
                    if (isPlayer) {
                        pScore = 5;
                        } else {
                        cScore = 5;
                        }
                }
                if (count == 3) {
                    if (isPlayer) {
                        pScore = 3;
                        } else {
                        cScore = 3;
                        }
                }
                if (count == 2) {
                    pairCheck = true;
                    if (isPlayer) {
                        pScore = 1;
                        } else {
                        cScore = 1;
                        }
                }

            }

            if (i == 1) {
                int count = 1;
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                        if (count > 1) {
                            takenTwo = diceArray[i];
                        }
                    }
                }
                if (takenTwo != takenOne) {
                    if (count == 4) {
                        if (isPlayer) {
                            pScore = 5;
                            } else {
                            cScore = 5;
                            }
                    }
                    if (count == 3) {
                        if (isPlayer) {
                            pScore = 3;
                            } else {
                            cScore = 3;
                            }
                    }
                    if (count == 2) {
                        if (pairCheck) {
                            if (isPlayer) {
                                pScore = 2;
                                } else {
                                cScore = 2;
                                }

                        } else {
                            pairCheck = true;
                            if (isPlayer) {
                                pScore = 1;
                                } else {
                                cScore = 1;
                                }
                        }
                    }
                }
            }
            if (i == 2) {
                int count = 1;
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                        if (count > 1) {
                            takenThree = diceArray[i];
                        }
                    }
                }
                if (takenThree != takenTwo && takenThree != takenOne) {
                    if (count == 3) {
                        if (isPlayer) {
                            pScore = 3;
                            } else {
                            cScore = 3;
                            }
                    }
                    if (count == 2) {
                        if (pairCheck) {
                            if (isPlayer) {
                                pScore = 2;
                                } else {
                                cScore = 2;
                                }

                        } else {
                            pairCheck = true;
                            if (isPlayer) {
                                pScore = 1;
                                } else {
                                cScore = 1;
                                }
                        }
                    }
                }
            }

            if (i == 3) {
                int count = 1;
                for (int j = (i + 1); j < diceArray.length; j++) {
                    if (diceArray[i] == diceArray[j]) {
                        count++;
                        if (count > 1) {
                            takenFour = diceArray[i];
                        }
                    }
                }
                if (takenFour != takenThree && takenFour != takenTwo && takenFour != takenOne) {
                    if (count == 2) {
                        if (pairCheck) {
                            if (isPlayer) {
                                pScore = 2;
                                } else {
                                cScore = 2;
                                }

                        } else {
                            pairCheck = true;
                            if (isPlayer) {
                                pScore = 1;
                                } else {
                                cScore = 1;
                                }
                        }
                    }
                }
            }
        }

        if (takenOne != 0 || takenTwo != 0 || takenThree != 0 || takenFour != 0) {
            isPairsAndThrees = true;
            isStraight = false;
        } else {
            isPairsAndThrees = false;
        }
        pairCheck = false;
    }

    //find an appropriate die for the cpu to reroll
    private void cFindReroll(int[] diceArray) {
        if (isPairsAndThrees || !isStraight) {
            int rerollDie = 0;
            int dieOne = diceArray[0];
            int dieTwo = diceArray[1];
            int dieThree = diceArray[2];
            int dieFour = diceArray[3];
            int dieFive = diceArray[4];

            if (dieOne != dieTwo && dieOne != dieThree && dieOne != dieFour && dieOne != dieFive) {
                rerollDie = dieOne;
            }
            if (dieTwo != dieOne && dieTwo != dieThree && dieTwo != dieFour && dieTwo != dieFive) {
                rerollDie = dieTwo;
            }
            if (dieThree != dieOne && dieThree != dieTwo && dieThree != dieFour && dieThree != dieFive) {
                rerollDie = dieThree;
            }
            if (dieFour != dieOne && dieFour != dieTwo && dieFour != dieThree && dieFour != dieFive) {
                rerollDie = dieFour;
            }
            if (dieFive != dieOne && dieFive != dieTwo && dieFive != dieThree && dieFive != dieFour) {
                rerollDie = dieFive;
            }

            rerollDie(rerollDie, cpuDiceArray);
            displayImageArray(false);
        }
    }

    //rerolls the value of a single die
    private void rerollDie(int diceVal, int [] diceArray){
        for (int i = 0; i < diceArray.length; i++) {
            if (diceArray[i] == diceVal) {
                diceArray[i] = randomGen();
                break;
            }
        }
    }

    //antes for the CPU within certain ranges according to hand quality
    private void cpuAnte(){
        if(isPairsAndThrees){
            Random rand = new Random();
            int cAnte = rand.nextInt(5) + 1;
            cBid = cBid + cAnte;
            cInitBid = cBid;
        }
        if(isFullHouse){
            Random rand = new Random();
            int cAnte = rand.nextInt(5) + 10;
            cBid = cBid + cAnte;
            cInitBid = cBid;
        }
        if(isStraight){
            Random rand = new Random();
            int cAnte = rand.nextInt(5) + 15;
            cBid = cBid + cAnte;
            cInitBid = cBid;
        }
        if(cBid > pBid) {
            cBidArea.setText("CPU's current Bid: " + cBid);
            scoreArea.setText(scoreArea.getText() + "CPU Anted Up!");
        }
    }
}