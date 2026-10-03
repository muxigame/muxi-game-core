"""Compile actual SSO/config/trust sources against deterministic connection fixtures.

The unit mode tests lifecycle races with a controllable transport. --serve uses
the actual Java HTTPS client for an isolated Auth/Core/website integration run.
Minecraft fixtures model listeners; this is not GUI/MCEF/player acceptance.
"""
import argparse
from pathlib import Path
import os
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
FILES={
"com/mojang/authlib/GameProfile.java":'''package com.mojang.authlib;
public record GameProfile(java.util.UUID id,String name){public String getName(){return name;}public java.util.UUID getId(){return id;}}''',
"net/minecraft/server/network/ServerGamePacketListenerImpl.java":'''package net.minecraft.server.network;
public class ServerGamePacketListenerImpl {public boolean online=true;public boolean isAcceptingMessages(){return online;}}''',
"net/minecraft/server/MinecraftServer.java":'''package net.minecraft.server;
public class MinecraftServer {
 private final Thread owner=Thread.currentThread();public boolean isSameThread(){return Thread.currentThread()==owner;}
 public final Players players=new Players();public final java.util.Queue<Runnable> work=new java.util.concurrent.ConcurrentLinkedQueue<>();
 public static class Players {public final java.util.Map<java.util.UUID,net.minecraft.server.level.ServerPlayer> values=new java.util.HashMap<>();
 public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id){return values.get(id);}}
 public Players getPlayerList(){return players;}public void execute(Runnable r){work.add(r);}
 public void flush(){while(!work.isEmpty())work.remove().run();}
 public void until(java.util.function.BooleanSupplier done)throws Exception {long deadline=System.nanoTime()+6_000_000_000L;
 while(!done.getAsBoolean()&&System.nanoTime()<deadline){flush();Thread.sleep(5);}flush();if(!done.getAsBoolean())throw new AssertionError("Callback timeout");}
}''',
"net/minecraft/server/level/ServerPlayer.java":'''package net.minecraft.server.level;
public class ServerPlayer {
 public final net.minecraft.server.MinecraftServer server;public net.minecraft.server.network.ServerGamePacketListenerImpl connection;
 public com.mojang.authlib.GameProfile profile;
 public ServerPlayer(net.minecraft.server.MinecraftServer s,String name,java.util.UUID id){server=s;profile=new com.mojang.authlib.GameProfile(id,name);connection=new net.minecraft.server.network.ServerGamePacketListenerImpl();s.players.values.put(id,this);}
 public com.mojang.authlib.GameProfile getGameProfile(){return profile;}public java.util.UUID getUUID(){return profile.id();}
}''',
"net/muxigame/core/feature/login/TerminalPassportNetwork.java":'''package net.muxigame.core.feature.login;
public class TerminalPassportNetwork {public record Request(String requestId,String gameSession,int action){}public record Result(String requestId,String gameSession,long uid,int status){}}''',
"net/neoforged/neoforge/network/PacketDistributor.java":'''package net.neoforged.neoforge.network;
public class PacketDistributor {public record Delivery(net.minecraft.server.level.ServerPlayer player,net.muxigame.core.feature.login.TerminalPassportNetwork.Result packet){}
 public static final java.util.List<Delivery> deliveries=new java.util.ArrayList<>();
 public static void sendToPlayer(net.minecraft.server.level.ServerPlayer p,net.muxigame.core.feature.login.TerminalPassportNetwork.Result packet){deliveries.add(new Delivery(p,packet));}}
''',
}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--gson',type=Path,required=True)
    parser.add_argument('--trusted-source',type=Path,required=True)
    parser.add_argument('--serve',action='store_true')
    parser.add_argument('--truststore',type=Path)
    args=parser.parse_args()
    suffix='.exe' if os.name=='nt' else ''
    FILES['net/neoforged/neoforge/event/tick/ServerTickEvent.java']='package net.neoforged.neoforge.event.tick; public class ServerTickEvent {public static class Post {}}'
    with tempfile.TemporaryDirectory(prefix='terminal-sso-trust-') as directory:
        tmp=Path(directory);classes=tmp/'classes';classes.mkdir();generated=[]
        for relative,text in FILES.items():
            target=tmp/'src'/relative;target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(text,encoding='utf-8');generated.append(target)
        generated.append(ROOT/'tests/terminal-sso-trust/TerminalTrustHarness.java')
        generated.append(args.trusted_source)
        for relative in ('config/CoreConfig.java','feature/identity/IdentityRules.java',
                         'feature/login/TerminalSsoIdentity.java','feature/login/TerminalPassportServer.java'):
            generated.append(ROOT/'src/main/java/net/muxigame/core'/relative)
        arguments=['--release','21','-encoding','UTF-8','-proc:none','-cp',str(args.gson),'-d',str(classes),*map(str,generated)]
        argfile=tmp/'compile.args'
        argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in arguments),encoding='utf-8')
        subprocess.run([str(args.java_home/'bin'/('javac'+suffix)),'@'+str(argfile)],check=True)
        command=[str(args.java_home/'bin'/('java'+suffix))]
        if args.truststore:command.extend(['-Djavax.net.ssl.trustStore='+str(args.truststore),'-Djavax.net.ssl.trustStorePassword=isolated-synthetic'])
        command.extend(['-cp',str(classes)+os.pathsep+str(args.gson),'net.muxigame.core.feature.login.TerminalTrustHarness'])
        if args.serve:command.append('--serve')
        raise SystemExit(subprocess.run(command).returncode)

if __name__=='__main__':main()
