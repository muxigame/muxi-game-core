"""Audited SavedData policy and trader timer conversion, own synthetic saves only."""
from pathlib import Path
import json,sys
from run_synthetic import ROOT,OUT,fixture,C,S,I,write,val
sys.path.insert(0,str(ROOT/'tools'))
from world_roles.transaction import plan,apply,rollback,snapshot
POLICY=json.loads((OUT/'verified-saveddata-policy.json').read_text());CHECKS=0

def check(ok,message):
    global CHECKS
    CHECKS+=1
    if not ok:raise AssertionError(message)

def populated():
    p=fixture()
    for name,scope in POLICY.items():
        write(p/'data'/name,C(data=C(marker=S('home '+name),spawnDelay=I(120),spawnChance=I(25))))
        if scope=='dimension' and name!='goblintraders_goblin_trader_spawner.dat':
            write(p/'dimensions/muxi_game_core/overworld/data'/name,C(data=C(marker=S('survival '+name),spawnDelay=I(400),spawnChance=I(40))))
    return p

def run():
    p=populated();before=snapshot(p);draft=plan(p,data_policy=POLICY)
    check(not draft['blockers'],'all audited ownership policies accepted together')
    timer=p/'dimensions/muxi_game_core/overworld/data/muxi_goblin_trader.dat';timer_bytes=timer.read_bytes()
    check(apply(p,draft)['committed'],'metadata transaction committed')
    for name,scope in POLICY.items():
        if scope=='global':check(val(p/'data'/name)['data']['marker']=='home '+name,'global primary storage retained '+name)
        else:
            check(val(p/'dimensions/muxi_game_core/homeworld/data'/name)['data']['marker']=='home '+name,'home per-world storage moved '+name)
            if name not in ('muxi_goblin_trader.dat','goblintraders_goblin_trader_spawner.dat'):
                check(val(p/'data'/name)['data']['marker']=='survival '+name,'survival per-world storage moved '+name)
    check((p/'data/goblintraders_goblin_trader_spawner.dat').read_bytes()==timer_bytes,'native primary trader counter filename changes without changing bytes')
    check(not (p/'data/muxi_goblin_trader.dat').exists(),'survival timer not duplicated under obsolete primary name')
    check(rollback(p)['byteIdenticalOriginalSave'],'audited metadata transaction rolls back exactly')
    check(snapshot(p)==before,'every original metadata payload restored')
    for boundary in range(len(draft['operations'])+1,len(draft['operations'])+len(draft['moves'])+1):
        p=populated();before=snapshot(p);draft=plan(p,data_policy=POLICY)
        try:apply(p,draft,fail_after=boundary);raise AssertionError('missing interrupt')
        except RuntimeError:pass
        check(rollback(p)['byteIdenticalOriginalSave'],'every data move interruption restores bytes')
        check(snapshot(p)==before,'metadata interruption byte identity')
    p=populated();write(p/'dimensions/muxi_game_core/overworld/data/goblintraders_goblin_trader_spawner.dat',C(data=C(spawnDelay=I(999))))
    check(any('timer variants' in x for x in plan(p,data_policy=POLICY)['blockers']),'actual two-timer player progress conflict requires explicit resolution')
    report={'success':True,'checks':CHECKS,'auditedPolicies':len(POLICY),'fixturesOnly':True,'productionMutation':False}
    (OUT/'metadata-synthetic-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
if __name__=='__main__':run()
