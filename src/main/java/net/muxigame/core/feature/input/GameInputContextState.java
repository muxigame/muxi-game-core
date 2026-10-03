package net.muxigame.core.feature.input;

/** Server-issued context only; never derives gameplay ownership from terminal page state. */
public final class GameInputContextState {
    private record Context(String game,String dimension) {}
    private static volatile Context context=new Context("","");
    private GameInputContextState() {}
    public static void accept(String game,String dimension){context=new Context(game,dimension);}
    public static void reset(){context=new Context("","");}
    public static boolean claimsOutbreak(String dimension){var value=context;return value.game.equals("outbreak")&&value.dimension.equals(dimension);}
}
