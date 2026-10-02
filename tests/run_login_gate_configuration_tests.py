"""Exercise actual LoginGate and TrustedAccounts over real loopback HTTP.

Minecraft/NeoForge lifecycle objects are deterministic fixtures. This does not
establish actual player, MCEF, broker or production admission acceptance.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
from run_terminal_sso_trust import FILES as SSO_FILES

FILES = dict(SSO_FILES)
FILES['net/minecraft/server/MinecraftServer.java'] = FILES['net/minecraft/server/MinecraftServer.java'].replace(
    'public void execute(Runnable r){work.add(r);}',
    'public final java.util.concurrent.atomic.AtomicInteger enqueued=new java.util.concurrent.atomic.AtomicInteger();public void execute(Runnable r){enqueued.incrementAndGet();work.add(r);}')
FILES.update({
"net/minecraft/network/Connection.java": '''package net.minecraft.network;
public class Connection {public boolean connected=true;public Object listener;public int refused;
public boolean isConnected(){return connected;}public Object getPacketListener(){return listener;}
public void disconnect(net.minecraft.network.chat.Component reason){connected=false;refused++;}}''',
"net/minecraft/network/chat/Component.java": '''package net.minecraft.network.chat;
public record Component(String text){public static Component literal(String s){return new Component(s);}}''',
"net/minecraft/network/protocol/Packet.java": '''package net.minecraft.network.protocol; public interface Packet<T>{}''',
"net/minecraft/network/protocol/configuration/ServerConfigurationPacketListener.java": '''package net.minecraft.network.protocol.configuration;
public interface ServerConfigurationPacketListener {net.minecraft.network.Connection getConnection();}''',
"net/minecraft/server/network/ConfigurationTask.java": '''package net.minecraft.server.network;
public interface ConfigurationTask {record Type(String id){} Type type();void start(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sender);}''',
"net/minecraft/server/network/ServerConfigurationPacketListenerImpl.java": '''package net.minecraft.server.network;
public class ServerConfigurationPacketListenerImpl implements net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener {
public final net.minecraft.server.MinecraftServer server;public final net.minecraft.network.Connection raw;
public com.mojang.authlib.GameProfile profile;public int finished;public boolean failFinish;
public ServerConfigurationPacketListenerImpl(net.minecraft.server.MinecraftServer s,net.minecraft.network.Connection c,com.mojang.authlib.GameProfile p){server=s;raw=c;profile=p;c.listener=this;}
public com.mojang.authlib.GameProfile getOwner(){return profile;}public net.minecraft.network.Connection getConnection(){return raw;}
public net.minecraft.server.MinecraftServer getMainThreadEventLoop(){return server;}
public void finishCurrentTask(ConfigurationTask.Type type){if(!server.isSameThread())throw new AssertionError("Off-thread finish");
if(!type.id().equals("muxi_game_core:join_grant"))throw new AssertionError("Wrong type");if(failFinish)throw new IllegalStateException("Task replaced");finished++;}}''',
"net/minecraft/server/network/ServerGamePacketListenerImpl.java": '''package net.minecraft.server.network;
public class ServerGamePacketListenerImpl {public boolean online=true;public net.minecraft.network.Connection raw=new net.minecraft.network.Connection();
public boolean isAcceptingMessages(){return online&&raw.isConnected();}public net.minecraft.network.Connection getConnection(){return raw;}
public void disconnect(net.minecraft.network.chat.Component reason){online=false;raw.disconnect(reason);}}''',
"net/neoforged/neoforge/common/util/FakePlayer.java": '''package net.neoforged.neoforge.common.util;
public class FakePlayer extends net.minecraft.server.level.ServerPlayer {public FakePlayer(net.minecraft.server.MinecraftServer s,String n,java.util.UUID id){super(s,n,id);}}''',
"net/neoforged/bus/api/IEventBus.java": '''package net.neoforged.bus.api;
public interface IEventBus {<T>void addListener(java.util.function.Consumer<T> listener);}''',
"net/neoforged/neoforge/event/entity/player/PlayerEvent.java": '''package net.neoforged.neoforge.event.entity.player;
public class PlayerEvent {private final Object entity;public PlayerEvent(Object e){entity=e;}public Object getEntity(){return entity;}
public static class PlayerLoggedInEvent extends PlayerEvent{public PlayerLoggedInEvent(Object e){super(e);}}
public static class PlayerLoggedOutEvent extends PlayerEvent{public PlayerLoggedOutEvent(Object e){super(e);}}}''',
"net/neoforged/neoforge/network/event/RegisterConfigurationTasksEvent.java": '''package net.neoforged.neoforge.network.event;
public class RegisterConfigurationTasksEvent {private final net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener listener;
public final java.util.List<net.minecraft.server.network.ConfigurationTask> tasks=new java.util.ArrayList<>();
public RegisterConfigurationTasksEvent(net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener l){listener=l;}
public net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener getListener(){return listener;}
public void register(net.minecraft.server.network.ConfigurationTask task){tasks.add(task);}}''',
"org/slf4j/Logger.java": '''package org.slf4j;public interface Logger {default void info(String s,Object... args){}default void warn(String s,Object... args){}}''',
"org/slf4j/LoggerFactory.java": '''package org.slf4j;public class LoggerFactory {public static Logger getLogger(String s){return new Logger(){};}}''',
})


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home', type=Path, required=True)
    p.add_argument('--gson', type=Path, required=True)
    p.add_argument('--trusted-source', type=Path, required=True)
    args = p.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    with tempfile.TemporaryDirectory(prefix='login-gate-configuration-') as directory:
        tmp = Path(directory)
        classes = tmp / 'classes'
        classes.mkdir()
        sources = []
        for relative, content in FILES.items():
            target = tmp / 'src' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8')
            sources.append(target)
        sources.extend([args.trusted_source, ROOT / 'tests/login-gate-configuration/LoginGateConfigurationHarness.java'])
        for relative in ('config/CoreConfig.java', 'feature/ServerFeature.java',
                         'feature/identity/IdentityRules.java', 'feature/login/ConnectionAdmissions.java',
                         'feature/login/LoginGate.java'):
            sources.append(ROOT / 'src/main/java/net/muxigame/core' / relative)
        arguments = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', str(args.gson), '-d', str(classes), *map(str, sources)]
        argfile = tmp / 'compile.args'
        argfile.write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in arguments), encoding='utf-8')
        subprocess.run([str(args.java_home / 'bin' / ('javac' + suffix)), '@' + str(argfile)], check=True)
        raise SystemExit(subprocess.run([str(args.java_home / 'bin' / ('java' + suffix)), '-cp',
                         str(classes) + os.pathsep + str(args.gson),
                         'net.muxigame.core.feature.login.LoginGateConfigurationHarness']).returncode)


if __name__ == '__main__':
    main()
