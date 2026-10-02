/* The map remains Xaero's native screen. No hotkey override or embedded renderer. */
(() => {
  function install(){
    const grid=document.querySelector('#home .app-grid');if(!grid||document.getElementById('nativeMapApp'))return;
    const card=document.createElement('button');card.type='button';card.className='app-card';card.id='nativeMapApp';
    card.innerHTML='<span class="app-icon guide-icon" aria-hidden="true"><i></i><i></i><i></i></span><span class="app-name">传送网络</span><span class="app-desc">原生地图 · 石碑网络</span>';
    card.addEventListener('click',async()=>{if(card.disabled)return;card.disabled=true;
      try{await native('map.open');}catch(error){setStatus(error.message||'地图暂时不可用');}
      finally{card.disabled=false;}
    });grid.append(card);
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install);else install();
})();
