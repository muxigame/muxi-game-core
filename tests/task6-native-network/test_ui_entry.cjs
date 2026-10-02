const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(__dirname+'/muxi-terminal/src/main/resources/assets/muxi_terminal/html/terminal/native-map-app.js','utf8');let checks=0;
const check=(ok)=>{assert.ok(ok);checks++;};
class Element{constructor(){this.handlers={};this.disabled=false;}addEventListener(type,fn){this.handlers[type]=fn;}}
const children=[],grid={append(card){children.push(card);}},calls=[],statuses=[];let ready,resolve,reject;
const document={readyState:'loading',addEventListener(type,fn){ready=fn;},querySelector(selector){return selector==='#home .app-grid'?grid:null;},getElementById(id){return children.find(c=>c.id===id)||null;},createElement(){return new Element();}};
const context={document,native:request=>{calls.push(request);return new Promise((r,j)=>{resolve=r;reject=j;});},setStatus:s=>statuses.push(s)};
vm.runInNewContext(source,context);check(children.length===0);ready();check(children.length===1&&children[0].className==='app-card');ready();check(children.length===1);
(async()=>{const card=children[0];check(card.innerHTML.includes('app-name')&&card.innerHTML.includes('传送网络'));
const first=card.handlers.click();check(card.disabled&&calls[0]==='map.open');await card.handlers.click();check(calls.length===1);resolve({ok:true});await first;check(!card.disabled);
const second=card.handlers.click();reject(Error('地图未就绪'));await second;check(!card.disabled&&statuses[0]==='地图未就绪');
check(!source.includes('location.hash')&&!source.includes('KeyMapping')&&!source.includes('teleport:'));
console.log(JSON.stringify({success:true,checks,scope:'production app-card script with synthetic DOM/query; no browser or map runtime'}));
})().catch(e=>{console.error(e);process.exitCode=1;});
