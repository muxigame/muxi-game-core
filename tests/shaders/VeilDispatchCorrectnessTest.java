import net.neoforged.bus.api.*;
import net.muxigame.core.compat.shaders.*;
import java.util.*;

public final class VeilDispatchCorrectnessTest {
 public static class Parent extends Event{}
 public static final class Child extends Parent{}
 static int checks;
 static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static IEventBus bus(){var bus=BusBuilder.builder().allowPerPhasePost().build();bus.start();return bus;}
 static List<String> route(boolean optimized)throws Exception{
  var first=bus();var second=bus();var none=bus();var order=new ArrayList<String>();
  first.addListener(EventPriority.HIGHEST,Parent.class,e->{order.add("first-high");first.addListener(EventPriority.LOWEST,Child.class,next->order.add("first-dynamic-low"));});
  first.addListener(EventPriority.LOW,Child.class,e->order.add("first-low"));second.addListener(EventPriority.HIGHEST,Child.class,e->order.add("second-high"));
  var event=new Child();for(var priority:EventPriority.values())for(var bus:List.of(first,second,none))if(!optimized||!VeilShaderEventDispatch.empty(bus,priority,event))bus.post(priority,event);
  return order;
 }
 public static void main(String[] args)throws Exception{
  var bus=bus();var event=new Child();check(VeilShaderEventDispatch.empty(bus,EventPriority.NORMAL,event),"empty native phase detected");
  java.util.function.Consumer<Parent> listener=e->{};bus.addListener(EventPriority.HIGH,Parent.class,listener);
  check(!VeilShaderEventDispatch.empty(bus,EventPriority.HIGH,event),"inherited listener retained");check(VeilShaderEventDispatch.empty(bus,EventPriority.NORMAL,event),"other empty phase remains empty");bus.unregister(listener);check(VeilShaderEventDispatch.empty(bus,EventPriority.HIGH,event),"unregister re-read without stale subscription");
  List<String> nativeOrder=route(false),optimizedOrder=route(true);check(nativeOrder.equals(optimizedOrder),"phase-major mod order and dynamic registration preserved");check(nativeOrder.equals(List.of("first-high","second-high","first-low","first-dynamic-low")),"fixture covers ordering and dynamically added lower phase");
  var unsupported=(IEventBus)java.lang.reflect.Proxy.newProxyInstance(IEventBus.class.getClassLoader(),new Class[]{IEventBus.class},(proxy,method,values)->null);check(!VeilShaderEventDispatch.empty(unsupported,EventPriority.NORMAL,event),"unknown event bus requests native delivery");
  check(!VeilShaderEventDispatch.empty(null,EventPriority.NORMAL,event),"missing bus stays native");
  check(!VeilShaderEventDispatch.enabled(),"default disabled and post-bootstrap/version guards required");
  System.out.println("Veil native bus correctness checks passed: "+checks);
 }
}
