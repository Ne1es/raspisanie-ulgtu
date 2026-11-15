const NavigatorStorage=(()=>{
 function read(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback}catch{return fallback}}
 function write(key,value){try{localStorage.setItem(key,JSON.stringify(value))}catch{}}
 const favourites=()=>read('favourite_rooms',[]);
 function toggle(id){const values=favourites();write('favourite_rooms',values.includes(id)?values.filter(value=>value!==id):[id,...values]);}
 function timing(label){
  const events=read('map_timing',[]);
  if(label==='Первый кадр карты')events.length=0;
  events.push(label+' · '+Math.round(performance.now())+' мс');
  const recent=events.slice(-16);
  write('map_timing',recent);
  if(new URLSearchParams(location.search).get('app')==='android'){
   clearTimeout(timing.sync);
   timing.sync=setTimeout(()=>native('timing',{events:recent.join('\n')}),120);
  }
 }
 function networkBreakdown(host){
  const entries=(performance.getEntriesByType?.('resource')||[]).filter(entry=>{
   try{return new URL(entry.name).host===host;}catch{return false;}
  });
  const recent=entries[entries.length-1];
  if(!recent?.responseStart){timing(host+': DNS/TCP/TTFB недоступны');return;}
  const duration=(end,start)=>Math.max(0,Math.round(end-start));
  timing(host+': DNS '+duration(recent.domainLookupEnd,recent.domainLookupStart)+
   ', соединение '+duration(recent.connectEnd,recent.connectStart)+
   ', TTFB '+duration(recent.responseStart,recent.requestStart));
 }
 // Route points can include the user's location. Keep them in memory for this map session.
 const routes=new Map();
 function routeKey(id,from){return id+':'+from.map(value=>value.toFixed(3)).join(',')}
 function cachedRoute(id,from){
  const entry=routes.get(routeKey(id,from));
  return entry&&Date.now()-entry.time<15*60*1000?entry.route:null;
 }
 function cacheRoute(id,from,route){
  const key=routeKey(id,from);routes.delete(key);routes.set(key,{time:Date.now(),route});
  if(routes.size>12)routes.delete(routes.keys().next().value);
 }
 return {read,write,timing,networkBreakdown,favourites,toggle,cachedRoute,cacheRoute};
})();
