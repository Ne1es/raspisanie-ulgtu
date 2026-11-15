if(!Element.prototype.replaceChildren)Element.prototype.replaceChildren=function(...nodes){while(this.firstChild)this.removeChild(this.firstChild);this.append(...nodes)};
const $=id=>document.getElementById(id),NS='http://www.w3.org/2000/svg',params=new URLSearchParams(location.search);
if(params.get('theme')==='dark')document.documentElement.classList.add('dark');
const accent=params.get('accent');if(accent&&/^[0-9a-f]{6}$/i.test(accent))document.documentElement.style.setProperty('--primary','#'+accent);
const reduceMotion=params.get('motion')==='0'||window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;if(reduceMotion)document.documentElement.classList.add('reduce-motion');

let planDecor,viewScale=0,labelFrame=0,viewFrame=0,planFont=16;
const roomIndex=new Map();
const catalogue=Object.entries(MAPS).flatMap(([plan,p])=>p.rooms.flatMap(room=>{
 const key=plan+'/'+room.id;
 if(roomIndex.has(key))return [];
 const record={...room,plan};roomIndex.set(key,record);return [record];
}));
let anchor=null,marking=false,positionMode=false,firstFix=false,rotation=0,planBounds=null;
let current='c1-f1',selectedRoom=null,targetBuilding=null,gps=null,geoContext={building:null,floor:null,state:'waiting'},tracker=new CampusGeo.Tracker(),preview=false,view=null,follow=true,navigating=false,cityRoute=null,routeLine=null,routeHalo=null,person=null,accuracyCircle=null,routeController=null,routeSerial=0,routeInFlight=false,lastRequest=0,lastRouteFix=null,toastTimer,lastStale=false,gpsIssue="waiting",indoorPath=null,indoorFocusKey=null,routeRenderedKey=null,lastCameraFix=null;
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,4000)}
function native(action,fields={}){let event=new CustomEvent('app-action',{detail:{action,...fields},cancelable:true});if(!window.dispatchEvent(event))return;if(params.get('app')==='android')location.href='ulstuapp://'+action+'?'+new URLSearchParams(fields);else if(action==='location'||action==='precise')toast('Геолокация доступна в приложении Android')}
function svgElement(tag,attrs={}){let e=document.createElementNS(NS,tag);for(let [k,v]of Object.entries(attrs))e.setAttribute(k,v);return e}
function roomElement(id){return [...$('map').querySelectorAll('[data-id]')].find(e=>e.dataset.id===id)}
const cityMap=L.map('cityMap',{zoomControl:false,attributionControl:true,preferCanvas:true,zoomAnimation:!reduceMotion,fadeAnimation:false,zoomSnap:1,zoomDelta:1,maxZoom:20}).setView([54.337,48.389],13);
cityMap.attributionControl.setPrefix(false);cityMap.attributionControl.addAttribution('© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · <a href="https://routing.openstreetmap.de/about.html">FOSSGIS</a>');
const monochrome=document.documentElement.classList.contains('dark');
const cityLayer=CityLayer.attach(cityMap);
cityMap.on('dragstart zoomstart',()=>follow=false);cityMap.on('click',()=>{$('search').blur();$('results').hidden=true});$('mapStage').addEventListener('pointerdown',()=>{if(document.activeElement===$('search'))$('search').blur()});
const campusMarkers={}, campusClusters=[];
const mappedBuildings=Object.entries(CAMPUS).filter(([,b])=>b.point);
function pin(ids,offset=[0,0]){
 const labels=ids.join(' · '),approximate=ids.some(id=>CAMPUS[id].approximate);
 const size=ids.length===1?38:24+ids.length*23;
 const [dx,dy]=offset;
 const leader=dx||dy?'<svg class="pin-leader" viewBox="0 0 38 38" aria-hidden="true"><line x1="19" y1="19" x2="'+(19-dx)+'" y2="'+(19-dy)+'"/><circle cx="'+(19-dx)+'" cy="'+(19-dy)+'" r="2.5"/></svg>':'';
 return L.divIcon({className:'',html:leader+'<span class="campus-pin'+(ids.length>1?' campus-cluster':'')+(approximate?' approximate-pin':'')+(ids.includes(targetBuilding)?' destination-pin':'')+'" title="'+(approximate?'Примерное положение · ':'')+'Корпус '+labels+'">'+labels+(approximate?' <small>≈</small>':'')+'</span>',iconSize:[size,38],iconAnchor:[size/2-dx,19-dy]});
}
for(const [id,b] of mappedBuildings){
 for(const footprint of b.footprints)L.polygon([footprint.outer,...footprint.holes],{color:monochrome?'#d9b9ff':'#724d9e',weight:2,fillColor:monochrome?'#a680dc':'#aa84d0',fillOpacity:.42}).addTo(cityMap);
 campusMarkers[id]=L.marker(b.point,{icon:pin([id]),title:b.name+(b.approximate?' · Примерное положение':''),zIndexOffset:100}).on('click',()=>{chooseBuilding(id);bouncePin(id)});
}
const activeMotion=new WeakMap();
let initialFrame=true;
function motion(element,keyframes,duration=210){
 if(!element?.animate)return;
 activeMotion.get(element)?.cancel();
 if(!initialFrame&&!reduceMotion)activeMotion.set(element,element.animate(keyframes,{duration,easing:'cubic-bezier(.2,0,0,1)'}));
}
function bouncePin(id){motion(campusMarkers[id]?.getElement?.()?.querySelector('.campus-pin'),[
 {transform:'scale(.91)'},{transform:'scale(1.12)',offset:.57},{transform:'scale(1)'}],250);}
function overviewPadding(){
 return {padding:[34,36]};
}
function showAllBuildings(){
 positionMode=false;targetBuilding=null;selectedRoom=null;navigating=false;clearCityRoute();
 follow=false;preview=true;showView('city');setExpanded(false);
 cityMap.fitBounds(mappedBuildings.map(([,b])=>b.point),{...overviewPadding(),maxZoom:16,animate:false});
}
$('allBuildings').onclick=showAllBuildings;
$('cityZoomIn').onclick=()=>cityMap.zoomIn();
$('cityZoomOut').onclick=()=>cityMap.zoomOut();
function updateCampusPins(){
 for(const marker of [...Object.values(campusMarkers),...campusClusters])if(cityMap.hasLayer(marker))cityMap.removeLayer(marker);
 campusClusters.length=0;
 const groups=[],zoom=cityMap.getZoom();
 for(const [id,b] of mappedBuildings){
  const point=cityMap.latLngToContainerPoint(b.point);
  const group=zoom<16?groups.find(g=>Math.hypot(g.point.x-point.x,g.point.y-point.y)<48):null;
  if(group)group.ids.push(id);else groups.push({point,ids:[id]});
 }
 const fourth=cityMap.latLngToContainerPoint(CAMPUS['4'].point);
 const fifth=cityMap.latLngToContainerPoint(CAMPUS['5'].point);
 const gap=Math.hypot(fourth.x-fifth.x,fourth.y-fifth.y);
 const crowded=zoom>=16&&gap<64;
 const shift=crowded&&gap>0?[(fourth.x-fifth.x)*18/gap,(fourth.y-fifth.y)*18/gap]:[-18,0];
 for(const {ids} of groups){
  if(ids.length===1){
   const id=ids[0],offset=crowded?(id==='4'?shift:id==='5'?shift.map(value=>-value):undefined):undefined;
   campusMarkers[id].setIcon(pin(ids,offset)).addTo(cityMap);continue;
  }
  const points=ids.map(id=>CAMPUS[id].point);
  const center=[0,1].map(axis=>points.reduce((sum,point)=>sum+point[axis],0)/points.length);
  const marker=L.marker(center,{icon:pin(ids),title:'Корпуса '+ids.join(', ')}).addTo(cityMap);
  marker.on('click',()=>{
   follow=false;
   if(cityMap.getZoom()<16){cityMap.fitBounds(points,{...overviewPadding(),maxZoom:16,animate:false});if(cityMap.getZoom()<16)cityMap.setView(center,16,{animate:false});return;}
   $('results').replaceChildren();
   for(const id of ids){const button=document.createElement('button');button.textContent=CAMPUS[id].name+(CAMPUS[id].approximate?' · Примерное положение':'');button.onclick=()=>chooseBuilding(id);$('results').append(button);}
   $('results').hidden=false;
  });
  campusClusters.push(marker);
 }
}
cityMap.on('zoomend',updateCampusPins);updateCampusPins();
let resizeFrame=0;
function adjustMap(){if(document.body.dataset.mode==='city')cityMap.invalidateSize({pan:false});if(view){if(rotation)orientation(rotation);setView()}}
function queueAdjust(){if(!resizeFrame)resizeFrame=requestAnimationFrame(()=>{resizeFrame=0;adjustMap()})}
if(typeof ResizeObserver==='function'){
 const observer=new ResizeObserver(queueAdjust);
 for(const id of ['cityStage','mapStage'])observer.observe($(id));
}
window.addEventListener('resize',queueAdjust);
function setExpanded(on){$('planExtra').hidden=!on;$('sheetHandle').setAttribute('aria-expanded',String(on));queueAdjust()}
$('sheetHandle').onclick=()=>setExpanded($('planExtra').hidden);
function hidePlanSearch(){
 delete document.body.dataset.search;
 $('search').blur();
 $('results').hidden=true;
 if(document.body.dataset.mode==='indoor')$('searchRow').hidden=true;
}
function showSearch(){
 document.body.dataset.search='open';
 $('search').value='';
 $('searchRow').hidden=false;
 $('search').focus();
 searchResults();
}
$('openSearch').onclick=showSearch;$('planSearch').onclick=showSearch;
$('planSources').onclick=()=>native('sources');
function showView(kind){
 const indoor=kind==='indoor',previous=document.body.dataset.mode,changed=previous!==kind;
 if(changed)hidePlanSearch();
 document.body.dataset.mode=kind;
 for(const id of ['indoorView','indoorControls','openSearch','context','floorSubtitle','moreRoute','planCard'])$(id).hidden=!indoor;
 for(const id of ['cityStage','allBuildings','locate'])$(id).hidden=indoor;
 $('cityHeading').hidden=indoor;
 $('cityCard').hidden=indoor||!targetBuilding;
 $('sheet').hidden=!indoor&&!targetBuilding;
 $('searchRow').hidden=indoor;
 $('results').hidden=true;
 $('positionNotice').hidden=!indoor||!positionMode;
 $('fitRoute').hidden=indoor||!routeLine;
 cityLayer.update();
 if(changed){
  setExpanded(false);setRouteMenu(false);if(!indoor)marking=false;
  if(previous)motion(indoor?$('indoorView'):$('cityStage'),[{opacity:.84,transform:'translateY(3px)'},{opacity:1,transform:'translateY(0)'}]);
 }
 if(!$('sheet').hidden&&!$('sheet').classList.contains('sheet-open')){
  $('sheet').classList.add('sheet-open');
  motion($('sheet'),[{opacity:.82,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],210);
 }else if($('sheet').hidden)$('sheet').classList.remove('sheet-open');
 updateRouteMenu();queueAdjust();
}
function fitCurrentRoute(){if(!routeLine)return;cityMap.fitBounds(routeLine.getBounds(),{padding:[30,30],maxZoom:17,animate:!reduceMotion});follow=false}
$('fitRoute').onclick=fitCurrentRoute;
function setView(){
 if(viewFrame)return;
 viewFrame=requestAnimationFrame(()=>{
 viewFrame=0;
 const svg=$('map').querySelector('svg');
 if(!svg||!view)return;
 svg.setAttribute('viewBox',[view.x,view.y,view.w,view.h].join(' '));
 if(labelFrame)return;
 labelFrame=requestAnimationFrame(()=>{
  labelFrame=0;
  const currentSvg=$('map').querySelector('svg');
  if(!currentSvg||!view||!planDecor)return;
  const scale=Math.min((rotation?currentSvg.clientHeight:currentSvg.clientWidth)/view.w,(rotation?currentSvg.clientWidth:currentSvg.clientHeight)/view.h);
  if(!scale||Math.abs(viewScale-scale)<.0004)return;
  viewScale=scale;
  for(const label of planDecor.labels){
   const pixels=Math.max(Number(label.dataset.originalSize)*scale,Math.max(10,planFont*.625));
   const fits=!label.dataset.roomWidth||Number(label.dataset.roomWidth)*scale>=pixels*Number(label.dataset.labelLength)*.57;
   label.style.visibility=fits?'visible':'hidden';
   if(fits){label.style.fontSize=(pixels/scale)+'px';label.style.strokeWidth=(2/scale)+'px';}
  }
  const iconScale=(rotation?'rotate(-90) ':'')+'scale('+1/scale+')';
  for(const marker of currentSvg.querySelectorAll('.plan-icon-size'))marker.setAttribute('transform',iconScale);
 });
 });
}
function fit(){
 const svg=$('map').querySelector('svg');if(!svg)return;
 const bounds=planBounds||PlanStyle.fit(svg,MAPS[current]);
 view={...bounds};setView();
}
function orientation(force){
 const map=$('map'),svg=map.querySelector('svg');if(!svg||!planBounds)return;
 const ratio=planBounds.w/planBounds.h;
 rotation=force??(ratio>2.8&&map.clientHeight>map.clientWidth*1.25?1:0);
 svg.style.width=rotation?map.clientHeight+'px':'100%';
 svg.style.height=rotation?map.clientWidth+'px':'100%';
 svg.style.rotate=rotation?'90deg':'none';
 viewScale=0;
}
function rotatePlan(){orientation(rotation?0:1);fit();setRouteMenu(false)}
$('rotatePlan').onclick=rotatePlan;$('rotateMenu').onclick=rotatePlan;
function zoom(factor,point){
 if(!view)return;
 const before={...view},plan=MAPS[current];
 const center=point||{x:before.x+before.w/2,y:before.y+before.h/2};
 const width=Math.max(planBounds.w/8,Math.min(plan.width*1.5,before.w*factor));
 const ratio=width/before.w;
 view={x:center.x-(center.x-before.x)*ratio,y:center.y-(center.y-before.y)*ratio,w:width,h:before.h*ratio};
 setView();
}
let draggedAt=0;
function showPlan(plan){if(!MAPS[plan])return;
 if(current===plan&&$('map').firstElementChild){renderIndoorRoute();return;}
 const previous=MAPS[current],changed=current!==plan;if(changed)marking=false;current=plan;indoorFocusKey=null;routeRenderedKey=null;let p=MAPS[plan];$('map').innerHTML=p.svg;
 const svg=$('map').querySelector('svg');
 planBounds=PlanStyle.fit(svg,p);orientation();viewScale=0;planFont=parseFloat(getComputedStyle(document.documentElement).fontSize)||16;planDecor=PlanStyle.decorate(svg,p);
 if(changed&&document.body.dataset.mode==='indoor'){
  const otherBuilding=previous.building!==p.building;
  motion(svg,[{opacity:.78,transform:otherBuilding?'translateX(8px)':'translateY(4px)'},{opacity:1,transform:'translate(0,0)'}]);
 }
 $('context').textContent='План этажа';$('floorSubtitle').textContent='Корпус '+p.building+' · этаж '+p.floor;$('sheetHandle').replaceChildren(...levelText('Корпус',p));$('buildingUnits').textContent=CAMPUS[p.building].units||'Подразделения не указаны';for(let b of $('buildings').children)b.classList.toggle('active',b.dataset.building===p.building);$('floors').replaceChildren();for(let k of Object.keys(MAPS).filter(k=>MAPS[k].building===p.building).sort((a,b)=>parseFloat(MAPS[a].floor)-parseFloat(MAPS[b].floor)||a.length-b.length)){let b=document.createElement('button');b.textContent=MAPS[k].floor;b.className=k===plan?'active':'';b.onclick=()=>{if(positionMode)NavigatorStorage.write('floor_'+p.building,k);native('tick');preview=!geoContext.building||geoContext.building!==MAPS[k].building;showPlan(k);showView('indoor');renderIndoorRoute()};$('floors').append(b)}let active=$('floors').querySelector('.active');if(active)$('floors').scrollTop=Math.max(0,active.offsetTop-$('floors').offsetTop-($('floors').clientHeight-active.offsetHeight)/2);
 for(let e of $('map').querySelectorAll('.room')){e.onclick=()=>{if(marking||Date.now()-draggedAt<250)return;let r=roomIndex.get(plan+'/'+e.dataset.id);if(r)selectRoom(r)};e.onkeydown=ev=>{if(ev.key==='Enter')e.click()};}
 for(const transition of $('map').querySelectorAll('[data-to]')){
  const destination=transition.dataset.to;
  if(!MAPS[destination])continue;
  transition.onclick=()=>{preview=true;showPlan(destination);showView('indoor');};
  transition.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();transition.onclick();}};
 }
 updateRoomHighlight();
 fit();wirePan();updateAnchor();renderIndoorRoute();}
function updateRoomHighlight(){
 for(const node of $('map').querySelectorAll('.room.selected'))node.classList.remove('selected');
 if(selectedRoom?.plan===current)roomElement(selectedRoom.id)?.classList.add('selected');
}
function selectRoom(room,startRoute=false){
 positionMode=false;$('positionNotice').hidden=true;
 const plan=MAPS[room.plan];
 hidePlanSearch();
 remember(room.name);$('search').blur();$('results').hidden=true;
 if(targetBuilding!==plan.building)clearCityRoute();
 navigating=false;
 selectedRoom=room;targetBuilding=plan.building;
 $('search').value=room.name;
 $('roomTitle').textContent=room.name;
 $('buildingAddress').textContent=CAMPUS[targetBuilding].address;
 preview=true;
 if(startRoute){if(fresh()&&geoContext.building!==plan.building&&['1','2'].includes(plan.building))openCityForRoom();else buildIndoorRoute();}
 else{
  showView('indoor');showPlan(room.plan);updateRoomHighlight();renderIndoorRoute();
  native('tick');
  const selection=roomElement(room.id);
  motion(selection,[{opacity:.65},{opacity:1}],250);
 }
}
function chooseBuilding(id){
 positionMode=false;$('cityRoomNote').hidden=true;$('insideRoute').hidden=true;
 if(targetBuilding!==id||selectedRoom)clearCityRoute();
 navigating=false;selectedRoom=null;targetBuilding=id;$('search').value='';$('results').hidden=true;
 const building=CAMPUS[id];
 $('roomTitle').textContent=building.name;
 $('buildingAddress').textContent=building.address+(building.approximate?' · Примерное положение':'');
 $('previewPlan').hidden=!hasPlan(id);
 follow=false;preview=true;showView('city');
 if(building.point)cityMap.setView(building.point,17,{animate:false});
 updateCampusPins();
}
function hasPlan(id){return Boolean(id&&MAPS['c'+id+'-f1'])}
function updateAnchor(){
 const svg=$('map').querySelector('svg');
 svg?.querySelector('.indoor-anchor')?.remove();
 if(anchor?.plan===current)svg?.append(svgElement('circle',{class:'indoor-anchor',cx:anchor.x,cy:anchor.y,r:8}));
 $('calibrateHeight').hidden=!anchor||anchor.plan!==current;
 updateRouteMenu();$('setAnchor').textContent=marking?'Отмена':anchor?.plan===current?'Уточнить точку':'Я здесь';
 if(marking)toast('Коснись коридора на плане');
}
$('setAnchor').onclick=()=>{marking=!marking;updateAnchor()};
$('map').addEventListener('click',event=>{
 if(!marking||Date.now()-draggedAt<250)return;
 const raw=screenPoint(event),nearest=RouteEngine.nearest(current,raw);
 if(!nearest||nearest.distance>MAPS[current].grid.step*2){toast('Выбери точку в коридоре');return}
 anchor={plan:current,x:nearest.x,y:nearest.y};
 if(navigating&&selectedRoom)activeRouteOrigin=routeOrigin();
 window.resetHeight();marking=false;updateAnchor();routeRenderedKey=null;renderIndoorRoute();
});
$('calibrateHeight').onclick=()=>{
 $('heightStatus').hidden=false;$('heightStatus').textContent='Стой на отмеченном этаже, пока датчик калибруется…';native('barometer');
};
window.onHeight=meters=>{
 const text=$('heightStatus');
 if(!anchor||!text.textContent)return;
 text.hidden=document.body.dataset.mode!=='indoor';
 text.textContent=meters===null?'Барометр недоступен на этом устройстве':
  'От отмеченного этажа: '+(Math.abs(meters)<.5?'около 0 м':(meters>0?'+':'−')+Math.abs(meters).toFixed(1).replace('.',',')+' м')+'. Номер этажа выбери по указателю.';
};
window.resetHeight=()=>{$('heightStatus').hidden=true;$('heightStatus').textContent=''};

function recentRooms(){return NavigatorStorage.read('recent_rooms',[]).filter(x=>typeof x==='string').slice(0,6)}
function remember(name){NavigatorStorage.write('recent_rooms',[name,...recentRooms().filter(n=>n!==name)].slice(0,6))}
function pointAge(){return gps?(gps.ageMillis||0)+Date.now()-(gps.receivedAt||Date.now()):Infinity}
function pointStatus(){document.body.classList.toggle('stale-gps',pointAge()>12000||lastStale)}
function fresh(){return CampusGeo.valid(gps)&&pointAge()<12000&&!lastStale}
window.onGPS=data=>{
 if(data.state!=='fix'){gpsIssue=data.state;if(navigating&&['disabled','denied'].includes(data.state))$('routeSummary').textContent='Для маршрута нужен доступ к геолокации';if(follow&&['disabled','denied'].includes(data.state))toast(data.state==='disabled'?'Геолокация выключена':'Доступ к геолокации закрыт');$('gpsSettings').hidden=!['permission','disabled','denied'].includes(data.state);$('gpsSettings').textContent=data.state==='disabled'?'Включить геолокацию':data.state==='denied'?'Настройки доступа':'Разрешить точное положение';if(['stale','disabled','denied'].includes(data.state)){lastStale=true;geoContext.state=data.state;document.body.classList.add('stale-gps')}return}
 if(!CampusGeo.valid(data)||(data.ageMillis||0)>12000||gps&&Number.isFinite(data.stamp)&&data.stamp<gps.stamp)return;
 if(!firstFix){firstFix=true;NavigatorStorage.timing('Первый GPS-фикс');}
 gps={...data,receivedAt:Date.now()};lastStale=false;gpsIssue=data.precise===false?'approximate':'fix';$('gpsSettings').hidden=data.precise!==false;$('gpsSettings').textContent=data.precise===false?'Включить точное положение':'Разрешить геолокацию';geoContext=tracker.update(data);pointStatus();let latlng=[gps.latitude,gps.longitude];
 if(!person){person=L.marker(latlng,{interactive:false,zIndexOffset:900,icon:L.divIcon({className:'',html:'<div class="person-dot"><i class="person-heading" hidden></i></div>',iconSize:[18,18],iconAnchor:[9,9]})}).addTo(cityMap);accuracyCircle=L.circle(latlng,{radius:gps.accuracy,weight:1,color:'#347fea',fillColor:'#347fea',fillOpacity:.12,interactive:false}).addTo(cityMap)}else{person.setLatLng(latlng);accuracyCircle.setLatLng(latlng).setRadius(gps.accuracy)}
 const heading=person.getElement()?.querySelector('.person-heading');
 if(heading){heading.hidden=!Number.isFinite(gps.bearing);if(!heading.hidden)heading.style.transform='rotate('+gps.bearing+'deg)';}
 if(follow&&(!lastCameraFix||CampusGeo.distance(lastCameraFix,latlng)>Math.max(2,gps.accuracy*.2))){cityMap.setView(latlng,Math.max(16,cityMap.getZoom()),{animate:false});follow=true;lastCameraFix=latlng}
 if(positionMode&&hasPlan(geoContext.building)){
  const saved=NavigatorStorage.read('floor_'+geoContext.building,'c'+geoContext.building+'-f1');
  if(document.body.dataset.mode!=='indoor'||MAPS[current].building!==geoContext.building){
   showView('indoor');showPlan(saved);
  }
 }
 if(navigating&&document.body.dataset.mode==='city'){
  if(geoContext.building===targetBuilding&&selectedRoom)buildIndoorRoute();
  else if(!routeInFlight&&(lastRequest===0||Date.now()-lastRequest>20000)&&(!cityRoute||lastRouteFix&&CampusGeo.distance(latlng,lastRouteFix)>45))requestCityRoute();
 }

};
setInterval(()=>{if(gps&&(gpsIssue==='fix'||gpsIssue==='approximate'||lastStale))pointStatus()},1000);

function levelText(prefix,plan){
 const level=document.createElement('span');level.className='level-suffix';
 level.textContent=plan.building+' · эт. '+plan.floor;
 return [prefix+' ',level];
}
let pathWorker=null,pathJob=0,runningPath=null,pendingPath=null;
let routeGeneration=0,activeRouteOrigin=null;
function dispatchIndoorPath(){
 if(runningPath||!pendingPath)return;
 runningPath=pendingPath;pendingPath=null;
 pathWorker.postMessage(runningPath.message);
}
function calculateIndoorPath(plan,starts,ends){
 if(!pathWorker){
  try{pathWorker=new Worker('indoor-worker.js');}catch(error){return Promise.reject(error);}
  pathWorker.onmessage=({data})=>{
   if(runningPath?.message.id!==data.id)return;
   const job=runningPath;runningPath=null;
   job.resolve(data.path);dispatchIndoorPath();
  };
  pathWorker.onerror=event=>{
   event.preventDefault();pathWorker.terminate();pathWorker=null;
   const error=new Error('Не удалось обработать план');
   runningPath?.reject(error);pendingPath?.reject(error);
   runningPath=pendingPath=null;
  };
 }
 pendingPath?.resolve(null);
 return new Promise((resolve,reject)=>{
  pendingPath={message:{id:++pathJob,plan,starts,ends},resolve,reject};
  dispatchIndoorPath();
 });
}
function renderIndoorRoute(){
 const svg=$('map').querySelector('svg');if(!svg)return;
 const room=selectedRoom?selectedRoom.plan+'/'+selectedRoom.id:'';
 const origin=navigating&&selectedRoom?(activeRouteOrigin||routeOrigin()):null;
 const key=JSON.stringify([current,room,origin,anchor]);
 if(routeRenderedKey===key)return;
 routeRenderedKey=key;
 const generation=++routeGeneration;
 pendingPath?.resolve(null);pendingPath=null;
 for(const old of svg.querySelectorAll('.route-line,.route-dashes,.route-start,.route-end'))old.remove();
 indoorPath=null;
 $('routeStepper').hidden=!navigating||!selectedRoom;
 const roomVisible=!navigating&&selectedRoom?.plan===current;
 $('roomCard').hidden=!roomVisible;
 $('planBar').hidden=!$('routeStepper').hidden||roomVisible;
 const panel=!$('routeStepper').hidden?$('routeStepper'):roomVisible?$('roomCard'):$('planBar');
 if($('sheet').dataset.panel!==panel.id){
  $('sheet').dataset.panel=panel.id;
  motion(panel,[{opacity:.6,transform:'translateY(5px)'},{opacity:1,transform:'translateY(0)'}],185);
 }
 if(roomVisible){
  $('indoorRoomName').textContent=selectedRoom.name;
  $('indoorRoomFloor').textContent='Корпус '+MAPS[current].building+' · эт. '+MAPS[current].floor;
  $('roomCityRoute').hidden=!['1','2'].includes(MAPS[current].building);
  updateFavourite();
 }
 $('routeSteps').textContent='';$('routeHint').textContent='';$('routeList').replaceChildren();$('routeList').hidden=true;
 
 $('previousFloor').hidden=true;$('nextFloor').hidden=true;
 if(!navigating||!selectedRoom)return;
 const p=MAPS[current];
 const floors=RouteEngine.floors(origin,selectedRoom.plan),index=floors?.indexOf(current)??-1;
 if(index<0){$('routeSteps').textContent=floors?'Этот этаж вне маршрута':'Подтверждённого перехода нет';return;}
 const prior=floors[index-1],next=floors[index+1];
 const destination=next?MAPS[next]:null;
 const tail=destination?levelText(destination.building!==p.building?'Переход в корпус':'Корпус',destination):[selectedRoom.name];
 const hint=(from,to)=>from.building!==to.building?'Переход в корпус '+to.building+' · эт. '+to.floor
  :(parseFloat(to.floor)>=parseFloat(from.floor)?'Поднимись':'Спустись')+' на '+to.floor+' этаж по лестнице';
 $('routeHint').textContent=destination?(destination.building===p.building?hint(p,destination):'Дойди до перехода'):'Дойди до аудитории '+selectedRoom.name;
 for(let i=index;i<floors.length-1;i++){
  const item=document.createElement('li');item.textContent=hint(MAPS[floors[i]],MAPS[floors[i+1]]);$('routeList').append(item);
 }
 const finish=document.createElement('li');finish.textContent='Аудитория '+selectedRoom.name;$('routeList').append(finish);

 $('routeSteps').replaceChildren(...levelText('Корпус',p),' → ',...tail);
 for(const [id,plan] of [['previousFloor',prior],['nextFloor',next]]){
  const button=$(id);button.hidden=!plan;button.onclick=()=>{if(plan){preview=true;showPlan(plan);showView('indoor')}};
 }
 const localStairs=()=>p.transitions.filter(t=>MAPS[t.to]?.building===p.building);
 let starts=anchor?.plan===current?[anchor]:p.transitions.filter(t=>t.to===(prior||'global'));
 if(!starts.length&&prior&&MAPS[prior].building===p.building)starts=localStairs();
 let ends=next?p.transitions.filter(t=>t.to===next):[selectedRoom];
 if(!ends.length&&next&&MAPS[next].building===p.building)ends=localStairs();
 const direction=$('routeHint').textContent;
 if(!starts.length||!ends.length){
  $('routeHint').textContent='На плане нет точки прохода. Можно отметить старт кнопкой «Я здесь».';
  return;
 }
 $('routeHint').textContent='Строим путь по плану…';
 calculateIndoorPath(current,starts,ends).then(path=>{
  if(generation!==routeGeneration||document.body.dataset.mode!=='indoor'||!svg.isConnected)return;
  if(!path){
   $('routeHint').textContent='Проход по плану не найден. Отметь старт кнопкой «Я здесь».';
   return;
  }
   $('routeHint').textContent=direction;
  indoorPath=path;
  PlanStyle.route(svg,path,next,Boolean(next&&MAPS[next].building!==p.building));
  viewScale=0;
  if(current===selectedRoom.plan&&path.points.length>1&&planBounds){
   const xs=path.points.map(point=>point.x),ys=path.points.map(point=>point.y);
   const padding=Math.min(planBounds.w,planBounds.h)*.14;
   const w=Math.max(planBounds.w*.32,Math.max(...xs)-Math.min(...xs)+padding*2);
   const h=Math.max(planBounds.h*.32,Math.max(...ys)-Math.min(...ys)+padding*2);
   view={x:(Math.min(...xs)+Math.max(...xs)-w)/2,y:(Math.min(...ys)+Math.max(...ys)-h)/2,w,h};
  }
  setView();
 }).catch(()=>{
  if(generation===routeGeneration&&document.body.dataset.mode==='indoor'&&svg.isConnected)$('routeHint').textContent='Не удалось рассчитать путь по этому плану';
 });

}
function updateRouteMenu(){
 const indoors=document.body.dataset.mode==='indoor';
 $('setAnchor').hidden=!indoors;
 $('calibrateHeight').hidden=!indoors||!anchor;
 $('endRoute').hidden=!navigating;
 $('moreRoute').hidden=!indoors&&!navigating;
 if($('moreRoute').hidden)setRouteMenu(false);
}
function setRouteMenu(open){
 $('routeMenu').hidden=!open;
 $('moreRoute').setAttribute('aria-expanded',String(open));
 queueAdjust();
}
$('moreRoute').onclick=()=>setRouteMenu($('routeMenu').hidden);
for(const id of ['setAnchor','calibrateHeight','endRoute'])$(id).addEventListener('click',()=>setRouteMenu(false));
function updateRouteButton(){ $('endCityRoute').hidden=!navigating||document.body.dataset.mode!=='city';$('routeButtonText').textContent=routeInFlight?'Строим…':navigating?'Обновить':'Маршрут';$('goHere').setAttribute('aria-busy',routeInFlight?'true':'false');$('sheet').classList.toggle('routing',routeInFlight);updateRouteMenu() }
function clearCityRoute(){if(routeController)routeController.abort();routeSerial++;routeInFlight=false;if(routeLine)cityMap.removeLayer(routeLine);if(routeHalo)cityMap.removeLayer(routeHalo);routeLine=null;routeHalo=null;cityRoute=null;lastRouteFix=null;$('cityTrip').hidden=true;$('fitRoute').hidden=true;$('routeSummary').textContent='';updateRouteButton()}
function nearestCityEntrance(id,from){
 const building=CAMPUS[id];
 const entrances=building.entrances.filter(entry=>CampusGeo.building(entry.point,id).distance<4);
 if(!entrances.length&&building.campusArrival)return nearestCityEntrance('6',from);
 return entrances.reduce((best,entry)=>!best||CampusGeo.distance(from,entry.point)<CampusGeo.distance(from,best.point)?entry:best,null)?.point||building.point;
}
async function requestCityRoute(force=false){
 if(!targetBuilding||!CAMPUS[targetBuilding].point||!fresh())return;if(geoContext.building===targetBuilding){if(selectedRoom)buildIndoorRoute();return}if(gps.accuracy>100){$('routeSummary').textContent='Уточняем местоположение…';native('location');return}if(routeInFlight)return;
 if(routeController)routeController.abort();const controller=new AbortController();routeController=controller;routeInFlight=true;let serial=++routeSerial;lastRequest=Date.now();const requestedTarget=targetBuilding,from=[gps.latitude,gps.longitude],to=nearestCityEntrance(targetBuilding,from);$('routeSummary').textContent='Ищем пеший маршрут…';updateRouteButton();let timeout=setTimeout(()=>controller.abort(),10000);
 NavigatorStorage.timing('Маршрут: запрос');
 try{
  const cached=force?null:NavigatorStorage.cachedRoute(requestedTarget,from);
  let r=cached;
  if(!r){
   const url='https://routing.openstreetmap.de/routed-foot/route/v1/foot/'+from[1]+','+from[0]+';'+to[1]+','+to[0]+'?overview=full&geometries=geojson&steps=false';
   const response=await fetch(url,{signal:controller.signal});
   NavigatorStorage.timing('Маршрут: заголовки ответа');
   if(!response.ok)throw Error('Сервис маршрутов недоступен');
   const data=await response.json();
   NavigatorStorage.networkBreakdown('routing.openstreetmap.de');
   if(data.code!=='Ok'||!data.routes?.length)throw Error('Пеший маршрут не найден');
   r=data.routes[0];
  }
  if(serial!==routeSerial||requestedTarget!==targetBuilding)return;
  if(r.geometry?.type!=='LineString'||!r.geometry.coordinates||r.geometry.coordinates.length<2||!r.geometry.coordinates.every(p=>Array.isArray(p)&&Number.isFinite(p[0])&&Number.isFinite(p[1])&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90)||!Number.isFinite(r.distance)||r.distance<0||!Number.isFinite(r.duration)||r.duration<0)throw Error('Не удалось прочитать маршрут');
  NavigatorStorage.cacheRoute(requestedTarget,from,r);
  let points=r.geometry.coordinates.map(p=>[p[1],p[0]]),halo=L.polyline(points,{color:'#fff',weight:8,opacity:.55,lineCap:'round',interactive:false}).addTo(cityMap),line=L.polyline(points,{color:'#8b61bb',weight:4.5,opacity:1,lineCap:'round',interactive:false}).addTo(cityMap);if(routeLine)cityMap.removeLayer(routeLine);if(routeHalo)cityMap.removeLayer(routeHalo);routeLine=line;routeHalo=halo;cityRoute=r;lastRouteFix=from;
  const distance=r.distance<1000?Math.round(r.distance)+' м':(r.distance/1000).toFixed(1).replace('.',',')+' км';
  $('tripTime').textContent=Math.max(1,Math.ceil(r.duration/60))+' мин';
  $('tripDistance').textContent=distance;
  $('cityTrip').hidden=false;
  $('routeSummary').textContent='Пеший маршрут'+(CAMPUS[requestedTarget].campusArrival?' до главного кампуса':!CAMPUS[requestedTarget].entrances.length?' к зданию · вход не нанесён':'');
  $('fitRoute').hidden=false;adjustMap();fitCurrentRoute();
 }catch(e){
  if(serial!==routeSerial)return;
  NavigatorStorage.timing(e.name==='AbortError'?'Маршрут: таймаут':'Маршрут: ошибка');
  if(cityRoute){$('routeSummary').textContent='Показан последний маршрут';return;}
  if(routeLine)cityMap.removeLayer(routeLine);
  routeLine=L.polyline([from,to],{color:'#8b61bb',weight:4.5,dashArray:'7 8',interactive:false}).addTo(cityMap);
  lastRouteFix=from;
  $('cityTrip').hidden=true;
  $('routeSummary').textContent='Пеший маршрут недоступен · Пунктир показывает направление';
  $('fitRoute').hidden=false;fitCurrentRoute();
 }
 finally{clearTimeout(timeout);if(serial===routeSerial){routeInFlight=false;routeController=null;updateRouteButton()}}
}
function routeOrigin(){
 const building=MAPS[selectedRoom.plan].building;
 const entry=Object.keys(MAPS).find(key=>MAPS[key].building===building&&MAPS[key].floor==='1'&&MAPS[key].transitions.some(step=>step.to==='global'))||'c'+building+'-f1';
 if(anchor&&RouteEngine.floors(anchor.plan,selectedRoom.plan))return anchor.plan;
 const floor=geoContext.building&&NavigatorStorage.read('floor_'+geoContext.building,null);
 return fresh()&&floor&&RouteEngine.floors(floor,selectedRoom.plan)?floor:entry;
}
$('allSteps').onclick=()=>{$('routeList').hidden=!$('routeList').hidden};
function openCityForRoom(){
 if(!selectedRoom)return;
 if(params.get('app')==='android'){native('cityroom',{room:selectedRoom.name});return;}
 showView('city');$('cityRoomNote').hidden=true;$('insideRoute').hidden=false;
 $('previewPlan').hidden=true;buildRoute();
}
$('roomCityRoute').onclick=openCityForRoom;
$('roomRoute').onclick=buildIndoorRoute;$('insideRoute').onclick=buildIndoorRoute;
function updateFavourite(){
 const on=NavigatorStorage.favourites().includes(selectedRoom.id);
 $('favouriteRoom').textContent=on?'★':'☆';
 $('favouriteRoom').setAttribute('aria-label',on?'Убрать из избранного':'Добавить в избранное');
 $('favouriteRoom').setAttribute('aria-pressed',String(on));
}
$('favouriteRoom').onclick=()=>{if(selectedRoom){NavigatorStorage.toggle(selectedRoom.id);updateFavourite();}};
function buildIndoorRoute(){
 if(!selectedRoom){showSearch();return;}
 const entry=routeOrigin();
 activeRouteOrigin=entry;navigating=true;positionMode=false;preview=true;routeRenderedKey=null;
 showView('indoor');showPlan(entry);setExpanded(false);updateRouteButton();
}
$('indoorRoute').onclick=buildIndoorRoute;
function buildRoute(){
 if(!targetBuilding){toast('Выбери корпус или найди аудиторию');return;}
 if(selectedRoom&&document.body.dataset.mode==='indoor'){buildIndoorRoute();return;}
 navigating=true;updateRouteButton();
 if(geoContext.building===targetBuilding){
  if(selectedRoom)buildIndoorRoute();
  else $('routeSummary').textContent='Ты рядом с корпусом '+targetBuilding;
 }else if(!fresh()){
  $('routeSummary').textContent='Уточняем местоположение…';native('location');
 }else requestCityRoute();
}
$('endCityRoute').onclick=$('endRoute').onclick=()=>{navigating=false;clearCityRoute();routeRenderedKey=null;renderIndoorRoute();updateRouteButton()};
function screenPoint(ev){let svg=$('map').querySelector('svg');if(svg&&typeof svg.createSVGPoint==='function'){let p=svg.createSVGPoint();p.x=ev.clientX;p.y=ev.clientY;return p.matrixTransform(svg.getScreenCTM().inverse())}return {x:0,y:0}}
function wirePan(){
 const svg=$('map').querySelector('svg'),pointers=new Map();
 let gesture=null,lastTap=0;
 const point=e=>({x:e.clientX,y:e.clientY});
 const midpoint=(a,b)=>({x:(a.x+b.x)/2,y:(a.y+b.y)/2});
 const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
 const local=p=>screenPoint({clientX:p.x,clientY:p.y});
 svg.onpointerdown=e=>{
  const pos=point(e);pointers.set(e.pointerId,pos);
  if(!gesture)gesture={start:pos,moved:false};
  if(pointers.size>1)gesture.moved=true;
 };
 svg.onpointermove=e=>{
  if(!pointers.has(e.pointerId)||!gesture)return;
  const previous=pointers.get(e.pointerId),before=[...pointers.values()],next=point(e);
  pointers.set(e.pointerId,next);
  if(pointers.size===2){
   const [a,b]=before,[c,d]=pointers.values();
   const oldDistance=distance(a,b),newDistance=distance(c,d);
   const start=local(midpoint(a,b)),end=local(midpoint(c,d));
   if(oldDistance&&newDistance)zoom(oldDistance/newDistance,start);
   view.x+=start.x-end.x;view.y+=start.y-end.y;setView();return;
  }
  if(distance(next,gesture.start)>6)gesture.moved=true;
  if(!gesture.moved)return;
  try{svg.setPointerCapture(e.pointerId);}catch(ignored){}
  const from=local(previous),to=local(next);
  view.x+=from.x-to.x;view.y+=from.y-to.y;setView();
 };
 svg.onpointerup=e=>{
  try{svg.releasePointerCapture(e.pointerId);}catch(ignored){}
  pointers.delete(e.pointerId);
  if(gesture?.moved){draggedAt=Date.now();lastTap=0;}
  else if(e.pointerType==='touch'){
   const now=Date.now();
   if(now-lastTap<300){zoom(.64,local(point(e)));draggedAt=now;lastTap=0;}
   else lastTap=now;
  }
  if(!pointers.size)gesture=null;
 };
 svg.onpointercancel=()=>{pointers.clear();gesture=null;};
 svg.ondblclick=e=>{e.preventDefault();zoom(.64,local(point(e)));draggedAt=Date.now();};
 svg.onwheel=e=>{e.preventDefault();zoom(e.deltaY>0?1.15:.87,local(point(e)));};
}
window.navigateBack=()=>{
 if(document.body.dataset.search){hidePlanSearch();return true;}
 if(!$('routeMenu').hidden){setRouteMenu(false);return true;}
 if(document.body.dataset.mode==='indoor'){
  if(!$('routeList').hidden){$('routeList').hidden=true;return true;}
  if(!$('planExtra').hidden){setExpanded(false);return true;}
  if(marking){marking=false;updateAnchor();return true;}
  if(navigating&&selectedRoom){
   navigating=false;activeRouteOrigin=null;routeRenderedKey=null;
   showPlan(selectedRoom.plan);updateRouteButton();
   return true;
  }
 }
 if(params.get('plan'))return false;
 if(document.body.dataset.mode==='indoor'){
  navigating=false;positionMode=false;setExpanded(false);showView('city');showAllBuildings();return true;
 }
 if(targetBuilding){targetBuilding=null;selectedRoom=null;navigating=false;clearCityRoute();showView('city');updateCampusPins();return true;}
 return false;
};
$('back').onclick=()=>{if(!window.navigateBack())native('back')};
$('locate').onclick=()=>{
 follow=true;lastCameraFix=null;
 positionMode=true;
 if(fresh()){
  cityMap.setView([gps.latitude,gps.longitude],18,{animate:false});follow=true;
  if(hasPlan(geoContext.building)){showView('indoor');showPlan(NavigatorStorage.read('floor_'+geoContext.building,'c'+geoContext.building+'-f1'));}
 }else toast('Уточняем местоположение…');
 native('location');
};
$('gpsSettings').onclick=()=>native(gpsIssue==='disabled'?'locationSettings':gpsIssue==='denied'?'appSettings':gpsIssue==='approximate'||gpsIssue==='permission'?'precise':'location');
$('goHere').onclick=buildRoute;
$('previewPlan').onclick=()=>{const plan=selectedRoom?.plan||'c'+targetBuilding+'-f1';if(!MAPS[plan])return;preview=true;showView('indoor');showPlan(plan);setExpanded(false)};
for(const [id,building] of Object.entries(CAMPUS)){
 const button=document.createElement('button');
 button.dataset.building=id;button.textContent=id;button.setAttribute('aria-label',building.name);
 button.onclick=()=>{if(!hasPlan(id)){toast('План недоступен');return;}preview=true;showView('indoor');showPlan('c'+id+'-f1');native('tick')};
 $('buildings').append(button);
}
$('clearSearch').onclick=()=>{$('search').value='';$('search').focus();searchResults()};
function searchResults(){
 const q=$('search').value.trim().toLocaleLowerCase('ru').replace(/[–—]/g,'-');
 const numericRoom=/^[0-9]+(?:[-\s]+[0-9]+)?[а-яa-z]?$/u.test(q)?q.replace(/[-\s]+/g,''):null;
 const results=$('results');results.replaceChildren();
 if(/^3[-\s]*(?:сз|спортзал)$/i.test(q)||q==='спортзал')result('Спортзал','Корпус 3',()=>{hidePlanSearch();openDestination('3-СЗ',false)});
 function result(title,subtitle,callback){
  const b=document.createElement('button'),name=document.createElement('strong'),sub=document.createElement('small');
  name.textContent=title;sub.textContent=subtitle;b.append(name,sub);b.onclick=callback;results.append(b);
 }
 if(q){
  for(let [id,building] of Object.entries(CAMPUS))if(building.name.toLowerCase().includes(q)||building.address.toLowerCase().includes(q))
   result(building.name,building.address,()=>{
    const indoor=document.body.dataset.mode==='indoor';hidePlanSearch();
    if(indoor&&hasPlan(id)){preview=true;showPlan('c'+id+'-f1');renderIndoorRoute()}else chooseBuilding(id);
   });
  for(let r of catalogue.filter(r=>r.name.toLowerCase().includes(q)||r.label.toLowerCase().includes(q)||(numericRoom!==null&&r.name.toLowerCase().replace(/-/g,'')===numericRoom)).sort((a,b)=>Number(b.name.replace(/-/g,'')===numericRoom)-Number(a.name.replace(/-/g,'')===numericRoom)).slice(0,30))
   result(r.name,'Корпус '+MAPS[r.plan].building+' · эт. '+MAPS[r.plan].floor,()=>selectRoom(r));
 }else {
 for(const id of NavigatorStorage.favourites()){
  const room=catalogue.find(r=>r.id===id);
  if(room)result('★ '+room.name,'Избранное',()=>selectRoom(room));
 }
 for(let name of recentRooms()){
  const r=catalogue.find(r=>r.name===name);
  if(r)result(name,'Недавний поиск',()=>selectRoom(r));
 }
 }
 if(!results.children.length&&(q||document.body.dataset.search))
  results.textContent=q?'Аудитория или корпус не найдены':'Введи номер аудитории или название корпуса';
 results.hidden=!document.body.dataset.search&&!q&&!results.children.length;
}
$('search').oninput=searchResults;
$('search').addEventListener('focus',searchResults);
function openDestination(raw,route=true){
 const wanted=raw.trim().toLowerCase().replace(/[–—]/g,'-').replace(/\s+/g,'');
 const room=catalogue.find(item=>item.name.toLowerCase()===wanted);
 if(room){selectRoom(room,route);return;}
 const id=wanted.match(/^([1-6])-/)?.[1];
 if(!id){$('search').value=raw;searchResults();return;}
 chooseBuilding(id);
 $('cityRoomNote').hidden=false;
 $('cityRoomNote').textContent=(/^3-(?:сз|спортзал)$/i.test(wanted)?'Спортзал':raw)+' · Плана помещения нет';
 $('previewPlan').hidden=true;
}
const initialBuilding=params.get('plan');
const initialFloor=initialBuilding&&Object.keys(MAPS).find(key=>MAPS[key].building===initialBuilding&&MAPS[key].floor==='1');
let wanted='';
try{wanted=decodeURIComponent(location.hash.slice(1));}catch(ignored){}
const initialRoom=params.get('indoor_route')==='1'&&catalogue.find(item=>item.name.toLowerCase()===wanted.toLowerCase());
if(initialRoom)selectRoom(initialRoom,true);
else if(initialFloor){showView('indoor');showPlan(initialFloor);}
else showAllBuildings();
if(wanted&&!initialRoom)openDestination(wanted,!initialBuilding&&params.get('indoor_route')!=='1');
initialFrame=false;
updateRouteButton();
window.mapInterfaceReady=true;
requestAnimationFrame(()=>{
 NavigatorStorage.timing('Первый кадр карты');
 if(params.get('app')==='android')native('ready');
});
