import net.muxigame.shadernative.JoinObservation;
import com.google.gson.*;
public final class JoinObservationTest {
 private static int checks;private static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  check(JoinObservation.token()==0);JoinObservation.begin("join-0");long old=JoinObservation.token();JoinObservation.ui("ConnectScreen","null");JoinObservation.ui("ConnectScreen","null");check(JoinObservation.snapshot().getAsJsonArray("events").size()==2);
  JsonObject log=new JsonObject();log.addProperty("leasePresent",false);JoinObservation.logging(log,"poll",false);JoinObservation.logging(log,"poll",false);check(JoinObservation.snapshot().getAsJsonArray("events").size()==3);JoinObservation.logging(log,"handleLogin",true);check(JoinObservation.snapshot().getAsJsonArray("events").size()==4);
  long reload=JoinObservation.reloadBegin(old);JoinObservation.reloadEnd(old,reload,null);check(JoinObservation.snapshot().getAsJsonArray("events").size()==6);
  var snapshot=JoinObservation.finish();check(JoinObservation.token()==0);JoinObservation.event(old,"late","ignored");check(snapshot.getAsJsonArray("events").size()==6);
  JoinObservation.begin("join-1");Thread worker=new Thread(()->JoinObservation.reloadEnd(old,reload,new RuntimeException()));worker.start();worker.join();check(JoinObservation.snapshot().getAsJsonArray("events").size()==1);
  for(int i=0;i<100;i++)JoinObservation.event(JoinObservation.token(),"bounded",Integer.toString(i));check(JoinObservation.snapshot().getAsJsonArray("events").size()==64);check(JoinObservation.snapshot().get("dropped").getAsInt()==37);JoinObservation.finish();System.out.println("PASS "+checks+" bounded observation assertions");
 }
}
