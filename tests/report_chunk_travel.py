"""Check completed native A/B evidence and plot TPS alongside actual route progress."""
from pathlib import Path
import argparse,json,csv,zipfile
from report_multiplayer_gc import rolling
from thread_load_metrics import summarize_safepoints

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('matrix',type=Path);args=p.parse_args()
    manifest=json.loads((args.matrix/'matrix.json').read_text());runs=[];rows=[];clients={}
    assert [r['label'] for r in manifest['runs']] in (['baseline','travel'],['baseline','travel','retained'])
    for entry in manifest['runs']:
        assert entry['status']=='passed'
        lab=Path(entry['lab']);report=json.loads((lab/'load-result.json').read_text())
        assert report['success'] and report['serverExitCode']==0
        assert json.loads((lab/'load-save-result.json').read_text())['success']
        assert report['nativePlayers']==4 and report['heapGiB']==20 and report['gc']=='g1' and report['threaded']
        assert report['travelDisabled']==(entry['label']=='baseline')
        assert json.loads((lab/'config/spark/config.json').read_text())['backgroundProfiler'] is False
        if runs and report['core']['sha256']!=runs[0][1]['core']['sha256']:
            assert entry['label']=='retained'
            baseline=Path(manifest['runs'][0]['lab'])/'mods'/report['core']['artifact']
            with zipfile.ZipFile(baseline) as a,zipfile.ZipFile(lab/'mods'/report['core']['artifact']) as b:
                assert set(a.namelist())==set(b.namelist())
                changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
                assert changed and all(n.startswith('net/muxigame/core/threading/ChunkTravel') and n.endswith('.class') for n in changed)
            (args.matrix/'retention-class-diff.json').write_text(json.dumps({'baselineSha256':runs[0][1]['core']['sha256'],
                'retentionSha256':report['core']['sha256'],'changedEntries':changed},indent=2))
        runs.append((entry['label'],report));clients[entry['label']]=[]
        for role in ('Home','Survival','Home2','Survival2'):
            client=json.loads((lab/('client-'+role+'.log')).read_text(encoding='utf-8'))
            assert client['success'] and client['exitCode']==0
            assert (Path(client['lab'])/('load-'+role+'.png')).is_file()
            clients[entry['label']].append(client)
        safepoints=summarize_safepoints(lab,report)
        start=report['phaseStartEpochMillis']['exploration']
        arrivals=report['exploration']['firstArrivalEpochMillis']
        for phase in ('machines','exploration'):
            pts=list(rolling([r for r in report['tickTimeline'] if r['phase']==phase]))
            work=report['timing'][phase+'/server'];interval=report['timing'][phase+'/tickInterval']
            row={'configuration':entry['label'],'phase':phase,'averageTPS':min(20,1000/interval['meanMs']),
                'minimum20TickTPS':min(v[1] for v in pts),'meanTickMs':work['meanMs'],'p99TickMs':work['p99Ms'],
                'p99IntervalMs':interval['p99Ms'],'measuredTicks':work['count'],
                **report['resources']['phases'][phase],**report['gcProfile'][phase],**safepoints[phase]}
            row['lastPlayerArrivalSeconds']=(max(arrivals.values())-start)/1000 if phase=='exploration' else None
            row['routeSecondsPerPlayer']={k:(v-start)/1000 for k,v in arrivals.items()} if phase=='exploration' else {}
            rows.append(row)
    (args.matrix/'summary.json').write_text(json.dumps(rows,indent=2))
    (args.matrix/'clients.json').write_text(json.dumps(clients,indent=2))
    with (args.matrix/'summary.csv').open('w',encoding='utf-8-sig',newline='') as output:
        writer=csv.DictWriter(output,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(2,1,figsize=(12,8),layout='constrained')
    colors={'baseline':'#b36b35','travel':'#8c9099','retained':'#16856b'}
    for name,report in runs:
        pts=list(rolling([r for r in report['tickTimeline'] if r['phase']=='exploration']))
        axes[0].plot([p[0] for p in pts],[p[1] for p in pts],label=name,color=colors[name])
        start=report['phaseStartEpochMillis']['exploration']
        progress=report['exploration']['routeProgress']
        for i,player in enumerate(sorted({p['player'] for p in progress})):
            route=[p for p in progress if p['player']==player]
            axes[1].step([(p['epochMillis']-start)/1000 for p in route],[p['x']-.5 for p in route],where='post',
                color=colors[name],linestyle=['-','--',':','-.'][i],label=name+' / '+player[6:])
    axes[0].set_title('Effective TPS, rolling 20 ticks');axes[0].set_ylabel('TPS');axes[0].set_ylim(0,20.8);axes[0].legend()
    axes[1].set_title('Last sampled server position (every 20 ticks; holds last observation across gaps)');axes[1].set_ylabel('Blocks from start');axes[1].legend(ncols=4,fontsize=8)
    for ax in axes:ax.set_xlabel('Seconds since exploration start');ax.grid(alpha=.2)
    fig.suptitle('Four native clients / fresh 384-block routes / same host / G1')
    fig.savefig(args.matrix/'travel-comparison.png',dpi=160);plt.close(fig)
    print(json.dumps(rows,indent=2))
if __name__=='__main__':main()
