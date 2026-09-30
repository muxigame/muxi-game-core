"""Process resource sampling and bounded JFR GC summaries for isolated native load runs."""
from __future__ import annotations
from datetime import datetime
from pathlib import Path
import json,re,statistics,subprocess,time
import psutil


class ResourceMonitor:
    def __init__(self,pid:int,lab:Path):
        self.server=psutil.Process(pid);self.server.cpu_percent(None)
        self.lab=lab;self.clients={};self.rows=[];self.previous=time.monotonic();self.previous_phase=None
        self.logical_cpus=psutil.cpu_count();psutil.cpu_percent(None)

    def sample(self,client_roots):
        now=time.monotonic();dt=now-self.previous;self.previous=now
        try:phase=(self.lab/'load-phase.txt').read_text().strip()
        except OSError:return
        try:
            cpu=self.server.cpu_percent(None);mem=self.server.memory_info()
        except psutil.Error:return
        client_cpu=0.;client_rss=0
        for root in client_roots:
            try:children=[psutil.Process(root),*psutil.Process(root).children(recursive=True)]
            except psutil.Error:continue
            for child in children:
                try:
                    proc=self.clients.setdefault(child.pid,child)
                    client_cpu+=proc.cpu_percent(None);client_rss+=proc.memory_info().rss
                except psutil.Error:pass
        # A phase transition's CPU interval may include work from the preceding phase.
        stable=phase==self.previous_phase;self.previous_phase=phase
        self.rows.append({'epochMillis':time.time()*1000,'phase':phase,'stablePhase':stable,'intervalSeconds':dt,
            'serverCpuPercentOneCore':cpu,'serverRssGiB':mem.rss/2**30,
            'clientTreeCpuPercentOneCore':client_cpu,'clientTreeRssGiB':client_rss/2**30,
            'hostCpuPercent':psutil.cpu_percent(None)})

    def summary(self):
        out={'hostLogicalProcessors':self.logical_cpus,'phases':{}}
        for phase in ('machines','stability','survival-combat','exploration'):
            rows=[r for r in self.rows if r['phase']==phase and r['stablePhase']]
            if not rows:continue
            elapsed=sum(r['intervalSeconds'] for r in rows)
            mean=lambda key:sum(r[key]*r['intervalSeconds'] for r in rows)/elapsed
            out['phases'][phase]={'samples':len(rows),'sampledSeconds':elapsed,
                'serverCpuCoreEquivalents':mean('serverCpuPercentOneCore')/100,
                'serverCpuSeconds':sum(r['serverCpuPercentOneCore']*r['intervalSeconds']/100 for r in rows),
                'serverRssMeanGiB':mean('serverRssGiB'),'serverRssPeakGiB':max(r['serverRssGiB'] for r in rows),
                'clientsCpuCoreEquivalents':mean('clientTreeCpuPercentOneCore')/100,
                'clientsRssPeakGiB':max(r['clientTreeRssGiB'] for r in rows),'hostCpuMeanPercent':mean('hostCpuPercent')}
        return out

    def save(self):
        (self.lab/'resource-samples.json').write_text(json.dumps(self.rows,indent=2))


def duration_ms(value):
    match=re.fullmatch(r'PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?',value)
    if not match:raise ValueError('Unexpected JFR duration: '+value)
    h,m,s=(float(v or 0) for v in match.groups());return (h*3600+m*60+s)*1000


def measured_windows(report):
    starts=report['phaseStartEpochMillis']
    order=list(starts)
    return [(phase,order[order.index(phase)+1]) for phase in ('machines','stability','survival-combat','exploration')
            if phase in starts and order.index(phase)+1<len(order)]


def summarize_safepoints(lab:Path,report):
    """Include time to reach a safepoint, which GCPhasePause alone does not cover."""
    events=[]
    pattern=re.compile(r'^\[([^]]+)\].*Safepoint "([^"]+)".*Reaching safepoint: (\d+) ns.*Total: (\d+) ns')
    for line in (lab/'gc.log').read_text(encoding='utf-8').splitlines():
        match=pattern.search(line)
        if match:
            stamp,kind,reaching,total=match.groups()
            stamp=re.sub(r'([+-]\d{2})(\d{2})$',r'\1:\2',stamp)
            events.append({'epochMillis':datetime.fromisoformat(stamp).timestamp()*1000,
                'kind':kind,'reachingMs':int(reaching)/1e6,'totalMs':int(total)/1e6})
    starts=report['phaseStartEpochMillis'];out={}
    for phase,end_phase in measured_windows(report):
        rows=[r for r in events if starts[phase]<=r['epochMillis']<starts[end_phase]]
        gc_rows=[r for r in rows if r['kind'].startswith(('ZMark','ZRelocate','G1','GenCollect','ParallelGC'))]
        out[phase]={'safepointCount':len(rows),'safepointTotalMs':sum(r['totalMs'] for r in rows),
            'safepointMaxMs':max((r['totalMs'] for r in rows),default=0),
            'safepointReachMaxMs':max((r['reachingMs'] for r in rows),default=0),
            'gcSafepointMaxMs':max((r['totalMs'] for r in gc_rows),default=0),
            'gcSafepointReachMaxMs':max((r['reachingMs'] for r in gc_rows),default=0),
            'longestSafepointKind':max(rows,key=lambda r:r['totalMs'])['kind'] if rows else None}
    (lab/'safepoint-summary.json').write_text(json.dumps(out,indent=2));return out


def summarize_jfr(java_home:Path,lab:Path,report):
    result=subprocess.run([str(java_home/'bin/jfr.exe'),'print','--json','--events',
        'jdk.GCPhasePause,jdk.ZAllocationStall,jdk.GCHeapSummary',str(lab/'native-load.jfr')],
        capture_output=True,check=True,encoding='utf-8')
    events=json.loads(result.stdout)['recording']['events'];out={}
    starts=report['phaseStartEpochMillis']
    for phase,end_phase in measured_windows(report):
        pauses=[];stalls=[];heap=[]
        for event in events:
            v=event['values'];stamp=re.sub(r'(\.\d{6})\d+',r'\1',v['startTime'])
            t=datetime.fromisoformat(stamp).timestamp()*1000
            if not starts[phase]<=t<starts[end_phase]:continue
            if event['type']=='jdk.GCPhasePause':pauses.append(duration_ms(v['duration']))
            elif event['type']=='jdk.ZAllocationStall':stalls.append(duration_ms(v['duration']))
            elif event['type']=='jdk.GCHeapSummary':heap.append(v['heapUsed']/2**30)
        pauses.sort()
        out[phase]={'gcPauseCount':len(pauses),'gcPauseTotalMs':sum(pauses),'gcPauseMaxMs':max(pauses,default=0),
            'gcPauseP95Ms':pauses[int((len(pauses)-1)*.95)] if pauses else 0,
            'allocationStallCount':len(stalls),'allocationStallMaxMs':max(stalls,default=0),
            'gcObservedHeapPeakGiB':max(heap,default=None)}
    (lab/'gc-summary.json').write_text(json.dumps(out,indent=2));return out
