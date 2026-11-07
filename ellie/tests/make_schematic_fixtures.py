"""Small independently encoded NBT fixtures; no uploaded blueprint/entity data is copied."""
from pathlib import Path
import gzip, struct, sys
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
def text(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def payload(t,v):
 if t==1:return struct.pack('>b',v)
 if t==2:return struct.pack('>h',v)
 if t==3:return struct.pack('>i',v)
 if t==4:return struct.pack('>q',v)
 if t==8:return text(v)
 if t==7:return struct.pack('>i',len(v))+bytes(v)
 if t==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(payload(v[0],x) for x in v[1])
 if t==10:return b''.join(bytes([t2])+text(k)+payload(t2,v2) for k,(t2,v2) in v.items())+b'\0'
 if t==12:return struct.pack('>i',len(v))+b''.join(struct.pack('>Q',x) for x in v)
 raise ValueError(t)
def root(v):return b'\x0a\0\0'+payload(10,v)
def pos(x,y,z):return {k:(3,v) for k,v in zip('xyz',(x,y,z))}
def block(name,**props):return {'Name':(8,'minecraft:'+name),'Properties':(10,{k:(8,v) for k,v in props.items()})}
def region(size=(3,2,3),position=(0,0,0),blocks=None):
 w,h,d=map(abs,size);palette=[block('air'),block('cobblestone'),block('oak_trapdoor',facing='north',half='top',open='true',powered='false',waterlogged='false'),block('water',level='0')]
 if blocks is None:blocks=[1]*9+[2,3]+[0]*7
 packed=[0]*((len(blocks)*2+63)//64)
 for i,n in enumerate(blocks):packed[i//32]|=n<<((i%32)*2)
 return {'Position':(10,pos(*position)),'Size':(10,pos(*size)),'BlockStatePalette':(9,(10,palette)),'BlockStates':(12,packed),'Entities':(9,(10,[{'id':(8,'minecraft:zombie')}])),'TileEntities':(9,(10,[]))}
def lite(regions):
 bounds=[]
 for r in regions.values():
  p=[r['Position'][1][k][1] for k in 'xyz'];n=[r['Size'][1][k][1] for k in 'xyz'];lo=[min(a,a+b+(1 if b<0 else -1)) for a,b in zip(p,n)];bounds.append((lo,[a+abs(b) for a,b in zip(lo,n)]))
 dims=[max(b[1][i] for b in bounds)-min(b[0][i] for b in bounds) for i in range(3)]
 metadata={'Name':(8,'Ellie survival test'),'Author':(8,'PlayerEngine regression suite'),'RegionCount':(3,len(regions)),'EnclosingSize':(10,pos(*dims))}
 return {'Version':(3,6),'SubVersion':(3,1),'MinecraftDataVersion':(3,3955),'Metadata':(10,metadata),'Regions':(10,{k:(10,v) for k,v in regions.items()})}
for name,body in [('small',lite({'region':region()})),('negative',lite({'region':region(size=(-3,2,-3),position=(2,0,2))})),('multi',lite({'a':region(),'b':region(position=(4,0,0))})),('huge',lite({'a':region(size=(2147483647,384,64))}))]:
 (out/(name+'.litematic')).write_bytes(gzip.compress(root(body),mtime=0))
(out/'simple.schem').write_bytes(gzip.compress(root({'Version':(3,2),'DataVersion':(3,3955),'Width':(2,2),'Height':(2,1),'Length':(2,1),'PaletteMax':(3,2),'Palette':(10,{'minecraft:air':(3,0),'minecraft:cobblestone':(3,1)}),'BlockData':(7,[1,1]),'BlockEntities':(9,(10,[]))}),mtime=0))
(out/'simple.schematic').write_bytes(gzip.compress(root({'Materials':(8,'Alpha'),'Width':(2,2),'Height':(2,1),'Length':(2,1),'Blocks':(7,[4,4]),'Data':(7,[0,0]),'Entities':(9,(10,[])),'TileEntities':(9,(10,[]))}),mtime=0))
(out/'bad.litematic').write_bytes(b'\x0a\x00\x00\x07\x00\x01x'+struct.pack('>i',2147483647)+b'\0')
(out/'trailing.litematic').write_bytes(root(lite({'a':region()}))+b'bad')
# Pure-data malformed nesting; the bundled parser must never see it.
(out/'deep.litematic').write_bytes(b'\x0a\0\0'+b'\x0a\0\x01a'*66+b'\0'*67)

# Physically contained live smoke sample: 4x2x3 floor, 3x3 retaining ring, one source.
smoke=[1]*12+[0]*12
for z in range(3):
 for x in range(3):smoke[12+z*4+x]=3 if (x,z)==(1,1) else 1
smoke[12+3]=2  # trapdoor attached to the east face of the ring's north-east corner
r=region(size=(4,2,3),blocks=smoke)
r['BlockStatePalette'][1][1][2]=block('oak_trapdoor',facing='east',half='top',open='true',powered='false',waterlogged='false')
r['Entities']=(9,(10,[]))
(out/'smoke.litematic').write_bytes(gzip.compress(root(lite({'smoke':r})),mtime=0))
