const PlanStyle=(()=>{
 const icons={
  stairs:'M5 18h4v-4h4v-4h4V6h3',
  lift:'M8 8l4-4 4 4M8 16l4 4 4-4',
  exit:'M10 5H5v14h5m-1-7h11m-4-4 4 4-4 4',
  wc:'M5 10h14m-14 0v2a7 7 0 0014 0v-2M8 10V5h8v5m-4 9v2',
  crossing:'M4 9h16m-4-4 4 4-4 4m4 2H4m4-4-4 4 4 4',
  finish:'M7 20V5m0 1h11l-3 3 3 3H7'
 };
 const endOfLabel=room=>room.label.slice(room.name.length).replace(/^[\s·]+/u,'');
 function roomType(label,service){
  if(/сануз|туалет|уборн|\bWC\b/iu.test(label))return 'wc';
  if(/лифт/iu.test(label))return 'lift';
  if(/лаборатор/iu.test(label))return 'lab';
  if(/заведующ|преподавательск/iu.test(label)||/^(?:кафедра|деканат)(?:\s|$)/iu.test(label))return 'department';
  return service?'service':'classroom';
 }
 function icon(kind,x,y,additional=''){
  const marker=svgElement('g',{class:'plan-icon '+additional,transform:'translate('+x+' '+y+')'});
  const graphic=svgElement('g',{class:'plan-icon-size'});
  graphic.append(svgElement('circle',{r:10}));
  if(kind==='start')graphic.append(svgElement('circle',{class:'start-core',r:3}));
  else graphic.append(svgElement('path',{d:icons[kind]||icons.stairs,transform:'translate(-8 -8) scale(.667)'}));
  marker.append(graphic);return marker;
 }
 function decorate(svg,plan){
  const rooms=new Map(plan.rooms.map(room=>[room.id,room]));
  for(const element of svg.querySelectorAll('.room')){
   const room=rooms.get(element.dataset.id);
   if(!room)continue;
   element.querySelector(':scope > rect,:scope > polygon,:scope > path')?.classList.add('room-outline');
   const label=endOfLabel(room);
   const type=roomType(label,element.classList.contains('service'));
   element.dataset.type=type;
   if(type==='wc'||type==='lift'){
    const badge=icon(type,room.x,room.y,'facility-icon');
    element.append(badge);
   }
   for(const text of element.querySelectorAll('text')){
    text.classList.add('room-label');
    text.dataset.originalSize=Number(text.getAttribute('font-size'))||11;
    text.dataset.labelLength=text.textContent.trim().length;
    if(room.box)text.dataset.roomWidth=room.box[2];
   }
  }
  const old=[...svg.querySelectorAll('.transition[data-transition]')];
  const existing=new Map(old.map(el=>[el.dataset.transition,el]));
  for(const transition of plan.transitions){
   const target=MAPS[transition.to],element=existing.get(transition.id);
   const kind=transition.to==='global'?'exit':target&&target.building!==plan.building?'crossing':/лифт|lift/i.test(transition.kind+' '+transition.id)?'lift':'stairs';
   const marker=icon(kind,transition.x,transition.y,'facility-icon');
   if(element)element.classList.add('restyled-transition');
   if(transition.to==='global'){
    marker.setAttribute('aria-label','Выход');
   }else if(target){
    marker.dataset.to=transition.to;
    marker.setAttribute('tabindex','0');marker.setAttribute('role','button');
    marker.setAttribute('aria-label',kind==='crossing'?'Переход в корпус '+target.building+' · эт. '+target.floor:'Переход на этаж '+target.floor);
   }
   svg.append(marker);
  }
  return {labels:[...svg.querySelectorAll('.room-label')],markers:[...svg.querySelectorAll('.facility-icon .plan-icon-size')]};
 }
 function fit(svg,plan){
  const root=svg.firstElementChild;
  if(root?.getBBox){
   try{
    const bounds=root.getBBox();
    if(bounds.width>=plan.width*.45&&bounds.height>=plan.height*.32&&Number.isFinite(bounds.x)){
     const pad=Math.max(bounds.width,bounds.height)*.027;
     return {x:bounds.x-pad,y:bounds.y-pad,w:bounds.width+2*pad,h:bounds.height+2*pad};
    }
   }catch(unavailable){/* The nominal plan box is also valid. */}
  }
  return {x:-plan.width*.025,y:-plan.height*.025,w:plan.width*1.05,h:plan.height*1.05};
 }
 function route(svg,path,next,interbuilding){
  const points=path.points,shape=points.map(point=>point.x+','+point.y).join(' ');
  svg.append(svgElement('polyline',{class:'route-line',points:shape,pathLength:100}),
   svgElement('polyline',{class:'route-dashes',points:shape,pathLength:100}));
  svg.append(icon('start',points[0].x,points[0].y,'route-start'));
  const end=points[points.length-1];
  svg.append(icon(next?(interbuilding?'crossing':'stairs'):'finish',end.x,end.y,'route-end'));
 }
 return {decorate,fit,route};
})();
