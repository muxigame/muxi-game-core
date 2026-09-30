"""Validate finished disposable stress runs; retain full errors for human triage."""
from pathlib import Path
import argparse,hashlib,json,re
from report_multiplayer_gc import rolling
from thread_load_metrics import summarize_safepoints,measured_windows


def inspect(lab):
    report=json.loads((lab/'load-result.json').read_text())
    assert report['success'] and report['serverExitCode']==0, str(lab)
    assert report['stabilityEnabled'] and report['threaded'] and report['fullPack']
    saved=json.loads((lab/'load-save-result.json').read_text())
    assert saved['success']
    core=next((lab/'mods').glob('muxi-game-core-*.jar'))
    digest=hashlib.sha256(core.read_bytes()).hexdigest()
    assert digest==report['core']['sha256']
    clients=[]
    for role in json.loads((lab/'load-roles.json').read_text()):
        client=json.loads((lab/('client-'+role+'.log')).read_text(encoding='utf-8'))
        assert client['success'] and client['exitCode']==0
        assert all(client[k]>0 for k in ('nativeAttackAttempts','nativePlaceAttempts','nativeBreakAttempts'))
        clients.append(client)
    # Every ERROR line is retained for review; only concrete runtime failure patterns fail automatically.
    findings=[]
    fatal=re.compile(r'Dimension tick barrier failed|Off-owner world mutation|Cross-world synchronous chunk access|Neruina suppressed|ArrayIndexOutOfBoundsException|ConcurrentModificationException|OutOfMemoryError|RejectedExecutionException|Exception ticking|Caught exception ticking|Exception in server tick loop|ReportedException: Ticking')
    logs=[lab/'boot.log',lab/'restart.log',*[Path(c['lab'])/'boot.log' for c in clients]]
    for log in logs:
        lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
        critical=[line for line in lines if fatal.search(line)]
        assert not critical, (str(log),critical[:5])
        assert not list((log.parent/'crash-reports').glob('crash-*.txt'))
        findings.extend({'file':str(log),'line':i+1,'text':line} for i,line in enumerate(lines) if '/ERROR]' in line or 'Exception:' in line)
    safepoints=summarize_safepoints(lab,report)
    ends=dict(measured_windows(report))
    starts=report['phaseStartEpochMillis']
    phases={}
    for phase in ('machines','stability','survival-combat','exploration'):
        if phase+'/server' not in report['timing']:continue
        rows=[r for r in report['tickTimeline'] if r['phase']==phase]
        intervals=report['timing'][phase+'/tickInterval']
        # The first interval starts in the previous phase (arena setup may load cold chunks).
        # Use actual phase boundaries for average throughput; omit that interval from rolling windows.
        elapsed=starts[ends[phase]]-starts[phase]
        phases[phase]={'averageTPS':min(20,len(rows)*1000/elapsed),'phaseSeconds':elapsed/1000,
            'rawIntervalTPSIncludingTransition':min(20,1000/intervals['meanMs']),
            'minimum20TickTPS':min(v[1] for v in rolling(rows[1:])),
            'work':report['timing'][phase+'/server'],'safepoints':safepoints[phase]}
    out={'lab':str(lab),'sha256':digest,'seed':report['seed'],'save':saved,
        'stability':report['stability'],'phases':phases,'clients':clients,'errorsForReview':findings}
    (lab/'stability-audit.json').write_text(json.dumps(out,indent=2))
    return out


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('labs',nargs='+',type=Path);args=p.parse_args()
    for lab in args.labs:
        out=inspect(lab.resolve())
        print(json.dumps({k:v for k,v in out.items() if k not in ('clients','errorsForReview')},indent=2))


if __name__=='__main__':main()
