/* Full city coverage from OpenStreetMap. Leaflet draws the tiles and all
   overlays in the same camera, without a WebGL context or a second renderer. */
const CityLayer=(()=>{
 function attach(map){
  const stage=document.getElementById('cityStage');
  const notice=document.getElementById('mapNotice');
  const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
   minZoom:4,maxZoom:20,maxNativeZoom:19,keepBuffer:2,updateWhenIdle:true
  });
  let visible=false,loaded=0,failed=0,timer=0;
  function display(state,caption=''){
   stage.dataset.basemap=state;
   notice.textContent=caption;notice.hidden=!visible||!caption;
   notice.disabled=state==='loading';
  }
  function begin(){
   clearTimeout(timer);loaded=0;failed=0;
   if(stage.dataset.basemap!=='osm-raster')display('loading','Загружаем карту города…');
   timer=setTimeout(()=>{
    if(!loaded)display('error','Карта города недоступна · Повторить');
    else if(failed)display('partial','Часть карты не загрузилась · Повторить');
   },9000);
  }
  tiles.on('loading',begin);
  tiles.on('tileload',()=>{
   if(++loaded===1){
    if(stage.dataset.basemap!=='osm-raster')NavigatorStorage.timing('Подложка: OpenStreetMap');
    display('osm-raster');
   }
  });
  tiles.on('tileerror',()=>{failed++;});
  tiles.on('load',()=>{
   clearTimeout(timer);
   if(!loaded&&failed)display('error','Карта города недоступна · Повторить');
   else if(failed)display('partial','Часть карты не загрузилась · Повторить');
  });
  notice.onclick=()=>{begin();tiles.redraw();};
  function update(){
   visible=document.body.dataset.mode==='city';
   notice.hidden=!visible||!notice.textContent;
   if(visible&&!map.hasLayer(tiles)){
    begin();tiles.addTo(map);
   }
  }
  return {update};
 }
 return {attach};
})();
