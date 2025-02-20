package stud.g07;

import core.board.PieceColor;
import core.game.Move;

public class MoveWithValue {
    public Move move;
    public int Value;
    public PieceColor color;

    public MoveWithValue(Move move, int Value, PieceColor color){
        this.Value=Value;
        this.move=move;
        this.color=color;
    }
}
