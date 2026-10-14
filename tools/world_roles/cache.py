"""One explicitly selected Xaero server-cache root; payloads are never rewritten or merged."""
from pathlib import Path
from contextlib import contextmanager
import hashlib,json,os,time
from .transaction import receipt
JOURNAL='.muxi-xaero-world-roles'

def root_path(path):
    path=Path(path)
    if path.is_symlink() or path.is_junction():raise ValueError('Linked cache root unsupported')
    return path.resolve(strict=True)

def snapshot(root):
    result={}
    for p in sorted(root.rglob('*')):
        rel=p.relative_to(root).as_posix()
        if p.is_symlink() or p.is_junction():raise ValueError('Linked cache entry unsupported')
        if rel.split('/')[0]==JOURNAL:continue
        if p.is_dir():result[rel]=None
        elif p.is_file():
            if p.stat().st_nlink>1:raise ValueError('Hardlinked cache payload unsupported')
            h=hashlib.sha256()
            with p.open('rb') as f:
                for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
            result[rel]=h.hexdigest()
        else:raise ValueError('Nonregular cache entry unsupported')
    return result

def names(kind,primary):
    if kind=='minimap':
        if primary not in (None,'dim%0'):raise ValueError('Minimap primary folder must be dim%0')
        return 'dim%0','dim%muxi_game_core$overworld','dim%muxi_game_core$homeworld'
    if kind=='worldmap' and primary in ('null','DIM0'):
        return primary,'muxi_game_core$overworld','muxi_game_core$homeworld'
    raise ValueError('Worldmap requires the actual primary folder null or DIM0; never guessed')

def moved(state,source,target):
    result={}
    for key,value in state.items():
        new=target+key[len(source):] if key==source or key.startswith(source+'/') else key
        if new in result:raise ValueError('Cache target collision')
        result[new]=value
    return result

def plan(path,kind,primary=None):
    root=root_path(path);a,b,h=names(kind,primary);before=snapshot(root);blockers=[]
    journal=root/JOURNAL/'journal.json'
    if journal.exists():
        old=json.loads(journal.read_text(encoding='utf-8'))
        if old['phase']=='committed':
            if old['kind']!=kind or old['primary']!=a or before!=old['afterSnapshot']:raise ValueError('Committed cache has changed or selector differs')
            return {'alreadyMigrated':True,'root':str(root),'kind':kind,'primary':a,'blockers':[]}
        if old['phase']!='rolled_back':blockers.append('Incomplete cache transaction: rollback first')
    if h in before:blockers.append('Existing homeworld cache is never merged or overwritten')
    if kind=='worldmap' and ('DIM0' if a=='null' else 'null') in before:blockers.append('Alternate primary cache folder exists; resolve actual history first')
    for n in (a,b):
        if n in before and before[n] is not None:blockers.append('Dimension binding is a file, not a folder: '+n)
    # Nested DIM0/dim%0 is a wrong server-root selector, not an invitation to recurse.
    if not any(n in before for n in (a,b)):blockers.append('No old dimension folders at selected server root')
    moves=[{'source':source,'target':target,'state':'planned'} for source,target in [(a,h),(b,a)] if source in before]
    return {'schema':1,'root':str(root),'kind':kind,'primary':a,'beforeSnapshot':before,'moves':moves,'blockers':blockers,'payloadsRewritten':False,'scope':'audited dimension folder bindings only; external settings excluded'}

@contextmanager
def locked(root):
    directory=root/JOURNAL;directory.mkdir(exist_ok=True)
    p=directory/'lock'
    with p.open('a+b') as f:
        if not p.stat().st_size:f.write(b'LOCK');f.flush()
        f.seek(0)
        try:
            if os.name=='nt':
                import msvcrt;msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
            else:
                import fcntl;fcntl.lockf(f.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        except OSError as e:raise ValueError('Another cache transaction is active') from e
        try:yield
        finally:
            f.seek(0)
            if os.name=='nt':msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
            else:fcntl.lockf(f.fileno(),fcntl.LOCK_UN)

def apply(path,draft,clients_stopped=False,fail_at=None):
    if not clients_stopped:raise ValueError('All clients using this selected cache must be stopped')
    root=root_path(path)
    with locked(root):
        fresh=plan(root,draft['kind'],draft['primary'])
        if fresh.get('alreadyMigrated'):
            if draft.get('root')!=str(root):raise ValueError('Cache plan belongs to another root')
            return {'alreadyMigrated':True}
        if fresh['blockers']:raise ValueError(str(fresh['blockers']))
        if fresh!=draft:raise ValueError('Cache or selector changed after plan')
        jp=root/JOURNAL/'journal.json'
        if jp.exists():os.rename(jp,jp.with_name('history-'+str(time.time_ns())+'.json'))
        j={**draft,'phase':'applying'};receipt(jp,j)
        for i,move in enumerate(j['moves']):
            move['state']='intent';receipt(jp,j)
            if fail_at==(i,'intent'):raise RuntimeError('Injected cache interruption')
            os.rename(root/move['source'],root/move['target'])
            if fail_at==(i,'rename'):raise RuntimeError('Injected cache interruption')
            move['state']='applied';receipt(jp,j)
        j['afterSnapshot']=snapshot(root);j['phase']='committed';receipt(jp,j)
        return {'committed':True,'directoryMoves':len(j['moves']),'payloadsRewritten':False}

def rollback(path,clients_stopped=False,fail_at=None):
    if not clients_stopped:raise ValueError('All clients using this selected cache must be stopped')
    root=root_path(path)
    with locked(root):
        jp=root/JOURNAL/'journal.json';j=json.loads(jp.read_text(encoding='utf-8'))
        if j['root']!=str(root):raise ValueError('Cache journal belongs to another root')
        if j['phase']=='rolled_back':return {'alreadyRolledBack':True}
        a,b,h=names(j['kind'],j['primary']);allowed=[(a,h),(b,a)]
        if [(m['source'],m['target']) for m in j['moves']] != [(s,t) for s,t in allowed if s in j['beforeSnapshot']]:raise ValueError('Unexpected journal moves')
        expected=j['beforeSnapshot'];states=[]
        for m in j['moves']:
            state=m['state']
            if state=='intent':
                source=root/m['source'];target=root/m['target']
                if source.exists() and not target.exists():state='planned'
                elif not source.exists() and target.exists():state='applied'
                else:raise ValueError('Ambiguous interrupted cache rename')
            if state=='reverting':
                source=root/m['source'];target=root/m['target']
                if source.exists() and not target.exists():state='reverted'
                elif not source.exists() and target.exists():state='applied'
                else:raise ValueError('Ambiguous interrupted cache rollback')
            if state=='applied':expected=moved(expected,m['source'],m['target'])
            elif state not in ('planned','reverted'):raise ValueError('Unexpected cache move state')
            states.append(state)
        if snapshot(root)!=expected:raise ValueError('Cache changed after cutover; automatic rollback refused')
        for m,state in zip(j['moves'],states):m['state']=state
        j['phase']='rolling_back';receipt(jp,j)
        for m,state in reversed(list(zip(j['moves'],states))):
            if state=='applied':
                m['state']='reverting';receipt(jp,j)
                index=j['moves'].index(m)
                if fail_at==(index,'intent'):raise RuntimeError('Injected cache rollback interruption')
                os.rename(root/m['target'],root/m['source'])
                if fail_at==(index,'rename'):raise RuntimeError('Injected cache rollback interruption')
                m['state']='reverted';receipt(jp,j)
        if snapshot(root)!=j['beforeSnapshot']:raise ValueError('Cache rollback did not restore original bytes')
        j['phase']='rolled_back';receipt(jp,j)
        return {'byteIdenticalOriginalCache':True}
