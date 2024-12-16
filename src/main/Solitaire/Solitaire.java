import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Stack;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Solitaire
{
    public class Card{
        String value;
        String suit;
        boolean hidden;

        Card(String value, String suit, boolean hidden){
            this.value = value;
            this.suit = suit;
            this.hidden = hidden;
        }

        public String toString(){
            return hidden ? "Hidden" : value + "-" + suit;
        }

        public String getImgPath(){

            return hidden ? "/cards/BACK.png" : "./cards/" + toString() + ".png";
        }

        // get the value of each card
        public int getValueAsNumber() {
            if (value.equals("A")) return 1;
            if (value.equals("J")) return 11;
            if (value.equals("Q")) return 12;
            if (value.equals("K")) return 13;
            return Integer.parseInt(value);
        }

        // check the color of the card
        public boolean isRed() {
            return suit.equals("D") || suit.equals("H");
        }

        public boolean isBlack() {
            return suit.equals("C") || suit.equals("S");
        }

        // check if the card is hidden
        public boolean isHidden() {
            return hidden;
        }

        public void reveal() {
            this.hidden = false; // Reveal the card
        }

    }

    // the deck
    ArrayList<Card> deck;

    // for shuffling the deck
    Random random = new Random();

    // 7 separate player files
    ArrayList<Card> pile1;
    ArrayList<Card> pile2;
    ArrayList<Card> pile3;
    ArrayList<Card> pile4;
    ArrayList<Card> pile5;
    ArrayList<Card> pile6;
    ArrayList<Card> pile7;
    ArrayList<ArrayList<Card>> piles;

    // the remaining deck
    ArrayList<Card> remainingDeck;

    // window
    int boardWidth = 1200;
    int boardHeight = 1000;

    // card: ratio should be 1: 1.4
    int cardWidth = 80;
    int cardHeight = (int)(cardWidth * 1.4);

    // layout of the card
    int left = 80;
    int top = 80;
    int down = 240;
    int pileGap = 20;
    int cardGap = 25;

    // Drag-and-drop state
    private ArrayList<Card> draggingCards = null;
    // index
    private Point dragStartPoint = null;
    private int dragSourcePileIndex = -1;
    private boolean isDragging = false;
    private Point mouseOffset = new Point();

    // foundations
    ArrayList<Card> foundationDiamonds;
    ArrayList<Card> foundationClubs;
    ArrayList<Card> foundationHearts;
    ArrayList<Card> foundationSpades;
    ArrayList<ArrayList<Card>> foundations;

    // custom features
    private boolean isKingConstraintActive = false;

    // timer
    private Timer timer;
    private int elapsedTime = 0;
    private JLabel timerLabel;
    private int previousTime = 0;

    // undo
    private Stack<GameState> undoStack = new Stack<>();

    // score
    private int score = 0;
    private JLabel scoreLabel;

    // multithreading
    private Thread dragThread;
    private boolean isDraggingActive = false;

    // for recording
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    JFrame frame = new JFrame("Solitaire");
    JPanel boardPanel = new JPanel(){
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Draw remaining deck (draw pile)
            if (!remainingDeck.isEmpty()) {
                Image cardImg = new ImageIcon(getClass().getResource("/cards/BACK.png")).getImage();
                g.drawImage(cardImg, left, top, cardWidth, cardHeight, null);
            } else {
                g.drawRect(left, top, cardWidth, cardHeight);
            }

            // empty foundation slots with suits
            for (int i = 0; i < 4; i++) {
                int x = left + (cardWidth + pileGap) * (i + 3);
                int y = top;
                // base
                g.drawRect(x, y, cardWidth, cardHeight);
                // the shape of these four suits
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int centerX = x + cardWidth / 2;
                int centerY = y + cardHeight / 2;

                switch (i) {
                    // Diamonds (Red)
                    case 0:
                        g2d.setColor(Color.RED);
                        // create a diamond
                        Polygon diamond = new Polygon();
                        diamond.addPoint(centerX, centerY - 15);
                        diamond.addPoint(centerX - 10, centerY);
                        diamond.addPoint(centerX, centerY + 15);
                        diamond.addPoint(centerX + 10, centerY);
                        g2d.fill(diamond);
                        break;
                    // Clubs (Black)
                    case 1:
                        g2d.setColor(Color.BLACK);
                        // three circles with one rectangle
                        g2d.fillOval(centerX - 8, centerY - 20, 16, 16);
                        g2d.fillOval(centerX - 16, centerY - 8, 16, 16);
                        g2d.fillOval(centerX, centerY - 8, 16, 16);
                        g2d.fillRect(centerX - 3, centerY + 5, 6, 15);
                        break;
                    // Hearts (Red)
                    case 2:
                        g2d.setColor(Color.RED);
                        // two circle with one triangle
                        g2d.fillOval(centerX - 12, centerY - 15, 12, 12);
                        g2d.fillOval(centerX, centerY - 15, 12, 12);
                        Polygon heart = new Polygon();
                        heart.addPoint(centerX - 12, centerY - 9);
                        heart.addPoint(centerX + 12, centerY - 9);
                        heart.addPoint(centerX, centerY + 15);
                        g2d.fill(heart);
                        break;
                    // Spades (Black)
                    case 3:
                        g2d.setColor(Color.BLACK);
                        // triangle + oval + rectangle
                        Polygon spade = new Polygon();
                        spade.addPoint(centerX, centerY - 15);
                        spade.addPoint(centerX - 10, centerY + 5);
                        spade.addPoint(centerX + 10, centerY + 5);
                        g2d.fill(spade);
                        g2d.fillOval(centerX - 8, centerY - 5, 16, 16);
                        g2d.fillRect(centerX - 3, centerY + 10, 6, 10);
                        break;
                }

                g2d.dispose();
            }

            // draw empty pile
            for (int pileIndex = 0; pileIndex < piles.size(); pileIndex++) {
                g.drawRect(left + (cardWidth + pileGap) * pileIndex, down, cardWidth, cardHeight);
            }


            // draw each pile
            for (int pileIndex = 0; pileIndex < piles.size(); pileIndex++) {
                ArrayList<Card> pile = piles.get(pileIndex);

                for (int cardIndex = 0; cardIndex < pile.size(); cardIndex++) {
                    Card card = pile.get(cardIndex);
                    Image cardImg = new ImageIcon(getClass().getResource(card.getImgPath())).getImage();

                    // compute the index
                    int x = left + (cardWidth + pileGap) * pileIndex;
                    int y = down + cardGap * cardIndex;
                    // overlapping piles
                    // if selected by a mouse
                    if (isDragging && pileIndex == dragSourcePileIndex && draggingCards.contains(card)) {
                        // become transparent to indicate selected
                        Graphics2D g2d = (Graphics2D) g.create();
                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                        g2d.drawImage(cardImg, x, y, cardWidth, cardHeight, null);
                        g2d.dispose();
                    } else {
                        // normal cards
                        g.drawImage(cardImg, x, y, cardWidth, cardHeight, null);
                    }
                }
            }

            // foundations
            for (int foundationIndex = 0; foundationIndex < foundations.size(); foundationIndex++) {
                ArrayList<Card> foundation = foundations.get(foundationIndex);
                int foundationX = left + (cardWidth + pileGap) * (3 + foundationIndex);
                int foundationY = top;
                // not empty: draw the top card
                if (!foundation.isEmpty()) {
                    Card topCard = foundation.get(foundation.size() - 1);
                    Image cardImg = new ImageIcon(getClass().getResource(topCard.getImgPath())).getImage();
                    g.drawImage(cardImg, foundationX, foundationY, cardWidth, cardHeight, null);
                } else {
                    // empty foundation
                    g.drawRect(foundationX, foundationY, cardWidth, cardHeight);
                }
            }

            // draw dragging cards if any
            if (isDragging && draggingCards != null && dragStartPoint != null) {
                for (int i = 0; i < draggingCards.size(); i++) {
                    Card card = draggingCards.get(i);
                    Image cardImg = new ImageIcon(getClass().getResource(card.getImgPath())).getImage();
                    int x = dragStartPoint.x - mouseOffset.x;
                    int y = dragStartPoint.y - mouseOffset.y + (cardGap * i);
                    g.drawImage(cardImg, x, y, cardWidth, cardHeight, null);
                }
            }
        }
    };

    // storing game state for undo
    private class GameState {
        ArrayList<ArrayList<Card>> pilesState;
        ArrayList<Card> remainingDeckState;

        GameState(ArrayList<ArrayList<Card>> piles, ArrayList<Card> remainingDeck) {
            // Deep copy piles
            pilesState = new ArrayList<>();
            for (ArrayList<Card> pile : piles) {
                pilesState.add(new ArrayList<>(pile));
            }

            // Deep copy remaining deck
            remainingDeckState = new ArrayList<>(remainingDeck);
        }
    }

    Solitaire(){
        Start();

        frame.setVisible(true);
        frame.setSize(boardWidth, boardHeight);
        frame.setLocationRelativeTo(null);
        // override the exit method
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        boardPanel.setLayout(new BorderLayout());
        boardPanel.setBackground(new Color(53, 101, 77));
        frame.add(boardPanel);

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 20));// Two buttons stacked vertically
        leftPanel.setPreferredSize(new Dimension(200, boardHeight));
        leftPanel.setBackground(new Color(73, 137, 107)); // A color matching the theme

        // "Rules" button: checking the game rule
        JButton rulesButton = new JButton("Rules");
        rulesButton.setFont(new Font("Arial", Font.BOLD, 14)); // Adjust font size to fit smaller buttons
        rulesButton.setPreferredSize(new Dimension(120, 50));  // Set size to 50x40
        rulesButton.addActionListener(e -> showRules());
        leftPanel.add(rulesButton);

        // "New Game" button: start a new game
        JButton newGameButton = new JButton("New Game");
        newGameButton.setFont(new Font("Arial", Font.BOLD, 14)); // Adjust font size to fit smaller buttons
        newGameButton.setPreferredSize(new Dimension(120, 50));  // Set size to 50x40
        newGameButton.addActionListener(e -> startNewGame());
        leftPanel.add(newGameButton);

        // "Toggle King Constraint" button: if triggered, only king or cards with king as the bottom can be moved to the empty space
        JButton toggleKingConstraintButton = new JButton("<html><center>King<br>Constraint</center></html>");
        toggleKingConstraintButton.setFont(new Font("Arial", Font.BOLD, 14));
        toggleKingConstraintButton.setPreferredSize(new Dimension(120, 50)); // Adjust size
        toggleKingConstraintButton.addActionListener(e -> toggleKingConstraint());
        leftPanel.add(toggleKingConstraintButton);

        // "Undo" button: undo previous action
        JButton undoButton = new JButton("Undo");
        undoButton.setFont(new Font("Arial", Font.BOLD, 14));
        undoButton.setPreferredSize(new Dimension(120, 50));
        undoButton.addActionListener(e -> undo());
        leftPanel.add(undoButton);

        // timer
        timerLabel = new JLabel("Time: 0s", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setPreferredSize(new Dimension(120, 40)); // Adjust size
        timerLabel.setForeground(Color.WHITE); // Make text visible
        leftPanel.add(timerLabel);

        // score
        scoreLabel = new JLabel("Score: " + score, SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 16));
        scoreLabel.setPreferredSize(new Dimension(120, 40));
        scoreLabel.setForeground(Color.WHITE);
        leftPanel.add(scoreLabel);

        // add the left panel to the frame
        frame.add(leftPanel, BorderLayout.WEST);

        // update the timer label
        timer = new Timer(1000, e -> {
            elapsedTime++;
            timerLabel.setText("Time: " + (elapsedTime + previousTime) + "s");
        });
        timer.start();

        // Add mouse listener for drag-and-drop functionality
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e); // Existing left-click logic
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }
        });

        boardPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
        });

        // Add window listener for handling exit
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleExitBeforeCompletion();
            }
        });
    }

    // right click for automatic selection
    private void handleRightClick(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();

        // Determine which pile was clicked
        for (int selectedIndex = 0; selectedIndex < piles.size(); selectedIndex++) {
            int pileX = left + selectedIndex * (cardWidth + pileGap);
            int pileY = down;

            if (x >= pileX && x <= pileX + cardWidth) {
                ArrayList<Card> selectedCards = getSelected(selectedIndex);
                if (!selectedCards.isEmpty()) {
                    // Automatically select the longest valid sequence
                    draggingCards = selectedCards;
                    dragSourcePileIndex = selectedIndex;
                    dragStartPoint = new Point(x, y);
                    mouseOffset.setLocation(x - pileX, y - (pileY + cardGap * (piles.get(selectedIndex).size() - selectedCards.size())));
                    isDragging = true;
                    boardPanel.repaint();
                }
                break;
            }
        }
    }

    private void handleMousePressed(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();

        // Check if clicking on the remaining deck pile
        if (x >= left && x <= left + cardWidth && y >= top && y <= top + cardHeight) {
            animateCardDealing(false);
            boardPanel.repaint();
            return;
        }

        // Determine which pile was clicked
        // Detect clicked pile
        for (int selectedIndex = 0; selectedIndex < piles.size(); selectedIndex++) {
            int pileX = left + selectedIndex * (cardWidth + pileGap);
            int pileY = down;

            if (x >= pileX && x <= pileX + cardWidth) {
                ArrayList<Card> fromPile = piles.get(selectedIndex);
                if (!fromPile.isEmpty()){
                    int clickedCardIndex = (y - pileY) / cardGap;
                    int topCardY = pileY + (cardGap * (fromPile.size() - 1)); // Y position of the top card

                    if (clickedCardIndex >= 0 && clickedCardIndex < (fromPile.size() - 1)) {
                        ArrayList<Card> potentialDragCards = new ArrayList<>(fromPile.subList(clickedCardIndex, fromPile.size()));

                        // Check if the selected cards are valid for dragging
                        if (isValidDragSequence(potentialDragCards)) {
                            draggingCards = potentialDragCards;
                            dragSourcePileIndex = selectedIndex;
                            dragStartPoint = new Point(x, y);
                            mouseOffset.setLocation(x - pileX, y - (pileY + cardGap * clickedCardIndex));
                            isDragging = true;
                            // start a new thread for it to ensure smooth move
                            startDraggingThread();
                            boardPanel.repaint();
                        }
                        break;
                    }

                    // Check if the top card is clicked
                    if (y >= topCardY && y <= topCardY + cardHeight) {
                        Card topCard = fromPile.get(fromPile.size() - 1);

                        // Dragging logic for the top card
                        draggingCards = new ArrayList<>();
                        draggingCards.add(topCard);
                        dragSourcePileIndex = selectedIndex;
                        dragStartPoint = new Point(x, y);
                        mouseOffset.setLocation(x - pileX, y - topCardY);
                        isDragging = true;
                        // start a new thread for it to ensure smooth move
                        startDraggingThread();
                        boardPanel.repaint();
                        break;
                    }
                }
                break;
            }
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (isDragging && draggingCards != null) {
            dragStartPoint = e.getPoint();
//            boardPanel.repaint();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        // get the index of the mouse
        int x = e.getX();
        int y = e.getY();

        // moving to the foundation
        if (isDragging && draggingCards.size() == 1) {
            stopDraggingThread();
            Card selectedCard = draggingCards.get(0);
            for (int foundationIndex = 0; foundationIndex < foundations.size(); foundationIndex++) {
                ArrayList<Card> foundation = foundations.get(foundationIndex);
                String requiredSuit = switch (foundationIndex) {
                    case 0 -> "D";
                    case 1 -> "C";
                    case 2 -> "H";
                    case 3 -> "S";
                    default -> throw new IllegalStateException("Unexpected foundation index");
                };

                int foundationX = left + (cardWidth + pileGap) * (3 + foundationIndex);
                int foundationY = top;

                if (x >= foundationX && x <= foundationX + cardWidth && y >= foundationY && y <= foundationY + cardHeight) {
                    if (isValidFoundationMove(selectedCard, foundation, requiredSuit)) {
                        ArrayList<Card> fromPile = piles.get(dragSourcePileIndex);
                        foundation.add(selectedCard);
                        fromPile.remove(selectedCard);
                        draggingCards = null;
                        isDragging = false;
                        dragSourcePileIndex = -1;
                        boardPanel.repaint();

                        // update the score
                        score += 10;
                        updateScore();

                        if (isGameCompleted()) {
                            handleGameCompletion();
                        }
                        if (!fromPile.isEmpty()) {
                            fromPile.get(fromPile.size() - 1).reveal();
                        }
                        break;
                    }
                }
            }
        }

        // moving between piles
        if (isDragging) {
            stopDraggingThread();
            // Check for valid drop pile
            for (int pileIndex = 0; pileIndex < piles.size(); pileIndex++) {
                if (pileIndex == dragSourcePileIndex) continue; // Skip the source pile

                int pileX = left + pileIndex * (cardWidth + pileGap);
                int pileY = down + (piles.get(pileIndex).size() - 1) * cardGap;

                if (x >= pileX && x <= pileX + cardWidth && y >= pileY && y <= pileY + cardHeight) {
                    if (isValidMove(piles.get(pileIndex))) {
                        moveCards(dragSourcePileIndex, pileIndex);

                        if (isGameCompleted()) {
                            handleGameCompletion();
                        }

                        break;
                    }
                }
            }

            isDragging = false;
            draggingCards = null;
            dragSourcePileIndex = -1;
            boardPanel.repaint();
        }

    }

    // starting
    public void Start(){
        // initialize the database
        initializeDatabase();
        // record starting time
        startTime = LocalDateTime.now();
        if (hasSavedProgress()) {
            int option = JOptionPane.showConfirmDialog(frame,
                    "A saved game was found. Do you want to continue with the previous game?",
                    "Resume Game",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                loadSavedGame();

                return; // Exit Start method to skip initializing a new game
            }
        }
        createDeck();
        // shuffle the deck
        Collections.shuffle(deck);
//        System.out.println("Shuffled deck");
//        System.out.println(deck);
        // Initialize piles
        dealInitialCards();
//        configurePilesManually();
    }

    public void createDeck(){
        deck = new ArrayList<Card>();
        String[] values = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        String[] suits = {"C", "D", "H", "S"}; // "Clubs", "Diamonds", "Hearts", "Spades"

        for (String value : values) {
            for (String suit : suits) {
                deck.add(new Card(value, suit, false));
            }
        }
//        System.out.println("Deck built successfully");
//        System.out.println(deck);
    }

    public void dealInitialCards(){
        // player piles and remaining cards
        // create separate player piles
        pile1 = new ArrayList<Card>();
        pile2 = new ArrayList<Card>();
        pile3 = new ArrayList<Card>();
        pile4 = new ArrayList<Card>();
        pile5 = new ArrayList<Card>();
        pile6 = new ArrayList<Card>();
        pile7 = new ArrayList<Card>();
        // to manage these piles
        piles = new ArrayList<>();
        piles.add(pile1);
        piles.add(pile2);
        piles.add(pile3);
        piles.add(pile4);
        piles.add(pile5);
        piles.add(pile6);
        piles.add(pile7);
        remainingDeck = new ArrayList<Card>(deck);

        // deal 3 rounds of cards
        for (int round = 0; round < 3; round++) {
            boolean hidden = (round < 2);
            dealingCards(hidden);
        }

//        for (int i = 0; i < piles.size(); i++) {
//            System.out.println("Player Pile " + (i + 1) + ": " + piles.get(i));
//        }
//        System.out.println("Remaining Cards: " + remainingDeck);

        // set up the foundations
        foundationDiamonds = new ArrayList<>();
        foundationClubs = new ArrayList<>();
        foundationHearts = new ArrayList<>();
        foundationSpades = new ArrayList<>();
        foundations = new ArrayList<>();
        foundations.add(foundationDiamonds);
        foundations.add(foundationClubs);
        foundations.add(foundationHearts);
        foundations.add(foundationSpades);
    }

    // ** Thread
    // animated dealing effect
    public void animateInitialDealing() {
        new Thread(() -> {
            try {
                // Create piles and setup for initial dealing
                pile1 = new ArrayList<>();
                pile2 = new ArrayList<>();
                pile3 = new ArrayList<>();
                pile4 = new ArrayList<>();
                pile5 = new ArrayList<>();
                pile6 = new ArrayList<>();
                pile7 = new ArrayList<>();
                piles = new ArrayList<>();
                piles.add(pile1);
                piles.add(pile2);
                piles.add(pile3);
                piles.add(pile4);
                piles.add(pile5);
                piles.add(pile6);
                piles.add(pile7);
                remainingDeck = new ArrayList<>(deck);

                // Deal 3 rounds of cards
                for (int round = 0; round < 3; round++) {
                    boolean hidden = (round < 2);
                    for (int i = 0; i < piles.size(); i++) {
                        if (!remainingDeck.isEmpty()) {
                            Card drawnCard = remainingDeck.removeFirst();
                            drawnCard.hidden = hidden;
                            piles.get(i).add(drawnCard);

                            // Repaint the board after adding each card
                            SwingUtilities.invokeLater(() -> boardPanel.repaint());
                            Thread.sleep(300); // Delay for animation
                        }
                    }
                }

                // Setup foundations after dealing
                foundationDiamonds = new ArrayList<>();
                foundationClubs = new ArrayList<>();
                foundationHearts = new ArrayList<>();
                foundationSpades = new ArrayList<>();
                foundations = new ArrayList<>();
                foundations.add(foundationDiamonds);
                foundations.add(foundationClubs);
                foundations.add(foundationHearts);
                foundations.add(foundationSpades);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Handle interruption
            }
        }).start();
    }

    // dealing 1 card for each pile
    public void dealingCards(boolean hidden){
        // save current game state
        saveGameState();
        for (int i = 0; i < piles.size(); i++) {
            if (!remainingDeck.isEmpty()) {
                Card drawnCard = remainingDeck.removeFirst();
                drawnCard.hidden = hidden;
                piles.get(i).add(drawnCard);
            }else{
                break;
            }
        }
    }

    // ** Thread
    // use thread to achieve an animated effect
    private void animateCardDealing(boolean hidden) {
        new Thread(() -> {
            try {
                // Save current game state
                saveGameState();

                // Deal one card for each pile with animation
                for (int i = 0; i < piles.size(); i++) {
                    if (!remainingDeck.isEmpty()) {
                        Card drawnCard = remainingDeck.removeFirst();
                        drawnCard.hidden = hidden;
                        piles.get(i).add(drawnCard);

                        // Repaint the board to reflect the updated piles
                        SwingUtilities.invokeLater(() -> boardPanel.repaint());

                        // Add a delay for animation
                        Thread.sleep(300); // 300 ms delay between card deals
                    } else {
                        break; // Stop if no cards are left
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Handle thread interruption
            }
        }).start();
    }

    // check if the sequence of cards is valid for moving
    private boolean isValidMove(ArrayList<Card> targetPile) {
        if (draggingCards.isEmpty()) return false;
        Card targetCard = targetPile.isEmpty() ? null : targetPile.get(targetPile.size() - 1);
        Card firstDraggedCard = draggingCards.get(0);

        return targetCard == null || (firstDraggedCard.getValueAsNumber() + 1 == targetCard.getValueAsNumber()
                && ((firstDraggedCard.isRed() && targetCard.isBlack()) || (firstDraggedCard.isBlack() && targetCard.isRed())));
    }

    // move cards between piles
    private void moveCards(int fromPileIndex, int toPileIndex) {
        // save current game state
        saveGameState();
        ArrayList<Card> fromPile = piles.get(fromPileIndex);
        ArrayList<Card> toPile = piles.get(toPileIndex);
        // if onlyKing is triggered
        if (toPile.isEmpty() && isKingConstraintActive) {
            // if the bottom card of the dragged cards is a King
            Card bottomCard = draggingCards.get(0);
            // not allowed
            if (bottomCard.getValueAsNumber() != 13) {
                JOptionPane.showMessageDialog(frame,
                        "Only Kings can be moved to an empty pile when this rule is active.",
                        "Invalid Move",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        toPile.addAll(draggingCards);
        fromPile.removeAll(draggingCards);

        // reveal the card on top of the fromPile if it exists
        if (!fromPile.isEmpty()) {
            fromPile.get(fromPile.size() - 1).reveal();
            // update the score
            score += 4;
            updateScore();
        }
    }

    // checking if the card can be moved to the foundation
    private boolean isValidFoundationMove(Card card, ArrayList<Card> foundation, String suit) {
        if (!card.suit.equals(suit)) return false; // Suit mismatch
        if (foundation.isEmpty()) return card.getValueAsNumber() == 1; // First card must be Ace
        Card topCard = foundation.get(foundation.size() - 1);
        return card.getValueAsNumber() == topCard.getValueAsNumber() + 1; // Value must increment by 1
    }


    // method for getting movable cards when clicking on the piles
    private ArrayList<Card> getSelected(int pileIndex) {
        ArrayList<Card> fromPile = piles.get(pileIndex);
        ArrayList<Card> selectedCards = new ArrayList<>();

        if (fromPile.isEmpty()) return selectedCards; // Return empty if pile is empty

        int startIndex = fromPile.size() - 1; // Start from the last card
        while (startIndex > 0) {
            Card currentCard = fromPile.get(startIndex);
            Card previousCard = fromPile.get(startIndex - 1);
            // stop if a hidden card is encountered
            if (currentCard.isHidden() || previousCard.isHidden()) {
                break;
            }
            if (currentCard.getValueAsNumber() == previousCard.getValueAsNumber() - 1 &&
                    ((currentCard.isRed() && previousCard.isBlack()) || (currentCard.isBlack() && previousCard.isRed()))) {
                startIndex--;
            } else {
                break;
            }
        }

        for (int i = startIndex; i < fromPile.size(); i++) {
            selectedCards.add(fromPile.get(i));
        }
        return selectedCards;
    }

    // * Rule
    // if valid for moving
    private boolean isValidDragSequence(ArrayList<Card> cards) {
        if (cards.isEmpty()) return false;

        for (int i = 0; i < cards.size() - 1; i++) {
            Card currentCard = cards.get(i);
            Card nextCard = cards.get(i + 1);

            if (currentCard.getValueAsNumber() != nextCard.getValueAsNumber() + 1 ||
                    !((currentCard.isRed() && nextCard.isBlack()) || (currentCard.isBlack() && nextCard.isRed()))) {
                return false;
            }
        }
        return true;
    }

    // show the rules of the game
    private void showRules() {
        String rules = """
            <html><body style='width:300px; font-family:Arial;'>
            <h3>Easthaven Solitaire Rules</h3>
            <b>Overview:</b><br>
            Easthaven is a one-deck game that blends features of Spider and Klondike. 
            With careful play, you can win one game in ten, and perhaps more.<br><br>

            <b>Layout:</b><br>
            - Shuffle the deck and lay out seven piles of three cards each:<br>
            &emsp;• Two cards face-down and one face-up in each pile.<br>
            - These are the tableaus.<br>
            - Four foundations start empty.<br>
            - Keep the remaining cards in the deck.<br><br>

            <b>Play:</b><br>
            - Tableaus build down, alternating red and black.<br>
            - Full builds (sequences) can be moved onto other tableaus.<br>
            - Only Kings or builds whose bottom card is a King may be moved to an empty tableau pile 
              (except when dealing).<br>
            - Top cards of tableaus may be moved to foundations, which build up in suit, starting with Ace.<br><br>

            <b>Dealing:</b><br>
            - You may deal any time, provided there are no empty tableaus 
              that could be filled by available Kings.<br>
            - Turn up seven cards and place one on each tableau regardless of rank or suit.<br>
            - Usually, you deal when no other moves are available.<br><br>

            <b>Goal:</b><br>
            Move all the cards onto the foundations.<br><br>

            <b>Score Calculation:</b><br>
            - Putting a card to the foundation: <b>+10 points</b>.<br>
            - Revealing a hidden card: <b>+4 points</b>.<br>
            - Undo an action: <b>-1 point</b>.
            </body></html>
            """;

        JOptionPane.showMessageDialog(
                frame,
                rules,
                "Game Rules - Easthaven Solitaire",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // * New game
    // restart the game
    private void startNewGame() {
        int choice = JOptionPane.showConfirmDialog(
                frame,
                "Are you sure you want to start a new game?",
                "Confirm New Game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            // reset the game
            if (deck != null) {
                deck.clear();
            }

            if (piles != null) {
                piles.forEach(pile -> {
                    if (pile != null) {
                        pile.clear();
                    }
                });
            }

            if (foundations != null) {
                foundations.forEach(foundation -> {
                    if (foundation != null) {
                        foundation.clear();
                    }
                });
            }

            if (remainingDeck != null) {
                remainingDeck.clear();
            }

            createDeck();
            Collections.shuffle(deck);
            animateInitialDealing();

            // reset timer
            elapsedTime = 0;
            previousTime = 0;
            timer.restart();

            // reset the score
            score = 0;
            updateScore();

            boardPanel.repaint();
        }
    }

    // * handle completion
    // termination
    private boolean isGameCompleted() {
        // if all piles and the remaining deck are empty
        boolean pilesEmpty = piles.stream().allMatch(ArrayList::isEmpty);
        boolean deckEmpty = remainingDeck.isEmpty();

        // if each foundation has exactly 13 cards in correct order
        boolean foundationsComplete = foundations.stream().allMatch(foundation -> foundation.size() == 13);

        return pilesEmpty && deckEmpty && foundationsComplete;
    }

    // congratulation
    private void handleGameCompletion() {
        int choice = JOptionPane.showOptionDialog(
                frame,
                "Congratulations! You have successfully completed the game!\nDo you want to start a new game?",
                "Game Completed",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                new Object[]{"Yes", "No"}, // Custom options
                "Yes"
        );

        if (choice == JOptionPane.YES_OPTION) {
            startNewGame();
        } else if (choice == JOptionPane.NO_OPTION) {
            saveGameHistory(score);
            System.exit(0); // Quit the game
        }
    }

    // custom function: whether only King and cards sequence with King as a bottom can be moved to the empty slot
    private void toggleKingConstraint() {
        isKingConstraintActive = !isKingConstraintActive;
        String message = isKingConstraintActive
                ? "The 'Only Kings to Empty Pile' rule is now ACTIVE."
                : "The 'Only Kings to Empty Pile' rule is now DISABLED.";
        JOptionPane.showMessageDialog(frame, message, "King Constraint Toggled", JOptionPane.INFORMATION_MESSAGE);
    }

    // * Undo
    // save the current game state
    private void saveGameState() {
        undoStack.push(new GameState(piles, remainingDeck));
    }

    // undo
    private void undo() {
        if (!undoStack.isEmpty()) {
            GameState previousState = undoStack.pop();

            // Restore piles
            for (int i = 0; i < piles.size(); i++) {
                piles.get(i).clear();
                piles.get(i).addAll(previousState.pilesState.get(i));
            }

            // Restore remaining deck
            remainingDeck.clear();
            remainingDeck.addAll(previousState.remainingDeckState);

            // update the score
            score -= 1;
            updateScore();

            boardPanel.repaint();
        } else {
            JOptionPane.showMessageDialog(frame, "No moves to undo!", "Undo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // * Score
    // update the score
    private void updateScore() {
        scoreLabel.setText("Score: " + score);
    }

    // ** Threads
    // a separate thread for dragging to ensure smooth move
    private void startDraggingThread() {
        if (dragThread != null && dragThread.isAlive()) {
            dragThread.interrupt(); // Stop any existing thread
        }

        isDraggingActive = true;
        dragThread = new Thread(() -> {
            try {
                while (isDraggingActive) {
                    SwingUtilities.invokeLater(() -> boardPanel.repaint()); // Repaint UI
                    Thread.sleep(16); // ~60 FPS for smooth animation
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Handle interruption
            }
        });

        dragThread.setPriority(Thread.MAX_PRIORITY); // Set high priority for responsiveness
        dragThread.start();
    }

    private void stopDraggingThread() {
        isDraggingActive = false;
        if (dragThread != null) {
            dragThread.interrupt(); // Safely stop the thread
        }
    }

    // handle exit
    private void handleExitBeforeCompletion() {
        int option = JOptionPane.showOptionDialog(frame,
                "You haven't completed the game. What would you like to do?",
                "Exit Confirmation",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Save & Exit", "Exit without Saving", "Cancel"},
                "Save & Exit");

        if (option == 0) { // Save & Exit
            saveGameProgress(score);
            saveGameHistory(score);
            System.exit(0);
        } else if (option == 1) { // Exit without Saving
            saveGameHistory(score);
            System.exit(0);
        }
        // If Cancel, do nothing and return to the game
    }

    // ** Connect to the database and interact
    // database
    public void initializeDatabase() {
        String url = "jdbc:sqlite:game_history.db";

        String createGameHistoryTableSQL = """
        CREATE TABLE IF NOT EXISTS game_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            starting_time TEXT NOT NULL,
            ending_time TEXT NOT NULL,
            final_score INTEGER NOT NULL
        );
        """;

        String createArchivedTableSQL = """
        CREATE TABLE IF NOT EXISTS archived (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
                starting_time TEXT NOT NULL,
                finish_time TEXT NOT NULL,
                final_score INTEGER NOT NULL,
                piles TEXT NOT NULL,
                remaining_cards TEXT NOT NULL,
                foundations TEXT NOT NULL
        );
        """;

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createGameHistoryTableSQL);
            stmt.execute(createArchivedTableSQL);
            System.out.println("Database initialized successfully.");
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    // ** Saving
    // save the game history
    public void saveGameHistory(int finalScore) {
        endTime = LocalDateTime.now(); // Record ending time
        String formattedStartTime = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedEndTime = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String url = "jdbc:sqlite:game_history.db";
        String insertSQL = "INSERT INTO game_history(starting_time, ending_time, final_score) VALUES(?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(url);
             java.sql.PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, formattedStartTime);
            pstmt.setString(2, formattedEndTime);
            pstmt.setInt(3, finalScore);

            pstmt.executeUpdate();
            System.out.println("Game history saved successfully.");
        } catch (SQLException e) {
            System.err.println("Error saving game history: " + e.getMessage());
        }
    }

    // saving the game progress
    public void saveGameProgress(int finalScore) {
        String finishTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedStartTime = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // serialize the current game state as strings
        String pilesData = serializePiles();
        String remainingCardsData = serializeCards(remainingDeck);
        String foundationsData = serializeFoundations();

        String insertSQL = """
        INSERT INTO archived (starting_time, finish_time, final_score, piles, remaining_cards, foundations) VALUES (?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:game_history.db");
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, formattedStartTime);
            pstmt.setString(2, finishTime);
            pstmt.setInt(3, score);
            pstmt.setString(4, pilesData);
            pstmt.setString(5, remainingCardsData);
            pstmt.setString(6, foundationsData);

            pstmt.executeUpdate();
            System.out.println("Game progress saved successfully.");
        } catch (SQLException e) {
            System.err.println("Error saving game progress: " + e.getMessage());
        }
    }

    // ** Load the archived game
    // check whether there's an archived game
    private boolean hasSavedProgress() {
        String query = "SELECT COUNT(*) FROM archived";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:game_history.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Error checking saved progress: " + e.getMessage());
        }
        return false;
    }

    // load the saved game
    private void loadSavedGame() {
        String query = "SELECT * FROM archived ORDER BY id DESC LIMIT 1";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:game_history.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                // Deserialize and load game state
                LocalDateTime previousStartTime = LocalDateTime.parse(rs.getString("starting_time"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                LocalDateTime previousEndTime = LocalDateTime.parse(rs.getString("finish_time"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                Duration duration = Duration.between(previousStartTime, previousEndTime);
                previousTime = (int) duration.getSeconds();
                score = rs.getInt("final_score");
                deserializePiles(rs.getString("piles"));
                deserializeRemaining(rs.getString("remaining_cards"));
                deserializeFoundations(rs.getString("foundations"));
                System.out.println("Saved game loaded successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Error loading saved game: " + e.getMessage());
        }
    }

    // ** Serialize
    // serialize piles
    private String serializePiles() {
        StringBuilder sb = new StringBuilder();
        for (ArrayList<Card> pile : piles) {
            for (Card card : pile) {
                sb.append(card.value).append("-")
                        .append(card.suit).append("-")
                        .append(card.isHidden() ? "1" : "0").append(","); // "1" = hidden, "0" = visible
            }
            sb.append(";"); // Separate each pile
        }
        return sb.toString();
    }

    // serialize remaining deck
    private String serializeCards(ArrayList<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            sb.append(card.value).append("-")
                    .append(card.suit).append("-")
                    .append(card.isHidden() ? "1" : "0").append(",");
        }
        return sb.toString();
    }

    // serialize foundations
    private String serializeFoundations() {
        StringBuilder sb = new StringBuilder();
        for (ArrayList<Card> foundation : foundations) {
            for (Card card : foundation) {
                sb.append(card.value).append("-")
                        .append(card.suit).append("-")
                        .append(card.isHidden() ? "1" : "0").append(",");
            }
            sb.append(";"); // Separate each foundation
        }
        return sb.toString();
    }

    // ** Deserialize
    private void deserializePiles(String data) {
        String[] pileStrings = data.split(";");
        pile1 = new ArrayList<Card>();
        pile2 = new ArrayList<Card>();
        pile3 = new ArrayList<Card>();
        pile4 = new ArrayList<Card>();
        pile5 = new ArrayList<Card>();
        pile6 = new ArrayList<Card>();
        pile7 = new ArrayList<Card>();
        // to manage these piles
        piles = new ArrayList<>();
        piles.add(pile1);
        piles.add(pile2);
        piles.add(pile3);
        piles.add(pile4);
        piles.add(pile5);
        piles.add(pile6);
        piles.add(pile7);
        for (int i = 0; i < pileStrings.length; i++) {
            String[] cardStrings = pileStrings[i].split(",");

            for (String cardString : cardStrings) {
                if (!cardString.isEmpty()) {
                    String[] parts = cardString.split("-");
                    String value = parts[0];
                    String suit = parts[1];
                    boolean hidden = parts[2].equals("1");
                    piles.get(i).add(new Card(value, suit, hidden));
                }
            }
        }
    }

    private void deserializeRemaining(String data) {
        remainingDeck = new ArrayList<>();
        String[] cardStrings = data.split(",");
        for (String cardString : cardStrings) {
            if (!cardString.isEmpty()) {
                String[] parts = cardString.split("-");
                String value = parts[0];
                String suit = parts[1];
                boolean hidden = parts[2].equals("1");
                remainingDeck.add(new Card(value, suit, hidden));
            }
        }
    }

    private void deserializeFoundations(String data) {
        String[] foundationStrings = data.split(";");
        foundationDiamonds = new ArrayList<>();
        foundationClubs = new ArrayList<>();
        foundationHearts = new ArrayList<>();
        foundationSpades = new ArrayList<>();
        foundations = new ArrayList<>();
        foundations.add(foundationDiamonds);
        foundations.add(foundationClubs);
        foundations.add(foundationHearts);
        foundations.add(foundationSpades);
        for (int i = 0; i < foundationStrings.length; i++) {
            String[] cardStrings = foundationStrings[i].split(",");

            for (String cardString : cardStrings) {
                if (!cardString.isEmpty()) {
                    String[] parts = cardString.split("-");
                    String value = parts[0];
                    String suit = parts[1];
                    boolean hidden = parts[2].equals("1");
                    foundations.get(i).add(new Card(value, suit, hidden));
                }
            }
        }
    }

    public static void main(String[] args) {
        new Solitaire();
    }
}
