importScripts('map-data.js','route-engine.js');

function length(path){
 return path.points.reduce((total,p,i,points)=>i?total+Math.hypot(p.x-points[i-1].x,p.y-points[i-1].y):0,0);
}

self.onmessage=({data:{id,plan,starts,ends}})=>{
 let best=null,bestLength=Infinity;
 for(const start of starts)for(const end of ends){
  const path=RouteEngine.walk(plan,start,end);
  if(path){
   const size=length(path);
   if(size<bestLength){bestLength=size;best=path;}
  }
 }
 self.postMessage({id,path:best});
};
