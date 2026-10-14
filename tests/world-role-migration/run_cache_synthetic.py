"""Own synthetic cache payloads only; no user cache or game processes."""
from pathlib import Path
import json,sys,uuid
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from world_roles.cache import plan,apply,rollback,snapshot,names
OUT=ROOT/'build/world-role-migration-20261014';CHECKS=0

def check(value,message):
    global CHECKS
    CHECKS+=1
    if not value:raise AssertionError(message)

def fixture(kind,primary):
    p=OUT/'synthetic-cache'/uuid.uuid4().hex;p.mkdir(parents=True)
    a,b,h=names(kind,primary)
    for folder,marker in [(a,b'HOME-MAP\x00'),(b,b'SURVIVAL-MAP\xff'),('DIM-1',b'NETHER'),('DIM1',b'END')]:
        (p/folder/'multiworld').mkdir(parents=True)
        (p/folder/'multiworld/cache.bin').write_bytes(marker)
        (p/folder/'multiworld/empty').mkdir()
    (p/'server-config.txt').write_text('untouched settings')
    return p

def rejected(action,label):
    try:action()
    except ValueError:check(True,label);return
    raise AssertionError(label)

def run():
    for kind,primary in [('minimap','dim%0'),('worldmap','null'),('worldmap','DIM0')]:
        p=fixture(kind,primary);a,b,h=names(kind,primary);before=snapshot(p);draft=plan(p,kind,primary)
        check(not draft['blockers'],'valid explicit selector')
        rejected(lambda:apply(p,draft),'write requires stopped-client attestation')
        check(apply(p,draft,True)['committed'],'folder bindings commit')
        check((p/h/'multiworld/cache.bin').read_bytes()==b'HOME-MAP\x00','home map bytes moved intact')
        check((p/a/'multiworld/cache.bin').read_bytes()==b'SURVIVAL-MAP\xff','survival map bytes moved intact')
        check(apply(p,draft,True)['alreadyMigrated'],'idempotent commit')
        check(rollback(p,True)['byteIdenticalOriginalCache'],'exact cache rollback')
        check(snapshot(p)==before,'all empty directories and other dimensions retained')
        check(rollback(p,True)['alreadyRolledBack'],'inert repeated rollback')
        check(apply(p,plan(p,kind,primary),True)['committed'],'retry commits after rollback')
        for index in (0,1):
            for edge in ('intent','rename'):
                p=fixture(kind,primary);before=snapshot(p);draft=plan(p,kind,primary)
                try:apply(p,draft,True,fail_at=(index,edge));raise AssertionError('missing injected apply failure')
                except RuntimeError:pass
                check(rollback(p,True)['byteIdenticalOriginalCache'],'forward interruption recovery')
                check(snapshot(p)==before,'forward byte identity')
                p=fixture(kind,primary);before=snapshot(p);apply(p,plan(p,kind,primary),True)
                try:rollback(p,True,fail_at=(index,edge));raise AssertionError('missing injected rollback failure')
                except RuntimeError:pass
                check(rollback(p,True)['byteIdenticalOriginalCache'],'rollback interruption resumes')
                check(snapshot(p)==before,'rollback byte identity')
        p=fixture(kind,primary);draft=plan(p,kind,primary);(p/h).mkdir()
        check(bool(plan(p,kind,primary)['blockers']),'existing home cache blocks merge')
        rejected(lambda:apply(p,draft,True),'stale target blocked')
        p=fixture(kind,primary);draft=plan(p,kind,primary);(p/a/'new.bin').write_bytes(b'changed')
        rejected(lambda:apply(p,draft,True),'new cache progress invalidates plan')
        p=fixture(kind,primary);apply(p,plan(p,kind,primary),True);(p/h/'new.bin').write_bytes(b'new gameplay')
        rejected(lambda:rollback(p,True),'post-cutover progress protected')
    # A forward intent that never ran must be normalized before an interrupted rollback.
    p=fixture('worldmap','null');before=snapshot(p);draft=plan(p,'worldmap','null')
    try:apply(p,draft,True,fail_at=(1,'intent'))
    except RuntimeError:pass
    try:rollback(p,True,fail_at=(0,'rename'))
    except RuntimeError:pass
    check(rollback(p,True)['byteIdenticalOriginalCache'],'mixed forward and rollback interruption resumes')
    check(snapshot(p)==before,'mixed interruption byte identity')
    p=fixture('worldmap','null');(p/'DIM0').mkdir()
    check(bool(plan(p,'worldmap','null')['blockers']),'both primary folders require conflict resolution')
    rejected(lambda:plan(p,'worldmap'),'worldmap encoding never guessed')
    report={'success':True,'checks':CHECKS,'fixturesOnly':True,'productionMutation':False,'actualUserCachesTouched':False,'scope':'Xaero folder bindings and recovery only'}
    (OUT/'cache-synthetic-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
if __name__=='__main__':run()
