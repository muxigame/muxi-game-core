"""Produce reproducible metrics and a TPS time-series plot from completed native GC runs."""
from pathlib import Path
import argparse,csv,json,statistics
from thread_load_metrics import summarize_safepoints


def rolling(rows,size=20):
    for i in range(size-1,len(rows)):
        window=rows[i-size+1:i+1]
        elapsed=sum(r['intervalMs'] for r in window)
        yield ((rows[i]['epochMillis']-rows[0]['epochMillis'])/1000,
            min(20,1000*size/elapsed),statistics.mean(r['workMs'] for r in window))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('matrix',type=Path);args=parser.parse_args()
    manifest=json.loads((args.matrix/'matrix.json').read_text());metrics=[];runs=[]
    expected={'serial-g1','parallel-g1','serial-zgc','parallel-zgc'}
    if len(manifest['runs'])!=4 or {r['label'] for r in manifest['runs']}!=expected:
        raise SystemExit('Expected one completed run for each architecture/collector combination')
    for entry in manifest['runs']:
        if entry['status']!='passed':raise SystemExit('Unfinished/failed matrix entry: '+entry['label'])
        lab=Path(entry['lab']);report=json.loads((lab/'load-result.json').read_text())
        spark=json.loads((lab/'config/spark/config.json').read_text())
        if spark.get('backgroundProfiler') is not False:
            raise SystemExit('Spark background sampler must be disabled in the final comparison: '+str(lab))
        if not report['success'] or not json.loads((lab/'load-save-result.json').read_text())['success']:
            raise SystemExit('Functional or restart check failed: '+str(lab))
        if report['nativePlayers']!=manifest['players'] or report['heapGiB']!=manifest['heapGiB']:
            raise SystemExit('Matrix settings mismatch: '+str(lab))
        if runs and report['core']['sha256']!=runs[0][1]['core']['sha256']:
            raise SystemExit('Refusing to compare different core builds')
        runs.append((entry['label'],report))
        safepoints=summarize_safepoints(lab,report)
        for phase in ('machines','exploration'):
            timing=report['timing'][phase+'/server'];interval=report['timing'][phase+'/tickInterval']
            resource=report['resources']['phases'][phase];gc=report['gcProfile'][phase]
            points=list(rolling([r for r in report['tickTimeline'] if r['phase']==phase]))
            metrics.append({'configuration':entry['label'],'phase':phase,'players':report['nativePlayers'],
                'averageTPS':min(20,1000/interval['meanMs']),'minimum20TickTPS':min(t[1] for t in points),
                'meanTickMs':timing['meanMs'],'p95TickMs':timing['p95Ms'],'p99TickMs':timing['p99Ms'],
                'maxTickMs':timing['maxMs'],'tickOver50ms':timing['over50ms'],'tickOver100ms':timing['over100ms'],
                'p99TickIntervalMs':interval['p99Ms'],**resource,**gc,**safepoints[phase]})
    (args.matrix/'summary.json').write_text(json.dumps(metrics,indent=2))
    with (args.matrix/'summary.csv').open('w',newline='',encoding='utf-8-sig') as file:
        writer=csv.DictWriter(file,fieldnames=list(metrics[0]));writer.writeheader();writer.writerows(metrics)
    lines=['# Native multiplayer measurements','',
        'Core SHA-256: `'+runs[0][1]['core']['sha256']+'`','',
        'One run per configuration; same-host clients contend for CPU. CPU = mean core equivalents.',
        'Tick work excludes work between tick events. Minimum TPS uses a rolling 20-tick window.','']
    for phase in ('machines','exploration'):
        lines += ['## '+phase,'',
            '| Configuration | Avg TPS | Min TPS | Mean tick ms | P99 tick ms | Server CPU | Clients CPU | Host CPU % | Max GC phase ms | Max GC safepoint ms | Z allocation stalls |',
            '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
        for m in sorted((m for m in metrics if m['phase']==phase),key=lambda m:m['configuration']):
            lines.append('| '+m['configuration']+' | '+' | '.join(f'{m[k]:.3f}' for k in (
                'averageTPS','minimum20TickTPS','meanTickMs','p99TickMs','serverCpuCoreEquivalents',
                'clientsCpuCoreEquivalents','hostCpuMeanPercent','gcPauseMaxMs','gcSafepointMaxMs'))+f" | {m['allocationStallCount']} |")
        lines.append('')
    lines += ['![TPS](tps-comparison.png)','![CPU and RSS](total-load.png)','', '## Raw evidence','']
    lines += ['- '+e['label']+': `'+e['lab']+'`' for e in manifest['runs']]
    (args.matrix/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    plt.rcParams.update({'font.size':10,'axes.spines.top':False,'axes.spines.right':False})
    fig,axes=plt.subplots(2,2,figsize=(13,8),layout='constrained')
    colors={'serial-g1':'#7c8390','parallel-g1':'#247ba0','serial-zgc':'#b56d35','parallel-zgc':'#16856b'}
    for name,report in runs:
        for column,phase in enumerate(('machines','exploration')):
            pts=list(rolling([r for r in report['tickTimeline'] if r['phase']==phase]))
            x,tps,work=zip(*pts)
            axes[0,column].plot(x,tps,label=name,color=colors[name],linewidth=1.4)
            axes[1,column].plot(x,work,label=name,color=colors[name],linewidth=1.4)
    for column,phase in enumerate(('Machines','Exploration')):
        axes[0,column].set_title(phase+' / effective TPS');axes[0,column].set_ylim(bottom=0,top=20.7)
        axes[1,column].set_title(phase+' / mean tick work');axes[1,column].set_ylim(bottom=0)
        axes[0,column].set_ylabel('TPS (20-tick rolling window, capped at 20)')
        axes[1,column].set_ylabel('Milliseconds (20-tick rolling mean)')
        for row in range(2):axes[row,column].set_xlabel('Seconds since phase start');axes[row,column].grid(alpha=.2)
    axes[0,0].legend(loc='lower left',ncols=2)
    fig.suptitle(f"{manifest['players']} native clients, two dimensions, 2,048-block lanes | G1 vs generational ZGC",fontsize=14)
    fig.savefig(args.matrix/'tps-comparison.png',dpi=160)
    plt.close(fig)
    fig,axes=plt.subplots(1,2,figsize=(13,5),layout='constrained')
    ordered=sorted(metrics,key=lambda m:(m['phase'],m['configuration']))
    labels=[m['configuration']+'\n'+m['phase'] for m in ordered]
    positions=list(range(len(ordered)))
    server_cpu=[m['serverCpuCoreEquivalents'] for m in ordered]
    client_cpu=[m['clientsCpuCoreEquivalents'] for m in ordered]
    axes[0].bar(positions,server_cpu,label='Server JVM',color='#247ba0')
    axes[0].bar(positions,client_cpu,bottom=server_cpu,label='Four client process trees',color='#a8c9d8')
    host_cpus=runs[0][1]['resources']['hostLogicalProcessors']
    axes[0].scatter(positions,[m['hostCpuMeanPercent']*host_cpus/100 for m in ordered],
        color='#b56d35',marker='D',label='Whole host (incl. other apps)',zorder=3)
    axes[0].axhline(host_cpus,color='#888888',linestyle='--',linewidth=1)
    axes[0].set_ylabel('Mean CPU core equivalents (1 = one fully busy CPU)')
    axes[0].set_title('Total CPU demand during measured phases')
    axes[0].legend(fontsize=8)
    axes[1].bar(positions,[m['serverRssMeanGiB'] for m in ordered],color='#247ba0')
    axes[1].set_title('Server resident memory (includes native memory)')
    axes[1].set_ylabel('Mean RSS / working set (GiB)')
    for ax in axes:
        ax.set_xticks(positions,labels,rotation=45,ha='right',fontsize=8)
        ax.grid(axis='y',alpha=.2);ax.set_axisbelow(True)
    fig.suptitle('Same-host native multiplayer load; JVM processor count is not CPU affinity',fontsize=13)
    fig.savefig(args.matrix/'total-load.png',dpi=160)
    print(str(args.matrix/'summary.csv'));print(str(args.matrix/'tps-comparison.png'))


if __name__=='__main__':main()
