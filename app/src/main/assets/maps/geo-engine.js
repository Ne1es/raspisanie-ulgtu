/* Real building footprints, with uncertainty and hysteresis. No inferred room/floor. */
const CampusGeo=(()=>{
 const R=6371008.8,rad=Math.PI/180;
 function valid(f){return f&&Number.isFinite(f.latitude)&&Number.isFinite(f.longitude)&&Math.abs(f.latitude)<=90&&Math.abs(f.longitude)<=180&&Number.isFinite(f.accuracy)&&f.accuracy>0&&f.accuracy<=20000}
 function distance(a,b){let p=(b[0]-a[0])*rad,q=(b[1]-a[1])*rad,h=Math.sin(p/2)**2+Math.cos(a[0]*rad)*Math.cos(b[0]*rad)*Math.sin(q/2)**2;return 2*R*Math.asin(Math.min(1,Math.sqrt(h)))}
 function contains(point,ring){let x=point[1],y=point[0],inside=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){let yi=ring[i][0],xi=ring[i][1],yj=ring[j][0],xj=ring[j][1];if((yi>y)!==(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside}return inside}
 function segmentDistance(p,a,b){let scale=R*rad,cos=Math.cos(p[0]*rad),ax=(a[1]-p[1])*cos*scale,ay=(a[0]-p[0])*scale,bx=(b[1]-p[1])*cos*scale,by=(b[0]-p[0])*scale,dx=bx-ax,dy=by-ay,t=Math.max(0,Math.min(1,-(ax*dx+ay*dy)/(dx*dx+dy*dy||1)));return Math.hypot(ax+t*dx,ay+t*dy)}
 function boundary(point,ring){let d=Infinity;for(let i=1;i<ring.length;i++)d=Math.min(d,segmentDistance(point,ring[i-1],ring[i]));return d}
 function building(point,id){let inside=false,d=Infinity;for(let p of CAMPUS[id].footprints){if(contains(point,p.outer)&&!p.holes.some(h=>contains(point,h)))inside=true;d=Math.min(d,boundary(point,p.outer));for(let hole of p.holes)d=Math.min(d,boundary(point,hole))}return {id,inside,distance:d}}
 function classify(f){if(!valid(f))return {valid:false,building:null,floor:null,reason:'invalid'};let point=[f.latitude,f.longitude],candidates=Object.keys(CAMPUS).map(id=>building(point,id)),hit=candidates.find(c=>c.inside),near=candidates.slice().sort((a,b)=>a.distance-b.distance)[0];return {valid:true,building:hit?.id||null,floor:null,inside:!!hit,distance:hit?.distance??near.distance,nearest:near.id,confident:!!hit&&f.accuracy<=12&&hit.distance>f.accuracy*1.25,eligible:f.accuracy<=25&&f.precise!==false};}
 class Tracker{
  constructor(){this.current=null;this.candidate=null;this.since=0;this.samples=0;this.lastStamp=-1;this.outsideSince=0}
  update(f,now=Date.now()){
   const c=classify(f);if(!c.valid)return {building:this.current,floor:null,state:'invalid',certain:false};let stamp=Number.isFinite(f.stamp)?f.stamp:now;
   if((f.ageMillis||0)>15000||f.stale)return {building:this.current,floor:null,state:'stale',certain:false};
   if(!c.eligible)return {building:this.current,floor:null,state:'uncertain',certain:false,accuracy:f.accuracy};
   if(c.building){this.outsideSince=0;if(this.candidate!==c.building){this.candidate=c.building;this.since=now;this.samples=0}if(stamp!==this.lastStamp)this.samples++;if(c.confident||this.samples>=2&&now-this.since>=2500)this.current=c.building;}
   else{this.candidate=null;this.samples=0;if(this.current){let d=building([f.latitude,f.longitude],this.current).distance;if(d>Math.max(7,f.accuracy*1.2)){if(!this.outsideSince)this.outsideSince=now;else if(now-this.outsideSince>=2500)this.current=null}else this.outsideSince=0;}}
   this.lastStamp=stamp;return {building:this.current,floor:null,state:this.current?'building':'city',certain:this.current===c.building&&c.confident,accuracy:f.accuracy,possibleBuilding:c.building};
  }
 }
 function entrance(id,from){let b=CAMPUS[id],options=b.entrances.filter(e=>building(e.point,id).distance<4);if(!options.length)return b.point;let main=options.filter(e=>e.kind==='main');if(main.length)options=main;options.sort((a,c)=>distance(a.point,from||b.point)-distance(c.point,from||b.point));return options[0].point}
 return {valid,distance,contains,boundary,building,classify,Tracker,entrance};
})();
