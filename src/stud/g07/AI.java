package stud.g07;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.*;

public class AI extends core.player.AI {
    private int[][] initRoadsValues = new int[19][19];
    private PieceColor[] boardState;
    private static final int INF = Integer.MAX_VALUE;
    private ArrayList<MoveWithValue> moveWithValues = new ArrayList<>();
    private HashSet<Integer> candidatePoints = new HashSet<>(); // 候选点集合
    private static final int[] DX = {-1, -1, 0, 1, 1, 1, 0, -1}; // 八个方向
    private static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};
    private PieceColor myColor = PieceColor.EMPTY;
    private int Depth = 5; // 搜索深度
    private int Width = 10; // 搜索宽度
//    private Board board;
    private int nowValue;
    private static int[] opponentValue = new int[]{1, 25, 50, 6000, 6000, 1000000};
    private static int[] myValue = new int[]{1, 20, 40, 200, 200, 1000000};

    // Zobrist hashing
    private long[][] zobristTable = new long[361][3]; // 361 positions, 3 states (EMPTY, BLACK, WHITE)
    private long zobristHash = 0; // Current Zobrist hash of the board
    private Map<Long, TranspositionEntry> transpositionTable = new HashMap<>();

    public AI() {
        super();
        initializeZobristTable();
    }

//    public Board getBoard() {
//        return this.board;
//    }

    /**
     * 初始化 Zobrist 哈希表
     */
    private void initializeZobristTable() {
        Random rand = new Random();
        for (int i = 0; i < 361; i++) {
            for (int j = 0; j < 3; j++) {
                zobristTable[i][j] = rand.nextLong();
            }
        }
    }

    /**
     * 更新 Zobrist 哈希值
     */
    private void updateZobristHash(int index, PieceColor oldColor, PieceColor newColor) {
        if (oldColor != PieceColor.EMPTY) {
            zobristHash ^= zobristTable[index][oldColor.ordinal()];
        }
        if (newColor != PieceColor.EMPTY) {
            zobristHash ^= zobristTable[index][newColor.ordinal()];
        }
    }

    /**
     * 初始化道路估值。
     */
    public int InitRoads(int row0, int col0, int row1, int col1, PieceColor color) {
        int totalValue = 0;

        int row;
        int col;
        for (int k = 0; k < 2; k++) {
            if (k == 0) {
                row = row0;
                col = col0;
            } else {
                row = row1;
                col = col1;
            }
            // 遍历四个方向：竖直、水平、左上到右下、右上到左下
            totalValue += calculateDirectionalRoadValue(row, col, 0, 1, color);  // 竖直
            totalValue += calculateDirectionalRoadValue(row, col, 1, 0, color);  // 水平
            totalValue += calculateDirectionalRoadValue(row, col, 1, 1, color);  // 左上到右下
            totalValue += calculateDirectionalRoadValue(row, col, 1, -1, color); // 右上到左下
        }

        return totalValue;
    }

    private int calculateDirectionalRoadValue(int row, int col, int dx, int dy, PieceColor color) {
        int totalValue = 0;
        for (int i = -5; i <= 0; i++) { // 从 -5 到 0 作为窗口起点
            int myCount = 0, opponentCount = 0;
            boolean flag = false;
            for (int j = 0; j < 6; j++) { // 窗口大小为 6
                int newRow = row + (i + j) * dx;
                int newCol = col + (i + j) * dy;
                if (newRow >= 0 && newRow < 19 && newCol >= 0 && newCol < 19) {
                    int index = newRow * 19 + newCol;
                    if (boardState[index] == color) {
                        myCount++;
                    } else if (boardState[index] == color.opposite()) {
                        opponentCount++;
                    }
                }else {
                    flag = true;
                }
            }
            if(flag){
                opponentCount++;
            }
            totalValue += calculateRoadValue(myCount, opponentCount, color);
        }
        return totalValue;
    }

    private int calculateRoadValue(int myCount, int opponentCount, PieceColor color) {
        if (myCount > 0 && opponentCount == 0) {
            return myValue[myCount - 1];
        } else if (myCount == 0 && opponentCount > 0) {
            return -opponentValue[opponentCount - 1];
        }
        return 0;
    }

    /**
     * 生成候选点集合（只考虑已有棋子周围的空点）。
     */
    public ArrayList<Move> GenerateLegalMoves() {
        ArrayList<Move> LegalMoves = new ArrayList<>();
        candidatePoints.clear();

        // 遍历棋盘，找出所有棋子的周围点
        for (int i = 0; i < 361; i++) {
            if (boardState[i] != PieceColor.EMPTY) {
                int row = i / 19;
                int col = i % 19;
                for (int d = 0; d < 8; d++) { // 八个方向
                    int newRow = row + DX[d];
                    int newCol = col + DY[d];
                    if (newRow >= 0 && newRow < 19 && newCol >= 0 && newCol < 19) {
                        int index = newRow * 19 + newCol;
                        if (boardState[index] == PieceColor.EMPTY) {
                            if (!candidatePoints.contains(index)) {
                                candidatePoints.add(index);
                            }
                        }
                    }
                    newRow = row + 2 * DX[d];
                    newCol = col + 2 * DY[d];
                    if (newRow >= 0 && newRow < 19 && newCol >= 0 && newCol < 19) {
                        int index = newRow * 19 + newCol;
                        if (boardState[index] == PieceColor.EMPTY) {
                            if (!candidatePoints.contains(index)) {
                                candidatePoints.add(index);
                            }
                        }
                    }
                }
            }
        }

        // 生成两两组合的合法走法
        Integer[] candidates = candidatePoints.toArray(new Integer[0]);
        for (int i = 0; i < candidates.length; i++) {
            for (int j = i + 1; j < candidates.length; j++) {
                LegalMoves.add(new Move(candidates[i], candidates[j]));
            }
        }

        return LegalMoves;
    }

    /**
     * Alpha-beta 搜索，加入 Zobrist 哈希和置换表优化。
     */
    private int alphaBeta(int depth, int alpha, int beta, PieceColor color) {
        // 检查置换表
        if (transpositionTable.containsKey(zobristHash)) {
//            System.out.println("transpositionTable exists.");
            TranspositionEntry entry = transpositionTable.get(zobristHash);
            if (entry.depth >= depth) {
                if (entry.flag == TranspositionEntry.EXACT) return entry.value;
                if (entry.flag == TranspositionEntry.LOWERBOUND) alpha = Math.max(alpha, entry.value);
                if (entry.flag == TranspositionEntry.UPPERBOUND) beta = Math.min(beta, entry.value);
                if (alpha >= beta) return entry.value;
            }
        }

        if (depth == 0) {
            return evaluateBoard(myColor);
        }

        ArrayList<Move> legalMoves = GenerateLegalMoves();
        List<MoveWithValue> evaluatedMoves = new ArrayList<>();
        for (Move move : legalMoves) {
            evaluatedMoves.add(SimulateMove(move, color).get(moveWithValues.size() - 1));
        }
        evaluatedMoves.sort(Comparator.comparingInt(m -> -m.Value));

        int originalAlpha = alpha;
        int bestValue;

        if (color == myColor) {
            bestValue = Integer.MIN_VALUE;
            for (int i = 0; i < Math.min(Width, evaluatedMoves.size()); i++) {
                Move move = evaluatedMoves.get(i).move;

                if(evaluatedMoves.get(i).Value >= 800000){
                    boardState[move.index1()] = color;
                    boardState[move.index2()] = color;
                    bestValue = 5000000; // 胜利
                    boardState[move.index1()] = PieceColor.EMPTY;
                    boardState[move.index2()] = PieceColor.EMPTY;
                    break;
                }

                // Make the move
                updateZobristHash(move.index1(), boardState[move.index1()], color);
                updateZobristHash(move.index2(), boardState[move.index2()], color);
                boardState[move.index1()] = color;
                boardState[move.index2()] = color;

                // Recursively search
                int value = alphaBeta(depth - 1, alpha, beta, color.opposite());

                // Undo the move
                boardState[move.index1()] = PieceColor.EMPTY;
                boardState[move.index2()] = PieceColor.EMPTY;
                updateZobristHash(move.index1(), color, PieceColor.EMPTY);
                updateZobristHash(move.index2(), color, PieceColor.EMPTY);

                // Update best value and alpha
                bestValue = Math.max(bestValue, value);
                alpha = Math.max(alpha, value);

                if (beta <= alpha) break; // Beta cutoff
            }
        } else {
            bestValue = Integer.MAX_VALUE;
            for (int i = 0; i < Math.min(Width, evaluatedMoves.size()); i++) {
                if(evaluatedMoves.get(i).Value >= 800000){
                    Move move = evaluatedMoves.get(i).move;
                    boardState[move.index1()] = color;
                    boardState[move.index2()] = color;
                    bestValue = -5000000; // 不允许扩展漏手
                    boardState[move.index1()] = PieceColor.EMPTY;
                    boardState[move.index2()] = PieceColor.EMPTY;
                    break;
                }else {
                    Move move = evaluatedMoves.get(i).move;

                    // Make the move
                    updateZobristHash(move.index1(), boardState[move.index1()], color);
                    updateZobristHash(move.index2(), boardState[move.index2()], color);
                    boardState[move.index1()] = color;
                    boardState[move.index2()] = color;

                    // Recursively search
                    int value = alphaBeta(depth - 1, alpha, beta, color.opposite());

                    // Undo the move
                    boardState[move.index1()] = PieceColor.EMPTY;
                    boardState[move.index2()] = PieceColor.EMPTY;
                    updateZobristHash(move.index1(), color, PieceColor.EMPTY);
                    updateZobristHash(move.index2(), color, PieceColor.EMPTY);

                    // Update best value and beta
                    bestValue = Math.min(bestValue, value);
                    beta = Math.min(beta, value);

                    if (beta <= alpha) break; // Alpha cutoff
                }
            }
        }

        // Store the result in the transposition table
        int flag;
        if (bestValue <= originalAlpha) {
            flag = TranspositionEntry.UPPERBOUND;
        } else if (bestValue >= beta) {
            flag = TranspositionEntry.LOWERBOUND;
        } else {
            flag = TranspositionEntry.EXACT;
        }

        transpositionTable.put(zobristHash, new TranspositionEntry(bestValue, depth, flag));

        return bestValue;
    }

    private int evaluateBoard(PieceColor color) {
        int totalValue = 0;
        for (int i = 0; i < 361; i++) {
            int row = i / 19;
            int col = i % 19;
            if (boardState[i] == color) {
                totalValue += InitRoads(row, col, row, col, color);
            } else if (boardState[i] == color.opposite()) {
                totalValue -= InitRoads(row, col, row, col, color.opposite());
            }
        }
        return totalValue;
    }

    public ArrayList<MoveWithValue> SimulateMove(Move move, PieceColor color) {
        int preValue = InitRoads(move.row0() - 'A', move.col0() - 'A', move.row1() - 'A', move.col1() - 'A', color);
        boardState[move.index1()] = color;
        boardState[move.index2()] = color;
        int curValue = InitRoads(move.row0() - 'A', move.col0() - 'A', move.row1() - 'A', move.col1() - 'A', color);
        boardState[move.index1()] = PieceColor.EMPTY;
        boardState[move.index2()] = PieceColor.EMPTY;
        moveWithValues.add(new MoveWithValue(move, curValue - preValue, color));
        return moveWithValues;
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        if (this.myColor == PieceColor.EMPTY) {
            this.myColor = board.whoseMove();
        }
        boardState = this.board.get_board();
        ArrayList<Move> legalMoves = GenerateLegalMoves();
        moveWithValues.clear();

        List<MoveWithValue> evaluatedMoves = new ArrayList<>();
        for (Move move : legalMoves) {
            evaluatedMoves.add(SimulateMove(move, myColor).get(moveWithValues.size() - 1));
        }
        evaluatedMoves.sort(Comparator.comparingInt(m -> -m.Value));

        int bestValue = Integer.MIN_VALUE;
        Move bestMove = null;

        for (int i = 0; i < Math.min(Width, evaluatedMoves.size()); i++) {

            if(evaluatedMoves.get(i).Value >= 900000){
                Move move = evaluatedMoves.get(i).move;
                return move;
            }

            Move move = evaluatedMoves.get(i).move;

            // Make the move
            updateZobristHash(move.index1(), boardState[move.index1()], myColor);
            updateZobristHash(move.index2(), boardState[move.index2()], myColor);
            boardState[move.index1()] = myColor;
            boardState[move.index2()] = myColor;

            // Alpha-Beta search
            int value = alphaBeta(Depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, myColor.opposite());

            // Undo the move
            boardState[move.index1()] = PieceColor.EMPTY;
            boardState[move.index2()] = PieceColor.EMPTY;
            updateZobristHash(move.index1(), myColor, PieceColor.EMPTY);
            updateZobristHash(move.index2(), myColor, PieceColor.EMPTY);

            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
        }
        System.out.println(bestValue);
        this.board.makeMove(bestMove);
        return bestMove;
    }

    @Override
    public String name() {
        return "G07";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
        for (int i = 0; i < 19; i++) {
            for (int j = 0; j < 19; j++) {
                initRoadsValues[i][j] = Integer.MIN_VALUE;
            }
        }
    }

    // Transposition Entry class for storing table data
    private static class TranspositionEntry {
        static final int EXACT = 0;         // Exact value
        static final int LOWERBOUND = 1;   // Lower bound
        static final int UPPERBOUND = 2;   // Upper bound

        int value;
        int depth;
        int flag;

        TranspositionEntry(int value, int depth, int flag) {
            this.value = value;
            this.depth = depth;
            this.flag = flag;
        }
    }
}