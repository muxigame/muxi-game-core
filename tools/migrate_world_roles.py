"""Default command is plan. Never run apply on production without the separate cutover authorization."""
from pathlib import Path
import argparse,json,sys
from world_roles.transaction import plan,apply,rollback,offline

def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('command',choices=['plan','apply','rollback']);ap.add_argument('--world',type=Path,required=True);ap.add_argument('--plan-file',type=Path,required=True);ap.add_argument('--main-spawn',nargs=3,type=int);ap.add_argument('--portal-hub',choices=['home','survival']);ap.add_argument('--data-policy',type=Path);ap.add_argument('--sable-jar',type=Path);ap.add_argument('--config-root',type=Path);ap.add_argument('--config-policy',type=Path);ap.add_argument('--authorize-offline-write',action='store_true');args=ap.parse_args()
 if args.command=='plan':
  with offline(args.world.resolve(strict=True)):result=plan(args.world,args.main_spawn,args.portal_hub,json.loads(args.data_policy.read_text(encoding='utf-8')) if args.data_policy else None,args.sable_jar,args.config_root,json.loads(args.config_policy.read_text(encoding='utf-8')) if args.config_policy else None)
  args.plan_file.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in result.items() if k not in ['operations','moves','beforeSnapshot']},ensure_ascii=False));return
 if not args.authorize_offline_write:raise ValueError('Explicit offline-write authorization required; production is not authorized by plan')
 if args.command=='apply':result=apply(args.world,json.loads(args.plan_file.read_text(encoding='utf-8')))
 else:result=rollback(args.world)
 print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
