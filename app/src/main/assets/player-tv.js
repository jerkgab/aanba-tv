(function(){
'use strict';
if(window.AanbaTv)return;
let selected=null,menu=false,chromeHidden=false,oldOutline='';

function roots(){const out=[document];for(let i=0;i<out.length;i++)for(const n of out[i].querySelectorAll('*'))if(n.shadowRoot)out.push(n.shadowRoot);return out;}
function all(selector){const out=[];for(const root of roots())for(const node of root.querySelectorAll(selector))out.push(node);return out;}
function chrome(hidden){chromeHidden=hidden;const v=video();if(!v||v.readyState===0)return;for(const root of roots()){let node=root.querySelector('#aanba-chrome');if(!node){node=document.createElement('style');node.id='aanba-chrome';node.textContent='.wrapper-bottom,.wrapper-top,.btn-m--play,.playButton{visibility:hidden!important;pointer-events:none!important}';(root===document?document.head:root).appendChild(node);}node.media=hidden?'all':'not all';}}
function rect(e){return e.getBoundingClientRect();}
function visible(e){const r=rect(e),s=getComputedStyle(e);return r.width>4&&r.height>4&&r.right>0&&r.bottom>0&&r.x<innerWidth&&r.y<innerHeight&&s.display!=='none'&&s.visibility!=='hidden';}
function video(){return all('video').filter(visible).sort((a,b)=>rect(b).width*rect(b).height-rect(a).width*rect(a).height)[0]||all('video')[0];}
function settingsButton(){return all('button,[role=button],[title],[aria-label],[class*=settings]').filter(e=>rect(e).width>4&&rect(e).height>4&&!e.disabled).find(e=>/настрой|settings/i.test((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||''))&&!e.querySelector('button,[role=button]'))||null;}
function reveal(){chrome(false);document.documentElement.classList.remove('aanba-tv-hide-chrome');const target=video()||document;target.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,composed:true,clientX:innerWidth/2,clientY:innerHeight-25}));target.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,composed:true,clientX:innerWidth/2,clientY:innerHeight-25}));chromeHidden=false;}
function candidates(){const items=all('[role=menuitem],[role=menuitemradio],[role=option]').filter(e=>visible(e)&&e.getAttribute('aria-disabled')!=='true');if(items.length)return items;return all('button,[role=button],[tabindex]').filter(e=>visible(e)&&e.getAttribute('aria-disabled')!=='true'&&!e.disabled&&!e.querySelector('button,[role=menuitem],[role=menuitemradio]'));}
function focus(e){if(selected){selected.classList.remove('aanba-tv-focus');selected.style.outline=oldOutline;}selected=e;if(!e)return false;oldOutline=e.style.outline;e.classList.add('aanba-tv-focus');e.style.outline='3px solid white';e.style.outlineOffset='-3px';e.setAttribute('tabindex','0');e.focus({preventScroll:true});e.scrollIntoView({block:'nearest',inline:'nearest'});return true;}
function choose(){const all=candidates();if(selected&&selected.isConnected&&visible(selected))return all;focus(all.find(e=>e.getAttribute('aria-checked')==='true')||all[0]);return all;}
window.AanbaTv={
 state(){if(chromeHidden)chrome(true);const menuOpen=all("[role=menuitem],[role=menuitemradio]").some(visible),v=video();if(!v)return {ready:false,time:0,start:0,end:0,canSeek:false,paused:true,menuOpen};let start=0,end=Number.isFinite(v.duration)?v.duration:0;try{if(v.seekable.length){const first=v.seekable.start(0),last=v.seekable.end(v.seekable.length-1);if(last>first){start=first;end=last;}}}catch(e){}let buffered=0;try{if(v.buffered.length)buffered=v.buffered.end(v.buffered.length-1);}catch(e){}return {buffered,menuOpen,ready:v.readyState>0,time:Number.isFinite(v.currentTime)?v.currentTime:0,start,end,canSeek:end>start,paused:v.paused,muted:v.muted||v.volume===0,width:v.videoWidth,height:v.videoHeight,live:!Number.isFinite(v.duration)};},
 muteToggle(){const v=video();if(!v)return false;v.muted=!v.muted;if(!v.muted&&v.volume===0)v.volume=1;return true;},
 play(){const v=video();if(!v)return false;v.play().catch(()=>{});return true;},
 pause(){const v=video();if(!v)return false;v.pause();return true;},
 toggle(){const v=video();if(!v)return false;v.paused?v.play().catch(()=>{}):v.pause();return true;},
 seekTo(time){const v=video(),s=this.state();if(!v||!s.canSeek)return false;v.currentTime=Math.max(s.start,Math.min(s.end-.05,Number(time)));return true;},
 settings(){reveal();const button=settingsButton();if(!button)return false;button.click();menu=true;selected=null;setTimeout(()=>choose(),180);return true;},
 navigate(direction){reveal();const all=choose();if(!selected||!all.length)return false;const s=rect(selected),sx=s.x+s.width/2,sy=s.y+s.height/2;let best=null,cost=Infinity;for(const e of all){if(e===selected)continue;const r=rect(e),dx=r.x+r.width/2-sx,dy=r.y+r.height/2-sy;let forward=0,side=0;if(direction==='right'){forward=dx;side=Math.abs(dy);}if(direction==='left'){forward=-dx;side=Math.abs(dy);}if(direction==='down'){forward=dy;side=Math.abs(dx);}if(direction==='up'){forward=-dy;side=Math.abs(dx);}if(forward<2)continue;const score=forward+side*3;if(score<cost){cost=score;best=e;}}return best?focus(best):false;},
 activate(){choose();if(!selected)return false;selected.click();selected=null;setTimeout(()=>choose(),180);return true;},
 dismiss(){if(all('[role=menuitem],[role=menuitemradio]').some(visible)){const button=settingsButton();if(button)button.click();}document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true}));if(selected){selected.classList.remove('aanba-tv-focus');selected.style.outline=oldOutline;selected.blur();}selected=null;menu=false;this.hideChrome();return true;},
 focusedLabel(){return selected?(selected.getAttribute('aria-label')||selected.innerText||''):'';},
 hideChrome(){chrome(true);return true;}
};
window.AanbaTv.hideChrome();
})();
