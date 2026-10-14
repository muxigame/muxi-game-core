"""Plan one selected Xaero server-cache directory. Write commands require stopped clients and authorization."""
import argparse,json
from pathlib import Path
from world_roles.cache import plan,apply,rollback

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=['plan','apply','rollback']);p.add_argument('--cache-root',type=Path,required=True)
    p.add_argument('--kind',choices=['minimap','worldmap']);p.add_argument('--primary-folder',choices=['dim%0','null','DIM0'])
    p.add_argument('--plan-file',type=Path,required=True);p.add_argument('--authorize-offline-write',action='store_true');p.add_argument('--clients-stopped',action='store_true')
    a=p.parse_args()
    if a.command=='plan':
        if not a.kind:p.error('--kind required for plan')
        result=plan(a.cache_root,a.kind,a.primary_folder);a.plan_file.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    else:
        if not a.authorize_offline_write or not a.clients_stopped:p.error('Write requires explicit authorization and stopped clients')
        result=apply(a.cache_root,json.loads(a.plan_file.read_text(encoding='utf-8')),True) if a.command=='apply' else rollback(a.cache_root,True)
    print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
