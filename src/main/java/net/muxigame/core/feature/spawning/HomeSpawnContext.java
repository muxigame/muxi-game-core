package net.muxigame.core.feature.spawning;
import java.util.ArrayDeque;
/** Invocation provenance, never stored in entity NBT or retained beyond a task. */
public final class HomeSpawnContext {
 public enum Origin { PLAYER, SPAWNER, TRAVEL, AMBIENT }
 private static final ThreadLocal<ArrayDeque<Scope>> STACK=new ThreadLocal<>();
 public static Origin current(){var s=STACK.get();if(s==null)return null;for(var scope:s)if(scope.origin==Origin.TRAVEL)return Origin.TRAVEL;for(var scope:s)if(scope.origin==Origin.AMBIENT)return Origin.AMBIENT;return s.peek().origin;}
 public static Scope enter(Origin origin){var s=STACK.get();if(s==null){s=new ArrayDeque<>();STACK.set(s);}var scope=new Scope(s,origin);s.push(scope);return scope;}
 public static Runnable bind(Origin origin,Runnable action){return ()->{try(var ignored=enter(origin)){action.run();}};}
 public static final class Scope implements AutoCloseable {
  private final ArrayDeque<Scope> stack;private final Origin origin;private final Thread thread=Thread.currentThread();private boolean closed;
  private Scope(ArrayDeque<Scope> stack,Origin origin){this.stack=stack;this.origin=origin;}
  public void close(){if(closed)return;if(Thread.currentThread()!=thread||STACK.get()!=stack||stack.peek()!=this)throw new IllegalStateException("Spawn context must close on its owner thread in LIFO order");stack.pop();closed=true;if(stack.isEmpty())STACK.remove();}
 }
 private HomeSpawnContext(){}
}
