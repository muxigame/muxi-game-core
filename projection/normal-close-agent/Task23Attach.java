import com.sun.tools.attach.VirtualMachine;
public final class Task23Attach {
 public static void main(String[] args)throws Exception {if(args.length!=3||!args[0].equals("33816"))throw new IllegalArgumentException("Own PID only");VirtualMachine vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(args[1],args[2]);}finally{vm.detach();}}
}
