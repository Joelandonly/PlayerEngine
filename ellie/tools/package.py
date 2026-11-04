from pathlib import Path
from zipfile import ZipFile,ZipInfo,ZIP_DEFLATED
from hashlib import sha256
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'baseline/playerengine-acquisition-safety.jar'
out=ROOT/'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-CHATGPT-INTERPRETER-PREVIEW.jar'
changes={p.relative_to(ROOT/'build/classes').as_posix():p.read_bytes() for p in (ROOT/'build/classes').rglob('*.class')}
changes.update({p.relative_to(ROOT/'build/patches').as_posix():p.read_bytes() for p in (ROOT/'build/patches').rglob('*.class')})
assert len(changes)==2,changes.keys()
with ZipFile(base) as z: orig={k:z.read(k) for k in z.namelist() if not k.endswith('/')}
assert len(set(changes)-set(orig))==1
assert set(changes)&set(orig)=={'com/player2/playerengine/player2api/Player2APIService.class'}
out.parent.mkdir(parents=True,exist_ok=True)
with ZipFile(out,'w',compression=ZIP_DEFLATED,compresslevel=6) as z:
    for key in sorted(set(orig)|set(changes)):
        info=ZipInfo(key,date_time=(2026,11,3,0,0,0));info.compress_type=ZIP_DEFLATED;info.external_attr=0o644<<16
        z.writestr(info,changes.get(key,orig.get(key)),compress_type=ZIP_DEFLATED,compresslevel=6)
with ZipFile(out) as z:
    assert z.testzip() is None
    assert len(z.namelist())==len(set(z.namelist()))
    for key,value in orig.items():
        if key not in changes:assert z.read(key)==value,key
print('Generated',out,'sha256',sha256(out.read_bytes()).hexdigest())
