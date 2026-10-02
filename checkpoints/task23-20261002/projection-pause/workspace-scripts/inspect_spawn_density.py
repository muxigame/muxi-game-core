"""Read-only live evidence; credentials never enter the output."""
import json
from pathlib import Path
from online_clone import Rcon, utc

def main():
    rcon = Rcon()
    commands = ['list', 'difficulty', 'gamerule doMobSpawning', 'neoforge tps',
                'execute as @a run data get entity @s Dimension',
                'execute as @a run data get entity @s Pos',
                'execute as @a run data get entity @s playerGameType']
    dimensions = ['minecraft:overworld','muxi_game_core:overworld','muxi_game_core:adventure']
    for dimension in dimensions:
        prefix = f'execute in {dimension} run '
        commands += [prefix+'difficulty', prefix+'gamerule doMobSpawning',
                     prefix+'time query daytime', prefix+'neoforge entity list']
        if dimension != 'minecraft:overworld':
            for mob in ['zombie','drowned','skeleton','creeper','spider','slime','husk','witch','enderman','stray','zombie_villager','warden']:
                commands.append(f'execute in {dimension} as @e[type=minecraft:{mob},distance=0..] run data get entity @s Pos')
    evidence = {'utc': utc(), 'mode':'read_only', 'commands':[]}
    for command in commands:
        evidence['commands'].append({'command':command, 'response':rcon.command(command)})
    destination = Path(__file__).with_name('spawn-density-live.json')
    destination.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'path':str(destination),'commands':len(commands),
                      'utc':evidence['utc'],'summary':evidence['commands'][:7]},ensure_ascii=False))

if __name__ == '__main__':
    main()
