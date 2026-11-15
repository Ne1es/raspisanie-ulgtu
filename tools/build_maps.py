"""Derive a new visual map and conservative corridor masks from official geometry.
Room doors are not encoded in the source. Routes end in the adjacent corridor.
"""
from pathlib import Path
import sys,json,re,copy,math,io,base64
sys.path.insert(0,str(Path('.tooling/py-svg').resolve()))
from lxml import etree as ET
from svgpathtools import parse_path
import cairosvg
from PIL import Image
BASE=Path('app/src/main/assets/maps')
NAV=json.loads((BASE/'navigation.js').read_text().split('=',1)[1].rstrip(';'))
NS='http://www.w3.org/2000/svg'
def tag(e):return e.tag.split('}')[-1]
def primitive(e):
 if tag(e) in ('path','rect','polygon','circle','ellipse'):return e
 for c in e:
  if tag(c) in ('path','rect','polygon','circle','ellipse') and c.get('fill','black') not in ('none','transparent'):return c
 return next((c for c in e.iter() if tag(c) in ('path','rect','polygon') and c.get('fill','black') not in ('none','transparent')),None)
def bbox(e):
 t=tag(e)
 if t=='path':
  x1,x2,y1,y2=parse_path(e.get('d')).bbox()
 elif t=='rect':x1=float(e.get('x',0));y1=float(e.get('y',0));x2=x1+float(e.get('width',0));y2=y1+float(e.get('height',0))
 elif t in ('circle','ellipse'):
  cx=float(e.get('cx',0));cy=float(e.get('cy',0));rx=float(e.get('rx',e.get('r',0)));ry=float(e.get('ry',e.get('r',0)));x1,x2,y1,y2=cx-rx,cx+rx,cy-ry,cy+ry
 else:
  a=list(map(float,re.findall(r'-?\d+(?:\.\d+)?',e.get('points',''))));x1,x2,y1,y2=min(a[::2]),max(a[::2]),min(a[1::2]),max(a[1::2])
 tr=e.get('transform','');m=re.search(r'rotate\(([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\)',tr)
 if m:
  a,cx,cy=map(float,m.groups());a=math.radians(a);points=[(cx+(x-cx)*math.cos(a)-(y-cy)*math.sin(a),cy+(x-cx)*math.sin(a)+(y-cy)*math.cos(a)) for x,y in [(x1,y1),(x1,y2),(x2,y1),(x2,y2)]];x1,x2,y1,y2=min(p[0] for p in points),max(p[0] for p in points),min(p[1] for p in points),max(p[1] for p in points)
 return [round(v,2) for v in (x1,y1,x2-x1,y2-y1)]
def shape(e,fill,stroke='none'):
 n=copy.deepcopy(e)
 for attr in list(n.attrib):
  if attr not in ('d','x','y','width','height','cx','cy','rx','ry','r','points','transform','fill-rule','clip-rule'):n.attrib.pop(attr,None)
 n.set('fill',fill);n.set('stroke',stroke);n.set('stroke-width','1.4');n.set('stroke-linejoin','round');return n
out={};stats=[]
for key,nav in NAV.items():
 tree=ET.parse(str(BASE/('ulstu-'+key+'.svg')));root=tree.getroot();view=list(map(float,root.get('viewBox').split()));w,h=view[2:]
 nodes={e.get('id'):e for e in root.iter() if e.get('id')};floor=nodes.get('floor')
 if floor is None:
  top=root[0];floor=top[0]
 floor_prims=[e for e in floor.iter() if tag(e) in ('path','rect','polygon') and e.get('fill','black').lower() in ('white','#fff','#ffffff')]
 if not floor_prims:
  raise ValueError('No floor geometry '+key)
 svg=ET.Element('{'+NS+'}svg',nsmap={None:NS},viewBox=' '.join(map(str,view)),role='img')
 fg=ET.SubElement(svg,'{'+NS+'}g',attrib={'class':'floor'})
 mask=ET.Element('{'+NS+'}svg',nsmap={None:NS},viewBox=' '.join(map(str,view)),width=str(w),height=str(h))
 for e in floor_prims:fg.append(shape(e,'var(--corridor)','var(--outline)'));mask.append(shape(e,'white'))
 # Preserve explicit internal wall segments on the floor outline.
 for e in floor.iter():
  if tag(e)=='path' and e.get('stroke') and e.get('fill','black')=='none':
   n=shape(e,'none','var(--outline)');fg.append(n);mask.append(shape(e,'none','black'))
 rooms=[];obstacles=[]
 for id,e in nodes.items():
  if not re.match(r'^c[126]-f[^-]+-',id):continue
  p=primitive(e)
  if p is None:continue
  try:b=bbox(p)
  except Exception:continue
  if b[2]<4 or b[3]<4:continue
  obstacles.append(p)
  item=nav['items'].get(id,{})
  name=item.get('name','');match=re.match(r'^([126]-\d+[а-яa-zА-Я]*)\b',name)
  room=match.group(1) if match else ''
  g=ET.SubElement(svg,'{'+NS+'}g',attrib={'class':'room'+(' service' if not room else ''),'data-id':id,'data-name':room or name or 'Помещение','tabindex':'0','role':'button','aria-label':name or 'Помещение'})
  g.append(shape(p,'var(--room)','var(--outline)'))
  title=ET.SubElement(g,'{'+NS+'}title');title.text=name or 'Помещение'
  cx,cy=b[0]+b[2]/2,b[1]+b[3]/2
  text=ET.SubElement(g,'{'+NS+'}text',x=str(round(cx,2)),y=str(round(cy,2)),attrib={'class':'room-label','dominant-baseline':'middle','text-anchor':'middle','font-size':str(max(9,min(20,b[2]/max(3,len(room))*1.35)))})
  text.text=room.split('-',1)[1] if room else ('WC' if 'wc' in id else 'Л' if 'elevator' in id else '')
  if room:rooms.append({'id':id,'name':room,'label':name,'box':b,'x':round(cx,2),'y':round(cy,2)})
 for p in obstacles:mask.append(shape(p,'black','black'))
 transitions=[]
 for id,item in nav['items'].items():
  if item.get('action')!='move' or id not in nodes:continue
  prim=primitive(nodes[id])
  if prim is None:continue
  try:b=bbox(prim)
  except Exception:continue
  x,y=round(b[0]+b[2]/2,2),round(b[1]+b[3]/2,2)
  dest=item.get('direction','');kind='exit' if dest=='global' else 'stairs'
  g=ET.SubElement(svg,'{'+NS+'}g',attrib={'class':'transition '+kind,'data-transition':id,'data-to':dest,'role':'button','tabindex':'0','aria-label':'Выход' if kind=='exit' else 'Переход: '+NAV.get(dest,{}).get('config',{}).get('mapLabel','соседняя зона'),'transform':f'translate({x} {y})'})
  ET.SubElement(g,'{'+NS+'}rect',x='-11',y='-11',width='22',height='22',rx='7',attrib={'class':'transition-bg'})
  ET.SubElement(g,'{'+NS+'}path',d='M-6 5H-2V1H2V-3H6' if kind=='stairs' else 'M-5 -5H1V5H-5ZM1 0H7M4 -3L7 0L4 3',fill='none',stroke='currentColor',attrib={'stroke-width':'2','stroke-linecap':'round','stroke-linejoin':'round'})
  transitions.append({'id':id,'to':dest,'x':x,'y':y,'kind':kind})
 # 5px cells, rasterize the same exact paths to avoid straight lines through rooms.
 step=5;gw=math.ceil(w/step);gh=math.ceil(h/step)
 png=cairosvg.svg2png(bytestring=ET.tostring(mask),output_width=gw,output_height=gh)
 im=Image.open(io.BytesIO(png)).convert('RGBA');bits=bytearray(math.ceil(gw*gh/8));free=0
 for y in range(gh):
  for x in range(gw):
   r,g,b,a=im.getpixel((x,y))
   if a>200 and min(r,g,b)>230:bits[(y*gw+x)//8]|=1<<((y*gw+x)%8);free+=1
 out[key]={'label':nav['config']['mapLabel'],'building':key[1],'floor':str(nav['config']['floorName']),'width':w,'height':h,'svg':ET.tostring(svg,encoding='unicode'),'rooms':rooms,'transitions':transitions,'prev':nav['config'].get('prevMap',''),'next':nav['config'].get('nextMap',''),'grid':{'width':gw,'height':gh,'step':step,'bits':base64.b64encode(bits).decode()}}
 stats.append((key,len(rooms),len(transitions),free))
(BASE/'map-data.js').write_text('const MAPS='+json.dumps(out,ensure_ascii=False,separators=(',',':'))+';')
print('\n'.join(map(str,stats)));print('Plans',len(out),'room entries',sum(len(p['rooms']) for p in out.values()))
