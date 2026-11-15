from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse,parse_qs
import urllib.request,urllib.error,json,hashlib,threading,time
cache=Path('build/browser-service-cache');cache.mkdir(parents=True,exist_ok=True);lock=threading.Lock();receipts=[]
Path('verification/0.6.0').mkdir(parents=True,exist_ok=True)
class Server(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  url=parse_qs(urlparse(self.path).query).get('url',[''])[0];parsed=urlparse(url)
  if parsed.scheme!='https' or parsed.hostname not in {'tile.openstreetmap.org','routing.openstreetmap.de'}:self.send_error(403);return
  key=hashlib.sha256(url.encode()).hexdigest();bodyfile=cache/(key+'.bin');metafile=cache/(key+'.json')
  try:
   if bodyfile.exists() and metafile.exists():body=bodyfile.read_bytes();meta=json.loads(metafile.read_text())
   else:
    req=urllib.request.Request(url,headers={'User-Agent':'UlstuTimetable/0.6.0 (educational app visual verification)','Referer':'https://appassets.androidplatform.net/','Origin':'https://appassets.androidplatform.net'})
    with urllib.request.urlopen(req,timeout=25) as r:body=r.read();meta={'url':url,'status':r.status,'headers':dict(r.headers)}
    bodyfile.write_bytes(body);metafile.write_text(json.dumps(meta))
   self.send_response(meta['status'])
   for k,v in meta['headers'].items():
    if k.lower() in {'content-type','access-control-allow-origin','cache-control','etag','last-modified'}:self.send_header(k,v)
   self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
   with lock:
    receipts.append({'url':url,'status':meta['status'],'type':next((v for k,v in meta['headers'].items() if k.lower()=='content-type'),''),'bytes':len(body)})
    Path('verification/0.6.0/browser-services.json').write_text(json.dumps(receipts,indent=2))
  except Exception as e:
   self.send_response(502);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps({'error':str(e)}).encode())
ThreadingHTTPServer(('127.0.0.1',8765),Server).serve_forever()
